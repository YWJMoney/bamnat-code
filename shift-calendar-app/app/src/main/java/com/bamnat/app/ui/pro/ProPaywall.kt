package com.bamnat.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.billing.Plan
import com.bamnat.app.review.InAppReview.findActivity
import com.bamnat.core.tier.ProPitch
import com.bamnat.core.tier.RewardLedger
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.36 840 세 장 페이월 (수익 3판 2절): from an offer card's 자세히. Page 1 the user's own numbers (밤낮이 찾아 준 돈, this month's
 * 보상 장부), skipped when there are none; page 2 what 프로 adds for their kind of work, five lines; page 3 the plans, the yearly
 * one first and marked 추천 (none picked). 닫기 and '7일간 보지 않기' on every page.
 */
object ProPaywall {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    /** 1.36 PC P21: a lock's '1개월 무료로 써 보기' opens straight on the plans. */
    var plansFirst = false
        private set
    fun show(plans: Boolean = false) { plansFirst = plans; _open.value = true }
    fun close() { _open.value = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallHost(c: AppContainer) {
    val open by ProPaywall.open.collectAsStateWithLifecycle()
    if (!open) return
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = ProPaywall::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ProPaywallContent(c, onClose = ProPaywall::close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProPaywallContent(c: AppContainer, onClose: () -> Unit) {
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val plans by c.billing.plans.collectAsStateWithLifecycle()
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val yearly = plans.firstOrNull { it.yearly }
    val ledger = ledgerOf(c, gate, yearly?.priceMicros)
    val pages = listOfNotNull(0.takeIf { !ledger.empty }, 1, 2)
    var at by rememberSaveable { mutableIntStateOf(if (ProPaywall.plansFirst) pages.lastIndex else 0) }
    val page = pages[at.coerceIn(0, pages.lastIndex)]
    LaunchedEffect(page) { c.telemetry.event("pro_paywall", "page${page + 1}") }
    // 1.39 [FLOW-25] 닫기 늘 보임: the pages scroll, the buttons under them stay put.
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${at + 1} / ${pages.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (page) {
                0 -> {
                    Text("밤낮이 이번 달 드린 것", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (ledger.found > 0) Text("올해 밤낮이 찾아 준 돈 ${won(ledger.found)}", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (ledger.found > 0) Text("명세서와 맞춰 볼 때 계산이 명세서보다 많았던 차이예요. 회사에 물어볼 근거가 돼요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ledgerLine(ledger).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    RewardLedger.perDay(yearly?.priceMicros)?.let { Text("프로는 하루 ${it}원에 전부 열려 있어요.", style = MaterialTheme.typography.bodyMedium) }
                }
                1 -> {
                    Text("프로가 더해 주는 것", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(ProPitch.headline(settings.workStyle), style = MaterialTheme.typography.bodyLarge)
                    ProPitch.five(settings.workStyle).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text("여기 없는 기능은 모두 무료예요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("요금제", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (plans.isEmpty()) Text("Play에서 요금제를 불러오는 중이에요. 인터넷을 켜고 잠시 뒤 다시 열어 주세요.", style = MaterialTheme.typography.bodyMedium)
                    else PlanRows(c, plans, onMessage = {})
                    Text("어떤 요금제도 미리 골라 두지 않아요. 해지는 언제든 Play 스토어에서 하고, 해지해도 낸 기간까지는 프로예요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onClose(); goPro() }) { Text("자세한 안내와 해지 방법") }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (at < pages.lastIndex) Button(onClick = { at += 1 }) { Text("다음") }
            if (at > 0) OutlinedButton(onClick = { at -= 1 }) { Text("이전") }
            TextButton(onClick = onClose) { Text("닫기") }
            TextButton(onClick = { c.proMoments.snooze(Today.date()); onClose() }) { Text("7일간 보지 않기") }
        }
    }
}

/**
 * 3판 1절: the plans, the yearly one first with 추천 and its discount against the monthly plan × 12 (only when Play gave both prices),
 * the month it comes to after it; the free month only on the yearly plan. No plan is picked; each has its own button.
 */
@Composable
internal fun PlanRows(c: AppContainer, plans: List<Plan>, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val passOffer by c.billing.pass.collectAsStateWithLifecycle()
    val proState by c.proStore.state.collectAsStateWithLifecycle()
    val monthly = plans.firstOrNull { !it.yearly }
    plans.sortedByDescending { it.yearly }.forEach { plan ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plan.priceWords + (if (plan.yearly) " (1년마다)" else " (매달)") + if (plan.yearly) " · 추천" else "",
                style = MaterialTheme.typography.titleSmall)
            if (plan.yearly) {
                val pct = RewardLedger.savingsPercent(monthly?.priceMicros?.takeIf { monthly.currency == plan.currency }, plan.priceMicros)
                val lines = listOfNotNull(
                    pct?.let { "월 요금으로 1년 내면 ${won(monthly!!.priceMicros * 12 / 1_000_000)} → ${it}% 할인" },
                    RewardLedger.perMonth(plan.priceMicros)?.takeIf { plan.currency == "KRW" }?.let { "월 ${won(it)} 꼴" },
                )
                if (lines.isNotEmpty()) Text(lines.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            val trial = plan.trial && plan.yearly
            // 1.39 [FLOW-25] the free month said as money and a date, before the one big button.
            if (trial) Text(trialHead(Today.date(), plan.priceWords), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Button(onClick = {
                val activity = context.findActivity()
                if (activity == null || !c.billing.buy(activity, plan)) onMessage("Play 결제 화면을 열지 못했어요. 잠시 뒤 다시 해 주세요.")
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (trial) "1개월 무료로 시작 (연 요금제)" else "${plan.priceWords}로 구독")
            }
        }
        // 1.38 [GROW-04] A안: the 1년 이용권 right beside the yearly plan, "한 번 결제 · 자동 결제 없음" first (no 평생권, 1.36 결정).
        if (plan.yearly) YearPassRow(c, plan, onMessage)
    }
    // With no yearly plan from Play the pass still shows, after the plans.
    if (plans.none { it.yearly }) YearPassRow(c, null, onMessage)
}

/**
 * 841 1년 이용권, never for someone subscribed (no double payment); buyable again from 30 days before its end. 1.38 [GROW-04]: beside
 * the yearly plan, its price set against the yearly plan's when Play gave both in won, for "한 번에 내고 끝" buyers (오늘근무 리뷰 "한 번에
 * 2만 원이면 산다", 사용자 관찰/공개 정보).
 */
@Composable
private fun YearPassRow(c: AppContainer, yearly: Plan?, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val passOffer by c.billing.pass.collectAsStateWithLifecycle()
    val proState by c.proStore.state.collectAsStateWithLifecycle()
    val subscribed = proState.sub?.play()?.entitled(Today.instant()) == true
    val p = passOffer
    if (p != null && !subscribed) {
        val until = proState.yearPassUntil
        val canBuy = com.bamnat.core.tier.YearPass.rebuy(until, Today.instant())
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("1년 이용권 ${p.price}", style = MaterialTheme.typography.titleSmall)
            Text("한 번 결제 · 자동 결제 없음", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("한 번 사면 1년 써요. 1년 뒤 그냥 끝나고, 끝나기 7일 전에 한 번 알려 드려요. 다시 사면 이어져요." +
                if (yearly != null) " 연 구독은 해마다 저절로 이어지고, 이용권은 내가 다시 살 때만 이어져요." else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (until != null && Today.instant() < until) Text(until.atZone(Today.zone()).toLocalDate().let { "지금 이용권은 ${it.year}년 ${it.monthValue}월 ${it.dayOfMonth}일까지예요." },
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val activity = context.findActivity()
                if (activity == null || !c.billing.buyPass(activity)) onMessage("Play 결제 화면을 열지 못했어요. 잠시 뒤 다시 해 주세요.")
            }, enabled = canBuy, modifier = Modifier.fillMaxWidth()) {
                Text(if (canBuy) "1년 이용권 사기" else "끝나기 30일 전부터 이어서 살 수 있어요")
            }
        }
    }
}

/**
 * 840 환영 카드 (수익 3판 3절: 결제 직후 프로 기능 두 개를 켜면 첫 달 취소가 줄어요): once, right after 프로 is bought, two taps to the
 * two always-on 프로 features most people want. Closed for good with 닫기 or either tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProWelcomeCard(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val s by c.proMoments.state.collectAsStateWithLifecycle()
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    if (!s.welcome) return
    if (!gate.access.pro) return
    com.bamnat.app.ui.company.SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("프로를 시작했어요. 오늘부터 광고가 없어요", style = MaterialTheme.typography.titleMedium)
            Text("두 가지만 켜 두면 더 든든해요.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = {
                    c.proMoments.welcome(false)
                    com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, "매일 자동 백업")
                }) { Text("매일 자동 백업 켜기") }
                androidx.compose.material3.FilledTonalButton(onClick = {
                    c.proMoments.welcome(false)
                    com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, "휴대폰 캘린더에 근무 쓰기")
                }) { Text("캘린더에 근무 쓰기") }
                TextButton(onClick = { c.proMoments.welcome(false) }) { Text("닫기") }
            }
        }
    }
}
