package com.bamnat.core.recipe

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MAT-01..07] 1.36 꾸미기 재료: the words read back as written, and the app and the server know the same fonts. */
class MaterialsTest {
    @Test
    fun everyPresetFillWritesWhatItReads() {
        Fill.PRESETS.forEach { (name, f) ->
            assertNull(name, Fill.problem(f.encode()))
            assertEquals(name, f, Fill.decode(f.encode()))
        }
    }

    @Test
    fun stopsWithoutAPlaceShareTheRoomEvenly() {
        val g = Fill.decode("lg.90.FF000000,FF111111@80,FF222222,FF333333") as Fill.Linear
        val p = g.places()
        assertEquals(0f, p[0], 0.001f)
        assertEquals(0.8f, p[1], 0.001f)
        assertEquals(0.9f, p[2], 0.001f)
        assertEquals(1f, p[3], 0.001f)
        // A place that goes back is held at the one before, so the gradient always rises.
        assertEquals(listOf(0f, 0.6f, 0.6f), (Fill.decode("lg.0.FF000000@0,FF111111@60,FF222222@30") as Fill.Linear).places())
    }

    @Test
    fun aFillReadsAsItsAverageColour() {
        assertEquals(0xFF808080L, Fill.decode("lg.0.FF000000,FFFFFFFF")!!.average)
        val pattern = Fill.Pattern("dots", 0xFF000000L, 0xFFFFFFFFL)
        assertTrue(Palette.luminance(pattern.average) > 0.5)
        val s = Fill.Shader("aurora", listOf(0xFF0D47A1L, 0xFF00BFA5L, 0xFF7C4DFFL))
        assertEquals(s.colors, s.still().stops.map { it.color })
    }

    @Test
    fun aStrokeWritesWhatItReads() {
        val s = Stroke("dash", 15, 0xFF3F51B5L, 80)
        assertEquals("dash.15.FF3F51B5.80", s.encode())
        assertEquals(s, Stroke.decode(s.encode()))
        assertEquals(1.5f, s.widthDp, 0.001f)
    }

    @Test
    fun photoAdjustRoundTripsAndTheIdentityLeavesAPictureAlone() {
        val a = PhotoAdjust(bright = 20, contrast = -10, saturate = 30, gray = true, tint = 0xFF5C6BC0L, tintAmount = 40, blur = 8, blend = "multiply")
        assertEquals(a, PhotoAdjust.decode(a.encode()))
        assertEquals("", PhotoAdjust().encode())
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertArrayEquals(id, PhotoAdjust().matrix(), 0.0001f)
        // 흑백: every channel takes the same mix of red, green and blue.
        val g = PhotoAdjust(gray = true).matrix()
        assertEquals(g[0], g[5], 0.0001f)
        assertEquals(g[1], g[11], 0.0001f)
        PhotoAdjust.PRESETS.forEach { (n, p) -> assertNull(n, PhotoAdjust.problem(p.encode())) }
    }

    @Test
    fun aLettersFrameKeepsItsWord() {
        val f = PhotoFrame.ofLetters("밤 낮")!!
        assertNull(PhotoFrame.problem(f))
        assertEquals("밤낮", PhotoFrame.letters(f))
        assertNull(PhotoFrame.letters("star"))
        assertNull(PhotoFrame.ofLetters("  "))
    }

    @Test
    fun motionStopsWhenThePhoneSaysSo() {
        val m = Motion(turn = "flip", today = "pulse", speed = "f")
        assertEquals(m, Motion.decode(m.encode()))
        assertEquals("none", m.effective(systemOff = true).turn)
        assertEquals("none", m.copy(off = true).effective(systemOff = false).today)
        assertEquals(m, m.effective(systemOff = false))
        assertEquals(200, m.millis(300))
    }

    @Test
    fun theOlderLookLinesCarryTheNewParts() {
        val cell = CellLook(fill = Fill.PRESETS.first().second, borderWidth = 25, borderAlpha = 60, dateFont = "jua", border = "thin")
        assertEquals(cell, CellLook.decode(cell.encode()))
        assertNull(CellLook.looksProblem(CellLook.encodeAll(listOf(cell, CellLook(fill = Fill.Glass(0x66FFFFFFL))))))
        val screen = ScreenLook(fill = Fill.Radial(50, 20, 120, listOf(Fill.Stop(0xFF3949ABL), Fill.Stop(0xFF0D1333L))),
            adjust = PhotoAdjust(gray = true), font = "gowundodum", photo = "0123456789abcdef")
        assertEquals(screen, ScreenLook.decode(screen.encode()))
        assertNull(ScreenLooks.problem(ScreenLooks(mapOf("all" to screen)).encode()))
        // A screen's own plain colour hides the whole app's fill, as it hides its picture.
        assertNull(ScreenLook(bg = 0xFFFFFFFFL).over(screen).fill)
        assertEquals("gowundodum", ScreenLook(bg = 0xFFFFFFFFL).over(screen).font)
        assertEquals(screen.fill?.average?.let { it or 0xFF000000L }, screen.ground)
    }

    @Test
    fun anUnknownFontFallsBackToAnAppFont() {
        assertEquals("jua", FontCatalog.resolve("jua", emptySet()))
        assertEquals("u0123456789abcdef", FontCatalog.resolve("u0123456789abcdef", setOf("u0123456789abcdef")))
        assertEquals("hand", FontCatalog.resolve("u0123456789abcdef", emptySet()) { "hand" })
        assertEquals("sans", FontCatalog.resolve("u0123456789abcdef", emptySet()))
        assertEquals("sans", FontCatalog.resolve(null, emptySet()))
        assertEquals(FontCatalog.IDS.size, FontCatalog.IDS.distinct().size)
        assertTrue("공개 글꼴을 많이: ${FontCatalog.ALL.size}", FontCatalog.ALL.size >= 60)
    }

    @Test
    fun theServerKnowsTheSameFonts() {
        val js = File("../server/company-worker/src/materials.js").readText()
        val list = js.substringAfter("export const FONT_IDS = [").substringBefore("];")
        val ids = Regex("\"([a-z0-9]+)\"").findAll(list).map { it.groupValues[1] }.toList()
        assertEquals(FontCatalog.IDS, ids)
        Fill.PATTERNS.forEach { assertTrue(it, js.contains("\"$it\"")) }
        Fill.TEXTURES.forEach { assertTrue(it, js.contains("\"$it\"")) }
        Fill.SHADERS.forEach { assertTrue(it, js.contains("\"$it\"")) }
    }
}
