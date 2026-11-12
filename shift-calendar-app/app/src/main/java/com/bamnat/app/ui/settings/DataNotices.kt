package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.AppLock
import com.bamnat.app.NoticeLinks
import com.bamnat.app.appContainer
import com.bamnat.app.data.PhoneMove
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVL-02] (검토 DATA-08) A restore the app was closed in the middle of was put back to the data as it was before it, at this start:
 * said once over the calendar, with the way to the file again.
 */
@Composable
fun RestoreStoppedNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val stopped by c.restoreStopped.collectAsStateWithLifecycle()
    if (!stopped) return
    NudgeCard(
        // 조건 1·4: short, so the calendar keeps the top of the screen also at 200%.
        "불러오기가 멈춰 전 기록으로 돌려놓았어요",
        modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        tone = Tone.Caution,
        icon = Icons.Outlined.Restore,
        detail = "기록은 섞이지 않았어요",
        action = "다시 불러오기",
        onAction = {
            c.restoreStopped.value = false
            SettingsLinks.open(SettingsPage.DATA, "파일 불러오기")
        },
        onClose = { c.restoreStopped.value = false },
    )
}

/**
 * 2.0.0 [RVL-06][RVL-07] (검토 U12-03·DATA-10·T12-04) The first start of a phone the data came to by Android's own backup (구글 계정으로
 * 되살리기, 휴대폰 옮기기): one card with what came (알람 다시 걸었어요, 이어받은 공유 링크), what to do again (받은 근무표 다시 잇기, 알림·정확한
 * 알람 허용) and what stays on the old phone ([MovedDialog]). Its one button is the first thing left to do; it goes once closed.
 */
@Composable
fun PhoneMovedNotice(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val moved by c.phoneMove.moved.collectAsStateWithLifecycle()
    val m = moved ?: return
    val context = LocalContext.current
    // Allowed on the way back from the phone's settings: read again each time the app comes to the front.
    var allowed by remember { mutableStateOf(true to true) }
    // 조건 13: a 가게 or 투자 phone with no work alarm is not told its alarms were set again, nor asked for exact alarms.
    var ringing by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        ringing = runCatching { c.alarmScheduler.systemHolds() }.getOrDefault(true)
        allowed = com.bamnat.app.alarm.Notifications.canPost(context) to (c.alarmScheduler.canScheduleExact() || !ringing)
        onPauseOrDispose { }
    }
    var said by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val todo = movedTodo(m, allowed.first, allowed.second)
    NudgeCard(
        "새 휴대폰으로 옮겨 왔어요",
        modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        tone = if (todo == MovedTodo.NONE) Tone.Good else Tone.Plain,
        icon = Icons.Outlined.PhoneAndroid,
        detail = said ?: movedLine(m, allowed.first, allowed.second, ringing),
        action = when (todo) {
            // Short, beside the words on a folded phone (the line above says what is joined or allowed).
            MovedTodo.RELINK -> if (busy) "잇는 중…" else "다시 잇기"
            MovedTodo.ALLOW -> "허용하기"
            MovedTodo.NONE -> "자세히"
        },
        onAction = {
            when (todo) {
                MovedTodo.RELINK -> if (!busy) {
                    busy = true
                    scope.launch {
                        said = relinkMoved(c)
                        busy = false
                    }
                }
                MovedTodo.ALLOW -> NoticeLinks.open(NoticeLinks.Target.ALARM_CHECK)
                MovedTodo.NONE -> open = true
            }
        },
        onClose = { c.phoneMove.close() },
        onClick = { open = true },
    )
    if (open) MovedDialog(m, allowed.first, allowed.second, ringing, onDismiss = { open = false })
}

internal enum class MovedTodo { RELINK, ALLOW, NONE }

/** What is left to do first: the received links (a 리더's roster stops coming in until then), then what to allow again. */
internal fun movedTodo(m: PhoneMove.Moved, notify: Boolean, exact: Boolean): MovedTodo = when {
    m.waiting.isNotEmpty() -> MovedTodo.RELINK
    !notify || !exact -> MovedTodo.ALLOW
    else -> MovedTodo.NONE
}

/**
 * The card's second line: the thing most worth knowing (and a second while it fits), short (디자인 품질 조건 4), the first one what its
 * button does.
 * [ringing]: a work alarm is set on this phone (a 가게 or 투자 phone with none is not told about alarms, 조건 13).
 */
internal fun movedLine(m: PhoneMove.Moved, notify: Boolean, exact: Boolean, ringing: Boolean = true): String = buildList {
    m.waiting.firstOrNull { it.mine }?.let { add("${it.title.ifBlank { "리더" }} 근무표를 다시 이어 주세요") }
        ?: m.waiting.takeIf { it.isNotEmpty() }?.let { add("받은 근무표 ${it.size}개를 다시 이어 주세요") }
    if (m.lostReceived > 0) add("받은 근무표 ${m.lostReceived}개는 보낸 분께 링크를 다시 받아 주세요")
    if (!notify) add("알림을 다시 허용해 주세요") else if (!exact) add("정확한 알람을 다시 허용해 주세요")
    if (ringing) add("알람은 다시 걸었어요")
    if (m.links > 0) add("내 공유 링크 ${m.links}개를 이어받았어요")
    if (!m.keys) add("공유 링크 열쇠는 옛 휴대폰 백업 파일로 옮겨요")
    if (isEmpty()) add("설정도 그대로 옮겼어요")
}.let { items ->
    // The first always; the second only while both fit two short lines on a folded phone (never cut with '…', 조건 4·12).
    val two = items.take(2).joinToString(" · ")
    if (items.size > 1 && two.length <= LINE_FITS) two else items.first()
}

/** Characters two lines of a card's second line hold on a folded phone (369dp) at 100%. */
private const val LINE_FITS = 28

/** Every line of what came, what to do again and what stays behind, for a tap on the card. */
@Composable
private fun MovedDialog(m: PhoneMove.Moved, notify: Boolean, exact: Boolean, ringing: Boolean, onDismiss: () -> Unit) {
    val style = LocalContext.current.appContainer.settingsRepository.current.workStyle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("옮겨 온 것 확인") },
        text = {
            // Scrolls inside: with big letters the three groups are taller than the window (조건 12).
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("그대로 왔어요", listOfNotNull(
                    movedRecords(style),
                    "설정: ${movedSettings(style)}",
                    "알람: 이 휴대폰에 다시 걸었어요".takeIf { ringing },
                    "내가 만든 공유 링크 ${m.links}개".takeIf { m.links > 0 },
                ))
                Section("한 번 더 해 주세요", listOfNotNull(
                    "받은 근무표 ${m.waiting.size}개 다시 잇기 (${m.waiting.joinToString(", ") { it.title.ifBlank { "이름 없음" } }})".takeIf { m.waiting.isNotEmpty() },
                    "알림 허용".takeIf { !notify },
                    "정확한 알람 허용".takeIf { !exact },
                    "헬스 커넥트·잠 자동으로 잡기·방해 금지 허용(쓰던 분만)",
                ))
                Section("옛 휴대폰에만 있어요", listOfNotNull(
                    "밤 소리 클립",
                    "공유 링크를 고치는 열쇠와 프로 복구 코드: 화면 잠금이 없던 휴대폰의 구글 백업에는 들어가지 않아요. 옛 휴대폰에서 암호를 건 백업 파일로 옮겨 주세요".takeIf { !m.keys },
                    "받은 근무표 ${m.lostReceived}개: 같은 까닭으로 오지 않았어요. 보낸 분께 링크를 다시 받아 주세요".takeIf { m.lostReceived > 0 },
                    "영수증·메모 사진: 구글 백업에는 빠져요(휴대폰 옮기기와 ‘사진도 백업’ 파일에는 들어가요)",
                ))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}

/** 조건 13: what came, in the words of the user's kind of work (a 가게 has no 조 or 근무 약어). */
internal fun movedRecords(style: WorkStyle?): String = when (style) {
    WorkStyle.BUSINESS -> "매출·메모·돈 기록과 달력"
    WorkStyle.INVEST -> "매매·메모 기록과 달력"
    WorkStyle.FIELD -> "일정·메모·돈 기록과 달력"
    else -> "근무·메모·돈 기록과 달력"
}

internal fun movedSettings(style: WorkStyle?): String = when (style ?: WorkStyle.SHIFT) {
    WorkStyle.SHIFT -> "입사일, 모든 조, 근무 약어, 끈 기능, 회사·급여 방식, 희망 오프, 테마"
    WorkStyle.DAY -> "입사일, 끈 기능, 회사·급여 방식, 테마"
    WorkStyle.HOURLY -> "끈 기능, 급여 방식, 희망 오프, 테마"
    else -> "끈 기능, 테마와 화면 설정"
}

@Composable
private fun Section(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        lines.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * 2.0.0 [RVL-07] (검토 DATA-10) Reads each received link the old phone followed again, as it had it (내 근무표, 따라가기); returns what to say.
 * A link that is gone does not wait any more; one that could not be reached waits for the next press.
 */
internal suspend fun relinkMoved(c: AppContainer): String {
    val m = c.phoneMove.moved.value ?: return ""
    if (!c.shareApi.enabled) return "이 휴대폰에서는 공유를 쓸 수 없어요"
    val done = mutableListOf<String>()
    var gone = 0
    var waiting = 0
    for (w in m.waiting) {
        try {
            val share = c.shareApi.read(w.id)
            c.received.relink(w.id, share, mine = w.mine, adopted = w.adopted, follow = w.follow)
            done += w.id
        } catch (e: ShareLinkException) {
            if (e.message == "not_found") {
                gone++
                done += w.id
            } else waiting++
        } catch (e: java.io.IOException) {
            waiting++
        }
    }
    c.phoneMove.joined(done)
    val joined = done.size - gone
    return when {
        waiting > 0 && joined == 0 -> "인터넷에 연결되지 않았어요. 연결되면 다시 눌러 주세요"
        waiting > 0 -> "${joined}개를 이었어요. ${waiting}개는 연결되면 다시 눌러 주세요"
        gone > 0 && joined == 0 -> "링크가 끊겼거나 기간이 지났어요. 보낸 분께 새 링크를 부탁해 보세요"
        gone > 0 -> "${joined}개를 이었어요. ${gone}개는 끊긴 링크라 새 링크가 필요해요"
        else -> "받은 근무표 ${joined}개를 다시 이었어요"
    }
}

/**
 * 2.0.0 [RVL-09] (검토 DATA-11·U12-08) 앱 잠금 is on, but the phone's own lock was taken off: the app opens as it is. Said over the
 * calendar, with the way to the phone's lock settings (the switch stays as the user left it, and locks again once there is a lock).
 */
@Composable
fun AppLockRestingNotice(on: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var resting by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(on) {
        resting = AppLock.resting(context, on)
        onPauseOrDispose { }
    }
    if (!resting) return
    NudgeCard(
        "휴대폰 잠금이 없어 앱 잠금이 쉬고 있어요",
        modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        tone = Tone.Caution,
        icon = Icons.Outlined.Lock,
        detail = "휴대폰에 잠금을 걸면 다시 잠겨요",
        action = "잠금 설정 열기",
        onAction = {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
        },
    )
}
