package com.bamnat.core.pay

import kotlin.math.abs

/**
 * [PHOTO-24] 1.30 AV: the layout of the company's payslip as the user confirmed it once ('네, 같아요' or '다 고쳤어요'), so the
 * next month's photo reads the same lines in the same order even when it is worse (사용자 2026-10-27 "사진마다 읽는 부분이
 * 다르다", R3 A-1 #8). Each line's name and kind and where its name and its amount stood on the photo (the photo's pixels,
 * levelled). No amount, no person's name, no photo: only the payslip's line names and where they sit. On the phone only,
 * in the backup with the names the user taught ([com.bamnat.core.pay.SlipNames], 설정 파일 "read_fixes"), never sent (D68).
 *
 * Read with it ([SlipTable.read]): where this month's photo lost a line the template has (its name read as other letters,
 * or not at all), the place the template says is looked at: an unread name there, or an amount in the amount column there,
 * becomes that line, yellow, to look at. The lines come in the template's order.
 */
data class SlipTemplate(val lines: List<Line>) {
    /** One line: its name and kind, the middle of its name ([x], [y]) and of its amount ([ax], [ay]), and its letters' height. */
    data class Line(val name: String, val role: SlipRole, val x: Double, val y: Double, val ax: Double?, val ay: Double?, val h: Double)

    /** One template a line of text each: "v1", then `role\tname\tx\ty\tax\tay\th` (no amount). */
    fun encode(): String = (listOf("v1") + lines.map { l ->
        listOf(l.role.name, l.name.replace('\t', ' ').replace('\n', ' '), r(l.x), r(l.y), l.ax?.let(::r) ?: "", l.ay?.let(::r) ?: "", r(l.h)).joinToString("\t")
    }).joinToString("\n")

    /** The share of [read]'s lines whose names this template holds: how alike the two payslips are. */
    fun likeness(read: PayslipRead): Double {
        val mine = lines.map { key(it.name) }.toSet()
        val theirs = read.lines.filter { it.amount != null }.map { key(it.name) }
        if (theirs.isEmpty() || mine.isEmpty()) return 0.0
        return theirs.count { it in mine }.toDouble() / maxOf(theirs.size, mine.size)
    }

    companion object {
        /** At most this many templates are kept (a company's monthly and its bonus payslip, a second job). */
        const val KEEP = 3
        /** At most this many lines a template: more is not a payslip. */
        const val MAX_LINES = 60

        private fun r(v: Double) = Math.round(v).toString()

        /** The letters a line's name is known by: what [SlipVote] matches readings on. */
        fun key(name: String): String = PayslipReader.ocrFix(name.filter { it.isLetter() }.lowercase()).take(6)

        fun decode(s: String?): SlipTemplate? {
            val rows = s?.lines() ?: return null
            if (rows.firstOrNull() != "v1") return null
            val lines = rows.drop(1).mapNotNull { row ->
                val p = row.split('\t')
                if (p.size != 7) return@mapNotNull null
                val role = SlipRole.entries.firstOrNull { it.name == p[0] } ?: return@mapNotNull null
                val x = p[2].toDoubleOrNull() ?: return@mapNotNull null
                val y = p[3].toDoubleOrNull() ?: return@mapNotNull null
                val h = p[6].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                Line(p[1].take(40), role, x, y, p[4].toDoubleOrNull(), p[5].toDoubleOrNull(), h)
            }.take(MAX_LINES)
            return SlipTemplate(lines).takeIf { lines.size >= 3 }
        }

        /**
         * The template of the payslip the user confirmed: [confirmed] (the lines as they agreed them), read from [words] (the
         * photo as taken). A line found on the photo by its name or, failing that, by its amount keeps where it stood; a line
         * the user added by hand has no place and is left out. null when too few lines have a place to be worth keeping.
         */
        fun learn(confirmed: PayslipRead, words: List<OcrLine>): SlipTemplate? {
            if (words.isEmpty()) return null
            val laid = SlipTable.laid(words, null)
            val read = laid.result.read.lines
            val used = HashSet<Int>()
            val out = mutableListOf<Line>()
            for (c in confirmed.lines) {
                if (c.amount == null && c.minutes == null) continue
                val i = read.indices.firstOrNull { it !in used && key(read[it].name) == key(c.name) }
                    ?: read.indices.firstOrNull { it !in used && read[it].amount != null && read[it].amount == c.amount }
                    ?: continue
                val spot = laid.spots.getOrNull(i) ?: continue
                used += i
                // Whole pixels: what is kept is what was learned (a template read back is the same template).
                fun px(v: Double) = Math.round(v).toDouble()
                out += Line(c.name, c.role, px(spot.x), px(spot.y), spot.ax?.let(::px), spot.ay?.let(::px), px(spot.h))
            }
            return SlipTemplate(out.take(MAX_LINES)).takeIf { out.size >= 3 }
        }

        /** Of [templates], the one most like [read] (half its lines or more alike), or null. */
        fun pick(templates: List<SlipTemplate>, read: PayslipRead): SlipTemplate? =
            templates.map { it to it.likeness(read) }.filter { it.second >= 0.5 }.maxByOrNull { it.second }?.first

        /** [kept] with [new] first, one per payslip (a template as like as half or more is the same payslip), at most [KEEP]. */
        fun keep(kept: List<SlipTemplate>, new: SlipTemplate): List<SlipTemplate> {
            val asRead = PayslipRead(new.lines.map { SlipLine(it.name, it.role, 1) })
            return (listOf(new) + kept.filter { it.likeness(asRead) < 0.5 }).take(KEEP)
        }
    }
}

/**
 * [PHOTO-24] Where a template's points fall on this photo: from the lines both have (their names' middles), the nearest six to
 * the point, a straight fit along each axis (so a photo nearer, farther, shifted or cut at the top still lands, and a photo
 * taken at an angle lands within a row). null with fewer than three lines in common.
 */
internal class TemplateMap(private val pairs: List<Pair<SlipTemplate.Line, Pair<Double, Double>>>) {
    fun at(x: Double, y: Double): Pair<Double, Double>? {
        if (pairs.size < 3) return null
        val near = pairs.sortedBy { (t, _) -> abs(t.y - y) + 0.2 * abs(t.x - x) }.take(6)
        val fx = fit(near.map { it.first.x to it.second.first }) ?: scaleOnly(near.map { it.first.x to it.second.first }, near.map { it.first.y to it.second.second })
        val fy = fit(near.map { it.first.y to it.second.second }) ?: return null
        return fx.first + fx.second * x to fy.first + fy.second * y
    }

    /** v' = a + b·v through [points], or null when they do not spread (one column). */
    private fun fit(points: List<Pair<Double, Double>>): Pair<Double, Double>? {
        val n = points.size
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        val spread = points.maxOf { it.first } - points.minOf { it.first }
        if (sxx < 1e-6 || spread < 20) return null
        val b = points.sumOf { (it.first - mx) * (it.second - my) } / sxx
        if (b <= 0.2 || b > 5) return null
        return (my - b * mx) to b
    }

    /** Across one column the names do not spread: their shift, and the scale the rows give. */
    private fun scaleOnly(xs: List<Pair<Double, Double>>, ys: List<Pair<Double, Double>>): Pair<Double, Double> {
        val b = fit(ys)?.second ?: 1.0
        val a = xs.sumOf { it.second - b * it.first } / xs.size
        return a to b
    }
}
