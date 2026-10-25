package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bamnat.app.company.ChangeLine
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.QuickDecimalChips
import com.bamnat.app.ui.components.TimeField
import com.bamnat.core.plaza.CompareRow
import com.bamnat.core.plaza.FactGroup
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.core.plaza.SetupEdit
import java.time.LocalTime

/*
 * [PLAZA-02] 1.25 묶음 X (계획/1.25-X-개발계획.md 386, U2): a company's rule set compared item by item, and
 * 다른 점 말하기 — the rule set's own values in an editor where each value is picked, not typed. What was
 * changed goes as the proposal's lines and the edited file as the rule people vote on.
 */

/** Rows of a comparison: what differs first, marked, the rest folded under "같은 곳 n가지". */
@Composable
fun CompareRows(rows: List<CompareRow>, beforeName: String, afterName: String) {
    var same by rememberSaveable { mutableStateOf(false) }
    val diff = rows.filter { !it.same }
    val kept = rows.filter { it.same }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (diff.isEmpty()) Text("다른 곳이 없어요. 두 설정이 같아요.", style = MaterialTheme.typography.bodyMedium)
        diff.groupBy { it.group }.forEach { (group, list) ->
            Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            list.forEach { CompareLine(it, beforeName, afterName) }
        }
        if (kept.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { same = !same }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (same) "같은 곳 접기" else "같은 곳 ${kept.size}가지 보기", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(if (same) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (same) kept.forEach { SetupLine(it.label, it.after.orEmpty()) }
        }
    }
}

@Composable
private fun CompareLine(r: CompareRow, beforeName: String, afterName: String) {
    val spoken = when (r.state) {
        CompareRow.State.ADDED -> "${r.label}: $afterName 에만 있어요, ${r.after}"
        CompareRow.State.REMOVED -> "${r.label}: $beforeName 에만 있어요, ${r.before}"
        else -> "${r.label}: $beforeName ${r.before}, $afterName ${r.after}"
    }
    SoftCard(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.label, style = MaterialTheme.typography.titleSmall)
            Text("$beforeName: ${r.before ?: "없음"}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (r.state == CompareRow.State.CHANGED || r.state == CompareRow.State.REMOVED) TextDecoration.LineThrough else null)
            Text("$afterName: ${r.after ?: "없음"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Two rule sets side by side in a sheet; [action] is what can be done with the second (쓰기, 되돌리기). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareSheet(
    title: String,
    before: String,
    after: String,
    beforeName: String,
    afterName: String,
    onDismiss: () -> Unit,
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val rows = remember(before, after) { SetupCompare.rows(before, after) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            SetupCompare.summary(rows)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            CompareRows(rows, beforeName, afterName)
            action?.invoke()
        }
    }
}

/**
 * 다른 점 말하기: [base] (the rule set as the plaza has it) in an editor. [mine] is this phone's own setup, for
 * "내 설정으로 채우기". [onSend] gets the proposal's lines, the edited file and the reason, if any.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiffEditorSheet(
    title: String,
    base: String,
    mine: String?,
    busy: Boolean,
    error: String?,
    onSend: (lines: List<ChangeLine>, setup: String, why: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember(base) { SetupEdit.of(base) }
    var edit by remember(base) { mutableStateOf(start) }
    var why by rememberSaveable(base) { mutableStateOf("") }
    var comparing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val e = edit
        if (start == null || e == null) {
            Text("이 설정은 고칠 수 없어요. 토론에 글로 적어 주세요.", Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        val rows = remember(e) { SetupCompare.rows(base, e.text) }
        val changed = rows.filter { !it.same }
        val changedGroups = changed.map { it.group }.toSet()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding()) {
            Text("다른 점 말하기", style = MaterialTheme.typography.titleLarge)
            Text("$title · 이 회사 값으로 채워 두었어요. 우리와 다른 곳만 바꿔 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The count on its own line and the two buttons flowing under it, so large text never squeezes the count.
            FlowRow(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(if (changed.isEmpty()) "아직 바꾼 곳이 없어요" else "바꾼 곳 ${changed.size}가지",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                    color = if (changed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                if (mine != null) TextButton(onClick = { SetupEdit.of(mine)?.let { m -> edit = m } }) { Text("내 설정으로 채우기") }
                if (changed.isNotEmpty()) TextButton(onClick = { edit = start }) { Text("처음대로") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupEditor(e, changedGroups) { edit = it }
                if (changed.isNotEmpty()) {
                    TextButton(onClick = { comparing = !comparing }) { Text(if (comparing) "비교 접기" else "바꾼 곳 비교해 보기") }
                    if (comparing) changed.forEach { CompareLine(it, "지금", "바꿀 값") }
                }
                // The reason and the note scroll with the editor, so large text still leaves room to edit; only the send stays.
                OutlinedTextField(
                    why, { why = it.take(160) }, singleLine = false, maxLines = 3,
                    label = { Text("왜 다른지 (선택)") }, placeholder = { Text("예: 10월부터 주간이 한 시간 줄었어요") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("보내면 토론에 ‘고치자는 제안’으로 올라가요. 같은 부서 분들이 맞아요를 누르면 이 설정이 다음 버전이 돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            Button(
                onClick = { onSend(proposalChange(rows), e.text, why.trim()) },
                enabled = changed.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("제안 보내기")
            }
            if (changed.isEmpty()) {
                Text("우리와 다른 값을 하나 이상 바꾸면 보낼 수 있어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** The proposal's lines from the compare rows, in the server's fields (at most six, each under 80 letters). */
fun proposalChange(rows: List<CompareRow>): List<ChangeLine> =
    SetupCompare.proposalLines(rows).map { ChangeLine(it.field, it.now, it.want, "") }

/** What a proposal says in its post when the user wrote no reason: "다른 점 2가지: 주간 · 야간". */
fun proposalBody(rows: List<CompareRow>, why: String): String = why.ifBlank { SetupCompare.summary(rows) ?: "설정이 달라요" }

private val RATE_KEYS = listOf("ot" to "연장", "night" to "야간", "hw" to "휴일", "ho" to "휴일 8시간 넘게")

/** Each part of the file with its own pickers; a part with a change gets a coloured card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupEditor(e: SetupEdit, changed: Set<FactGroup>, onChange: (SetupEdit) -> Unit) {
    // The order's days cycle through every shift, 휴무 too, then 쉼.
    val names = e.shifts.map { it.name }
    Part(FactGroup.SHIFT, changed) {
        e.shifts.filter { !it.off }.forEach { s ->
            Text(s.name, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("${s.name} 시작", s.start?.let(::time), { t -> t?.let { onChange(e.shiftTime(s.name, hhmm(it), s.end ?: hhmm(it), s.breakMinutes)) } },
                    Modifier.weight(1f))
                TimeField("${s.name} 끝", s.end?.let(::time), { t -> t?.let { onChange(e.shiftTime(s.name, s.start ?: hhmm(it), hhmm(it), s.breakMinutes)) } },
                    Modifier.weight(1f))
            }
            QuickDecimalChips(
                s.breakMinutes.toDouble(), { m -> onChange(e.shiftTime(s.name, s.start ?: "09:00", s.end ?: "18:00", m.toInt())) },
                name = "${s.name} 휴게", quick = listOf(0.0, 30.0, 60.0, 90.0), step = 10.0, range = 0.0..240.0, unit = "분",
            )
        }
    }
    Part(FactGroup.CYCLE, changed) {
        val rotation = e.rotation
        val weekly = e.weekly
        when {
            rotation != null -> {
                Text("한 칸을 누르면 다음 근무로 바뀌어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rotation.forEachIndexed { i, step ->
                        FilterChip(selected = step != null, onClick = { onChange(e.rotation(rotation.toMutableList().also { it[i] = next(names, step) })) },
                            label = { Text("${i + 1}일 ${step ?: "쉼"}") })
                    }
                }
                Row {
                    IconButton(onClick = { onChange(e.rotation(rotation.dropLast(1))) }, enabled = rotation.size > 1) { Icon(Icons.Filled.Remove, "순서 하루 빼기") }
                    Text("${rotation.size}일 순서", Modifier.align(Alignment.CenterVertically))
                    IconButton(onClick = { onChange(e.rotation(rotation + null)) }, enabled = rotation.size < 56) { Icon(Icons.Filled.Add, "순서 하루 늘리기") }
                }
            }
            weekly != null -> {
                Text("요일을 누르면 다음 근무로 바뀌어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
                        FilterChip(selected = weekly[i] != null, onClick = { onChange(e.weekly(weekly.toMutableList().also { it[i] = next(names, weekly[i]) })) },
                            label = { Text("$d ${weekly[i] ?: "쉼"}") })
                    }
                }
            }
            else -> Text("매달 근무표로 일해요. 순서는 없어요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!e.hasPay) {
        Text("급여 계산 방식이 없는 설정이에요. 근무 시각과 순서만 고칠 수 있어요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Part(FactGroup.PLACE, changed) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = e.fiveOrMore, onClick = { onChange(e.fiveOrMore(true)) }, label = { Text("5인 이상") })
            FilterChip(selected = !e.fiveOrMore, onClick = { onChange(e.fiveOrMore(false)) }, label = { Text("5인 미만") })
        }
    }
    Part(FactGroup.RATE, changed) {
        RATE_KEYS.forEach { (key, label) ->
            val v = e.rate(key)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = v == null, onClick = { onChange(e.rate(key, null)) }, label = { Text("법대로") })
                Spacer(Modifier.width(8.dp))
                QuickDecimalChips(v?.let { it * 100 }, { p -> onChange(e.rate(key, p / 100)) }, name = "$label 가산",
                    quick = emptyList(), step = 10.0, range = 0.0..300.0, unit = "%", start = 50.0)
            }
        }
        val window = e.nightWindow ?: ("22:00" to "06:00")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("야간 시작", time(window.first), { t -> t?.let { onChange(e.nightWindow(hhmm(it), window.second)) } }, Modifier.weight(1f))
            TimeField("야간 끝", time(window.second), { t -> t?.let { onChange(e.nightWindow(window.first, hhmm(it))) } }, Modifier.weight(1f))
        }
    }
    Part(FactGroup.RULE, changed) {
        Text("쉬는 날 근무", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LAW" to "법대로", "HOLIDAY" to "휴일근로로", "OVERTIME" to "연장근로로").forEach { (k, l) ->
                FilterChip(selected = e.restDay == k, onClick = { onChange(e.restDay(k)) }, label = { Text(l) })
            }
        }
        Text("주휴일", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("FIRST_REST" to "쉬는 날 중 첫날") + DAYS.map { (k, d) -> k to "${d}요일" }).forEach { (k, l) ->
                FilterChip(selected = e.weeklyDay == k, onClick = { onChange(e.weeklyDay(k)) }, label = { Text(l) })
            }
        }
    }
    if (e.kindRules.isNotEmpty()) Part(FactGroup.KIND, changed) {
        e.kindRules.forEach { k ->
            Text(k.name, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("LEGAL" to "법대로", "HOURLY_RATE" to "통상시급 ×", "PERCENT_OF_BASE" to "기본급 %").forEach { (b, l) ->
                    FilterChip(selected = k.basis == b, onClick = { onChange(e.kindRule(k.id, b)) }, label = { Text(l) })
                }
            }
            when (k.basis) {
                "HOURLY_RATE" -> QuickDecimalChips(k.multiplier * 100, { p -> onChange(e.kindRule(k.id, k.basis, multiplier = p / 100)) },
                    name = "${k.name} 배율", quick = listOf(100.0, 150.0, 200.0), step = 10.0, range = 0.0..500.0, unit = "%")
                "PERCENT_OF_BASE" -> QuickDecimalChips(k.percent * 100, { p -> onChange(e.kindRule(k.id, k.basis, percent = p / 100)) },
                    name = "${k.name} 기본급 비율", quick = listOf(5.0, 10.0, 15.0), step = 1.0, range = 0.0..100.0, unit = "%")
            }
        }
    }
    if (e.items.isNotEmpty()) Part(FactGroup.ITEM, changed) {
        e.items.forEach { (name, cadence) ->
            Text(name, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("MONTHLY" to "매달", "PER_WORK_DAY" to "근무일마다", "PER_NIGHT_SHIFT" to "야간마다", "BONUS" to "정한 달").forEach { (k, l) ->
                    FilterChip(selected = cadence == k, onClick = { onChange(e.itemCadence(name, k)) }, label = { Text(l) })
                }
            }
        }
        Text("금액은 사람마다 달라서 보내지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val DAYS = listOf("MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목", "FRIDAY" to "금", "SATURDAY" to "토", "SUNDAY" to "일")

@Composable
private fun Part(group: FactGroup, changed: Set<FactGroup>, content: @Composable () -> Unit) {
    val on = group in changed
    SoftCard(highlighted = on) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (on) StatusChip("바꿈", Tone.WAIT)
            }
            content()
        }
    }
}

/** The next shift name after [now] in [names], then 쉼 (null), then the first again. */
internal fun next(names: List<String>, now: String?): String? {
    if (names.isEmpty()) return null
    val i = names.indexOf(now)
    return if (now == null) names.first() else if (i < 0 || i == names.lastIndex) null else names[i + 1]
}

private fun time(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()

private fun hhmm(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
