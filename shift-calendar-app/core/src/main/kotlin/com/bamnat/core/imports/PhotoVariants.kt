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
        // Small print: letters under 20 pixels high read better twice the size (ML Kit's guidance: at least 16 pixels a letter).
        if (letterHeight != null && letterHeight in 1..19) add(Kind.UP2)
        if (stats(g).mean < 110) add(Kind.INVERT)
    }

    fun make(kind: Kind, g: Gray): Gray = when (kind) {
        Kind.ORIG -> g
        Kind.CONTRAST -> contrast(g)
        Kind.FLAT -> flat(g)
        Kind.BINARY -> binary(flat(g))
        Kind.UP2 -> up2(flat(g))
        Kind.INVERT -> invert(g)
    }

    /** From a variant's pixels back to the photo's (the double-size one is halved). */
    fun factor(kind: Kind): Double = if (kind == Kind.UP2) 0.5 else 1.0

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    /** The middle of [values], for a letter height. */
    fun median(values: List<Int>): Int? = values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
}
