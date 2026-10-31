package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [STU-30][STU-31][STU-32] 한 색으로 앱 전체: every seed, variant and shuffle reads well; a picture's standout colours come out first. */
class ThemeSeedTest {
    private val seeds = listOf(0xFF4F6BEDL, 0xFFE91E63L, 0xFFFFEB3BL, 0xFF00C853L, 0xFF795548L, 0xFF000000L, 0xFFFFFFFFL, 0xFF808080L, 0xFF00BCD4L, 0xFFFF6D00L)

    @Test fun everySchemeReads() {
        for (seed in seeds) for (v in ThemeSeed.Variant.entries) for (dark in listOf(false, true)) for (i in ThemeSeed.SHUFFLES.indices) {
            val s = ThemeSeed.shuffle(seed, v, dark, i)
            val what = "${seed.toString(16)} $v dark=$dark #$i"
            assertTrue("$what 글자/바탕", Palette.contrast(s.ink, s.bg) >= 4.5)
            assertTrue("$what 글자/카드", Palette.contrast(s.ink, s.card) >= 4.5)
            assertTrue("$what 강조/바탕 ${Palette.contrast(s.accent, s.bg)}", Palette.contrast(s.accent, s.bg) >= 3.0)
            assertTrue("$what 토/바탕", Palette.contrast(s.sat, s.bg) >= 3.0)
            assertTrue("$what 일/바탕", Palette.contrast(s.sun, s.bg) >= 3.0)
            assertEquals(dark, Palette.luminance(s.bg) < 0.18)
            listOf(s.bg, s.card, s.ink, s.accent, s.sat, s.sun).forEach { assertEquals("$what 불투명", 0xFF000000L, it and 0xFF000000L) }
        }
    }

    @Test fun shufflesDifferAndWrap() {
        val all = ThemeSeed.SHUFFLES.indices.map { ThemeSeed.shuffle(0xFF4F6BEDL, ThemeSeed.Variant.SOFT, false, it) }
        assertEquals("섞기마다 다른 조합", all.size, all.toSet().size)
        assertEquals(all[0], ThemeSeed.shuffle(0xFF4F6BEDL, ThemeSeed.Variant.SOFT, false, ThemeSeed.SHUFFLES.size))
        assertEquals(all[0], ThemeSeed.scheme(0xFF4F6BEDL))
    }

    @Test fun variantsDiffer() {
        val v = ThemeSeed.Variant.entries.map { ThemeSeed.scheme(0xFFE91E63L, it).accent }
        assertEquals(v.size, v.toSet().size)
        val mono = ThemeSeed.scheme(0xFFE91E63L, ThemeSeed.Variant.MONO)
        assertTrue("흑백 바탕은 색이 없어요", ThemeSeed.hsl(mono.bg).second < 0.02)
    }

    @Test fun hslRoundTrips() {
        for (c in seeds + listOf(0xFF123456L, 0xFFABCDEFL)) {
            val (h, s, l) = ThemeSeed.hsl(c)
            val back = ThemeSeed.fromHsl(h, s, l)
            for (sh in listOf(16, 8, 0)) assertTrue(kotlin.math.abs(((c shr sh) and 0xFF) - ((back shr sh) and 0xFF)) <= 1)
        }
    }

    @Test fun pictureColoursMostPresentFirst() {
        val red = 0xFFE53935.toInt()
        val blue = 0xFF1E88E5.toInt()
        val grey = 0xFF9E9E9E.toInt()
        val px = IntArray(1000) { i -> when { i < 500 -> grey; i < 800 -> blue; else -> red } }
        val got = ThemeSeed.fromPixels(px)
        assertEquals(2, got.size)
        assertEquals(blue.toLong() and 0xFFFFFFFFL, got[0])
        assertEquals(red.toLong() and 0xFFFFFFFFL, got[1])
        val dull = ThemeSeed.fromPixels(IntArray(100) { grey })
        assertEquals(1, dull.size)
        assertNotEquals(0L, dull[0])
        assertTrue(ThemeSeed.fromPixels(IntArray(0)).size == 1)
    }
}
