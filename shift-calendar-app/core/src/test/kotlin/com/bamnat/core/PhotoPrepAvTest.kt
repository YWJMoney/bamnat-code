package com.bamnat.core

import com.bamnat.core.imports.PhotoQuality
import com.bamnat.core.imports.PhotoVariants
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * [PHOTO-20][PHOTO-21][PHOTO-25] 1.30 AV: what the app does to a photo before reading it, as plain arithmetic: small letters
 * worked at the photo's size and read bigger, a monitor's moiré told from print, a photo read on its side told, a slanted or
 * keystoned photo told (and the scanner's page not).
 */
class PhotoPrepAvTest {
    /** A page of print: rows of letters made of strokes, on paper of [paper]; with [moire], a screen's fine dots over it. */
    private fun page(w: Int, h: Int, moire: Boolean, seed: Int, paper: Int = 225): PhotoVariants.Gray {
        val r = Random(seed)
        val px = IntArray(w * h) { paper }
        var y = 20
        while (y + 18 < h) {
            var x = 10 + r.nextInt(10)
            while (x + 14 < w) {
                // A letter: two to four strokes in a 12 × 16 cell.
                repeat(2 + r.nextInt(3)) {
                    if (r.nextBoolean()) { val sx = x + r.nextInt(12); for (yy in y until y + 16) for (xx in sx until minOf(sx + 2, w)) px[yy * w + xx] = 40 }
                    else { val sy = y + r.nextInt(16); for (xx in x until x + 12) for (yy in sy until minOf(sy + 2, h)) px[yy * w + xx] = 40 }
                }
                x += 14 + if (r.nextInt(6) == 0) 10 else 0
            }
            y += 30
        }
        if (moire) for (yy in 0 until h) for (xx in 0 until w) {
            val v = 14 * sin(2 * PI * xx / 3.6 + yy * 0.3)
            px[yy * w + xx] = (px[yy * w + xx] + v).toInt().coerceIn(0, 255)
        }
        return PhotoVariants.Gray(w, h, px)
    }

    @Test fun aScreensMoireIsToldFromPrint() {
        for (seed in 0 until 5) {
            val print = PhotoVariants.periodic(page(480, 480, false, seed))
            val screen = PhotoVariants.periodic(page(480, 480, true, seed))
            println("주기성 인쇄 %.3f · 화면 %.3f".format(print, screen))
            assertTrue("print $print", print < PhotoVariants.MOIRE)
            assertTrue("screen $screen", screen >= PhotoVariants.MOIRE)
        }
        assertEquals(0.0, PhotoVariants.periodic(PhotoVariants.Gray(480, 480, IntArray(480 * 480) { 230 })), 0.0)
    }

    /** How much the paper ripples from pixel to pixel along the rows (the mean square of the second step), letters left out. */
    private fun ripple(g: PhotoVariants.Gray): Double {
        var sum = 0.0
        var n = 0
        val paper = g.px.sorted()[g.px.size * 3 / 4]
        for (y in 0 until g.h) for (x in 0 until g.w - 2) {
            val a = g[x, y]; val b = g[x + 1, y]; val c = g[x + 2, y]
            if (minOf(a, b, c) < paper - 60) continue
            val d = b - (a + c) / 2.0
            sum += d * d; n++
        }
        return sum / n.coerceAtLeast(1)
    }

    /** Smoothed first: the moiré's dots no longer land between the digits of the made-over picture. */
    @Test fun aScreenIsSmoothedBeforeEachWay() {
        val g = page(240, 120, true, 1)
        val plain = PhotoVariants.make(PhotoVariants.Kind.CONTRAST, g)
        val smoothed = PhotoVariants.make(PhotoVariants.Kind.CONTRAST, g, moire = true)
        // Every made-over picture of a screen is made from the smoothed one: the dots' ripple a fifth of what it was or less.
        assertFalse(smoothed.px.contentEquals(plain.px))
        val soft = PhotoVariants.soft(g)
        assertTrue("${ripple(soft)} vs ${ripple(g)}", ripple(soft) * 5 < ripple(g))
        assertTrue(PhotoVariants.make(PhotoVariants.Kind.CONTRAST, soft).px.contentEquals(smoothed.px))
        // The photo as taken is read as it is.
        assertTrue(PhotoVariants.make(PhotoVariants.Kind.ORIG, g, moire = true) === g)
    }

    @Test fun smallLettersAreWorkedBigger() {
        // 1.29: twice the size only under 20 px; now under 24, as big as 28 px wants, up to three times.
        assertTrue(PhotoVariants.Kind.UP2 in PhotoVariants.plan(page(200, 100, false, 1), 22))
        assertFalse(PhotoVariants.Kind.UP2 in PhotoVariants.plan(page(200, 100, false, 1), 26))
        assertEquals(2.0, PhotoVariants.upFactor(14, 1000, 4000)!!, 1e-9)
        assertEquals(3.0, PhotoVariants.upFactor(8, 1000, 4000)!!, 1e-9)
        assertEquals(1.4, PhotoVariants.upFactor(22, 1000, 4000)!!, 1e-9)
        assertEquals(2.0, PhotoVariants.upFactor(null, 1000, 3600)!!, 1e-9)
        // A table too wide to make bigger in memory: not worth it.
        assertNull(PhotoVariants.upFactor(14, 3200, 4000))
        // A photo from far off (letters 16 px on 3200) is worked at its own size; near (40 px), at 2400 as before.
        assertEquals(3200, PhotoVariants.workSide(3200, 16))
        assertEquals(2400, PhotoVariants.workSide(3200, 40))
        assertEquals(2400, PhotoVariants.workSide(3200, null))
        assertEquals(1800, PhotoVariants.workSide(1800, 10))
    }

    private fun box(text: String, x: Int, y: Int, w: Int, h: Int, angle: Float = 0f) = OcrLine(text, x, y, x + w, y + h, angle)

    @Test fun aPhotoOnItsSideIsTold() {
        // Read upright: words wider than tall.
        val upright = (0 until 20).map { box("기본급여", 100, 50 + it * 40, 120, 30) }
        assertFalse(OcrRows.sideways(upright))
        // On its side: a few letters in boxes standing up, or lines the reader says stand at 90°.
        assertTrue(OcrRows.sideways((0 until 5).map { box("기본", 100 + it * 40, 50, 30, 90) }))
        assertTrue(OcrRows.sideways((0 until 12).map { box("지급내역", 100, 50 + it * 140, 120, 30, 89f) }))
        assertTrue(OcrRows.sideways(listOf(box("a", 1, 1, 5, 5))))
    }

    @Test fun aSlantAndAKeystoneAreTold() {
        val level = PlantSample.payslip(PlantSample.Noise.CLEAN, Random(1))
        val (tilt0, key0) = OcrRows.lean(level)
        assertEquals(0.7, tilt0, 0.2)
        assertTrue("$key0", key0 < 0.8)
        val side = PlantSample.payslip(PlantSample.Noise.CLEAN.copy(perspective = 0.12, tilt = 3.0), Random(1))
        val (tilt, key) = OcrRows.lean(side)
        println("기울기 %.2f°, 위아래 차이 %.2f°".format(tilt, key))
        assertEquals(3.7, tilt, 1.0)
        assertTrue("$key", key > 1.5)
        val stats = PhotoVariants.Stats(200.0, 150, 20.0, 0.1)
        val taken = PhotoQuality.of(stats, 30, 200, tilt, keystone = key)
        assertTrue(PhotoQuality.Issue.KEYSTONE in taken.issues)
        // The scanner's page: straightened, nothing about slant, a little more trusted.
        val scanned = PhotoQuality.of(stats, 30, 200, tilt, scanned = true, keystone = key)
        assertTrue(scanned.issues.none { it == PhotoQuality.Issue.KEYSTONE || it == PhotoQuality.Issue.TILTED })
        assertTrue(scanned.score > taken.score)
    }
}
