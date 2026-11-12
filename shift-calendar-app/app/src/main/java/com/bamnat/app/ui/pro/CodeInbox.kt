package com.bamnat.app.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.tier.ProReason
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/** 2.0.0 [RVO-06] What a typed code is, by its shape (no server call): each kind went in its own card before. */
enum class CodeKind {
    /** 6 digits from the admin app: an admin's phone or a 가족 프로 이용권 ([com.bamnat.app.tier.TierApi.link]). */
    LINK,
    /** 16 letters and digits: 보상 이용권 moved from another phone ([com.bamnat.app.tier.TierApi.claim]). */
    RECOVERY,
    /** 8 of [InviteRules]' letters: 친구 초대 or 프로 나눔, made the same way on the server, so the person says which. */
    EIGHT,
    NONE,
}

/** 2.0.0 [RVO-06] The kind of [raw], however it was typed ("123 456", "abcd-2345", the recovery code with dashes). */
fun codeKind(raw: String): CodeKind {
    val t = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }
    return when {
        t.length == 6 && t.all(Char::isDigit) -> CodeKind.LINK
        t.length == 16 -> CodeKind.RECOVERY
        InviteRules.code(t) != null -> CodeKind.EIGHT
        else -> CodeKind.NONE
    }
}

/** 2.0.0 [RVO-06] An 8-letter code is one of these two; [CodeKind.EIGHT] asks which when both can be taken here. */
enum class EightKind(val label: String) { INVITE("친구 초대 코드"), SHARE("프로 나눔 코드") }

/**
 * 2.0.0 [RVO-06] (검토 SET-10 '6자리 코드 칸이 모든 사용자에게', 맞히기 시도) Wrong codes in a row on this phone; after [WRONG_MAX] the box
 * waits [WAIT_MS]. The server limits tries too (company-worker `linkPhone` linkWrongPerWindow, claim·share 시간당 한도); this only stops a
 * person guessing from the screen. Kept for the app's life, so leaving and coming back does not reset it.
 */
object ProCodeTries {
    const val WRONG_MAX = 5
    const val WAIT_MS = 10 * 60 * 1000L
    private var wrong = 0
    private var until = 0L
    fun waiting(now: Long = Today.instant().toEpochMilli()): Boolean = now < until
    fun missed(now: Long = Today.instant().toEpochMilli()) {
        wrong += 1
        if (wrong >= WRONG_MAX) { until = now + WAIT_MS; wrong = 0 }
    }
    fun hit() { wrong = 0 }
    internal fun reset() { wrong = 0; until = 0L }
}

/** 2.0.0 [RVO-06] A search result or a help answer asked for the code box: 프로 opens with it open and in view. */
object ProCodeLink {
    private var asked = false
    fun ask() { asked = true }
    fun take(): Boolean = asked.also { asked = false }
}

internal const val CODE_INBOX_TITLE = "코드를 받으셨나요?"
internal const val CODE_WAIT = "코드가 다섯 번 맞지 않았어요. 10분 뒤 다시 넣어 주세요."

/** 2.0.0 [RVO-06] Why an 8-letter code cannot go in here now, or null when it can. */
internal fun eightWhy(invite: Boolean, share: Boolean, bought: Boolean, given: Boolean, reported: Boolean): String? = when {
    invite || share -> null
    reported -> "친구 초대는 이미 알렸어요. 같은 휴대폰은 한 번만 알릴 수 있어요."
    bought -> "직접 산 프로가 있어 나눔 코드는 넣지 않아도 돼요. 친구 초대 코드는 깐 지 7일 안에만 넣을 수 있어요."
    given -> "이미 나눠 받은 프로를 쓰고 있어요. 친구 초대 코드는 깐 지 7일 안에만 넣을 수 있어요."
    else -> "친구 초대 코드는 깐 지 7일 안에만 넣을 수 있어요."
}

/**
 * 2.0.0 [RVO-06] (검토 U11b-03 '코드를 받으셨나요?' 한 줄, U11a-15, SET-10) 프로 화면 맨 아래 한 줄. 누르면 칸 하나가 펴지고, 넣은 코드의 모양으로
 * 가족 프로·관리자(6자리), 친구 초대·프로 나눔(8자리, 둘 다 될 때만 고르기), 이용권 복구(16자리)를 알아서 가려 받아요. 친구 초대는 보내기 전에
 * 보내는 것 한 줄([inviteUseConsent], 결정 D42)을 그대로 보여요. 결과는 이 칸 아래에 남아요. 운영 말(관리자 앱·최고 관리자)은 쓰지 않아요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodeInbox(c: AppContainer, gate: Gate, startOpen: Boolean = false, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val inviteState by c.invites.store.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(startOpen) }
    var code by rememberSaveable { mutableStateOf("") }
    var eight by rememberSaveable { mutableStateOf<EightKind?>(null) }
    var said by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val kind = codeKind(code)
    val bought = gate.boughtPro
    val given = gate.access.reason == ProReason.SHARED
    val inviteOk = c.inviteApi.enabled && c.invites.mayType()
    val shareOk = c.tierApi.enabled && !bought && !given
    // One kind only: no question.
    val eightAs = eight ?: when {
        inviteOk && !shareOk -> EightKind.INVITE
        shareOk && !inviteOk -> EightKind.SHARE
        else -> null
    }
    val eightNo = if (kind == CodeKind.EIGHT) eightWhy(inviteOk, shareOk, bought, given, inviteState.reportedCode != null) else null
    val ready = !busy && when (kind) {
        CodeKind.LINK, CodeKind.RECOVERY -> c.tierApi.enabled
        CodeKind.EIGHT -> eightAs != null && eightNo == null
        CodeKind.NONE -> false
    }
    fun send() {
        if (!ready) return
        if (ProCodeTries.waiting()) { said = CODE_WAIT; return }
        focus.clearFocus()
        busy = true
        val typed = code
        scope.launch {
            try {
                said = when (kind) {
                    CodeKind.LINK -> {
                        val state = c.tierApi.link(c.companyStore.device, typed.filter(Char::isDigit))
                        c.syncPasses(force = true, linked = true)
                        c.featureGate.refresh()
                        when {
                            state.approved && state.family -> "가족 프로 이용권을 연결했어요. 프로를 무료로 써요."
                            state.approved -> "연결했어요. 프로를 무료로 써요."
                            else -> "연결했어요. 승인되면 프로가 열려요."
                        }
                    }
                    CodeKind.RECOVERY -> {
                        c.proStore.setPasses(c.tierApi.claim(c.companyStore.device, typed))
                        c.featureGate.refresh()
                        "이용권을 이 휴대폰으로 옮겼어요."
                    }
                    CodeKind.EIGHT -> if (eightAs == EightKind.INVITE) {
                        inviteAnswerWords(c.invites.report(InviteRules.code(typed)!!, "typed"))
                    } else {
                        c.tierApi.shareClaim(c.companyStore.device, InviteRules.code(typed)!!)
                        runCatching { c.proStore.setPasses(c.tierApi.passes(c.companyStore.device)) }
                        c.featureGate.refresh()
                        "나눠 받은 프로를 켰어요."
                    }
                    CodeKind.NONE -> null
                }
                ProCodeTries.hit()
                code = ""; eight = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: CompanyException) {
                if (e.code == "code") ProCodeTries.missed()
                said = when {
                    ProCodeTries.waiting() -> CODE_WAIT
                    e.code == "code" && kind == CodeKind.EIGHT && eightAs == EightKind.INVITE -> "초대 코드가 맞지 않아요. 친구에게 다시 확인해 주세요."
                    else -> serverProblem(e.code)
                }
            } catch (e: Exception) {
                said = serverProblem("bad_response")
            } finally {
                busy = false
            }
        }
    }
    SectionCard(title = null, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "접기" else "코드 넣는 칸 열기") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(CODE_INBOX_TITLE, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!open) return@SectionCard
        Text("받은 코드를 넣으면 어떤 코드인지 알아서 가려요. 가족 프로·친구 초대·프로 나눔·이용권 복구 코드를 여기에 넣어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // [INPUT-04] The code copied on the old phone or from a message: one tap.
        if (code.isBlank()) com.bamnat.app.ui.components.ClipboardHint("받은 코드", fits = { codeKind(it) != CodeKind.NONE },
            onPaste = { code = it.trim().uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == ' ' }.take(24); said = null })
        OutlinedTextField(
            code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == ' ' }.take(24); said = null },
            label = { Text("받은 코드") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { send() }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (kind) {
            CodeKind.LINK -> Text("6자리 연결 코드예요. 넣으면 이 휴대폰을 연결해요.", style = MaterialTheme.typography.bodySmall)
            CodeKind.RECOVERY -> Text("이용권 복구 코드예요. 옮기면 그 휴대폰의 이용권은 끝나요.", style = MaterialTheme.typography.bodySmall)
            CodeKind.EIGHT -> {
                if (eightNo != null) Text(eightNo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    if (inviteOk && shareOk) {
                        Text("어떤 코드인가요?", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EightKind.entries.forEach { k -> FilterChip(selected = eight == k, onClick = { eight = k; said = null }, label = { Text(k.label) }) }
                        }
                    }
                    if (eightAs == EightKind.INVITE) Text(inviteUseConsent(InviteRules.code(code)!!, inviteState.rules.days), style = MaterialTheme.typography.bodySmall)
                    if (eightAs == EightKind.SHARE) Text("프로를 산 분이 나눠 준 코드예요. 그분이 프로를 쓰는 동안 이어져요.", style = MaterialTheme.typography.bodySmall)
                }
            }
            CodeKind.NONE -> if (code.count { it.isLetterOrDigit() } >= 6) Text("코드 모양이 맞지 않아요. 받은 그대로 넣어 주세요(6·8·16자리).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 조건 2: 이 화면의 채운 단추는 '프로 시작하기' 하나라, 넣기는 테두리 단추.
        OutlinedButton(onClick = { send() }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (kind == CodeKind.EIGHT && eightAs == EightKind.INVITE) "동의하고 넣기" else "넣기")
        }
        said?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
