package com.bamnat.app.ui.company

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.company.TalkPost
import com.bamnat.core.plaza.HideList
import com.bamnat.core.plaza.PlazaRules

/**
 * [COMM-08] Google Play's rule for posts people write (UGC): the user agrees to the plaza's rules
 * before the first post, reply, proposal or 임금표 entry. Asked once per rules version.
 */
@Stable
class PlazaRulesGate internal constructor(private val agreed: () -> Boolean, private val agree: () -> Unit) {
    internal var waiting by mutableStateOf<(() -> Unit)?>(null)

    /** Runs [then] now when the rules are agreed, or after the user agrees to them. */
    fun ensure(then: () -> Unit) {
        if (agreed()) then() else waiting = then
    }

    /** Whether the rules in force are agreed on this phone. */
    val isAgreed: Boolean get() = agreed()

    /** [TOWN-12] Agreed from the card above the box, nothing waiting to be sent. */
    fun agreeNow() { agree() }

    internal fun accept() {
        agree()
        waiting?.invoke()
        waiting = null
    }
}

/** 1.39 [CARE-12] Whether the rules in force are 2판 (마을 돌봄 on, from the 3.12 effective date). */
internal fun rulesCare(c: com.bamnat.app.AppContainer): Boolean = c.flags.on(com.bamnat.core.flags.FlagCatalog.COMMUNITY_CARE)

@Composable
fun rememberPlazaRules(): PlazaRulesGate {
    val c = LocalContext.current.appContainer
    val gate = remember {
        PlazaRulesGate(
            // 1.39 [CARE-12] 운영 규칙 2판은 community.care가 켜진 뒤에만(3.12 시행일).
            agreed = { PlazaRules.agreed(c.settingsRepository.current.plazaRulesAgreed, rulesCare(c)) },
            agree = {
                val v = PlazaRules.version(rulesCare(c))
                c.settingsRepository.update { it.copy(plazaRulesAgreed = v) }
                c.telemetry.event("plaza_rules_agree", "v$v")
            },
        )
    }
    PlazaRulesDialog(gate)
    return gate
}

@Composable
private fun PlazaRulesDialog(gate: PlazaRulesGate) {
    if (gate.waiting == null) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { gate.waiting = null },
        title = { Text("광장 운영 규칙") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // [COMM-11] I6: after a change, what changed is marked, and the rest is the same as agreed.
                val care = rulesCare(context.appContainer)
                val changed = PlazaRules.changedSince(context.appContainer.settingsRepository.current.plazaRulesAgreed, care)
                Text(if (changed.isEmpty()) "처음 글을 쓰기 전에 한 번만 봐 주세요. 모두가 편하게 쓰는 광장을 위한 약속이에요."
                    else "운영 규칙이 바뀌었어요. 바뀐 줄(${changed.size}개)을 보고 다시 동의해 주세요.",
                    style = MaterialTheme.typography.bodyMedium)
                PlazaRules.lines(care).forEachIndexed { i, line ->
                    if (i in changed) Text("${i + 1}. $line (바뀜)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    else Text("${i + 1}. $line", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    com.bamnat.app.ui.components.PolicyPage.open(context, PlazaRules.URL, "광장 운영 규칙")
                }) { Text("운영 규칙 전체 보기") }
            }
        },
        confirmButton = { TextButton(onClick = gate::accept) { Text("동의하고 쓰기") } },
        dismissButton = { TextButton(onClick = { gate.waiting = null }) { Text("나중에") } },
    )
}

/**
 * [TOWN-12] PC 확인 a6cab3c8 (답글 칸을 누르면 규칙 창이 먼저 떠서 쓰던 글이 사라지고, 바깥을 누르면 동의 없이 닫혀요): the rules
 * as a card above the box, not a window over it. Writing goes on underneath; 동의 is a button of its own, and nothing closes it
 * without one. 보내기 still asks in the window when it was never agreed.
 */
@Composable
fun PlazaRulesCard(gate: PlazaRulesGate, onAgreed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val care = rulesCare(context.appContainer)
    val changed = PlazaRules.changedSince(context.appContainer.settingsRepository.current.plazaRulesAgreed, care)
    SoftCard(modifier.fillMaxWidth(), highlighted = true) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (changed.isEmpty()) "처음 쓰기 전에 광장 운영 규칙에 동의해 주세요" else "운영 규칙이 바뀌었어요 (${changed.size}줄). 다시 동의해 주세요",
                style = MaterialTheme.typography.titleSmall)
            // PC 확인 f7c41660 (키보드가 올라오면 카드가 커서 목록이 거의 가려져요): one line here; the rules themselves under 전체 보기.
            Text(PlazaRules.cardLine(care),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { com.bamnat.app.ui.components.PolicyPage.open(context, PlazaRules.URL, "광장 운영 규칙") },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("전체 보기") }
                Spacer(Modifier.weight(1f))
                com.bamnat.app.ui.components.FilledTonalButton(onClick = { gate.agreeNow(); onAgreed() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("동의해요")
                }
            }
        }
    }
}

/** 1.36 PC 재검토 (마을에서 운영 규칙 여는 곳을 못 찾았어요): under every writing guide, the rules in the app's own page. */
@Composable
fun RulesLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { com.bamnat.app.ui.components.PolicyPage.open(context, PlazaRules.URL, "운영 규칙") },
        modifier = modifier.heightIn(min = 48.dp)) { Text("운영 규칙 보기") }
}

/** [COMM-09] Asks before hiding a writer; the list can be undone in 내 활동 › 안 보는 사람 (마을 › 팔로잉 › 차단). */
@Composable
fun HideWriterDialog(post: TalkPost?, onDismiss: () -> Unit, onHide: (TalkPost) -> Unit) {
    val p = post ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 사람 글을 안 볼까요?") },
        text = {
            Text("${p.nick ?: "참여자 ${p.who}"}님의 글과 답글이 모든 토론에서 이 휴대폰에만 보이지 않아요. 상대에게는 알리지 않고, " +
                "서버에도 목록을 남기지 않아요. 마을 › 팔로잉 › 차단에서 언제든 다시 볼 수 있어요.", style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onHide(p) }) { Text("안 보기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 2.0.0 [RVN-06][U10a-06] What one 신고 says on the app's line: it went, and where its outcome comes. */
const val FLAGGED = "신고했어요 · 결과는 밤낮 소식으로 알려 드려요"

/**
 * [COMM-06] 신고 with a reason, the same in the talk sheet and on 광장's own cards (2.0.0 [RVN-06] COMP-01, 지난 글 too). 2.0.0 COMP-10
 * 성적인 내용·불법은 their own reasons, as 마을 돌봄 handles them first (CARE_SEVERE); [invest] adds 종목 추천·리딩.
 */
@Composable
fun FlagPostDialog(post: TalkPost?, invest: Boolean, onDismiss: () -> Unit, onFlag: (TalkPost, String) -> Unit) {
    val p = post ?: return
    // 2.0.0 [RVN-13] HQ-39 Nothing picked first: each reason is handled its own way (개인 정보 is hidden at once), so 신고하기 waits
    // until one is chosen rather than sending the first one unread.
    var reason by remember(p.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 글을 신고할까요?") },
        text = {
            // At 200% text the reasons scroll inside the dialog rather than push its buttons away.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FLAG_REASONS.filter { (key, _) -> key != "advice" || invest }.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = reason == key, role = Role.RadioButton) { reason = key },
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = reason == key, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("서로 다른 세 분이 신고하면 운영자가 확인할 때까지 가려져요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let { onDismiss(); onFlag(p, it) } }, enabled = reason != null) { Text("신고하기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** The reasons a post can be reported for, as the server keeps them (FLAGS). */
internal val FLAG_REASONS = listOf("abuse" to "욕설·비방", "personal" to "개인 정보가 있어요", "sexual" to "성적인 내용", "illegal" to "불법·위험한 내용",
    "spam" to "광고·도배", "false" to "일부러 틀린 정보", "advice" to "종목 추천·리딩")

/**
 * [VIL-02] 1.36 마을 V2 차단 하나로: opens 마을 › 팔로잉 › 차단, where 광장에서 가린 분 and 장터·프로필에서 차단한 분 are one list.
 * Null where no screen can be opened (previews).
 */
val LocalOpenBlocked = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** 1.36 [VIL-16] Opens one recipe's page (a 자랑's card); null where there is no way there. */
val LocalOpenRecipe = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** 1.36 [VIL-15] Opens 요청 판 with a request started from these words (a plaza question); null where there is no way there. */
val LocalOpenRequest = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * [COMM-09][COMM-10] 내 활동's part about this phone: 광장 소식 알림 (on unless turned off) and the writers
 * the user hid, each with 다시 보기.
 */
@Composable
fun PlazaPhoneSettings() {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val hidden = HideList.decode(settings.plazaHidden)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = settings.plazaNews, role = Role.Switch) { on -> c.settingsRepository.update { it.copy(plazaNews = on) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("밤낮 소식 알림", style = MaterialTheme.typography.titleSmall)
                // [TOWN-13] PC 확인 a6cab3c8: what it carries said in full (내 글의 새 답, 팔로우한 분의 새 작품 were not named).
                Text("광장 내 글에 달린 새 답, 팔로우한 분의 새 작품, 쓰는 규칙이 바뀌거나 보낸 규칙·제안·시세가 채택되거나 신고한 글에 결과가 나오면, 문의 답과 함께 하루에 한 번 모아서 알려 줘요. " +
                    "켜 두어도 광고는 오지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.plazaNews, onCheckedChange = null)
        }
        // 1.38 PC 10차 LOW (앱 안 소식 스위치와 휴대폰 알림 설정 중 어디서 꺼야 하는지 몰라요): what each one does, and the way to the other.
        val ctx = LocalContext.current
        Text(if (settings.plazaNews) "위 스위치를 끄면 소식을 아예 묻지 않아요. 휴대폰 알림 설정에서 ‘밤낮 소식’만 끄면 알림창에는 안 뜨고, 마을 탭의 점과 마을 맨 위 줄로만 보여요."
            else "꺼 두면 소식을 묻지 않아요. 새 답과 새 작품은 마을에 들어가면 보여요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (settings.plazaNews && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            TextButton(onClick = { com.bamnat.app.ui.components.SystemSettings.channel(ctx, com.bamnat.app.alarm.Notifications.PLAZA_CHANNEL_ID) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("휴대폰 알림 설정에서 ‘밤낮 소식’ 보기") }
        }
        val openBlocked = LocalOpenBlocked.current
        Text("${com.bamnat.core.plaza.PlazaWords.HIDDEN} ${hidden.writers.size}", style = MaterialTheme.typography.titleSmall)
        Text(if (hidden.writers.isEmpty()) "글의 ⋮ › 이 사람 글 안 보기로 고른 분은 장터·프로필에서 차단한 분과 한 목록(마을 › 팔로잉 › 차단)에 모여요. 이 휴대폰에만 남아요."
            else hidden.writers.take(3).joinToString(" · ") { it.label } + if (hidden.writers.size > 3) " 외 ${hidden.writers.size - 3}명" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (openBlocked != null) {
            OutlinedButton(onClick = openBlocked, modifier = Modifier.heightIn(min = 48.dp)) { Text("안 보는 사람·차단한 분 보기") }
        } else hidden.writers.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(w.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { c.settingsRepository.update { s -> s.copy(plazaHidden = HideList.decode(s.plazaHidden).show(w.mark).encode()) } }) {
                    Text("다시 보기")
                }
            }
        }
    }
}
