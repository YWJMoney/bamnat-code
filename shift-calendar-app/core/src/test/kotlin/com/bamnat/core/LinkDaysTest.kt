package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.schedule.LinkDays
import com.bamnat.core.schedule.RosterAdopt
import com.bamnat.core.schedule.RosterFollow.Their
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * [RVG-20] 검토 HQ-09: '내 근무표예요' picked for a 가족·동료's link by mistake, found after the setup. Only the days that link put in
 * and the user left as they were come out; a day the user changed by hand stays.
 */
class LinkDaysTest {
    private val theirs = listOf(
        RosterAdopt.Theirs(10, "주", "데이", LocalTime.of(7, 0), LocalTime.of(15, 0), false),
        RosterAdopt.Theirs(11, "N", "야간", LocalTime.of(22, 0), LocalTime.of(7, 0), false),
    )
    private val pairs = RosterAdopt.match(theirs, Fixtures.all)
    private val d1 = LocalDate.of(2026, 12, 1)
    private fun d(n: Int) = d1.plusDays(n - 1L)

    /** A calendar with no pattern of its own: every day is what was put in (a 근무표 that comes each month). */
    private fun resolver(vararg entries: DayEntry) = ScheduleResolver(Fixtures.all, emptyList(), entries.toList())

    @Test fun onlyTheLinksOwnDaysComeOut() {
        val link = mapOf(d(1) to Their.Shift(10), d(2) to Their.Shift(11), d(3) to Their.Rest, d(4) to Their.Shift(10), d(5) to Their.Shift(10))
        val r = resolver(
            DayEntry(d(1), ShiftOverride.Shift(1)), // put in, left as it was
            DayEntry(d(2), ShiftOverride.Shift(3)),
            DayEntry(d(3), ShiftOverride.Off),
            DayEntry(d(4), ShiftOverride.Shift(5)), // changed by hand to 장주간
            // d(5): taken out by hand already: nothing to take out
        )
        val s = LinkDays.split(link, link, pairs, Fixtures.all, r::resolve)
        assertEquals(listOf(d(1), d(2), d(3)), s.theirs)
        assertEquals(listOf(d(4)), s.kept)
    }

    @Test fun aDayWithSomethingOfTheUsersOwnStays() {
        val link = mapOf(d(1) to Their.Shift(10), d(2) to Their.Shift(10))
        val r = resolver(
            DayEntry(d(1), ShiftOverride.Shift(1), customStart = LocalTime.of(8, 0), customEnd = LocalTime.of(16, 0)),
            DayEntry(d(2), ShiftOverride.Shift(1), leave = Leave(quarters = 2)),
        )
        val s = LinkDays.split(link, link, pairs, Fixtures.all, r::resolve)
        assertEquals(emptyList<LocalDate>(), s.theirs)
        assertEquals(listOf(d(1), d(2)), s.kept)
    }

    @Test fun aChangeNotPutInYetStillFindsTheDayAsTheLinkFirstSaidIt() {
        // The link now says 야간 for d(1), asked and not put in: the day still holds what the link said before (basis).
        val before = mapOf(d(1) to Their.Shift(10))
        val now = mapOf(d(1) to Their.Shift(11), d(2) to Their.Shift(11))
        val r = resolver(DayEntry(d(1), ShiftOverride.Shift(1)), DayEntry(d(2), ShiftOverride.Shift(3)))
        assertEquals(listOf(d(1), d(2)), LinkDays.split(now, before, pairs, Fixtures.all, r::resolve).theirs)
    }

    @Test fun daysBeforeTheSetupMonthAreNotTheLinks() {
        val link = mapOf(d1.minusMonths(1) to Their.Shift(10), d(1) to Their.Shift(10))
        val r = resolver(DayEntry(d1.minusMonths(1), ShiftOverride.Shift(1)), DayEntry(d(1), ShiftOverride.Shift(1)))
        assertEquals(listOf(d(1)), LinkDays.split(link, null, pairs, Fixtures.all, r::resolve, from = d1).theirs)
    }
}
