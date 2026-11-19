package com.bamnat.core.review

import com.bamnat.core.plaza.CommunityCare
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVO-31] 갈래 O 8차: 마을 머리줄 낱말(U11b-12·T11b-06)과 해명 붙여 넣기(PRO-23·U11b-19).
 */
class VillageWordsTest {
    /** Every kind has its own line, ends in "과" (…분들과 / …사장님들과), and none says "투자 분들과"-style "<유형 이름> 분들과". */
    @Test fun headLineIsInEachKindsOwnWords() {
        val lines = WorkStyle.entries.map { RecipeDescribe.together(it) }
        assertEquals("여섯 유형이 모두 다른 말", WorkStyle.entries.size, lines.toSet().size)
        for ((s, line) in WorkStyle.entries.zip(lines)) {
            assertTrue("$s: $line", line.endsWith("들과"))
            assertFalse("$s: '${RecipeDescribe.short(s)} 분들과' 꼴", line == RecipeDescribe.short(s) + " 분들과")
        }
        assertEquals("가게 사장님들과", RecipeDescribe.together(WorkStyle.BUSINESS))
    }

    @Test fun aLongPasteIsCutAtTheLimitAndSaysSo() {
        val limit = CommunityCare.APPEAL_MAX
        val (fit, cut) = CommunityCare.fitAppeal("가".repeat(limit + 250))
        assertEquals(limit, fit.length)
        assertTrue("잘랐으면 알려요", cut)
        val (same, notCut) = CommunityCare.fitAppeal("가".repeat(limit))
        assertEquals(limit, same.length)
        assertFalse("한도까지는 그대로", notCut)
        assertEquals("" to false, CommunityCare.fitAppeal(""))
    }
}
