package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.39 [CAL2-20] 약속 겹침 경고, [TASK-04][TASK-20] 알림에서 미루기. */
class ShiftClashSnoozeTest {
    private val today = LocalDate.of(2026, 11, 9)
    private fun night(d: LocalDate) = ShiftMemoTime.Day(d, "야간", true, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private fun dayShift(d: LocalDate) = ShiftMemoTime.Day(d, "주간", true, LocalTime.of(9, 0), LocalTime.of(18, 0))
    private fun off(d: LocalDate) = ShiftMemoTime.Day(d, "휴무", false, null, null)

    @Test fun aTimeInsideAShiftIsSaid() {
        val days = listOf(dayShift(today), night(today.plusDays(1)))
        assertEquals("주간(09:00~18:00)과 겹쳐요", ShiftClash.of(today.atTime(15, 0), days)?.text)
        // Over midnight, on the next day's early hours.
        assertEquals("야간(22:00~07:00)과 겹쳐요", ShiftClash.of(today.plusDays(2).atTime(3, 0), days)?.text)
        // The end itself is free.
        assertNull(ShiftClash.of(today.atTime(18, 0), days))
    }

    @Test fun theMorningAfterANightIsSleep() {
        val days = listOf(night(today.minusDays(1)), off(today))
        val c = ShiftClash.of(today.atTime(10, 0), days)!!
        assertTrue(c.sleep)
        assertEquals("야간 퇴근(07:00) 뒤 3시간이에요 · 잘 시간일 수 있어요", c.text)
        assertEquals("야간 퇴근(07:00) 뒤 30분이에요 · 잘 시간일 수 있어요", ShiftClash.of(today.atTime(7, 30), days)?.text)
        // 2.0.0 [RVI-42] Seven hours on (the same as the day off's afternoon notice, 14:00), the afternoon is free.
        assertTrue(ShiftClash.of(today.atTime(13, 0), days)!!.sleep)
        assertNull(ShiftClash.of(today.atTime(14, 0), days))
        // A day shift's evening is not sleep.
        assertNull(ShiftClash.of(today.atTime(19, 0), listOf(dayShift(today))))
    }

    @Test fun noShiftsNoHoursAndDaysOffWarnOfNothing() {
        assertNull(ShiftClash.of(today.atTime(10, 0), emptyList()))
        assertNull(ShiftClash.of(today.atTime(10, 0), listOf(off(today))))
        assertNull(ShiftClash.of(today.atTime(10, 0), listOf(ShiftMemoTime.Day(today, "일근", true, null, null))))
    }

    @Test fun snoozeGoesToTheNextDayOffMorningOrTomorrow() {
        val now = LocalDateTime.of(2026, 11, 9, 14, 3, 20)
        val was = LocalDateTime.of(2026, 11, 9, 14, 0)
        val rests = setOf(today.plusDays(3), today.plusDays(4))
        val restDay = MemoSnooze.nextRest(today) { it in rests }
        assertEquals(today.plusDays(3), restDay)
        val rest = ShiftMemoTime.offTime(restDay!!, null)
        assertEquals(MemoSnooze.Way.REST, MemoSnooze.later(rest))
        assertEquals("쉬는 날(11/12)", MemoSnooze.label(MemoSnooze.Way.REST, rest))
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), MemoSnooze.at(MemoSnooze.Way.REST, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 14, 13), MemoSnooze.at(MemoSnooze.Way.MIN10, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 15, 3), MemoSnooze.at(MemoSnooze.Way.HOUR1, now, was, rest))
        // No day off ahead (투자, nothing scheduled): tomorrow at the same time.
        assertNull(MemoSnooze.nextRest(today) { false })
        assertEquals(MemoSnooze.Way.TOMORROW, MemoSnooze.later(null))
        // 1.39 [TASK-39] 다른 때…: the day off first when there is one, then the times.
        assertEquals(listOf(MemoSnooze.Way.REST, MemoSnooze.Way.MIN10, MemoSnooze.Way.MIN30, MemoSnooze.Way.HOUR1, MemoSnooze.Way.HOUR3,
            MemoSnooze.Way.TOMORROW), MemoSnooze.choices(rest))
        assertEquals(MemoSnooze.Way.MIN10, MemoSnooze.choices(null).first())
        assertEquals(LocalDateTime.of(2026, 11, 9, 14, 33), MemoSnooze.at(MemoSnooze.Way.MIN30, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 9, 17, 3), MemoSnooze.at(MemoSnooze.Way.HOUR3, now, was, rest))
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), MemoSnooze.at(MemoSnooze.Way.TOMORROW, now, was, null))
        assertEquals(MemoSnooze.Way.REST, MemoSnooze.Way.of("rest"))
    }

    /**
     * 1.39 [TASK-39] PC 6차 교대: 11/10·11 야간(22~07) 뒤 11/12 쉬는 날 아침 9시는 자는 시간이라 깨울 수 있었어요. 야간 다음 날은 오후
     * (퇴근 7시간 뒤, 14시보다 이르지 않게), 그다음 쉬는 날은 아침 그대로.
     */
    @Test fun theDayOffAfterANightIsToldInTheAfternoon() {
        val d10 = LocalDate.of(2026, 11, 10)
        val days = listOf(night(d10), night(d10.plusDays(1)), off(d10.plusDays(2)), off(d10.plusDays(3)))
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), ShiftMemoTime.offTime(d10.plusDays(2), night(d10.plusDays(1))))
        assertEquals(LocalDateTime.of(2026, 11, 13, 9, 0), ShiftMemoTime.offTime(d10.plusDays(3), off(d10.plusDays(2))))
        // A later 퇴근 (09:00) sleeps till 16:00; a 마감 at 00:30 leaves the morning; a day shift the day before too.
        assertEquals(LocalDateTime.of(2026, 11, 12, 16, 0), ShiftMemoTime.offTime(d10.plusDays(2),
            ShiftMemoTime.Day(d10.plusDays(1), "야간", true, LocalTime.of(21, 0), LocalTime.of(9, 0))))
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), ShiftMemoTime.offTime(d10.plusDays(2),
            ShiftMemoTime.Day(d10.plusDays(1), "마감", true, LocalTime.of(17, 0), LocalTime.of(0, 30))))
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), ShiftMemoTime.offTime(d10.plusDays(2), dayShift(d10.plusDays(1))))
        // 2.0.0 [RVI-42] (검토 MEMO-21) A 04:00 퇴근: the afternoon, and the notice is not itself warned of as 잘 시간.
        val late = ShiftMemoTime.Day(d10.plusDays(1), "마감", true, LocalTime.of(18, 0), LocalTime.of(4, 0))
        val told = ShiftMemoTime.offTime(d10.plusDays(2), late)
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), told)
        assertNull(ShiftClash.of(told, listOf(late)))
        // '근무에 맞춰' and "쉬는 날" in the words say the afternoon too.
        val chip = ShiftMemoTime.suggestions(days, LocalDateTime.of(2026, 11, 9, 20, 0)).first { it.name == "휴무" }
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), chip.at)
        assertEquals("다음 휴무(11/12) 오후", chip.label)
        val found = ShiftMemoTime.find("쉬는 날 장보기", LocalDateTime.of(2026, 11, 9, 20, 0), days)!!
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), found.at)
        assertEquals("다음 쉬는 날(11/12) 오후", found.phrase)
        assertEquals("오후", ShiftMemoTime.partOf(found.at))
        assertEquals("아침", ShiftMemoTime.partOf(LocalDateTime.of(2026, 11, 13, 9, 0)))
    }

    /**
     * 1.39 [TASK-39] PC 6차 일반 직장인: 평일 일근은 토·일이 비어 있어 쉬는 날이 없다고 봤어요. 근무표가 비워 둔 날도 쉬는 날이에요(일요일
     * 11/8에 보면 토 11/14). 아무 근무표도 없는 달력(투자)은 그대로 없음.
     */
    @Test fun aWeekdayWorkersWeekendIsADayOff() {
        val sunday = LocalDate.of(2026, 11, 8)
        val weekdays = com.bamnat.core.model.WorkPattern.Weekly.fromSteps(LocalDate.of(2026, 1, 1),
            listOf(1L, 1L, 1L, 1L, 1L, null, null))
        val office = com.bamnat.core.schedule.ScheduleResolver(com.bamnat.core.Fixtures.all, weekdays, emptyList())
        val rest = MemoSnooze.nextRest(sunday) { ShiftMemoTime.isRest(office.resolve(it)) }
        assertEquals(LocalDate.of(2026, 11, 14), rest)
        val sat = ShiftMemoTime.of(office.resolve(rest!!))!!
        assertEquals(ShiftMemoTime.REST_NAME, sat.name)
        assertEquals(LocalDateTime.of(2026, 11, 14, 9, 0), ShiftMemoTime.offTime(rest, ShiftMemoTime.of(office.resolve(rest.minusDays(1)))))
        val days = (0L..7L).mapNotNull { ShiftMemoTime.of(office.resolve(sunday.plusDays(it))) }
        assertEquals("다음 쉬는 날(11/14) 아침", ShiftMemoTime.suggestions(days, sunday.atTime(10, 0)).first { !it.label.contains("출근") }.label)
        val nothing = com.bamnat.core.schedule.ScheduleResolver(com.bamnat.core.Fixtures.all, null as com.bamnat.core.model.WorkPattern?, emptyList())
        assertNull(MemoSnooze.nextRest(sunday) { ShiftMemoTime.isRest(nothing.resolve(it)) })
        assertNull(ShiftMemoTime.of(nothing.resolve(sunday.plusDays(6))))
    }
}
