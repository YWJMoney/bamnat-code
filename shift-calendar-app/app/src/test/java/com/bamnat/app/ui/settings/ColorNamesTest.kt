package com.bamnat.app.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PC 확인 W33 1.36: colours in a row read apart in words for 화면 읽기. */
@RunWith(AndroidJUnit4::class)
class ColorNamesTest {
    @Test fun lavenderAndSlateAreNotLightBlue() {
        assertEquals("연한 파랑", colorName(0xFFD9E7FF))
        assertEquals("연한 보라", colorName(0xFFE6DDFF))
        assertEquals("회색빛 파랑", colorName(0xFF8E9AAF))
        assertEquals("아주 짙은 파랑", colorName(0xFF0F1220))
    }

    @Test fun twoAlikeAreLighterAndDeeper() {
        val n = colorNames(listOf(0xFFFFFFFF, 0xFFF7F8FC, 0xFF2F5BEA))
        assertEquals(listOf("더 밝은 흰색", "더 짙은 흰색", "파랑"), n)
    }

    @Test fun everyNameInARowIsDifferent() {
        val row = listOf(0xFFFFFFFF, 0xFFF7F8FC, 0xFFFFF8E1, 0xFFFFF4CC, 0xFFFFE2CC, 0xFFFFDDE4, 0xFFE6DDFF, 0xFFD9E7FF, 0xFFCFF3EA, 0xFFE8F5D0,
            0xFFF2994A, 0xFFE5484D, 0xFFE84393, 0xFF9B51E0, 0xFF2F5BEA, 0xFF00A3C4, 0xFF00857A, 0xFF27AE60, 0xFF8E9AAF, 0xFF4A5068,
            0xFF1B1B1B, 0xFF0F1220, 0xFF1E335C, 0xFF16443B, 0xFF4D2230)
        val n = colorNames(row)
        assertTrue(n.toString(), n.toSet().size == n.size)
    }
}
