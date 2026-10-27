package com.bamnat.core.imports

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * [READ-13] 1.27 순번 446 (사용자 2026-10-25 "사진 인식 기술을 비약적으로 향상"): a photo made over in several ways before it is
 * read, all on the phone (결정 D68). One reading of a dim, shaded or screen photo loses lone letters (D·E·N·O) and the last
 * digits of an amount; the same photo with its paper's light evened out, or turned black and white, or twice the size,
 * loses others. Each is read, and the readings vote cell by cell ([RosterVote], [com.bamnat.core.pay.SlipVote],
 * [com.bamnat.core.invest.FillVote]): a cell read alike twice or more is sure, one read differently is asked in yellow.
 *
 * Pure arithmetic on grey pixels (0~255, row by row), so it is tested on a computer and the app only moves pixels in and
 * out of a Bitmap. `tools/photo_bench.py` does the same in numpy for the bench photos (research/1.27-AJ-사진인식-조사.md 3장).
 */
object PhotoVariants {
    /** A grey picture, [w] × [h], one value 0~255 a pixel. */
    class Gray(val w: Int, val h: Int, val px: IntArray) {
        init { require(px.size == w * h) }
        operator fun get(x: Int, y: Int): Int = px[y * w + x]
    }

    /** What a variant is called in the bench and in the app's log of tries. */
    enum class Kind { ORIG, CONTRAST, FLAT, BINARY, UP2, INVERT }

    /** Grey from packed ARGB (as `Bitmap.getPixels` gives): the eye's weights 0.299·0.587·0.114. */
    fun gray(argb: IntArray, w: Int, h: Int): Gray {
        val out = IntArray(w * h)
        for (i in out.indices) {
            val c = argb[i]
            out[i] = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return Gray(w, h, out)
    }

    /** Back to packed ARGB, opaque. */
    fun argb(g: Gray): IntArray = IntArray(g.px.size) { i -> val v = g.px[i]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }

    /** Darker darks and lighter lights around the middle: 1.25's second look. */
    fun contrast(g: Gray, c: Double = 1.6): Gray = Gray(g.w, g.h, IntArray(g.px.size) { i -> clamp(((g.px[i] - 128) * c + 128).toInt()) })

    /** The block the paper's brightness is measured in: about 64 along the long side, never under 8 pixels. */
    fun block(w: Int, h: Int): Int = max(8, max(w, h) / 64)

    /**
     * The paper's brightness around each pixel: the brightest pixel of each block (letters are darker than the paper),
     * smoothed twice over 3 × 3 blocks, spread back out by straight lines between block middles.
     */
    fun background(g: Gray): FloatArray {
        val k = block(g.w, g.h)
        val bw = (g.w + k - 1) / k
        val bh = (g.h + k - 1) / k
        var small = FloatArray(bw * bh)
        for (by in 0 until bh) for (bx in 0 until bw) {
            var m = 0
            for (y in by * k until min((by + 1) * k, g.h)) for (x in bx * k until min((bx + 1) * k, g.w)) m = max(m, g.px[y * g.w + x])
            // A block cut by the edge counts as the edge pixels repeated, which have the same brightest value.
            small[by * bw + bx] = m.toFloat()
        }
        repeat(2) { small = blur3(small, bw, bh) }
        val out = FloatArray(g.w * g.h)
        val sx = bw.toFloat() / g.w
        val sy = bh.toFloat() / g.h
        for (y in 0 until g.h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (bh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, bh - 1); val ty = fy - y0
            for (x in 0 until g.w) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (bw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, bw - 1); val tx = fx - x0
                val top = small[y0 * bw + x0] * (1 - tx) + small[y0 * bw + x1] * tx
                val bottom = small[y1 * bw + x0] * (1 - tx) + small[y1 * bw + x1] * tx
                out[y * g.w + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    private fun blur3(a: FloatArray, w: Int, h: Int): FloatArray = FloatArray(a.size) { i ->
        val x = i % w; val y = i / w
        var s = 0f
        for (dy in -1..1) for (dx in -1..1) s += a[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
        s / 9f
    }

    /**
     * The paper's light evened out: each pixel over the paper's brightness around it, the letters' darkness ×[gain].
     * A shadow across the table, a dim lamp or the glare of a screen leave white paper and dark letters.
     */
    fun flat(g: Gray, gain: Double = 1.6): Gray {
        val bg = background(g)
        return Gray(g.w, g.h, IntArray(g.px.size) { i ->
            val ratio = g.px[i] / max(bg[i], 24f).toDouble()
            clamp((255 * (1 - (1 - ratio) * gain)).toInt())
        })
    }

    /**
     * [PHOTO-12] 1.29 AQ: each pixel the mean of the 3 × 3 around it. A photo of a monitor (사용자 2026-10-27 화학 공장 명세서)
     * has a moiré of fine dots that black and white turned into specks all over the table; smoothed first, the specks
     * mostly go and the letters stay.
     */
    fun soft(g: Gray): Gray = Gray(g.w, g.h, IntArray(g.px.size) { i ->
        val x = i % g.w; val y = i / g.w
        var s = 0; var n = 0
        for (yy in max(0, y - 1)..min(g.h - 1, y + 1)) for (xx in max(0, x - 1)..min(g.w - 1, x + 1)) { s += g.px[yy * g.w + xx]; n++ }
        s / n
    })

    /** Black where a pixel is darker than its neighbourhood's mean by [k] (Bradley's adaptive threshold), white elsewhere. */
    fun binary(g: Gray, k: Double = 0.12): Gray {
        val win = max(15, max(g.w, g.h) / 50) or 1
        val r = win / 2
        // Summed-area table with the edges repeated, as the bench's box blur.
        val iw = g.w + 1
        val sum = LongArray(iw * (g.h + 1))
        for (y in 0 until g.h) {
            var row = 0L
            for (x in 0 until g.w) {
                row += g.px[y * g.w + x]
                sum[(y + 1) * iw + x + 1] = sum[y * iw + x + 1] + row
            }
        }
        fun area(x0: Int, y0: Int, x1: Int, y1: Int): Long = sum[y1 * iw + x1] - sum[y0 * iw + x1] - sum[y1 * iw + x0] + sum[y0 * iw + x0]
        return Gray(g.w, g.h, IntArray(g.px.size) { i ->
            val x = i % g.w; val y = i / g.w
            val x0 = max(0, x - r); val x1 = min(g.w, x + r + 1); val y0 = max(0, y - r); val y1 = min(g.h, y + r + 1)
            val mean = area(x0, y0, x1, y1).toDouble() / ((x1 - x0) * (y1 - y0))
            if (g.px[i] < mean * (1 - k)) 0 else 255
        })
    }

    /** Twice the size, by straight lines between pixels: small print read by a reader that wants letters 20+ pixels high. */
    fun up2(g: Gray): Gray {
        val w = g.w * 2; val h = g.h * 2
        return Gray(w, h, IntArray(w * h) { i ->
            val fx = ((i % w + 0.5f) / 2 - 0.5f).coerceIn(0f, (g.w - 1).toFloat())
            val fy = ((i / w + 0.5f) / 2 - 0.5f).coerceIn(0f, (g.h - 1).toFloat())
            val x0 = fx.toInt(); val x1 = min(x0 + 1, g.w - 1); val tx = fx - x0
            val y0 = fy.toInt(); val y1 = min(y0 + 1, g.h - 1); val ty = fy - y0
            val top = g[x0, y0] * (1 - tx) + g[x1, y0] * tx
            val bottom = g[x0, y1] * (1 - tx) + g[x1, y1] * tx
            clamp((top * (1 - ty) + bottom * ty + 0.5f).toInt())
        })
    }

    /** Light letters on a dark screen (다크 모드 캡처) turned dark on light. */
    fun invert(g: Gray): Gray = Gray(g.w, g.h, IntArray(g.px.size) { 255 - g.px[it] })

    /** How a photo looks, for [PhotoQuality]: brightness, the spread of light to dark, and sharpness. */
    data class Stats(val mean: Double, val spread: Int, val sharpness: Double, val darkShare: Double)

    fun stats(g: Gray): Stats {
        val hist = IntArray(256)
        g.px.forEach { hist[it]++ }
        val n = g.px.size.toDouble()
        fun pct(p: Double): Int { var c = 0.0; for (v in 0..255) { c += hist[v]; if (c >= n * p) return v }; return 255 }
        val mean = g.px.average()
        // Sharpness: the spread of the Laplacian (an edge's step), every other pixel; a blurred photo has soft steps.
        var s = 0.0; var s2 = 0.0; var m = 0
        var y = 1
        while (y < g.h - 1) {
            var x = 1
            while (x < g.w - 1) {
                val l = (4 * g[x, y] - g[x - 1, y] - g[x + 1, y] - g[x, y - 1] - g[x, y + 1]).toDouble()
                s += l; s2 += l * l; m++
                x += 2
            }
            y += 2
        }
        val sharp = if (m == 0) 0.0 else sqrt(max(0.0, s2 / m - (s / m) * (s / m)))
        return Stats(mean, pct(0.95) - pct(0.05), sharp, (0..80).sumOf { hist[it] } / n)
    }

    /** The variants worth reading for [g]: the photo, and the made-over ones its look calls for (an inverted one only for a dark screen). */
    fun plan(g: Gray, letterHeight: Int?): List<Kind> = buildList {
        add(Kind.ORIG); add(Kind.FLAT); add(Kind.CONTRAST); add(Kind.BINARY)
        // Small print: letters under [SMALL] pixels high read better made bigger (ML Kit's guidance: at least 16 pixels a letter).
        if (letterHeight != null && letterHeight in 1 until SMALL) add(Kind.UP2)
        if (stats(g).mean < 110) add(Kind.INVERT)
    }

    /**
     * [PHOTO-21] 1.30 AV: letters lower than this (pixels of the photo) get a bigger reading. 1.29 made one under 20 only;
     * a payslip photographed from farther (14 px) lost the last digits in the made-over readings, worked at a smaller size.
     */
    const val SMALL = 24

    /** [PHOTO-21] The letters' height the bigger reading aims for. */
    const val AIM = 28

    /**
     * [PHOTO-21] How much bigger the part with text is read: to [AIM] pixels a letter (at most three times, at least 1.4, or
     * not worth it), and its longest side no more than [maxSide]. null: not worth it.
     */
    fun upFactor(letterHeight: Int?, region: Int, maxSide: Int): Double? {
        val want = if (letterHeight != null && letterHeight > 0) (AIM.toDouble() / letterHeight).coerceIn(1.4, 3.0) else 2.0
        val f = minOf(want, maxSide.toDouble() / region.coerceAtLeast(1))
        return f.takeIf { it >= 1.4 }
    }

    /**
     * [PHOTO-21] 1.30 AV: the side the made-over readings are worked at. 1.29 worked them all at [work] (2,400) while the photo
     * itself was read at up to 3,200: the same letters 25% smaller, so a photo from far off read its amounts in the first
     * reading and lost their last digits in the others ("사진마다 다르다", R3 A-1 #3). Letters that would fall under 20 pixels at
     * [work] are worked at the photo's own size.
     */
    fun workSide(photoSide: Int, letterHeight: Int?, work: Int = 2400): Int {
        if (photoSide <= work) return photoSide
        val shrunk = (letterHeight ?: return work) * work.toDouble() / photoSide
        return if (shrunk < 20) photoSide else work
    }

    /**
     * [PHOTO-25] 1.30 AV: how strongly the picture repeats itself every 2~6 pixels along its rows, 0~1: a photo of a monitor
     * has a moiré of fine dots (사용자 2026-10-27 화학 공장 명세서), print and paper do not. Each row's brightness steps are
     * compared with themselves a few pixels on (autocorrelation), on the paper between the letters; the best lag's share.
     */
    fun periodic(g: Gray): Double {
        if (g.w < 32 || g.h < 8) return 0.0
        // Only the paper between the letters: the strokes' own edges repeat too (letters are about as wide as each other) and
        // would drown a faint moire. The paper is the brightest quarter of the picture, the letters well under it.
        val hist = IntArray(256)
        for (v in g.px) hist[v.coerceIn(0, 255)]++
        var seen = 0
        var paper = 255
        for (v in 255 downTo 0) { seen += hist[v]; if (seen >= g.px.size / 4) { paper = v; break } }
        val floor = paper - 45
        val num = DoubleArray(7)
        var den = 0.0
        var count = 0
        val step = max(1, g.h / 60)
        var y = 0
        while (y < g.h) {
            val d = DoubleArray(g.w - 2) { i ->
                val a = g[i, y]; val b = g[i + 1, y]; val c = g[i + 2, y]
                if (a < floor || b < floor || c < floor) Double.NaN else b - (a + c) / 2.0
            }
            for (i in d.indices) if (!d[i].isNaN()) {
                den += d[i] * d[i]; count++
                for (lag in 2..6) if (i + lag < d.size && !d[i + lag].isNaN()) num[lag] += d[i] * d[i + lag]
            }
            y += step
        }
        // Paper as flat as this has nothing repeating on it (a camera's noise is a level or so and does not repeat).
        if (count == 0 || den / count < 1.0) return 0.0
        return (2..6).maxOf { num[it] / den }.coerceIn(0.0, 1.0)
    }

    /** [PHOTO-25] A picture whose rows repeat this strongly ([periodic]) is a photo of a screen: smoothed before anything else. */
    const val MOIRE = 0.25

    fun make(kind: Kind, g: Gray, moire: Boolean = false): Gray {
        // [PHOTO-25] 1.30: a screen's moiré smoothed first for every made-over reading (1.29 did it for black and white only:
        // contrast and light evened out made the dots into commas and points between the digits).
        val s = if (moire && kind != Kind.ORIG && kind != Kind.INVERT) soft(g) else g
        return when (kind) {
            Kind.ORIG -> g
            Kind.CONTRAST -> contrast(s)
            Kind.FLAT -> flat(s)
            Kind.BINARY -> binary(soft(flat(g)))
            Kind.UP2 -> up2(flat(s))
            Kind.INVERT -> invert(g)
        }
    }

    /** From a variant's pixels back to the photo's (the double-size one is halved). */
    fun factor(kind: Kind): Double = if (kind == Kind.UP2) 0.5 else 1.0

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    /** The middle of [values], for a letter height. */
    fun median(values: List<Int>): Int? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
}
