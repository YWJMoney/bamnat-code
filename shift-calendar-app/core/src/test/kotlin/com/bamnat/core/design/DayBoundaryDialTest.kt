package com.bamnat.core.design

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.39 [FLOW-30] 근무 기준 하루, [FLOW-28] 하루 다이얼. */
class DayBoundaryDialTest {
    private val day = ShiftType(11, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0))
    private val night = ShiftType(12, "야간", "야", 0xFF5E35B1, LocalTime.of(19, 0), LocalTime.of(7, 0))
    private val dawn = ShiftType(14, "새벽", "새", 0xFF00897B, LocalTime.of(3, 0), LocalTime.of(12, 0))
    private val off = ShiftType(13, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val start = LocalDate.of(2026, 11, 2)
    // 11/2 주간, 11/3 야간, 11/4 야간, 11/5 휴무, 11/6 새벽, then again.
    private val r = ScheduleResolver(listOf(day, night, dawn, off), WorkPattern.Rotation(start, listOf(day.id, night.id, night.id, off.id, dawn.id)), emptyList())
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun aMomentInLastNightsShiftIsThatNights() {
        assertEquals(LocalDate.of(2026, 11, 3), DayBoundary.dayOf(at(4, 2, 10), r::resolve))
        // After it ends it is the clock's day again.
        assertEquals(LocalDate.of(2026, 11, 4), DayBoundary.dayOf(at(4, 7, 30), r::resolve))
    }

    @Test fun theSmallHoursOfADayWithoutWorkAreYesterdays() {
        // 11/5 휴무 → 11/6 02:00 nothing works around: before 04:00 is 11/5.
        assertEquals(LocalDate.of(2026, 11, 5), DayBoundary.dayOf(at(6, 2, 0), r::resolve))
        assertEquals(LocalDate.of(2026, 11, 6), DayBoundary.dayOf(at(6, 2, 0), r::resolve, LocalTime.MIDNIGHT))
    }

    @Test fun aShiftThatStartsInTheSmallHoursIsItsOwnDay() {
        assertEquals(LocalDate.of(2026, 11, 6), DayBoundary.dayOf(at(6, 3, 30), r::resolve))
    }

    @Test fun theDialKeepsANightShiftWholeAcrossMidnight() {
        val items = DayStoryItems.of(LocalDate.of(2026, 11, 3), r)
        val dial = DayDialArcs.of(items, at(3, 14), shiftColor = { 0xFF5E35B1 })
        val shift = dial.arcs.single { it.kind == DialKind.Shift && it.startHour == 19f }
        assertEquals(12f, shift.sweepHours)
        assertEquals(0xFF5E35B1, shift.color)
        assertEquals(14f, dial.nowHour)
    }

    @Test fun theShiftWorkedNowStaysWholeOnTheRing() {
        val items = DayStoryItems.of(LocalDate.of(2026, 11, 4), r) // 11/3 night into 11/4, and 11/4's night
        val dial = DayDialArcs.of(items, at(4, 5))
        assertTrue(dial.arcs.any { it.kind == DialKind.Shift && it.startHour == 19f && it.sweepHours == 12f })
        assertTrue(DayDialArcs.says(items, dial).startsWith("19:00부터 07:00까지 야간"))
    }

    @Test fun moneyOfTheNightsSmallHoursSitsAfterMidnightOnItsStory() {
        val coffee = com.bamnat.core.money.MoneyEntry(id = 1, date = LocalDate.of(2026, 11, 3), kind = com.bamnat.core.money.MoneyKind.EXPENSE,
            amount = 2500, category = "식비", time = LocalTime.of(2, 10))
        val items = DayStoryItems.of(LocalDate.of(2026, 11, 3), r, money = listOf(coffee))
        assertEquals(at(4, 2, 10), items.single { it.kind == StoryKind.Money }.start)
        val lunch = coffee.copy(id = 2, date = LocalDate.of(2026, 11, 2), time = LocalTime.of(12, 30))
        assertEquals(at(2, 12, 30), DayStoryItems.of(LocalDate.of(2026, 11, 2), r, money = listOf(lunch)).single { it.kind == StoryKind.Money }.start)
    }
}
