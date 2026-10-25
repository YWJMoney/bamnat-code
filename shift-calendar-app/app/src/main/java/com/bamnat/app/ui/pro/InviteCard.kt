package com.bamnat.app.ui.pro

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import com.bamnat.core.tier.InviteRules
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
    "credited" -> "사용 기간을 채웠어요. 초대한 분이 프로를 받았어요."
    "capped" -> "사용 기간을 채웠어요. 초대한 분은 올해 받을 수 있는 프로를 모두 받았어요."
    "expired" -> "사용 기간 안에 앱을 다시 열지 않아 이 초대는 끝났어요."
    else -> InviteRules.refused(a.reason)
}

/**
 * [INVITE-02][INVITE-05][INVITE-03] 설정 › 프로 › 친구 초대: the inviter's counts and link (made after the line above),
 * shared with the phone's share sheet; and, for a phone installed in the last 7 days, 초대 코드 넣기.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InviteCard(c: AppContainer, onMessage: (String) -> Unit) {
    if (!c.inviteApi.enabled) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val view = state.view
    val terms = state.rules
    LaunchedEffect(view?.code) { runCatching { c.invites.refresh() } }
    SectionCard(title = "친구 초대") {
        Text("내 링크로 친구가 밤낮을 깔고 첫 설정 뒤 초대를 알린 날부터 ${terms.days}일이 지나 앱을 다시 열면 친구 한 명으로 세요. " +
            "${terms.words()}. 휴대폰마다 한 번이고, 정식 출시 전에 받은 달은 출시 날부터 세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (view?.code != null) {
            Text(InviteRules.progress(view.progress, terms.need, view.waiting), style = MaterialTheme.typography.titleMedium)
            Text(InviteRules.summary(view.friends, view.given), style = MaterialTheme.typography.bodyMedium)
            if (view.capReached) {
                Text("올해 받을 수 있는 ${terms.capMonths}개월을 모두 받았어요. 친구는 계속 셀게요.", style = MaterialTheme.typography.bodySmall)
            }
            Text("내 초대 코드 ${InviteRules.shown(view.code)}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        if (asking && view?.code == null) {
            Text(INVITE_MAKE_CONSENT, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                view?.code != null && view.link != null -> Button(onClick = { share(context, InviteRules.shareText(view.link, view.code)) }) {
                    Text("초대 링크 보내기")
                }
                !asking -> Button(onClick = { asking = true }) { Text("초대 링크 만들기") }
                else -> Button(enabled = !busy, onClick = {
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
                }) { Text("동의하고 링크 만들기") }
            }
            if (c.invites.mayType() && !typing) TextButton(onClick = { typing = true }) { Text("초대 코드 넣기") }
        }
        if (typing && c.invites.mayType()) {
            OutlinedTextField(typed, { typed = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(9) },
                label = { Text("받은 초대 코드 (8자리)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth())
            val code = InviteRules.code(typed)
            if (code != null) Text(inviteUseConsent(code, terms.days), style = MaterialTheme.typography.bodySmall)
            Button(enabled = code != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val a = c.invites.report(code!!, "typed")
                        typing = false
                        onMessage(inviteAnswerWords(a))
                    } catch (e: CompanyException) {
                        onMessage(if (e.code == "code") "초대 코드가 맞지 않아요. 친구에게 다시 확인해 주세요." else serverProblem(e.code))
                    } finally {
                        busy = false
                    }
                }
            }) { Text("동의하고 보내기") }
        }
        state.answer?.takeIf { state.reportedCode != null }?.let {
            Text(inviteAnswerWords(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [INVITE-03] On the calendar after the first setup: the friend's phone found an invite code in Play's install referrer
 * and asks once whether to tell the server. Nothing is sent before 알리기.
 */
@Composable
fun InviteArrivalNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    if (!c.inviteApi.enabled) return
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val code = state.found?.takeIf { !state.offerClosed && state.reportedCode == null }
    // [INVITE-08] The period of use to say; the rules go without anything about this phone.
    LaunchedEffect(code) { if (code != null) runCatching { c.invites.loadRules() } }
    if (code == null && said == null) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (said != null) {
                Text(said!!, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { said = null }) { Text("닫기") }
                return@Column
            }
            Text("친구 초대로 오셨나요?", style = MaterialTheme.typography.titleMedium)
            Text(inviteUseConsent(code!!, state.rules.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        said = try {
                            inviteAnswerWords(c.invites.report(code, "referrer"))
                        } catch (e: CompanyException) {
                            serverProblem(e.code)
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

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(send, "초대 링크 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
