package com.bamnat.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.TimeMachine
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.ChoiceRow
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.components.EmptyState
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.SkeletonRows
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 2.0.0 [RVL-76] What going back would change, said before it is done. */
internal fun TimeMachine.Diff.words(part: TimeMachine.Part): String = when (part) {
    TimeMachine.Part.DAYS -> {
        val shown = days.take(3).joinToString(", ") { Fmt.monthDay(it) } + if (days.size > 3) " 외 ${days.size - 3}일" else ""
        listOfNotNull(
            "근무 ${days.size}일이 그때대로 돌아가요".takeIf { days.isNotEmpty() }?.let { "$it ($shown)" },
            "근무 주기도 그때대로예요".takeIf { pattern },
            "근무 종류도 그때대로예요".takeIf { kinds },
        ).joinToString(". ")
    }
    TimeMachine.Part.MONEY -> listOfNotNull(
        "${back}줄이 다시 생겨요".takeIf { back > 0 }, "${gone}줄이 없어져요".takeIf { gone > 0 }, "${changed}줄이 그때대로 바뀌어요".takeIf { changed > 0 },
    ).joinToString(", ").let { if (it.isEmpty()) it else "가계부 기록 $it" }
}

/**
 * 2.0.0 [RVL-76] (검토 I12b-02) 설정 › 데이터 › 날짜별 되돌리기: a calendar's 근무 or its 가계부 back to how it was at a moment of the last
 * 7 days. The moments are listed by day, newest first; tapping one says what would change and asks once. Going back keeps how it is
 * now as a moment too, so it can be undone from the message after it.
 */
@Composable
internal fun TimeMachineDialog(
    machine: TimeMachine,
    calendars: List<PersonalCalendar>,
    /** The calendar the calendar tab shows, chosen first. */
    activeId: String,
    onDone: (message: String, undo: TimeMachine.Point?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listed = calendars.filter { it.ready }.take(4)
    var calendar by rememberSaveable { mutableStateOf(listed.firstOrNull { it.id == activeId }?.id ?: listed.firstOrNull()?.id ?: PersonalCalendar.MAIN) }
    var partIndex by rememberSaveable { mutableIntStateOf(0) }
    val part = TimeMachine.Part.entries[partIndex]
    var points by remember { mutableStateOf<List<TimeMachine.Point>?>(null) }
    var asking by remember { mutableStateOf<Pair<TimeMachine.Point, TimeMachine.Diff>?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(calendar, part) {
        points = null
        points = runCatching { machine.points(calendar, part) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("날짜별 되돌리기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "실수로 바꾼 근무나 가계부를 그때 모습으로 돌려요. 이 휴대폰에만 ${TimeMachine.KEEP_DAYS}일 동안 남아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (listed.size > 1) {
                    ChoiceRow(listed.map { it.name }, listed.indexOfFirst { it.id == calendar }.coerceAtLeast(0), { calendar = listed[it].id }, check = false)
                }
                ChoiceRow(listOf("근무", "가계부"), partIndex, { partIndex = it })
                val list = points
                when {
                    list == null -> SkeletonRows(3, dot = false) // 조건 7: a list being read shows its rows to come
                    list.isEmpty() -> EmptyState(
                        title = "아직 돌아갈 때가 없어요",
                        detail = if (part == TimeMachine.Part.DAYS) "근무를 바꾸면 그 전 모습이 여기 남아요" else "가계부를 바꾸면 그 전 모습이 여기 남아요",
                        mood = KaniMood.Point,
                    )
                    else -> {
                        val today = Today.date()
                        list.groupBy { AlarmScheduler.millisToLocal(it.at).toLocalDate() }.forEach { (day, ofDay) ->
                            Text(dayWord(day, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp))
                            ofDay.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .clickable(enabled = !working, role = Role.Button, onClickLabel = "이때로 되돌리기") {
                                            scope.launch { asking = p to runCatching { machine.diff(p) }.getOrDefault(TimeMachine.Diff()) }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(Fmt.time(AlarmScheduler.millisToLocal(p.at).toLocalTime()) + " 모습",
                                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text("이때로", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
    asking?.let { (p, diff) ->
        val at = AlarmScheduler.millisToLocal(p.at)
        val title = "${Fmt.monthDay(at.toLocalDate())} ${Fmt.time(at.toLocalTime())} 모습으로 되돌릴까요?"
        if (diff.isEmpty) {
            AlertDialog(
                onDismissRequest = { asking = null },
                title = { Text("지금과 같아요") },
                text = { Text("${Fmt.monthDay(at.toLocalDate())} ${Fmt.time(at.toLocalTime())}의 ${if (part == TimeMachine.Part.DAYS) "근무" else "가계부"}는 지금과 같아서 바꿀 것이 없어요.") },
                confirmButton = { TextButton(onClick = { asking = null }) { Text("확인") } },
            )
        } else ConfirmDialog(
            title = title,
            message = diff.words(part) + ". 지금 모습도 남겨 둬서 다시 돌아올 수 있어요.",
            confirmLabel = "되돌리기",
            onDismiss = { asking = null },
            onConfirm = {
                asking = null
                working = true
                scope.launch {
                    val before = runCatching { machine.restore(p) }
                    working = false
                    val said = "${Fmt.monthDay(at.toLocalDate())} ${Fmt.time(at.toLocalTime())} 모습으로 되돌렸어요"
                    before.fold(
                        onSuccess = { onDone(said, it) },
                        onFailure = { onDone("되돌리지 못했어요. 지금 기록은 그대로예요", null) },
                    )
                    points = runCatching { machine.points(calendar, part) }.getOrDefault(emptyList())
                }
            },
        )
    }
}

private fun dayWord(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "오늘"
    today.minusDays(1) -> "어제"
    else -> Fmt.dayTitle(day)
}
