package com.bamnat.app.ui.grow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.diag.CrashLog
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.pro.InviteProgressLines
import com.bamnat.app.ui.pro.InviteShareAction
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GrowNudges
import com.bamnat.core.guide.GuideState
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AG 1.27 [INVITE-11][INVITE-12] Growing the app together (사용자 리뷰 2026-10-25: "친구 초대는 프로에 있는게 아니고 사용자들이 보기
 * 좋은곳 … 버그나 오류 개선점을 많이 남길수 있도록 문의활용성을 권고해야해"): the asks that are not on the 프로 screen, how
 * often they come (core [GrowNudges], 결정 AG-D6) and the 문의 they open (the existing [InquiryLinks]).
 */
object GrowAsk {
    /** The feature switch of the 설정 card (기능 켜고 끄기). */
    const val CARD_ID = "grow.card"

    /** What a 문의 started from here begins with: the kind the person can still change, and where it came from. */
    fun feedback(about: String, kind: InquiryKind = InquiryKind.OTHER, withCheck: Boolean = false) =
        InquiryLinks.ask(InquiryStart(about = about, kind = kind, withCheck = withCheck))

    /**
     * After a 되돌리기 on the calendar: the [GrowNudges.UNDO_COUNT]th of the day, once in [GrowNudges.UNDO_EVERY_DAYS],
     * one line with '알려 주기'.
     */
    suspend fun afterUndo(c: AppContainer, host: SnackbarHostState) {
        if (!c.grow.undone()) return
        val r = host.showSnackbar("잘 안 되는 게 있었나요? 알려 주시면 고칠게요", actionLabel = "알려 주기", duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) feedback("달력 › 되돌리기")
    }

    fun off(state: GuideState, id: String): Boolean = FeatureRegistry.get(id)?.let { state.isOff(it, GuideState.ALL) } ?: false
}

/**
 * AG 1.27 [INVITE-11][INVITE-12] 설정 맨 위 '함께 만들어요': 친구 초대 (the rules and progress, the link) and '불편한 점 알려
 * 주기' (a 문의, answered by the developer). '닫기' for 30 days; 기능 켜고 끄기 `grow.card` turns it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrowSettingsCard(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val guide = com.bamnat.app.data.GuideStore.get(LocalContext.current)
    val guideState by guide.state.collectAsStateWithLifecycle()
    val grow by c.grow.state.collectAsStateWithLifecycle()
    val state by c.invites.store.state.collectAsStateWithLifecycle()
    // Both halves go through the company server (an invite, a 문의 that is answered): none without it.
    if (!c.inviteApi.enabled || GrowAsk.off(guideState, GrowAsk.CARD_ID) || !GrowNudges.card(grow, Today.date())) return
    var said by remember { mutableStateOf<String?>(null) }
    val inviting = true
    LaunchedEffect(Unit) { runCatching { c.invites.refresh() } }
    SoftCard(modifier.fillMaxWidth().padding(bottom = 4.dp), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("함께 만들어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (inviting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PersonAdd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" 친구 초대", style = MaterialTheme.typography.titleSmall)
                }
                Text(state.rules.words() + ".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InviteProgressLines(c)
                said?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                InviteShareAction(c, onMessage = { said = it }, tonal = true) {
                    TextButton(onClick = { com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.PRO) }) { Text("자세히") }
                }
                HorizontalDivider()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Feedback, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(" 불편한 점 알려 주기", style = MaterialTheme.typography.titleSmall)
            }
            Text(FEEDBACK_WORDS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { GrowAsk.feedback("설정 › 함께 만들어요") }) { Text("알려 주기") }
                TextButton(onClick = { c.grow.cardClosed() }) { Text("닫기") }
            }
        }
    }
}

/** AG 1.27 [INVITE-12] What a 문의 is for, said where it is offered. */
const val FEEDBACK_WORDS = "버그·오류·불편한 점·바라는 점을 남겨 주세요. 개발자가 직접 읽고 답을 드리고, 고친 것은 새 판 소식에 적어요."

/**
 * AG 1.27 [INVITE-12] On the calendar after the app stopped by itself (diag/CrashLog kept a note): one card asking
 * what happened, opening a 문의 with the check attached (the note leaves the phone only then, as before), once a note.
 */
@Composable
fun CrashAskNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val grow by c.grow.state.collectAsStateWithLifecycle()
    var last by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        last = withContext(Dispatchers.IO) {
            CrashLog.notes(context).firstOrNull()?.name?.removePrefix("crash-")?.removeSuffix(".txt")?.toLongOrNull() ?: 0L
        }
    }
    if (!GrowNudges.crash(grow, last, Today.millis())) return
    SoftCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("앱이 갑자기 닫혔어요", style = MaterialTheme.typography.titleMedium)
            Text("어떤 화면에서 무엇을 하다 그랬는지 알려 주시면 고칠게요. 보내기 전에 점검 내용을 보고 고를 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    c.grow.crashSeen(last)
                    GrowAsk.feedback("앱이 갑자기 닫힘", kind = InquiryKind.BUG, withCheck = true)
                }) { Text("점검 내용과 함께 알려 주기") }
                TextButton(onClick = { c.grow.crashSeen(last) }) { Text("괜찮아요") }
            }
        }
    }
}
