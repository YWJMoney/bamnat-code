package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.padding
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GuideState
import com.bamnat.core.tier.ProOfferTimes
import com.bamnat.core.tier.RewardLedger
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle

/** 1.32 [PROX-02] The feature switch of the offer card (FeatureRegistry, 기능 켜고 끄기). */
const val MOMENTS_ID = "pro.moments"

/**
 * 1.36 840 (수익 2판 2-3, 사용자 2026-10-31 "이탈 0 · 확실한 보상"): the one 프로 card on the calendar, only at
 * [ProOfferTimes]' times: the second ad for one bundle in a month, two bundles by ads in a month, 5 of 7 days used, and back after
 * a month away (a bundle opens 7 days free, no offer). Its body is the user's own 보상 장부. 닫기 puts it away for the day,
 * '7일간 보지 않기' for a week, '그만 보기' turns it off (기능 켜고 끄기). Never with 프로, before the launch, in the first seconds
 * after the app opens or while the user is painting or writing. 자세히 opens the three-page [ProPaywall].
 */
@Composable
fun ProMomentNotice(style: WorkStyle?, @Suppress("UNUSED_PARAMETER") payday: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val guide = com.bamnat.app.data.GuideStore.get(context)
    val guideState by guide.state.collectAsStateWithLifecycle()
    val s by c.proMoments.state.collectAsStateWithLifecycle()
    val busy by com.bamnat.app.ui.components.HandsBusy.busy.collectAsStateWithLifecycle()
    val today = Today.date()
    LaunchedEffect(today) { c.proMoments.opened(today) }
    // Not in the first seconds after the app opens (광고 약속: 앱을 켠 직후에는 안 띄움).
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(SETTLE_MS); settled = true }
    if (!gate.live || gate.preview || gate.tier >= Tier.PRO || !settled || busy) return
    val feature = FeatureRegistry.get(MOMENTS_ID)
    if (feature != null && guideState.isOff(feature, GuideState.ALL)) return
    if (s.closedOn == today) return
    val showingToday = s.showingOn == today
    val due = remember(s, gate.leases, today) {
        ProOfferTimes.due(gate.leases, Today.instant(), today, Today.zone(), s.used + today, s.lastBefore,
            if (showingToday) s.offered - s.showing.orEmpty() else s.offered, s.snoozedUntil, if (showingToday) null else s.lastOffer)
    } ?: return
    val folded = com.bamnat.app.ui.calendar.LocalNoticeFolded.current
    LaunchedEffect(due.key, folded) {
        if (folded) return@LaunchedEffect
        c.proMoments.offerShown(due.key, today)
        if (due.time == ProOfferTimes.Time.BACK) due.bundle?.let { c.featureGate.back(it) }
        c.telemetry.event("pro_offer", due.time.key)
    }
    val plans by c.billing.plans.collectAsStateWithLifecycle()
    val words = offerWords(c, gate, due, style, plans.firstOrNull { it.yearly }?.priceMicros)
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words.first, style = MaterialTheme.typography.titleMedium)
            Text(words.second, style = MaterialTheme.typography.bodyMedium)
            // 1.36 PC Y13 (단추 4개가 무거워 보여요): 자세히 and 닫기 only; 7일간 보지 않기 and 그만 보기 under ⋯.
            var more by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (due.time != ProOfferTimes.Time.BACK) FilledTonalButton(onClick = { ProPaywall.show() }) { Text("자세히") }
                TextButton(onClick = { c.proMoments.closed(today) }) { Text("닫기") }
                Spacer(Modifier.weight(1f))
                if (due.time != ProOfferTimes.Time.BACK || feature != null) Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "더 보기") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        if (due.time != ProOfferTimes.Time.BACK) DropdownMenuItem(text = { Text("7일간 보지 않기") },
                            onClick = { more = false; c.proMoments.snooze(today) })
                        if (feature != null) DropdownMenuItem(
                            text = {
                                Column {
                                    Text("그만 보기")
                                    Text("설정 › 기능 켜고 끄기에서 다시 켜요", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { more = false; guide.setOn(feature, GuideState.ALL, false) })
                    }
                }
            }
        }
    }
}

/** The card's title and body: what happened, then the user's own 보상 장부 in a line. */
internal fun offerWords(c: AppContainer, gate: Gate, due: ProOfferTimes.Due, style: WorkStyle?, yearlyMicros: Long?): Pair<String, String> {
    val ledger = ledgerOf(c, gate, yearlyMicros)
    val perDay = RewardLedger.perDay(yearlyMicros)?.let { " 프로는 하루 ${it}원에 전부 열려 있어요." } ?: " 프로는 매달 전부 열려 있어요."
    return when (due.time) {
        ProOfferTimes.Time.SECOND_AD -> "${due.bundle?.label} 묶음을 이번 달 ${due.ads}번째 열었어요" to (ledgerLine(ledger) + perDay)
        // 1.36 PC Y18 (제목은 14일치, 본문은 21일치): the title counts what the body counts — the month's days in all, the ads beside.
        ProOfferTimes.Time.TWO_BUNDLES -> "이번 달 ${maxOf(ledger.days, due.days.toLong())}일치를 받았어요 · 광고 ${due.ads}편" to (ledgerLine(ledger) + perDay)
        ProOfferTimes.Time.HEAVY_USE -> "이번 주 거의 매일 밤낮을 썼어요" to
            ((if (ledger.found > 0) "올해 밤낮이 찾아 준 돈 ${won(ledger.found)}. " else com.bamnat.core.tier.ProPitch.headline(style) + ". ") + perDay.trim())
        ProOfferTimes.Time.BACK -> "다시 오셨네요" to
            "${due.bundle?.label} 묶음(${due.bundle?.gives})을 7일 동안 열어 뒀어요. 광고 없이 그냥 써요."
    }
}

/** This month's 보상 장부 for [gate]'s phone. */
internal fun ledgerOf(c: AppContainer, gate: Gate, yearlyMicros: Long?): RewardLedger.Month {
    val today = Today.date()
    val zone = Today.zone()
    val from = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
    val to = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant()
    return RewardLedger.month(gate.leases, from, to, emptyList(), yearlyMicros, c.proMoments.state.value.foundIn(today.year))
}

/** "이번 달 받은 것 급여 7일 · 돈 통계 7일 (14일치, 약 1,100원어치)." or "" when nothing was given. */
internal fun ledgerLine(m: RewardLedger.Month): String {
    if (m.days == 0L) return ""
    val value = m.wonValue?.let { ", 약 ${won(it)}어치" }.orEmpty()
    return "이번 달 받은 것 ${m.partsLine}" + (if (m.parts.size > 1) " (모두 ${m.days}일치$value)." else if (value.isNotEmpty()) " (${value.removePrefix(", ")})." else ".")
}

internal fun won(v: Long): String = "%,d원".format(v)

private const val SETTLE_MS = 4_000L
