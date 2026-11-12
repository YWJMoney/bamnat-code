package com.bamnat.core.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 2.0.0 [RVH-51] PC-72 What a tap may have put on another day in 1.36.1~1.39.0 (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md).
 *
 * With the month turn 스르륵·책장·움직이지 않기 (or 움직임 끄기, or the phone's animations off) the next month's page lay on top of the
 * month in sight ([PC-28], fixed in 2.0.0): a tap on cell i of month k opened cell i of month k+1. The month grid is always 6 weeks
 * from the week of the 1st ([GRID_DAYS]), so the day that opened is [firstCell] (k+1) + i and the day meant is [firstCell] (k) + i, 28
 * or 35 days earlier. Nothing here says a line is wrong: the sheet asks.
 */
object TurnSlip {
    /** 1.36.1, the first version with the month turn over the month in sight, went out on this day. */
    val SINCE: LocalDate = LocalDate.of(2026, 10, 31)

    /** The cells of a month grid: always 6 rows (GRID_ROWS of the app's MonthGrid). */
    const val GRID_DAYS = 42

    /** How far ahead a month can have been in sight and still be asked about: a year. */
    private const val MONTHS_AHEAD = 12L

    /**
     * Days changed this close one after another are one write ([BATCH_DAYS]): a tap, or a drag over a week on the month grid,
     * changes a few; 근무표 넣기, 휴무 요일, 받은 근무표 and a calendar file change many in one go, milliseconds apart. Taps one by
     * one (칠하기) are further apart than this.
     */
    const val BATCH_GAP_MILLIS = 150L

    /** More days than this in one write are not taps on the month grid. */
    const val BATCH_DAYS = 7

    enum class Kind { DAY, MEMO, MONEY }

    /** A day's change ([Kind.DAY], day_changes), or a dated memo or money line with when it was written. */
    data class Source(val kind: Kind, val date: LocalDate, val at: Instant, val id: Long = 0)

    /** [source] may have been meant for [meant]; the month in sight was [viewed]. */
    data class Line(val source: Source, val viewed: YearMonth, val meant: LocalDate) {
        val date: LocalDate get() = source.date
        /** How far it went: 28 or 35 days. */
        val days: Long get() = ChronoUnit.DAYS.between(meant, date)
    }

    /** The first cell of [month]'s grid: the week of the 1st, from [weekStart]. */
    fun firstCell(month: YearMonth, weekStart: DayOfWeek): LocalDate = CalendarGrid.weekStartOf(month.atDay(1), weekStart)

    /**
     * The day a tap on [date]'s cell meant, written on [changedOn]: the app opens on this month, so the month in sight is taken to be
     * changedOn's month first, then the next ones (a month turned to). The smallest month whose next month's grid holds [date] wins
     * (a row two grids share is the earlier month's, much the more common). Null when [date] is before the next month's grid: such a
     * change cannot have come from this, or cannot be told apart from a change to last month (최근 바꾼 기록 shows it). Null too for a
     * day changed on or after the day itself (오늘·어제의 출퇴근, 위젯, 워치, 오늘 카드): from the grid it would have meant a day four or
     * five weeks before, much the rarer.
     */
    fun meant(date: LocalDate, changedOn: LocalDate, weekStart: DayOfWeek): Pair<YearMonth, LocalDate>? {
        var k = YearMonth.from(changedOn)
        if (!date.isAfter(changedOn) || date.isBefore(firstCell(k.plusMonths(1), weekStart))) return null
        repeat((MONTHS_AHEAD + 1).toInt()) {
            val here = firstCell(k, weekStart)
            val next = firstCell(k.plusMonths(1), weekStart)
            if (!date.isBefore(next) && date.isBefore(next.plusDays(GRID_DAYS.toLong()))) {
                return k to date.minusDays(ChronoUnit.DAYS.between(here, next))
            }
            k = k.plusMonths(1)
        }
        return null
    }

    /**
     * The lines to ask about: [sources] written from [SINCE] (in [zone], the phone's) until [until] (the update to 2.0.0), each day once
     * (the last change of a day sets the month in sight: what the day holds now is what moves). A day whose last change was one
     * write of more than [BATCH_DAYS] days is not asked about.
     */
    fun lines(sources: List<Source>, weekStart: DayOfWeek, zone: ZoneId, until: Instant): List<Line> {
        val from = SINCE.atStartOfDay(zone).toInstant()
        val inWindow = sources.filter { !it.at.isBefore(from) && it.at.isBefore(until) }
        val batched = batched(inWindow.filter { it.kind == Kind.DAY })
        return inWindow.asSequence()
            .groupBy { Triple(it.kind, it.date, if (it.kind == Kind.DAY) 0L else it.id) }
            .values.map { same -> same.maxBy { it.at } }
            .filter { it !in batched }
            .mapNotNull { s ->
                meant(s.date, s.at.atZone(zone).toLocalDate(), weekStart)?.let { (viewed, meant) -> Line(s, viewed, meant) }
            }
            .toList()
    }

    /** The day changes that were part of one write of more than [BATCH_DAYS] days. */
    private fun batched(days: List<Source>): Set<Source> {
        val out = HashSet<Source>()
        var run = ArrayList<Source>()
        fun close() { if (run.mapTo(HashSet()) { it.date }.size > BATCH_DAYS) out += run; run = ArrayList() }
        for (s in days.sortedBy { it.at }) {
            if (run.isNotEmpty() && s.at.toEpochMilli() - run.last().at.toEpochMilli() > BATCH_GAP_MILLIS) close()
            run += s
        }
        close()
        return out
    }

    /**
     * The sheet's order (총괄 11-12): days still to come first, a day whose alarm went off first among them, then by date; days gone
     * by after them. Memo and money lines go last, folded, as do days gone by (the sheet folds them).
     */
    fun ordered(lines: List<Line>, today: LocalDate, alarmOff: (Line) -> Boolean): List<Line> =
        lines.sortedWith(
            compareBy<Line>(
                { it.source.kind != Kind.DAY },
                { it.date.isBefore(today) },
                { !(it.source.kind == Kind.DAY && !it.date.isBefore(today) && alarmOff(it)) },
                { it.date },
            ),
        )
}
