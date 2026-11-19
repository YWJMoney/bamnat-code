package com.bamnat.app.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.ShiftProgress
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-30] (PC-198) 자정 넘어 야간 근무 중인 사람의 잠금 화면 '오늘 근무': the title counted down to 퇴근 while the text said
 * '오늘 휴무 · 내일 휴무'. Work begun yesterday is told as now's work, with what comes after it; a day's own work is as before.
 */
@RunWith(AndroidJUnit4::class)
class NightNoticeWordsTest {
    private val night = ShiftType(2, "야간", "야", 0xFF283593, LocalTime.of(19, 0), LocalTime.of(7, 0))
    private val start = LocalDate.of(2026, 11, 18)
    /** 야간, 쉬는 날, 쉬는 날, … from [start]. */
    private val resolver = ScheduleResolver(listOf(night), listOf(WorkPattern.Rotation(start, listOf(2L, null, null))), emptyList())
    private val today = start.plusDays(1)

    private fun going(at: java.time.LocalDateTime) =
        ShiftProgress.of(listOf(today.minusDays(1), today).flatMap { SleepSchedule.work(resolver.resolve(it)) }, at)

    @Test fun pastMidnightOnANightTheTextSaysTheNightNotTodaysDayOff() {
        val now = today.atTime(0, 11)
        val (title, text) = TodayShiftNotice.words(resolver, today, now, WorkStyle.SHIFT, going(now))
        assertEquals("퇴근까지 6시간 49분", title)
        assertEquals("지금 야간 근무 중 · 07:00 퇴근 · 퇴근 뒤 쉬는 날", text)
        assertFalse(text.contains("오늘 쉬는 날"))
    }

    @Test fun anOlderPhoneWithoutTheBarSaysTheSame() {
        val (title, text) = TodayShiftNotice.words(resolver, today, today.atTime(3, 0), WorkStyle.SHIFT, null)
        assertEquals("지금 야간 근무 중 · 07:00 퇴근", title)
        assertEquals("퇴근 뒤 쉬는 날", text)
    }

    @Test fun anotherNightTonightIsSaidAsTodays() {
        val nights = ScheduleResolver(listOf(night), listOf(WorkPattern.Rotation(start, listOf(2L))), emptyList())
        val now = today.atTime(2, 0)
        val (_, text) = TodayShiftNotice.words(nights, today, now, WorkStyle.SHIFT, null)
        assertEquals("오늘 야간 19:00~07:00", text)
    }

    @Test fun afterTheNightEndsItIsTheDayAsBefore() {
        val now = today.atTime(8, 0)
        assertEquals("오늘 쉬는 날" to "내일 쉬는 날", TodayShiftNotice.words(resolver, today, now, WorkStyle.SHIFT, going(now)))
    }

    @Test fun theNightsOwnDayIsAsBefore() {
        val now = start.atTime(20, 0)
        val g = ShiftProgress.of(listOf(start.minusDays(1), start).flatMap { SleepSchedule.work(resolver.resolve(it)) }, now)
        val (title, text) = TodayShiftNotice.words(resolver, start, now, WorkStyle.SHIFT, g)
        assertEquals("퇴근까지 11시간", title)
        assertEquals("오늘 야간 19:00~07:00 · 내일 쉬는 날", text)
    }
}
