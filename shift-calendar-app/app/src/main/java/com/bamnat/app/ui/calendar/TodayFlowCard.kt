package com.bamnat.app.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.WorkOutline
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import com.bamnat.app.ui.components.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.sleep.TodayFlow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import com.bamnat.core.time.Today
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.app.ui.theme.container

/** The clock to the minute, moving on at each minute's turn, for what changes with the time of day. */
@Composable
internal fun rememberMinute(): LocalDateTime {
    val now by produceState(Today.dateTime().withSecond(0).withNano(0)) {
        while (true) {
            val t = Today.dateTime()
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000))
            value = Today.dateTime().withSecond(0).withNano(0)
        }
    }
    return now
}

/**
 * [SLP-14] 오늘의 흐름 at the top of the calendar, worked out from the schedule, the alarms that will
 * ring and the sleep log, again each minute.
 */
@Composable
internal fun TodayFlowSection(vm: CalendarViewModel, snapshot: ScheduleSnapshot, settings: AppSettings, modifier: Modifier = Modifier) {
    val log by vm.sleepLog.collectAsStateWithLifecycle()
    val chosen by vm.flowFolded.collectAsStateWithLifecycle()
    // [CAL-21] On a common phone (under about 860dp tall) the open card pushes the month below the screen.
    // [VIS-04] At 150% text and more the card is as tall as on a small phone.
    val short = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp < SHORT_SCREEN_DP && it.screenWidthDp < 600 } ||
        androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    // 1.38 통합 12차: in the month-first line (no order of the user's own, [CALENDAR_FIRST]) it is over the month as one line until
    // opened, so the five weeks stay in view on any phone.
    val monthFirst = remember(settings.calendarLayout) { com.bamnat.core.layout.PageLayout.decode(settings.calendarLayout).order.isEmpty() }
    val openNow by vm.flowOpenNow.collectAsStateWithLifecycle()
    val folded = !openNow && (chosen ?: (short || monthFirst))
    val now = rememberMinute()
    val card = remember(snapshot, settings, log, now) { vm.todayFlow(snapshot, settings, log, now) }
    // 1.39 [FLOW-28] 하루 다이얼: the same day as a 24-hour ring (work, planned and real sleep, alarms, now), off in 설정 › 화면과 글자.
    val calendarAlarms by vm.calendarAlarmsOn.collectAsStateWithLifecycle()
    val dial = remember(snapshot, settings, log, now, calendarAlarms) {
        if (!settings.dayDial) null else runCatching {
            val today = now.toLocalDate()
            val alarms = if (!settings.alarmsEnabled || !calendarAlarms) emptyList()
                else listOf(today, today.plusDays(1)).flatMap { com.bamnat.core.alarm.AlarmPlanner.alarmsOn(snapshot.resolver, it) }
            val items = listOf(today, today.plusDays(1)).flatMap { d ->
                com.bamnat.core.design.DayStoryItems.of(d, snapshot.resolver,
                    sleeps = log.filter { it.start.toLocalDate() in today.minusDays(1)..today.plusDays(1) },
                    plans = if (settings.sleepTips) vm.sleepTips(snapshot, d, settings) else emptyList())
            }.distinctBy { it.id } + alarms.distinct().map { a ->
                com.bamnat.core.design.StoryItem(a.triggerAt, null, com.bamnat.core.design.StoryKind.Alarm, "알람", id = "alarm@${a.triggerAt}")
            }
            val d = com.bamnat.core.design.DayDialArcs.of(items, now) { i ->
                snapshot.resolver.resolve(i.start.toLocalDate()).shiftType?.colorArgb
            }
            d.takeIf { !it.isEmpty }?.let { it to com.bamnat.core.design.DayDialArcs.says(items, it) }
        }.getOrNull()
    }
    TodayFlowCard(
        dial = dial,
        card = card,
        targetMinutes = settings.sleepTargetMinutes,
        commuteMinutes = settings.commuteMinutes,
        asleepSince = log.firstOrNull { it.end == null && !it.imported }?.start,
        onAction = { action, date ->
            when (action) {
                // 1.39 [FLOW-13] and the dark night screen (clock, tomorrow's alarm, 잠드는 소리, 깼어요).
                TodayFlow.Action.ASLEEP -> { vm.fellAsleep(); com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.NIGHT) }
                TodayFlow.Action.AWAKE -> vm.wokeUp()
                TodayFlow.Action.ALARM_ON -> date?.let { vm.setAlarmSkipped(it, false, announce = true) }
                TodayFlow.Action.AWAKE_AT -> Unit // asks for the time first, below
                // 1.38 [SLEEP-23] 낮잠 자기: asleep now, and a wake alarm at the nap's end.
                TodayFlow.Action.NAP -> card.napUntil?.let(vm::napNow) ?: vm.fellAsleep()
            }
        },
        onWokeAt = { vm.wokeUp(it) },
        onNumbers = vm::setSleepNumbers,
        onHide = vm::hideTodayFlow,
        modifier = modifier,
        folded = folded,
        onFold = vm::setFlowFolded,
        openNow = openNow,
        // 1.38 사용자 2026-11-06: 밤샘·첫 야간 rules only where there are nights to work.
        nights = nightWork(settings.workStyle, snapshot),
        // 1.39 사용자 2026-11-08 (수면 기록이 찾기 어려워요): the card that says when to sleep opens the sleep log too, as the
        // feature list has long said (달력 › 오늘의 흐름 › 수면 기록).
        onSleepLog = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.SLEEP) },
    )
}

/**
 * 1.38 사용자 2026-11-06 (교대가 없는 유형에 주·야가 보임): whether this calendar works nights, for the words about 야간: 교대 (or not chosen
 * yet), or any kind it uses that runs past midnight (a 자영업 who made a 야간 영업 of their own).
 */
internal fun nightWork(style: com.bamnat.core.work.WorkStyle?, snapshot: ScheduleSnapshot): Boolean =
    style == null || style == com.bamnat.core.work.WorkStyle.SHIFT || run {
        val used = snapshot.patterns.flatMap { p ->
            when (p) {
                is com.bamnat.core.model.WorkPattern.Rotation -> p.steps
                is com.bamnat.core.model.WorkPattern.Weekly -> p.steps
            }
        }.filterNotNull().toSet()
        snapshot.shiftTypes.any { t ->
            val start = t.start
            val end = t.end
            t.id in used && !t.isOff && start != null && end != null && !end.isAfter(start)
        }
    }

/** [CAL-21] Shorter than this, 오늘의 흐름 starts as one line; a fold's cover screen (905dp) is not. */
private const val SHORT_SCREEN_DP = 860

/**
 * [SLP-14] The card: one thing to do now in a big line and why in a small one (ⓘ beside them), a rest
 * sign when there is one, the next 24 hours as a strip, and the one button that fits. The day's work
 * is on the strip, so it has no title line of its own; TalkBack reads it with the strip. A day off
 * folds to one line that opens on a tap. [CAL-16] Any day folds to that line with ⌃, and stays folded
 * until opened again, so a small screen keeps room for the month.
 */
@Composable
internal fun TodayFlowCard(
    card: TodayFlow.Card,
    targetMinutes: Int,
    commuteMinutes: Int,
    asleepSince: LocalDateTime?,
    onAction: (TodayFlow.Action, LocalDate?) -> Unit,
    onWokeAt: (LocalDateTime) -> Unit,
    onNumbers: (target: Int, commute: Int) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    folded: Boolean = false,
    onFold: (Boolean) -> Unit = {},
    nights: Boolean = true,
    /** 1.39 [FLOW-28] The day dial and what a screen reader hears of it; null when it is off or has nothing to show. */
    dial: Pair<com.bamnat.core.design.DayDialArcs.Dial, String>? = null,
    /** 1.39 The way to 수면 기록 (근무별 평균, 아침 한 장, 기록 고치기) from the opened card; null shows no link. */
    onSleepLog: (() -> Unit)? = null,
    /** 2.0.0 [RVI-68] Opened by a today link: a day off's one line opens too. */
    openNow: Boolean = false,
) {
    var info by remember { mutableStateOf(false) }
    var askWake by remember { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(false) }
    val cal = LocalCalendarColors.current
    val warning = card.state == TodayFlow.State.ALARM_OFF || card.state == TodayFlow.State.FORGOT_WAKE
    // 1.38 [FLOW-07] The same one card wears the time of day: work, the night's sleep and a day off each have their own
    // colour, so the calendar's top tells which part of the day this is before a word is read (no card is added, EASE-03).
    val face = when {
        // 2.0.0 [RVP-13] (HQ-54) An alarm that will not ring is the one danger here.
        warning -> Tone.Danger.container()
        card.state == TodayFlow.State.ON_SHIFT -> MaterialTheme.colorScheme.primaryContainer
        card.state == TodayFlow.State.SLEEPING || card.state == TodayFlow.State.SLEEP_TIME -> cal.sleepContainer
        card.state == TodayFlow.State.REST_DAY -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val countdown = remember(card) { TodayFlow.countdown(card) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = face,
        contentColor = when (face) {
            MaterialTheme.colorScheme.primaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer
            cal.sleepContainer -> cal.onSleepContainer
            MaterialTheme.colorScheme.secondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        // 1.36 첫 실행 멈춤 (PC 확인 C12, TodayFlowCard animateContentSize): no size animation while the first month fills the
        // screen; folding and unfolding afterwards still move smoothly.
        modifier = modifier.fillMaxWidth().then(if (FirstMonth.settled.value) Modifier.animateContentSize() else Modifier),
    ) {
        if (folded || card.compact && !opened && !openNow) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(onClickLabel = "오늘의 흐름 펼치기") { if (folded) onFold(false); opened = true }
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(stateIcon(card), null, tint = if (warning) Tone.Danger.color() else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(card.headline, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // [CAL-16] A warning keeps its one button on the folded line: an alarm is turned back on without opening.
                if (warning) card.action?.let { a ->
                    ActionButton(a, Modifier.padding(start = 8.dp)) { onPick(a, card, onAction) { askWake = true } }
                }
                Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
            return@Surface
        }
        // What to do now leads; the strip under it shows the day around it, and the one button comes last.
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 12.dp)) {
            // The buttons sit beside the headline only; the line under it runs the full width.
            // 1.39 [FLOW-28] The dial stands at the head's left on a phone at normal text; at 150% and more the words need the width.
            val showDial = dial != null && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.5f
            Box {
                if (showDial) com.bamnat.app.ui.components.DayDial(dial!!.first, dial.second, Modifier.padding(top = 12.dp), size = 60.dp)
                Column(Modifier.padding(top = 12.dp, end = 12.dp, start = if (showDial) 68.dp else 0.dp)) {
                    // 1.38 [FLOW-06] One number first: "출근까지 2시간 10분", the words of what to do under it.
                    countdown?.let { cd ->
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.6f.em, fontWeight = FontWeight.Medium)) { append(cd.label + " ") }
                            append(com.bamnat.app.ui.components.numberWithUnit(cd.time))
                        }, style = MaterialTheme.typography.headlineSmall.merge(com.bamnat.app.ui.theme.NumberStyle).copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 32.dp, end = 84.dp, bottom = 2.dp))
                    }
                    Row {
                        Icon(stateIcon(card), null, tint = if (warning) Tone.Danger.color() else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp, end = 10.dp).size(22.dp))
                        Text(card.headline, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(end = 84.dp))
                    }
                    card.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp))
                    }
                }
                Row(Modifier.align(Alignment.TopEnd)) {
                    // [CAL-16] Folds to one line on every day, and stays so.
                    IconButton(onClick = { opened = false; onFold(true) }) { Icon(Icons.Filled.ExpandLess, contentDescription = "오늘의 흐름 접기") }
                    IconButton(onClick = { info = true }) { Icon(Icons.Outlined.Info, contentDescription = "오늘의 흐름 안내와 목표 수면") }
                }
            }
            card.rest?.let { rest ->
                // 2.0.0 [RVP-13] (HQ-54) Short rest is a thing to mind (주의), said in the one alert line.
                com.bamnat.app.ui.components.ToneLine(rest, Modifier.padding(start = 32.dp, top = 4.dp, end = 12.dp))
            }
            FlowStrip(card, Modifier.padding(top = 12.dp, end = 12.dp))
            if (card.action != null || onSleepLog != null) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 1.39 사용자 2026-11-08: 수면 기록 one tap from the card looked at every day, beside its one button.
                    if (onSleepLog != null) TextButton(onClick = onSleepLog, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                        Icon(Icons.Outlined.Bedtime, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("수면 기록", maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    card.action?.let { a -> ActionButton(a) { onPick(a, card, onAction) { askWake = true } } }
                }
            }
        }
    }

    if (info) FlowInfoSheet(targetMinutes, commuteMinutes, onNumbers, onHide = { info = false; onHide() }, onDismiss = { info = false }, nights = nights)
    if (askWake && asleepSince != null) {
        WakeTimeDialog(asleepSince, card.from, targetMinutes, onDismiss = { askWake = false }) {
            askWake = false
            onWokeAt(it)
        }
    }
}

private fun onPick(action: TodayFlow.Action, card: TodayFlow.Card, onAction: (TodayFlow.Action, LocalDate?) -> Unit, askWake: () -> Unit) {
    if (action == TodayFlow.Action.AWAKE_AT) askWake() else onAction(action, card.actionDate)
}

@Composable
private fun ActionButton(action: TodayFlow.Action, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (label, icon) = when (action) {
        TodayFlow.Action.ASLEEP -> "잠들었어요" to Icons.Outlined.Bedtime
        TodayFlow.Action.AWAKE -> "깼어요" to Icons.Outlined.WbSunny
        TodayFlow.Action.AWAKE_AT -> "깬 시각 적기" to Icons.Outlined.Schedule
        TodayFlow.Action.ALARM_ON -> "알람 켜기" to Icons.Outlined.Alarm
        TodayFlow.Action.NAP -> "낮잠 자기" to Icons.Outlined.Bedtime
    }
    val content: @Composable () -> Unit = {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
    if (action == TodayFlow.Action.ASLEEP || action == TodayFlow.Action.NAP) {
        val cal = LocalCalendarColors.current
        FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = cal.sleepContainer, contentColor = cal.onSleepContainer)) { content() }
    } else {
        Button(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) { content() }
    }
}

private fun stateIcon(card: TodayFlow.Card): ImageVector = when (card.state) {
    TodayFlow.State.ALARM_OFF -> Icons.Outlined.AlarmOff
    TodayFlow.State.SLEEPING -> Icons.Outlined.NightsStay
    TodayFlow.State.FORGOT_WAKE -> Icons.Outlined.Schedule
    TodayFlow.State.ON_SHIFT -> if (card.headline.endsWith("커피")) Icons.Outlined.LocalCafe else Icons.Outlined.WorkOutline
    TodayFlow.State.AFTER_SHIFT -> Icons.Outlined.DirectionsCar
    TodayFlow.State.SLEEP_TIME -> Icons.Outlined.Bedtime
    TodayFlow.State.NAP -> Icons.Outlined.Snooze
    TodayFlow.State.AWAKE -> Icons.Outlined.WbSunny
    TodayFlow.State.REST_DAY -> Icons.Outlined.Weekend
    TodayFlow.State.NEXT -> Icons.Outlined.Event
}

/**
 * [SLP-14] The next 24 hours from now: work in its shift's colour, sleep and naps in night blues,
 * alarms as ticks, with the times under it where they fit. TalkBack reads it as one sentence.
 */
@Composable
private fun FlowStrip(card: TodayFlow.Card, modifier: Modifier = Modifier) {
    val cal = LocalCalendarColors.current
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.primary
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val inside = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val under = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
    val total = Duration.between(card.from, card.to).toMinutes().toFloat().coerceAtLeast(1f)
    val said = remember(card) { stripText(card) }
    Canvas(modifier.fillMaxWidth().height(42.dp).semantics { contentDescription = said }) {
        val bar = 22.dp.toPx()
        val radius = CornerRadius(6.dp.toPx())
        fun x(t: LocalDateTime) = (Duration.between(card.from, t).toMinutes() / total * size.width).coerceIn(0f, size.width)
        drawRoundRect(track, Offset.Zero, Size(size.width, bar), radius)
        for (s in card.segments) {
            val color = when (s.part) {
                TodayFlow.Part.WORK -> s.colorArgb?.asColor() ?: tick
                TodayFlow.Part.SLEEP -> cal.sleep
                TodayFlow.Part.NAP -> cal.nap
            }
            val left = x(s.from)
            val width = (x(s.to) - left).coerceAtLeast(2.dp.toPx())
            drawRoundRect(color, Offset(left, 0f), Size(width, bar), radius)
            val text = measurer.measure(s.name, inside.copy(color = contentColorOn(color)), maxLines = 1)
            if (text.size.width + 8.dp.toPx() <= width) {
                drawText(text, topLeft = Offset(left + (width - text.size.width) / 2, (bar - text.size.height) / 2))
            }
        }
        for (a in card.alarms) {
            val ax = x(a)
            drawRect(tick, Offset(ax - 1.dp.toPx(), -2.dp.toPx()), Size(2.dp.toPx(), bar + 4.dp.toPx()))
        }
        // Times under the strip, most useful first. A time may slide sideways while it still sits under
        // its own point, so one close to "지금" or to an alarm moves over rather than dropping out.
        val marks = buildList {
            add(Mark(0f, "지금", label, NOW))
            card.alarms.forEach { add(Mark(x(it), hm(it), tick, 3)) }
            card.segments.forEach { s ->
                if (s.from.isAfter(card.from)) add(Mark(x(s.from), hm(s.from), label, 2))
                if (s.to.isBefore(card.to)) add(Mark(x(s.to), hm(s.to), label, 1))
            }
        }
        val gap = 6.dp.toPx()
        val placed = mutableListOf<ClosedFloatingPointRange<Float>>()
        for (m in marks.sortedByDescending { it.rank }) {
            val text = measurer.measure(m.text, under.copy(color = m.color), maxLines = 1)
            val w = text.size.width.toFloat()
            val lo = if (m.rank == NOW) 0f else (m.x - w).coerceIn(0f, size.width - w)
            val hi = if (m.rank == NOW) 0f else m.x.coerceIn(0f, size.width - w)
            val want = (m.x - w / 2).coerceIn(lo, hi)
            fun free(left: Float) = placed.none { left < it.endInclusive + gap - 0.5f && it.start < left + w + gap - 0.5f }
            val left = (listOf(want) + placed.flatMap { listOf(it.endInclusive + gap, it.start - gap - w) })
                .filter { it in lo..hi && free(it) }
                .minByOrNull { abs(it - want) } ?: continue
            // Twice the room after "지금", so a time beside it does not read as the time now.
            placed += left..(left + w + if (m.rank == NOW) gap else 0f)
            drawText(text, topLeft = Offset(left, bar + 4.dp.toPx()))
        }
    }
}

private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

/** A time under the strip; higher [rank] is placed first. */
private data class Mark(val x: Float, val text: String, val color: Color, val rank: Int)

/** The rank of "지금", which always sits at the left end. */
private const val NOW = 4

/** What the strip shows, read out: "오늘 · 야간 19:00–07:00. 앞으로 24시간: 15:00부터 16:30까지 낮잠, 17:30 알람, …". */
private fun stripText(card: TodayFlow.Card): String {
    val parts = (card.segments.map { it.from to "${hm(it.from)}부터 ${hm(it.to)}까지 ${it.name}" } + card.alarms.map { it to "${hm(it)} 알람" })
        .sortedBy { it.first }.map { it.second }
    return "${card.caption}. " + if (parts.isEmpty()) "앞으로 24시간 동안 정해진 일정이 없어요" else "앞으로 24시간: " + parts.joinToString(", ")
}

/**
 * [SLP-14] ⓘ: how the times are worked out and how sure each rule is, with the two numbers they rest on
 * (목표 수면, 퇴근 후 잠들기까지), and a way to take the card off the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowInfoSheet(target: Int, commute: Int, onNumbers: (Int, Int) -> Unit, onHide: () -> Unit, onDismiss: () -> Unit, nights: Boolean = true) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text("오늘의 흐름은 이렇게 정해요", style = MaterialTheme.typography.titleLarge)
            Text("근무표와 실제로 울릴 알람으로 앞으로 24시간을 계산한 참고 정보예요. 진단이나 치료가 아니니 몸 상태와 회사 사정에 맞게 조절하세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            // [UX-08] The app's one stepper.
            com.bamnat.app.ui.components.StepperRow("목표 수면") {
                com.bamnat.app.ui.components.CountStepper(target, { onNumbers(it, commute) }, name = "목표 수면", range = MIN_TARGET..MAX_TARGET,
                    step = 30, format = HoursFormat::korean, valueWidth = 104.dp)
            }
            com.bamnat.app.ui.components.StepperRow("퇴근 후 잠들기까지", hint = "집에 가고 씻는 시간") {
                com.bamnat.app.ui.components.CountStepper(commute, { onNumbers(target, it) }, name = "퇴근 후 잠들기까지", range = 0..MAX_COMMUTE,
                    step = 10, format = { if (it == 0) "바로" else HoursFormat.korean(it) }, valueWidth = 104.dp)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            if (nights) Rule("밤새 일한 뒤", "집에 오자마자 목표만큼, 적어도 6시간 이어서 자요. 다음 근무 알람이 먼저 울리면 거기까지예요.", "안전보건 지침")
            Rule("이른 근무 전날", "알람에서 목표 수면만큼 거꾸로 잠자리 시각을 정해요. 알람이 없으면 근무 1시간 30분 전에 일어나는 걸로 봐요.", "일반 권고")
            if (nights) Rule("첫 야간 전", "근무 2시간 30분 전에 끝나는 90분 안쪽 낮잠이에요.", "일반 권고")
            Rule("커피", "잠들기 6시간 전까지만 마셔요.", "근거 수준 낮음")
            Rule("휴식", "휴식 점검을 켜 두면 근무 사이가 11시간보다 짧을 때 작은 줄로 알려 줘요.", null)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("설정 > 알림과 잠에서 다시 켤 수 있어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onHide) { Text("달력에서 숨기기") }
            }
        }
    }
}

@Composable
private fun Rule(title: String, text: String, basis: String?) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(text + (basis?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [SLP-14] When the user woke, for a sleep left open: a time after it began, by now at the latest. */
@Composable
internal fun WakeTimeDialog(since: LocalDateTime, now: LocalDateTime, targetMinutes: Int, onDismiss: () -> Unit, onConfirm: (LocalDateTime) -> Unit) {
    val guess = minOf(since.plusMinutes(targetMinutes.toLong()), now)
    TimePickerDialog(
        title = "${since.monthValue}/${since.dayOfMonth} ${hm(since)}에 잠들고 언제 깼나요?",
        initial = guess.toLocalTime(),
        presets = listOf(6L, 7L, 8L, 9L).map { since.plusHours(it) }.filter { !it.isAfter(now) }.map { it.toLocalTime() },
        onDismiss = onDismiss,
    ) { time ->
        var at = since.toLocalDate().atTime(time)
        if (!at.isAfter(since)) at = at.plusDays(1)
        onConfirm(minOf(at, now))
    }
}

private const val MIN_TARGET = 4 * 60
private const val MAX_TARGET = 10 * 60
private const val MAX_COMMUTE = 180
