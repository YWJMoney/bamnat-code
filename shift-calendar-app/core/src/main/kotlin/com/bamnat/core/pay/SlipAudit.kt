package com.bamnat.core.pay

import java.time.YearMonth
import kotlin.math.abs

/**
 * [PAYX-36][PAYX-41] "내 월급 맞나요?": a payslip line by line against what the calendar and the settings make of the
 * month. The 공제 lines go next to [TakeHome]'s estimate, and the pay lines that differ fall in two baskets — lines
 * the company seems to have paid short, and lines the app seems not to know about — so the user knows whether to
 * ask the company or fix the calendar (research/1.23-리뷰 K I1).
 */
object SlipAudit {
    /** One 공제 line: what the payslip took, and what the app expects for the same 지급 총액. */
    data class Deduction(val name: String, val slip: Long?, val estimate: Long?) {
        val diff: Long? get() = if (slip != null && estimate != null) slip - estimate else null
    }

    /** The order TakeHome writes its 공제 in, and the words a payslip uses for each. */
    private val DEDUCTIONS: List<Pair<String, List<String>>> = listOf(
        "국민연금" to listOf("국민연금", "연금"),
        "건강보험" to listOf("건강보험", "건강"),
        "장기요양" to listOf("장기요양", "요양"),
        "고용보험" to listOf("고용보험", "고용"),
        TakeHome.TAX_LABEL to listOf("근로소득세", "소득세", "갑근세"),
        TakeHome.LOCAL_TAX_LABEL to listOf("지방소득세", "지방세", "주민세"),
    )

    private fun norm(s: String) = s.filterNot { it.isWhitespace() }

    /** Which of the five (six with 장기요양) a payslip's 공제 line is, or null for another (조합비, 식대 공제 …). */
    fun deductionOf(name: String): String? {
        val n = norm(name)
        // 지방소득세 before 소득세, 장기요양 before 건강, since one name holds the other.
        val order = listOf(5, 2, 0, 1, 3, 4)
        return order.map { DEDUCTIONS[it] }.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.first
    }

    /**
     * The payslip's 4대보험·소득세 lines against [TakeHome.estimate] on the payslip's own 지급 총액 (4대보험 setting,
     * the profile's 가족 수 and 비과세). Lines the payslip does not have are shown with the estimate only.
     */
    fun deductions(read: PayslipRead, profile: PayProfile, month: YearMonth): List<Deduction> {
        val gross = read.gross ?: return emptyList()
        val est = TakeHome.estimate(gross, TaxMode.INSURANCE_4, profile.taxFamily, month.atDay(1), profile.taxChildren,
            TakeHome.nonTaxable(profile, month)).deductions.toMap()
        val slip = read.lines.filter { it.role == SlipRole.DEDUCTION && it.amount != null }
            .groupBy { deductionOf(it.name) }.filterKeys { it != null }.mapValues { (_, l) -> l.sumOf { it.amount!! } }
        return DEDUCTIONS.map { (name, _) -> Deduction(name, slip[name], est[name]) }.filter { it.slip != null || (it.estimate ?: 0) > 0 }
    }

    /**
     * E 1.32 [LEDX-11] 591: what one 공제 line is and how it is worked out, in words, for [gross] on [month]:
     * the rate the law sets this year, the base it is taken on and where the payslip may differ (국민연금 is last year's
     * 소득, 소득세 the 간이세액표 at the 80·100·120% the company picked).
     */
    fun explain(name: String, gross: Long, month: YearMonth): String {
        val sh = TakeHome.sharesOn(month.atDay(1))
        fun pct(v: Double) = "%.3f".format(v * 100).trimEnd('0').trimEnd('.') + "%"
        fun w(v: Long) = "%,d원".format(v)
        return when (name) {
            "국민연금" -> "기준소득월액(보통 작년 소득으로 정해요, ${w(sh.baseMin)}~${w(sh.baseMax)})의 ${pct(sh.pension)}. 회사도 같은 만큼 내요. 올해 월급과 달라도 7월에 바뀌기 전까지는 그대로예요."
            "건강보험" -> "보수월액(비과세 뺀 월급) ${w(gross)}의 ${pct(sh.health)}. 회사도 같은 만큼 내요. 다음 해 4월에 1년치를 다시 셈해 더 내거나 돌려받아요."
            "장기요양" -> "건강보험료의 ${pct(sh.careOfHealth)}(소득의 약 ${pct(sh.health * sh.careOfHealth)}). 건강보험료를 따라가요."
            "고용보험" -> "보수월액의 ${pct(sh.employment)}(실업급여). 회사는 이보다 더 내요."
            TakeHome.TAX_LABEL -> "근로소득 간이세액표(소득세법 시행령 별표 2)로 비과세 뺀 월급과 공제대상가족 수에 따라 정해요. 회사가 80·100·120% 중 고를 수 있고, 1년치는 연말정산에서 맞춰요."
            TakeHome.LOCAL_TAX_LABEL -> "소득세의 10%(지방세법 제103조의13). 소득세를 따라가요."
            else -> "회사마다 따로 빼는 돈이에요(조합비·식대·사우회비 등). 명세서나 회사에 물어보세요."
        }
    }

    enum class Basket(val label: String, val hint: String) {
        COMPANY_LESS("회사가 적게 준 것 같은 줄", "근무표대로면 더 받아야 해요. 회사에 계산 방법을 물어보세요."),
        NOT_ENTERED("앱에 안 넣은 것 같은 줄", "명세서가 더 많아요. 달력에 빠진 근무나 앱에 없는 수당이 있는지 봐 주세요."),
    }

    /** One pay line that differs by more than [tolerance]: the payslip's amount, the app's, and its basket. */
    data class Gap(val label: String, val slip: Long, val app: Long, val basket: Basket) {
        val diff: Long get() = slip - app
    }

    private val PAIRS: List<Triple<String, (Payslip) -> Long?, Set<LineKind>>> = listOf(
        Triple("기본급", { s -> s.base }, setOf(LineKind.BASE)),
        Triple("연장근로수당", { s -> s.overtimePay }, setOf(LineKind.OVERTIME, LineKind.EXTRA_HOURS)),
        Triple("야간근로수당", { s -> s.nightPay }, setOf(LineKind.NIGHT)),
        Triple("휴일근로수당", { s -> s.holidayPay }, setOf(LineKind.HOLIDAY)),
    )

    /**
     * The pay lines of [slip] against [pay] (the month with the settings the user keeps or the match found): each
     * premium and the 기본급 the payslip gives, then each allowance by name; one the app does not have at all is
     * [Basket.NOT_ENTERED].
     */
    fun gaps(slip: Payslip, pay: MonthPay, tolerance: Long = 1_000): List<Gap> {
        val out = mutableListOf<Gap>()
        for ((label, get, kinds) in PAIRS) {
            val s = get(slip) ?: continue
            val a = pay.lines.filter { it.kind in kinds }.sumOf { it.amount }
            if (abs(s - a) > tolerance) out += Gap(label, s, a, if (s < a) Basket.COMPANY_LESS else Basket.NOT_ENTERED)
        }
        for ((name, s) in slip.allowances) {
            val a = pay.lines.filter { it.kind == LineKind.ALLOWANCE && norm(it.label) == norm(name) }.sumOf { it.amount }
            if (abs(s - a) > tolerance) out += Gap(name, s, a, if (s < a) Basket.COMPANY_LESS else Basket.NOT_ENTERED)
        }
        return out
    }

    /**
     * D 1.32 [PAYA-02] 576: why a premium line differs. [RATE]: the hours agree and the money does not (시급·배율·끝자리);
     * [HOURS]: the hours themselves differ (a shift missing or extra on the calendar, or the company closing the month on
     * another day); [SAME]: both agree.
     */
    enum class Cause(val label: String, val hint: String) {
        SAME("맞아요", ""),
        RATE("요율 문제", "시간은 같은데 금액이 달라요. 시급(통상임금)이나 배율, 원 미만 처리가 회사와 달라요. ‘명세서로 회사 방식 맞추기’가 맞춰 줘요."),
        HOURS("근무 기록·마감 문제", "시간부터 달라요. 달력에 빠지거나 더 적힌 근무가 있는지, 회사 정산 기간(마감일)이 앱과 같은지 봐 주세요."),
    }

    /** D 1.32 [PAYA-02] One premium line: the payslip's hours and money next to the calendar's, and the [Cause]. */
    data class HourRow(val label: String, val slipMinutes: Int, val appMinutes: Int, val slipPay: Long?, val appPay: Long, val cause: Cause) {
        val minutesDiff: Int get() = slipMinutes - appMinutes
    }

    /** Hours within this many minutes are the same (a payslip rounds to the half hour, the calendar to the minute). */
    const val SAME_MINUTES = 30

    private val HOUR_PAIRS: List<Triple<String, (Payslip) -> Pair<Int?, Long?>, Set<LineKind>>> = listOf(
        Triple("연장", { s -> s.overtimeMinutes to s.overtimePay }, setOf(LineKind.OVERTIME, LineKind.EXTRA_HOURS)),
        Triple("야간", { s -> s.nightMinutes to s.nightPay }, setOf(LineKind.NIGHT)),
        Triple("휴일", { s -> s.holidayMinutes to s.holidayPay }, setOf(LineKind.HOLIDAY)),
    )

    /** D 1.32 [PAYA-02] Each premium line whose hours the payslip gives, against the calendar's. */
    fun hours(slip: Payslip, pay: MonthPay, tolerance: Long = 1_000): List<HourRow> = HOUR_PAIRS.mapNotNull { (label, get, kinds) ->
        val (minutes, money) = get(slip)
        if (minutes == null || minutes <= 0) return@mapNotNull null
        val mine = pay.lines.filter { it.kind in kinds }
        val appMinutes = mine.sumOf { it.minutes }
        val appPay = mine.sumOf { it.amount }
        val sameHours = abs(minutes - appMinutes) <= SAME_MINUTES
        val samePay = money == null || abs(money - appPay) <= tolerance
        HourRow(label, minutes, appMinutes, money, appPay, when {
            !sameHours -> Cause.HOURS
            !samePay -> Cause.RATE
            else -> Cause.SAME
        })
    }

    /** D 1.32 [PAYA-02] The one sentence over the rows: what to look at first. */
    fun hoursVerdict(rows: List<HourRow>): String? = when {
        rows.isEmpty() -> null
        rows.all { it.cause == Cause.SAME } -> "연장·야간·휴일 시간과 금액이 명세서와 맞아요"
        rows.any { it.cause == Cause.HOURS } -> "근무 시간부터 달라요: ${rows.filter { it.cause == Cause.HOURS }.joinToString(", ") { it.label }}. 달력 기록과 마감일을 먼저 봐 주세요"
        else -> "시간은 맞고 금액이 달라요: ${rows.filter { it.cause == Cause.RATE }.joinToString(", ") { it.label }}. 시급·배율이 회사와 달라요"
    }

    fun hoursText(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}시간" else "%.1f시간".format(minutes / 60.0)

    /** [PAYX-41] The comparison as a page to keep or show: both baskets, the 공제, and where to ask. */
    fun doc(month: YearMonth, gross: Long, appTotal: Long, gaps: List<Gap>, deductions: List<Deduction>, belowLaw: Long?,
            hours: List<HourRow> = emptyList()): com.bamnat.core.report.ReportDoc {
        fun w(v: Long) = "%,d원".format(v)
        val blocks = mutableListOf<com.bamnat.core.report.ReportDoc.Block>(
            com.bamnat.core.report.ReportDoc.Facts(listOf("달" to "${month.year}년 ${month.monthValue}월", "명세서 지급 총액" to w(gross), "앱 계산 (세전)" to w(appTotal), "차이" to w(gross - appTotal))),
        )
        Basket.entries.forEach { b ->
            val mine = gaps.filter { it.basket == b }
            if (mine.isEmpty()) return@forEach
            blocks += com.bamnat.core.report.ReportDoc.Heading(b.label)
            blocks += com.bamnat.core.report.ReportDoc.Table(listOf("항목", "명세서", "앱", "차이"), mine.map { listOf(it.label, w(it.slip), w(it.app), w(it.diff)) }, listOf(2f, 1.3f, 1.3f, 1.2f), right = setOf(1, 2, 3))
            blocks += com.bamnat.core.report.ReportDoc.Paragraph(b.hint, small = true)
        }
        if (hours.isNotEmpty()) {
            // D 1.32 [PAYA-02]
            blocks += com.bamnat.core.report.ReportDoc.Heading("시간 비교")
            blocks += com.bamnat.core.report.ReportDoc.Table(listOf("항목", "명세서 시간", "달력 시간", "판단"),
                hours.map { listOf(it.label, hoursText(it.slipMinutes), hoursText(it.appMinutes), it.cause.label) }, listOf(1.2f, 1.3f, 1.3f, 1.6f), right = setOf(1, 2))
            hoursVerdict(hours)?.let { blocks += com.bamnat.core.report.ReportDoc.Paragraph(it, small = true) }
        }
        belowLaw?.let { blocks += com.bamnat.core.report.ReportDoc.Paragraph("법 기준으로 계산하면 이 달 ${w(it)} 더 많아요.") }
        if (deductions.isNotEmpty()) {
            blocks += com.bamnat.core.report.ReportDoc.Heading("공제")
            blocks += com.bamnat.core.report.ReportDoc.Table(listOf("항목", "명세서", "앱 예상"), deductions.map { listOf(it.name, it.slip?.let(::w) ?: "없음", it.estimate?.let(::w) ?: "-") },
                listOf(2f, 1.3f, 1.3f), right = setOf(1, 2))
        }
        blocks += com.bamnat.core.report.ReportDoc.Paragraph(ASK_NOTE, small = true)
        return com.bamnat.core.report.ReportDoc("${month.year}년 ${month.monthValue}월 명세서 대조", blocks, footer = "밤낮 · 명세서 대조 · 참고용")
    }

    /** "회사가 적게 준 것 같아요" goes with the 1350 상담 (근로기준법 제43조 임금 전액 지급, 제56조 가산임금). */
    const val ASK_NOTE = "적게 받은 것 같으면 회사에 먼저 물어보고, 그래도 다르면 고용노동부 고객상담센터(국번 없이 1350)에 상담할 수 있어요 " +
        "(근로기준법 제43조·제56조)."
}
