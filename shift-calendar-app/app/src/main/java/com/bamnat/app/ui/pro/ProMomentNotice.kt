package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ads.AdIds
import com.bamnat.app.appContainer
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GuideState
import com.bamnat.core.tier.AdRules
import com.bamnat.core.tier.ProMoments
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle

/** 1.32 [PROX-02] The feature switch of the money-day offer (FeatureRegistry, 기능 켜고 끄기). */
const val MOMENTS_ID = "pro.moments"

/**
 * 1.32 [PROX-02] (가계부 조사 5장, 근무유형 조사 11장): on a day the user's money is at stake, one card on the calendar
 * asks once whether to open that day's answer for free (the sheet's 광고 하루 이용권 / 체험 / 초대). Once a season each,
 * never two within two weeks, the same card the whole day it first shows. Never with 프로, never before the 정식 출시
 * switch, never when no rewarded ad can be offered today. '괜찮아요' puts it away for the day, '그만 보기' turns it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProMomentNotice(style: WorkStyle?, payday: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val guide = com.bamnat.app.data.GuideStore.get(context)
    val guideState by guide.state.collectAsStateWithLifecycle()
    val s by c.proMoments.state.collectAsStateWithLifecycle()
    val today = Today.date()
    if (!gate.live || gate.preview || gate.tier >= Tier.PRO) return
    val feature = FeatureRegistry.get(MOMENTS_ID)
    if (feature != null && guideState.isOff(feature, GuideState.ALL)) return
    val adsOn = rows[com.bamnat.core.flags.FlagCatalog.ADS]?.on == true
    if (!AdRules.rewardedOffer(adsOn, false, AdIds.rewarded.isNotBlank(), pro.dayPass, today)) return
    val moment = c.proMoments.today(s, today) { ProMoments.due(today, style, payday, s.rosterOn == today, s.offered, s.lastOffer) } ?: return
    if (style != null && style !in moment.styles) return
    val folded = com.bamnat.app.ui.calendar.LocalNoticeFolded.current
    LaunchedEffect(moment, folded) { if (!folded) c.proMoments.shown(moment, today) }
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(moment.title, style = MaterialTheme.typography.titleMedium)
            Text(moment.words, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { ProOffer.show(Feature.entries.firstOrNull { it.name == moment.feature }) }) { Text("오늘 하루 열어 보기") }
                TextButton(onClick = { c.proMoments.closed(today) }) { Text("괜찮아요") }
                if (feature != null) TextButton(onClick = { guide.setOn(feature, GuideState.ALL, false) }) { Text("그만 보기") }
            }
            Text("그만 보기를 눌러도 설정 › 기능 켜고 끄기에서 다시 켤 수 있어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
