package com.bamnat.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.bamnat.core.layout.Contrast
import com.bamnat.core.recipe.ScreenLook
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PC 확인 C16 1.36: a very dark 화면 배경 사진 (스튜디오 색·배경 '사진 고르기') under a light page with its 30% veil. With the cards on the
 * ground's side, as on a 꾸밈 사진, the words turn light and read on the photo (before: dark words, 1.3~2.3:1).
 */
class ScreenPhotoInkTest {
    private fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL

    @Test fun darkPhotoWithDarkCardsGetsLightWords() {
        val page = LightColors.background
        val look = ScreenLook(photo = "0123456789abcdef", dim = 30)
        val avg = Color(10, 12, 25)
        val ground = lerp(avg, page, 0.3f)
        val card = lerp(page, Color.Black, 0.8f)
        val s = skinned(LightColors, look.copy(card = argb(card)), photoLight = 0.05f, photoAvg = avg)
        assertTrue(Contrast.ratio(argb(s.onSurface), argb(ground)) >= 4.5)
        assertTrue(Contrast.ratio(argb(s.onSurface), argb(card)) >= 4.5)
    }
}
