@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bamnat.app.ui.memo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Warning
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
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
    // 2.0.0 [RVIB-04] Told by the work: "출근 1시간 전마다", "야간마다 출근 1시간 전", once "출근 1시간 전 · 11/13".
    r.work?.let { a -> return workLine(memo, a, next, shiftName) }
    val again = com.bamnat.core.memo.MemoRepeat.label(memo.repeat, shiftName).takeIf { memo.date != null && it.isNotEmpty() }
        ?: return scheduleText(r.at)
    val time = scheduleText(r.at).substringAfter(") ")
    return "$again $time" + (next?.let { " · 다음 ${it.monthValue}/${it.dayOfMonth}" } ?: "")
}

/** 2.0.0 [RVIB-04] (검토 I07ac-01) A schedule told by the work, as its chip and card say it. */
internal fun workLine(memo: com.bamnat.core.memo.Memo, a: com.bamnat.core.memo.WorkAnchor, next: LocalDateTime? = null, shiftName: (Long) -> String? = { null }): String {
    val kind = com.bamnat.core.memo.MemoRepeat.kindOf(memo.repeat)
    val head = when {
        memo.date == null || kind == com.bamnat.core.memo.MemoRepeat.Kind.NONE -> a.label
        kind == com.bamnat.core.memo.MemoRepeat.Kind.DAY -> a.label + "마다"
        else -> com.bamnat.core.memo.MemoRepeat.label(memo.repeat, shiftName) + " " + a.label
    }
    return head + (next?.let { " · 다음 ${it.monthValue}/${it.dayOfMonth} ${Fmt.time(it.toLocalTime())}" } ?: "")
}

/** [SCHED-01] How a schedule is brought up, in a few words: "알람·알림", "알람", "알림". */
internal fun scheduleWays(r: MemoReminder): String = when {
    r.ring && r.notify -> "울리는 알람·알림 메시지"
    r.ring -> "울리는 알람"
    else -> "알림 메시지"
}

/** [SCHED-01] The schedule in the editor's chip row: tap to change, ✕ to make it a plain memo again. */
@Composable
internal fun ScheduleChip(reminder: MemoReminder?, onOpen: () -> Unit, onClear: () -> Unit = {}, line: String? = null) {
    // 2.0.0 [RVI-30] (검토 MEMO-09·28·29) One name for it, '알림' ('알람' only when it rings). [RVP-10] (검토 UIX-10) The X is pressed
    // as 48dp beside the chip, not 18dp inside it, and says what it does ('알림 빼기', the window's word too).
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        AssistChip(
            onClick = onOpen,
            label = { Text(reminder?.let { "${line ?: scheduleText(it.at)} · ${if (it.ring) "알람" else "알림"}" } ?: "알림 넣기") },
            leadingIcon = {
                Icon(if (reminder?.ring == true) Icons.Filled.Alarm else Icons.Filled.EventAvailable, null, Modifier.size(18.dp))
            },
        )
        if (reminder != null) com.bamnat.app.ui.components.ChipClear("알림 빼기", onClear)
    }
}

/**
 * [SCHED-01] Under the memo when it names a time: "이 메모, 일정으로 넣을까요?". It asks once per time;
 * when the memo is a schedule already and the written time changed, it offers the new time.
 *
 * 2.0.0 [RVI-75] (검토 U07a-06, 디자인 품질 3) It was a big card under the words (a title, a line of explanation and two buttons, one
 * of them filled): now the one line every tip has ([com.bamnat.app.ui.components.NudgeCard]) over the keyboard — "10월 15일 (목) 오후
 * 3:00 · 알림 넣기", X to keep it a memo — so the memo being written keeps the screen and the page's filled button stays its own.
 * A time that runs into a 근무 or the sleep after a 야간 says so under it, in the caution colour.
 */
@Composable
internal fun ScheduleOffer(found: MemoTime.Found, changing: Boolean, onAccept: () -> Unit, onDecline: () -> Unit, clash: String? = null) {
    val at = scheduleText(found.at)
    com.bamnat.app.ui.components.NudgeCard(
        text = if (changing) "$at(으)로 바꿀까요?" else at,
        icon = Icons.Filled.EventAvailable,
        tone = if (clash != null) com.bamnat.app.ui.theme.Tone.Caution else com.bamnat.app.ui.theme.Tone.Plain,
        detail = clash,
        action = if (changing) "바꾸기" else "알림 넣기",
        onAction = onAccept,
        onClose = onDecline,
        closeLabel = if (changing) "그대로 둘게요" else "메모로 둘게요",
        // (디자인 품질 14) Read aloud as it shows: the time, then '알림 넣기' and '메모로 둘게요'.
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * 2.0.0 [RVIB-08] (검토 I07ac-04) Under the memo when one line says a repeat and an amount ("매달 25일 월세 50만 원"): the same one
 * line as a time's offer — "매달 25일 월세 500,000원 · 반복 메모와 가계부 고정 지출로" · 넣기, X to keep it a memo.
 */
@Composable
internal fun MoneyOffer(found: com.bamnat.core.memo.MoneyRepeatLine.Found, onAccept: () -> Unit, onDecline: () -> Unit) {
    val what = if (found.kind == com.bamnat.core.money.MoneyKind.INCOME) "고정 수입" else "고정 지출"
    com.bamnat.app.ui.components.NudgeCard(
        text = "${found.said} ${found.name} ${com.bamnat.app.ui.Fmt.won(found.amount)} · 반복 메모와 가계부 $what${com.bamnat.app.ui.Fmt.ro(what)}",
        icon = Icons.Filled.Repeat,
        action = "넣기",
        onAction = onAccept,
        onClose = onDecline,
        closeLabel = "메모로 둘게요",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
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
    clashOf: (LocalDateTime) -> com.bamnat.core.memo.ShiftClash.Clash? = { null },
    /** 2.0.0 [RVIB-05] 근무 중 메모 알림 모으기 is on: a notice at work says it comes when the work ends. */
    heldAtWork: Boolean = false,
    /**
     * 1.39 [TASK-44] PC 6차 (매일 반복을 고르는 곳을 못 찾음): the memo's repeat ([com.bamnat.core.memo.Memo.repeat]) as '반복: 안 함·매일·평일·
     * 쉬는 날' here, called with the kind picked before [onSave] when it changed; null leaves the row out. [restRepeat]: the calendar
     * has a schedule to read days off from.
     */
    repeat: String = "",
    /** 2.0.0 [RVI-73] The stored text of the repeat picked in the one repeat window ("" for none, "S12" for 근무마다). */
    onRepeat: ((String) -> Unit)? = null,
    restRepeat: Boolean = false,
    /** 2.0.0 [RVI-73] The calendar's kinds of shift, for 근무마다 in the repeat window. */
    shiftTypes: List<com.bamnat.core.model.ShiftType> = emptyList(),
    /** 2.0.0 [RVIB-04] (검토 I07ac-01) When each 근무 기준 choice is next told, read from the calendar's work; null leaves the row out. */
    workNext: ((com.bamnat.core.memo.WorkAnchor) -> LocalDateTime?)? = null,
    /** 2.0.0 [RVIB-04] '야간 끝나고 일어나면': how long the sleep after a night is (잠 목표). */
    wakeAfterMinutes: Int = 7 * 60,
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
    var again by rememberSaveable { mutableStateOf(repeat) }
    var repeating by remember { mutableStateOf(false) }
    val at = date.atTime(time)
    // 2.0.0 [RVIB-04] Told by the work instead of a clock time: its next time is read from the calendar.
    var work by rememberSaveable { mutableStateOf(current?.work?.code) }
    val anchor = com.bamnat.core.memo.WorkAnchor.parse(work)
    val anchorAt = anchor?.let { workNext?.invoke(it) }
    // A repeating schedule whose time today is gone rings on its next day, so it can be kept.
    val ahead = if (anchor != null) anchorAt != null else at.isAfter(now()) || again.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EventAvailable, null) },
        title = { Text(if (current == null) "알림 시각" else "알림 시각 바꾸기") },
        text = {
            // 2.0.0 [RVI-31] (검토 MEMO-17) Scrolls: on a folded phone or at 200% text the ways and the 허용하기 button were cut off.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 2.0.0 [RVI-31] (디자인 품질 12) At 200% on a folded phone the time stood in three lines beside the date: each keeps
                // one line, and the time goes under the date when they do not fit side by side.
                if (anchor == null) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text(Fmt.dayTitle(date), maxLines = 1, softWrap = false) }
                    OutlinedButton(onClick = { pickTime = true }) { Text(scheduleText(at).substringAfter(") "), maxLines = 1, softWrap = false) }
                }
                if (workNext != null) WorkRow(anchor, wakeAfterMinutes, anchorAt, workNext) { a ->
                    work = a?.code
                    // "출근 1시간 전마다": picked on a memo that does not repeat, it comes back on each day with work.
                    if (a != null && again.isEmpty() && onRepeat != null) again = com.bamnat.core.memo.MemoRepeat.code(com.bamnat.core.memo.MemoRepeat.Kind.DAY, date)
                }
                // 2.0.0 [RVI-73] (검토 U07a-07) One line that opens the one repeat window (the editor's chip opens the same).
                if (onRepeat != null) RepeatLine(again, shiftTypes, worked = anchor != null) { repeating = true }
                if (anchor == null && shiftTimes.isNotEmpty()) ShiftTimeChips(shiftTimes, at) { date = it.toLocalDate(); time = it.toLocalTime() }
                if (ahead && anchor == null) clashOf(at)?.let { c ->
                    ClashLine(if (heldAtWork && notify && !ring && !c.sleep) "${c.text} · 알림은 근무가 끝날 때 모아 와요" else c.text)
                }
                if (!ahead) {
                    Text(if (anchor != null) "다가오는 근무가 없어요. 근무표를 채우거나 시각으로 골라 주세요." else "지난 시각이에요. 날짜나 시각을 바꿔 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                if (onRepeat != null && again != repeat) onRepeat(again)
                onSave(MemoReminder(anchorAt ?: at, ring, notify, work = anchor))
            }, enabled = ahead && (ring || notify)) { Text(if ((notify || ring) && !notificationsOk) "알림 켜고 저장" else "저장") }
        },
        dismissButton = {
            Row {
                if (current != null && onClear != null) TextButton(onClick = onClear) { Text("알림 빼기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    // 2.0.0 [RVI-73] The one repeat window over this one; what is picked is kept with the schedule when it is saved.
    if (repeating) RepeatSheet(again, date, onDismiss = { repeating = false }, onPick = { again = it }, shiftTypes = shiftTypes,
        onShift = { again = com.bamnat.core.memo.MemoRepeat.shiftCode(it) }, restRepeat = restRepeat)
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

/**
 * [SCHED-01] Where the dialog starts: the found time, the schedule already set, or the next round hour (09:00 on a later day).
 * 2.0.0 [RVI-79] (검토 U07a-10·T07a-05) That last one moved past a 근무 or the sleep after a 야간 on [days] (퇴근 뒤, 깬 뒤), so a 교대
 * worker does not start at a time they are at work or asleep.
 */
internal fun scheduleStart(
    found: MemoTime.Found?, current: MemoReminder?, date: LocalDate?, now: LocalDateTime,
    days: List<com.bamnat.core.memo.ShiftMemoTime.Day> = emptyList(),
): LocalDateTime =
    found?.at ?: current?.at ?: run {
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        com.bamnat.core.memo.ShiftClash.free(if (date != null && date.isAfter(now.toLocalDate())) date.atTime(LocalTime.of(9, 0)) else nextHour, days)
    }

/**
 * 1.39 [TASK-44] '반복' in the 일정 창. 2.0.0 [RVI-73] (검토 U07a-07) One line with what it is now, opening the one repeat window
 * ([RepeatSheet]): the chips here (안 함·매일·평일·쉬는 날) were another list than the editor's.
 */
@Composable
private fun RepeatLine(repeat: String, shiftTypes: List<com.bamnat.core.model.ShiftType>, worked: Boolean = false, onOpen: () -> Unit) {
    val names = remember(shiftTypes) { shiftTypes.associate { it.id to it.name } }
    // 2.0.0 [RVIB-04] Told by the work, 매일 is each day with work.
    val daily = worked && com.bamnat.core.memo.MemoRepeat.kindOf(repeat) == com.bamnat.core.memo.MemoRepeat.Kind.DAY
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text("반복", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
        androidx.compose.material3.AssistChip(
            onClick = onOpen,
            label = { Text(if (daily) "근무하는 날마다" else com.bamnat.core.memo.MemoRepeat.label(repeat) { names[it] }.ifEmpty { "안 함" }, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)) },
        )
    }
}

/**
 * 2.0.0 [RVIB-04] (검토 I07ac-01) 근무에 붙여 매번: 출근 1시간·30분 전, 퇴근할 때, 퇴근 30분 뒤, 야간 끝나고 일어나면, each offered only when
 * the calendar has work for it ahead (가게·투자처럼 근무 시각이 없으면 줄이 없어요). [picked] first if it is another one saved before.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WorkRow(
    picked: com.bamnat.core.memo.WorkAnchor?, wakeAfter: Int, next: LocalDateTime?,
    nextOf: (com.bamnat.core.memo.WorkAnchor) -> LocalDateTime?, onPick: (com.bamnat.core.memo.WorkAnchor?) -> Unit,
) {
    val S = com.bamnat.core.memo.WorkAnchor.From.START
    val E = com.bamnat.core.memo.WorkAnchor.From.END
    val all = listOf(com.bamnat.core.memo.WorkAnchor(S, -60), com.bamnat.core.memo.WorkAnchor(S, -30), com.bamnat.core.memo.WorkAnchor(E, 0),
        com.bamnat.core.memo.WorkAnchor(E, 30), com.bamnat.core.memo.WorkAnchor(com.bamnat.core.memo.WorkAnchor.From.WAKE, wakeAfter))
    val offered = (listOfNotNull(picked?.takeIf { it !in all }) + all).filter { it == picked || nextOf(it) != null }
    if (offered.isEmpty()) return
    Text("근무에 붙여 매번", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.bamnat.app.ui.components.FilterChip(selected = picked == null, onClick = { onPick(null) }, label = { Text("시각으로") })
        for (a in offered) com.bamnat.app.ui.components.FilterChip(selected = picked == a, onClick = { onPick(a) }, label = { Text(a.label) })
    }
    if (picked != null && next != null) {
        Text("다음 ${scheduleText(next)} · 근무가 바뀌면 저절로 맞춰요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
