package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.TurnSlipCheck.Choice
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.memo.Memo
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.schedule.TurnSlip.Kind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 2.0.0 [RVH-51] PC-72 '이 날 맞아요?' on a real database (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md 3·4·5절): which days, memos and
 * money lines it asks about, and that 옮기기·빼기 change only what they say and come back with 되돌리기.
 */
@RunWith(AndroidJUnit4::class)
class TurnSlipCheckTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val seoul = ZoneId.of("Asia/Seoul")
    private var now = at(2026, 11, 1)
    private val schedule = ScheduleRepository(db, clock = { now })
    private val money = MoneyRepository(db, clock = { now })
    private val memos = MemoRepository(db, clock = { now })
    private val check = TurnSlipCheck({ listOf(PersonalCalendar.MAIN) }, { schedule }, { money }, memos, { DayOfWeek.SUNDAY }, { seoul })
    private val update: Instant = Instant.ofEpochMilli(at(2026, 11, 20, 9))

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    private fun day(m: Int, d: Int, y: Int = 2026) = LocalDate.of(y, m, d)

    @After fun close() = db.close()

    /** 주간 every day from 1 November, so a 연차 or a day off is a change the sheet can show. */
    private suspend fun days() {
        val work = schedule.saveShiftType(ShiftType(0, "주간", "주", 0xFF2196F3, LocalTime.of(9, 0), LocalTime.of(18, 0)))
        schedule.savePattern(WorkPattern.Rotation(day(11, 1), listOf(work)))
    }

    @Test fun findsTheTapsThatWentAMonthOnAndNothingElse() = runBlocking {
        days()
        now = at(2026, 11, 12, 10)
        schedule.setLeave(day(12, 17), Leave()) // PC 4절: meant for 19 November.
        schedule.setOverride(day(12, 10), ShiftOverride.Off) // meant for 12 November.
        now = at(2026, 11, 13)
        schedule.setOverride(day(11, 20), ShiftOverride.Off) // this month: not a line.
        now = at(2026, 11, 14)
        // 근무표 넣기 wrote all of January in one go: not a tap.
        schedule.updateDays((1..31).map { day(1, it, 2027) }) { it.copy(override = ShiftOverride.Off) }
        now = at(2026, 11, 15)
        schedule.setOverride(day(12, 24), ShiftOverride.Off)
        now = at(2026, 11, 16)
        schedule.resetDay(day(12, 24)) // put back since: nothing to move.
        now = at(2026, 11, 5)
        val memo = memos.save(Memo(date = day(12, 3), title = "건강검진"))
        memos.save(Memo(date = day(12, 5), title = "월세", repeat = "M5")) // a repeat comes back on its own: not a line.
        now = at(2026, 11, 12, 11)
        val spent = money.save(MoneyEntry(date = day(12, 10), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비"))
        money.save(MoneyEntry(date = day(12, 11), kind = MoneyKind.EXPENSE, amount = 5_000, category = "식비", source = MoneySource.NOTIFICATION))
        now = at(2026, 11, 21) // after the update: not a line.
        schedule.setOverride(day(12, 31), ShiftOverride.Off)

        val found = check.find(update)
        val moved = found.filter { it.kind == Kind.DAY }.associate { it.line.date to it.line.meant }
        assertEquals(mapOf(day(12, 17) to day(11, 19), day(12, 10) to day(11, 12)), moved)
        assertEquals(memo.id, found.single { it.kind == Kind.MEMO }.memo!!.id)
        assertEquals(day(11, 5), found.single { it.kind == Kind.MEMO }.line.meant)
        assertEquals(spent.id, found.single { it.kind == Kind.MONEY }.entry!!.id)
        assertEquals(4, found.size)
    }

    @Test fun movingTakesTheDayToTheDayMeantAndUndoBringsBothBack() = runBlocking {
        days()
        now = at(2026, 11, 10)
        schedule.updateDay(day(11, 19)) { it.copy(extras = listOf(ExtraEntry(kindId = 1, minutes = 60))) } // what 19 November held.
        now = at(2026, 11, 12, 10)
        schedule.setLeave(day(12, 17), Leave())
        schedule.setOverride(day(12, 10), ShiftOverride.Off)
        val memo = memos.save(Memo(date = day(12, 3), title = "건강검진"))
        val spent = money.save(MoneyEntry(date = day(12, 10), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비"))

        val found = check.find(update)
        val leave = found.single { it.line.date == day(12, 17) }
        // 19 November holds a change of its own: the sheet says it is replaced (조건 10).
        assertNotNull(leave.meantHeld)
        now = at(2026, 11, 20, 10)
        val done = check.apply(mapOf(
            leave to Choice.MOVE,
            found.single { it.kind == Kind.DAY && it.line.date == day(12, 10) } to Choice.CLEAR,
            found.single { it.kind == Kind.MEMO } to Choice.MOVE,
            found.single { it.kind == Kind.MONEY } to Choice.KEEP,
        ))
        assertEquals(3, done.count)
        assertNotNull(schedule.entryNow(day(11, 19))!!.leave)
        assertTrue(schedule.entryNow(day(11, 19))!!.extras.isEmpty())
        assertNull(schedule.entryNow(day(12, 17)))
        assertNull(schedule.entryNow(day(12, 10)))
        assertEquals(day(11, 5), memos.get(memo.id)!!.date)
        assertEquals(day(12, 10), money.allEntries().single { it.id == spent.id }.date) // 맞아요 changes nothing.
        // Both days say what happened in 최근 바꾼 기록.
        assertEquals("12월 17일에서 옮김 (지난 판 확인)", schedule.changesOn(day(11, 19)).first().first().text)
        assertEquals("11월 19일로 옮김 (지난 판 확인)", schedule.changesOn(day(12, 17)).first().first().text)

        check.undo(done)
        assertNotNull(schedule.entryNow(day(12, 17))!!.leave)
        assertEquals(1, schedule.entryNow(day(11, 19))!!.extras.size)
        assertNull(schedule.entryNow(day(11, 19))!!.leave)
        assertEquals(ShiftOverride.Off, schedule.entryNow(day(12, 10))!!.override)
        assertEquals(day(12, 3), memos.get(memo.id)!!.date)
    }

    @Test fun movingAMoneyLineKeepsAllButItsDate() = runBlocking {
        now = at(2026, 11, 12, 11)
        val spent = money.save(MoneyEntry(date = day(12, 10), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비", note = "점심"))
        val line = check.find(update).single()
        val done = check.apply(mapOf(line to Choice.MOVE))
        val moved = money.allEntries().single()
        assertEquals(listOf(spent.id, day(11, 12), 12_000L, "식비", "점심"), listOf(moved.id, moved.date, moved.amount, moved.category, moved.note))
        check.undo(done)
        assertEquals(day(12, 10), money.allEntries().single().date)
    }

    @Test fun nothingFoundOnAPhoneThatChangedNothingThere() = runBlocking {
        days()
        now = at(2026, 11, 12)
        schedule.setOverride(day(11, 14), ShiftOverride.Off)
        assertTrue(check.find(update).isEmpty())
    }

    @Test fun clearingPutsTheDayBackToItsCycle() = runBlocking {
        days()
        now = at(2026, 11, 12)
        schedule.setOverride(day(12, 10), ShiftOverride.Off)
        val line = check.find(update).single()
        assertEquals(DayEntry(day(12, 10), override = ShiftOverride.Off), line.held)
        val done = check.apply(mapOf(line to Choice.CLEAR))
        assertNull(schedule.entryNow(day(12, 10)))
        check.undo(done)
        assertEquals(ShiftOverride.Off, schedule.entryNow(day(12, 10))!!.override)
    }
}
