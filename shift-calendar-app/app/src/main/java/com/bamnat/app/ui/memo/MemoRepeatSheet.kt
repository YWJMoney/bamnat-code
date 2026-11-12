package com.bamnat.app.ui.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.memo.MemoRepeat.Kind
import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 2.0.0 [RVI-73] (검토 U07a-07·I07aa-05·T07a-09·T07a-10) One window for every way a memo comes back. Before, the editor's chip had
 * 매주·매달·매년·근무마다 and the 일정 창 had 안 함·매일·평일·쉬는 날, two different lists, and neither had several weekdays (가게의 납품
 * 요일), 격주 (격주 휴무), 매달 둘째 수요일 (정기 휴무), N일마다 (현장 점검 주기) or an end. Both open this one now; it shows the next
 * three days it falls on, so a repeat is checked before it is kept.
 *
 * [date] is the memo's day (null: it gets the first day the repeat falls on from today, as the editor says); [onPick] gets the
 * stored text ("" for none), [onShift] a 근무마다 kind.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatSheet(
    repeat: String,
    date: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    shiftTypes: List<com.bamnat.core.model.ShiftType> = emptyList(),
    dayShiftId: Long? = null,
    onShift: (Long) -> Unit = {},
    restRepeat: Boolean = shiftTypes.isNotEmpty(),
) {
    val today = Today.date()
    val start = date ?: today
    val was = remember(repeat) { MemoRepeat.kindOf(repeat) }
    // 매주 and 요일 골라 are one way here ("요일마다"): one weekday picked is 매주 수요일, several are 매주 월·수·금.
    var kind by rememberSaveable { mutableStateOf(if (was == Kind.DAYS_OF_WEEK) Kind.WEEK else was) }
    var days by rememberSaveable {
        mutableStateOf(MemoRepeat.weekdaysOf(repeat).takeIf { was != Kind.WEEKDAYS && it.isNotEmpty() }?.map { it.value }?.toSet()
            ?: setOf(start.dayOfWeek.value))
    }
    var weeks by rememberSaveable { mutableIntStateOf(MemoRepeat.stepOf(repeat)?.takeIf { was == Kind.EVERY_WEEKS } ?: 2) }
    var every by rememberSaveable { mutableIntStateOf(MemoRepeat.stepOf(repeat)?.takeIf { was == Kind.EVERY_DAYS } ?: 10) }
    // 매달: on its day (14일) or on its 몇째 요일 (둘째 수요일).
    var byWeekday by rememberSaveable { mutableStateOf(was == Kind.NTH_WEEKDAY) }
    var shiftId by rememberSaveable { mutableStateOf(MemoRepeat.shiftIdOf(repeat)) }
    var untilDay by rememberSaveable { mutableStateOf(MemoRepeat.untilOf(repeat)?.toEpochDay()) }
    var pickUntil by remember { mutableStateOf(false) }

    val weekdays = days.map(DayOfWeek::of).toSet()
    val nth = MemoRepeat.nthOf(start)
    val body: String? = when (kind) {
        Kind.NONE -> ""
        Kind.WEEK, Kind.DAYS_OF_WEEK -> MemoRepeat.daysOfWeek(weekdays)
        Kind.EVERY_WEEKS -> MemoRepeat.everyWeeks(weeks, weekdays)
        Kind.EVERY_DAYS -> MemoRepeat.everyDays(every)
        Kind.MONTH, Kind.NTH_WEEKDAY -> if (byWeekday) MemoRepeat.nthWeekday(nth, start.dayOfWeek) else MemoRepeat.code(Kind.MONTH, start)
        Kind.SHIFT -> shiftId?.let(MemoRepeat::shiftCode)
        else -> MemoRepeat.code(kind, start).ifEmpty { null }
    }
    val until = untilDay?.let(LocalDate::ofEpochDay)
    val code = body?.let { MemoRepeat.withUntil(it, until) }

    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("반복") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ways = listOfNotNull(
                        Kind.NONE to "안 함", Kind.DAY to "매일", Kind.WEEKDAYS to "평일", Kind.WEEK to "요일마다", Kind.EVERY_WEEKS to "격주",
                        Kind.EVERY_DAYS to "며칠마다", Kind.MONTH to "매달", Kind.YEAR to "매년",
                        (Kind.LUNAR to "매년 음력").takeIf { MemoRepeat.code(Kind.LUNAR, start).isNotEmpty() },
                        (Kind.REST to "쉬는 날").takeIf { restRepeat || was == Kind.REST },
                    )
                    for ((k, label) in ways) FilterChip(
                        selected = kind == k || (k == Kind.MONTH && kind == Kind.NTH_WEEKDAY),
                        onClick = { kind = k },
                        label = { Text(label) },
                    )
                    // [EASE-22] 근무마다: the day's own kind first.
                    for (t in shiftTypes.sortedBy { if (it.id == dayShiftId) -1 else 0 }) FilterChip(
                        selected = kind == Kind.SHIFT && shiftId == t.id,
                        onClick = { kind = Kind.SHIFT; shiftId = t.id },
                        label = { Text("${t.name}마다") },
                        leadingIcon = { com.bamnat.app.ui.components.ShiftBadge(t, size = 18.dp, outlined = t.isOff) },
                    )
                }
                when (kind) {
                    Kind.WEEK, Kind.DAYS_OF_WEEK, Kind.EVERY_WEEKS -> {
                        if (kind == Kind.EVERY_WEEKS) Stepper("${if (weeks == 2) "격주" else "${weeks}주마다"} · ${Fmt.monthDay(start)}이 든 주부터",
                            weeks, 2..4, "주") { weeks = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in DayOfWeek.entries) {
                                val on = d.value in days
                                FilterChip(
                                    selected = on,
                                    // At least one weekday stays picked.
                                    onClick = { days = if (on && days.size > 1) days - d.value else days + d.value },
                                    label = { Text("월화수목금토일"[d.value - 1].toString()) },
                                    modifier = Modifier.semantics { contentDescription = "${"월화수목금토일"[d.value - 1]}요일" },
                                )
                            }
                        }
                    }
                    Kind.EVERY_DAYS -> Stepper("${every}일마다 · ${Fmt.monthDay(start)}부터", every, 2..365, "일") { every = it }
                    Kind.MONTH, Kind.NTH_WEEKDAY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !byWeekday, onClick = { byWeekday = false },
                            label = { Text(MemoRepeat.label(MemoRepeat.code(Kind.MONTH, start))) })
                        FilterChip(selected = byWeekday, onClick = { byWeekday = true },
                            label = { Text(MemoRepeat.label(MemoRepeat.nthWeekday(nth, start.dayOfWeek).orEmpty())) })
                    }
                    else -> Unit
                }
                if (kind != Kind.NONE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                        Text("끝", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
                        FilterChip(selected = until == null, onClick = { untilDay = null }, label = { Text("계속") })
                        FilterChip(selected = until != null, onClick = { pickUntil = true },
                            label = { Text(until?.let { "${Fmt.monthDay(it)}까지" } ?: "날짜까지") })
                    }
                    // [RVI-73] (검토 I07aa-05) 다음 차례: the next three days, so 격주 or 둘째 수요일 is seen to land where meant.
                    nextLine(code, date, kind)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val picked = code ?: return@Button
                if (kind == Kind.SHIFT) shiftId?.let(onShift) else onPick(picked)
                onDismiss()
            }, enabled = code != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("정하기") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } },
    )

    if (pickUntil) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (until ?: start.plusMonths(3)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        // An end before the start is the start: the memo is still on its own day.
                        untilDay = maxOf(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate(), start).toEpochDay()
                    }
                    pickUntil = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

/** − n + with 48dp buttons, for 격주's weeks and 며칠마다's days. */
@Composable
private fun Stepper(text: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, "1$unit 줄이기")
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, "1$unit 늘리기")
        }
    }
}

/**
 * [RVI-73] "다음: 10/16 (금) · 10/21 (수) · 10/23 (금)" for [code] from [date] (a memo with none starts on its first day from today);
 * for one read from the schedule (쉬는 날, 근무마다), where the days are the calendar's, the line says so.
 */
internal fun nextLine(code: String?, date: LocalDate?, kind: Kind): String? {
    if (code.isNullOrEmpty()) return null
    if (kind == Kind.SHIFT || kind == Kind.REST) return "달력의 근무표를 따라가요. 근무가 바뀌면 메모도 따라가요"
    val today = Today.date()
    val start = date ?: MemoRepeat.firstFrom(code, today) ?: return null
    val next = MemoRepeat.datesIn(Memo(date = start, repeat = code), maxOf(today, start), today.plusDays(400)).take(3)
    if (next.isEmpty()) return "앞으로 올 날이 없어요"
    return "다음: " + next.joinToString(" · ") { Fmt.monthDay(it) + " (" + "월화수목금토일"[it.dayOfWeek.value - 1] + ")" }
}
