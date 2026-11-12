package com.bamnat.app.ui.parts

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bamnat.app.ui.theme.MovingShaders
import com.bamnat.core.layout.Contrast
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.PartFloor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-06] 그릴 때 지키는 바닥선, the app's side (검토 SN-05·SN-03·SN-12(1), 디자인 품질 조건 12·14·15): a look's 앱 바탕 brings the
 * helper letters, lines and fields that read on it; a 단추's letters read on its fill; a screen of moving fills moves at most two.
 */
class PartFloorDrawTest {
    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    @Test fun aNewGroundBringsColoursThatReadOnIt() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            for (ground in listOf(0xFF0B1E3AL, 0xFFFFF4B0L, 0xFF7A1F5CL)) {
                val s = PartTheme.colors(base, Blueprint(listOf(mapOf("id" to "app", "fill" to ground))))
                val g = s.background.argb()
                assertEquals(ground, g)
                // Before: onSurfaceVariant and outline stayed the app's own grey, unread on a dark ground.
                assertTrue("보조 글자 on $ground", Contrast.ratio(s.onSurfaceVariant.argb(), g) >= PartFloor.BODY)
                assertTrue("글자 on $ground", Contrast.ratio(s.onBackground.argb(), g) >= PartFloor.BODY)
                assertTrue("테두리 on $ground", Contrast.ratio(s.outline.argb(), g) >= PartFloor.SMALL)
                assertTrue("입력 칸은 바탕과 조금 달라요", s.surfaceContainerHighest.argb() != g)
            }
        }
    }

    @Test fun aButtonsLettersReadOnItsFill() {
        // Letters the colour of the fill (RCP-18 '글자색=바탕색').
        val s = PartTheme.colors(lightColorScheme(), Blueprint(listOf(mapOf("id" to "app.button", "fill" to 0xFFFFD54FL, "ink" to 0xFFFFE082L))))
        assertTrue(Contrast.ratio(s.onPrimary.argb(), s.primary.argb()) >= PartFloor.BODY)
    }

    @Test fun aLookWithoutAGroundLeavesTheAppsColours() {
        val base = lightColorScheme()
        val s = PartTheme.colors(base, Blueprint(listOf(mapOf("id" to "memo.card", "fill" to 0xFF000000L))))
        assertEquals(base.background, s.background)
        assertEquals(base.outline, s.outline)
    }

    @Test fun atMostTwoFillsMoveAtOnce() {
        val a = Any(); val b = Any(); val c = Any()
        assertTrue(MovingShaders.take(a))
        assertTrue(MovingShaders.take(b))
        assertTrue("셋째는 멈춘 그림", !MovingShaders.take(c))
        assertTrue("이미 움직이는 것은 그대로", MovingShaders.take(a))
        MovingShaders.give(a)
        assertTrue("하나가 화면을 떠나면 다음 것이 움직여요", MovingShaders.take(c))
        MovingShaders.give(b); MovingShaders.give(c)
        assertEquals(0, MovingShaders.moving)
    }
}
