package com.bamnat.app.ui.decor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bamnat.core.layout.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PC 확인 C12 1.36: on a 꾸밈 사진, 토·일 숫자와 '알림 더 보기' keep their colour, made lighter or darker until they read 3:1. */
class ReadableOnPhotoTest {
    private fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL
    private fun ratio(a: Color, b: Color) = Contrast.ratio(argb(a), argb(b))

    @Test fun blueOnTheGreyVeilTurnsLighterBlue() {
        val veil = Color(104, 106, 117)
        val cell = Color(51, 51, 51)
        val blue = Color(47, 91, 234)
        val out = readableOn(blue, listOf(veil, cell))
        assertTrue(ratio(out, veil) >= 3.0 && ratio(out, cell) >= 3.0)
        assertTrue("파랑 그대로 밝게", out.blue > out.red)
    }

    @Test fun aColourThatAlreadyReadsStays() {
        val red = Color(0xFFD32F2F)
        assertEquals(red, readableOn(red, listOf(Color.White)))
    }
}
