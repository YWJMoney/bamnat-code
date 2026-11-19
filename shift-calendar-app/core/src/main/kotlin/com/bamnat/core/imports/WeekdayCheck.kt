package com.bamnat.core.imports

import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.abs

/**
 * 2.0.0 [RVL-75] (검토 I07ba-02) A roster's row of weekdays (월 화 수 … under 1 2 3 …) said nothing: it was recognised and dropped. It says
 * which month the dates are of, and whether the columns line up. Read against the month a roster was read as:
 * - the weekdays fit → nothing to say;
 * - they fit the month before or after (or the same month a year off) → that month is offered (a "1월" read as "7월", 작성일's month);
 * - they fit one day over → the columns are a day off (a column lost, a 31 read as two): the cells are to be looked at;
 * - too few to tell, or they fit nothing → nothing to say.
 */
object WeekdayCheck {
    sealed interface Verdict {
        /** The weekdays are of [month], not the month read. */
        data class OtherMonth(val month: YearMonth) : Verdict
        /** The dates sit [days] columns off their weekdays (-1: the weekday under the 3rd is the 2nd's). */
        data class Shifted(val days: Int) : Verdict
    }

    /** At least this many dates with a weekday under them to say anything. */
    const val MIN_PAIRS = 5

    /**
     * [pairs]: a day of the month and the weekday written with it (a day before the roster crossed into [month], as 26~31 of a 4주 듀티, is
     * negative: -26); null when they fit [month] or say nothing sure.
     */
    fun check(pairs: Map<Int, DayOfWeek>, month: YearMonth): Verdict? {
        if (pairs.size < MIN_PAIRS) return null
        fun fits(m: YearMonth, shift: Int = 0) = pairs.count { (d, w) ->
            val date = if (d < 0) m.minusMonths(1).let { p -> p.atDay(minOf(-d, p.lengthOfMonth())) } else m.atDay(1).plusDays(d - 1L)
            date.plusDays(shift.toLong()).dayOfWeek == w
        }
        fun most(n: Int) = n * 10 >= pairs.size * 8
        if (most(fits(month))) return null
        listOf(month.minusMonths(1), month.plusMonths(1)).firstOrNull { most(fits(it)) }?.let { return Verdict.OtherMonth(it) }
        // A column a day off is more likely than a year misread (whose weekdays are often one over too).
        listOf(-1, 1).firstOrNull { most(fits(month, it)) }?.let { return Verdict.Shifted(it) }
        listOf(month.minusYears(1), month.plusYears(1)).firstOrNull { most(fits(it)) }?.let { return Verdict.OtherMonth(it) }
        return null
    }

    /** The weekdays of a sheet's rows (a file or pasted text): a row of day numbers and the row of weekdays beside it, by column. */
    fun ofRows(rows: List<List<String>>): Map<Int, DayOfWeek> {
        val days = rows.indices.map { i -> rows[i].mapIndexedNotNull { c, t -> number(t)?.let { c to it } }.toMap() }
        val weekdays = rows.indices.map { i -> rows[i].mapIndexedNotNull { c, t -> TimeSheet.weekday(t)?.let { c to it } }.toMap() }
        val dateRow = days.indices.filter { rises(days[it].toSortedMap().values.toList()) }.maxByOrNull { days[it].size } ?: return emptyMap()
        val keys = crossed(days[dateRow].toSortedMap().entries.map { it.key to it.value }).toMap()
        // The weekday row is the one next to it (above or below, within two rows).
        val weekRow = (dateRow - 2..dateRow + 2).filter { it in rows.indices && it != dateRow && weekdays[it].size >= MIN_PAIRS }
            .minByOrNull { abs(it - dateRow) }
        // A date and its weekday in one cell: "1(월)".
        val same = rows[dateRow].mapIndexedNotNull { c, t -> keys[c]?.let { d -> both(t)?.let { d to it } } }.toMap()
        if (weekRow == null) return same
        return keys.mapNotNull { (c, d) -> weekdays[weekRow][c]?.let { d to it } }.toMap() + same
    }

    /** The weekdays of a photo's words: the row of rising day numbers and the row of weekdays nearest it, each weekday under its date. */
    fun ofWords(words: List<RosterWord>): Map<Int, DayOfWeek> {
        if (words.isEmpty()) return emptyMap()
        val height = words.map { it.bottom - it.top }.sorted()[words.size / 2].coerceAtLeast(1)
        // Lines: words whose middles are within half a word's height.
        val lines = words.sortedBy { (it.top + it.bottom) / 2 }.fold(mutableListOf<MutableList<RosterWord>>()) { acc, w ->
            val mid = (w.top + w.bottom) / 2
            val last = acc.lastOrNull()
            if (last != null && abs(mid - last.map { (it.top + it.bottom) / 2 }.average().toInt()) <= height / 2) last += w else acc += mutableListOf(w)
            acc
        }.map { l -> l.sortedBy { it.left } }
        val dateLine = lines.filter { l -> rises(l.mapNotNull { number(it.text) }) }.maxByOrNull { l -> l.count { number(it.text) != null } } ?: return emptyMap()
        val dates = crossed(dateLine.mapNotNull { w -> number(w.text)?.let { d -> (w.left + w.right) / 2 to d } }).map { (x, d) -> d to x }
        val y = dateLine.map { (it.top + it.bottom) / 2 }.average()
        val weekLine = lines.filter { l -> l !== dateLine && l.count { TimeSheet.weekday(it.text) != null } >= MIN_PAIRS }
            .minByOrNull { l -> abs(l.map { (it.top + it.bottom) / 2 }.average() - y) }
        val same = dateLine.mapNotNull { w -> dates.firstOrNull { it.second == (w.left + w.right) / 2 }?.first?.let { d -> both(w.text)?.let { d to it } } }.toMap()
        if (weekLine == null || dates.size < 2) return same
        val pitch = dates.zipWithNext { a, b -> abs(b.second - a.second) }.sorted().let { it[it.size / 2] }.coerceAtLeast(1)
        return weekLine.mapNotNull { w ->
            val wd = TimeSheet.weekday(w.text) ?: return@mapNotNull null
            val x = (w.left + w.right) / 2
            val near = dates.minByOrNull { abs(it.second - x) } ?: return@mapNotNull null
            if (abs(near.second - x) * 2 > pitch) null else near.first to wd
        }.toMap() + same
    }

    private val NUMBER = Regex("""^0?(\d{1,2})(?:일)?(?:\s*\(?([월화수목금토일])\)?)?$""")

    private fun number(t: String): Int? = NUMBER.matchEntire(t.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }

    private fun both(t: String): DayOfWeek? = NUMBER.matchEntire(t.trim())?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let(TimeSheet::weekday)

    /** Days in order with where they are; those before the row crosses into the next month (…30 31 1 2…) made negative. */
    private fun crossed(days: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val cross = days.zipWithNext().indexOfFirst { (a, b) -> b.second - a.second < -20 }
        return if (cross < 0) days else days.mapIndexed { i, (at, d) -> at to if (i <= cross) -d else d }
    }

    /** Day numbers that go up one by one for the most part (crossing into the next month once). */
    private fun rises(days: List<Int>): Boolean {
        if (days.size < MIN_PAIRS) return false
        val steps = days.zipWithNext { a, b -> b - a }
        return steps.count { it == 1 || it < -20 } * 10 >= steps.size * 8
    }
}
