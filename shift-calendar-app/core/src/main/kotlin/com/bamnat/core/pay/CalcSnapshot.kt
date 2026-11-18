package com.bamnat.core.pay

import java.time.LocalDate
import java.time.YearMonth

/**
 * A closed month: the result and the rate version it was made with, so later changes to rules
 * or rates never silently rewrite a past month [PAY-10]. Recalculating keeps the old snapshot.
 *
 * 2.0.0 [RVJ-18] 뿌리 고침 6 '굳은 달' (검토 PAYU-06·I05c-01): a closed month is one value everywhere it is shown. Besides the
 * lines it keeps each day's amounts ([days], so 날짜별 내역 adds up to the same 합계) and the 세후 as it was worked out that day
 * ([net]); [frozen] gives the month as every screen should show it. Months closed before 2.0.0 have no [days] ([hasDays] false):
 * their day list is the recalculation, said so in one line.
 */
data class CalcSnapshot(
    val month: YearMonth,
    val total: Long,
    val lines: List<PayLine>,
    val ratesVersion: String,
    val closedAtMillis: Long,
    /** Earlier snapshots of the same month, newest first, kept when the user recalculated. */
    val previous: List<CalcSnapshot> = emptyList(),
    /** [RVJ-18] The month's days as they were when closed. */
    val days: List<FrozenDay> = emptyList(),
    /** [RVJ-18] 세후 (통장에 들어올 돈) when closed, and the table's own figure beside it; null when it was not worked out. */
    val net: Long? = null,
    val tableNet: Long? = null,
    /** [RVJ-18] How [net] was reached ([com.bamnat.core.tax.TaxLearner.Basis] name). */
    val netBasis: String? = null,
) {
    /** Whether the days were kept (closed on 2.0.0 or later). */
    val hasDays: Boolean get() = days.isNotEmpty() || lines.isEmpty()

    /** [RVJ-18] The 세후 as kept when closed, for the pay screen, the 세후 card and the 월급 알림 alike; null when none was kept. */
    val takeHome: com.bamnat.core.tax.TaxLearner.Forecast?
        get() = net?.let { n ->
            com.bamnat.core.tax.TaxLearner.Forecast(month, total, n, tableNet ?: n,
                com.bamnat.core.tax.TaxLearner.Basis.entries.firstOrNull { it.name == netBasis } ?: com.bamnat.core.tax.TaxLearner.Basis.TABLE)
        }

    fun recalculated(result: MonthPay, atMillis: Long, takeHome: TakeHomeAt? = null): CalcSnapshot =
        close(result, atMillis, takeHome).copy(previous = listOf(copy(previous = emptyList())) + previous)

    /**
     * [RVJ-18] The month as every screen shows it: the closed lines and days over [fresh] (today's calculation, kept for its minutes,
     * weeks and checks). A month closed before 2.0.0 keeps [fresh]'s days.
     */
    fun frozen(fresh: MonthPay): MonthPay = fresh.copy(
        lines = lines,
        ratesVersion = ratesVersion,
        days = if (days.isEmpty()) fresh.days else days.map { d -> d.toDay(fresh.day(d.date)?.minutes ?: DayMinutes(d.date)) },
        // What was missing then is said by the closed lines; today's 'missing' would not add up to them.
        missing = emptyList(),
        afterTax = fresh.afterTax,
    )

    /** [RVJ-18] 세후 as kept when closed. */
    data class TakeHomeAt(val net: Long, val tableNet: Long, val basis: String)

    companion object {
        fun close(result: MonthPay, atMillis: Long, takeHome: TakeHomeAt? = null) = CalcSnapshot(
            result.month, result.total, result.lines, result.ratesVersion, atMillis,
            days = result.days.filter { it.parts.isNotEmpty() }.map(FrozenDay::of),
            net = takeHome?.net, tableNet = takeHome?.tableNet, netBasis = takeHome?.basis,
        )
    }
}

/** [RVJ-18] One day of a closed month: what the 날짜별 내역 shows, without the minutes behind it. */
data class FrozenDay(
    val date: LocalDate,
    val label: String,
    val holidayName: String?,
    val extras: List<String>,
    val parts: List<DayPart>,
) {
    val amount: Long get() = parts.sumOf { it.amount }
    fun toDay(minutes: DayMinutes) = DayPay(date, label, holidayName, extras, minutes, parts)

    companion object {
        fun of(d: DayPay) = FrozenDay(d.date, d.label, d.holidayName, d.extras, d.parts)
    }
}

/**
 * 2.0.0 [RVJ-18] 검토 PC-02: 날짜별 내역 and 합계 are one sum. The days carry what is earned on a day (시급의 기본, 연장·야간·휴일,
 * 근무마다 받는 수당); the rest is paid for the month as a whole (월급의 기본급, 주휴, 월 수당, 상여). [month] is that rest, so
 * [days] + [month] = 합계 always, and [kinds] says what it is made of.
 */
object DaySplit {
    /**
     * [pieces] is [month] by what it is made of, for the line's ⓘ (2.0.0 [RVJ-35] 검토 PC-86: '달로 주는 몫 2,799,993원' read as a
     * 기본급 of 2,800,000원 gone wrong; it is 기본급 2,800,000원 − 원 단위 끝전 7원).
     */
    data class Split(val days: Long, val month: Long, val kinds: List<String>, val pieces: List<Pair<String, Long>> = emptyList()) {
        val total: Long get() = days + month
    }

    fun of(p: MonthPay): Split {
        val days = p.days.sumOf { it.amount }
        val parts = p.days.flatMap { it.parts }
        val dayKinds = parts.groupBy { it.kind }.mapValues { e -> e.value.sumOf { it.amount } }
        // A kind worked out day by day rounds each day to the won: a few won apart from its line is 끝전, not a share of the month.
        val dayCounts = p.days.flatMap { d -> d.parts.map { it.kind }.distinct() }.groupingBy { it }.eachCount()
        var oddWon = false
        var odd = 0L
        val named = p.lines.groupBy { it.kind }.mapNotNull { (k, ls) ->
            val rest = ls.sumOf { it.amount } - (dayKinds[k] ?: 0)
            if (rest == 0L) return@mapNotNull null
            if (k in DAILY && kotlin.math.abs(rest) <= (dayCounts[k] ?: 0)) { oddWon = true; odd += rest; return@mapNotNull null }
            val word = when (k) {
                LineKind.BASE -> "기본급"
                LineKind.ALLOWANCE -> ls.filter { l -> p.days.none { d -> d.parts.any { it.label == l.label } } }.map { it.label }
                    .let { names -> if (names.any { it.startsWith("주휴") }) "주휴·월 수당" else "월 수당" }
                LineKind.LEAVE -> if (rest < 0) "무급 휴가 공제" else "유급휴가"
                LineKind.OVERTIME -> "주 40시간 넘는 연장"
                LineKind.NIGHT -> "야간"
                LineKind.HOLIDAY -> "휴일"
                LineKind.EXTRA -> "회사 규칙 몫"
                LineKind.EXTRA_HOURS -> "소정 외 근로"
            }
            word to rest
        }
        val words = named.map { it.first }.distinct() + listOfNotNull("원 단위 끝전".takeIf { oddWon })
        // What is left after the named pieces (a line of a kind the days carry in part) stays in the last named piece, so the pieces
        // always add up to the month's share.
        val pieces = named.groupBy({ it.first }, { it.second }).map { (w, v) -> w to v.sum() } +
            listOfNotNull(("원 단위 끝전" to odd).takeIf { odd != 0L })
        return Split(days, p.total - days, words, pieces.takeIf { it.sumOf { x -> x.second } == p.total - days } ?: emptyList())
    }

    /**
     * 2.0.0 [RVJ-35] PC-86: [Split.pieces] in words, "기본급 2,800,000원 − 원 단위 끝전 7원"; null with one piece or none (the line says it).
     * [won] writes an amount ("2,800,000원").
     */
    fun piecesText(split: Split, won: (Long) -> String): String? {
        if (split.pieces.size < 2) return null
        return split.pieces.mapIndexed { i, (name, v) ->
            val sign = if (v < 0) "− " else if (i == 0) "" else "+ "
            "$sign$name ${won(kotlin.math.abs(v))}"
        }.joinToString(" ")
    }

    /** Kinds the days carry in full, so what is left of them is rounding. */
    private val DAILY = setOf(LineKind.OVERTIME, LineKind.NIGHT, LineKind.HOLIDAY, LineKind.EXTRA, LineKind.EXTRA_HOURS)
}

/**
 * 2.0.0 [RVJ-18] 검토 U05-01·I05a-02·I05c-01: the pay day has passed and the month is not closed → "이대로 마감할까요?". Asked from
 * the day after the 급여일, or from the day the month's pay was seen arriving ([paidOn], 월급 입금 알림·적은 실수령) when that is
 * sooner, for [WINDOW_DAYS] days, then left (the month can still be closed by hand).
 */
object CloseAsk {
    const val WINDOW_DAYS = 14L

    fun due(month: YearMonth, payDate: LocalDate?, today: LocalDate, closed: Boolean, paidOn: LocalDate? = null): Boolean {
        if (closed) return false
        val from = listOfNotNull((payDate ?: month.plusMonths(1).atDay(1)).plusDays(1), paidOn).min()
        return !today.isBefore(from) && !today.isAfter(from.plusDays(WINDOW_DAYS - 1))
    }

    /**
     * 2.0.0 [RVJ-57] 검토 PC-144: the months before a first pay setup not to ask about. A phone set up today was asked '10월 급여, 이대로
     * 마감할까요?' for a month nothing was written in; the months [due] can reach back to (two) stay quiet unless work was [written] in
     * them or the setup came from that month's payslip ([slip]).
     */
    fun quietAtSetup(today: LocalDate, written: (YearMonth) -> Boolean, slip: YearMonth? = null): List<YearMonth> =
        (1L..2L).map { YearMonth.from(today).minusMonths(it) }.filter { it != slip && !written(it) }

    /**
     * 2.0.0 [RVJ-58] 검토 PC-145: '이 달 마감하기' before the month's work is over asks once ("아직 11월 근무가 남았어요"); a month whose
     * pay period has ended closes at once as before.
     */
    fun early(periodEnd: LocalDate, today: LocalDate): Boolean = !today.isAfter(periodEnd)
}
