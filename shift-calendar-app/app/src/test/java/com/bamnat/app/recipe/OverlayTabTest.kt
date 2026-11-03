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
}
