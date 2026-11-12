package com.bamnat.app.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ExpandMore
import com.bamnat.app.data.ScheduleSnapshot
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
import com.bamnat.app.ui.components.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
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
    AlarmPart(vm, d)
}

/**
 * [EASE-07] 1.38: the alarm on the last screen, already at 출근 1시간 전, instead of a screen of its own (one tap less). The same
 * choices; [compact] leaves the per-shift lines out until '근무마다 다르게' is on.
 */
@Composable
internal fun AlarmPart(vm: SetupViewModel, d: SetupDraft, compact: Boolean = false) {
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
    if (!compact || perShift) d.workTypes.forEach { t ->
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
        // 1.39 [FLOW-05] The first time it is needed, what it gives is drawn first: the alarm big on the lock screen with its 끄기.
        com.bamnat.app.ui.components.PermissionAskCard(com.bamnat.app.ui.components.PermissionResult.LOCK_ALARM,
            "알림을 허용해 주세요", "허용해야 잠금 화면에서 알람을 끌 수 있어요", action = "허용", onAction = ask)
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

/**
 * [RVG-16] 검토 TYPE-21: started from a 근무표 link, the phone was always 교대 (a 알바 team member got the 교대 pay setup and tabs).
 * One line asks how the user works; the days stay the link's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkWorkAs(vm: SetupViewModel, d: SetupDraft) {
    val shown = com.bamnat.app.ui.flags.typeChoices()
    val now = d.savedStyle
    // The same card as the alarm's below (디자인 품질 조건 5).
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("어떻게 일하세요?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupViewModel.LINK_WORK.filter { shown.shows(it) }.forEach { s ->
                    FilterChip(selected = now == s, onClick = { vm.workAs(s) }, label = { Text(LINK_WORK_WORDS.getValue(s)) })
                }
            }
            Text("날짜는 그대로, 급여와 메뉴만 이 일에 맞춰요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LINK_WORK_WORDS = mapOf(WorkStyle.SHIFT to "교대 근무", WorkStyle.DAY to "평일 근무", WorkStyle.HOURLY to "알바 · 시급")

@Composable
internal fun SummaryStep(vm: SetupViewModel, d: SetupDraft, onNotMine: (() -> Unit)? = null) {
    // 1.38 PC 12.1: filled from a 근무표 link, the names are what is left to check.
    // 1.39 [FLOW-04] The check is the calendar itself: this month as it will be saved, a day tapped goes back to where it is set, and
    // the answers in words fold into one line under it (opened, each has its '고치기').
    val preview = remember(d.types, d.rotation, d.anchor, d.weekly, d.painted, d.leaves, d.holidaysOff) {
        ScheduleSnapshot(d.typesToSave(), d.pattern(vm.today), d.paintedEntries()).takeIf { !it.isEmpty }
    }
    val types = remember(d.types) { d.types.associateBy { it.id } }
    val thisMonth = remember(vm.today) { java.time.YearMonth.from(vm.today) }
    // [RVG-18] 검토 PC-22 (도약 14): a link's roster of next month opens on that month, not on an empty this month.
    var month by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf((if (d.linkTitle.isNotBlank()) d.openingMonth(vm.today).coerceAtMost(thisMonth.plusMonths(2)) else thisMonth).toString())
    }
    val monthPart: @Composable () -> Unit = {
        if (preview != null) SetupMonth(
            month = java.time.YearMonth.parse(month),
            snapshot = preview,
            weekStart = vm.weekStart,
            today = vm.today,
            selected = null,
            onMonth = { month = it.toString() },
            first = thisMonth,
            last = thisMonth.plusMonths(2),
            // An order is fixed where today's place in it is asked; days of the week and a painted roster where they were set.
            onDayClick = { vm.fix(if (d.usesRotation && SetupStep.TODAY in d.steps) SetupStep.TODAY else SetupStep.ORDER) },
        )
    }
    if (d.linkTitle.isNotBlank() && onNotMine != null) {
        // [RVG-19] 검토 HQ-09 (조건 10): '내 근무표예요' pressed by mistake had no way back (← only went to earlier questions). The other
        // answer sits right under the question it answers, one press. It takes the place of the line saying the names, times and days
        // were filled in (the month and '근무 이름' under it show them), and the month comes 4dp under it (the button's own room is
        // round its words), so the month still starts in the screen's top quarter on a folded phone (조건 1).
        Column(Modifier.fillMaxWidth()) {
            Text("‘${d.linkTitle}’ 근무표를 내 근무로 시작할까요?", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            // Its words line up with the title's (a text button's own side room is 12dp).
            TextButton(onClick = onNotMine, modifier = Modifier.offset(x = (-12).dp)) { Text("가족·동료 근무표였어요") }
            if (preview != null) {
                Spacer(Modifier.height(4.dp))
                monthPart()
            }
        }
    } else {
        if (d.linkTitle.isNotBlank()) {
            // [RVG-15] 검토 TYPE-04: said plainly that this is the user's own 근무표 (the first screen asked whose it is).
            // 디자인 품질 조건 1·4: one line under the title, so the month starts within the screen's top quarter.
            StepHeader("‘${d.linkTitle}’ 근무표를 내 근무로 시작할까요?", "근무 이름과 시간, 날짜를 그대로 넣었어요.")
        } else StepHeader("이대로 저장할까요?",
            if (preview != null) "저장하면 달력이 이렇게 채워져요. 다른 날이 있으면 그 칸을 눌러 바로 고쳐요." else "저장하면 달력이 바로 채워져요. 고칠 곳은 ‘고치기’를 누르세요.")
        monthPart()
    }
    // [RVG-16] Under the month (디자인 품질 조건 1: the month first): how the user works, the days staying the link's.
    if (d.linkTitle.isNotBlank()) LinkWorkAs(vm, d)
    if (d.linkTitle.isNotBlank()) SummaryCard("근무 이름", d.types.filter { !it.isOff }.joinToString(", ") { if (it.label != it.name) "${it.name}(${it.label})" else it.name },
        onFix = { vm.fix(SetupStep.TIMES) })
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(preview == null) }
    if (!open) {
        SummaryFold(foldLine(d, preview != null)) { open = true }
    } else {
    if (d.linkTitle.isBlank()) SummaryCard("근무 형태", styleTitle(d), onFix = { vm.fix(SetupStep.STYLE) })
    val source = d.source
    if ((source != null || d.companyName.isNotBlank()) && SetupStep.COMPANY in d.steps) {
        // 1.38 통합 7차 347b7c37 (직접 만든 순서에도 '3조 2교대로 시작'): an order of the user's own is said so.
        val own = remember(d.source, d.rotation) { d.rotation.isNotEmpty() && vm.sourceOrder(d)?.let { it != d.rotation } == true }
        // 1.38 통합 7차 (회사를 고르지 않았는데 '병원 간호 3교대로 시작'): with no company the card is the order's own.
        val noCompany = d.companyName.isBlank() && source?.kind == SetupSource.Kind.TEMPLATE
        SummaryCard(if (noCompany) "근무 순서" else "회사", companyLine(d, source, own), onFix = { vm.fix(SetupStep.COMPANY) })
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
    }
    // 1.38 통합 11차 (알바 월수금 13~18시: 쉬는 시간 30분이 붙어 주 13시간 30분 → 주휴 없음, 요약에는 작게만): the 15-hour line
    // of 주휴수당 is said plainly when the week's hours make or miss it, and when the break alone decides it. 1.39: never folded away.
    weeklyHolidayNote(d)?.let { SummaryCard("주휴수당", it, onFix = { vm.fix(SetupStep.TIMES) }) }
    // [EASE-07] 1.38: the alarm is chosen here (already 출근 1시간 전), not on a screen of its own.
    val first = remember(d) { vm.firstAlarm(d) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("근무 알람 · 출근 몇 분 전에 깨울까요?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AlarmPart(vm, d, compact = true)
            Text(
                buildString {
                    append(alarmLine(d))
                    first?.let { a ->
                        append(" · 첫 알람 ${relativeDay(a.triggerAt.toLocalDate(), vm.today)} ${Fmt.time(a.triggerAt.toLocalTime())}")
                        if (a.shiftName.isNotEmpty()) append(" ${a.shiftName}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    // [EASE-03] 1.38 PC 994c2328: sharing a public cycle with the same company is one box here, never ticked beforehand,
    // instead of a window that came up over the new calendar after 저장.
    d.source?.takeIf { it.kind == SetupSource.Kind.PUBLIC }?.let { s ->
        val c = LocalContext.current.appContainer
        if (remember(s.name) { c.leadShare.canAsk(s.name) }) {
            val on by vm.shareCycle.collectAsStateWithLifecycle()
            ShareCycleLine(s.name, on, upload = s.id.isNullOrEmpty()) { vm.shareCycle.value = it }
        }
    }
    // 1.39 사용자 2026-11-08 (잠도 첫 설정에서 '이런 것도 돼요'로, 아래 메뉴 '잠' 한 번 권하기). 8차 PC 1-24: a new install ends its
    // first setup here, so the offer is here (the 추천 page has it only for a phone updated mid-way).
    if (vm.offersSleepBar) {
        val app = LocalContext.current.appContainer
        val settings = remember { app.settingsRepository.current }
        val sleepKey = com.bamnat.core.layout.TabSlots.FEATURE + com.bamnat.core.guide.Place.SLEEP.name
        val bar = remember(settings) { com.bamnat.core.layout.TabSlots.resolve(settings.tabSlots, settings.tabOrder, com.bamnat.app.ui.TabPicks.known) }
        val swaps = remember(bar) { com.bamnat.core.layout.TabSlots.swapChoices(bar, sleepKey) }
        val soundOn = remember { runCatching { app.flags.on(com.bamnat.core.flags.FlagCatalog.SLEEP_SOUND) }.getOrDefault(false) }
        val chosen by vm.sleepSlot.collectAsStateWithLifecycle()
        com.bamnat.app.ui.onboarding.SleepBarOffer(bar, swaps, d.style, chosen, soundOn) { vm.sleepSlot.value = it }
    }
    // [EASE-05][ONBOARD-05][OBS2-06] 사용 통계 묻기는 1.39 7차 PC 1-12부터 아래 막대, 저장 단추 바로 위에 있어요(SetupHost).
}

/** 1.39 [FLOW-04] The answers in words, folded to one line under the month: "교대 근무 · 4조 2교대 · 주간 07:00~19:00 외 1 ▾". */
@Composable
private fun SummaryFold(text: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClickLabel = "고른 답 모두 보기", onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("고른 답 보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** 1.39 [FLOW-04] The folded line: the kind of work, where the order came from and the first shift's hours. */
internal fun foldLine(d: SetupDraft, withMonth: Boolean): String = buildList {
    // [RVG-16] A setup from a link says the kind of work its summary was told.
    add(d.workAs?.let { LINK_WORK_WORDS[it] } ?: styleTitle(d).substringBefore(" · "))
    d.source?.name?.takeIf { it.isNotBlank() }?.let(::add) ?: d.companyName.takeIf { it.isNotBlank() }?.let(::add)
    val work = d.workTypes
    work.firstOrNull()?.let { t -> add("${t.name} ${hoursOf(t)}" + if (work.size > 1) " 외 ${work.size - 1}" else "") }
    if (!withMonth && d.style == WorkStyle.SHIFT && !d.usesRotation) add(if (d.painted.isEmpty()) "근무표는 나중에" else "칠한 날 ${d.painted.size}일")
}.joinToString(" · ")

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

private fun companyLine(d: SetupDraft, source: SetupSource?, ownOrder: Boolean = false): String = buildList {
    // [J133-26] Said once: the name is the company picked, so its line says it already.
    if (d.companyName.isNotBlank() && (source == null || source.kind == SetupSource.Kind.TEMPLATE || source.name.trim() != d.companyName.trim())) add(d.companyName.trim())
    when (source?.kind) {
        SetupSource.Kind.TEMPLATE -> add(when {
            ownOrder -> "내가 만든 순서 (근무 시간은 ${source.name} 업종 일반형)"
            d.companyName.isBlank() -> "${source.name} (업종 일반형)"
            else -> "${source.name}${Fmt.ro(source.name)} 시작 (업종 일반형)"
        })
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
/**
 * 시급 알바의 주휴수당 (근로기준법 제55조·제18조 제3항, 1주 소정근로 15시간 이상): the week's hours from the days set, said against the
 * 15 hours, and the break named when it alone takes the week under. Null for other kinds of work or no fixed days.
 */
internal fun weeklyHolidayNote(d: SetupDraft): String? {
    if (d.style != WorkStyle.HOURLY || d.noFixedDays) return null
    val days = d.weekly.mapNotNull { id -> id?.takeIf { !d.isOff(it) }?.let(d::type) }
    if (days.isEmpty()) return null
    val work = days.sumOf { it.workMinutes }
    val withBreaks = days.sumOf { it.workMinutes + it.breakMinutes }
    val line = 15 * 60
    val hours = HoursFormat.korean(work)
    return when {
        work >= line -> "주 $hours 일해요 · 주 15시간 이상이라 주휴수당을 받아요"
        withBreaks >= line -> "쉬는 시간을 빼면 주 ${hours}이라 주휴수당 기준(주 15시간)에 ${HoursFormat.korean(line - work)} 모자라요. " +
            "쉬는 시간 없이 일하면 ‘고치기’에서 쉬는 시간을 0분으로 바꿔 주세요"
        else -> "주 ${hours}이라 주휴수당 기준(주 15시간)에 ${HoursFormat.korean(line - work)} 모자라요"
    }
}

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
                    intro = "설정을 마쳤어요. ‘${way.title}’ 급여 계산도 들어 있어요. 쓸까요?",
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
            Text("설정을 마쳤어요. 예상 급여도 볼까요?", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (companyPay) "회사 급여 방식은 함께 받아 왔어요. 내 기본급이나 시급만 넣으면 이번 달 예상 급여를 보여 드려요."
                // [J133-71] 1.33: 알바 looks for no company; the 시급 is all it takes.
                else if (c.scopeOf(calendar).currentSettings().workStyle?.usesCompany == false) "시급만 넣으면 주휴수당까지 넣어 이번 달 예상 급여를 보여 드려요."
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

/**
 * [EASE-03] 1.38: the company's pay that came with the first setup (PAYG-07), offered where the pay is set up, on the pay tab the
 * first time, instead of a sheet over the first calendar. Nothing when there is none. '쓰지 않을게요' forgets it.
 */
@Composable
fun CompanyPayWaiting(calendar: String, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val company = remember(calendar) { c.companyOf(calendar) }
    var gone by remember { mutableStateOf(false) }
    val way = remember(company, gone) {
        val text = company.store.pendingPay
        val a = company.store.applied.value
        if (gone || text == null || a == null) null else PendingWay(a.companyId, a.company, a.dept, a.setup, text, PendingWay.Source.FIRST_SETUP)
    } ?: return
    androidx.compose.runtime.LaunchedEffect(way) { company.pendingWays.offer(way) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PendingDone(c, company, way, onDone = { gone = true }, onSaved = null,
                intro = "근무 설정에서 고른 ‘${way.title}’의 급여 계산도 들어 있어요. 쓸까요?",
                onSkip = {
                    company.store.pendingPay = null
                    company.payWays.use(null)
                    gone = true
                })
        }
    }
}

/** [EASE-03] The summary's one box for sharing the picked public cycle with others at [company]. */
@Composable
private fun ShareCycleLine(company: String, on: Boolean, upload: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(if (upload) "‘$company’ 근무표 올리기 · 같은 회사 분이 바로 써요" else "‘$company’ 다른 분들도 이 근무주기를 쓰게 나누기 (선택)",
                style = MaterialTheme.typography.bodyLarge)
            if (upload) Text("체크하면 저장할 때 올라가요. 공개 근무주기 목록에는 검토 뒤(최대 7일)에 올라가요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("근무 이름·시간·순서만 나눠요. 급여 금액, 이름, 연락처, 사진은 보내지 않아요. 설정 › 개인정보·앱 정보에서 바꿔요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
