package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVG-01] 검토 PAYU-01 (PC 재현: 닫은 10월이 시급 인상 뒤 2,146,560 → 2,288,000원) and [RVG-02] PAYU-02 (달 걸친 주의 결근).
 */
@RunWith(AndroidJUnit4::class)
class ClosedPayrollMonthTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = PhaseThreeStore(db) { 1L }
    private val october = YearMonth.of(2026, 10)
    private val place = Workplace("w1", "우리 가게", fivePlus = false, payday = 10)
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val weekdays = DayOfWeek.entries.filter { it.value <= 5 }.associateWith { nine }

    @After fun close() = db.close()

    private suspend fun fill(): StaffMember {
        store.saveWorkplace(place)
        val s = StaffMember("s1", "민수", base = 10_320, weekly = weekdays, hiredOn = LocalDate.of(2026, 1, 1))
        store.saveStaff(s, place.id)
        return s
    }

    @Test fun aClosedMonthStaysAsItWasClosed() = runBlocking {
        val s = fill()
        val open = store.inputs(place, october).first()
        assertFalse(open.frozen)
        val total = open.ledger.total
        store.close(place, october, open.ledger.lines, "2026", open.work)
        // 11월에 시급을 올림 (법 바뀜 '눌러서 고쳐요').
        store.saveStaff(s.copy(base = 11_000), place.id)
        val closed = store.inputs(place, october).first()
        assertTrue(closed.frozen)
        assertEquals("닫은 10월은 그대로", total, closed.ledger.total)
        assertEquals(10_320L, closed.staff.single().base)
        assertEquals("신고 달력·관리 첫 화면도 같은 값", total, store.ledgerOf(place, october).total)
        // 다시 열면 지금 값으로 다시 계산해요.
        store.reopen(place.id, october, "2026")
        assertNotEquals(total, store.inputs(place, october).first().ledger.total)
    }

    @Test fun aSnapshotKeepsNoClockKeyOrContract() = runBlocking {
        val s = fill().copy(clockId = "C1", clockToken = "secret")
        val snap = PhaseThreeStore.snapshot(place, s, emptyList())
        assertFalse(snap.contains("secret"))
    }

    @Test fun aMonthClosedBeforeKeepsTheOldWay() = runBlocking {
        fill()
        val open = store.inputs(place, october).first()
        store.close(place, october, open.ledger.lines, "2026")
        // 예전 판처럼 줄에 계산 근거가 없는 경우.
        val lines = db.payrollDao().linesOf(place.id, october.toString())
        db.payrollDao().upsertLines(lines.map { it.copy(deduct = "") })
        assertFalse(store.inputs(place, october).first().frozen)
    }

    @Test fun aWeekThatCrossesTheFirstCountsItsAbsence() = runBlocking {
        fill()
        val before = store.inputs(place, october).first().ledger.total
        // 9/29(화) 결근: 그 주(9/28~10/4)의 주휴는 10월 대장에서 계산돼요.
        store.setWork("s1", LocalDate.of(2026, 9, 29), StaffWork("s1", LocalDate.of(2026, 9, 29), WorkKind.ABSENT))
        val after = store.inputs(place, october).first()
        assertTrue("9/29 결근이 10월 계산에 들어와요", after.work.any { it.date == LocalDate.of(2026, 9, 29) })
        val week = after.ledger.lines.single().pay!!.holidayWeeks.first { !it.from.isAfter(LocalDate.of(2026, 9, 29)) && !it.to.isBefore(LocalDate.of(2026, 9, 29)) }
        assertFalse("결근이 있는 주에는 주휴가 없어요", week.earned)
        assertTrue(after.ledger.total < before)
    }
}
