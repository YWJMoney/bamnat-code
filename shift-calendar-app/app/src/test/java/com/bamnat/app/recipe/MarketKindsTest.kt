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

    /** 1.39 [MKD-11] '무료로 다 되는 것': ✦ 프로 기능과 같은 자리, 둘 중 하나만; 다른 칩으로 옮겨도 남아요(탭·화면). */
    @Test fun freeOnlyIsThePairOfFx() {
        val tab = RecipeClient.tabKind("memo", null, free = true)
        assertEquals("tab:memo!free", tab)
        assertEquals("memo", RecipeClient.tabOf(tab))
        assertTrue(RecipeClient.freeOnly(tab) && !RecipeClient.fxOnly(tab))
        assertEquals("tab:money!free", RecipeClient.withPrice(RecipeClient.tabKind("money", null), RecipeClient.price(tab)))
        assertEquals("theme!fx", RecipeClient.withFx("theme!free", true))
        assertEquals("theme", RecipeClient.withFree("theme!free", false))
        assertEquals("tool:@alarm!free", RecipeClient.toolKind("", "alarm", free = true))
        val free = RecipeItem("A", "a", "", emptySet(), com.bamnat.core.recipe.Cover(), null, 1, 0, 0, 0, null, false, false, null,
            java.time.Instant.EPOCH, tabs = listOf("memo"), scope = "tab")
        val pro = free.copy(code = "B", fx = listOf("values"))
        assertTrue(RecipeClient.fits(free, tab))
        assertTrue(!RecipeClient.fits(pro, tab))
        assertTrue(RecipeClient.fits(pro, RecipeClient.tabKind("memo", null, fx = true)))
    }

    /** 1.39 [MKD-03] 장치·종류 이름으로 찾으면 그 칩으로. */
    @Test fun aDeviceNameFindsItsChip() {
        assertEquals("tab:memo!fx" to "✦ 프로 기능 작품", RecipeClient.wordKind(" 값 칸 ", "tab:memo"))
        assertEquals("theme!fx", RecipeClient.wordKind("✦ 프로", "theme")?.first)
        assertEquals("theme!free", RecipeClient.wordKind("무료", "theme!fx")?.first)
        assertEquals("tab:widget", RecipeClient.wordKind("위젯", "theme")?.first)
        assertEquals("tool:part", RecipeClient.wordKind("부품", "theme")?.first)
        assertNull("이미 그 칩이면 없어요", RecipeClient.wordKind("프로", "theme!fx"))
        assertNull(RecipeClient.wordKind("야간 파랑", "theme"))
    }

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

    /** PC 4차 c0ca3d9f: 부품 상세의 넣기 단추가 '이 테마 입기'였어요. 작품 종류대로. */
    @Test fun thePutInButtonSaysWhatKindItIs() {
        assertEquals("이 테마 입기", com.bamnat.app.ui.recipe.wholeWords("theme", part = false))
        assertEquals("이 부품 넣기", com.bamnat.app.ui.recipe.wholeWords("part", part = true))
        assertEquals("이 부품 넣기", com.bamnat.app.ui.recipe.wholeWords("logic", part = true))
        assertEquals("이 작품 넣기", com.bamnat.app.ui.recipe.wholeWords("tab", part = false))
        assertEquals("이 작품 그대로 넣기", com.bamnat.app.ui.recipe.wholeWords("tab", part = false, plain = true))
        assertEquals("이 작품 넣기", com.bamnat.app.ui.recipe.wholeWords("logic", part = false))
    }
}
