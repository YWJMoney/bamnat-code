package com.bamnat.app.pay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.imports.ImagePrep
import com.bamnat.core.imports.PhotoVariants
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * [PHOTO-08] 1.29 AQ: a payslip photo made here as a PNG (uneven light, a screen's dot pattern, a slanted pale watermark,
 * amounts half a row under their names), taken apart at each step the phone takes: the made-over pictures stay within
 * memory and map back to the photo, black and white keeps the letters and does not turn the dot pattern into specks,
 * and the words each picture would give, put back on the photo, read as the payslip it is. ML Kit does not run on the
 * JVM, so the words are the ones drawn; what this checks is everything around the reading.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PayslipImageTest {
    private val earn = listOf(
        "기본급" to "2,397,500", "자격수당" to "57,500", "상여금" to "2,555,830",
        "휴일근로수당" to "2,626,592", "야간근무수당" to "691,778",
    )
    private val ded = listOf("소득세" to "1,731,400", "주민세" to "173,130", "건강보험" to "280,730", "국민연금" to "313,020")
    private val gross = earn.sumOf { it.second.replace(",", "").toLong() }
    private val dedSum = ded.sumOf { it.second.replace(",", "").toLong() }

    /** The words as drawn, at the photo's pixels: names at x 500 / 1700, amounts right-aligned 900 px on, 30 px lower. */
    private fun words(): List<OcrLine> = buildList {
        fun cell(t: String, cx: Int, y: Int) { val w = t.length * 44; add(OcrLine(t, cx - w / 2, y - 28, cx + w / 2, y + 28)) }
        cell("지급총액", 400, 200); cell(String.format("%,d", gross), 900, 260)
        cell("공제총액", 1500, 200); cell(String.format("%,d", dedSum), 2000, 260)
        cell("지급내역", 500, 420); cell("공제내역", 1700, 420)
        (earn + ("지급계" to String.format("%,d", gross))).forEachIndexed { i, (n, a) -> cell(n, 500, 560 + i * 140); cell(a, 1200, 590 + i * 140) }
        (ded + ("공제계" to String.format("%,d", dedSum))).forEachIndexed { i, (n, a) -> cell(n, 1700, 560 + i * 140); cell(a, 2400, 590 + i * 140) }
    }

    /** The photo: drawn, saved as a PNG and opened again, as the camera's file would be. */
    private fun photo(words: List<OcrLine>): Bitmap {
        val b = Bitmap.createBitmap(3000, 1800, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        // Light falling off to the right and bottom.
        for (x in 0 until 3000 step 20) for (y in 0 until 1800 step 20) {
            val v = (245 - x / 60 - y / 50).coerceIn(150, 255)
            c.drawRect(x.toFloat(), y.toFloat(), x + 20f, y + 20f, Paint().apply { color = Color.rgb(v, v, v) })
        }
        // A screen's dot pattern, every 4 px.
        val dot = Paint().apply { color = Color.argb(60, 0, 0, 0) }
        for (x in 0 until 3000 step 4) for (y in 0 until 1800 step 4) c.drawRect(x.toFloat(), y.toFloat(), x + 1.5f, y + 1.5f, dot)
        // A pale watermark at 30°.
        c.save(); c.rotate(-30f, 1500f, 900f)
        c.drawText("XX Chem abcde 10.11.222.33", 600f, 900f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0); textSize = 120f })
        c.restore()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 20); textSize = 48f }
        words.forEach { w -> c.drawText(w.text, w.left.toFloat(), w.bottom - 8f, ink) }
        val png = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        b.recycle()
        return BitmapFactory.decodeByteArray(png, 0, png.size)
    }

    private fun dark(b: Bitmap, r: Rect): Double {
        var n = 0; var d = 0
        for (y in r.top until r.bottom step 2) for (x in r.left until r.right step 2) { n++; if (Color.red(b.getPixel(x, y)) < 110) d++ }
        return d.toDouble() / n
    }

    @Test fun madeOverPicturesKeepTheLettersAndMapBack() {
        val ws = words()
        val base = photo(ws)
        val letter = PhotoVariants.median(ws.map { it.height })
        val (_, kinds) = ImagePrep.plan(base, letter)
        assertTrue("$kinds", PhotoVariants.Kind.ORIG in kinds)
        val margin = Rect(2700, 100, 2950, 400)
        val name = ws.first { it.text == "기본급" }
        for (kind in listOf(PhotoVariants.Kind.FLAT, PhotoVariants.Kind.CONTRAST, PhotoVariants.Kind.BINARY)) {
            val v = ImagePrep.variant(base, kind)
            assertNotNull(kind.name, v)
            v!!
            assertTrue("$kind 크기", max(v.bitmap.width, v.bitmap.height) <= ImagePrep.WORK_SIDE)
            // The picture's far corner is the photo's far corner.
            assertTrue("$kind 되돌림", abs(v.x(v.bitmap.width) - base.width) <= 2 && abs(v.y(v.bitmap.height) - base.height) <= 2)
            if (kind == PhotoVariants.Kind.BINARY) {
                fun onV(r: Rect) = Rect((r.left / v.toBase).toInt(), (r.top / v.toBase).toInt(), (r.right / v.toBase).toInt(), (r.bottom / v.toBase).toInt())
                val letters = dark(v.bitmap, onV(Rect(name.left, name.top, name.right, name.bottom)))
                val dots = dark(v.bitmap, onV(margin))
                assertTrue("글자가 남아야 해요: $letters", letters > 0.08)
                assertTrue("점 무늬가 얼룩이 되면 안 돼요: $dots", dots < 0.03)
            }
            v.bitmap.recycle()
        }
        // Twice the size holds only the words' part and maps back onto it.
        val box = Rect(ws.minOf { it.left }, ws.minOf { it.top }, ws.maxOf { it.right }, ws.maxOf { it.bottom })
        val up = ImagePrep.variant(base, PhotoVariants.Kind.UP2, box)
        if (up != null) {
            assertTrue(up.x(0) <= box.left && up.y(0) <= box.top)
            assertTrue(up.x(up.bitmap.width) >= box.right - 2 && up.y(up.bitmap.height) >= box.bottom - 2)
            up.bitmap.recycle()
        }
    }

    @Test fun eachPicturesWordsPutBackReadAsThePayslip() {
        val ws = words()
        val base = photo(ws)
        val box = Rect(ws.minOf { it.left }, ws.minOf { it.top }, ws.maxOf { it.right }, ws.maxOf { it.bottom })
        val readings = mutableListOf(ws)
        for (kind in listOf(PhotoVariants.Kind.BINARY, PhotoVariants.Kind.UP2)) {
            val v = ImagePrep.variant(base, kind, box) ?: continue
            // What ML Kit would give on that picture: the same words at its own pixels.
            val seen = ws.map { w ->
                fun bx(x: Int) = ((x - v.left) / v.toBase).toInt()
                fun by(y: Int) = ((y - v.top) / v.toBase).toInt()
                w.copy(left = bx(w.left), top = by(w.top), right = bx(w.right), bottom = by(w.bottom))
            }
            val back = PayslipScanner.onPhoto(seen, v)
            back.zip(ws).forEach { (b, w) -> assertTrue("${w.text} 위치", abs(b.left - w.left) <= 3 && abs(b.top - w.top) <= 3) }
            readings += back
            v.bitmap.recycle()
        }
        val scan = PayslipScanner.put(readings.map { OcrRows.join(it) }, ImagePrep.forScreen(base).first, null, readings)
        val read = scan.slip.read
        (earn + ded).forEach { (n, a) ->
            assertEquals(n, a.replace(",", "").toLong(), read.lines.firstOrNull { it.name == n }?.amount)
        }
        assertEquals(gross, read.gross)
        assertTrue("합계가 맞으니 물을 것이 없어요: ${scan.slip.unsure}", scan.slip.sure)
    }
}
