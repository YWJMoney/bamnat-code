package com.bamnat.core

import com.bamnat.core.tier.PlanBand
import com.bamnat.core.tier.WorkHold
import com.bamnat.core.tier.WorkPlanKind
import com.bamnat.core.tier.WorkPlanRules
import com.bamnat.core.tier.WorkPlanRules.Verdict
import com.bamnat.core.tier.WorkPlanTable
import java.time.Instant
import java.time.YearMonth
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** 1.38 Phase 3 요금 (사용자 2026-11-05 20:22, 계획 8장). */
class WorkPlansTest {
    private val t = WorkPlanTable.DEFAULT
    private val nov = YearMonth.of(2026, 11)

    @Test fun theTableIsThePlansOne() {
        assertEquals(listOf(15, 30, 50, 100), t.bands(WorkPlanKind.TEAM).map { it.cap })
        assertEquals(listOf(7_900L, 9_900L, 14_900L, 19_900L), t.team.map { it.monthly })
        assertEquals(listOf(5, 10, 20, 50), t.bands(WorkPlanKind.STAFF).map { it.cap })
        assertEquals(listOf(9_900L, 14_900L, 24_900L, 49_900L), t.staff.map { it.monthly })
        // 연 = 월×12의 약 50%, 이용권 = 연보다 약 15% 높게.
        (t.team + t.staff).forEach { b ->
            val half = b.monthly * 12 / 2.0
            assertTrue("연 $b", b.yearly in (half * 0.97).toLong()..(half * 1.03).toLong())
            assertTrue("이용권 $b", b.pass in (b.yearly * 1.13).toLong()..(b.yearly * 1.17).toLong())
            assertTrue(b.yearlySaves in 48..52)
        }
        assertEquals(3, t.freeStaff)
        assertEquals(1, t.freeTeamMonths)
    }

    @Test fun theBandIsTheSmallestThatHolds() {
        assertEquals(15, t.bandFor(WorkPlanKind.TEAM, 1)?.cap)
        assertEquals(15, t.bandFor(WorkPlanKind.TEAM, 15)?.cap)
        assertEquals(30, t.bandFor(WorkPlanKind.TEAM, 16)?.cap)
        assertEquals(100, t.bandFor(WorkPlanKind.TEAM, 100)?.cap)
        assertNull(t.bandFor(WorkPlanKind.TEAM, 101))
        assertEquals(5, t.bandFor(WorkPlanKind.STAFF, 4)?.cap)
        assertNull(t.bandFor(WorkPlanKind.STAFF, 51))
    }

    @Test fun playNamesReadBack() {
        assertEquals(30, t.capOf(WorkPlanKind.TEAM, "c30-yearly"))
        assertEquals(15, t.capOf(WorkPlanKind.TEAM, "c15-monthly"))
        assertEquals(10, t.capOf(WorkPlanKind.STAFF, "bamnat_staff_pass_10"))
        assertNull(t.capOf(WorkPlanKind.TEAM, "monthly"))
        assertNull(t.capOf(WorkPlanKind.TEAM, null))
        assertEquals(WorkPlanKind.TEAM, WorkPlanKind.ofProduct("bamnat_team"))
        assertEquals(WorkPlanKind.STAFF, WorkPlanKind.ofProduct("bamnat_staff_pass_20"))
        assertNull(WorkPlanKind.ofProduct("bamnat_pro"))
        assertEquals("bamnat_team_pass_15", WorkPlanKind.TEAM.passProduct(15))
        assertEquals("c15-monthly", PlanBand(15, 1, 1, 1).monthlyPlan)
    }

    @Test fun theFirstConfirmedMonthIsFreeAndOnlyIt() {
        val first = WorkPlanRules.teamConfirm(t, live = true, hold = null, confirmed = emptySet(), month = nov, members = 8)
        assertEquals(Verdict.FREE, first.verdict)
        // Confirming it again (고친 뒤 다시 확정) stays free.
        assertEquals(Verdict.FREE, WorkPlanRules.teamConfirm(t, true, null, setOf(nov), nov, 8).verdict)
        // The next month asks for the band that holds 8.
        val next = WorkPlanRules.teamConfirm(t, true, null, setOf(nov), nov.plusMonths(1), 8)
        assertEquals(Verdict.PLAN, next.verdict)
        assertEquals(15, next.band?.cap)
        assertTrue(WorkPlanRules.words(WorkPlanKind.TEAM, next, t).contains("15명 구간"))
        // A month before the free one is not free either.
        assertEquals(Verdict.PLAN, WorkPlanRules.teamConfirm(t, true, null, setOf(nov), nov.minusMonths(1), 8).verdict)
    }

    @Test fun aHeldBandOpensUpToItsCapAndAsksForABiggerOne() {
        val hold = WorkHold(15, Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(Verdict.OPEN, WorkPlanRules.teamConfirm(t, true, hold, setOf(nov), nov.plusMonths(1), 15).verdict)
        val bigger = WorkPlanRules.teamConfirm(t, true, hold, setOf(nov), nov.plusMonths(1), 16)
        assertEquals(Verdict.BIGGER, bigger.verdict)
        assertEquals(30, bigger.band?.cap)
        val contact = WorkPlanRules.teamConfirm(t, true, WorkHold(100, null), setOf(nov), nov.plusMonths(1), 101)
        assertEquals(Verdict.CONTACT, contact.verdict)
        assertTrue(WorkPlanRules.words(WorkPlanKind.TEAM, contact, t).contains("100명이 넘는"))
    }

    @Test fun staffThreeAreFree() {
        assertEquals(Verdict.FREE, WorkPlanRules.staff(t, true, null, 3).verdict)
        val fourth = WorkPlanRules.staff(t, true, null, 4)
        assertEquals(Verdict.PLAN, fourth.verdict)
        assertEquals(5, fourth.band?.cap)
        assertEquals(Verdict.OPEN, WorkPlanRules.staff(t, true, WorkHold(5, null), 5).verdict)
        assertEquals(Verdict.BIGGER, WorkPlanRules.staff(t, true, WorkHold(5, null), 6).verdict)
        assertEquals(Verdict.CONTACT, WorkPlanRules.staff(t, true, null, 51).verdict)
    }

    @Test fun beforeLaunchEverythingIsOpenAndASecondTeamNeedsAPlanAfter() {
        assertEquals(Verdict.OPEN, WorkPlanRules.teamConfirm(t, false, null, setOf(nov), nov.plusMonths(3), 300).verdict)
        assertEquals(Verdict.OPEN, WorkPlanRules.staff(t, false, null, 300).verdict)
        assertEquals(Verdict.OPEN, WorkPlanRules.moreTeams(t, true, null, 0).verdict)
        assertEquals(Verdict.PLAN, WorkPlanRules.moreTeams(t, true, null, 1).verdict)
        assertEquals(Verdict.OPEN, WorkPlanRules.moreTeams(t, true, WorkHold(15, null), 3).verdict)
    }

    @Test fun passesStackForAYearEach() {
        val a = Instant.parse("2027-01-01T00:00:00Z")
        assertEquals(Instant.parse("2028-01-01T00:00:00Z"), WorkPlanRules.passUntil(listOf(a)))
        assertEquals(Instant.parse("2028-12-31T00:00:00Z"), WorkPlanRules.passUntil(listOf(a, Instant.parse("2027-06-01T00:00:00Z"))))
    }
}
