package com.bamnat.core.recipe

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * [STU-30] 1.36 한 색으로 앱 전체 (스튜디오 극상 조사 S1·S2·S3, 사용자 2026-10-31 "스튜디오 기능을 극상으로"): one colour (씨앗 색) makes
 * the whole app's colours — 바탕, 카드, 글자, 강조, 오늘, 토·일 — that go together, in one of four [Variant]s, light or dark. Text always
 * stands 4.5:1 on its ground and the accent and 토·일 3:1 ([Palette.contrast]). Idea from public descriptions of tonal palettes built from
 * one colour (Material You) and of 색 섞기 (Canva Shuffle); written from scratch for 밤낮.
 *
 * [shuffle] gives the same seed in other places (accent next door or across the wheel, ground and cards swapped, a stronger ground),
 * and [fromPixels] picks a picture's standout colours (S2 사진에서 색 뽑기) to start from.
 */
object ThemeSeed {
    enum class Variant(val label: String) {
        SOFT("부드럽게"), VIVID("선명하게"), CALM("차분하게"), MONO("흑백"),
    }

    /** The colours one seed makes (ARGB, opaque). */
    data class Scheme(
        val bg: Long, val card: Long, val ink: Long, val accent: Long,
        val today: Long, val sat: Long, val sun: Long, val dark: Boolean,
    )

    /** The places [shuffle] cycles through, by name for the 기록 목록 and TalkBack. */
    val SHUFFLES = listOf("기본", "카드에 색", "옆 색 강조", "반대 색 강조", "세 갈래 강조", "진한 바탕", "다른 옆 색 강조")

    const val DEFAULT_SEED = 0xFF4F6BEDL

    fun scheme(seed: Long, variant: Variant = Variant.SOFT, dark: Boolean = false): Scheme = shuffle(seed, variant, dark, 0)

    /** [seed]'s scheme in place [i] of [SHUFFLES] (any [i]; it wraps around). */
    fun shuffle(seed: Long, variant: Variant, dark: Boolean, i: Int): Scheme {
        val (h0, s0, _) = hsl(seed)
        val way = ((i % SHUFFLES.size) + SHUFFLES.size) % SHUFFLES.size
        val accentHue = when (way) { 2 -> h0 + 30; 3 -> h0 + 180; 4 -> h0 + 120; 6 -> h0 - 30; else -> h0 }.let { ((it % 360) + 360) % 360 }
        // How much colour the ground and the accent take, by variant.
        val (groundS, accentS) = when (variant) {
            Variant.SOFT -> min(s0, 0.45) * 0.55 to min(max(s0, 0.35), 0.6)
            Variant.VIVID -> min(s0, 0.7) * 0.6 to max(s0, 0.78)
            Variant.CALM -> min(s0, 0.2) * 0.5 to min(max(s0, 0.15), 0.32)
            Variant.MONO -> 0.0 to min(s0, 0.12)
        }
        val strong = way == 5
        var bg: Long
        var card: Long
        if (!dark) {
            bg = fromHsl(h0, groundS * (if (strong) 1.8 else 1.0), if (strong) 0.915 else 0.962)
            card = fromHsl(h0, groundS * 0.25, 0.995)
            // 카드에 색: a plain white ground and the colour on the cards.
            if (way == 1) { bg = fromHsl(h0, 0.0, 0.99); card = fromHsl(h0, groundS * 1.4, 0.94) }
        } else {
            bg = fromHsl(h0, groundS * (if (strong) 1.4 else 0.8), if (strong) 0.13 else 0.085)
            card = fromHsl(h0, groundS * 0.9, 0.145)
            if (way == 1) card = fromHsl(h0, groundS * 1.6, 0.19)
        }
        val grounds = longArrayOf(bg, card)
        val ink = Palette.inkOn(if (dark) fromHsl(h0, 0.12, 0.93) else fromHsl(h0, 0.25, 0.13), *grounds)
        val accent = reach(accentHue, accentS, if (dark) 0.68 else 0.45, 3.0, *grounds)
        val weekend = if (variant == Variant.MONO) 0.25 else if (variant == Variant.CALM) 0.45 else 0.7
        val sat = reach(215.0, weekend, if (dark) 0.70 else 0.45, 3.0, *grounds)
        val sun = reach(4.0, weekend, if (dark) 0.70 else 0.47, 3.0, *grounds)
        return Scheme(bg, card, ink, accent, accent, sat, sun, dark)
    }

    /**
     * [STU-31] S2: up to [count] standout colours of a picture's pixels (ARGB ints, a small copy is enough), each a different hue: the
     * colour that covers the most of the picture first (PC 확인 1.36: 가운데 큰 노란 해가 빠짐), then the most colourful. A picture with
     * almost no colour gives its middle grey.
     */
    fun fromPixels(pixels: IntArray, count: Int = 5): List<Long> {
        val bins = 24
        val weight = DoubleArray(bins)
        val sums = Array(bins) { DoubleArray(3) }
        val n = IntArray(bins)
        var grey = 0.0
        var greyN = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val c = p.toLong() and 0xFFFFFFFFL
            val (h, s, l) = hsl(c)
            if (s < 0.18 || l < 0.12 || l > 0.95) { grey += l; greyN++; continue }
            val b = ((h / 360.0) * bins).toInt().coerceIn(0, bins - 1)
            // Colourful and mid-light pixels count more, so a small bright flower beats a large dull wall.
            weight[b] += s * (1.0 - abs(l - 0.5))
            sums[b][0] += ((p shr 16) and 0xFF).toDouble(); sums[b][1] += ((p shr 8) and 0xFF).toDouble(); sums[b][2] += (p and 0xFF).toDouble()
            n[b]++
        }
        val byWeight = (0 until bins).filter { n[it] > 0 }.sortedByDescending { weight[it] }
        // The widest colour leads when it is a real part of the picture (an eighth of the coloured pixels or more).
        val widest = (0 until bins).maxByOrNull { n[it] }?.takeIf { n[it] > 0 && n[it] * 8 >= n.sum() }
        val order = listOfNotNull(widest) + byWeight.filter { it != widest }
        val picked = mutableListOf<Int>()
        for (b in order) {
            if (picked.size >= count) break
            if (picked.any { minOf(abs(it - b), bins - abs(it - b)) < 2 }) continue
            if (b != widest && weight[b] < weight[byWeight.first()] * 0.04) break
            picked += b
        }
        if (picked.isEmpty()) {
            val l = if (greyN == 0) 0.5 else grey / greyN
            return listOf(fromHsl(0.0, 0.0, l.coerceIn(0.3, 0.7)))
        }
        return picked.map { b -> argb(sums[b][0] / n[b], sums[b][1] / n[b], sums[b][2] / n[b]) }
    }

    /** The colour of hue [h], saturation [s] and lightness near [l] that stands [min]:1 on every one of [grounds] (darker on light, lighter on dark). */
    private fun reach(h: Double, s: Double, l: Double, min: Double, vararg grounds: Long): Long {
        val light = grounds.all { Palette.luminance(it) > 0.18 }
        var x = l
        repeat(60) {
            val c = fromHsl(h, s, x)
            if (grounds.all { g -> Palette.contrast(c, g) >= min }) return c
            x = if (light) x - 0.01 else x + 0.01
            if (x !in 0.0..1.0) return fromHsl(h, s, x.coerceIn(0.0, 1.0))
        }
        return fromHsl(h, s, x.coerceIn(0.0, 1.0))
    }

    /** Hue 0..360, saturation and lightness 0..1. */
    fun hsl(c: Long): Triple<Double, Double, Double> {
        val r = ((c shr 16) and 0xFF) / 255.0
        val g = ((c shr 8) and 0xFF) / 255.0
        val b = (c and 0xFF) / 255.0
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2
        if (mx == mn) return Triple(0.0, 0.0, l)
        val d = mx - mn
        val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60
        return Triple(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double): Long {
        val ss = s.coerceIn(0.0, 1.0)
        val ll = l.coerceIn(0.0, 1.0)
        val hh = (((h % 360) + 360) % 360) / 360.0
        if (ss == 0.0) return argb(ll * 255, ll * 255, ll * 255)
        val q = if (ll < 0.5) ll * (1 + ss) else ll + ss - ll * ss
        val p = 2 * ll - q
        fun ch(t0: Double): Double {
            var t = t0
            if (t < 0) t += 1
            if (t > 1) t -= 1
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        return argb(ch(hh + 1.0 / 3) * 255, ch(hh) * 255, ch(hh - 1.0 / 3) * 255)
    }

    private fun argb(r: Double, g: Double, b: Double): Long =
        0xFF000000L or (r.roundToInt().coerceIn(0, 255).toLong() shl 16) or (g.roundToInt().coerceIn(0, 255).toLong() shl 8) or
            b.roundToInt().coerceIn(0, 255).toLong()
}
