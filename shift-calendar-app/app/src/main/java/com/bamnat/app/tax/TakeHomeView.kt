package com.bamnat.app.tax

import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxLearner
import java.time.YearMonth

/**
 * [TAX-02][TAX-03] What the 세금·실수령 card and sheet show for [month]: 세전 from the pay engine, 세후 예상 from the
 * records so far ([TaxLearner]), and last month's real pay, worked out off the main thread from the calendar's data.
 */
data class TakeHomeView(
    val month: YearMonth,
    /** The pay engine's 세전 of [month]; null without a pay setup. */
    val gross: Long?,
    val forecast: TaxLearner.Forecast?,
    /** The month before's record (typed, read or a deposit), the one to compare with. */
    val lastActual: PayObservation?,
    val fit: TaxLearner.Fit,
    val year: TaxLearner.YearSoFar,
    val records: List<PayObservation>,
) {
    val deposits: List<PayObservation> get() = records.filter { it.source == PayObservation.Source.DEPOSIT }
    val typed: List<PayObservation> get() = records.filter { it.source != PayObservation.Source.DEPOSIT }

    companion object {
        fun of(profile: PayProfile?, snapshot: ScheduleSnapshot?, records: List<PayObservation>, month: YearMonth): TakeHomeView {
            val table = profile?.let(TaxLearner.Table::of) ?: TaxLearner.Table(com.bamnat.core.pay.TaxMode.NONE)
            val gross = profile?.let { SalaryWatch.grossOf(it, snapshot) } ?: { null }
            val fit = TaxLearner.fit(records, table, gross)
            val g = gross(month)
            val last = TaxLearner.perMonth(records).firstOrNull { it.payMonth < month }
            return TakeHomeView(
                month = month, gross = g, forecast = g?.let { TaxLearner.forecast(month, it, fit, table) },
                lastActual = last, fit = fit, year = TaxLearner.yearSoFar(month.year, fit, table),
                records = records.sortedByDescending { it.payMonth },
            )
        }
    }
}
