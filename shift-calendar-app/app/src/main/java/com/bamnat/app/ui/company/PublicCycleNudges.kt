package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.PublicUse
import com.bamnat.core.plaza.CycleOrders
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicPay
import com.bamnat.core.plaza.PublicSetup

/**
 * [SPAT-04][SPAT-05] The way on after a public cycle went on the calendar, one card at a time: first the pay (V 간편 급여 with
 * the 업종's usual allowances), then, once the pay is set or the user said 다음에 to it, putting the cycle on the plaza with
 * the reward said first. Gone once something went up from it; asked again 14·30·90 days after each 다음에 (ShareAsk).
 */
@Composable
fun PublicWayNotice(modifier: Modifier = Modifier, onPay: () -> Unit, onShare: () -> Unit) {
    val c = LocalContext.current.appContainer
    // [CALI-04] 1.30 BB: the public cycle of the calendar on screen ([LocalCompanyCalendar]), with that calendar's pay.
    val scope = companyScope(c)
    val store = scope.store
    val use by store.publicUse.collectAsStateWithLifecycle()
    val profile by scope.pay.profile.collectAsStateWithLifecycle()
    val u = use ?: return
    if (u.sentId != null) return
    val cycle = u.cycle ?: return
    Box(modifier) {
        when (publicStep(c, u, profile != null)) {
            PublicStep.PAY -> PublicPayCard(cycle, u.codes, onPay = onPay, onLater = { store.updatePublicUse { it.copy(payClosed = true) } })
            PublicStep.SHARE -> PublicShareCard(
                u, cycle, hasPay = profile != null, reward = rewardShort(cachedTerms()), onShare = onShare,
                onLater = { store.updatePublicUse { it.later() } },
                onNever = { store.updatePublicUse { it.copy(inviteNever = true) } },
            )
            null -> Unit
        }
    }
}

internal enum class PublicStep { PAY, SHARE }

/** Which card [PublicWayNotice] shows now, if any. */
internal fun publicStep(c: AppContainer, u: PublicUse, hasPay: Boolean): PublicStep? = when {
    u.sentId != null -> null
    !hasPay && !u.payClosed -> PublicStep.PAY
    c.companyApi.enabled && u.inviteDue() -> PublicStep.SHARE
    else -> null
}

@Composable
private fun PublicPayCard(cycle: PublicCycle, codes: String, onPay: () -> Unit, onLater: () -> Unit) {
    val items = remember(cycle) { PublicPay.usualItems(cycle) }
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("급여도 넣어 볼까요?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                "‘${cycle.company}’ 근무주기에 맞춰 ${PublicPay.form(cycle, codes).label}로 시작해요." +
                    (if (items.isNotEmpty()) " ${items.joinToString("·")}도 미리 넣어 둘게요." else "") + " 기본급만 넣으면 이번 달 예상 급여가 나와요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPay, modifier = Modifier.heightIn(min = 48.dp)) { Text("급여 넣기") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text("다음에") }
            }
        }
    }
}

/** [SPAT-05] The reward first, then what goes and what does not, then 올리기 into the report form with its own consent. */
@Composable
private fun PublicShareCard(
    u: PublicUse,
    cycle: PublicCycle,
    hasPay: Boolean,
    reward: String?,
    onShare: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    val plan = remember(cycle, u.codes) { PublicSetup.plan(cycle, u.codes) }
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RewardPill(publicReward(reward))
            Text("‘${cycle.company}’ 근무주기를 광장에 올려 주실래요?", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(
                if (u.cycleId.isEmpty()) "처음 제공이에요. 같은 회사 분들이 검색해서 바로 맞춰요."
                else "공개 자료를 확인·고친 근무주기로 올라가요. 같은 회사 분들이 더 믿고 써요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Line(true, "근무 ${plan.types.filter { !it.isOff }.joinToString(" · ") { it.name }}" +
                (if (u.codes.isNotEmpty()) " · 순서 ${CycleOrders.spoken(u.codes)}" else ""))
            Line(hasPay, if (hasPay) "급여 계산 방식과 수당 이름 (금액 빼고)" else "급여 정보는 아직 없어서 근무주기만 가요")
            Line(false, "기본급·수당 금액, 이름·연락처는 가지 않아요")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("올리기") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text("다음에") }
                TextButton(onClick = onNever, modifier = Modifier.heightIn(min = 48.dp)) { Text("그만 볼게요") }
            }
        }
    }
}

@Composable
private fun Line(goes: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (goes) Icons.Filled.CheckCircle else Icons.Filled.Block, null, Modifier.size(16.dp).padding(top = 2.dp),
            tint = if (goes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
