package com.bamnat.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.PatternTimeline
import java.time.LocalDate

/**
 * [SCHED-03] I 리뷰 I5·G P26: "이 날부터 순서 다시 맞추기" from a day's long-press menu. The user says which step of
 * the order [date] is; the order restarts there and every day before stays. The days set by hand in the next
 * [AHEAD_DAYS] days are listed, ticked, because they were usually the workaround for the order being off.
 *
 * [EASE-25] 1.38 "오늘부터 하루 밀기" (오늘근무 분석 P7): 하루 밀기·하루 당기기 first, in the same window as the step chips (one
 * menu, not two), with the week ahead as it is now and as it will be, so the result is seen before it is saved; the
 * snackbar after says it in one line with 되돌리기.
 */
@Composable
internal fun RealignDialog(
    date: LocalDate,
    snap: ScheduleSnapshot,
    onDismiss: () -> Unit,
    onConfirm: (step: Int, clear: List<LocalDate>) -> Unit,
    onPause: ((days: Int) -> Unit)? = null,
) {
    val rotation = snap.resolver.patternOn(date) as? WorkPattern.Rotation ?: return
    val original = remember(rotation, date) { rotation.stepIndexOn(date) }
    var step by rememberSaveable(date) { mutableIntStateOf(original) }
    val handChanged = remember(snap, date) { PatternTimeline.handChangedFrom(snap.entries, date, date.plusDays(AHEAD_DAYS)) }
    // The days the user ticked to go back to the order. None by default: a day set by hand stays as it is unless the user
    // says otherwise (1.38 PC 확인, 9b028f4c: 밀기 뒤 직접 바꾼 휴무·야간이 사라졌어요). Kept as epoch days so a fold keeps the ticks.
    var picked by rememberSaveable(date) { mutableStateOf(listOf<Long>()) }
    val clear = handChanged.filter { it.toEpochDay() in picked }
    val push = PatternTimeline.pushOf(rotation, date, step)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Fmt.monthDay(date)}부터 순서 밀기·맞추기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // [EASE-25] The two most common fixes, a tap each; tapped again they go a day further.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { step = Math.floorMod(step + 1, rotation.steps.size) },
                        modifier = Modifier.weight(1f)) { Text("‹ 하루 당기기", maxLines = 1) }
                    androidx.compose.material3.FilledTonalButton(onClick = { step = Math.floorMod(step - 1, rotation.steps.size) },
                        modifier = Modifier.weight(1f)) { Text("하루 밀기 ›", maxLines = 1) }
                }
                WeekAhead(date, snap, rotation, original, step, clear)
                Text(
                    when {
                        push == 0 -> "그 전날까지는 그대로 남아요."
                        push > 0 -> "${Fmt.monthDay(date)}부터 ${push}일 밀려요. 그 전날까지는 그대로 남아요."
                        else -> "${Fmt.monthDay(date)}부터 ${-push}일 당겨져요. 그 전날까지는 그대로 남아요."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("${Fmt.dayTitle(date)}은 순서의 몇 번째 날인가요?", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rotation.steps.forEachIndexed { i, id ->
                        val type = id?.let { snap.typesById[it] }
                        FilterChip(
                            selected = step == i,
                            onClick = { step = i },
                            label = { Text("${i + 1}") },
                            leadingIcon = { ShiftBadge(type, size = 18.dp, text = type?.label ?: "휴") },
                        )
                    }
                }
                // G 1.32 [WTYP-07] 명절 휴무: stop the order for a few days and go on where it left off.
                if (onPause != null) {
                    var days by rememberSaveable(date) { mutableIntStateOf(3) }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("명절·공장 휴무로 며칠 멈추기", style = MaterialTheme.typography.titleSmall)
                    Text("${Fmt.monthDay(date)}부터 ${days}일 쉬고, 그다음 날 이 날 순서로 이어 가요. 쉬는 날은 비워 둬요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 2.0.0 [RVH-13] 검토 CAL-18: the screen reader says what the buttons do, not "빼기·더하기 기호".
                        TextButton(onClick = { days = (days - 1).coerceAtLeast(1) },
                            modifier = Modifier.semantics { contentDescription = "멈추는 날 하루 줄이기" }) { Text("−") }
                        Text("${days}일", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { days = (days + 1).coerceAtMost(30) },
                            modifier = Modifier.semantics { contentDescription = "멈추는 날 하루 늘리기" }) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.OutlinedButton(onClick = { onPause(days) }) { Text("멈추고 이어 가기") }
                    }
                }
                if (handChanged.isNotEmpty()) {
                    Text("직접 바꾼 날이 있어요", style = MaterialTheme.typography.titleSmall)
                    Text("그 날짜 그대로 둬요. 순서대로 돌릴 날만 고르세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (d in handChanged) {
                        val day = snap.resolver.resolve(d)
                        val on = d.toEpochDay() in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = on, role = Role.Checkbox) {
                                    picked = if (it) picked + d.toEpochDay() else picked - d.toEpochDay()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${Fmt.dayTitle(d)} · ${day.shiftType?.name ?: "쉬는 날"}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(step, clear) }, enabled = push != 0 || clear.isNotEmpty() || step != original) {
                Text(when { push > 0 -> "밀기"; push < 0 -> "당기기"; else -> "다시 맞추기" })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [EASE-25] The [WEEK] days from [date] as they are now and as they will be with [step] on [date]: a day set by hand keeps its
 * own shift unless it is in [clear]. Each day is a badge under its date, as on the calendar.
 */
@Composable
private fun WeekAhead(date: LocalDate, snap: ScheduleSnapshot, rotation: WorkPattern.Rotation, original: Int, step: Int, clear: List<LocalDate>) {
    val n = rotation.steps.size
    fun pattern(i: Int, s: Int) = rotation.steps[Math.floorMod(s + i, n)]?.let { snap.typesById[it] }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("", Modifier.width(44.dp))
            for (i in 0 until WEEK) {
                val d = date.plusDays(i.toLong())
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for ((label, s) in listOf("지금" to original, "바꾸면" to step)) {
            val after = label != "지금"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                for (i in 0 until WEEK) {
                    val d = date.plusDays(i.toLong())
                    val byHand = snap.resolver.resolve(d).takeIf { it.source == com.bamnat.core.schedule.DaySource.OVERRIDE }
                    val type = if (byHand != null && (!after || d !in clear)) byHand.shiftType else pattern(i, s)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ShiftBadge(type, size = 24.dp, text = type?.label ?: "휴", outlined = type == null || type.isOff)
                    }
                }
            }
        }
    }
}

private const val WEEK = 7

/** How far ahead hand-set days are offered for clearing: two months of shifts. */
private const val AHEAD_DAYS = 62L
