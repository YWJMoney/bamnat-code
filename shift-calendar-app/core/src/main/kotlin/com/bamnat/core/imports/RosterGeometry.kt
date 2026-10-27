package com.bamnat.core.imports

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * [READ-02] Where the words of a roster photo stand, whatever way the phone held it (1.25 순번 368).
 *
 * 1.24 put words on one line when their middles were within half a word's height. A phone photo of a 31-day table is
 * tilted 1~3° more often than not, and across 2,000 pixels 1.5° moves the right end ~50 pixels, more than a word's
 * height: the row of days broke into pieces mixed with the lines under it, no row of days was found and nothing was
 * read (통합 재현 2026-10-25: 1°부터 줄이 섞이고 2~3°는 0줄). Here the tilt is measured first and taken out, lines
 * are followed word to word from the left (so a table skewed by the camera's angle stays one line each), and a day's
 * column is where a straight line through the day numbers puts it (x = a + b·day), so a number the photo lost or two
 * numbers read as one ("1213") still have their columns.
 */

/** A word moved into the level frame: its middle ([x], [y]) and size. */
internal class Placed(val word: RosterWord, val x: Double, val y: Double, val w: Double, val h: Double) {
    val left: Double get() = x - w / 2
    val right: Double get() = x + w / 2
    val text: String get() = word.text
}

/** Columns of days on one line: day d's middle is at a + b·d, for days [first]..[last]. */
internal data class DayFit(
    val a: Double, val b: Double, val first: Int, val last: Int,
    /**
     * [READ-13] 1.27: the bend of the columns (x = a + b·d + c·d²). A photo taken from one side of the table (strong
     * perspective) has narrower columns at the far end; a straight line put the far days' cells one day off (1.27 bench
     * r05: 9 cells on the next day without a yellow mark). 0 for a table seen square on.
     */
    val c: Double = 0.0,
) {
    fun x(day: Int): Double = a + b * day + c * day * day
    /** The width of a column at [day]. */
    fun step(day: Int): Double = (b + c * (2 * day)).coerceAtLeast(b * 0.3)
    /** The day whose column [x] is in, and how far from its middle in columns, or null outside [first]..[last]. */
    fun day(x: Double): Pair<Int, Double>? {
        val guess = ((x - a) / b).roundToInt()
        val d = if (c == 0.0) guess else (guess - 3..guess + 3).minBy { abs(x - x(it)) }
        if (d < first || d > last) return null
        return d to abs(x - x(d)) / step(d)
    }
}

internal object RosterGeometry {
    /** Steepest tilt looked for, in degrees: a photo tilted more is taken again rather than read wrong. */
    const val MAX_TILT = 10.0

    /**
     * [READ-02] The tilt of the text in degrees (positive: lines go down to the right), from the words' middles: the
     * angle at which they stack into the fewest, fullest rows (projection profile). Level text stays 0.
     */
    fun tilt(words: List<RosterWord>): Double {
        if (words.size < 4) return 0.0
        val h = median(words.map { it.height.toDouble() }).coerceAtLeast(2.0)
        val bin = (h / 3).coerceAtLeast(1.0)
        fun sharpness(deg: Double): Double {
            val r = Math.toRadians(deg)
            val s = sin(r)
            val c = cos(r)
            val counts = HashMap<Int, Int>()
            for (w in words) {
                val y = -w.centerX * s + w.centerY * c
                val k = Math.floorDiv(y.toInt(), bin.toInt().coerceAtLeast(1))
                counts.merge(k, 1, Int::plus)
            }
            return counts.values.sumOf { (it * it).toDouble() }
        }
        val level = sharpness(0.0)
        val tried = (-(MAX_TILT * 20).toInt()..(MAX_TILT * 20).toInt()).map { it / 20.0 }.map { it to sharpness(it) }
        val top = tried.maxOf { it.second }
        // A level table read a hair off level is no gain: 0 unless a tilt stacks the words clearly better.
        if (top < level * 1.05) return 0.0
        // Neighbouring angles stack the words alike: the middle of the best run.
        val run = tried.filter { it.second >= top * 0.995 }.map { it.first }
        return median(run)
    }

    /** Words turned by -[deg] about the origin, so lines tilted [deg] lie level. */
    fun level(words: List<RosterWord>, deg: Double): List<Placed> {
        val r = Math.toRadians(deg)
        val s = sin(r)
        val c = cos(r)
        return words.map { w ->
            val cx = (w.left + w.right) / 2.0
            val cy = (w.top + w.bottom) / 2.0
            val width = (w.right - w.left).toDouble().coerceAtLeast(1.0)
            // A tilted word's box is taller than its letters by its width's rise: that rise taken off.
            val h = (w.height - width * abs(s)).coerceAtLeast(w.height * 0.3)
            Placed(w, cx * c + cy * s, -cx * s + cy * c, width, h)
        }
    }

    /** A point of the photo in the level frame of [deg]. */
    fun level(x: Int, y: Int, deg: Double): Pair<Double, Double> {
        val r = Math.toRadians(deg)
        return x * cos(r) + y * sin(r) to -x * sin(r) + y * cos(r)
    }

    /**
     * [READ-02] Lines of words, each followed from the left: a word joins the line whose course (fitted through its last
     * words) passes nearest its middle, within half a word's height, so a line bent by the camera's angle stays whole and
     * the next line never takes its words. Top to bottom, each left to right.
     */
    fun lines(words: List<Placed>): List<List<Placed>> {
        if (words.isEmpty()) return emptyList()
        val h = median(words.map { it.h })
        val open = mutableListOf<MutableList<Placed>>()
        for (w in words.sortedWith(compareBy({ it.left }, { it.y }))) {
            val tol = 0.5 * minOf(h, w.h).coerceAtLeast(1.0)
            var best: MutableList<Placed>? = null
            var bestGap = Double.MAX_VALUE
            for (line in open) {
                val last = line.last()
                // In order along the line: a word may touch the one before it, never sit under it.
                if (last.x >= w.x || last.right > w.left + 0.5 * minOf(w.w, last.w)) continue
                val gap = abs(courseAt(line, w.x) - w.y)
                if (gap <= tol && gap < bestGap) { best = line; bestGap = gap }
            }
            if (best != null) best += w else open += mutableListOf(w)
        }
        return open.sortedBy { line -> line.sumOf { it.y } / line.size }
    }

    /** Where [line] runs at [x]: a straight fit through its last few words, or its last word's height. */
    private fun courseAt(line: List<Placed>, x: Double): Double {
        val tail = line.takeLast(8)
        if (tail.size < 3) return tail.last().y
        val mx = tail.sumOf { it.x } / tail.size
        val my = tail.sumOf { it.y } / tail.size
        val sxx = tail.sumOf { (it.x - mx) * (it.x - mx) }
        if (sxx < 1e-6) return tail.last().y
        val slope = (tail.sumOf { (it.x - mx) * (it.y - my) } / sxx).coerceIn(-0.05, 0.05)
        return tail.last().y + slope * (x - tail.last().x)
    }

    /**
     * [READ-03] The days of a line as (day, x): one number each, and numbers read together ("1213", "293031") split into
     * days in a row across their box.
     */
    fun days(line: List<Placed>, dayOf: (String) -> Int?): List<Pair<Int, Double>> = line.flatMap { p ->
        dayOf(p.text)?.let { return@flatMap listOf(it to p.x) }
        val t = DutyCodes.fold(p.text).trim()
        if (t.length !in 3..8 || !t.all { it.isDigit() }) return@flatMap emptyList()
        joined(t)?.let { parts ->
            var at = 0
            parts.map { (d, len) ->
                val mid = (at + len / 2.0) / t.length
                at += len
                d to p.left + p.w * mid
            }
        }.orEmpty()
    }

    /** "1213" as 12 13, "91011" as 9 10 11: consecutive days that spell [t] exactly, with each one's letters. */
    private fun joined(t: String): List<Pair<Int, Int>>? {
        for (firstLen in 1..2) {
            val first = t.take(firstLen).toIntOrNull() ?: continue
            if (first !in 1..31 || t[0] == '0') continue
            val out = mutableListOf(first to firstLen)
            var at = firstLen
            var next = first + 1
            while (at < t.length && next <= 31) {
                val s = next.toString()
                if (!t.startsWith(s, at)) break
                out += next to s.length
                at += s.length
                next++
            }
            if (at == t.length && out.size >= 2) return out
        }
        return null
    }

    /**
     * [READ-03] The straight line x = a + b·day through the most of [points] (each pair tried, the best refitted), or
     * null when fewer than [min] numbers agree. The days it covers run from the first to the last number on it.
     */
    fun fit(points: List<Pair<Int, Double>>, min: Int): DayFit? {
        if (points.size < min) return null
        var best: List<Pair<Int, Double>> = emptyList()
        var bestErr = Double.MAX_VALUE
        for (i in points.indices) for (j in i + 1 until points.size) {
            val (di, xi) = points[i]
            val (dj, xj) = points[j]
            if (di == dj) continue
            val b = (xj - xi) / (dj - di)
            if (b <= 0) continue
            val a = xi - b * di
            val inliers = points.filter { (d, x) -> abs(x - (a + b * d)) <= 0.3 * b }.distinctBy { it.first }
            val err = inliers.sumOf { (d, x) -> abs(x - (a + b * d)) / b }
            if (inliers.size > best.size || (inliers.size == best.size && err < bestErr)) { best = inliers; bestErr = err }
        }
        if (best.size < min) return null
        val (a, b) = leastSquares(best) ?: return null
        val line = DayFit(a, b, best.minOf { it.first }, best.maxOf { it.first })
        return bent(line, points) ?: line
    }

    /**
     * [READ-13] [line] bent to fit [points] when the columns narrow or widen along the row (perspective): a quadratic
     * through the points near it, grown while more points fall within 0.3 of a column. Null when the straight line is as
     * good, or the bend is not a photo's (columns more than halving or doubling across the month).
     */
    fun bent(line: DayFit, points: List<Pair<Int, Double>>): DayFit? {
        if (points.distinctBy { it.first }.size < 8) return null
        var fit = line
        var inliers = points.filter { (d, x) -> abs(x - fit.x(d)) <= 0.3 * fit.step(d) }.distinctBy { it.first }
        repeat(3) {
            val q = quadratic(inliers) ?: return@repeat
            val next = DayFit(q.first, q.second, minOf(fit.first, inliers.minOf { it.first }), maxOf(fit.last, inliers.maxOf { it.first }), q.third)
            val (lo, hi) = next.first to next.last
            if (next.step(lo) <= 0 || next.step(hi) / next.step(lo) !in 0.5..2.0) return@repeat
            val more = points.filter { (d, x) -> abs(x - next.x(d)) <= 0.3 * next.step(d) }.distinctBy { it.first }
            if (more.size < inliers.size) return@repeat
            fit = next
            inliers = more
        }
        if (fit === line || abs(fit.c) * 31 * 31 < 0.15 * fit.b) return null
        return fit.copy(first = minOf(line.first, inliers.minOf { it.first }), last = maxOf(line.last, inliers.maxOf { it.first }))
    }

    /** a, b and c of x = a + b·d + c·d² through [points] (least squares), or null for fewer than three days. */
    fun quadratic(points: List<Pair<Int, Double>>): Triple<Double, Double, Double>? {
        if (points.distinctBy { it.first }.size < 3) return null
        // Normal equations in d centred on its mean, for a steady solve.
        val m = points.sumOf { it.first.toDouble() } / points.size
        var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0; var t0 = 0.0; var t1 = 0.0; var t2 = 0.0
        for ((d, x) in points) {
            val u = d - m
            s0 += 1; s1 += u; s2 += u * u; s3 += u * u * u; s4 += u * u * u * u
            t0 += x; t1 += x * u; t2 += x * u * u
        }
        val det = s0 * (s2 * s4 - s3 * s3) - s1 * (s1 * s4 - s3 * s2) + s2 * (s1 * s3 - s2 * s2)
        if (abs(det) < 1e-9) return null
        val p = (t0 * (s2 * s4 - s3 * s3) - s1 * (t1 * s4 - s3 * t2) + s2 * (t1 * s3 - s2 * t2)) / det
        val q = (s0 * (t1 * s4 - s3 * t2) - t0 * (s1 * s4 - s3 * s2) + s2 * (s1 * t2 - t1 * s2)) / det
        val r = (s0 * (s2 * t2 - t1 * s3) - s1 * (s1 * t2 - t1 * s2) + t0 * (s1 * s3 - s2 * s2)) / det
        // x = p + q·u + r·u², u = d − m.
        val c = r
        val b = q - 2 * r * m
        val a = p - q * m + r * m * m
        if (b + 2 * c * m <= 0) return null
        return Triple(a, b, c)
    }

    /** a and b of x = a + b·d through [points], or null when they are all one day. */
    fun leastSquares(points: List<Pair<Int, Double>>): Pair<Double, Double>? {
        val n = points.size
        if (n < 2) return null
        val md = points.sumOf { it.first.toDouble() } / n
        val mx = points.sumOf { it.second } / n
        val sdd = points.sumOf { (it.first - md) * (it.first - md) }
        if (sdd < 1e-9) return null
        val b = points.sumOf { (it.first - md) * (it.second - mx) } / sdd
        if (b <= 0) return null
        return (mx - b * md) to b
    }

    fun median(values: List<Double>): Double = values.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }

    /**
     * [PHOTO-21] 1.30 AV: the slant of the rows as it changes down the page, `angle = a + b·y` degrees, from the slopes the
     * text reader gave its long lines (사용자 2026-10-27 "사진마다 읽는 부분이 다르다", R3 A-1 #1). A page taken from one side
     * (keystone) has its top rows going one way and its bottom rows the other; one tilt for the page levelled the middle and
     * left the top and bottom rows slanted, so an amount half a row lower at the far end was lost or taken by the next row.
     * Slopes more than 2° off the middle one are a watermark or a misread line and left out. null when fewer than three
     * slopes; [Slant.b] 0 when the page leans alike top to bottom (under 0.8° apart), as one tilt did.
     */
    fun slant(words: List<RosterWord>): Slant? {
        val long = words.filter { it.angle != 0f && it.right - it.left > 3 * it.height }
        if (long.size < 3) return null
        val mid = median(long.map { it.angle.toDouble() })
        val near = long.filter { abs(it.angle - mid) <= 2.0 }.map { it.centerY.toDouble() to it.angle.toDouble() }
        if (near.size < 6) return Slant(mid, 0.0)
        val my = near.sumOf { it.first } / near.size
        val ma = near.sumOf { it.second } / near.size
        val syy = near.sumOf { (it.first - my) * (it.first - my) }
        if (syy < 1e-6) return Slant(mid, 0.0)
        val b = near.sumOf { (it.first - my) * (it.second - ma) } / syy
        val span = near.maxOf { it.first } - near.minOf { it.first }
        // A page that leans alike top to bottom keeps the one tilt (1.29's reading, unchanged).
        if (abs(b) * span < 0.8) return Slant(mid, 0.0)
        return Slant(ma - b * my, b)
    }

    /** [PHOTO-21] The rows' slant at height y of the photo: [a] + [b]·y degrees. */
    data class Slant(val a: Double, val b: Double) {
        fun at(y: Double): Double = a + b * y
    }

    /**
     * [PHOTO-21] Words levelled each by the slant of its own row ([slant] at its height), turned about the middle of the page
     * so the rows keep their order and each row lies level. With [Slant.b] 0 it is [level] by one tilt.
     */
    fun level(words: List<RosterWord>, slant: Slant): List<Placed> {
        if (slant.b == 0.0) return level(words, slant.a)
        val cx = (words.minOf { it.left } + words.maxOf { it.right }) / 2.0
        val cy = (words.minOf { it.top } + words.maxOf { it.bottom }) / 2.0
        return words.map { w ->
            val x = (w.left + w.right) / 2.0 - cx
            val y = (w.top + w.bottom) / 2.0 - cy
            val r = Math.toRadians(slant.at(y + cy))
            val s = sin(r)
            val c = cos(r)
            val width = (w.right - w.left).toDouble().coerceAtLeast(1.0)
            val h = (w.height - width * abs(s)).coerceAtLeast(w.height * 0.3)
            Placed(w, cx + x * c + y * s, cy - x * s + y * c, width, h)
        }
    }
}
