package com.bamnat.core

import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.TimeVersion
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.SinceChoice
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVH-33] 검토 WORK-19 근무 종류의 시간을 '오늘부터만' 바꾸면 지난 날은 옛 시간 그대로(근무시간·통계·예상 급여), '지난 날도'면 모두
 * 새 시간. [RVH-28] 검토 WORK-04 처음 설정 다시 받기는 같은 이름 근무의 알람·색을 지키고 새 시간은 오늘부터.
 */
class ShiftTimeHistoryTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val night = ShiftType(2, "야간", "야", 0xFF3F51B5, LocalTime.of(22, 0), LocalTime.of(7, 0), breakMinutes = 60)
    private val later = night.copy(end = LocalTime.of(8, 0))

    /** 야간 every day for the last month, as an order. */
    private fun resolver(type: ShiftType, entries: List<DayEntry> = emptyList()) =
        ScheduleResolver(listOf(type), WorkPattern.Rotation(today.minusDays(40), steps = listOf(2L)), entries)

    @Test fun fromTodayKeepsThePastDaysHours() {
        val saved = SinceChoice.apply(night, later, SinceChoice.Way.FROM_TODAY, today)
        val r = resolver(saved)
        assertEquals("어제는 옛 시간", LocalTime.of(7, 0), r.resolve(today.minusDays(1)).end)
        assertEquals("지난달도 옛 시간", 480, r.resolve(today.minusDays(30)).scheduledMinutes)
        assertEquals("오늘부터 새 시간", LocalTime.of(8, 0), r.resolve(today).end)
        assertEquals(540, r.resolve(today.plusDays(3)).scheduledMinutes)
    }

    @Test fun pastTooChangesEveryDay() {
        val first = SinceChoice.apply(night, later, SinceChoice.Way.FROM_TODAY, today)
        val all = SinceChoice.apply(first, later.copy(end = LocalTime.of(9, 0)), SinceChoice.Way.PAST_TOO, today)
        assertTrue(all.timeHistory.isEmpty())
        assertEquals(LocalTime.of(9, 0), resolver(all).resolve(today.minusDays(20)).end)
    }

    @Test fun aSecondChangeOnTheSameDayKeepsTheFirstHoursForThePast() {
        val once = SinceChoice.apply(night, later, SinceChoice.Way.FROM_TODAY, today)
        val twice = SinceChoice.apply(once, once.copy(end = LocalTime.of(9, 0)), SinceChoice.Way.FROM_TODAY, today)
        assertEquals(1, twice.timeHistory.size)
        val r = resolver(twice)
        assertEquals(LocalTime.of(7, 0), r.resolve(today.minusDays(1)).end)
        assertEquals(LocalTime.of(9, 0), r.resolve(today).end)
        // A week later, changed again: the week between keeps the 09:00 it had.
        val week = SinceChoice.apply(twice, twice.copy(end = LocalTime.of(6, 0)), SinceChoice.Way.FROM_TODAY, today.plusDays(7))
        val r2 = resolver(week)
        assertEquals(LocalTime.of(7, 0), r2.resolve(today.minusDays(1)).end)
        assertEquals(LocalTime.of(9, 0), r2.resolve(today.plusDays(6)).end)
        assertEquals(LocalTime.of(6, 0), r2.resolve(today.plusDays(7)).end)
    }

    @Test fun onlyAChangeOfHoursOnAKindWorkedBeforeAsks() {
        assertTrue(SinceChoice.asks(night, later, 10))
        assertFalse("시간이 같으면 묻지 않음", SinceChoice.asks(night, night.copy(colorArgb = 1, alarms = listOf(AlarmSpec(30))), 10))
        assertFalse("지난 날이 없으면 묻지 않음", SinceChoice.asks(night, later, 0))
        assertFalse("새 종류는 묻지 않음", SinceChoice.asks(night.copy(id = 0), later, 10))
        // The same hours again leave no version behind.
        assertTrue(night.withTimesFrom(night.copy(colorArgb = 1), today).timeHistory.isEmpty())
    }

    @Test fun thePastDaysCountSkipsDaysWithTheirOwnHoursAndOtherKinds() {
        val day = ShiftType(1, "주간", "주", 0, LocalTime.of(9, 0), LocalTime.of(18, 0))
        val entries = listOf(
            DayEntry(today.minusDays(1), customStart = LocalTime.of(21, 0), customEnd = LocalTime.of(6, 0)),
            DayEntry(today.minusDays(2), override = ShiftOverride.Shift(1)),
        )
        val r = ScheduleResolver(listOf(day, night), WorkPattern.Rotation(today.minusDays(10), steps = listOf(2L)), entries)
        assertEquals(8, SinceChoice.pastDays(r, 2, today, today.minusDays(10)))
        assertEquals("지난 날 8일의 시간도 바뀌어요", SinceChoice.line(8))
    }

    @Test fun theHistoryReadsBackAsItWasWritten() {
        val list = listOf(
            TimeVersion(today.minusDays(30), LocalTime.of(22, 0), LocalTime.of(6, 30), 60, LocalTime.of(2, 0)),
            TimeVersion(today.minusDays(1), LocalTime.of(22, 0), LocalTime.of(7, 0), 0, null),
        )
        assertEquals(list, TimeVersion.decode(TimeVersion.encode(list)))
        assertTrue(TimeVersion.decode("").isEmpty())
        assertEquals("못 읽는 조각만 빠짐", 1, TimeVersion.decode("x|1|2|3|4;" + TimeVersion.encode(list.take(1))).size)
    }

    @Test fun setupAgainKeepsAlarmsAndColorAndCountsNewHoursFromToday() {
        val mine = night.copy(id = 7, colorArgb = 0xFF123456, alarmEnabled = true, alarms = listOf(AlarmSpec(90), AlarmSpec(60)))
        val draft = SetupDraft(style = WorkStyle.SHIFT, types = listOf(later.copy(id = -1, colorArgb = 0xFF000000)), alarmMinutes = 30)
        val saved = draft.typesToSave(listOf(mine), today).single()
        assertEquals(listOf(90, 60), saved.alarms.map { it.offsetMinutes })
        assertEquals(0xFF123456, saved.colorArgb)
        assertEquals(LocalTime.of(8, 0), saved.end)
        assertEquals(LocalTime.of(7, 0), saved.on(today.minusDays(1)).end)
        assertEquals(listOf("야간"), draft.timeChanges(listOf(mine)).map { it.first.name })
        assertEquals(listOf("야간"), draft.keptWork(listOf(mine)).map { it.name })
        // A first setup (no shifts kept) takes the alarm answered.
        assertEquals(listOf(30), draft.typesToSave().single().alarms.map { it.offsetMinutes })
    }
}
