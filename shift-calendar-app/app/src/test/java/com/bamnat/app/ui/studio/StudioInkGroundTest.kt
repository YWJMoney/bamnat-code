package com.bamnat.app.ui.studio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.theme.DarkColors
import com.bamnat.app.ui.theme.LightColors
import com.bamnat.app.ui.theme.skinned
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.ScreenLook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVS-06] 검토 STU-11: the studio's '글자가 잘 안 보일 수 있어요' check and the screen skin read the same grounds
 * ([Palette.groundsFor]), on the light and on the dark screen. Before, the check used one light colour (0xFFF7F8FC) for every look, so
 * black letters on a dark phone were not flagged (the skin turned them white) and a pale card was not looked at.
 */
@RunWith(AndroidJUnit4::class)
class StudioInkGroundTest {
    private fun Color.argb() = toArgb().toLong() and 0xFFFFFFFFL

    private val inks = listOf(0xFF000000L, 0xFFFFFFFFL, 0xFF1B5E20L, 0xFFFFE0B2L, 0xFF888888L)
    private val pages = listOf(null, 0xFFFFFFFFL, 0xFF121212L, 0xFFFFF4B0L, 0xFF1A237EL)
    private val cards = listOf(null, 0xFFFFFFFFL, 0xFF1E1E1EL, 0xFFFFD54FL)

    /** The check says 'would not read' exactly when the skin changes the letters' colour. */
    @Test fun theCheckAgreesWithWhatTheSkinDraws() {
        var flagged = 0
        for (dark in listOf(false, true)) {
            val base = if (dark) DarkColors else LightColors
            for (ink in inks) for (page in pages) for (card in cards) {
                val look = ScreenLook(bg = page, card = card, ink = ink)
                val grounds = Palette.groundsFor(look, base.background.argb(), base.surfaceContainerLow.argb(), base.surfaceContainerHigh.argb())
                val flags = Palette.inkOn(ink, *grounds) != ink
                val drawn = skinned(base, look).onBackground.argb()
                assertEquals("${if (dark) "어두운" else "밝은"} 화면 · 글자 ${ink.toString(16)} 바탕 ${page?.toString(16)} 카드 ${card?.toString(16)}", flags, drawn != ink)
                if (flags) flagged++
            }
        }
        // Not vacuous: plenty of the mixes are flagged, plenty are not.
        assert(flagged in 20..(2 * inks.size * pages.size * cards.size - 20)) { "flagged $flagged" }
    }

    /** Black letters on the app's own dark screen were never flagged (one light ground); they are now. */
    @Test fun blackOnTheDarkScreenIsFlagged() {
        val look = ScreenLook(ink = 0xFF000000L)
        val dark = Palette.groundsFor(look, DarkColors.background.argb(), DarkColors.surfaceContainerLow.argb(), DarkColors.surfaceContainerHigh.argb())
        val light = Palette.groundsFor(look, LightColors.background.argb(), LightColors.surfaceContainerLow.argb(), LightColors.surfaceContainerHigh.argb())
        assert(Palette.inkOn(look.ink, *dark) != look.ink)
        assertEquals(look.ink, Palette.inkOn(look.ink, *light))
    }
}
