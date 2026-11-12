package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.RosterAdopt
import com.bamnat.core.schedule.RosterFollow
import com.bamnat.core.schedule.RosterFollow.Their
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.39 [TEAM-22] D9: what the 리더 changed on the link goes into the member's calendar; what the member changed by hand stays. */
class RosterFollowTest {
    private val theirs = listOf(
        RosterAdopt.Theirs(10, "주", "데이", LocalTime.of(7, 0), LocalTime.of(15, 0), false),
        RosterAdopt.Theirs(11, "N", "야간", LocalTime.of(22, 0), LocalTime.of(7, 0), false),
        RosterAdopt.Theirs(14, "X", "교육", LocalTime.of(8, 0), LocalTime.of(17, 0), false), // no pair
    )
    private val pairs = RosterAdopt.match(theirs, Fixtures.all)
    private val d1 = LocalDate.of(2026, 12, 1)
    private val weekly = WorkPattern.Weekly(d1, DayOfWeek.entries.associateWith { if (it.value <= 5) 1L else 4L })

    private fun resolver(vararg entries: DayEntry) = ScheduleResolver(Fixtures.all, weekly, entries.toList())

    @Test fun aChangedDayGoesInWithItsLine() {
        // 12/1 (화) was 주 on the link and is 주간 here; the 리더 made it N.
        val old = mapOf(d1 to Their.Shift(10))
        val new = mapOf(d1 to Their.Shift(11))
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver()::resolve)
        assertEquals(mapOf(d1 to ShiftOverride.Shift(3)), p.apply)
        assertEquals(1, p.lines.size)
        assertTrue(p.lines.single(), p.lines.single().startsWith("12/1 "))
        assertTrue(p.kept.isEmpty())
    }

    @Test fun aDayChangedByHandIsKept() {
        // The member swapped 12/2 to 장주간(5) themselves; the 리더 then made it N: theirs stays, said once.
        val d2 = d1.plusDays(1)
        val old = mapOf(d2 to Their.Shift(10))
        val new = mapOf(d2 to Their.Shift(11))
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver(DayEntry(d2, ShiftOverride.Shift(5)))::resolve)
        assertTrue(p.apply.isEmpty())
        assertEquals(listOf(d2), p.kept)
    }

    @Test fun onlyDaysTheLinkChangedAreLookedAt() {
        // 12/3 is 주 on both copies but the member made it 야간 by hand: not the link's change, nothing happens.
        val d3 = d1.plusDays(2)
        val same = mapOf(d3 to Their.Shift(10))
        val p = RosterFollow.plan(same, same, pairs, Fixtures.all, resolver(DayEntry(d3, ShiftOverride.Shift(3)))::resolve)
        assertTrue(p.isEmpty)
    }

    @Test fun aWorkDayMadeRestIsOff() {
        val old = mapOf(d1 to Their.Shift(10))
        val new = mapOf(d1 to Their.Rest)
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver()::resolve)
        assertEquals(mapOf(d1 to ShiftOverride.Off), p.apply)
        assertTrue(p.lines.single().endsWith("→ 쉬는 날"))
    }

    @Test fun unpairedShiftsAndThePastAreLeft() {
        val d4 = d1.plusDays(3)
        val old = mapOf(d1 to Their.Shift(10), d4 to Their.Shift(10))
        val new = mapOf(d1 to Their.Shift(11), d4 to Their.Shift(14))
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver()::resolve, from = d1.plusDays(1))
        assertTrue(p.isEmpty)
    }

    @Test fun withNoOldCopyEveryDifferingDayGoesIn() {
        val new = mapOf(d1 to Their.Shift(11), d1.plusDays(1) to Their.Shift(10))
        val p = RosterFollow.plan(null, new, pairs, Fixtures.all, resolver()::resolve)
        assertEquals(mapOf(d1 to ShiftOverride.Shift(3)), p.apply)
    }

    /** 2.0.0 [RVH-02] 검토 CAL-06: a day new on the link, which the member had written by hand, is kept and said. */
    @Test fun aNewDayWrittenByHandIsKept() {
        val d9 = d1.plusDays(8)
        val old = mapOf(d1 to Their.Shift(10))
        val new = mapOf(d1 to Their.Shift(10), d9 to Their.Shift(11))
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver(DayEntry(d9, ShiftOverride.Off))::resolve)
        assertTrue(p.apply.isEmpty())
        assertEquals(listOf(d9), p.kept)
        // Not written by hand: it goes in.
        val q = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver()::resolve)
        assertEquals(mapOf(d9 to ShiftOverride.Shift(3)), q.apply)
    }

    /** [RVH-02] A new day with extra work written on it (대근) would count twice under the link's shift: kept. */
    @Test fun aNewDayWithExtraWorkIsKept() {
        val d6 = d1.plusDays(5) // 일요일: 휴무 by the weekly order
        val old = mapOf(d1 to Their.Shift(10))
        val new = mapOf(d1 to Their.Shift(10), d6 to Their.Shift(10))
        val extra = DayEntry(d6, extras = listOf(com.bamnat.core.model.ExtraEntry(kindId = 1, minutes = 480)))
        val p = RosterFollow.plan(old, new, pairs, Fixtures.all, resolver(extra)::resolve)
        assertEquals(listOf(d6), p.kept)
    }
}
