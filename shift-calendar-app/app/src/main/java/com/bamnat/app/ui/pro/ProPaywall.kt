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
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
    val playPlans by c.billing.plans.collectAsStateWithLifecycle()
    // 2.0.0 [RVO-01] (PC-54) 미리 보기에서는 Play가 없어도 요금제 쪽이 보여요(예시 가격, 결제하지 않음).
    val plans = shownPlans(gate, playPlans)
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val yearly = plans.firstOrNull { it.yearly }
    val ledger = ledgerOf(c, gate, yearly?.priceMicros)
    val pages = listOfNotNull(0.takeIf { !ledger.empty }, 1, 2)
    var at by rememberSaveable { mutableIntStateOf(if (ProPaywall.plansFirst) pages.lastIndex else 0) }
    val page = pages[at.coerceIn(0, pages.lastIndex)]
    LaunchedEffect(page) { c.telemetry.event("pro_paywall", "page${page + 1}") }
    // 2.0.0 [RVO-07] (검토 PRO-11 '결제 창을 못 열면 아무 말도 안 해요') 요금제 단추의 말은 이 창 안에.
    var said by rememberSaveable { mutableStateOf<String?>(null) }
    val playPass by c.billing.pass.collectAsStateWithLifecycle()
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
                    // 2.0.0 [RVO-07] (검토 PRO-12) 다섯 줄만 보이니 "여기 없는 기능은 모두 무료"는 틀려요: 나머지가 어디 있는지.
                    Text(PAYWALL_REST, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("요금제", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (plans.isEmpty()) Text("Play에서 요금제를 불러오는 중이에요. 인터넷을 켜고 잠시 뒤 다시 열어 주세요.", style = MaterialTheme.typography.bodyMedium)
                    else PlanRows(c, plans, onMessage = { said = it })
                    said?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Text("어떤 요금제도 미리 골라 두지 않아요. 해지해도 낸 기간까지는 프로예요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 2.0.0 [RVO-07] (검토 PRO-05) 결제 단추가 있는 곳의 고지는 프로 화면과 같은 부품: 한 줄 + 모두 보기(청약철회·판매자까지).
                    PayNotice(plans, shownPass(gate, playPass)?.price)
                    TextButton(onClick = { onClose(); goPro() }) { Text("자세한 안내와 해지 방법") }
                }
            }
            // 2.0.0 [RVO-13] 검토 HQ-48: 돈을 내지 않고 여는 길도 장마다 한 줄로(프로 화면의 초대 카드로 가요).
            HorizontalDivider()
            InviteFreeRow(c) { onClose(); goPro() }
        }
        // 2.0.0 [RVO-07] (검토 U11b-04 '가격은 세 번째 장에 가야 보여요') 앞 장에서도 가격 한 줄이 늘 보여요.
        if (page != 2) priceLine(plans)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (at < pages.lastIndex) Button(onClick = { at += 1 }) { Text("다음") }
            if (at > 0) OutlinedButton(onClick = { at -= 1 }) { Text("이전") }
            TextButton(onClick = onClose) { Text("닫기") }
            TextButton(onClick = { c.proMoments.snooze(Today.date()); onClose() }) { Text("7일간 보지 않기") }
        }
    }
}

/** 2.0.0 [RVO-07] (검토 PRO-12) 페이월 둘째 장 끝 한 줄. */
internal const val PAYWALL_REST = "이 밖의 프로 기능은 다음 장 ‘자세한 안내’에서 볼 수 있어요. 나머지는 모두 무료예요."

/**
 * 2.0.0 [RVO-07] (검토 U11b-04) 페이월 앞 장 아래에 늘 보이는 가격 한 줄: "월 4,990원 · 연 39,000원(35% 할인)". 할인은 Play가 두 가격을 같은
 * 돈으로 줄 때만. Play가 아직 아무것도 주지 않았으면 null.
 */
internal fun priceLine(plans: List<Plan>): String? {
    val month = plans.firstOrNull { !it.yearly }
    val year = plans.firstOrNull { it.yearly }
    val pct = if (month != null && year != null && month.currency == year.currency) RewardLedger.savingsPercent(month.priceMicros, year.priceMicros) else null
    return listOfNotNull(month?.priceWords, year?.let { it.priceWords + (pct?.let { p -> "(${p}% 할인)" } ?: "") })
        .joinToString(" · ").ifEmpty { null }
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
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val monthly = plans.firstOrNull { !it.yearly }
    plans.sortedByDescending { it.yearly }.forEachIndexed { i, plan ->
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
            val buy = {
                val activity = context.findActivity()
                // [RVO-01] 미리 보기는 결제하지 않아요.
                if (previewNoPay(gate)) onMessage(PREVIEW_NO_PAY)
                else if (activity == null || !c.billing.buy(activity, plan)) onMessage("Play 결제 화면을 열지 못했어요. 잠시 뒤 다시 해 주세요.")
            }
            // 2.0.0 [RVO-07] (검토 PRO-13 "월 4,990원로 구독") 조사를 낱말에 맞게.
            val words = if (trial) "1개월 무료로 시작 (연 요금제)" else "${plan.priceWords}${com.bamnat.app.ui.Fmt.ro(plan.priceWords)} 구독"
            // 2.0.0 [RVO-01] (PC-54 미리 보기로 처음 본 요금제 창: 채운 단추가 둘) 채운 단추는 맨 위 추천 하나, 나머지는 테두리 단추(조건 2).
            if (i == 0) Button(onClick = buy, modifier = Modifier.fillMaxWidth()) { Text(words) }
            else OutlinedButton(onClick = buy, modifier = Modifier.fillMaxWidth()) { Text(words) }
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
    val playPass by c.billing.pass.collectAsStateWithLifecycle()
    val proState by c.proStore.state.collectAsStateWithLifecycle()
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val subscribed = proState.sub?.play()?.entitled(Today.instant()) == true
    // [RVO-01] 미리 보기에서는 Play가 없어도 이용권 줄이 보여요(예시 가격).
    val p = shownPass(gate, playPass)
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
                if (previewNoPay(gate)) onMessage(PREVIEW_NO_PAY)
                else if (activity == null || !c.billing.buyPass(activity)) onMessage("Play 결제 화면을 열지 못했어요. 잠시 뒤 다시 해 주세요.")
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
                com.bamnat.app.ui.components.FilledTonalButton(onClick = {
                    c.proMoments.welcome(false)
                    com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, "매일 자동 백업")
                }) { Text("매일 자동 백업 켜기") }
                com.bamnat.app.ui.components.FilledTonalButton(onClick = {
                    c.proMoments.welcome(false)
                    com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA, "휴대폰 캘린더에 근무 쓰기")
                }) { Text("캘린더에 근무 쓰기") }
                TextButton(onClick = { c.proMoments.welcome(false) }) { Text("닫기") }
            }
        }
    }
}
