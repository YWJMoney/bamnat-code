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
        return com.bamnat.core.pay.TakeHome.estimate(gross, mode, profile?.taxFamily ?: 1, month.atDay(1), profile?.taxChildren ?: 0,
            nonTaxable ?: profile?.let { com.bamnat.core.pay.TakeHome.nonTaxable(it, month) } ?: 0).net
    }

    val CHOICES = listOf(TaxMode.INSURANCE_4 to "4대보험·소득세", TaxMode.WITHHOLDING_3_3 to "3.3%", TaxMode.NONE to "떼지 않음")
}

/** [J133-01] The three ways as chips; none selected while [selected] is null. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaxWayChips(selected: TaxMode?, modifier: Modifier = Modifier, onPick: (TaxMode) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaxWay.CHOICES.forEach { (m, label) -> FilterChip(selected = selected == m, onClick = { onPick(m) }, label = { Text(label) }) }
    }
}
