package com.bamnat.app.recipe

import com.bamnat.app.recipe.MarketMood.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [MKD-45] 테마 분위기: 목록이 보내는 테마 색으로 이 폰에서 셈해요. */
class MarketMoodTest {
    private fun look(bg: Long, vararg days: Long) = ThemeSwatch(bg = bg, days = days.map { ThemeSwatch.Day(it, "주", null, null) })

    @Test fun darkAndVivid() {
        val m = MarketMood.of(look(0xFF101018, 0xFFE53935, 0xFF1E88E5, 0xFFFDD835))
        assertTrue(m.toString(), Mood.DARK in m && Mood.VIVID in m)
    }

    @Test fun lightPastel() {
        val m = MarketMood.of(look(0xFFFFFBF5, 0xFFFFCDD2, 0xFFBBDEFB, 0xFFC8E6C9))
        assertTrue(m.toString(), Mood.LIGHT in m && Mood.PASTEL in m)
    }

    @Test fun calmGreys() {
        val m = MarketMood.of(look(0xFFF5F5F5, 0xFF78909C, 0xFF8D8D8D, 0xFF6D6D72))
        assertTrue(m.toString(), Mood.CALM in m)
    }

    @Test fun noColoursNoMood() {
        assertEquals(emptySet<Mood>(), MarketMood.of(null))
        assertEquals(emptySet<Mood>(), MarketMood.of(ThemeSwatch.example(null)))
    }
}
