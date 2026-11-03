package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaTest {
    private val day = FormulaScope.of(
        mapOf("근무" to "야간", "꼬리표" to listOf("인수인계", "병원"), "지출" to 52000, "근무시간" to 9.5, "주말" to false, "요일" to "금"),
        mapOf("기준 금액" to 50000),
    )

    private fun v(src: String) = Formula.eval(src, day)?.text

    @Test
    fun multiConditionIfPicksTheFirstTrue() {
        assertEquals("빨강", v("만약(근무 = \"야간\" 그리고 꼬리표.포함(\"인수인계\"), \"빨강\", 지출 > 50000, \"주황\", \"없음\")"))
        assertEquals("주황", v("만약(근무 = \"주간\", \"빨강\", 지출 > 50000, \"주황\", \"회색\")"))
        assertEquals("회색", v("if(shift = \"주간\", \"빨강\", spend > 90000, \"주황\", \"회색\")"))
        assertEquals("", v("만약(거짓, 1)"))
    }

    @Test
    fun numbersTextsAndInputs() {
        assertEquals("52,000원", v("원(지출)"))
        assertEquals("9.5", v("근무시간"))
        assertEquals("10", v("반올림(근무시간)"))
        assertEquals("참", v("지출 > 칸(\"기준 금액\")"))
        assertEquals("야간 9.5시간", v("근무 & \" \" & 근무시간 & \"시간\""))
        assertEquals("2", v("꼬리표.길이()"))
        assertEquals("참", v("중하나(요일, \"토\", \"금\")"))
        assertEquals("굽은", v("“굽은”"))
    }

    @Test
    fun errorsSayWhere() {
        val bad = Formula.parse("만약(근무 = \"야간\", \"빨강\"") as Parsed.Bad
        assertEquals("paren", bad.error.code)
        val name = Formula.parse("근뮤 = 1") as Parsed.Bad
        assertEquals("name", name.error.code)
        assertEquals(0, name.error.from)
        assertTrue(name.error.messageKo.contains("근무"))
        assertEquals("args", Formula.problem("반올림()"))
        assertEquals("char", Formula.problem("근무 ; 1"))
        assertEquals("chain", Formula.problem("1 < 근무시간 < 3"))
    }

    @Test
    fun limitsHold() {
        assertEquals("long", Formula.problem("1+".repeat(400) + "1"))
        assertEquals("deep", Formula.problem("(".repeat(30) + "1" + ")".repeat(30)))
        assertNull(Formula.eval("1 / 0", day))
    }

    @Test
    fun easyRowsGoBothWays() {
        val w = EasyWhen(listOf(EasyCond("근무", EasyCond.Op.IS, "야간"), EasyCond("꼬리표", EasyCond.Op.HAS, "인수인계"), EasyCond("지출", EasyCond.Op.GE, "50,000")))
        val f = w.toFormula()
        assertEquals("근무 = \"야간\" 그리고 꼬리표.포함(\"인수인계\") 그리고 지출 >= 50000", f)
        assertEquals(w.copy(conds = w.conds.mapIndexed { i, c -> if (i == 2) c.copy(value = "50000") else c }), EasyWhen.read(f))
        assertEquals(true, Formula.eval(f, day)?.truthy)
        assertNull(EasyWhen.read("근무 = \"야간\" 또는 지출 > 1 그리고 주말"))
        assertEquals("근무가 야간 그리고 꼬리표에 인수인계 있음", EasyWhen(w.conds.take(2)).say())
    }

    @Test
    fun textWithFormulas() {
        assertEquals("이번 주 야간 9.5시간 {끝}", FormulaText.render("이번 주 야간 {근무시간}시간 {{끝}}", day))
        assertEquals("brace", FormulaText.problem("야간 {근무"))
    }
}
