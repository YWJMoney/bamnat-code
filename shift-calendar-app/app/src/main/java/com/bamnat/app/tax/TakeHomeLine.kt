package com.bamnat.app.tax

import com.bamnat.app.AppContainer
import com.bamnat.app.tier.Feature
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * [TAX-02] 세후 예상 beside a 세전 the pay engine worked out, for the pay screen's total and the summary over the calendar
 * (순번 380): the table's deductions × what the calendar's records taught ([TaxLearner]). Pro ([Feature.TAX_FORECAST]); null
 * when locked, without a pay setup or when nothing is taken off, so a free user sees the screens as before.
 */
object TakeHomeLine {
    fun of(profile: PayProfile, records: List<PayObservation>, gross: Long, month: YearMonth): TaxLearner.Forecast {
        val table = TaxLearner.Table.of(profile)
        return TaxLearner.forecast(month, gross, TaxLearner.fit(records, table), table)
    }

    suspend fun forecast(container: AppContainer, calendarId: String, profile: PayProfile?, gross: Long?, month: YearMonth): TaxLearner.Forecast? {
        if (profile == null || gross == null || gross <= 0) return null
        val gate = container.featureGate.state.value
        if (!gate.allows(Feature.TAX_FORECAST) && !gate.canTry(Feature.TAX_FORECAST)) return null
        val records = container.tax(calendarId).allObservations()
        // A pay setup that takes nothing off (세금 없음) with no records to learn from says nothing: "떼는 돈 0원" would mislead.
        val f = withContext(Dispatchers.Default) { of(profile, records, gross, month) }.takeIf { it.basis != TaxLearner.Basis.NONE } ?: return null
        // [MON-13] 1.36: the first 세후 예상 shown is its first use, so its free week starts here.
        return f.takeIf { container.featureGate.tryOrAsk(Feature.TAX_FORECAST) }
    }

    /** "세후 예상 2,612,000원 · 떼는 돈 388,000원 (기록 3달로 맞춤)". */
    fun text(f: TaxLearner.Forecast, won: (Long) -> String): String =
        "세후 예상 ${won(f.net)} · 떼는 돈 ${won(f.deducted)}" + (if (f.basis == TaxLearner.Basis.LEARNED) " (내 기록으로 맞춤)" else "")
}
