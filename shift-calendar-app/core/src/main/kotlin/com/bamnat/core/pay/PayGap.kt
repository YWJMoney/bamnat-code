package com.bamnat.core.pay

import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.report.ReportDoc
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * [SHFT-04] What came in for a month's pay against what the calendar worked out. [expected] is the
 * take-home estimate when there is one (else the pay before tax), so tax alone does not read as a gap.
 */
data class PayGap(val month: YearMonth, val expected: Long, val received: Long, val dates: List<LocalDate>) {
    /** Received less expected: below zero when less came in. */
    val diff: Long get() = received - expected
    /** Within [PayGaps.TOLERANCE] of the estimate. */
    val matches: Boolean get() = abs(diff) <= PayGaps.TOLERANCE

    /** "예상보다 52,300원 적게 들어왔어요". */
    val text: String
        get() = when {
            matches -> "예상과 거의 같게 들어왔어요"
            diff < 0 -> "예상보다 ${"%,d".format(-diff)}원 적게 들어왔어요"
            else -> "예상보다 ${"%,d".format(diff)}원 많이 들어왔어요"
        }
}

object PayGaps {
    /** Differences up to this many won are rounding, not news. */
    const val TOLERANCE = 1_000L
    /** Days around the pay day a 급여 line still counts for that month (weekends, holidays, early pay). */
    const val WINDOW_DAYS = 7L

    /**
     * The 급여 income lines that are [month]'s pay: within [WINDOW_DAYS] of [payDate] when the pay day is
     * known, else dated in the month itself. Null when none came in yet.
     */
    fun received(month: YearMonth, payDate: LocalDate?, entries: List<MoneyEntry>): Pair<Long, List<LocalDate>>? {
        val pay = entries.filter { it.kind == MoneyKind.INCOME && payAmount(it) > 0 }.filter { e ->
            if (payDate != null) !e.date.isBefore(payDate.minusDays(WINDOW_DAYS)) && !e.date.isAfter(payDate.plusDays(WINDOW_DAYS))
            else YearMonth.from(e.date) == month
        }
        if (pay.isEmpty()) return null
        return pay.sumOf(::payAmount) to pay.map { it.date }.distinct().sorted()
    }

    /** The gap for [month], or null until its pay came in or when nothing was worked out. */
    fun of(month: YearMonth, expected: Long?, payDate: LocalDate?, entries: List<MoneyEntry>): PayGap? {
        if (expected == null || expected <= 0) return null
        val (amount, dates) = received(month, payDate, entries) ?: return null
        return PayGap(month, expected, amount, dates)
    }

    /** What of a line is 급여: all of a 급여 line, or the 급여 share of a split one (its main part included). */
    private fun payAmount(e: MoneyEntry): Long = e.shareOf(MoneyCategories.SALARY)

    /**
     * D 1.32 [PAYA-04] 578 '덜 받은 돈': what came in short of the estimate, said plainly (무료). Null when it did not
     * come in short.
     */
    fun short(gap: PayGap): Long? = if (!gap.matches && gap.diff < 0) -gap.diff else null

    /**
     * D 1.32 [PAYA-04] (프로) the 근거표 and 정리본: the month's 세전 from the calendar, each 공제 the estimate took, the
     * estimate, what came in on which days, the gap, and where to ask. A page to show the company or a 상담 centre.
     */
    fun doc(gap: PayGap, gross: Long, deductions: List<Pair<String, Long>>, basis: String, payLines: List<Pair<String, Long>> = emptyList()): ReportDoc {
        fun w(v: Long) = "%,d원".format(v)
        val blocks = mutableListOf<ReportDoc.Block>(
            ReportDoc.Facts(listOf("달" to "${gap.month.year}년 ${gap.month.monthValue}월", "근무표로 계산한 급여(세전)" to w(gross),
                "예상 실수령" to "${w(gap.expected)} ($basis)", "들어온 돈" to "${w(gap.received)} (${gap.dates.joinToString(", ") { "${it.monthValue}/${it.dayOfMonth}" }})",
                "차이" to w(gap.diff))),
        )
        if (payLines.isNotEmpty()) {
            blocks += ReportDoc.Heading("세전 내역 (달력의 근무로 계산)")
            blocks += ReportDoc.Table(listOf("항목", "금액"), payLines.map { listOf(it.first, w(it.second)) }, listOf(3f, 1.5f), right = setOf(1))
        }
        if (deductions.isNotEmpty()) {
            blocks += ReportDoc.Heading("예상 공제")
            blocks += ReportDoc.Table(listOf("항목", "금액"), deductions.map { listOf(it.first, w(it.second)) }, listOf(3f, 1.5f), right = setOf(1))
        }
        blocks += ReportDoc.Paragraph("예상은 근무표와 급여 설정, 올해 4대보험·간이세액표로 계산한 참고값이에요. 회사 명세서와 줄마다 맞춰 보면 어디서 차이가 났는지 알 수 있어요.", small = true)
        blocks += ReportDoc.Paragraph(SlipAudit.ASK_NOTE, small = true)
        return ReportDoc("${gap.month.year}년 ${gap.month.monthValue}월 덜 받은 돈 정리", blocks, footer = "밤낮 · 덜 받은 돈 정리 · 참고용")
    }
}
