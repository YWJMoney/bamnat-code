package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** [MKX-02] A tab laid on a theme is not the user's own change: taking the theme off keeps no '내가 고친 …'. */
@RunWith(AndroidJUnit4::class)
class OverlayTabTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    @Test fun aTabLaidOnIsNotKeptAsMineWhenTheThemeComesOff() = runBlocking {
        val theme = BuiltInThemes.of(WorkStyle.SHIFT).first()
        assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
        val money = Recipe("돈 탭", styles = WorkStyle.entries.toSet(), pieces = listOf(
            Piece(PieceKind.SCREEN_LOOK, mapOf("screens" to ScreenLooks().with("money", ScreenLook(bg = 0xFF102030)).encode()))))
        assertTrue(c.wardrobe.overlay(money, "ABCD2345"))
        assertEquals(1, c.wardrobe.overlays.value.size)
        c.wardrobe.takeOff()
        assertTrue(c.wardrobe.lease.value.history.none { it.own && it.title.startsWith("내가 고친") })
    }

    /** PC 확인 5c21f3bb: a pay tab laid on with 목표 급여 150만 — its formulas read 150만, not the maker's 250만, also after another theme. */
    @Test fun aTabLaidOnKeepsItsAnswers() = runBlocking {
        val goal = Recipe("목표 급여 진행", styles = WorkStyle.entries.toSet(),
            inputs = listOf(com.bamnat.core.recipe.Input("i1", "목표 급여", com.bamnat.core.recipe.InputKind.MONEY, default = 2_500_000L)),
            pieces = listOf(Piece(PieceKind.PARTS, mapOf("parts" to listOf(mapOf("id" to "pay.total", "fill" to 4280825235L),
                mapOf("id" to "pay.total", "when" to "이달급여 >= 칸(\"목표 급여\", 2500000)", "paint" to "lg.120.FFFFD54F,FFFFA000"))))))
        assertTrue(c.wardrobe.overlay(goal, "GOAL2345", mapOf("i1" to 1_500_000L)))
        assertEquals(1_500_000L, (overlayInputs(c.wardrobe.overlays.value)["목표 급여"] as Number).toLong())
        val parts = com.bamnat.core.recipe.Blueprint.decode(c.settingsRepository.current.parts)
        assertTrue(parts.lines.any { it["id"] == "pay.total" && it["when"] != null })
        val theme = BuiltInThemes.of(WorkStyle.SHIFT).first()
        assertTrue(c.wardrobe.wear(theme, "builtin:${theme.title}", own = false))
        assertEquals(1_500_000L, (overlayInputs(c.wardrobe.overlays.value)["목표 급여"] as Number).toLong())
    }
}
