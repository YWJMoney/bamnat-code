package com.bamnat.core

import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.TaskList
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.work.WatchDay
import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVI-54] (검토 ALM-27) The places the 03 review found without a test: a night that starts after midnight, the watch the day
 * after it was sent, and the 할 일 위젯 drawn again only when it looks different ([RVI-53] 검토 MEMO-19).
 */
class ReviewIGapTest {
    private val day = LocalDate.of(2026, 11, 12)

    /** [RVI-13] (검토 ALM-11) 00:00~08:00 is a night: the sleep after it is planned like any 야간's. */
    @Test fun aNightFromMidnightHasItsSleepAfter() {
        val night = SleepSchedule.Work(day, day.atTime(0, 0), day.atTime(8, 0), "야간")
        assertTrue(night.overnight)
        val plan = SleepSchedule.plan(listOf(night), emptyList(), 7 * 60, 30)
        val sleep = plan.sleepAfter(night)
        assertNotNull("a 야간 from midnight has its sleep after", sleep)
        assertFalse("the sleep starts after work", sleep!!.from.isBefore(night.to))
        // 05:00 is an early start, not a night: the night before it is the one cut short.
        val early = SleepSchedule.Work(day, day.atTime(5, 0), day.atTime(13, 0), "조출")
        assertFalse(early.overnight)
    }

    private fun payload(sentAt: LocalDateTime) = WatchPayload(
        days = listOf(
            WatchDay(day, "주간", "주", 0xFF2196F3, LocalTime.of(9, 0), LocalTime.of(18, 0)),
            WatchDay(day.plusDays(1), "주간", "주", 0xFF2196F3, LocalTime.of(9, 0), LocalTime.of(18, 0)),
        ),
        syncedAt = sentAt,
        nextAlarm = day.atTime(7, 30),
        alarmToday = true,
        clock = WatchPayload.Clock.OUT,
        hourly = true,
        weekMinutes = 20 * 60,
        sleepLine = "잘 시간 23:00–06:30",
    )

    /** [RVI-08] (검토 ALM-09) The phone sent yesterday evening; past midnight the watch works out today itself. */
    @Test fun theWatchDoesNotShowYesterdayAfterMidnight() {
        val p = payload(day.atTime(21, 0))
        val sameEvening = day.atTime(22, 0)
        assertEquals(WatchPayload.Clock.OUT, WatchDays.clockNow(p, sameEvening))
        val nextMorning = day.plusDays(1).atTime(6, 0)
        assertFalse("yesterday's alarm is not today's", WatchDays.alarmTodayNow(p, nextMorning))
        assertEquals("a new working day offers 출근", WatchPayload.Clock.IN, WatchDays.clockNow(p, nextMorning))
        assertEquals("", WatchDays.sleepLineNow(p, day.plusDays(1).atTime(8, 0)))
        // 11/12 is a Thursday: the same week on Sunday, a new one on Monday.
        assertEquals(20 * 60, WatchDays.weekMinutesNow(p, LocalDate.of(2026, 11, 15)))
        assertEquals(-1, WatchDays.weekMinutesNow(p, LocalDate.of(2026, 11, 16)))
        assertEquals(null, WatchDays.weekText(p, LocalDate.of(2026, 11, 16)))
    }

    /** [RVI-53] (검토 MEMO-19) Typing in a memo's body leaves the widget as it is; finishing it or a new title does not. */
    @Test fun theTaskWidgetIsDrawnAgainOnlyWhenItLooksDifferent() {
        val m = Memo(id = 1, title = "택배 찾기", body = "경비실", date = day, reminder = MemoReminder(day.atTime(18, 0), ring = false, notify = true))
        val before = TaskList.looks(listOf(m), day)
        assertEquals(before, TaskList.looks(listOf(m.copy(body = "경비실 옆 무인함", updatedAtMillis = 99)), day))
        assertNotEquals(before, TaskList.looks(listOf(m.copy(title = "택배 두 개 찾기")), day))
        assertNotEquals(before, TaskList.looks(listOf(m.copy(doneAtMillis = 1L)), day))
        assertNotEquals(before, TaskList.looks(listOf(m.copy(reminder = MemoReminder(day.atTime(19, 0), ring = false, notify = true))), day))
    }
}
