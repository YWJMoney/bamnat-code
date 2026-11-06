package com.bamnat.core

import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.RosterAdopt
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.38 [P3-X-03] 받은 근무표를 내 달력으로: pairs by letter, name, hours; writes only what differs. */
class RosterAdoptTest {
    private val theirs = listOf(
        RosterAdopt.Theirs(10, "주", "데이", LocalTime.of(7, 0), LocalTime.of(15, 0), false), // letter 주 → 주간
        RosterAdopt.Theirs(11, "N", "야간", LocalTime.of(22, 0), LocalTime.of(7, 0), false), // name 야간
        RosterAdopt.Theirs(12, "L", "롱", LocalTime.of(7, 0), LocalTime.of(19, 0), false), // hours → 장주간
        RosterAdopt.Theirs(13, "O", "오프", null, null, true), // any off
        RosterAdopt.Theirs(14, "X", "교육", LocalTime.of(8, 0), LocalTime.of(17, 0), false), // nothing fits
    )

    @Test fun pairs() {
        val m = RosterAdopt.match(theirs, Fixtures.all)
        assertEquals(1L, m[10]); assertEquals(3L, m[11]); assertEquals(5L, m[12]); assertEquals(4L, m[13]); assertNull(m[14])
    }

    @Test fun onlyWhatDiffers() {
        val start = LocalDate.of(2026, 12, 1)
        val weekly = WorkPattern.Weekly(start, DayOfWeek.entries.associateWith { if (it.value <= 5) 1L else 4L })
        val r = ScheduleResolver(Fixtures.all, weekly, emptyList())
        val days = mapOf(start to 10L, start.plusDays(1) to 11L, start.plusDays(2) to 14L, start.plusDays(3) to null)
        val got = RosterAdopt.changes(days, RosterAdopt.match(theirs, Fixtures.all), Fixtures.all, r::resolve)
        assertEquals(mapOf(start.plusDays(1) to ShiftOverride.Shift(3)), got)
    }
}
