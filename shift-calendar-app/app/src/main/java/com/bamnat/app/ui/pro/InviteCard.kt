package com.bamnat.app.ui.pro

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.tier.InviteAnswer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.guide.GrowNudges
import com.bamnat.core.guide.GuideState
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/** [INVITE-02] The one line said before anything about the inviter goes to the server (결정 D42). */
const val INVITE_MAKE_CONSENT = "초대 링크를 만들면 이 휴대폰의 설치 번호와 휴대폰 표시(기기 번호를 바꿔 만든 값)를 회사 서버에 보내요. " +
    "친구가 같은 휴대폰인지 확인하는 데만 써요."

/**
 * [INVITE-03][INVITE-08] The one line said before the friend's phone reports the code it came by: what goes, and
 * what counts as using the app ([days] from the server's rules).
 */
fun inviteUseConsent(code: String, days: Int): String = "초대 코드 ${InviteRules.shown(code)}와 이 휴대폰의 설치 번호·휴대폰 표시(기기 번호를 바꿔 만든 값)를 회사 서버에 보내요. " +
    "알린 날부터 ${days}일이 지난 뒤 앱을 다시 열면 초대한 분의 친구 수에 들어가요. 나에게 오는 보상은 없어요."

/** What the friend's phone is told after it reported, and each day after. */
fun inviteAnswerWords(a: InviteAnswer): String = when (a.status) {
    "waiting" -> "초대한 분께 알렸어요. " + (a.daysLeft?.takeIf { it > 0 }?.let { "${it}일 뒤에 앱을 다시 열면 초대한 분의 친구 수에 들어가요." }
        ?: "사용 기간이 지난 뒤 앱을 다시 열면 초대한 분의 친구 수에 들어가요.")
    "qualified" -> "사용 기간을 채웠어요. 초대한 분의 친구 수에 들어갔어요."
    // 2.0.0 [RVD-14] 사용자 결정 2026-11-12 '확인 뒤 지급': 초대한 분의 보상은 운영팀이 확인한 뒤 들어가요.
    "credited" -> "사용 기간을 채웠어요. 초대한 분의 보상은 운영팀이 확인한 뒤 들어가요."
    "capped" -> "사용 기간을 채웠어요. 초대한 분은 올해 받을 수 있는 프로를 모두 받았어요."
    "expired" -> "사용 기간 안에 앱을 다시 열지 않아 이 초대는 끝났어요."
    else -> InviteRules.refused(a.reason)
}

/**
 * AG 1.27 [INVITE-11] One way to invite wherever it is offered (프로 화면, 설정 맨 위, 달력 카드, 프로 기능 시트): the link
 * is shared at once when this install has one; otherwise [makeLabel], then the line of what goes and '동의하고 링크
 * 만들기' (결정 D42), then the share sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InviteShareAction(
    c: AppContainer, onMessage: (String) -> Unit, makeLabel: String = "초대 링크 만들기", tonal: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val view = state.view
    if (asking && view?.code == null) Text(INVITE_MAKE_CONSENT, style = MaterialTheme.typography.bodySmall)
    val button: @Composable (String, () -> Unit) -> Unit = { label, go ->
        if (tonal) FilledTonalButton(enabled = !busy, onClick = go) { Text(label) } else Button(enabled = !busy, onClick = go) { Text(label) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            view?.code != null && view.link != null -> button("초대 링크 보내기") { share(context, InviteRules.shareText(view.link, view.code)) }
            !asking -> button(makeLabel) { asking = true }
            else -> button("동의하고 링크 만들기") {
                busy = true
                scope.launch {
                    try {
                        val v = c.invites.makeLink()
                        asking = false
                        if (v.link != null && v.code != null) share(context, InviteRules.shareText(v.link, v.code))
                    } catch (e: CompanyException) {
                        onMessage(serverProblem(e.code))
                    } finally {
                        busy = false
                    }
                }
            }
        }
        extra()
    }
}

/** AG 1.27 [INVITE-09] "3/5명 · 2명은 사용 기간 채우는 중" and "다음 프로까지 2명", when this install has a code. */
@Composable
internal fun InviteProgressLines(c: AppContainer) {
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val view = state.view ?: return
    if (view.code == null) return
    Text(InviteRules.progress(view.progress, view.terms.need, view.waiting), style = MaterialTheme.typography.titleMedium)
    if (!view.capReached) Text(InviteRules.toNext(view.progress, view.terms.need), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary)
}

/** 2.0.0 [RVO-13] (HQ-48) The name of the way to 프로 without paying, the same on 프로, the locked feature's sheet and the paywall. */
const val INVITE_FREE_TITLE = "친구를 초대하면 무료로 써요"

/**
 * [INVITE-02][INVITE-05][INVITE-03] 설정 › 프로 › 친구 초대: the inviter's counts and link (made after the line above),
 * shared with the phone's share sheet. 2.0.0 [RVO-06] (검토 U11b-03) a code received goes in at the bottom of 프로 ([CodeInbox]);
 * this card keeps only what the friend's phone was told.
 *
 * 2.0.0 [RVO-13] 검토 HQ-48 (사용자 2026-11-12 20:28 KST): [short] for a phone that did not buy 프로, right under '지금': the one-line
 * promise ([InviteRules.promise]), what came of it so far in one line ([InviteRules.status]) and one button; how a friend counts,
 * what goes to the server and the code behind '자세히' (조건 1·4). A buyer's card stays as it was, lower down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InviteCard(c: AppContainer, onMessage: (String) -> Unit, short: Boolean = false) {
    if (!c.inviteApi.enabled) return
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val view = state.view
    val terms = state.rules
    LaunchedEffect(view?.code) { runCatching { c.invites.refresh() } }
    var more by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = if (short) INVITE_FREE_TITLE else "친구 초대") {
        if (short) {
            Text(InviteRules.promise(terms), style = MaterialTheme.typography.titleMedium)
            view?.let { v -> InviteRules.status(v.given, v.pending, v.waiting) }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        // AG 1.27 [INVITE-09] 리뷰 2026-10-25: each five different friends, not one friend's days.
        if (!short || more) Text(InviteRules.details(terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (view?.code != null && (!short || more)) {
            InviteProgressLines(c)
            Text(InviteRules.summary(view.friends, view.given), style = MaterialTheme.typography.bodyMedium)
            // 2.0.0 [RVD-14] 확인 뒤 들어와요.
            if (view.pending > 0) Text(InviteRules.pending(view.pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (view.capReached) {
                Text("올해 받을 수 있는 ${terms.capMonths}개월을 모두 받았어요. 친구는 계속 셀게요.", style = MaterialTheme.typography.bodySmall)
            }
            Text("내 초대 코드 ${InviteRules.shown(view.code)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        // 2.0.0 [RVO-06] 조건 2: 이 화면의 채운 단추는 '프로 시작하기' 하나라, 초대 링크는 옅은 채움 단추.
        InviteShareAction(c, onMessage, tonal = true) {
            if (short) TextButton(onClick = { more = !more }) { Text(if (more) "접기" else "자세히") }
        }
        state.answer?.takeIf { state.reportedCode != null }?.let {
            Text(inviteAnswerWords(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 2.0.0 [RVO-13] (HQ-48) The way to 프로 without paying, as one row where a sheet already has its buttons (the three-page paywall):
 * the title, the promise, and a tap to 프로 where the invite card is right under '지금'.
 */
@Composable
internal fun InviteFreeRow(c: AppContainer, onClick: () -> Unit) {
    if (!c.inviteApi.enabled) return
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "프로 화면에서 초대하기", onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(INVITE_FREE_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(InviteRules.promise(state.rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/**
 * [INVITE-03] On the calendar after the first setup: the friend's phone found an invite code in Play's install referrer
 * and asks once whether to tell the server. Nothing is sent before 알리기. AG 1.27 [INVITE-10]: first among the
 * calendar's notices (never folded), only for a phone that did not answer at the end of the first setup; [INVITE-13]
 * after the report, what it said and the friend's own chance to invite, until closed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteArrivalNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    if (!c.inviteApi.enabled) return
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val code = state.found?.takeIf { !state.offerClosed && state.reportedCode == null && !state.agreed }
    val relay = c.invites.relay()
    // [INVITE-08] The period of use to say; the rules go without anything about this phone.
    LaunchedEffect(code, relay != null) { if (code != null || relay != null) runCatching { c.invites.loadRules() } }
    if (code == null && said == null && relay == null) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (said != null) {
                Text(said!!, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { said = null }) { Text("닫기") }
                return@Column
            }
            if (code == null && relay != null) {
                Text(relay.answer?.let(::inviteAnswerWords) ?: "초대한 분께 알릴게요. 인터넷이 연결되면 저절로 알려요.",
                    style = MaterialTheme.typography.bodyMedium)
                // [INVITE-13] The friend can be the next one to invite (결정 AG-D1: no reward for the friend itself).
                Text("당신도 ${state.rules.words()}.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                InviteShareAction(c, onMessage = { said = it }, makeLabel = "내 초대 링크 만들기", tonal = true) {
                    TextButton(onClick = { c.invites.closeRelay() }) { Text("닫기") }
                }
                return@Column
            }
            Text("친구 초대로 오셨나요?", style = MaterialTheme.typography.titleMedium)
            Text(inviteUseConsent(code!!, state.rules.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            c.invites.report(code, "referrer")
                        } catch (e: CompanyException) {
                            said = serverProblem(e.code)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("알리기") }
                TextButton(onClick = { c.invites.decline() }) { Text("괜찮아요") }
            }
        }
    }
}

/**
 * AG 1.27 [INVITE-11] The calendar's 친구 초대 card (결정 AG-D6): after the first setup, three days after the install,
 * the whole day it shows, then two weeks later; '닫기' for two weeks, '그만 보기' turns it off (기능 켜고 끄기
 * `invite.nudge`). Not while the friend's own card has something to say.
 */
@Composable
fun InviteNudgeNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    if (!c.inviteApi.enabled) return
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val grow by c.grow.state.collectAsStateWithLifecycle()
    val guide = com.bamnat.app.data.GuideStore.get(LocalContext.current)
    val guideState by guide.state.collectAsStateWithLifecycle()
    val today = Today.date()
    val installedOn = c.invites.installedAt()?.atZone(Today.zone())?.toLocalDate()
    val setupDone = c.settingsRepository.current.setupDone
    val off = com.bamnat.core.guide.FeatureRegistry.get(NUDGE_ID)?.let { guideState.isOff(it, GuideState.ALL) } ?: false
    val friendBusy = c.invites.offer() != null || c.invites.relay() != null
    if (off || friendBusy || !GrowNudges.invite(grow, installedOn, today, setupDone)) return
    var said by remember { mutableStateOf<String?>(null) }
    val folded = com.bamnat.app.ui.calendar.LocalNoticeFolded.current
    LaunchedEffect(folded) { if (!folded) c.grow.inviteShown(today) }
    LaunchedEffect(Unit) { runCatching { c.invites.refresh() } }
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("친구에게 밤낮 알려 주기", style = MaterialTheme.typography.titleMedium)
            Text("${state.rules.words()}. 링크로 깔면 앱이 저절로 알아봐요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            InviteProgressLines(c)
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            InviteShareAction(c, onMessage = { said = it }, tonal = true) {
                TextButton(onClick = { c.grow.inviteClosed(today) }) { Text("닫기") }
                TextButton(onClick = { guide.setOn(com.bamnat.core.guide.FeatureRegistry.get(NUDGE_ID)!!, GuideState.ALL, false) }) { Text("그만 보기") }
            }
        }
    }
}

/**
 * 1.39 [SPREAD-07] (앱 안 퍼짐 설계, 사용자 2026-11-07): right after a leader first sends the team's links, or a 사장님 first adds a
 * staff member, one card offers to tell another leader·사장님, with the 친구 초대 link (초대한 분께 프로). It shows the day it
 * first comes and then never again ([GrowNudges.peerInvite]); '다시 안 보기' ends it at once. The calendar card's switch
 * (기능 켜고 끄기 › 달력의 친구 초대 카드) turns this off too.
 */
@Composable
fun PeerInviteCard(peer: com.bamnat.core.guide.Peer, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    if (!c.inviteApi.enabled) return
    val grow by c.grow.state.collectAsStateWithLifecycle()
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val guide = com.bamnat.app.data.GuideStore.get(LocalContext.current)
    val guideState by guide.state.collectAsStateWithLifecycle()
    val today = Today.date()
    val off = com.bamnat.core.guide.FeatureRegistry.get(NUDGE_ID)?.let { guideState.isOff(it, GuideState.ALL) } ?: false
    if (off || !GrowNudges.peerInvite(grow, peer, today)) return
    var said by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(peer) { c.grow.peerShown(peer, today); runCatching { c.invites.refresh() } }
    val (title, line) = when (peer) {
        com.bamnat.core.guide.Peer.TEAM -> "다른 팀 리더께도 알려 주세요" to "근무표 짜기·링크 보내기를 다른 병동·팀 리더도 덜 수 있어요."
        com.bamnat.core.guide.Peer.BOSS -> "아는 사장님께도 알려 주세요" to "직원 급여·출퇴근·계약서를 다른 사장님도 휴대폰 하나로 챙길 수 있어요."
    }
    SoftCard(modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$line ${state.rules.words()}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            InviteShareAction(c, onMessage = { said = it }, tonal = true) {
                TextButton(onClick = { c.grow.peerClosed(peer) }) { Text("다시 안 보기") }
            }
        }
    }
}

/** AG 1.27 [INVITE-11] The feature switch of the calendar's invite card (FeatureRegistry, 기능 켜고 끄기). */
const val NUDGE_ID = "invite.nudge"

internal fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(send, "초대 링크 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
