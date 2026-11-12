@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bamnat.app.ui.memo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bamnat.core.memo.MemoRepeat.Kind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SystemSettings
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.app.ui.components.rememberNotificationAsk
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.MemoTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import com.bamnat.core.time.Today

/** [SCHED-01] "10월 20일 (화) 오후 3:00" */
internal fun scheduleText(at: LocalDateTime): String {
    val h = at.hour % 12
    val time = "${if (at.hour < 12) "오전" else "오후"} ${if (h == 0) 12 else h}:%02d".format(at.minute)
    return "${Fmt.dayTitle(at.toLocalDate())} $time"
}

/**
 * 2.0.0 [RVI-49] (검토 MEMO-11) A schedule as its chip and card say it: once "11월 13일 (금) 오전 9:00"; repeating "매일 오전 9:00 · 다음
 * 11/13" (it said the first day, 11/1, as if it were past). [next]: its next time, where it can be worked out.
 */
internal fun scheduleLine(memo: com.bamnat.core.memo.Memo, r: MemoReminder, next: LocalDateTime? = null, shiftName: (Long) -> String? = { null }): String {
    val again = com.bamnat.core.memo.MemoRepeat.label(memo.repeat, shiftName).takeIf { memo.date != null && it.isNotEmpty() }
        ?: return scheduleText(r.at)
    val time = scheduleText(r.at).substringAfter(") ")
    return "$again $time" + (next?.let { " · 다음 ${it.monthValue}/${it.dayOfMonth}" } ?: "")
}

/** [SCHED-01] How a schedule is brought up, in a few words: "알람·알림", "알람", "알림". */
internal fun scheduleWays(r: MemoReminder): String = when {
    r.ring && r.notify -> "울리는 알람·알림 메시지"
    r.ring -> "울리는 알람"
    else -> "알림 메시지"
}

/** [SCHED-01] The schedule in the editor's chip row: tap to change, ✕ to make it a plain memo again. */
@Composable
internal fun ScheduleChip(reminder: MemoReminder?, onOpen: () -> Unit, @Suppress("UNUSED_PARAMETER") onClear: () -> Unit = {}, line: String? = null) {
    // 2.0.0 [RVI-30] (검토 MEMO-09·28·29) One name for it, '알림' ('알람' only when it rings); no 18dp X, '알림 빼기' is in the window.
    AssistChip(
        onClick = onOpen,
        label = { Text(reminder?.let { "${line ?: scheduleText(it.at)} · ${if (it.ring) "알람" else "알림"}" } ?: "알림 넣기") },
        leadingIcon = {
            Icon(if (reminder?.ring == true) Icons.Filled.Alarm else Icons.Filled.EventAvailable, null, Modifier.size(18.dp))
        },
    )
}

/**
 * [SCHED-01] Under the memo when it names a time: "이 메모, 일정으로 넣을까요?". It asks once per time;
 * when the memo is a schedule already and the written time changed, it offers the new time.
 */
@Composable
internal fun ScheduleOffer(found: MemoTime.Found, changing: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, clash: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EventAvailable, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    if (changing) "알림 시각을 ‘${found.phrase}’로 바꿀까요?" else "‘${found.phrase}’, 알림으로 넣을까요?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                if (changing) "${scheduleText(found.at)}에 알려 드릴게요."
                else "${scheduleText(found.at)}에 알람을 울리거나 알림 메시지로 알려 드릴 수 있어요. 메모는 그대로 남아요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            clash?.let { ClashLine(it) }
            // 2.0.0 [RVI-31] (디자인 품질 12) At 200% on a folded phone '알림 넣기' broke into two lines: the buttons keep one
            // line each and the filled one goes under when both do not fit. (디자인 품질 2) It is filled: a tonal button on this
            // card's own colour had no edge to see. (디자인 품질 14) Read aloud with its time: the editor's chips have a '알림 넣기' too.
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDecline) { Text(if (changing) "그대로 둘게요" else "메모로 둘게요", maxLines = 1, softWrap = false) }
                val said = "${if (changing) "알림 시각 바꾸기" else "알림 넣기"}, ${scheduleText(found.at)}"
                androidx.compose.material3.Button(onClick = onAccept, modifier = Modifier.semantics { contentDescription = said }) {
                    Text(if (changing) "바꾸기" else "알림 넣기", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * [SCHED-01] When, and how to be told: 울리는 알람 (rings like the shift alarm, over the lock screen)
 * and/or 알림 메시지 (a message in the notification shade). Says what is turned off on the phone that
 * the chosen way needs, with the button to turn it on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(
    initial: LocalDateTime,
    current: MemoReminder?,
    onDismiss: () -> Unit,
    onSave: (MemoReminder) -> Unit,
    now: () -> LocalDateTime = Today::dateTime,
    /** 2.0.0 [RVI-30] '알림 빼기' for a memo that has one; null leaves it out. */
    onClear: (() -> Unit)? = null,
    /** [MEMO-05] 1.27 AK: '근무에 맞춰' — the next of each shift ahead (출근 1시간 전) and the next day off. */
    shiftTimes: List<com.bamnat.core.memo.ShiftMemoTime.Suggestion> = emptyList(),
    /** 1.39 [CAL2-20] What a time runs into on the calendar (근무, 야간 뒤 잘 시간), read again as the time changes. */
    clashOf: (LocalDateTime) -> String? = { null },
    /**
     * 1.39 [TASK-44] PC 6차 (매일 반복을 고르는 곳을 못 찾음): the memo's repeat ([com.bamnat.core.memo.Memo.repeat]) as '반복: 안 함·매일·평일·
     * 쉬는 날' here, called with the kind picked before [onSave] when it changed; null leaves the row out. [restRepeat]: the calendar
     * has a schedule to read days off from.
     */
    repeat: String = "",
    onRepeat: ((com.bamnat.core.memo.MemoRepeat.Kind) -> Unit)? = null,
    restRepeat: Boolean = false,
) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var ring by rememberSaveable { mutableStateOf(current?.ring ?: false) }
    var notify by rememberSaveable { mutableStateOf(current?.notify ?: true) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    // Read again when the user comes back from the phone's settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationsOk = remember(refresh) { Notifications.canPost(context) }
    val fullScreenOk = remember(refresh) { Notifications.canUseFullScreen(context) }
    val askNotifications = rememberNotificationAsk()
    val was = remember(repeat) { com.bamnat.core.memo.MemoRepeat.kindOf(repeat) }
    var again by rememberSaveable { mutableStateOf(was) }
    val at = date.atTime(time)
    // A repeating schedule whose time today is gone rings on its next day, so it can be kept.
    val ahead = at.isAfter(now()) || again != com.bamnat.core.memo.MemoRepeat.Kind.NONE

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EventAvailable, null) },
        title = { Text(if (current == null) "알림 시각" else "알림 시각 바꾸기") },
        text = {
            // 2.0.0 [RVI-31] (검토 MEMO-17) Scrolls: on a folded phone or at 200% text the ways and the 허용하기 button were cut off.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 2.0.0 [RVI-31] (디자인 품질 12) At 200% on a folded phone the time stood in three lines beside the date: each keeps
                // one line, and the time goes under the date when they do not fit side by side.
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text(Fmt.dayTitle(date), maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = { pickTime = true }) { Text(scheduleText(at).substringAfter(") "), maxLines = 1, softWrap = false) }
                }
                if (onRepeat != null) RepeatChips(again, was, repeat, restRepeat) { again = it }
                if (shiftTimes.isNotEmpty()) ShiftTimeChips(shiftTimes, at) { date = it.toLocalDate(); time = it.toLocalTime() }
                if (ahead) clashOf(at)?.let { ClashLine(it) }
                if (!ahead) {
                    Text("지난 시각이에요. 날짜나 시각을 바꿔 주세요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Text("어떻게 알려 드릴까요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                WayRow(
                    checked = ring,
                    icon = { Icon(Icons.Filled.Alarm, null) },
                    title = "울리는 알람",
                    text = "근무 알람처럼 소리와 진동으로 울려요. 끄거나 다시 울림을 누를 때까지 울려요",
                ) { ring = it }
                WayRow(
                    checked = notify,
                    icon = { Icon(Icons.Filled.NotificationsActive, null) },
                    title = "알림 메시지",
                    text = "휴대폰 알림창에 메시지로 띄워요. 소리는 휴대폰 알림 설정을 따라요",
                ) { notify = it }
                if (!ring && !notify) {
                    Text("하나는 골라 주세요. 둘 다 고를 수도 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if ((notify || ring) && !notificationsOk) {
                    Fix("알림이 꺼져 있어요. 저장하면 알림을 켜도록 바로 물어봐요.", "지금 허용하기", askNotifications)
                } else if (ring && !fullScreenOk) {
                    Fix("‘잠금 화면에 알람 띄우기’가 꺼져 있어서 잠금 화면에서는 알림으로만 울려요.", "허용하기") {
                        if (Build.VERSION.SDK_INT >= 34) SystemSettings.open(
                            context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // [EASE-23] 1.38: notifications that are off are asked for right here as it saves, not left for the user to find
                // and turn on first; the schedule is kept either way and rings once they are allowed.
                if ((notify || ring) && !notificationsOk) askNotifications()
                if (onRepeat != null && again != was) onRepeat(again)
                onSave(MemoReminder(at, ring, notify))
            }, enabled = ahead && (ring || notify)) { Text(if ((notify || ring) && !notificationsOk) "알림 켜고 저장" else "저장") }
        },
        dismissButton = {
            Row {
                if (current != null && onClear != null) TextButton(onClick = onClear) { Text("알림 빼기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        TimePickerDialog(title = "일정 시각", initial = time, onDismiss = { pickTime = false }) {
            time = it
            pickTime = false
        }
    }
}

/** 1.39 [CAL2-20] One line: what the time runs into. A warning, the time is kept. */
@Composable
internal fun ClashLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun WayRow(checked: Boolean, icon: @Composable () -> Unit, title: String, text: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Fix(text: String, label: String, onFix: () -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onFix) { Text(label) }
    }
}

/** [SCHED-01] Where the dialog starts: the found time, the schedule already set, or the next round hour. */
internal fun scheduleStart(found: MemoTime.Found?, current: MemoReminder?, date: LocalDate?, now: LocalDateTime): LocalDateTime =
    found?.at ?: current?.at ?: run {
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        if (date != null && date.isAfter(now.toLocalDate())) date.atTime(LocalTime.of(9, 0)) else nextHour
    }

/**
 * 1.39 [TASK-44] '반복': 안 함 · 매일 · 평일 · 쉬는 날 (with a schedule to read it from). A repeat set elsewhere (매주 화요일, 야간마다) is
 * shown picked as it is, so saving here keeps it.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RepeatChips(
    picked: com.bamnat.core.memo.MemoRepeat.Kind,
    was: com.bamnat.core.memo.MemoRepeat.Kind,
    repeat: String,
    restRepeat: Boolean,
    onPick: (com.bamnat.core.memo.MemoRepeat.Kind) -> Unit,
) {
    Text("반복", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val ways = listOfNotNull(Kind.NONE to "안 함", Kind.DAY to "매일", Kind.WEEKDAYS to "평일", (Kind.REST to "쉬는 날").takeIf { restRepeat || was == Kind.REST })
        val own = (was to com.bamnat.core.memo.MemoRepeat.label(repeat)).takeIf { was != Kind.NONE && ways.none { it.first == was } }
        for ((kind, label) in ways + listOfNotNull(own)) com.bamnat.app.ui.components.FilterChip(
            selected = picked == kind,
            onClick = { onPick(kind) },
            label = { Text(label) },
        )
    }
}

/** [MEMO-05] 1.27 AK: 근무에 맞춰 — a chip per next shift (출근 1시간 전) and the next day off. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShiftTimeChips(times: List<com.bamnat.core.memo.ShiftMemoTime.Suggestion>, at: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
    Text("근무에 맞춰", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (sug in times) com.bamnat.app.ui.components.FilterChip(
            selected = at == sug.at,
            onClick = { onPick(sug.at) },
            label = { Text(sug.label) },
        )
    }
}
