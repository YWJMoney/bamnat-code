package com.bamnat.core.tax

import com.bamnat.core.money.Withholding
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * [TAX-02] 세후 예상 that learns this person's payslip (research/1.25-W-조사.md 1-2, 결정 W-D2).
 *
 * The official 간이세액표 and the 4대보험 rates ([TakeHome]) give a table amount; a real payslip differs by the
 * 80%·120% the employer may pick (소득세법 시행령 제194조 제1항), 비과세, the family count and the company's own
 * deductions. One ratio k = (세전 − 실수령) ÷ table deductions, the median of the recent months, carries all of that,
 * and since it multiplies the table, a month with more 연장·야간 still climbs the 누진 steps the table has. With no
 * record k is 1: the table itself. 3.3% and 일용 are fixed by law and need no learning.
 */
object TaxLearner {
    /** Recent months a fit looks at. */
    const val RECENT = 6
    /** A month whose k is this far from the others' median is 연말정산, a bonus or a typo, and is left out. */
    const val OUTLIER = 0.40
    val K_RANGE = 0.3..3.0

    /** How the table is read for this person: [PayProfile]'s tax setting. */
    data class Table(
        val mode: TaxMode,
        val family: Int = 1,
        val children: Int = 0,
        val nonTaxable: (YearMonth) -> Long = { 0L },
    ) {
        /** The table's deductions on [gross] for [month] in [asMode]. */
        fun deductions(gross: Long, month: YearMonth, asMode: TaxMode = mode): Long =
            TakeHome.estimate(gross, asMode, family, month.atDay(1), children, nonTaxable(month).coerceAtMost(gross)).deductions.sumOf { it.second }

        companion object {
            fun of(profile: PayProfile): Table =
                Table(profile.taxMode, profile.taxFamily, profile.taxChildren) { m -> TakeHome.nonTaxable(profile, m) }
        }
    }

    enum class Basis(val words: String) {
        TABLE("간이세액표·4대 보험 요율로 계산했어요"),
        LEARNED("실제 들어온 월급으로 맞춘 공제를 썼어요"),
        LAW("법에 정해진 비율이에요"),
        NONE("떼는 돈이 없어요"),
    }

    /** One record as the fit saw it. */
    data class Sample(
        val observation: PayObservation,
        val gross: Long,
        val deducted: Long,
        val table: Long,
        /** deducted ÷ table; null when the table takes nothing. */
        val k: Double?,
        val used: Boolean,
        val why: String = "",
    )

    data class Fit(
        /** The mode the forecast uses: the profile's, or the one the records show when the profile says none. */
        val mode: TaxMode,
        val k: Double,
        val basis: Basis,
        val samples: List<Sample>,
        /** True when [mode] came from the records, not from the pay setting. */
        val guessedMode: Boolean = false,
    ) {
        val used: List<Sample> get() = samples.filter { it.used }
        val left: List<Sample> get() = samples.filter { !it.used }

        /** "실제 3달로 맞췄어요 · 표보다 8% 더 떼요". */
        val words: String
            get() = when (basis) {
                Basis.LEARNED -> {
                    val pct = ((k - 1) * 100).roundToLong()
                    "실제 ${used.size}달로 맞췄어요" + when {
                        abs(pct) < 2 -> " · 표와 거의 같아요"
                        pct > 0 -> " · 표보다 $pct% 더 떼요"
                        else -> " · 표보다 ${-pct}% 덜 떼요"
                    } + (if (left.isNotEmpty()) " · 연말정산·상여로 보이는 ${left.size}달은 뺐어요" else "")
                }
                else -> basis.words
            }
    }

    data class Forecast(
        val month: YearMonth,
        val gross: Long,
        val net: Long,
        /** What the table alone leaves, to show beside it. */
        val tableNet: Long,
        val basis: Basis,
    ) {
        val deducted: Long get() = gross - net
    }

    /**
     * Fits [observations] for [table]. [grossOf] gives a pay month's 세전 from the pay engine, for a record that only
     * knows the deposit; a record without either is shown but not used.
     */
    fun fit(observations: List<PayObservation>, table: Table, grossOf: (YearMonth) -> Long? = { null }): Fit {
        val recent = perMonth(observations).take(RECENT)
        val withGross = recent.mapNotNull { o -> (o.gross ?: grossOf(o.payMonth))?.takeIf { it > 0 }?.let { o to it } }
        val mode = if (table.mode != TaxMode.NONE) table.mode else guessMode(withGross.map { (o, g) -> g to (g - o.net) })
        val guessed = table.mode == TaxMode.NONE && mode != TaxMode.NONE
        if (mode != TaxMode.INSURANCE_4) {
            val samples = withGross.map { (o, g) -> Sample(o, g, (g - o.net).coerceAtLeast(0), table.deductions(g, o.payMonth, mode), null, true) }
            return Fit(mode, 1.0, if (mode == TaxMode.NONE) Basis.NONE else Basis.LAW, samples, guessed)
        }
        val raw = withGross.map { (o, g) ->
            val ded = (g - o.net).coerceAtLeast(0)
            val t = table.deductions(g, o.payMonth, mode)
            val k = if (t > 0) ded.toDouble() / t else null
            Sample(o, g, ded, t, k, used = k != null && k in K_RANGE, why = if (k == null || k !in K_RANGE) "표와 너무 달라요" else "")
        }
        val first = median(raw.filter { it.used }.mapNotNull { it.k })
        val unknown = recent.filter { o -> withGross.none { it.first === o } }.map { o -> Sample(o, 0, 0, 0, null, false, "세전을 몰라요") }
        val samples = (if (first == null) raw else raw.map { s ->
            if (s.used && abs(s.k!! - first) / first > OUTLIER) s.copy(used = false, why = "연말정산·상여로 보여요") else s
        }) + unknown
        val k = median(samples.filter { it.used }.mapNotNull { it.k })
        return if (k == null) Fit(mode, 1.0, Basis.TABLE, samples, guessed) else Fit(mode, k, Basis.LEARNED, samples, guessed)
    }

    /**
     * One record per pay month, newest first: a typed or payslip one wins (the latest of them); otherwise the month's
     * deposits added up, as pay split over two transfers is still one month's pay.
     */
    fun perMonth(observations: List<PayObservation>): List<PayObservation> =
        observations.filter { it.kind == IncomeKind.SALARY || it.kind == IncomeKind.HOURLY }.groupBy { it.payMonth }
            .map { (_, list) ->
                list.filter { it.source != PayObservation.Source.DEPOSIT }.maxByOrNull { it.createdAt * 1_000 + it.id }
                    ?: list.first().copy(net = list.sumOf { it.net }, gross = list.mapNotNull { it.gross }.takeIf { it.size == list.size }?.sum())
            }
            .sortedByDescending { it.payMonth }

    /** The month's 세후 for [gross] with [fit]: table deductions × k, cut to 10원. */
    fun forecast(month: YearMonth, gross: Long, fit: Fit, table: Table): Forecast {
        val t = table.deductions(gross, month, fit.mode)
        val learned = if (fit.basis == Basis.LEARNED) (t * fit.k).roundToLong() / 10 * 10 else t
        val ded = learned.coerceIn(0, gross.coerceAtLeast(0))
        return Forecast(month, gross, gross - ded, gross - t, fit.basis)
    }

    /**
     * The records' own mode when the pay setting says none: nothing off (under 0.5%), 3.3% (within 0.4%p), else
     * 4대보험, by the most recent records' majority.
     */
    fun guessMode(pairs: List<Pair<Long, Long>>): TaxMode {
        if (pairs.isEmpty()) return TaxMode.NONE
        val votes = pairs.map { (g, d) ->
            val rate = d.toDouble() / g
            when {
                rate < 0.005 -> TaxMode.NONE
                abs(rate - 0.033) <= 0.004 -> TaxMode.WITHHOLDING_3_3
                else -> TaxMode.INSURANCE_4
            }
        }
        val counts = votes.groupingBy { it }.eachCount()
        val best = counts.values.max()
        // A tie goes to the most recent record's way.
        return votes.first { counts[it] == best }
    }

    private fun median(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** [TAX-04] 3.3% of [amount] (소득세 3% + 지방소득세 그 10%), as [Withholding]. */
    fun freelanceNet(amount: Long): Long = amount - Withholding.of(amount)

    /**
     * [TAX-04] 일용근로자 one day's 원천징수 on [dailyPay] (research/1.25-W-조사.md 1-3): (일당 − 15만 원) × 6% (소득세법
     * 제47조 제2항, 제129조 제1항 제4호) less 55% 세액공제 (제59조 제3항); under 1,000원 none is held back (제86조);
     * 지방소득세 a tenth more.
     */
    fun dailyWithheld(dailyPay: Long): Long {
        val tax = ((dailyPay - DAILY_DEDUCTION).coerceAtLeast(0) * 6 / 100) * 45 / 100
        if (tax < 1_000) return 0
        val t = tax / 10 * 10
        return t + t / 10 / 10 * 10
    }

    const val DAILY_DEDUCTION = 150_000L

    /** [TAX-03] The year so far: what came off the records, split by the table's own share of tax and 4대보험. */
    data class YearSoFar(val year: Int, val months: Int, val gross: Long, val net: Long, val tax: Long, val insurance: Long) {
        val deducted: Long get() = gross - net
    }

    fun yearSoFar(year: Int, fit: Fit, table: Table): YearSoFar {
        val inYear = fit.samples.filter { it.gross > 0 && it.observation.payMonth.year == year }
        var tax = 0L
        var ins = 0L
        for (s in inYear) {
            val lines = TakeHome.estimate(s.gross, fit.mode, table.family, s.observation.payMonth.atDay(1), table.children,
                table.nonTaxable(s.observation.payMonth).coerceAtMost(s.gross)).deductions
            val taxPart = lines.filter { it.first == TakeHome.TAX_LABEL || it.first == TakeHome.LOCAL_TAX_LABEL || it.first.startsWith("원천징수") }.sumOf { it.second }
            val all = lines.sumOf { it.second }
            val share = if (all > 0) taxPart.toDouble() / all else 1.0
            val t = (s.deducted * share).roundToLong()
            tax += t
            ins += s.deducted - t
        }
        return YearSoFar(year, inYear.size, inYear.sumOf { it.gross }, inYear.sumOf { it.gross - it.deducted }, tax, ins)
    }
}
