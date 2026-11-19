package com.bamnat.app.tax

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.data.WorkTypePrefs
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.taxKnown

/**
 * [J133-01][J133-02] 1.33 (보고서 A3·A10): the one tax way every 세후 on screen uses. Before, 근무·급여's cards read the
 * stats card's own choice (4대보험 unless changed) while 가계부 read the pay setup's (떼지 않음 unless changed), so the same
 * month showed 3,791,302원 there and the 세전 4,519,552원 here as 실수령. Now the pay setup holds it, and a setup that never
 * asked says no 세후 at all.
 */
object TaxWay {
    /** The way to use: the pay setup's when picked; null when the setup never asked. Without a setup, the stats card's. */
    fun of(profile: PayProfile?, statsChoice: TaxMode?): TaxMode? =
        if (profile != null) profile.taxMode.takeIf { profile.taxKnown } else statsChoice

    /** Said where a 세후 would be, while the way is not picked. */
    const val UNKNOWN = "세후는 세금 방식을 고르면 보여요"

    /** Keeps [mode] for [calendarId]: in the pay setup (picked) and the stats card's choice, so both say the same. */
    suspend fun pick(container: AppContainer, calendarId: String, mode: TaxMode) {
        WorkTypePrefs.get(container.appContext).setTaxMode(calendarId, mode)
        container.scopeOf(calendarId).pay.update { it.copy(taxMode = mode, taxChosen = true) }
    }

    /**
     * [J133-02] The month's 세후: the one the records taught (프로) when there is one, else the table for the way. Null when
     * no way is picked. 가계부's [TakeHomeView] reads the same table ([com.bamnat.core.tax.TaxLearner.Table.of]), so both
     * screens say one number.
     */
    fun net(
        profile: PayProfile?, gross: Long, month: java.time.YearMonth, nonTaxable: Long? = null,
        learned: com.bamnat.core.tax.TaxLearner.Forecast? = null, statsChoice: TaxMode? = null,
    ): Long? {
        if (learned?.basis == com.bamnat.core.tax.TaxLearner.Basis.LEARNED) return learned.net
        val mode = of(profile, statsChoice) ?: return null
        return com.bamnat.core.pay.TakeHome.of(profile, month, gross, mode, nonTaxable).net
    }

    val CHOICES = listOf(TaxMode.INSURANCE_4 to "4대보험·소득세", TaxMode.WITHHOLDING_3_3 to "3.3%", TaxMode.NONE to "떼지 않음")

    /** 2.0.0 [RVJ-80] 검토 T04-05: the 일용근로 chip's word. */
    const val DAILY_CHOICE = "일용근로"

    /**
     * 2.0.0 [RVJ-80] 검토 T04-05 (현장 일당): a 일당 setup ([daily]) gets 일용근로 first, the way a 일용 일당 is taxed; any other only
     * while it is the one picked (a 월급 that was a 일당), so the chip that holds the way is never missing.
     */
    fun choices(daily: Boolean, selected: TaxMode? = null): List<Pair<TaxMode, String>> =
        if (daily || selected == TaxMode.DAILY_WORKER) listOf(TaxMode.DAILY_WORKER to DAILY_CHOICE) + CHOICES else CHOICES

    /** [RVJ-80] True for a 일당 setup, the one 일용근로 is offered to. */
    fun daily(profile: PayProfile?): Boolean = profile?.wageType == com.bamnat.core.pay.WageType.DAILY
}

/** [J133-01] The ways as chips; none selected while [selected] is null. [daily]: a 일당 setup's, with 일용근로 ([RVJ-80]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaxWayChips(selected: TaxMode?, modifier: Modifier = Modifier, daily: Boolean = false, onPick: (TaxMode) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaxWay.choices(daily, selected).forEach { (m, label) -> FilterChip(selected = selected == m, onClick = { onPick(m) }, label = { Text(label) }) }
    }
}
