package com.bamnat.app.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.DurationDialog
import com.bamnat.app.ui.components.WarningBanner
import com.bamnat.app.ui.components.rememberNotificationAsk
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.ShiftType
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.work.WorkStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.appContainer
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.pendingWays
import com.bamnat.app.ui.company.PendingDone

// ---- 7. alarms ----

@Composable
internal fun AlarmStep(vm: SetupViewModel, d: SetupDraft) {
    StepHeader("출근 몇 분 전에 깨워 드릴까요?", "근무가 있는 날에만 울려요. 쉬는 날은 조용해요.")
    val perShift = d.alarmByType.isNotEmpty()
    if (!perShift) AlarmChoices(d.alarmMinutes, "알람", onPick = vm::setAlarm)
    if (d.workTypes.size > 1) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = perShift, role = Role.Switch, onValueChange = vm::alarmPerShift)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("근무마다 다르게", style = MaterialTheme.typography.bodyLarge)
                Text("야간만 일찍 깨우는 것처럼 따로 정해요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = perShift, onCheckedChange = null)
        }
    }
    d.workTypes.forEach { t ->
        val minutes = d.alarmFor(t)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.bamnat.app.ui.components.ShiftBadge(t, size = 24.dp)
                    Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(ringLine(t, minutes), style = MaterialTheme.typography.bodyMedium,
                        color = if (minutes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (perShift) AlarmChoices(minutes, "${t.name} 알람", start = t.start, onPick = { vm.setAlarmFor(t.id, it) })
            }
        }
    }
    // [SLP-15] Asked where it is needed: without notifications nothing on the lock screen stops an alarm.
    val anyAlarm = d.workTypes.any { d.alarmFor(it) != null }
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = Notifications.canPost(context) }
    val ask = rememberNotificationAsk()
    if (anyAlarm && !canPost) {
        WarningBanner("알림을 허용해 주세요", "허용해야 잠금 화면에서 알람을 끌 수 있어요", action = "허용", onAction = ask)
    }
}

/** 안 써요, 30분 … 2시간 전, or minutes typed in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmChoices(minutes: Int?, title: String, start: java.time.LocalTime? = null, onPick: (Int?) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = minutes == null, onClick = { onPick(null) }, label = { Text("안 써요") })
        SetupDraft.ALARM_CHOICES.forEach { m ->
            FilterChip(selected = minutes == m, onClick = { onPick(m) }, label = { Text(Fmt.offset(m)) })
        }
        val own = minutes != null && minutes !in SetupDraft.ALARM_CHOICES
        FilterChip(selected = own, onClick = { typing = true }, label = { Text(if (own) Fmt.offset(minutes!!) else "직접") })
    }
    // [ALRM-06] 1.25 Z (U14): one shift's alarm on the clock wheels, like the shift editor; all shifts at once by hours before.
    if (typing && start != null) {
        com.bamnat.app.ui.components.AlarmOffsetDialog(
            title = title,
            start = start,
            initialMinutes = minutes ?: SetupDraft.DEFAULT_ALARM_MINUTES,
            onDismiss = { typing = false },
            onPick = {
                typing = false
                onPick(it)
            },
        )
    } else if (typing) {
        DurationDialog(
            title = "$title · 출근 몇 분 전",
            initialMinutes = minutes ?: SetupDraft.DEFAULT_ALARM_MINUTES,
            maxMinutes = 12 * 60,
            onDismiss = { typing = false },
            onConfirm = {
                typing = false
                onPick(it)
            },
        )
    }
}

/** "06:00에 울려요", "전날 23:30에 울려요", "알람 없음". */
private fun ringLine(t: ShiftType, minutes: Int?): String {
    val start = t.start ?: return "알람 없음"
    if (minutes == null) return "알람 없음"
    val dayBefore = minutes > start.hour * 60 + start.minute
    return (if (dayBefore) "전날 " else "") + "${Fmt.time(start.minusMinutes(minutes.toLong()))}에 울려요"
}

// ---- 8. the summary ----

@Composable
internal fun SummaryStep(vm: SetupViewModel, d: SetupDraft) {
    StepHeader("이대로 저장할까요?", "저장하면 달력이 바로 채워져요. 고칠 곳은 ‘고치기’를 누르세요.")
    val types = remember(d.types) { d.types.associateBy { it.id } }
    SummaryCard("근무 형태", styleTitle(d), onFix = { vm.fix(SetupStep.STYLE) })
    val source = d.source
    if (source != null || d.companyName.isNotBlank()) {
        SummaryCard("회사", companyLine(d, source), onFix = { vm.fix(SetupStep.COMPANY) })
    }
    SummaryCard(
        "근무 시간",
        d.workTypes.joinToString("\n") { t ->
            "${t.name} ${hoursOf(t)}" + if (t.breakMinutes > 0) " · 쉬는 시간 ${HoursFormat.korean(t.breakMinutes)}" else ""
        },
        onFix = { vm.fix(SetupStep.TIMES) },
    )
    when {
        d.usesRotation -> SummaryCard("순서", orderLine(d.rotation, types), onFix = { vm.fix(SetupStep.ORDER) }) {
            BadgeStrip(d.rotation, types, Modifier.padding(top = 6.dp), size = 20.dp)
        }
        d.style == WorkStyle.SHIFT -> SummaryCard(
            "근무표",
            if (d.painted.isEmpty()) "아직 안 칠했어요. 달력에서 언제든 칠할 수 있어요" else "칠한 날 ${d.painted.size}일",
            onFix = { vm.fix(SetupStep.ORDER) },
        )
        d.style == WorkStyle.HOURLY && d.noFixedDays -> SummaryCard("요일", "그때그때 달라요. 일한 날 달력에 적어요", onFix = { vm.fix(SetupStep.ORDER) })
        else -> SummaryCard("요일", weekLine(d) + if (d.holidaysOff) " · 공휴일 쉼" else " · 공휴일에도 일함", onFix = { vm.fix(SetupStep.ORDER) })
    }
    if (d.usesRotation) {
        val date = vm.questionDate(d)
        todaySentence(d, date, vm.today)?.let { SummaryCard("오늘", it, onFix = { vm.fix(SetupStep.TODAY) }) }
    }
    val first = remember(d) { vm.firstAlarm(d) }
    SummaryCard(
        "알람",
        buildString {
            append(alarmLine(d))
            first?.let { a ->
                append("\n첫 알람: ${relativeDay(a.triggerAt.toLocalDate(), vm.today)} ${Fmt.time(a.triggerAt.toLocalTime())}")
                if (a.shiftName.isNotEmpty()) append(" · ${a.shiftName}")
            }
        },
        onFix = { vm.fix(SetupStep.ALARM) },
    )
}

@Composable
private fun SummaryCard(title: String, text: String, onFix: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onFix) { Text("고치기") }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
            extra?.invoke()
        }
    }
}

private fun companyLine(d: SetupDraft, source: SetupSource?): String = buildList {
    // [J133-26] Said once: the name is the company picked, so its line says it already.
    if (d.companyName.isNotBlank() && (source == null || source.kind == SetupSource.Kind.TEMPLATE || source.name.trim() != d.companyName.trim())) add(d.companyName.trim())
    when (source?.kind) {
        SetupSource.Kind.TEMPLATE -> add("${source.name}${Fmt.ro(source.name)} 시작 (업종 일반형)")
        SetupSource.Kind.SERVER -> add("같은 회사 설정: ${source.name}${source.dept.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
        SetupSource.Kind.FILE -> add("동료가 보낸 설정" + source.name.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty())
        SetupSource.Kind.PUBLIC -> add(
            (if (source.id.isNullOrEmpty()) "처음 제공: " else "${com.bamnat.core.plaza.PublicCycles.BADGE}: ") +
                "${source.name} · ${com.bamnat.core.plaza.PublicCycles.formName(source.dept)}",
        )
        null -> Unit
    }
}.joinToString("\n")

/** "월·화·수·목·금 주간" or "월 오전 알바 · 수 저녁 알바". */
private fun weekLine(d: SetupDraft): String {
    val days = java.time.DayOfWeek.entries.mapIndexedNotNull { i, day ->
        d.weekly.getOrNull(i)?.takeIf { !d.isOff(it) }?.let { day to d.type(it)!! }
    }
    if (days.isEmpty()) return "일하는 요일이 없어요"
    val one = days.map { it.second.id }.distinct().size == 1
    return if (one) days.joinToString("·") { Fmt.dayOfWeekShort(it.first) } + " " + days.first().second.name
    else days.joinToString(" · ") { "${Fmt.dayOfWeekShort(it.first)} ${it.second.name}" }
}

private fun alarmLine(d: SetupDraft): String {
    val work = d.workTypes
    if (d.alarmByType.isEmpty()) return d.alarmMinutes?.let { "근무 ${Fmt.offset(it)}에 울려요" } ?: "알람은 안 써요"
    return work.joinToString(" · ") { t -> "${t.name} " + (d.alarmFor(t)?.let { Fmt.offset(it) } ?: "안 씀") }
}

/** [SETUP-01] Once, right after the first setup: the expected pay is offered next, never forced. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupPaySheet(
    companyPay: Boolean,
    onNow: () -> Unit,
    onLater: () -> Unit,
    /** [PAYG-07] The calendar just set up, whose coworkers' setup may carry its pay. */
    calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // [PAYG-07] 1.30 BA (사용자 "회사를 광장에 있는 것으로 선택하면 '급여 계산도 적용되어 있어요, 사용할까요?'를 물어 바로 적용"):
    // the setup came with the company's pay, so the sheet asks to use it and its 기본급 at once; 이대로 계산 puts both in.
    val c = LocalContext.current.appContainer
    val company = remember(calendar) { c.companyOf(calendar) }
    val way = remember(company, companyPay) {
        val text = company.store.pendingPay
        val a = company.store.applied.value
        if (!companyPay || text == null || a == null) null
        else PendingWay(a.companyId, a.company, a.dept, a.setup, text, PendingWay.Source.FIRST_SETUP)
    }
    if (way != null) {
        androidx.compose.runtime.LaunchedEffect(way) { company.pendingWays.offer(way) }
        com.bamnat.app.ui.components.AppSheet(onDismissRequest = onLater, sheetState = state) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PendingDone(c, company, way, onDone = onNow, onSaved = null,
                    intro = "달력을 채웠어요. ‘${way.title}’ 급여 계산도 들어 있어요. 쓸까요?",
                    onSkip = {
                        // Not this company's pay: nothing of it stays to be asked again; the pay is made by hand later.
                        company.store.pendingPay = null
                        company.payWays.use(null)
                        onLater()
                    })
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("나중에") }
            }
        }
        return
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onLater, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Payments, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("달력을 채웠어요. 예상 급여도 볼까요?", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (companyPay) "회사 급여 방식은 함께 받아 왔어요. 내 기본급이나 시급만 넣으면 이번 달 예상 급여를 보여 드려요."
                // [PAYF-05] 1.29 AP: the pay starts from the company (근무주기처럼): its way when coworkers made one, else made by hand.
                else "먼저 우리 회사 급여 방식이 있는지 찾아봐요. 있으면 그대로 쓰고 기본급만 넣으면 돼요. 없으면 몇 가지만 골라 직접 만들어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onNow, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("지금 할게요") }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("나중에") }
        }
    }
}
