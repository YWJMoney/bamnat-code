package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.billing.WorkOffer
import com.bamnat.app.review.InAppReview.findActivity
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.core.tier.PlanBand
import com.bamnat.core.tier.WorkPlanKind
import com.bamnat.core.tier.WorkPlanRules

/**
 * 1.38 Phase 3 요금 (사용자 2026-11-05 20:22, 계획 8장): the ask for a 팀·직원 요금제, shown only at the moment the free taste ends (first
 * month's 확정 is behind, or the fourth staff member). Every band with its 월·연·1년 이용권, the one that fits marked; a band that is too
 * small is not offered; above the largest it says to ask. 개인 프로 is in it, 팀원 stay free, and what was made is never locked.
 */
@Composable
fun WorkPlanSheet(kind: WorkPlanKind, need: WorkPlanRules.Need, onDismiss: () -> Unit, onContact: (() -> Unit)? = null) {
    val context = LocalContext.current
    val c = context.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val offers by c.billing.work.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        c.telemetry.event(if (kind == WorkPlanKind.TEAM) com.bamnat.core.stats.UsageEvent.PLAN_VIEW_TEAM.key else com.bamnat.core.stats.UsageEvent.PLAN_VIEW_STAFF.key)
        runCatching { c.billing.refresh() }
    }
    // Bought (or changed): the need is met, the sheet closes by itself.
    LaunchedEffect(gate.hold(kind)?.cap) { if ((gate.hold(kind)?.cap ?: 0) >= need.count && need.count > 0) onDismiss() }
    val table = gate.plans
    val held = gate.hold(kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text(kind.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1.39 [FLOW-25] the band that fits, when its yearly plan starts free: 오늘 0원 · the day it turns paid · told before.
                offers.firstOrNull { it.kind == kind && it.cap == need.band?.cap && it.way == WorkOffer.Way.YEARLY && it.trial }?.let { o ->
                    if (held == null) Text(trialHead(com.bamnat.core.time.Today.date(), "연 ${o.price}"), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(WorkPlanRules.words(kind, need, table), style = MaterialTheme.typography.bodyMedium)
                Text(
                    (if (kind == WorkPlanKind.TEAM) "팀원은 늘 무료로 자기 근무를 받아요. " else "") +
                        "개인 프로 기능도 함께 들어 있어요. 끝나도 만든 ${if (kind == WorkPlanKind.TEAM) "근무표" else "급여 대장·명세서"}는 보고 내보낼 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (need.verdict == WorkPlanRules.Verdict.CONTACT) {
                    onContact?.let { OutlinedButton(onClick = { onDismiss(); it() }, modifier = Modifier.fillMaxWidth()) { Text("문의하기") } }
                }
                held?.takeIf { it.cap < Int.MAX_VALUE }?.let { h ->
                    Text("지금 ${h.cap}명 구간을 쓰고 있어요" + (if (h.pass) " (1년 이용권)" else if (h.trial) " (무료 체험 중)" else "") + ".",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (offers.none { it.kind == kind }) {
                    Text("Play 결제를 불러오는 중이에요. 아래 금액은 안내용이고, 실제 금액은 Play 결제 화면에 나와요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                table.bands(kind).forEach { band ->
                    BandCard(kind, band, fits = band.cap >= need.count, picked = band == need.band, held = held?.cap == band.cap,
                        offers = offers.filter { it.kind == kind && it.cap == band.cap }, passBlocked = held != null && !held.pass,
                        onBuy = { o ->
                            c.telemetry.event(com.bamnat.core.stats.UsageEvent.PLAN_BUY_TAP.key)
                            val a = context.findActivity()
                            if (a == null || !c.billing.buyWork(a, o)) message = "Play 결제 화면을 열지 못했어요. 잠시 뒤 다시 해 주세요."
                        })
                }
                Text("구간을 바꾸면 남은 기간은 Play가 비례로 계산해요. 해지는 Play 정기 결제에서 언제든 해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun BandCard(
    kind: WorkPlanKind, band: PlanBand, fits: Boolean, picked: Boolean, held: Boolean, offers: List<WorkOffer>, passBlocked: Boolean,
    onBuy: (WorkOffer) -> Unit,
) {
    val won = { v: Long -> "%,d원".format(v) }
    val monthly = offers.firstOrNull { it.way == WorkOffer.Way.MONTHLY }
    val yearly = offers.firstOrNull { it.way == WorkOffer.Way.YEARLY }
    val pass = offers.firstOrNull { it.way == WorkOffer.Way.PASS }
    Card(
        Modifier.fillMaxWidth(),
        colors = if (picked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${kind.who} ${band.cap}명까지", style = MaterialTheme.typography.titleSmall)
                if (picked) Text("· 맞는 구간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (held) Text("· 지금 구간", style = MaterialTheme.typography.labelLarge)
            }
            Text("월 ${monthly?.price ?: won(band.monthly)} · 연 ${yearly?.price ?: won(band.yearly)}(${band.yearlySaves}% 할인)",
                style = MaterialTheme.typography.bodySmall)
            if (!fits) {
                Text("지금 인원보다 작아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (held) return@Column
            yearly?.let { o ->
                if (o.trial) Text(trialHead(com.bamnat.core.time.Today.date(), "연 ${o.price}"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Button(onClick = { onBuy(o) }, modifier = Modifier.fillMaxWidth()) {
                    // 2.0.0 [RVO-07] (검토 PRO-13 "연 39,000원로") 조사를 금액에 맞게.
                    Text(if (o.trial) "1개월 무료로 시작 (연 ${o.price})" else "연 ${o.price}${com.bamnat.app.ui.Fmt.ro(o.price)} 구독")
                }
            }
            monthly?.let { o -> OutlinedButton(onClick = { onBuy(o) }, modifier = Modifier.fillMaxWidth()) { Text("월 ${o.price}${com.bamnat.app.ui.Fmt.ro(o.price)} 구독") } }
            // No double payment: the pass is for someone not subscribed to this kind.
            if (!passBlocked) pass?.let { o ->
                OutlinedButton(onClick = { onBuy(o) }, modifier = Modifier.fillMaxWidth()) { Text("1년 이용권 ${o.price} · 자동 결제 없음") }
            }
        }
    }
}
