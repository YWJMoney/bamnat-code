package com.bamnat.app.ui.calendar

import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import com.bamnat.core.schedule.QuickOrder
import com.bamnat.app.data.rules
import com.bamnat.app.appContainer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.LeaveMark
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.alarm.PlannedAlarm
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.holiday.KoreanLunar
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.schedule.DaySource
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ResolvedExtra
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.CalendarFocus
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import java.time.LocalDate

/** The day's alarms as they ring with its switch on, so a day turned off still shows what it would ring. */
internal fun plannedAlarms(snapshot: ScheduleSnapshot, day: ResolvedDay): List<PlannedAlarm> =
    AlarmPlanner.alarmsOn(snapshot.resolver, day.date).ifEmpty {
        if (!day.alarmSkipped) emptyList()
        else AlarmPlanner.alarmsOn(
            ScheduleResolver(snapshot.shiftTypes, snapshot.patterns,
                snapshot.entries.map { if (it.date == day.date) it.copy(alarmSkipped = false) else it }, snapshot.kinds),
            day.date,
        )
    }

/** "07:00" or "전날 22:30" for an alarm that rings the evening before. */
internal fun whenText(alarm: PlannedAlarm, date: LocalDate): String =
    (if (alarm.triggerAt.toLocalDate().isBefore(date)) "전날 " else "") + Fmt.time(alarm.triggerAt.toLocalTime())

/** [DAY-01] One fact about the day in the header: 알람 끔, 직접 바꿈, 메모 2 … */
private data class DayToken(val text: String, val tone: Tone, val said: String? = null, val struck: Boolean = false) {
    enum class Tone { PLAIN, ACCENT, ERROR }
}

/** "주간 · 09:00 – 18:00 · 8시간", "야간 · 연차 (11시간)", "쉬는 날". */
internal fun shiftLine(day: ResolvedDay): String {
    val type = day.shiftType
    val start = day.start
    val end = day.end
    val leave = day.leave
    return when {
        leave?.isFullDay == true -> "${type?.name ?: "근무"} · ${leave.label} (${HoursFormat.korean(day.leaveMinutes)})"
        day.isWorkDay && start != null && end != null ->
            "${type?.name ?: "근무"} · ${Fmt.timeRange(start, end)} · ${HoursFormat.korean(day.scheduledMinutes)}" +
                (leave?.let { " · ${it.label}" } ?: "")
        type != null -> type.name
        else -> "쉬는 날"
    }
}

/** "야간 대근 · 19:00 – 07:00 (다음날) · 11시간", "잔업 · 근무 뒤 2시간 (19–21)". */
internal fun extraLine(e: ResolvedExtra, style: com.bamnat.core.work.WorkStyle? = null): String {
    val from = e.from
    val to = e.to
    return when {
        from == null || to == null -> "${ExtraText.name(e, style)} · 시각 모름"
        // [PAYX-54] 1.38 PC 12.4.1: work in the shift's own hours adds none; the times as typed, and why.
        e.insideShiftMinutes > 0 -> "${ExtraText.name(e, style)} · ${Fmt.timeRange(e.entry.start ?: from.toLocalTime(), e.entry.end ?: to.toLocalTime())} · " +
            if (e.minutes <= 0) "근무 시간 안이라 시간은 안 더해요" else "${HoursFormat.korean(e.minutes)} (근무와 겹친 ${HoursFormat.korean(e.insideShiftMinutes)} 뺌)"
        e.entry.hasTimes || e.covered != null -> "${ExtraText.name(e, style)} · ${Fmt.timeRange(from.toLocalTime(), to.toLocalTime())} · ${HoursFormat.korean(e.minutes)}"
        else -> "${ExtraText.kindName(e.kind, style)} · ${if (e.kind.timing is com.bamnat.core.model.ExtraTiming.BeforeShift) "근무 전" else "근무 뒤"} " +
            "${HoursFormat.korean(e.minutes)} (${ExtraText.span(from.toLocalTime(), to.toLocalTime())})"
    }
}

/**
 * [DAY-01] The top of a day: ‹ date › (a swipe moves a day too), what is worked, the extra work,
 * the facts that matter at a glance, and one-tap chips for what people do most. The same header
 * opens the sheet, sits above the side pane, and is the preview under the calendar.
 */
@Composable
fun DayHeader(
    day: ResolvedDay,
    today: LocalDate,
    snapshot: ScheduleSnapshot,
    vm: CalendarViewModel,
    onDateChange: (LocalDate) -> Unit,
    onMemo: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in the calendar's preview card: the sheet opens with [onOpen]. */
    onOpen: (() -> Unit)? = null,
    /** [CAL-01] The smaller title, for the preview card and the narrow unfolded panel. */
    compact: Boolean = onOpen != null,
    /** The first memo's token ("메모 · 도시락"); off while the day's story line (FLOW-08) shows the memos already. */
    memoToken: Boolean = true,
    /** [CAL-01] The week of this day over the hour limit; said in the head's one 주의 line with the day's rest signs ([RVH-59]). */
    weekNote: String? = null,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var dragged by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(day.date) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > threshold) onDateChange(day.date.minusDays(1))
                        else if (dragged < -threshold) onDateChange(day.date.plusDays(1))
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The title takes the room the buttons on the right leave, so its weekday and 오늘 are never cut.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateChange(day.date.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "전날")
                }
                AnimatedContent(
                    targetState = day.date,
                    transitionSpec = {
                        val forward = targetState.isAfter(initialState)
                        (slideInHorizontally(tween(220)) { w -> if (forward) w / 2 else -w / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(160)) { w -> if (forward) -w / 2 else w / 2 } + fadeOut(tween(160))) using
                            SizeTransform(clip = false)
                    },
                    label = "day-title",
                    modifier = Modifier.weight(1f, fill = false),
                ) { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // [CAL-52] Smaller, never "10월 22…": the date is the sheet's whole point.
                        com.bamnat.app.ui.FitText(
                            Fmt.dayTitle(d),
                            if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        if (d == today) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "오늘",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = { onDateChange(day.date.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 날")
                }
            }
            if (day.date != today) TextButton(onClick = { onDateChange(today) }) { Text("오늘") }
            if (onOpen != null) {
                IconButton(onClick = onOpen) { Icon(Icons.Filled.ExpandLess, "하루 자세히 보기") }
            }
        }
        // PC 확인 1.37 A16 (그날 근무·추가 근무 줄의 높이가 날마다 달라 칩 줄이 128px씩 움직여 '대근'이 잘못 눌림): in the day sheet the
        // chips come right under the date, where nothing above them changes height; the day's facts follow.
        // With very large text on a small phone the head is taller than the sheet's top; there the facts stay first so they are not cut.
        val chipsFirst = onOpen == null && LocalDensity.current.fontScale < 1.5f
        // [EASE-10] 1.38 (오늘근무 비교: 근무 바꾸기 2번): the kinds of work open first (주간·야간·휴무…), one tap changes the day; the
        // 대근·비상소집·돈 chips are the row under it. Both rows are one line that scrolls sideways, so their height never
        // changes from day to day and the rows stay where the finger expects them (A17).
        val headChips = shiftChipsInHead(day, snapshot, settings.workStyle)
        if (chipsFirst && headChips) ShiftChips(day, snapshot, vm, settings, Modifier.padding(bottom = 4.dp), endSpace = 12.dp)
        if (chipsFirst) QuickChips(day, today, snapshot, vm, settings, onMemo, Modifier.padding(bottom = 6.dp), endSpace = 12.dp)
        AnimatedContent(
            targetState = day,
            contentKey = { it.date },
            transitionSpec = {
                val forward = targetState.date.isAfter(initialState.date)
                (slideInHorizontally(tween(220)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(220))) togetherWith
                    (fadeOut(tween(120))) using SizeTransform(clip = false)
            },
            label = "day-body",
        ) { d ->
            DayFacts(d, today, snapshot, vm, settings, Modifier.padding(horizontal = 12.dp), memoToken, weekNote)
        }
        // Under the calendar the folded FAB sits over the row's end, so the last chip can scroll out from under it.
        if (!chipsFirst && headChips) ShiftChips(day, snapshot, vm, settings, Modifier.padding(top = 6.dp), endSpace = 12.dp)
        if (!chipsFirst) QuickChips(day, today, snapshot, vm, settings, onMemo, Modifier.padding(top = if (headChips) 4.dp else 6.dp), endSpace = 76.dp)
        // [EASE-26] The extra work just put in, made longer or shorter right here (잔업 1시간 30분: 날짜 · 잔업 · −30분).
        // [EASE-26] Under the calendar only: the opened day has the same ± in its 추가 근무 piece, so its head stays short.
        if (onOpen != null && day.extras.isNotEmpty()) ExtraNudgeLine(day, vm, Modifier.padding(start = 12.dp, top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayFacts(day: ResolvedDay, today: LocalDate, snapshot: ScheduleSnapshot, vm: CalendarViewModel, settings: AppSettings, modifier: Modifier, memoToken: Boolean = true, weekNote: String? = null) {
    val cal = LocalCalendarColors.current
    val focus = CalendarFocus.of(settings.workStyle)
    val book by vm.moneyBook.collectAsStateWithLifecycle()
    val dayBook = remember(book, day.date) { book.on(day.date) }
    // [CAL-05] Field and shop work show a shift only when the day has one; an empty day is not a day off.
    val hasShift = focus == CalendarFocus.SHIFTS || day.isWorkDay || day.shiftType != null || day.leave != null ||
        day.extras.isNotEmpty() || day.hasCustomTimes
    val sales = dayBook.sales?.takeIf { it.sales != 0L || it.costs != 0L }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val holiday = KoreanHolidays.nameOf(day.date)
        // [CYC-04] 1.38 업종 기념일, in the accent colour (not a day off).
        val orgDay = orgDayName(orgGroupOf(settings), day.date, holiday)
        if (settings.showLunar || holiday != null || orgDay != null) {
            Row {
                if (holiday != null) Text(holiday, style = MaterialTheme.typography.labelLarge, color = cal.sunday)
                if (orgDay != null) Text(orgDay, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (settings.showLunar) {
                    if (holiday != null || orgDay != null) Spacer(Modifier.width(8.dp))
                    Text(KoreanLunar.fromSolar(day.date).label, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // [CAL-05] A field worker's jobs and a shop's sales come first: they are what the day is about.
        for (j in dayBook.jobs.take(MAX_JOB_LINES)) DayJobLine(j, day.date)
        if (dayBook.jobs.size > MAX_JOB_LINES) {
            Text("일감 외 ${dayBook.jobs.size - MAX_JOB_LINES}건", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
        }
        sales?.let { DaySalesLine(it) }
        if (hasShift) Row(verticalAlignment = Alignment.CenterVertically) {
            val type = day.shiftType
            val leave = day.leave
            if (leave?.isFullDay == true) LeaveMark(leave, size = 20.dp)
            else ShiftBadge(type, size = 20.dp, text = type?.label ?: "휴", outlined = type == null || type.isOff)
            Spacer(Modifier.width(8.dp))
            Text(shiftLine(day), style = MaterialTheme.typography.bodyLarge, maxLines = bigLines(2), overflow = TextOverflow.Ellipsis)
        } else if (dayBook.jobs.isEmpty() && sales == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventBusy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(focus.emptyDay(day.isWorkDay), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // [EASE-12] A day changed from its order says what it was: "원래 야간 → 주간", with 되돌리기 right there.
        if (hasShift && day.source == DaySource.OVERRIDE && snapshot.hasPattern) {
            val was = snapshot.resolver.patternShiftIdOn(day.date)?.let { snapshot.typesById[it] }
            val now = day.shiftType
            // [EASE-11] PC 3db30e1e: a day off made a working day keeps '대근으로' here too, after the bar is gone.
            val cover = now?.takeIf { (was == null || was.isOff) && it.hasTimes && !it.isOff }
            ChangedLine(was, now, onBack = { vm.assign(day.date, com.bamnat.core.model.ShiftOverride.None) },
                coverWord = cover?.let { vm.coverWord() }, onCover = { cover?.let { vm.coverFromChange(day.date, it, snapshot) } })
        }
        for (e in day.extras.take(MAX_EXTRA_LINES)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(2.dp))
                ExtraKindMark(e.kind, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(extraLine(e, settings.workStyle), style = MaterialTheme.typography.bodyMedium, maxLines = bigLines(1), overflow = TextOverflow.Ellipsis)
            }
        }
        if (day.extras.size > MAX_EXTRA_LINES) {
            Text("외 ${day.extras.size - MAX_EXTRA_LINES}건", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
        }
        if (day.hasExtraWork && day.extraMinutes > 0) {
            Text(
                "이 날 합계 ${HoursFormat.korean(day.totalMinutes)}",
                style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                // 2.0.0 [RVP-13] (검토 UIX-15·U13-06) A plain sum is not a caution: the page's own ink, orange only for 주의.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val tokens = dayTokens(day, snapshot, vm, settings, memoToken)
        if (tokens.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in tokens) Token(t)
            }
        }
        // 2.0.0 [RVH-59] 검토 PC-63 (대근을 넣은 날짜 창 한 화면에 빨강 칩 '야간 뒤 휴식 부족', 빨강 '이 주 66시간', 큰 빨강 상자 '휴식 점검'):
        // what is for reference is said once, here, in one 주의 line (the mark orange, the words quiet). The 몸 group keeps the detail
        // without colour; red stays for what cannot be undone.
        dayCaution(day, snapshot, vm, settings, weekNote)?.let { ToneLine(it, tone = com.bamnat.app.ui.theme.Tone.Caution) }
    }
}

private const val MAX_EXTRA_LINES = 3

/** [EASE-26] "잔업 2시간  −30분 +10분 +30분 +1시간": the last extra work of the day, its length changed with a tap. */
@Composable
private fun ExtraNudgeLine(day: ResolvedDay, vm: CalendarViewModel, modifier: Modifier = Modifier) {
    val i = day.extras.lastIndex
    val e = day.extras[i]
    val style = vm.settings.collectAsStateWithLifecycle().value.workStyle
    // [J133-27] A weekday worker's own word, as the chip says it.
    val name = ExtraText.name(e, style)
    MinuteNudges(name, lead = name, modifier = modifier, onNudge = { d ->
        vm.updateExtra(day.date, i, com.bamnat.core.schedule.MinuteNudge.extra(e.entry, e.start, e.end, d, com.bamnat.app.data.ScheduleRepository.MAX_EXTRA_MINUTES))
    })
}

/** [EASE-12] "원래 [야] 야간 → [주] 주간 · 되돌리기": the same meaning as the changed day's mark on the calendar. */
@Composable
private fun ChangedLine(was: com.bamnat.core.model.ShiftType?, now: com.bamnat.core.model.ShiftType?, onBack: () -> Unit, coverWord: String? = null, onCover: () -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.tertiary
    val wasName = was?.name ?: "쉬는 날"
    val nowName = now?.name ?: "쉬는 날"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "원래 $wasName, 지금 $nowName"
    }) {
        Spacer(Modifier.width(2.dp))
        Text("원래", style = MaterialTheme.typography.bodyMedium, color = ink)
        Spacer(Modifier.width(6.dp))
        ShiftBadge(was, size = 16.dp, text = was?.label ?: "휴", outlined = was == null || was.isOff)
        Spacer(Modifier.width(4.dp))
        Text(wasName, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ink, modifier = Modifier.size(18.dp))
        ShiftBadge(now, size = 16.dp, text = now?.label ?: "휴", outlined = now == null || now.isOff)
        Spacer(Modifier.width(4.dp))
        Text(nowName, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(0.01f))
        if (coverWord != null) TextButton(onClick = onCover, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            modifier = Modifier.semantics { contentDescription = "근무표는 쉬는 날 그대로, ${nowName}을 ${coverWord}${Fmt.ro(coverWord)} 넣기" }) {
            Text("$coverWord${Fmt.ro(coverWord)}", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            Text("되돌리기", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * [EASE-10][EASE-11] The day's kinds of work, open in one line: a tap puts it on the day at once (no window first). On a day
 * the roster gives off, the bar says so and offers 대근 ([CalendarViewModel.assignQuick]); 되돌리기 is on the day's line.
 */
@Composable
private fun ShiftChips(day: ResolvedDay, snapshot: ScheduleSnapshot, vm: CalendarViewModel, settings: AppSettings, modifier: Modifier, endSpace: Dp) {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val calendar by c.calendars.active.collectAsStateWithLifecycle()
    val folded = c.foldedTypes.state.collectAsStateWithLifecycle().value[calendar].orEmpty()
    val style = settings.workStyle
    val types = remember(snapshot, style, day.shiftType?.id, folded) { dayTypes(snapshot, style, day.shiftType?.id, folded) }
    if (types.isEmpty()) return
    val fullLeave = day.leave?.isFullDay == true
    ChipRow(modifier, start = 12.dp, end = endSpace) {
        for (t in types) item(key = "shift-${t.id}") {
            val on = !fullLeave && day.shiftType?.id == t.id
            FilterChip(
                selected = on,
                onClick = { if (day.shiftType?.id != t.id) vm.assignQuick(day, com.bamnat.core.model.ShiftOverride.Shift(t.id), snapshot) },
                label = { ChipText(t.name) },
                leadingIcon = { ShiftBadge(t, size = 18.dp, outlined = t.isOff) },
                // 1.39 PC 5차 (가게 유형 TalkBack '영업, 지금 근무'): said without '근무', which a shop's or a field day's chip is not.
                modifier = Modifier.semantics { contentDescription = if (on) "${t.name}, 이날 골라 둔 것" else "${t.name}${Fmt.ro(t.name)} 바꾸기" },
            )
        }
        if (snapshot.shiftTypes.none { it.isOff }) item(key = "shift-off") {
            FilterChip(
                selected = !day.isWorkDay && day.shiftType == null && !fullLeave,
                onClick = { if (day.isWorkDay || day.shiftType != null) vm.assignQuick(day, com.bamnat.core.model.ShiftOverride.Off, snapshot) },
                label = { ChipText("쉬는 날") },
            )
        }
    }
}
private const val MAX_JOB_LINES = 3

/** [CAL-05] "09:00–17:00 욕실 타일 시공 · 3일 중 2일째", with what it pays or what is still owed. */
@Composable
private fun DayJobLine(job: com.bamnat.core.money.Job, date: LocalDate) {
    val cal = LocalCalendarColors.current
    // 2.0.0 [RVH-56] 검토 HQ-36 (L이 찾은 같은 구조, 조건 12): the badge sits on the job's first line, not in the middle of the block
    // once the amount goes under it at a big font (설정 WarningLine HQ-35 the same way). The box is the first line's height.
    val firstLine = with(androidx.compose.ui.platform.LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.heightIn(min = firstLine), contentAlignment = Alignment.Center) { JobBadge(count = 1, owed = job.unpaid > 0) }
        Spacer(Modifier.width(8.dp))
        // [BIGTXT-01] 1.39 (사용자 사진: 큰 글자에서 '페…'·'09:0/0') the amount goes under the job when both do not fit.
        com.bamnat.app.ui.components.SideOrBelow(Modifier.weight(1f), minStart = 168.dp, start = {
        Column {
            val time = job.start?.let { s -> Fmt.time(s) + (job.end?.let { "–" + Fmt.time(it) } ?: "") + " " }.orEmpty()
            // [CAL-52] Two lines before the title is cut ("욕실…" lost "타일 시공" beside 받을 돈 in a day panel).
            Text(time + job.title, style = MaterialTheme.typography.bodyLarge, maxLines = bigLines(2), overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                job.place.ifBlank { null },
                job.client.ifBlank { null },
                if (job.days > 1) "${job.days}일 중 ${(date.toEpochDay() - job.date.toEpochDay()).toInt() + 1}일째" else null,
            )
            if (sub.isNotEmpty()) {
                Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        }, end = {
            if (job.unpaid > 0) {
                Text("받을 돈 ${Fmt.wonShort(job.unpaid)}", style = MaterialTheme.typography.labelLarge.merge(NumberStyle), color = cal.warning)
            } else if (job.payable > 0) {
                Text(Fmt.wonShort(job.payable), style = MaterialTheme.typography.labelLarge.merge(NumberStyle), color = cal.income)
            }
        })
    }
}

/** [CAL-05] "매출 1,200,000원 · 남은 돈 900,000원". */
@Composable
private fun DaySalesLine(sales: com.bamnat.core.money.DailySales) {
    val cal = LocalCalendarColors.current
    // [BIGTXT-01] 1.39 (200%: '매출' alone, its amount cut away) the two parts wrap to the next line instead of being cut.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Storefront, null, tint = cal.income, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("매출 ${Fmt.won(sales.sales)}", style = MaterialTheme.typography.bodyLarge.merge(NumberStyle), maxLines = 1, softWrap = false)
        if (sales.costs != 0L) {
            Text(" · 남은 돈 ${Fmt.wonShort(sales.net)}", style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                color = if (sales.net < 0) cal.expense else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun dayTokens(day: ResolvedDay, snapshot: ScheduleSnapshot, vm: CalendarViewModel, settings: AppSettings, memoToken: Boolean = true): List<DayToken> {
    val memos by vm.datedMemos.collectAsStateWithLifecycle()
    val dayMemos = remember(memos, day.date) { memos.filter { it.date == day.date } }
    val alarms = remember(snapshot, day.date) { plannedAlarms(snapshot, day) }
    val calendarAlarms by vm.calendarAlarmsOn.collectAsStateWithLifecycle()
    return buildList {
        // [CAL-13] An added calendar kept from ringing says so on each of its days.
        if (alarms.isNotEmpty() && !calendarAlarms) add(DayToken("이 달력 알람 꺼짐", DayToken.Tone.PLAIN))
        else if (alarms.isNotEmpty()) {
            // 1.37 시간 바퀴 PC LOW: turned off, the token keeps its words (struck through) so the rows under it do not jump.
            val first = alarms.first()
            val text = "알람 ${whenText(first, day.date)}" + if (alarms.size > 1) " 외 ${alarms.size - 1}" else ""
            val off = day.alarmSkipped || !settings.alarmsEnabled
            add(if (off) DayToken(text, DayToken.Tone.ERROR, said = "$text 꺼짐", struck = true) else DayToken(text, DayToken.Tone.PLAIN))
        }
        // [RVH-59] Quiet: the 추가 근무 group's card says it in 주의 (조건 6, one place).
        if (day.extras.any { it.entry.needsReview }) add(DayToken("추가 근무 확인 필요", DayToken.Tone.PLAIN))
        // [EASE-12] A changed shift has its own line ("원래 → 새 근무"); the token stays for changed times.
        // [READ-37] 1.38 PC: on a calendar without an order (매달 근무표) every day is put in by hand or from a photo, so
        // "직접 바꿈" said nothing on each of them; only a day's own times are worth a word there.
        if (day.hasCustomTimes) add(DayToken("이 날만 시간 바꿈", DayToken.Tone.ACCENT))
        // [NOTE-06] The first memo's title, cut to one line, and how many more there are.
        if (memoToken) dayMemos.firstOrNull()?.let { first ->
            val title = first.title.ifBlank { first.preview }.lineSequence().firstOrNull().orEmpty().trim()
            val shown = if (title.length > 14) title.take(13) + "…" else title
            val more = if (dayMemos.size > 1) " 외 ${dayMemos.size - 1}" else ""
            add(DayToken(if (shown.isEmpty()) "메모 ${dayMemos.size}" else "메모 · $shown$more", DayToken.Tone.PLAIN))
        }
        // [CAL-01][RVH-59] The day's rest signs and a week over the limit are said in the head's one 주의 line ([dayCaution]).
    }
}

/**
 * 2.0.0 [RVH-59] 검토 PC-63: the day's rest signs ("야간 뒤 휴식 부족") and its week over the limit ("이 주 66시간 · 52시간 넘음") in one
 * line, or null when there are none.
 */
@Composable
private fun dayCaution(day: ResolvedDay, snapshot: ScheduleSnapshot, vm: CalendarViewModel, settings: AppSettings, weekNote: String?): String? {
    val rest = remember(snapshot, day.date, settings.rules, settings.restCheck) {
        if (settings.restCheck) vm.restIssuesOn(snapshot, day.date, settings.rules) else emptyList()
    }
    return (rest.map { it.kind.label }.distinct() + listOfNotNull(weekNote)).joinToString(" · ").ifEmpty { null }
}

@Composable
private fun Token(t: DayToken) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (t.tone) {
        DayToken.Tone.PLAIN -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        DayToken.Tone.ACCENT -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        DayToken.Tone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Text(
        t.text,
        style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
        color = fg,
        maxLines = 1,
        textDecoration = if (t.struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
            .then(if (t.said != null) Modifier.semantics { contentDescription = t.said } else Modifier),
    )
}

/**
 * [DAY-01][EXTRA-01] What people do most on a day, one tap each: the likely extra work (taken off
 * with a second tap), 반차·연차, the day's alarm, sleep on today, and a memo. Each says itself back
 * with undo.
 */
@Composable
private fun QuickChips(
    day: ResolvedDay,
    today: LocalDate,
    snapshot: ScheduleSnapshot,
    vm: CalendarViewModel,
    settings: AppSettings,
    onMemo: () -> Unit,
    modifier: Modifier = Modifier,
    endSpace: Dp = 12.dp,
) {
    val focus = CalendarFocus.of(settings.workStyle)
    val moneyFirst = focus.moneyFirst
    val calendarAlarms by vm.calendarAlarmsOn.collectAsStateWithLifecycle()
    // 2.0.0 [RVK-05] (MONEY-12): 하루 마감·일감 sheets stay open through a rebuilt screen.
    var addingMoney by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val book by vm.moneyBook.collectAsStateWithLifecycle()
    // [HRLY-08][DAYW-03] Work run past midnight is clocked out on the day it started.
    val now = rememberMinute()
    val yesterday = { snapshot.resolver.resolve(today.minusDays(1)) }
    val leaveDay = leaveNowDay(settings.workStyle, day, today, now, yesterday)
    // 1.36 PC 재검토: no 대근 of a kind put away ('오전 알바 대근' after 알바 → 현장).
    val folded = com.bamnat.app.ui.shifts.foldedHere()
    val choices = remember(snapshot, day, folded) {
        if (moneyFirst) emptyList() else {
            val (on, off0) = ExtraText.choices(snapshot.kinds, snapshot.shiftTypes.filter { it.id !in folded }, day).partition { ExtraText.indexOf(day, it) >= 0 }
            // [CAL-37] The kinds used in the last 60 days first; a new user gets their kind of work's usual order.
            val used = snapshot.entries.filter { !it.date.isBefore(today.minusDays(QuickOrder.DAYS.toLong())) }.flatMap { e -> e.extras.map { it.kindId } }
            // [PAYX-51] The kind made last comes first until it is first used.
            val everUsed = snapshot.entries.flatMapTo(HashSet()) { e -> e.extras.map { it.kindId } }
            val off = QuickOrder.extras(off0, { it.kind.id }, used, settings.workStyle.takeIf { !day.isWorkDay }, everUsed)
            on + off.take((QUICK_EXTRAS - on.size).coerceAtLeast(0))
        }
    }
    // 1.39 [WTYP-31] PC 7차: 비상소집 only for a job that is called in (경찰·소방…), or a 비상소집 kind the user made.
    val payProfile by vm.payProfile.collectAsStateWithLifecycle()
    val callable = com.bamnat.core.work.Callout.forJob(settings.workStyle, payProfile?.occupation, settings.orgDayGroup, snapshot.kinds)
    val alarms = remember(snapshot, day.date) { plannedAlarms(snapshot, day) }
    val log by vm.sleepLog.collectAsStateWithLifecycle()
    // [FLD-01][FLD-02] A day-rate field calendar offers 공수 buttons instead of 일감 적기.
    val calendar by vm.activeCalendar.collectAsStateWithLifecycle()
    val calendarContainer = androidx.compose.ui.platform.LocalContext.current.appContainer
    val calendars = calendarContainer.calendars
    val fieldRules = calendar.field ?: com.bamnat.core.money.FieldRules()
    val dayRate = focus == CalendarFocus.JOBS && fieldRules.dayRateMode
    val platform = focus == CalendarFocus.JOBS && fieldRules.platformMode
    // [J133-19] 1.33 (실사용 테스트 "야간인 날에 '주간 대근'을 누르면 하루 22시간 근무"): on a working day a 대근 of another shift
    // asks first whether the day's shift changes or the 대근 comes on top of it.
    var coverAsk by remember { mutableStateOf<ExtraChoice?>(null) }
    coverAsk?.let { c -> CoverOrChangeDialog(day, c, snapshot, settings.workStyle,
        onChange = { id -> coverAsk = null; vm.assign(day.date, com.bamnat.core.model.ShiftOverride.Shift(id)) },
        onAdd = { coverAsk = null; vm.toggleExtra(day, c) },
        onDismiss = { coverAsk = null }) }
    ChipRow(modifier, start = 12.dp, end = endSpace) {
        if (dayRate) dayRateChips(day.date, book, fieldRules, onDelete = vm::deleteJob, onSave = { job, r ->
            calendars.updateField(calendar.id) { it.copy(dayRate = r.dayRate, lastClient = r.lastClient, rateFrom = r.rateFrom, unitRules = r.unitRules) }
            vm.saveJob(job)
        }, onReprice = { list -> list.forEach(vm::saveJob) })
        // G 1.32 [WTYP-02] 플랫폼형: 오늘 플랫폼마다 건수·금액·운행 시간.
        if (platform) platformChips(day.date, book, fieldRules, onSave = vm::saveJob, onDelete = vm::deleteJob,
            onCut = { p, c -> calendars.updateField(calendar.id) { it.copy(platformCosts = it.platformCosts + (p to c)) } })
        // [CAL-05] Field and shop work: the day's job or sales is the first chip.
        focus.addLabel?.takeIf { !dayRate && !platform }?.let { label ->
            val hasSales = focus == CalendarFocus.SALES && book.on(day.date).sales != null
            item {
                AssistChip(
                    onClick = { addingMoney = true },
                    label = { ChipText(if (hasSales) "매출 고치기" else label) },
                    leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    border = null,
                )
            }
        }
        // [HRLY-01][HRLY-08] 알바: 출근·퇴근, 지각·조퇴·결근, 대타.
        if (settings.workStyle == com.bamnat.core.work.WorkStyle.HOURLY) hourlyChips(day, today, snapshot, vm, now)
        // [DAYW-03] A day worker's 지금 퇴근, first on today's working day.
        if (leaveDay != null) item { LeaveNowChip(leaveDay, vm, yesterday = leaveDay.date != day.date) }
        // 1.32 [WTYP-09] 비번 날 비상소집·동원, one chip (기능 켜고 끄기 `shift.callout`).
        // [SETUP-16] 1.37 PC f42b2eba: without any 근무표 (일근 first setup put off) no day is a 비번 to be called in on.
        if (!moneyFirst && !day.isWorkDay && (snapshot.hasPattern || !settings.setupLater) && callable &&
            !com.bamnat.app.ui.guide.FeatureSwitches.hidden(calendarContainer, "shift.callout", calendar.id)
        ) item { CalloutChip(day, snapshot, vm) }
        // 1.32 [WTYP-11] 희망 오프 on a day to come (기능 켜고 끄기 `shift.wishOff`).
        if (!moneyFirst && day.date.isAfter(today) && com.bamnat.core.work.WishOff.offered(settings.workStyle) &&
            !com.bamnat.app.ui.guide.FeatureSwitches.hidden(calendarContainer, com.bamnat.app.calendar.DateNoticeAlarm.WISH_OFF, calendar.id)
        ) item { WishOffChips(day.date, calendar.id) }
        for (c in choices) item {
            val i = ExtraText.indexOf(day, c)
            FilterChip(
                selected = i >= 0,
                onClick = {
                    val covered = c.entry.coveredShiftId
                    if (i < 0 && covered != null && day.isWorkDay && day.shiftType != null && day.shiftType?.id != covered) coverAsk = c
                    else vm.toggleExtra(day, c)
                },
                label = {
                    // [J133-27] 1.33 (실사용 테스트 "직장인 말은 '야근'인데 칩은 '잔업'"): a weekday worker's own word for it.
                    ChipText(if (i >= 0) ExtraText.name(day.extras[i], settings.workStyle) else ExtraText.word(c.label, c.kind.id, settings.workStyle))
                },
                leadingIcon = {
                    if (i >= 0) Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) else ExtraKindMark(c.kind, size = 18.dp)
                },
            )
        }
        if (!moneyFirst && (day.leave != null || day.isWorkDay && day.shiftType?.hasTimes == true)) {
            val kind = day.leave?.kind ?: LeaveKind.ANNUAL
            for (q in listOf(2, 4)) item {
                val l = Leave(kind, q)
                val on = day.leave?.quarters == q
                FilterChip(
                    selected = on,
                    onClick = { vm.setLeave(day.date, if (on) null else l, announce = true) },
                    label = { ChipText(l.label) },
                    leadingIcon = { LeaveMark(l, size = 18.dp) },
                )
            }
        }
        if (alarms.isNotEmpty() && calendarAlarms) item {
            val off = day.alarmSkipped
            AssistChip(
                onClick = { vm.setAlarmSkipped(day.date, !off, announce = true) },
                label = { ChipText(if (off) "알람 켜기" else "알람 끄기") },
                leadingIcon = { Icon(if (off) Icons.Filled.Alarm else Icons.Filled.AlarmOff, null, Modifier.size(18.dp)) },
                colors = if (off) AssistChipDefaults.assistChipColors(labelColor = LocalCalendarColors.current.alarmOff,
                    leadingIconContentColor = LocalCalendarColors.current.alarmOff) else AssistChipDefaults.assistChipColors(),
                border = if (off) BorderStroke(1.dp, LocalCalendarColors.current.alarmOff) else AssistChipDefaults.assistChipBorder(true),
            )
        }
        // [CAL-05] Sleep goes with shifts, as 오늘의 흐름 does; a job or shop day keeps its chips for the day's work.
        if (day.date == today && settings.sleepTips && !moneyFirst) item {
            val asleep = log.firstOrNull { it.end == null } != null
            AssistChip(
                // 1.39 [FLOW-13] 잠들었어요 opens the dark night screen too.
                onClick = { if (asleep) vm.wokeUp() else { vm.fellAsleep(); com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.NIGHT) } },
                label = { ChipText(if (asleep) "깼어요" else "잠들었어요") },
                leadingIcon = { Icon(if (asleep) Icons.Filled.WbSunny else Icons.Filled.Bedtime, null, Modifier.size(18.dp)) },
            )
        }
        item {
            AssistChip(
                onClick = onMemo,
                label = { ChipText("메모") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null, Modifier.size(18.dp)) },
            )
        }
    }
    if (addingMoney) {
        // [FLD-01] The first 일감 적기 on a field calendar asks once how it counts its work.
        if (focus == CalendarFocus.JOBS && calendar.field?.mode == null) {
            FieldModePrompt { m ->
                calendars.updateField(calendar.id) { it.copy(mode = m) }
                if (m == com.bamnat.core.money.FieldMode.DAY_RATE || m == com.bamnat.core.money.FieldMode.PLATFORM) addingMoney = false
            }
        } else if (focus == CalendarFocus.INVEST) {
            // [INV-03] 매매 기록 for this day.
            com.bamnat.app.ui.invest.InvestQuickTrade(calendar.id, day.date, onDismiss = { addingMoney = false })
        } else if (focus == CalendarFocus.JOBS) {
            com.bamnat.app.ui.money.JobDialog(day.date, null, onSave = { vm.saveJob(it); addingMoney = false }, onDismiss = { addingMoney = false })
        } else {
            com.bamnat.app.ui.money.SalesDialog(day.date, book.on(day.date).sales,
                onSave = { vm.saveSales(it); addingMoney = false }, onDismiss = { addingMoney = false })
        }
    }
}

private const val QUICK_EXTRAS = 3

/**
 * Fades the ends of a row that scrolls sideways, each only while there is more that way, so a cut-off
 * row reads as "there is more" and a row shown whole is not dimmed. Read while drawing: scrolling
 * redraws the edge without recomposing the row.
 */
internal fun Modifier.fadingEdges(back: () -> Boolean, forward: () -> Boolean, width: Dp = 32.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 3)
        if (forward()) drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f),
            size = androidx.compose.ui.geometry.Size(w, size.height),
            blendMode = BlendMode.DstIn,
        )
        if (back()) drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
            size = androidx.compose.ui.geometry.Size(w, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

/** [RVH-49] What a [ChipRow] holds: one chip per [item], in order (the same calls as the sideways row it replaced). */
internal class ChipScope {
    internal val items = mutableListOf<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) { items += key to content }
}

/**
 * [CAL-18] Chips in one line. 2.0.0 [RVH-49] 총괄 사진 검토 HQ-16 (편 폭 날짜 창에서 '야간'·'오후 대근' 칩이 글자 가운데서 잘림): the
 * row no longer scrolls sideways under a fade. It shows as many whole chips as fit, never a chip cut in two, then "+2 ›" for the
 * rest; a tap on it opens every chip wrapped on as many lines as they need, and '접기' puts them back in one line (사용자 11-04
 * '좌우로 스크롤해야 보이는 줄은 펼쳐서 한눈에'). Taps to reach a chip out of sight stay one, as the scroll to the end was.
 */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, start: Dp, end: Dp, content: ChipScope.() -> Unit) {
    val chips = ChipScope().apply(content).items
    var open by rememberSaveable { mutableStateOf(false) }
    // A slot made for a chip of the day before (more chips) can be drawn once more before it goes: it then draws nothing.
    val chip: @Composable (Int) -> Unit = { i -> chips.getOrNull(i)?.let { (k, c) -> key(k ?: "chip#$i") { c() } } }
    if (open) {
        FlowRow(
            modifier.fillMaxWidth().padding(start = start, end = maxOf(end, 4.dp)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            chips.indices.forEach { chip(it) }
            MoreButton("접기", "칩 접기", Icons.Filled.KeyboardArrowUp) { open = false }
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val moreStyle = MaterialTheme.typography.labelLarge.merge(NumberStyle)
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gap = 8.dp.roundToPx()
        val s = start.roundToPx()
        val room = (constraints.maxWidth - s - end.roundToPx()).coerceAtLeast(0)
        val loose = Constraints(maxWidth = room, maxHeight = constraints.maxHeight)
        // An item may say nothing (a chip shown only on some days): it takes no place.
        // Measured in copies that are not there for TalkBack or touch (#451 a11y: chips measured but left out of the row were
        // still read and counted, with no place on screen); only the chips placed below are composed for real.
        val sizes = chips.indices.map { i ->
            subcompose("size#$i") { androidx.compose.foundation.layout.Box(Modifier.clearAndSetSemantics { }) { chip(i) } }.map { it.measure(loose) }
        }
        val widths = sizes.map { p -> if (p.isEmpty()) 0 else p.sumOf { it.width } + gap * (p.size - 1) }
        val shown = widths.indices.filter { widths[it] > 0 }
        fun span(n: Int) = shown.take(n).sumOf { widths[it] } + gap * (n - 1).coerceAtLeast(0)
        var count = shown.size
        var more: androidx.compose.ui.layout.Placeable? = null
        if (span(count) > room) {
            // The button's width as it will be: 10dp + "+n" + 18dp arrow + 4dp ([MoreButton]), and 4dp before it.
            val reserve = measurer.measure("+${shown.size}", moreStyle, maxLines = 1, softWrap = false).size.width + 36.dp.roundToPx()
            count = 0
            while (count < shown.size && span(count + 1) <= room - reserve) count++
            val hidden = shown.size - count
            more = subcompose("more") { MoreButton("+$hidden", "${hidden}개 더 보기") { open = true } }.first().measure(Constraints())
        }
        val real = shown.take(count).associateWith { i -> subcompose("chip#$i") { chip(i) }.map { it.measure(loose) } }
        val placed = real.values.flatten()
        val height = maxOf(placed.maxOfOrNull { it.height } ?: 0, more?.height ?: 0)
        layout(constraints.maxWidth, height) {
            var x = s
            for (i in shown.take(count)) for (p in real.getValue(i)) {
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
            more?.let { it.placeRelative(if (count == 0) s else x - gap + 4.dp.roundToPx(), (height - it.height) / 2) }
        }
    }
}

/** [CAL-18] "+2 ›": how many chips are out of sight, as a button that opens them; [RVH-49] '접기' in the same shape closes them. */
@Composable
private fun MoreButton(text: String, said: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 2.dp,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = said },
    ) {
        Row(Modifier.heightIn(min = 32.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChipText(text, MaterialTheme.typography.labelLarge.merge(NumberStyle))
            Icon(icon, null, Modifier.size(18.dp))
        }
    }
}

/**
 * [DAY-01] The selected day under the calendar on phones: the same header as the sheet, so most
 * things are done right here; a tap on the card (or on the day again) opens everything.
 */
@Composable
fun DayPreview(
    day: ResolvedDay,
    today: LocalDate,
    snapshot: ScheduleSnapshot,
    vm: CalendarViewModel,
    onDateChange: (LocalDate) -> Unit,
    onOpen: () -> Unit,
    onMemo: () -> Unit,
    modifier: Modifier = Modifier,
    /** [CAL-01] The week of this day over the hour limit, in one line. */
    weekNote: String? = null,
) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            DayHeader(day, today, snapshot, vm, onDateChange, onMemo, Modifier.padding(top = 4.dp, bottom = 10.dp), onOpen = onOpen,
                weekNote = weekNote)
        }
    }
}

/** [CAL-52] A line's room in the day's header: one more line with text at 150% and up, so large text wraps before it is cut. */
@Composable
private fun bigLines(lines: Int): Int = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) lines + 1 else lines

/**
 * [CAL-52] A chip's label in one line that gets smaller rather than cut when the row has less height than large
 * text wants (320dp at 200%: "잔업 2시간" lost its bottom).
 */
@Composable
internal fun ChipText(text: String, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text, modifier,
        // The line as tall as the letters: labelLarge's 20sp line, doubled, alone overran the chip.
        style = style.copy(color = androidx.compose.material3.LocalContentColor.current, lineHeight = 1.2.em).merge(FooterTrim),
        maxLines = 1, softWrap = false,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize.takeIf { it.isSp } ?: 14.sp),
    )
}

/** [J133-19] 근무를 바꿀까요, 더할까요: a 대근 chip on a day that already has a shift. */
@Composable
private fun CoverOrChangeDialog(day: ResolvedDay, c: ExtraChoice, snapshot: ScheduleSnapshot, style: com.bamnat.core.work.WorkStyle?, onChange: (Long) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val now = day.shiftType?.name ?: "근무"
    val word = ExtraText.coverWord(style) // 2.0.0 [RVH-07] 검토 CAL-11
    val covered = snapshot.shiftTypes.firstOrNull { it.id == c.entry.coveredShiftId }
    val total = (day.scheduledMinutes + c.entry.paidMinutes) / 60.0
    val hours = if (total % 1.0 == 0.0) "${total.toInt()}시간" else "%.1f시간".format(total)
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 날은 $now 근무예요") },
        text = {
            Text(buildString {
                covered?.let { append("근무를 ${it.name}${Fmt.ro(it.name)} 바꾸면 이 날은 ${it.name} 하나로 계산돼요. ") }
                append("‘${c.name}’${Fmt.obj(c.name)} 더하면 $now 근무에 $word${Fmt.iga(word)} 붙어 하루 $hours 일한 것으로 계산돼요.")
                if (total > 16) append(" 실제로 이어서 일했을 때만 더해 주세요.")
            })
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                covered?.let { t -> TextButton(onClick = { onChange(t.id) }) { Text("근무를 ${t.name}${Fmt.ro(t.name)} 바꾸기") } }
                TextButton(onClick = onAdd) { Text("$word${Fmt.ro(word)} 더하기 (하루 $hours)") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
