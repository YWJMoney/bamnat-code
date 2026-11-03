package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36 [SEC-12] 보안 점검 M4: someone else's buttons and rules come in with 가계부 amounts held, and the rules that write are known. */
class LogicReceivedTest {
    private val big = LogicRule("매일 지출", RuleTrigger.DAILY, at = "09:00", act = LogicAct(LogicVerb.ADD_MONEY, "식비", 100_000_000L))
    private val income = LogicButton("월급", null, ButtonPlace.MONEY, LogicAct(LogicVerb.ADD_INCOME, "월급", 3_000_000L))
    private val small = LogicRule("점심", RuleTrigger.TAP, act = LogicAct(LogicVerb.ADD_MONEY, "점심", 9_000L))
    private val note = LogicRule("야간 알림", RuleTrigger.START, min = -60, act = LogicAct(LogicVerb.NOTIFY, "{원(이달지출)}"))

    @Test fun amountsAreHeldToAMillion() {
        val l = Logic(listOf(income), listOf(big, small, note))
        assertTrue(l.overReceived)
        val got = l.received()
        assertEquals(Logic.RECEIVED_MAX_WON, got.rules[0].act.amount)
        assertEquals(Logic.RECEIVED_MAX_WON, got.buttons[0].act.amount)
        assertEquals(9_000L, got.rules[1].act.amount)
        assertEquals(note, got.rules[2])
        assertFalse(got.overReceived)
        // The key stays (its switch and count follow it): the amount is not part of it.
        assertEquals(big.key, got.rules[0].key)
    }

    @Test fun theRulesThatWriteAreTheOnesThatComeInOff() {
        val l = Logic(listOf(income), listOf(big, small, note))
        assertEquals(listOf(big, small), l.writingRules)
        assertEquals(emptyList<LogicRule>(), Logic(rules = listOf(note)).writingRules)
    }
}

/** 1.36 [SEC-12] 보안 점검 L1: a blueprint works out at most 24 day-changing lines, and each formula once for a day. */
class BlueprintWeightTest {
    @Test fun atMostTwentyFourLinesChangeByTheDay() {
        val lines = (0 until 40).map { i -> mapOf<String, Any?>("id" to "cal.cell", "when" to "근무시간 > $i", "fill" to (0xFF000000L + i)) }
        val bp = com.bamnat.core.recipe.Blueprint(lines)
        assertTrue(bp.heavy)
        // Every `when` holds at 100 hours: the 24th line is the last laid on.
        val s = bp.style("cal.cell", emptySet(), FormulaScope.of(mapOf("근무시간" to 100)))
        assertEquals(0xFF000000L + com.bamnat.core.recipe.BlueprintParts.MAX_BY_DAY - 1, s.fill)
        assertFalse(com.bamnat.core.recipe.Blueprint(lines.take(10)).heavy)
    }

    @Test fun aNewDayIsWorkedOutAgain() {
        val bp = com.bamnat.core.recipe.Blueprint(listOf(mapOf("id" to "cal.cell", "when" to "근무시간 > 8", "fill" to 0xFF000001L)))
        assertEquals(0xFF000001L, bp.style("cal.cell", emptySet(), FormulaScope.of(mapOf("근무시간" to 12))).fill)
        assertEquals(null, bp.style("cal.cell", emptySet(), FormulaScope.of(mapOf("근무시간" to 4))).fill)
    }
}
