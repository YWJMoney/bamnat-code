package com.bamnat.core.recipe

import com.bamnat.core.formula.FormulaScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** PC 확인 5c21f3bb: '목표 급여 진행' — the 합계 card turns gold once 이달급여 reaches the 받는 칸 '목표 급여'. */
class PayGoalLineTest {
    private val bp = Blueprint(Blueprint.clean(listOf(
        mapOf("id" to "pay.total", "fill" to 4280825235L, "r" to 18L, "ink" to 4294967295L),
        mapOf("id" to "pay.total", "when" to "이달급여 >= 칸(\"목표 급여\", 2500000)", "paint" to "lg.120.FFFFD54F,FFFFA000", "ink" to 4282263331L, "motion" to "shimmer"),
    )))

    @Test fun goldPastTheGoalTheReceiverPut() {
        val past = bp.style("pay.total", scope = FormulaScope.of(mapOf("이달급여" to 1_923_600L), mapOf("목표 급여" to 1_500_000L)))
        assertNotNull(past.paint)
        assertEquals(4282263331L, past.ink)
        val short = bp.style("pay.total", scope = FormulaScope.of(mapOf("이달급여" to 1_923_600L), mapOf("목표 급여" to 2_500_000L)))
        assertNull(short.paint)
        assertEquals(4280825235L, short.fill)
    }
}
