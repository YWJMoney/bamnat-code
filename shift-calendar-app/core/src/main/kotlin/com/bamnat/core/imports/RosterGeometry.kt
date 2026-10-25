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
internal data class DayFit(val a: Double, val b: Double, val first: Int, val last: Int) {
    fun x(day: Int): Double = a + b * day
    /** The day whose column [x] is in, and how far from its middle in columns, or null outside [first]..[last]. */
    fun day(x: Double): Pair<Int, Double>? {
        val d = ((x - a) / b).roundToInt()
        if (d < first || d > last) return null
        return d to abs(x - x(d)) / b
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
        return DayFit(a, b, best.minOf { it.first }, best.maxOf { it.first })
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
}
