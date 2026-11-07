package com.bamnat.app.recipe

import com.bamnat.core.recipe.Cover
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [MKC-01][MKC-04] 장터 구간 표시: 탭별 작품의 꾸미기·기능, 도구의 종류·화면, 어느 구간에든 붙는 '✦ 프로 기능'. */
class MarketKindsTest {
    private fun item(scope: String, tabs: List<String>, fx: List<String> = emptyList(), tools: List<String> = emptyList(), made: String? = null) =
        RecipeItem("CODE2345", "t", "", emptySet(), Cover(), null, 1, 0, 0, 0, null, false, false, null, Instant.EPOCH,
            tabs = tabs, scope = scope, made = made, tools = tools, fx = fx)

    @Test fun fxRidesOnEveryHalf() {
        val tab = RecipeClient.tabKind("memo", null, fx = true)
        assertEquals("tab:memo!fx", tab)
        assertEquals("memo", RecipeClient.tabOf(tab))
        assertNull(RecipeClient.madeOf(tab))
        assertTrue(RecipeClient.fxOnly(tab))
        // 꾸미기·기능을 고르면 '✦ 프로 기능'은 풀려요 (한 줄의 칩).
        assertEquals("tab:memo~look", RecipeClient.tabKind("memo", "look", fx = true))
        val tool = RecipeClient.toolKind("", "money", fx = true)
        assertEquals("tool:@money!fx", tool)
        assertEquals("money", RecipeClient.toolOn(tool))
        assertEquals("", RecipeClient.toolOf(tool))
        assertEquals("tool:rule@money", RecipeClient.toolKind("rule", "money", fx = true))
        assertEquals("theme!fx", RecipeClient.withFx(RecipeClient.SCOPE_THEME, true))
        assertEquals(RecipeClient.SCOPE_THEME, RecipeClient.plain("theme!fx"))
    }

    @Test fun onlyWorksWithANewDeviceFitTheFxChip() {
        val pro = item("logic", listOf("money"), fx = listOf("values"), tools = listOf("button"))
        val free = item("logic", listOf("money"), tools = listOf("button"))
        assertTrue(RecipeClient.fits(pro, "tool:!fx"))
        assertFalse(RecipeClient.fits(free, "tool:!fx"))
        assertTrue(RecipeClient.fits(free, "tool:"))
        assertTrue(RecipeClient.fits(pro, "tool:@money!fx"))
        assertFalse(RecipeClient.fits(pro, "tool:@memo!fx"))
        val theme = item("theme", listOf("calendar"), fx = listOf("outside"))
        assertTrue(RecipeClient.fits(theme, "theme!fx"))
        assertFalse(RecipeClient.fits(item("theme", listOf("calendar")), "theme!fx"))
    }
}
