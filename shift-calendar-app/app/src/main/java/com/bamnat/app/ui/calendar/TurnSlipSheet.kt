package com.bamnat.app.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.TurnSlipCheck
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AppSheet
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.ChoiceRow
import com.bamnat.app.ui.components.Kani
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.LocalShowKani
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.words
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 2.0.0 [RVH-64] 검토 PC-72 '이 날 맞아요?' (설계 4절): each line with what it holds, the day it may have been meant for, and 맞아요 ·
 * 11/19로 · 빼기 to pick (맞아요 first, so 이대로 할게요 alone changes nothing). Days to come first, a day whose alarm went off first among
 * them; days gone by and memos·가계부 fold under a line each. One filled button. Afterwards 칸이 says it is done, with 되돌리기 for
 * what was changed, all at once, on the sheet and on the bar under it once it closes (조건 10). What was picked stays in
 * [TurnSlipAsk] while the app runs, so closing the sheet by X, back or a swipe loses nothing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TurnSlipSheet(
    rows: List<TurnSlipAsk.Row>,
    today: LocalDate,
    style: WorkStyle?,
    onApply: suspend (Map<TurnSlipCheck.Found, TurnSlipCheck.Choice>) -> TurnSlipCheck.Done,
    onUndo: suspend (TurnSlipCheck.Done) -> Unit,
    onAnswered: (TurnSlipCheck.Done) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val picks = TurnSlipAsk.picks
    val done by TurnSlipAsk.finished.collectAsState()
    val undone by TurnSlipAsk.undone.collectAsState()
    var asking by remember { mutableStateOf<List<TurnSlipAsk.Row>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val (coming, rest) = remember(rows, today) { rows.partition { it.isDay && !it.line.date.isBefore(today) } }
    val (gone, notes) = remember(rest) { rest.partition { it.isDay } }
    val dayChanged = rows.any { it.isDay && picks[it.found].let { c -> c != null && c != TurnSlipCheck.Choice.KEEP } }

    fun apply() {
        if (busy) return
        busy = true
        scope.launch {
            val d = try { onApply(picks.toMap()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                TurnSlipCheck.Done(emptyList(), missed = picks.values.count { it != TurnSlipCheck.Choice.KEEP })
            }
            onAnswered(d)
            busy = false
        }
    }

    fun undo(d: TurnSlipCheck.Done) {
        if (busy || !TurnSlipAsk.markUndone()) return
        busy = true
        scope.launch { runCatching { onUndo(d) }; busy = false }
    }

    // The way out after it: 닫기, X, back, a swipe; what was changed can still be undone from the bar for a moment (조건 10).
    fun close(d: TurnSlipCheck.Done) {
        if (d.count > 0 && !undone) AppSnack.show("지난 판 확인으로 ${d.count}개를 바꿨어요", undo = { if (TurnSlipAsk.markUndone()) onUndo(d) })
        onDone()
    }

    // Opened all the way: 이대로 할게요 at its foot is never under the screen's edge, at any 글자 크기 (조건 1·12).
    AppSheet(onDismissRequest = { done?.let(::close) ?: onDismiss() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val finished = done
        // One filled button at the foot in both states, in the same place: 이대로 할게요 to answer, 닫기 once it is answered (조건 2).
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (finished != null) {
                // 조건 16: the end said in 칸이's words, with the way back for what was changed (조건 10).
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (LocalShowKani.current) Kani(KaniMood.Done, size = 72.dp)
                    Text(
                        when {
                            undone -> "되돌렸어요. 처음 그대로예요."
                            finished.count == 0 && finished.missed == 0 -> "확인했어요. 그대로 둘게요."
                            finished.count == 0 -> "확인했어요."
                            else -> "확인했어요. ${finished.count}개를 바꿨어요."
                        },
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (finished.missed > 0 && !undone) ToneLine("${finished.missed}개는 바꾸지 못했어요. 그 날을 눌러 직접 고쳐 주세요.", tone = Tone.Caution)
                    if (finished.count > 0 && dayChanged && !undone) Text("바꾼 날은 달력 도구 › 최근 바꾼 기록에 모여 있어요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (finished.count > 0 && !undone) TextButton(onClick = { undo(finished) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("되돌리기") }
                }
            } else {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("이 날 맞아요?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Why()
                    coming.forEach { RowItem(it, picks, style) }
                    if (gone.isNotEmpty()) Fold("지난 날 ${gone.size}개", "지난 날") { gone.forEach { RowItem(it, picks, style) } }
                    if (notes.isNotEmpty()) Fold("${notesWord(style)} ${notes.size}개", notesWord(style)) { notes.forEach { RowItem(it, picks, style) } }
                    Spacer(Modifier.size(4.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Button(
                onClick = {
                    if (finished != null) close(finished)
                    else {
                        val replacing = rows.filter { picks[it.found] == TurnSlipCheck.Choice.MOVE && it.meantHolds != null }
                        if (replacing.isNotEmpty()) asking = replacing else apply()
                    }
                },
                enabled = finished != null || !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp),
            ) { Text(if (finished != null) "닫기" else "이대로 할게요") }
        }
    }

    // 조건 10: a day meant that holds something already is told once more before it is replaced.
    asking?.let { list ->
        com.bamnat.app.ui.components.AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("옮길 날에 넣은 것이 바뀌어요") },
            text = {
                Text(list.joinToString("\n") { r -> "${Fmt.dayTitle(r.line.meant)}: ‘${r.meantHolds}’ 대신 ‘${r.what}’" })
            },
            confirmButton = { TextButton(onClick = { asking = null; apply() }) { Text("옮길게요") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("다시 볼게요") } },
        )
    }
}

/** '메모·가계부' in the kind of work's words (투자 keeps 기록, not 가계부). */
private fun notesWord(style: WorkStyle?): String = if (style == WorkStyle.INVEST) "메모·기록" else "메모·가계부"

/** One line + ⓘ (조건 4): why it is asked, and on ⓘ which phones it happened on and that 2.0.0 fixed it. */
@Composable
private fun Why() {
    var open by rememberSaveable { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지난 판에서 달을 넘긴 뒤 누른 날이 한 달 뒤 같은 자리에 들어갔을 수 있어요.", style = MaterialTheme.typography.bodyMedium,
                color = ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { open = !open }) {
                Icon(Icons.Outlined.Info, contentDescription = if (open) "설명 접기" else "설명 보기", tint = ink, modifier = Modifier.size(20.dp))
            }
        }
        if (open) Text(
            "달 넘김이 스르륵·책장 넘기기·움직이지 않기였거나, 움직임 끄기나 휴대폰의 애니메이션 끄기가 켜져 있던 폰에서 생겼어요. " +
                "2.0.0에서 고쳤어요. 맞는 날이면 그대로 두면 돼요.",
            style = MaterialTheme.typography.bodySmall, color = ink,
        )
    }
}

/** A folded group: its count on one line, opened by a tap (조건 1: what is still to come stays on top). */
@Composable
private fun Fold(title: String, name: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "$name 접기" else "$name 펼치기")
        }
        if (open) content()
    }
}

@Composable
private fun RowItem(row: TurnSlipAsk.Row, picks: MutableMap<TurnSlipCheck.Found, TurnSlipCheck.Choice>, style: WorkStyle?) {
    val line = row.line
    val meant = "${line.meant.monthValue}/${line.meant.dayOfMonth}"
    val options = if (row.isDay) listOf("맞아요", "${meant}로", "빼기") else listOf("맞아요", "${meant}로")
    val said = listOf("이 날이 맞아요, 그대로 두기", "${line.meant.monthValue}월 ${line.meant.dayOfMonth}일로 옮기기", "${style.words().pattern}대로 되돌리기")
    val choices = listOf(TurnSlipCheck.Choice.KEEP, TurnSlipCheck.Choice.MOVE, TurnSlipCheck.Choice.CLEAR)
    val picked = choices.indexOf(picks[row.found] ?: TurnSlipCheck.Choice.KEEP)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${Fmt.dayTitle(line.date)} · ${row.what}", style = MaterialTheme.typography.titleSmall)
        // 조건 6: one orange mark on a line, for the day whose alarm will not ring.
        if (row.alarmOff) ToneLine("그날 알람이 안 울려요", tone = Tone.Caution)
        Text("${Fmt.dayTitle(line.meant)}에 넣으려던 것일 수 있어요", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        row.meantHolds?.let { ToneLine("${line.meant.monthValue}월 ${line.meant.dayOfMonth}일에 넣어 둔 것: ‘$it’ · 옮기면 바뀌어요", tone = Tone.Plain) }
        ChoiceRow(options, picked, onSelect = { picks[row.found] = choices[it] }, descriptions = said.take(options.size))
    }
}
