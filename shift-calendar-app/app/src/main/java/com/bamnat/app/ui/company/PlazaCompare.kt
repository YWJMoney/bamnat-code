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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.bamnat.app.appContainer
import com.bamnat.app.company.ChangeLine
import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.ItemShare
import kotlinx.coroutines.launch
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
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
 * [ITEM-08] 1.30 BC: [draft] opens it already changed (a [com.bamnat.core.plaza.ProposalDraft], e.g. the 통상시급 hours the user
 * changed), with its reason; the user only presses 보내기. [PROP-03] [alsoMine]: the rule set is the one the user's pay uses,
 * so "내 급여도 이 값으로" is offered (on by default) and what was changed goes onto the user's own pay as it is sent.
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
    draft: com.bamnat.core.plaza.ProposalDraft? = null,
    alsoMine: Boolean = false,
    /** [J133-82] The company has a pay way this phone may not see (결정 D79): the user is told so, and it stays as it is. */
    payHidden: Boolean = false,
) {
    val start = remember(base) { SetupEdit.of(base) }
    var edit by remember(base, draft) { mutableStateOf(draft?.let { SetupEdit.of(it.text) } ?: start) }
    var why by rememberSaveable(base) { mutableStateOf(draft?.why.orEmpty()) }
    var comparing by remember { mutableStateOf(false) }
    var takeMine by rememberSaveable(base) { mutableStateOf(true) }
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // [ITEM-03] 결정 D102: the company's one amounts go only while the plaza.fixed_amount row is on. [FIN130-02] A flag that
    // cannot be read is off, as the flag itself is until 처리방침 3.4.
    val amounts = remember { runCatching { c.flags.on(com.bamnat.core.flags.FlagCatalog.PLAZA_FIXED_AMOUNT) }.getOrDefault(false) }
    // [FIN130-02] 내 급여도 이 값으로 goes onto the pay of the calendar on screen, never 내 달력's from another calendar's plaza.
    val mineScope = companyScope(c)
    // [J133-82] 1.33 광장 급여 써 보기 ("끌어내리니 바꾼 게 다 사라졌어요"): a swipe, the X, a tap outside or back ask first
    // once something is changed, as the month's typing sheet does.
    val mineEdit = remember(mine, payHidden) { mine?.let(SetupEdit::of)?.let { if (payHidden) it.withoutPay() else it } }
    var touched by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { v ->
        v != androidx.compose.material3.SheetValue.Hidden || (!busy && (!touched || false.also { asking = true }))
    })
    val close = { if (busy) Unit else if (touched) asking = true else onDismiss() }
    if (asking) com.bamnat.app.ui.components.ConfirmDialog(
        title = "바꾼 곳을 버릴까요?",
        message = "보내지 않고 닫으면 지금 바꾼 값이 사라져요.",
        confirmLabel = "버리고 닫기",
        onDismiss = { asking = false },
        onConfirm = { asking = false; touched = false; onDismiss() },
        destructive = true,
    )
    // [J133-90] 빌드 7·8 확인 ("뒤로 키·뒤로 제스처는 묻지 않고 닫혀 값이 사라져요"): the sheet's own back (predictive back too) hides
    // it and then asks to be dismissed without the swipe's check, so here the sheet comes back up and the question is asked.
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = {
        when {
            busy -> scope.launch { state.show() }
            touched -> { asking = true; scope.launch { state.show() } }
            else -> onDismiss()
        }
    }, sheetState = state, onClose = close) {
        val e = edit
        if (start == null || e == null) {
            Text("이 설정은 고칠 수 없어요. 토론에 글로 적어 주세요.", Modifier.padding(24.dp))
            return@AppSheet
        }
        val rows = remember(e) { SetupCompare.rows(base, e.text) }
        val changed = rows.filter { !it.same }
        val changedGroups = changed.map { it.group }.toSet()
        androidx.compose.runtime.SideEffect { touched = changed.isNotEmpty() }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding()) {
            Text("다른 점 말하기", style = MaterialTheme.typography.titleLarge)
            Text("$title · 이 회사 값으로 채워 두었어요. 우리와 다른 곳만 바꿔 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The count on its own line and the two buttons flowing under it, so large text never squeezes the count.
            FlowRow(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(if (changed.isEmpty()) "아직 바꾼 곳이 없어요" else "바꾼 곳 ${changed.size}가지",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                    color = if (changed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                if (mineEdit != null) TextButton(onClick = { edit = mineEdit }) { Text("내 설정으로 채우기") }
                if (changed.isNotEmpty()) TextButton(onClick = { edit = start }) { Text("처음대로") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupEditor(e, changedGroups, amounts = amounts, baseHours = start.monthlyHours, payHidden = payHidden,
                    myPay = mineEdit?.takeIf { it.hasPay }) { edit = it }
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
            // [PROP-03] 1.30 BC: the user's own pay follows what they said, sent or not (a refused post never blocks the pay).
            if (alsoMine && changed.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { takeMine = !takeMine }.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = takeMine, onCheckedChange = { takeMine = it })
                    Text("내 급여도 이 값으로 계산해요", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = {
                    if (alsoMine && takeMine) {
                        val text = e.text
                        scope.launch {
                            val n = runCatching { mineScope.share.takeEdited(base, text) }.getOrDefault(0)
                            c.telemetry.event("plaza_diff", if (n > 0) "mine_taken" else "mine_same")
                        }
                    }
                    onSend(proposalChange(rows), e.text, why.trim())
                },
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

/**
 * Each part of the file with its own pickers; a part with a change gets a coloured card. [amounts]: the company's one amount
 * (회사 공통) can be written (결정 D102). [baseHours]: the company's 통상시급 hours, the first chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupEditor(
    e: SetupEdit,
    changed: Set<FactGroup>,
    amounts: Boolean = true,
    baseHours: Int? = null,
    /** [J133-82] The company's pay way is there but not for this phone's eyes (결정 D79). */
    payHidden: Boolean = false,
    /** [J133-82] The user's own setup with its pay, to give a shifts-only setup its first pay way in one tap. */
    myPay: SetupEdit? = null,
    onChange: (SetupEdit) -> Unit,
) {
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
    if (payHidden && !e.hasPay) {
        // [J133-82] 1.33 광장 급여 써 보기: it had said there was no pay way while the card said there was one.
        SoftCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("급여 계산 방식은 이 회사를 쓰는 분께만 보여요", style = MaterialTheme.typography.titleSmall)
                Text("근무 시각·순서를 고쳐 보내도 회사의 급여 방식은 그대로 남아요. 가산율·수당까지 고치려면 ‘이 설정 쓰기’로 이 회사를 쓰고 다시 열어 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    if (!e.hasPay) {
        // [J133-82] 1.33 광장 급여 써 보기 ("급여 방식이 없는 회사에는 더할 길이 없어요"): the first pay way, from the user's own or the law.
        SoftCard(highlighted = true) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("이 설정엔 아직 급여 계산 방식이 없어요", style = MaterialTheme.typography.titleSmall)
                Text("처음 알려 주시면 같은 회사 분들이 월급을 바로 계산할 수 있어요. 금액은 담기지 않고, 가산율·수당 이름 같은 방식만 올라가요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (myPay != null) Button(onClick = { onChange(e.withPayOf(myPay)) }, modifier = Modifier.fillMaxWidth()) { Text("내 급여 설정으로 채우기") }
                OutlinedButton(onClick = { onChange(e.withLawPay(myPay?.fiveOrMore ?: true)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("법대로 시작해서 고치기")
                }
            }
        }
        return
    }
    Part(FactGroup.PLACE, changed) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = e.fiveOrMore, onClick = { onChange(e.fiveOrMore(true)) }, label = { Text("5인 이상") })
            FilterChip(selected = !e.fiveOrMore, onClick = { onChange(e.fiveOrMore(false)) }, label = { Text("5인 미만") })
        }
        // [ITEM-10] 1.30 BC: 통상시급 기준 시간 (LG화학 180시간 같은), the company's first, then the usual ones.
        Text("통상시급 기준 시간 (한 달)", style = MaterialTheme.typography.titleSmall)
        QuickDecimalChips(
            e.monthlyHours?.toDouble(), { h -> onChange(e.monthlyHours(h.toInt())) }, name = "통상시급 기준 시간",
            quick = (listOfNotNull(baseHours) + com.bamnat.core.pay.Unknowns.MONTHLY_HOURS_CHIPS).distinct().map { it.toDouble() },
            step = 1.0, range = SetupEdit.HOURS_MIN.toDouble()..SetupEdit.HOURS_MAX.toDouble(), unit = "시간",
            chipLabel = { h -> if (baseHours != null && h.toInt() == baseHours) "지금 ${h.toInt()}시간" else "${h.toInt()}시간" },
        )
        Text("명세서의 ‘통상시급’이 (기본급 + 통상 수당) ÷ 몇 시간인지예요. 모르면 그대로 두세요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // [HOLI-01] 빨간날 근무, the four ways of the pay setup (1.25 V); the amounts a time stay each person's own.
        if (e.fiveOrMore) {
            Text("빨간날 근무", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bamnat.core.pay.HolidayWork.entries.forEach { m ->
                    FilterChip(selected = e.holidayWork == m, onClick = { onChange(e.holidayWork(m)) }, label = { Text(m.label) })
                }
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
    // [ITEM-10] 1.30 BC (사용자 2026-10-27 "수당등등을 추가 삭제할수 있게"): every allowance with its name, way, who sets it, 통상임금
    // and a delete; new ones from the usual names or typed.
    Part(FactGroup.ITEM, changed) {
        e.allowances.forEach { a -> AllowanceEditor(e, a, amounts, onChange) }
        if (e.allowances.isEmpty()) Text("아직 수당이 없어요. 아래에서 더해 주세요.", style = MaterialTheme.typography.bodyMedium)
        AddAllowance(e, onChange)
        Text(if (amounts) "‘회사 공통 금액’ 수당만 금액이 함께 가요. 사람마다 다른 금액은 보내지 않아요." else "금액은 보내지 않아요. 쓰는 분이 각자 적어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [ITEM-10] One allowance in the proposal form: its name (고치기), how often, who sets the amount, 통상임금, and 빼기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowanceEditor(e: SetupEdit, a: SetupEdit.Allowance, amounts: Boolean, onChange: (SetupEdit) -> Unit) {
    var renaming by remember(a.name) { mutableStateOf(false) }
    var newName by remember(a.name) { mutableStateOf(a.name) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (renaming) {
                OutlinedTextField(newName, { newName = it.take(SetupEdit.MAX_ITEM_NAME) }, singleLine = true, label = { Text("수당 이름") },
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { renaming = false; onChange(e.renameItem(a.name, newName)) }, enabled = newName.isNotBlank()) { Text("바꾸기") }
            } else {
                Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { renaming = true }) { Text("이름 고치기") }
                IconButton(onClick = { onChange(e.removeItem(a.name)) }) { Icon(Icons.Filled.Delete, "‘${a.name}’ 빼기") }
            }
        }
        if (a.share != ItemShare.PERCENT) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("MONTHLY" to "매달", "PER_WORK_DAY" to "근무일마다", "PER_NIGHT_SHIFT" to "야간마다", "BONUS" to "정한 달").forEach { (k, l) ->
                FilterChip(selected = a.cadence == k, onClick = { onChange(e.itemCadence(a.name, k)) }, label = { Text(l) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemShare.entries.forEach { s ->
                FilterChip(selected = a.share == s, onClick = { onChange(e.itemShare(a.name, s)) }, label = { Text(s.label) })
            }
        }
        when (a.share) {
            ItemShare.PERCENT -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BonusBasis.entries.forEach { b ->
                        FilterChip(selected = a.percentOf == b, onClick = { onChange(e.itemShare(a.name, ItemShare.PERCENT, percentOf = b)) }, label = { Text("${b.label}의") })
                    }
                }
                QuickDecimalChips((a.percent ?: SetupEdit.DEFAULT_PERCENT) * 100, { p -> onChange(e.itemShare(a.name, ItemShare.PERCENT, percent = p / 100)) },
                    name = "${a.name} 비율", quick = listOf(5.0, 10.0, 15.0, 20.0), step = 0.5, range = 0.5..500.0, unit = "%")
            }
            ItemShare.FIXED -> if (amounts) {
                com.bamnat.app.ui.pay.MoneyField("${a.name} 회사 공통 금액 (선택)", a.fixedAmount, { v ->
                    onChange(e.itemShare(a.name, ItemShare.FIXED, fixedAmount = v ?: 0))
                }, placeholder = "모두 같은 금액이면 적어 주세요")
            }
            ItemShare.PERSONAL -> Text("금액은 쓰는 분이 각자 적어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("통상임금에 넣어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = a.ordinary, onCheckedChange = { onChange(e.itemOrdinary(a.name, it)) },
                modifier = Modifier.semantics { contentDescription = "‘${a.name}’ 통상임금에 넣기" })
        }
    }
}

/** [ITEM-10] The usual allowance names, a tap each; anything else is typed. */
private val USUAL_ITEMS = listOf("식대", "교통비", "근속수당", "자격수당", "직책수당", "위험수당", "교대수당", "기본급2", "위험예지")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAllowance(e: SetupEdit, onChange: (SetupEdit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val have = e.allowances.map { it.name }.toSet()
    Text("수당 더하기", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        USUAL_ITEMS.filter { it !in have }.forEach { n ->
            androidx.compose.material3.AssistChip(onClick = { onChange(e.addItem(n)) }, label = { Text("+ $n") })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it.take(SetupEdit.MAX_ITEM_NAME) }, singleLine = true, label = { Text("다른 이름") },
            placeholder = { Text("예: 기술전수교육수당") }, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(e.addItem(name)); name = "" }, enabled = name.isNotBlank() && name.trim() !in have) { Text("더하기") }
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
