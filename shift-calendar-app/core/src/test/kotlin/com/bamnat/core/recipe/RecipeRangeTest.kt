package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RCX-25] 받는 사람이 정할 칸의 범위: the receiving phone checks it again itself. */
class RecipeRangeTest {
    private val wage = Input("i1", "시급", InputKind.MONEY, min = 10320.0, max = 50000.0)

    @Test fun inRangeOrNoValueIsFine() {
        assertNull(RecipeResolve.rangeProblem(wage, 12000L))
        assertNull(RecipeResolve.rangeProblem(wage, null))
        assertNull(RecipeResolve.rangeProblem(Input("i2", "메모", InputKind.TEXT, min = 1.0), "아"))
    }

    @Test fun outsideSaysTheRange() {
        assertEquals("10,320~50,000 사이로 넣어 주세요", RecipeResolve.rangeProblem(wage, 9000L))
        assertEquals("10,320~50,000 사이로 넣어 주세요", RecipeResolve.rangeProblem(wage, 60000L))
        assertEquals("3 이상으로 넣어 주세요", RecipeResolve.rangeProblem(Input("i3", "분", InputKind.NUMBER, min = 3.0), 1L))
        assertEquals("1.5 이하로 넣어 주세요", RecipeResolve.rangeProblem(Input("i4", "공수", InputKind.NUMBER, max = 1.5), 2.0))
    }
}
