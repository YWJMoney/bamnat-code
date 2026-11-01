package com.bamnat.core.formula

import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.FormulaValues
import com.bamnat.core.recipe.Ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RUL-02] 조건부 꾸미기: a line with `when` is put on only on days it holds, after the states and before `sel`; fx values per day. */
class BlueprintWhenTest {
    private val bp = Blueprint(
        listOf(
            mapOf("id" to "cal.cell", "fill" to 0xFF000001L),
            mapOf("id" to "cal.cell", "on" to "role.night", "fill" to 0xFF000002L),
            mapOf("id" to "cal.cell", "when" to "꼬리표.포함(\"인수인계\")", "lineC" to 0xFF0000FFL, "line" to "solid"),
            mapOf("id" to "cal.cell", "when" to "지출 >= 50000", "fill" to 0xFF000003L),
            mapOf("id" to "cal.cell", "on" to "sel", "fill" to 0xFF000009L),
            mapOf("id" to "cal.cell.date", "ink" to Ref.Formula("만약(주말, \"빨강\", \"#112233\")"), "size" to Ref.Formula("근무시간 * 3")),
        ),
    )

    private fun day(vararg p: Pair<String, Any?>) = FormulaScope.of(mapOf(*p))

    @Test
    fun linesFollowTheDay() {
        val plain = bp.style("cal.cell", setOf("role.night"), day())
        assertEquals(0xFF000002L, plain.fill)
        assertNull(plain.line)
        val busy = bp.style("cal.cell", setOf("role.night"), day("꼬리표" to listOf("인수인계"), "지출" to 60000))
        assertEquals(0xFF000003L, busy.fill)
        assertEquals("solid", busy.line)
        assertEquals(0xFF0000FFL, busy.lineC)
        assertEquals(0xFF000009L, bp.style("cal.cell", setOf("sel"), day("지출" to 60000)).fill)
        // Without a day the formula lines are not drawn (and the static answer is kept).
        assertEquals(0xFF000002L, bp.style("cal.cell", setOf("role.night")).fill)
    }

    @Test
    fun formulaValuesBecomeTheFieldsValues() {
        val d = bp.style("cal.cell.date", emptySet(), day("주말" to true, "근무시간" to 40))
        assertEquals(FormulaValues.COLORS["빨강"], d.ink)
        assertEquals(96.0, d.size)
        assertEquals(0xFF112233L, bp.style("cal.cell.date", emptySet(), day("근무시간" to 4)).ink)
        assertEquals("dash", FormulaValues.forField("line", Value.Text("파선")))
        assertNull(FormulaValues.forField("line", Value.Text("물결")))
    }
}
