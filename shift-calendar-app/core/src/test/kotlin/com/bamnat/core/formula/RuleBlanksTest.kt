package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 2.0.0 [RVM-39] PC-148 (통합 4차, 조건 4·13): what a rule will do, said before it runs (넣기 창, 받은 규칙 켜기 창, 알림 모아 보기), has a ○
 * where a {수식} goes, never the formula's letters ({값("물 잔")}); what it did has the number.
 */
class RuleBlanksTest {
    @Test fun aPlaceIsARing() {
        assertEquals("이달 야간 ○번째 · 물 ○잔", FormulaText.blanks("이달 야간 {값(\"이달 야간\")}번째 · 물 {값(\"물 잔\")}잔"))
        assertEquals("○시간 잤어요. 오늘 ○예요", FormulaText.blanks("{지난시간(\"잠\")}시간 잤어요. 오늘 {근무}예요"))
        assertEquals("물 ○잔 연속", FormulaText.blanks("물 {연속(\"물 잔\")}잔 연속"))
        assertEquals("글자 그대로", FormulaText.blanks("글자 그대로"))
        assertEquals("{메모} 칸", FormulaText.blanks("{{메모}} 칸"))
    }

    @Test fun textThatIsNotFormulasAsAWholeStillHasNoLetters() {
        // Nine places (a rule's steps said together) is past what one text may hold; still no formula letters.
        val nine = (1..9).joinToString(" ") { "{값(\"물 잔\")}" }
        assertFalse(FormulaText.blanks(nine).contains("값("))
        assertEquals("열린 ○ 칸 {", FormulaText.blanks("열린 {근무} 칸 {"))
    }

    @Test fun whatItDidHasTheNumber() {
        val day = FormulaScope.of(mapOf("근무" to "주간"), emptyMap())
        assertEquals("오늘 주간이에요", FormulaText.said("오늘 {근무}예요", day))
        // Past what one text may hold: the places, not the letters.
        assertEquals((1..9).joinToString(" ") { "○" }, FormulaText.said((1..9).joinToString(" ") { "{근무}" }, day))
    }
}
