package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.PatternTimeline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * [EASE-22][EASE-25] 1.38: a 근무마다 메모 read through the repository the calendar, the day view and the widget use, on a real
 * database: it is on each 야간, leaves a 야간 changed by hand to 휴무, and moves with a 하루 밀기.
 */
@RunWith(AndroidJUnit4::class)
class ShiftMemoFollowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val schedule = ScheduleRepository(db)
    private val memos = MemoRepository(db, shiftsOf = { _ -> schedule.snapshot.map { s -> MemoRepeat.ShiftOf { d -> s.resolver.shiftIdOn(d) } } })
    private val start = LocalDate.of(2026, 11, 2)

    @After fun close() = db.close()

    @Test fun theMemoFollowsItsShift() = runBlocking {
        val day = schedule.saveShiftType(ShiftType(0, "주간", "주", 0xFF2196F3, LocalTime.of(9, 0), LocalTime.of(18, 0)))
        val night = schedule.saveShiftType(ShiftType(0, "야간", "야", 0xFF3F51B5, LocalTime.of(22, 0), LocalTime.of(7, 0)))
        val order = WorkPattern.Rotation(start, listOf(day, night, null, null)) // 주 야 휴 휴
        schedule.savePattern(order)
        memos.save(Memo(date = start.plusDays(1), title = "도시락", repeat = MemoRepeat.shiftCode(night), calendars = setOf(PersonalCalendar.MAIN)))
        val nights = (1L..9L step 4).map { start.plusDays(it) } // 3일, 7일, 11일
        val dates = memos.datesWithMemos(PersonalCalendar.MAIN).first()
        assertTrue(dates.containsAll(nights))
        assertFalse(start.plusDays(2) in dates)
        assertEquals("도시락", memos.memosOn(start.plusDays(5), PersonalCalendar.MAIN).first().single().title)

        // The 야간 of the 7th changed by hand to 쉬는 날: the memo is not there any more.
        schedule.updateDays(listOf(start.plusDays(5))) { it.copy(override = ShiftOverride.Off) }
        assertTrue(memos.memosOn(start.plusDays(5), PersonalCalendar.MAIN).first().isEmpty())

        // Pushed a day from the 8th: the 야간 of the 11th is on the 12th, and the memo with it.
        val from = start.plusDays(6)
        schedule.changePatternFrom(PatternTimeline.realign(order, from, PatternTimeline.pushedStep(order, from, 1)), from)
        val after = memos.datesWithMemos(PersonalCalendar.MAIN).first()
        assertFalse(start.plusDays(9) in after)
        assertTrue(start.plusDays(10) in after)
        assertTrue("before the push stays", start.plusDays(1) in after)
    }
}
