package com.bamnat.core

import com.bamnat.core.team.PlanSides
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 2.0.0 [RVJ-90] (총괄 HQ-133) 빈칸 자동 채우기의 안 견주기: what all share once, each 안 only where it differs. */
class PlanSidesTest {
    private val board = TeamBoard(YearMonth.of(2026, 11), emptyList())
    private fun plan(law: Int = 0, rule: Int = 0, run: Int = 5, hours: Int = 8, kept: Int = 0, missed: Int = 0, nights: Int = 1) =
        TeamAuto.Plan(board, 100, emptyMap(), nights, kept, missed, overWeeks = law, ruleSigns = rule, maxRun = run, hourSpread = hours)

    @Test fun theSameOverIsSaidOnceAboveTheirAn() {
        val plans = listOf(plan(law = 1, rule = 26, hours = 8), plan(law = 1, rule = 26, hours = 4), plan(law = 1, rule = 26, hours = 6))
        val top = PlanSides.shared(plans)!!
        assertEquals("어느 안으로 채워도 주 52시간 넘김 1건 · 기준 넘김 26건이 남아요", top.words)
        assertTrue("주 52시간은 위험", top.law)
        plans.forEach { assertNull("같은 넘김은 안마다 되풀이하지 않아요", PlanSides.own(plans, it)) }
        // 칸·부족·최대 연속·야간 차이 are the same in all three; 시간 차이 differs and 4 is the best; 희망 is left out (nobody asked).
        assertEquals(listOf(PlanSides.Facet.FILL, PlanSides.Facet.SHORT, PlanSides.Facet.RUN, PlanSides.Facet.NIGHTS),
            PlanSides.facets(plans).filter { PlanSides.same(plans, it) })
        assertEquals("부족 없음", PlanSides.Facet.SHORT.words(plans[0]))
        assertFalse(PlanSides.Facet.WISHES in PlanSides.facets(plans))
        assertFalse(PlanSides.same(plans, PlanSides.Facet.HOURS))
        assertTrue(PlanSides.best(plans, PlanSides.Facet.HOURS, plans[1]))
        assertFalse(PlanSides.best(plans, PlanSides.Facet.HOURS, plans[0]))
    }

    @Test fun anOverOnlySomeHaveIsSaidInThoseAn() {
        val plans = listOf(plan(law = 1, rule = 3), plan(law = 0, rule = 3), plan(law = 2, rule = 3, kept = 2, missed = 1))
        val top = PlanSides.shared(plans)!!
        assertEquals("어느 안으로 채워도 기준 넘김 3건이 남아요", top.words)
        assertFalse("기준만 남으면 주의", top.law)
        assertTrue(top.rule)
        assertEquals("주 52시간 넘김 1건", PlanSides.own(plans, plans[0])!!.words)
        assertTrue(PlanSides.own(plans, plans[0])!!.law)
        assertEquals("주 52시간 넘김 없음", PlanSides.own(plans, plans[1])!!.words)
        assertFalse(PlanSides.own(plans, plans[1])!!.law)
        // Someone asked for days: 희망 is a value to tell the 안 apart by, the most kept the best.
        assertTrue(PlanSides.Facet.WISHES in PlanSides.facets(plans))
        assertTrue(PlanSides.best(plans, PlanSides.Facet.WISHES, plans[2]))
        assertEquals("희망 2/3", PlanSides.Facet.WISHES.words(plans[2]))
    }

    @Test fun nothingOverInAnyAnIsSaidOnce() {
        val plans = listOf(plan(), plan(hours = 2))
        assertEquals("어느 안으로 채워도 넘는 것 없어요", PlanSides.shared(plans)!!.words)
        assertNull(PlanSides.own(plans, plans[0]))
    }

    @Test fun differentInBothIsSaidInEachAn() {
        val plans = listOf(plan(law = 1, rule = 2), plan(law = 0, rule = 0))
        assertNull(PlanSides.shared(plans))
        assertEquals("주 52시간 넘김 1건 · 기준 넘김 2건", PlanSides.own(plans, plans[0])!!.words)
        assertEquals("넘는 것 없음", PlanSides.own(plans, plans[1])!!.words)
    }

    @Test fun oneAnSaysItAllItself() {
        val one = listOf(plan(law = 1, rule = 26))
        assertNull(PlanSides.shared(one))
        assertEquals("주 52시간 넘김 1건 · 기준 넘김 26건", PlanSides.own(one, one[0])!!.words)
        assertFalse(PlanSides.same(one, PlanSides.Facet.RUN))
        assertEquals("넘는 것 없음", PlanSides.own(listOf(plan()), plan())!!.words)
    }
}
