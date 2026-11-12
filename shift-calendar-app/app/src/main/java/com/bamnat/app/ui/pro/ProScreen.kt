package com.bamnat.app.ui.pro

import com.bamnat.app.tier.launchAt
import com.bamnat.app.tier.thanks
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.billing.BillingState
import com.bamnat.app.billing.Plan
import com.bamnat.app.billing.ProductIds
import com.bamnat.app.company.CompanyException
import com.bamnat.app.review.InAppReview.findActivity
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.ProState
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.tier.ProPitch
import com.bamnat.core.tier.ProReason
import com.bamnat.core.time.Today
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * 1.38 [EASE-33] Beside the ad promise: no account, and the alarm check anyone can run. The store page says the same
 * (`docs/store-listing.md`); 알람 점검 is in 설정 › 알람.
 */
internal const val TRUST_LINE = "로그인·회원가입 없이 바로 써요. 알람이 제시간에 울릴지는 설정 › 알람 › 알람 점검에서 누구나 무료로 확인해요."

/**
 * [MON-05] What a subscription screen must say before anyone pays (구독 화면 규칙): price and how often,
 * when a trial turns paid and how much, cancelling in Play at any time, the 7-day withdrawal and its
 * limit for digital content, and where the terms are. Tested word for word in ProScreenTest.
 */
fun proDisclosures(plans: List<Plan>, today: LocalDate, pass: String? = null): List<String> {
    val month = plans.firstOrNull { !it.yearly }
    val year = plans.firstOrNull { it.yearly }
    val prices = listOfNotNull(month?.priceWords, year?.priceWords).joinToString(", ")
    val trialEnd = today.plusMonths(1)
    return listOfNotNull(
        "결제는 Google Play 계정으로 해요. " + (if (prices.isNotEmpty()) "$prices, " else "") + "월 요금제는 매달, 연 요금제는 매년 같은 날 자동으로 결제돼요.",
        if (plans.any { it.trial }) "1개월 무료 체험은 연 요금제를 처음 구독하는 분께 한 번이에요. 오늘 시작하면 ${trialEnd.monthValue}월 ${trialEnd.dayOfMonth}일부터 " +
            "고른 요금제의 금액이 결제돼요. 끝나기 7일 전에 계속 쓸지 따로 여쭤보고, 답이 없으면 3일 전에 해지 단추와 함께 한 번 더 알려요." else null,
        // 2.0.0 [RVO-07] (검토 PRO-05) 1년 이용권도 결제 전 고지에: 한 번 결제, 자동 결제 없음.
        pass?.let { "1년 이용권($it)은 한 번 결제하고 자동 결제가 없어요. 1년 뒤 그냥 끝나고, 끝나기 7일 전에 한 번 알려 드려요." },
        "해지는 언제든 Play 스토어 › 결제 및 정기 결제에서 해요. 해지해도 이미 낸 기간이 끝날 때까지 프로를 써요.",
        "결제한 날부터 7일 안에는 청약철회(환불)를 요청할 수 있어요. 이미 쓰기 시작한 디지털 콘텐츠는 법에 따라 철회가 제한될 수 있어요(전자상거래법 제17조 제2항). " +
            "Google Play 환불 요청이나 설정 › 도움말과 문의 › 문의하기로 접수해요.",
        "구독하면 이용약관과 개인정보처리방침에 동의하는 거예요.",
        // 1.36 [MON-12] Who sells it, as the 전자상거래법 asks on the screen that sells.
        com.bamnat.app.ui.settings.SellerInfo.SHORT,
    )
}

/**
 * 1.39 [FLOW-25] 결제 창 머리 (흐름 디자인 3-12, 체험 혼동이 1점 리뷰의 원인인 앱을 반대로 배움): what a free month costs today, the day
 * it turns paid and for how much, and that it is said again before then (7일 전 물음과 마지막 날, [TrialConsentPrompt]), in one line.
 */
fun trialHead(today: LocalDate, priceWords: String): String {
    val end = today.plusMonths(1)
    // 2.0.0 [RVO-07] (검토 PRO-05) 실제 알림([com.bamnat.app.tier.TrialNotice])은 7일 전 물음과 3일 전 마지막: '하루 전'이 아니에요.
    return "오늘 0원 · ${end.monthValue}월 ${end.dayOfMonth}일부터 $priceWords · 끝나기 7일 전과 3일 전에 알려 드려요"
}

/**
 * 2.0.0 [RVO-07] (검토 PRO-05·I11ba-02 '결제 고지를 부품 하나로', 결정 13 추천 '한 문구로 통일') 결제 단추가 있는 곳(프로 화면·페이월)의 결제 고지
 * 한 줄: 자동 결제·해지·청약철회. 아래 [PayNotice]가 이 줄을 늘 보이고 [proDisclosures] 전부는 '결제 안내 모두 보기'로 펴요.
 */
fun payNoticeLine(plans: List<Plan>, pass: String? = null): String = listOfNotNull(
    "구독은 같은 날 자동 결제".takeIf { plans.isNotEmpty() || pass == null },
    "1년 이용권은 자동 결제 없음".takeIf { pass != null },
    "해지는 Play에서 언제든",
    "결제 7일 안 청약철회",
).joinToString(" · ")

/** 1.39 [FAM-03] What a family phone's 프로 is, and what a paused one is told. */
internal const val FAMILY_ON = "가족 프로 이용권으로 프로를 무료로 써요"
internal const val FAMILY_PAUSED = "가족 프로 이용권을 잠시 멈췄어요. 다시 켜지면 코드를 넣지 않아도 저절로 프로로 돌아와요."

const val TERMS_URL = "https://ywjmoney.github.io/-/terms/"
const val PRIVACY_URL = "https://ywjmoney.github.io/-/privacy/"

/**
 * [MON-04][MON-05][MON-08][MON-09] 설정 › 프로: why this phone has 프로 now, what 프로 adds next to
 * the free app, the plans with everything said before paying, a day by a rewarded ad, reward passes
 * with their recovery code, and the admin link. Before the 정식 출시 switch it says everything is free.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val config by c.remoteConfig.config.collectAsStateWithLifecycle()
    val plans by c.billing.plans.collectAsStateWithLifecycle()
    val billingState by c.billing.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    // 1.39 (시험이 찾은 빈틈: 초대 코드가 틀리면 그 말이 화면 맨 위에 떠서 보낸 자리에서는 안 보여요): each message shows under the card
    // that said it.
    var messageAt by remember { mutableStateOf("") }
    val say: (String) -> (String) -> Unit = { at -> { m -> message = m; messageAt = at } }
    @Composable fun Said(at: String) { if (messageAt == at) message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
    val zone = Today.zone()
    // [STAT-04] The first step of the 프로 funnel; only counted once 결제 is open.
    LaunchedEffect(Unit) { if (c.flags.on(com.bamnat.core.flags.FlagCatalog.BILLING)) c.telemetry.event(com.bamnat.core.stats.UsageEvent.PAYWALL_VIEW.key) }
    // 1.36 [GRW-09] PC 6ec395bf: 프로 화면을 열면 보상 이용권을 새로 물어요(만든 작품 보상이 바로 보이게).
    LaunchedEffect(Unit) { runCatching { c.syncPasses(force = true) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("프로") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
    }) { padding ->
        // PC 확인 d263030e (관리자 연결 코드 칸에서 키보드가 연결하기를 가려요): the page ends above the keyboard, so what is under a
        // field can be scrolled to while typing.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
                val style = settings.workStyle
                // 2.0.0 [RVO-05] (검토 U11b-01 '카드 10개, 가격·시작 단추는 여섯째 카드 아래') 한 장씩 차례대로: 지금 → (안 산 분) 프로 시작하기
                // → 프로 기능 모아보기·늘 무료 → 초대·이용권 → 코드를 받으셨나요?. 산 분은 구독·나눠 주기가 '지금' 바로 밑.
                val subscribed = pro.sub?.play()?.entitled(Today.instant()) == true
                val selling = gate.live && !subscribed
                val sellFirst = selling && (!gate.access.pro || gate.access.reason == ProReason.DAY_PASS)
                // 2.0.0 [RVO-01] (PC-54) 개발용 빌드에서는 등급 미리 보기를 맨 위에: 프로가 아닐 때 모습을 여기서 바로 골라 봐요.
                if (tierPreviewShown()) TierPreviewCard(c, gate, showNow = false)
                StatusCard(gate, pro, zone, style)
                ConsentWaiting(c, pro, onMessage = say("top"))
                Said("top")
                // 2.0.0 [RVO-13] 검토 HQ-48 (사용자 2026-11-12 20:28 KST "프로를 구독 안한사람은 프로를 눌렀을때 친구 초대를 먼져"): 프로를 사지
                // 않은 휴대폰(무료·하루 이용권·감사 기간·보상 이용권)은 '지금' 바로 아래가 친구 초대. 구독·1년 이용권은 아래 그대로.
                val inviteFirst = inviteFirst(gate)
                if (inviteFirst) {
                    InviteCard(c, onMessage = say("invite"), short = true)
                    Said("invite")
                }
                if (gate.live && subscribed) {
                    SubscribedCard(c, plans, onMessage = say("plans"))
                    Said("plans")
                }
                // 1.36 [PSH-01] 프로 나눔 1인: 직접 산 분과 나눠 받은 분, 체험 중인 분에게만(U11b-01 '나눠 주기는 프로인 사람에게만 위로').
                if (c.tierApi.enabled && (gate.boughtPro || gate.access.reason == ProReason.SHARED || (gate.live && gate.access.reason == ProReason.TRIAL))) {
                    ProShareCard(c, gate, zone, onMessage = say("share"))
                    Said("share")
                }
                if (sellFirst) {
                    StartCard(c, gate, plans, billingState, style, onMessage = say("plans"))
                    Said("plans")
                }
                // 1.36 840 보상 장부: what this phone was given this month, for someone without 프로 (가격 다음에).
                if (gate.live && !gate.preview && gate.tier < com.bamnat.app.tier.Tier.PRO) RewardLedgerCard(c, gate, plans)
                // [TIER-07] Every pro feature, this kind of work's first, a tap from where it lives; 2.0.0 [RVO-05] what stays free, under it.
                ProCollectionCard(gate, settings, startFolded = sellFirst)
                if (selling && !sellFirst) {
                    StartCard(c, gate, plans, billingState, style, onMessage = say("plans"))
                    Said("plans")
                }
                if (!gate.live) {
                    SectionCard(title = "결제") {
                        // 2.0.0 [RVO-12] (HQ-46) 정식 출시(11-12) 뒤라 '출시 전'이라 하지 않아요: 3.1 보류 중인 휴대폰에는 그 끝나는 날과 감사 프로를.
                        val thanks = com.bamnat.core.tier.ProAccess.thanksUntil(c.installedAt, config.launchAt(), config.thanks(), zone)
                        Text(closedBillingWords(gate.access, thanks, zone), style = MaterialTheme.typography.bodyMedium)
                        // 1.36 PC 통합 확인 f629c6f1 (프로 화면에 '사업자 정보'가 없어요): before 정식 출시 the 결제 card has no plans, and
                        // the 사업자 정보 lived only under them; it is shown here too.
                        var seller by remember { mutableStateOf(false) }
                        TextButton(onClick = { seller = true }) { Text("사업자 정보") }
                        if (seller) com.bamnat.app.ui.settings.SellerInfoDialog { seller = false }
                    }
                }

                // [INVITE-02][INVITE-05] AE 1.26: 친구 초대 (2.0.0 [RVO-06] 받은 코드는 맨 아래 한 칸으로).
                if (!inviteFirst) {
                    InviteCard(c, onMessage = say("invite"))
                    Said("invite")
                }

                if (c.tierApi.enabled) {
                    PassesCard(c, pro, onMessage = say("passes"), zone = zone)
                    Said("passes")
                    // 2.0.0 [RVO-06] (검토 SET-10) 연결된 휴대폰에만: 상태와 연결 끊기. 코드는 아래 한 칸에서 받아요.
                    if (pro.passes?.admin != null) AdminCard(c, pro, onMessage = say("admin"))
                    Said("admin")
                }
                // 2.0.0 [RVO-06] (검토 U11b-03) 받은 코드는 모두 여기 한 칸에서.
                if (c.tierApi.enabled || c.inviteApi.enabled) {
                    val openCode = remember { ProCodeLink.take() }
                    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    LaunchedEffect(openCode) { if (openCode) { kotlinx.coroutines.delay(300); bring.bringIntoView() } }
                    CodeInbox(c, gate, startOpen = openCode, modifier = Modifier.bringIntoViewRequester(bring))
                }
                // 1.36 PC 재검토 (결제 단추가 아래 탭 자리와 겹쳐 잘못 눌려요): the last buttons scroll clear of the bar at the bottom.
                Spacer(Modifier.navigationBarsPadding().height(72.dp))
            }
        }
    }
}

/** [SRV-07] The trial answer that has not reached the server yet: said plainly, with a way to try now. */
@Composable
internal fun ConsentWaiting(c: AppContainer, pro: ProState, onMessage: (String) -> Unit) {
    val q = pro.pendingConsent ?: return
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(consentWaitingText(q), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = {
                scope.launch { onMessage(if (c.sendConsent()) "답을 서버에 기록했어요" else "아직 보내지 못했어요. 인터넷을 켜고 다시 해 주세요.") }
            }) { Text("지금 다시 보내기") }
        }
    }
}

/**
 * 2.0.0 [RVO-13] (HQ-48) Whether 친구 초대 comes right under '지금': 결제 is open and this phone did not buy 프로 (no 프로, a day by an ad,
 * the 감사 months, reward passes). A subscription, the 1년 이용권, a 팀·직원 요금제, an admin's or a shared 프로 keep it lower down.
 * 처리방침 3.1을 미뤄 둔 폰(9항, 11-23까지 모두 무료)도 산 프로가 아니라 먼저예요(총괄 11-12 23:37 KST, 사용자 말 '프로를 구독 안 한 사람은').
 * 결제를 잠시 끈 때·개발용 빌드(ALL_OPEN)는 구독한 폰도 같은 모습이라 아래 그대로예요.
 */
internal fun inviteFirst(gate: com.bamnat.app.tier.Gate): Boolean =
    (gate.live && (!gate.access.pro || gate.access.reason in INVITE_FIRST)) || gate.access.reason == ProReason.HELD

private val INVITE_FIRST = setOf(ProReason.DAY_PASS, ProReason.THANKS, ProReason.PASS, ProReason.NONE)

/**
 * 2.0.0 [RVO-12] (HQ-46) The 결제 card while 결제 is not open on this phone. 처리방침 3.1's hold (9항): the day 무료·프로 part, and the 감사
 * 프로 a phone from before the 출시 still has after it ([thanks]). Otherwise (개발용 빌드·로컬 시험 서버, 관리자가 결제를 잠시 끈 때) plainly that
 * every feature is open; never '정식 출시 전' (출시는 11-12).
 */
internal fun closedBillingWords(access: com.bamnat.core.tier.Access, thanks: java.time.Instant?, zone: ZoneId): String {
    val ends = access.until
    if (access.reason != ProReason.HELD || ends == null) return "지금은 모든 기능이 무료로 열려 있어요. 결제가 열리면 앱에서 알려 드려요."
    val from = ends.atZone(zone).toLocalDate()
    val after = thanks?.takeIf { it.isAfter(ends) }?.let { lastDay(it, zone) }
        ?.let { " 정식 출시 전부터 써 주셔서, 그 뒤에도 ${dateWords(it)}까지는 감사 프로로 그대로 써요." }.orEmpty()
    return "${access.say(zone)}. ${from.monthValue}월 ${from.dayOfMonth}일부터 무료·프로가 나뉘어요.$after"
}

/** [SRV-07] What the waiting answer means for the user, in their words. */
fun consentWaitingText(q: com.bamnat.app.tier.PendingConsent): String =
    (if (q.answer == "keep") "'계속 쓸게요' 답을 아직 밤낮 서버에 기록하지 못했어요."
    else "'해지할래요' 답을 아직 밤낮 서버에 기록하지 못했어요.") +
        " 인터넷이 연결되면 저절로 다시 보내요. 답은 이 휴대폰에 그대로 있어요."

@Composable
private fun StatusCard(gate: Gate, pro: ProState, zone: ZoneId, style: com.bamnat.core.work.WorkStyle?) {
    SectionCard(title = "지금") {
        // 1.39 [FAM-03] 1.38 최종 PC 제안 5: a family phone is said as one at the top, before the launch too ('모든 기능이 무료' alone
        // left it to the 관리자 연결 card at the very bottom), and after it not as an admin's phone. An admin's phone the same way.
        val admin = pro.passes?.admin?.takeIf { it.approved }
        Text(if (admin?.family == true && gate.access.reason == ProReason.ADMIN) FAMILY_ON else gate.access.say(zone),
            style = MaterialTheme.typography.titleMedium)
        if (admin != null && gate.access.reason != ProReason.ADMIN) {
            // 2.0.0 [RVO-12] 결제가 열리지 않은 동안(3.1 보류 등)에만 보여요: '정식 출시 뒤에도'는 출시 뒤라 걷어냈어요.
            Text(if (admin.family) "가족 프로 이용권이 연결돼 있어요. 결제가 열려도 끝나는 날 없이 프로예요."
                else "관리자 휴대폰으로 연결돼 있어요. 결제가 열려도 프로를 무료로 써요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (pro.passes?.familyPaused == true) {
            Text(FAMILY_PAUSED, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        // 2.0.0 [RVO-12] 3.1 보류의 끝나는 날은 윗줄에 있어요.
        gate.access.until?.takeIf { gate.access.reason != ProReason.HELD }?.let { until ->
            val day = lastDay(until, zone)
            val words = when (gate.access.reason) {
                ProReason.TRIAL -> "체험은 ${dateWords(day)}에 끝나요"
                ProReason.SUBSCRIPTION -> if (pro.sub?.autoRenew == true) "다음 결제일 ${dateWords(day)}" else "${dateWords(day)}까지 써요(해지했어요)"
                ProReason.GRACE -> "${dateWords(day)}까지 결제 수단을 바꾸면 그대로 써요"
                ProReason.DAY_PASS -> "오늘 밤 12시까지예요"
                ProReason.PASS_1Y -> "${dateWords(day)}까지 써요. 자동 결제 없이 그날 끝나요"
                else -> "${dateWords(day)}까지예요"
            }
            Text(words, style = MaterialTheme.typography.bodyMedium)
        }
        pro.passes?.waitingMonths?.takeIf { it > 0 }?.let {
            Text("받은 보상 이용권 ${it}개월은 정식 출시 날부터 시작돼요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        pro.passes?.waitingDays?.takeIf { it > 0 }?.let {
            val why = pro.passes?.earned?.map { e -> e.why }?.distinct()?.joinToString(", ")?.takeIf { w -> w.isNotBlank() }
            Text("받은 프로 ${it}일${why?.let { w -> "($w)" } ?: ""}은 정식 출시 날부터 시작돼요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (gate.access.reason == ProReason.DAY_PASS) DayPassOpened(style)
    }
}

/**
 * 1.32 [PROX-01] (가계부 조사 5장): what today's 하루 이용권 opened for this kind of work, each a tap from where it lives,
 * so the day goes on the answers worth money; and the line that says a subscription keeps them every day.
 */
@Composable
private fun DayPassOpened(style: com.bamnat.core.work.WorkStyle?) {
    Text("오늘 열린 것", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    com.bamnat.core.tier.ProMoments.opened(style).forEach { o ->
        val f = com.bamnat.core.guide.FeatureRegistry.get(o.featureId)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .let { m -> if (f != null) m.clickable(role = Role.Button, onClickLabel = "${o.name} 열기") { com.bamnat.app.ui.guide.FeatureLinks.open(f) } else m }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(o.name, style = MaterialTheme.typography.bodyLarge)
                Text(o.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (f != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
    Text(com.bamnat.core.tier.ProMoments.EVERY_DAY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    Text("매주 다시 계산 알림은 구독에서만 켤 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 2.0.0 [RVO-05] (검토 U11b-01, I11ba-04 '프로 화면을 유형별 한 장으로') 프로 시작하기 한 장: 이 유형의 한 줄과 다섯 줄([ProPitch]), 요금제(연 요금제가
 * 먼저, 채운 단추는 그 하나), 결제 고지 한 줄과 펴는 전부([PayNotice]), 구매 복원·약관·처리방침·사업자 정보.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(c: AppContainer, gate: Gate, playPlans: List<Plan>, state: BillingState, style: com.bamnat.core.work.WorkStyle?, onMessage: (String) -> Unit) {
    val plans = shownPlans(gate, playPlans)
    val playPass by c.billing.pass.collectAsStateWithLifecycle()
    SectionCard(title = "프로 시작하기") {
        Text(ProPitch.headline(style), style = MaterialTheme.typography.titleMedium)
        ProPitch.five(style).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // 1.36 사용자 결정 (2026-11-01, PC Y '무료 상태에서도 미리'): 사면 한 사람에게 나눌 수 있다는 것도 미리.
        if (c.tierApi.enabled && !gate.access.pro) Text(shareLead(gate), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            plans.isEmpty() -> Text(
                if (state == BillingState.UNAVAILABLE) "이 휴대폰에서 Play 결제를 쓸 수 없어요. Play 스토어에 로그인했는지 확인해 주세요."
                else "Play에서 요금제를 불러오는 중이에요. 인터넷을 켜고 잠시 뒤 다시 열어 주세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // [MON-05] Each plan its own button; none is picked for the user. 840: the yearly one first, 추천, its discount.
            else -> PlanRows(c, plans, onMessage)
        }
        PayNotice(plans, shownPass(gate, playPass)?.price)
        PayLinks(c, manage = false, onMessage = onMessage)
    }
}

/** 2.0.0 [RVO-05] 구독 중인 분: 맨 위 가까이 한 줄과 Play에서 관리·해지, 구매 복원·약관 링크. */
@Composable
private fun SubscribedCard(c: AppContainer, plans: List<Plan>, onMessage: (String) -> Unit) {
    SectionCard(title = "구독") {
        Text("구독 중이에요. 요금제 바꾸기와 해지는 Play 스토어에서 해요.", style = MaterialTheme.typography.bodyMedium)
        PayNotice(plans, null)
        PayLinks(c, manage = true, onMessage = onMessage)
    }
}

/**
 * 2.0.0 [RVO-07] (검토 PRO-05, I11ba-02) 결제 고지 부품 하나: [payNoticeLine] 한 줄은 늘, [proDisclosures] 전부(가격·자동 결제·체험·이용권·해지·
 * 청약철회와 그 제한·약관·판매자)는 '결제 안내 모두 보기'로 펴요. 프로 화면과 페이월이 같이 써요.
 */
@Composable
internal fun PayNotice(plans: List<Plan>, pass: String?) {
    var open by rememberSaveable { mutableStateOf(false) }
    Text(payNoticeLine(plans, pass), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    com.bamnat.app.ui.components.FoldButton(if (open) "결제 안내 접기 ▴" else "결제 안내 모두 보기 ▾",
        if (open) "결제 안내 접기" else "결제 안내 모두 펼쳐 보기") { open = !open }
    if (open) proDisclosures(plans, Today.date(), pass).forEach {
        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 구매 복원·약관·처리방침·사업자 정보 (and Play's own page for someone subscribed), as word buttons: none of them is the thing to do now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayLinks(c: AppContainer, manage: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (manage) OutlinedButton(onClick = { open(context, ProductIds.manageUrl(context.packageName)) }) { Text("Play에서 구독 관리·해지") }
        TextButton(onClick = {
            scope.launch {
                c.billing.refresh()
                c.featureGate.refresh()
                onMessage(if (c.proStore.current.sub != null) "구독을 다시 찾았어요" else "이 Google 계정에서 찾은 구독이 없어요")
            }
        }) { Text("구매 복원") }
        // 조건 2 (글자 단추는 둘까지): 약관·처리방침·사업자 정보(1.36 [MON-12])는 한 단추의 메뉴로.
        var menu by remember { mutableStateOf(false) }
        var seller by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menu = true }) { Text(PAY_PAPERS) }
            // The app's menu (AppDialogsTest): keeps the app's text size and is not built while closed.
            com.bamnat.app.ui.components.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("이용약관") }, onClick = {
                    menu = false; com.bamnat.app.ui.components.PolicyPage.open(context, TERMS_URL, "이용약관")
                })
                androidx.compose.material3.DropdownMenuItem(text = { Text("개인정보처리방침") }, onClick = {
                    menu = false; com.bamnat.app.ui.components.PolicyPage.open(context, PRIVACY_URL, "개인정보 처리방침")
                })
                androidx.compose.material3.DropdownMenuItem(text = { Text("사업자 정보") }, onClick = { menu = false; seller = true })
            }
        }
        if (seller) com.bamnat.app.ui.settings.SellerInfoDialog { seller = false }
    }
}

internal const val PAY_PAPERS = "약관·사업자 정보"

/** [MON-08] Reward passes and the recovery code that moves them to a new phone. */
@Composable
private fun PassesCard(c: AppContainer, pro: ProState, zone: ZoneId, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val passes = pro.passes
    SectionCard(title = "보상 이용권") {
        // 2.0.0 [RVD-13]~[RVD-15] 사용자 결정 2026-11-12: 운영팀이 확인한 뒤 드리고, 보상 이용권은 1년에 모두 합쳐 12개월까지.
        // [RVD-16] 디자인 품질 조건 4(총괄 11-12): 첫 줄은 이 한 줄. 받는 때 목록은 갈래 P의 설명 ⓘ 부품이 오면 그 안으로 옮겨요(부품 대기).
        // [RVO-13] HQ-58 (총괄 11-12): the 1년 12개월 only while the server counts it (처리방침 3.13 30일 줄, RVD-21), not before 12-12.
        val rules = com.bamnat.app.ui.settings.rememberInviteTerms()
        Text(passesHead(rules, Today.date()), style = MaterialTheme.typography.bodyMedium)
        Text("받는 때: 회사 근태·급여 방식이 채택되거나 제안이 반영될 때, 만든 작품이 이달의 주제에 뽑히거나 모두의 도구·기본 도구가 될 때, 초대한 친구가 밤낮을 쓸 때. 보내 주신 문제 신고가 고쳐지면 3일(한 달 14일까지). 받을수록 기간이 이어져요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        passes?.items?.forEach { item ->
            Text("• 프로 ${item.months}개월 (${item.what ?: passWhy(item.why)}, ${dateWords(item.at.atZone(zone).toLocalDate())})",
                style = MaterialTheme.typography.bodyMedium)
        }
        // 2.0.0 [RVD-13][RVD-14] 확인 뒤 들어와요: 바로 생긴 것처럼 보이지 않게 따로 말해요.
        passes?.pending?.forEach { p ->
            Text("• 프로 ${p.months}개월 (${p.what ?: passWhy(p.why)}) · 운영팀이 확인한 뒤 들어와요", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 1.36 841 쓰면 쌓이는 날.
        passes?.earned?.forEach { e ->
            Text("• 프로 ${e.days}일 (${e.why}, ${dateWords(e.at.atZone(zone).toLocalDate())})", style = MaterialTheme.typography.bodyMedium)
        }
        passes?.until?.let { Text("이용권은 ${dateWords(it.atZone(zone).toLocalDate())}까지예요.", style = MaterialTheme.typography.bodyMedium) }
        // 1.36 PC 5차: 출시 전(무료 기간)에 받은 이용권은 끝나는 날이 아직 없어요. 비워 두지 않고 언제 시작하는지 말해요.
            ?: passes?.waitingMonths?.takeIf { it > 0 }?.let {
                Text("이용권 ${it}개월은 정식 출시 날부터 이어져요. 그때 끝나는 날이 정해져요.", style = MaterialTheme.typography.bodyMedium)
            }
        if (passes?.deferred?.let { it > 0 } == true) Text("구독 중이라 ${passes.deferred}번은 Play 다음 결제일을 미뤄 드렸어요.", style = MaterialTheme.typography.bodySmall)
        pro.code?.let { saved ->
            Text("복구 코드", style = MaterialTheme.typography.labelLarge)
            Text(saved, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text("휴대폰을 바꾸면 새 휴대폰의 설정 › 프로 › ‘$CODE_INBOX_TITLE’에 이 코드를 넣거나 백업을 불러오면 이용권이 옮겨 가요. 한 번 옮기면 이 휴대폰의 이용권은 끝나요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 2.0.0 [RVO-06] 다른 휴대폰의 복구 코드는 맨 아래 '코드를 받으셨나요?' 한 칸에서 받아요.
        if (passes?.items?.isNotEmpty() == true || passes?.earned?.isNotEmpty() == true) {
            OutlinedButton(onClick = {
                scope.launch {
                    runServer(onMessage) {
                        val made = c.tierApi.newCode(c.companyStore.device)
                        c.proStore.setCode(made)
                        copy(context, made)
                        onMessage("복구 코드를 만들어 복사했어요. 백업 파일에도 들어가요.")
                    }
                }
            }) { Text(if (pro.code == null) "복구 코드 만들기" else "새 복구 코드") }
        }
    }
}

/**
 * [MON-09] An invited admin's phone, tied with the 6-digit code from the developer app. 2.0.0 [RVO-06] (검토 SET-10) only for a phone that is
 * linked or waiting; the code goes in [CodeInbox].
 */
@Composable
private fun AdminCard(c: AppContainer, pro: ProState, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val admin = pro.passes?.admin ?: return
    // PC 확인 보안 8차 (끊은 뒤 아무 안내가 없어요): the answer is said under where this card was, as the card goes once unlinked.
    SectionCard(title = if (admin.family) "가족 프로 이용권" else "관리자 연결") {
        when {
            // 1.37.1 [FAM-01] 가족 프로 이용권: said as what it is, not as an admin's phone.
            admin.approved && admin.family -> Text("가족 프로 이용권으로 프로를 무료로 써요. 끝나는 날은 없어요.", style = MaterialTheme.typography.bodyMedium)
            // PC 확인 8773e3ae: approved at once said why (the 최고 관리자's own code needs no 승인).
            admin.approved -> Text("${adminWhose(admin.name)} 휴대폰이에요. 프로를 무료로 써요." +
                if (admin.owner) " 최고 관리자 코드로 연결해서 따로 승인 없이 바로 열렸어요." else "", style = MaterialTheme.typography.bodyMedium)
            else -> Text("${adminWhose(admin.name)} 휴대폰으로 승인을 기다리고 있어요.", style = MaterialTheme.typography.bodyMedium)
        }
        // PC 확인 보안 7차 (한 번에 끊겨요): asked once, as linking again needs a new code from the admin app.
        var asking by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { asking = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("연결 끊기") }
        if (asking) com.bamnat.app.ui.components.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(if (admin.family) "가족 프로 이용권을 끊을까요?" else "관리자 연결을 끊을까요?") },
            text = { Text(if (admin.family) "가족 이용권으로 쓰던 프로가 꺼져요. 직접 구독한 것은 그대로예요. 다시 쓰려면 새 가족 코드를 받아야 해요."
                else "관리자 휴대폰이라 무료로 쓰던 프로가 꺼져요. 직접 구독한 것은 그대로예요. 다시 연결하려면 관리자 앱 › 내 정보에서 새 6자리 코드를 받아야 해요.") },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    scope.launch {
                        runServer(onMessage) {
                            c.tierApi.unlink(c.companyStore.device)
                            c.syncPasses(force = true)
                            c.featureGate.refresh()
                            onMessage(if (admin.family) "가족 프로 이용권을 끊었어요. 다시 쓰려면 새 가족 코드를 맨 아래 ‘$CODE_INBOX_TITLE’에 넣어 주세요."
                                else "관리자 연결을 끊었어요. 다시 연결하려면 새 6자리 코드를 맨 아래 ‘$CODE_INBOX_TITLE’에 넣어 주세요.")
                        }
                    }
                }) { Text("끊기") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("닫기") } },
        )
    }
}

internal suspend fun runServer(onMessage: (String) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: CompanyException) {
        onMessage(serverProblem(e.code))
    } catch (e: Exception) {
        onMessage(serverProblem("bad_response"))
    }
}

/** 2.0.0 [RVD-15] 보상 이용권은 한 사람이 1년에 모두 합쳐 이만큼까지예요(company-worker REWARD_CAP). */
const val REWARD_CAP_MONTHS = 12

/**
 * 2.0.0 [RVO-13] HQ-58: the first line of 보상 이용권. '1년에 모두 합쳐 12개월까지' only when the server's rules say a cap ([InviteTerms.capMonths]
 * > 0, company-worker inviteRules) and 처리방침 3.13's 30-day date ([PolicyConsent.REVISION_EFFECTIVE_30]) has come: before it the server
 * counts no year cap (RVD-21), and a cap only for 친구 초대 is not one for all rewards. Rules never read say no cap.
 */
internal fun passesHead(rules: com.bamnat.core.tier.InviteTerms?, today: java.time.LocalDate): String =
    if ((rules?.capMonths ?: 0) > 0 && !today.isBefore(com.bamnat.core.ops.PolicyConsent.REVISION_EFFECTIVE_30))
        "운영팀이 확인한 뒤 드려요. 1년에 모두 합쳐 ${REWARD_CAP_MONTHS}개월까지예요."
    else "운영팀이 확인한 뒤 드려요."

/** [MON-08][INVITE-05] Why a reward pass was given, as the server names it. */
fun passWhy(why: String): String = when (why) {
    "rule" -> "회사 방식 채택"
    "rate" -> "임금표 채택"
    "invite" -> "친구 초대"
    // 1.36 [GRW-09] 만든 분 보상: 이달의 주제·모두의 도구·기본 도구.
    "maker" -> "만든 작품 보상"
    else -> "제안 반영"
}

/** What to tell the user about a refusal from the company server here. */
fun serverProblem(code: String): String = when (code) {
    "code" -> "코드가 맞지 않거나 시간이 지났어요. 새 코드로 다시 해 주세요."
    // 1.36 [PSH-01] 프로 나눔 1인.
    "not_bought" -> "직접 구독한 프로가 있어야 나눌 수 있어요. 체험·이용권으로 쓰는 프로는 나눌 수 없어요."
    "giver_ended" -> "코드를 준 분의 프로가 끝나서 쓸 수 없어요."
    "self" -> "내가 만든 코드는 다른 사람 휴대폰에 넣어 주세요."
    "too_many" -> "이 관리자에게는 휴대폰을 2대까지 연결할 수 있어요. 관리자 앱에서 하나를 끊어 주세요."
    "rate_limited" -> com.bamnat.app.company.RateLimits.text()
    "offline" -> "인터넷에 연결되지 않았어요."
    else -> "지금은 처리하지 못했어요. 잠시 뒤 다시 해 주세요."
}

fun dateWords(d: LocalDate): String = "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"

/** The last day an end time still counts: an end at midnight belongs to the day before. */
fun lastDay(end: Instant, zone: ZoneId): LocalDate {
    val at = end.atZone(zone)
    return if (at.toLocalTime() == java.time.LocalTime.MIDNIGHT) at.toLocalDate().minusDays(1) else at.toLocalDate()
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * 2.0.0 [RVL-11] (검토 DATA-26) The recovery code moves the passes: marked sensitive, so Android 13+ does not show it in the copy preview
 * at the bottom of the screen (someone looking at the screen too).
 */
private fun copy(context: Context, text: String) {
    runCatching {
        val clip = ClipData.newPlainText("복구 코드", text).apply {
            description.extras = android.os.PersistableBundle().apply {
                putBoolean(if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}

/** [INPUT-04] A recovery code as the server makes it: 16 letters and digits, a dash every four (company-worker passCode). */
private val RECOVERY_CODE = Regex("""[A-Z0-9]{4}(-?[A-Z0-9]{4}){3}""")

/**
 * 1.32 [PROX-03] (가계부 조사 5장 "결제 투명성"): 설정's first line while money is or will be taken: the next 결제일 and 금액,
 * when a 체험 turns into a payment, or that a payment failed; and the way to 해지 one tap away. Null when nothing is billed.
 */
fun billingWords(reason: ProReason, lastDay: LocalDate?, autoRenew: Boolean, price: String?): String? {
    val day = lastDay?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: return null
    return when (reason) {
        ProReason.SUBSCRIPTION -> if (autoRenew) "프로 구독 중 · 다음 결제 $day" + (price?.let { " · $it" } ?: "") else "구독을 해지했어요 · ${day}까지 써요"
        ProReason.TRIAL -> if (autoRenew) "무료 체험 ${day}에 끝나요" + (price?.let { " · 그 뒤 $it 결제" } ?: " · 그 뒤 결제돼요") else "무료 체험 ${day}에 끝나요 · 해지해서 결제되지 않아요"
        ProReason.GRACE -> "결제가 되지 않았어요 · ${day}까지 결제 수단을 바꿔 주세요"
        else -> null
    }
}

/** 1.32 [PROX-03] The line itself, at the top of 설정. */
@Composable
fun BillingTopLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val plans by c.billing.plans.collectAsStateWithLifecycle()
    if (!gate.live || gate.preview) return
    val sub = pro.sub ?: return
    val price = plans.firstOrNull { it.basePlan == sub.basePlan }?.priceWords
    val words = billingWords(gate.access.reason, gate.access.until?.let { lastDay(it, Today.zone()) }, sub.autoRenew, price) ?: return
    Surface(
        color = if (gate.access.reason == ProReason.GRACE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(words, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { open(context, ProductIds.manageUrl(context.packageName)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (gate.access.reason == ProReason.GRACE) "결제 수단 바꾸기" else "해지·관리")
            }
        }
    }
}

/** 1.36 840 보상 장부 (수익 2판 2-2): the month's free days, what they are worth at the yearly price, and 밤낮이 찾아 준 돈. */
@Composable
private fun RewardLedgerCard(c: AppContainer, gate: Gate, plans: List<Plan>) {
    val moments by c.proMoments.state.collectAsStateWithLifecycle()
    val yearly = plans.firstOrNull { it.yearly }?.priceMicros
    val m = remember(gate.leases, moments.found, yearly) { ledgerOf(c, gate, yearly) }
    if (m.empty) return
    SectionCard(title = "보상 장부") {
        // 1.36 PC ('7일 … (7일치)' 말이 겹쳐요): the month's total only when there is more than one to add up.
        if (m.days > 0) Text("이번 달 받은 것  ${m.partsLine}" + (if (m.parts.size > 1) " (모두 ${m.days}일치)" else ""), style = MaterialTheme.typography.bodyMedium)
        // 1.36 PC Y16 ('받은 날' 뒤에 날짜가 없어요): each bundle open now, from the day it was given to the day it ends.
        val now = java.time.Instant.ofEpochMilli(gate.now)
        for (b in com.bamnat.core.tier.FeatureLease.Bundle.entries) {
            val end = gate.leases.until[b]?.takeIf { now < it } ?: continue
            val from = gate.leases.grants.lastOrNull { it.bundle == b }?.at ?: continue
            Text("${b.label}  ${dayWords(from)}에 받아 ${untilWords(end)}까지 열려 있어요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        m.wonValue?.let { Text("프로 연 요금(하루 ${com.bamnat.core.tier.RewardLedger.perDay(yearly)}원)으로 치면 약 ${won(it)}어치", style = MaterialTheme.typography.bodyMedium) }
        if (m.found > 0) Text("밤낮이 찾아 준 돈  올해 ${won(m.found)} (명세서보다 계산이 많았던 차이)", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

/** "1월 11일", in the phone's zone. */
private fun dayWords(at: java.time.Instant): String = at.atZone(com.bamnat.core.time.Today.zone()).toLocalDate().let { "${it.monthValue}월 ${it.dayOfMonth}일" }

/**
 * PC 확인 보안 6차 d00db8a4 ("최고 관리자 관리자 휴대폰이에요"): the owner's name is already "최고 관리자", so "관리자" is not said twice.
 */
internal fun adminWhose(name: String): String = name.trim().let { if (it.isEmpty()) "관리자" else if (it.endsWith("관리자")) it else "$it 관리자" }
