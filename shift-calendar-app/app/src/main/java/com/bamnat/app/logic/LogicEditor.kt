@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bamnat.app.logic

import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.bamnat.app.ui.components.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.EasyCond
import com.bamnat.core.formula.EasyWhen
import com.bamnat.core.formula.Formula
import com.bamnat.core.formula.FormulaName
import com.bamnat.core.formula.FormulaBlocks
import com.bamnat.core.formula.FormulaNames
import com.bamnat.core.formula.FormulaScope
import com.bamnat.core.formula.FormulaText
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.Parsed
import com.bamnat.core.formula.RuleTrigger
import java.time.LocalDate

/**
 * [RUL-06] 1.36 886 수식·규칙 편집기 (사용자 2026-11-01 "다중 조건식도 가능하게, 다만 사용자가 편하게"): one condition in two views that
 * open the same value. 쉬운 보기 is a table of picks — [근무] [이(가)] [야간] — with rows joined by 그리고 or 또는; 고급 보기 is the formula
 * itself, with names to tap in (자동 완성), the place of a mistake underlined and said, and what it gives on a real day.
 */
@Composable
fun ConditionEditor(value: String, onChange: (String) -> Unit, scope: FormulaScope, modifier: Modifier = Modifier, empty: String = "언제나") {
    val easy = remember(value) { EasyWhen.read(value) }
    // 누락 점검 N7: 쉬운 보기 · 블록 · 수식으로, 세 가지가 같은 값. 표로 못 나타내는 수식은 블록으로 먼저 열어요.
    var view by remember { mutableIntStateOf(if (easy != null) 0 else if (FormulaBlocks.read(value) != null) 1 else 2) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = view == 0 && easy != null, onClick = { if (easy != null) view = 0 }, enabled = easy != null || view == 0,
                shape = SegmentedButtonDefaults.itemShape(0, 3)) { Text("쉬운 보기") }
            SegmentedButton(selected = view == 1, onClick = { view = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 3)) { Text("블록") }
            SegmentedButton(selected = view == 2 || (view == 0 && easy == null), onClick = { view = 2 }, shape = SegmentedButtonDefaults.itemShape(2, 3)) { Text("수식으로") }
        }
        if (view == 1) {
            BlockEditor(value, onChange, scope)
            PreviewLine(value, scope)
        } else if (view == 2 || easy == null) {
            FormulaField(value, onChange, scope)
            if (easy == null && value.isNotBlank()) Text("이 수식은 표로 나타낼 수 없어 블록이나 수식으로 보여요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            EasyTable(easy, { onChange(it.toFormula().let { f -> if (it.conds.isEmpty()) "" else f }) }, empty)
            PreviewLine(value, scope)
        }
    }
}

@Composable
private fun EasyTable(w: EasyWhen, onChange: (EasyWhen) -> Unit, empty: String) {
    if (w.conds.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    w.conds.forEachIndexed { i, c ->
        if (i > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !w.any, onClick = { onChange(w.copy(any = false)) }, label = { Text("그리고") })
            FilterChip(selected = w.any, onClick = { onChange(w.copy(any = true)) }, label = { Text("또는") })
        }
        CondRow(c, { n -> onChange(w.copy(conds = w.conds.toMutableList().also { it[i] = n })) },
            { onChange(w.copy(conds = w.conds.filterIndexed { j, _ -> j != i })) })
    }
    TextButton(onClick = { onChange(w.copy(conds = w.conds + EasyCond(FormulaName.SHIFT.ko, EasyCond.Op.IS, ""))) }) {
        Text(if (w.conds.isEmpty()) "+ 조건 넣기" else "+ 조건 더하기")
    }
}

/** One row: [값 고르기] [어떻게] [무엇]. The value list follows the name (근무종류 → 주간·야간…, 요일 → 월~일). */
@Composable
private fun CondRow(c: EasyCond, onChange: (EasyCond) -> Unit, onRemove: () -> Unit) {
    val name = FormulaNames.of(c.name)
    val type = name?.type ?: FormulaName.Type.TEXT
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NamePicker(c.name) { n ->
                    val t = FormulaNames.of(n)?.type ?: FormulaName.Type.TEXT
                    onChange(EasyCond(n, EasyCond.Op.forType(t).first(), ""))
                }
                OpPicker(c.op, EasyCond.Op.forType(type)) { onChange(c.copy(op = it)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "이 조건 빼기" }) { Text("빼기") }
            }
            if (c.op.needsValue) {
                val choices = CHOICES[c.name]
                if (choices != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { v -> FilterChip(selected = c.value == v, onClick = { onChange(c.copy(value = v)) }, label = { Text(v) }) }
                } else OutlinedTextField(
                    c.value, { onChange(c.copy(value = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (type == FormulaName.Type.NUM) "숫자" else "글") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (type == FormulaName.Type.NUM) KeyboardType.Number else KeyboardType.Text),
                    supportingText = name?.let { { Text(it.help) } },
                )
            }
        }
    }
}

/** Values a name usually takes, as chips (no typing). */
internal val CHOICES: Map<String, List<String>> = mapOf(
    FormulaName.ROLE.ko to listOf("주간", "저녁", "야간", "당직", "휴무", "기타"),
    FormulaName.WEEKDAY.ko to listOf("월", "화", "수", "목", "금", "토", "일"),
    FormulaName.STYLE.ko to listOf("교대", "일근", "알바", "현장", "자영업", "투자"),
)

@Composable
private fun NamePicker(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(current) }
    DropdownMenu(open, { open = false }) {
        FormulaName.Group.entries.filter { it != FormulaName.Group.ROW }.forEach { g ->
            Text(g.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            FormulaName.entries.filter { it.group == g }.forEach { n ->
                DropdownMenuItem(text = { Column { Text(n.ko); Text(n.help, style = MaterialTheme.typography.bodySmall) } }, onClick = { open = false; onPick(n.ko) })
            }
        }
    }
}

@Composable
private fun OpPicker(current: EasyCond.Op, ops: List<EasyCond.Op>, onPick: (EasyCond.Op) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(current.label) }
    DropdownMenu(open, { open = false }) {
        ops.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { open = false; onPick(o) }) }
    }
}

/**
 * 직접 쓰기: the formula with its mistake underlined in red and said in words, names that start with what is being typed offered as
 * chips (누르면 넣어져요), and the answer on the day shown.
 */
@Composable
fun FormulaField(value: String, onChange: (String) -> Unit, scope: FormulaScope, text: Boolean = false, label: String = "수식") {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val error = remember(value, text) {
        if (value.isBlank()) null
        else if (text) FormulaText.parse(value).exceptionOrNull() as? com.bamnat.core.formula.FormulaError
        else (Formula.parse(value) as? Parsed.Bad)?.error
    }
    val word = remember(field) {
        val before = field.text.take(field.selection.start)
        before.takeLastWhile { it.isLetterOrDigit() || it == '_' }
    }
    val red = MaterialTheme.colorScheme.error
    OutlinedTextField(
        field,
        { field = it; onChange(it.text) },
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        minLines = 2,
        visualTransformation = remember(error, red) { ErrorMark(error?.from, error?.to, red) },
        supportingText = { Text(error?.messageKo ?: if (text) "중괄호 {} 안에 수식을 넣어요. 예: 이번 주 야간 {이번주야간}번" else "예: 근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\")") },
    )
    val offers = remember(word) { if (word.isEmpty()) emptyList() else FormulaNames.complete(word) }
    val quick = if (offers.isNotEmpty()) offers else QUICK
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        quick.forEach { o ->
            AssistChip(onClick = {
                val start = field.selection.start - (if (offers.isNotEmpty()) word.length else 0)
                val t = field.text.substring(0, start.coerceAtLeast(0)) + o + field.text.substring(field.selection.start)
                val cursor = start.coerceAtLeast(0) + o.length
                field = TextFieldValue(t, TextRange(cursor)); onChange(t)
            }, label = { Text(o) })
        }
    }
    if (!text) PreviewLine(value, scope)
}

/** Ready pieces to tap in when nothing is being typed. */
private val QUICK = listOf("만약(", "그리고 ", "또는 ", "아님 ", " = ", " > ", "\"\"", ".포함(", "근무종류", "꼬리표", "지출", "주말")

private class ErrorMark(val from: Int?, val to: Int?, val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (from == null || to == null || text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val a = from.coerceIn(0, text.length)
        val b = maxOf(to, from + 1).coerceIn(a, text.length)
        val out = buildAnnotatedString {
            append(text)
            if (b > a) addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), a, b)
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/** "오늘은: 참" — what the condition gives on the day (or on an example day before there are days). */
@Composable
private fun PreviewLine(value: String, scope: FormulaScope) {
    if (value.isBlank()) return
    val (v, e) = remember(value, scope) { (Formula.parse(value) as? Parsed.Ok)?.formula?.runOrError(scope) ?: (null to null) }
    val say = when {
        e != null -> "이 날에는 계산이 안 돼요: ${e.messageKo}"
        v == null -> return
        v is com.bamnat.core.formula.Value.Bool -> if (v.v) "이 날에는 맞아요 ✓" else "이 날에는 아니에요"
        else -> "이 날에는: ${v.text.ifEmpty { "(비어 있음)" }}"
    }
    Text(say, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- 무엇을 ----

@Composable
private fun ActEditor(act: LogicAct, onChange: (LogicAct) -> Unit, scope: FormulaScope) {
    val c = LocalContext.current.appContainer
    Text("무엇을", style = MaterialTheme.typography.labelLarge)
    // PC D6 (사용성 중): 8가지가 한눈에 보이게 여러 줄로 (옆으로 밀어야 보이던 쓰기 동작을 못 찾았어요).
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LogicVerb.entries.forEach { v ->
            FilterChip(selected = act.verb == v, onClick = { onChange(LogicAct(v, if (v == LogicVerb.SCREEN) "memo" else "", null)) },
                label = { Text(v.label + if (v.writes) " ⚠" else "") })
        }
    }
    when (act.verb) {
        LogicVerb.NOTIFY -> FormulaField(act.arg, { onChange(act.copy(arg = it.take(120))) }, scope, text = true, label = "알림 글")
        LogicVerb.MEMO_TEMPLATE, LogicVerb.NEW_MEMO, LogicVerb.CHECK_RESET -> {
            val templates by c.memoTemplates.items.collectAsState()
            if (templates.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { t -> FilterChip(selected = act.arg == t.title, onClick = { onChange(act.copy(arg = t.title)) },
                    label = { Text(listOfNotNull(t.emoji, t.title).joinToString(" ")) }) }
            }
            OutlinedTextField(act.arg, { onChange(act.copy(arg = it.take(30))) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (act.verb == LogicVerb.CHECK_RESET) "체크 목록 제목(비우면 그날 모든 목록)" else "메모 틀 이름") })
        }
        LogicVerb.SCREEN -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogicVerb.SCREENS.forEach { (k, label) -> FilterChip(selected = act.arg == k, onClick = { onChange(act.copy(arg = k)) }, label = { Text(label) }) }
        }
        LogicVerb.MEMO_LINE -> FormulaField(act.arg, { onChange(act.copy(arg = it.take(120))) }, scope, text = true, label = "더할 줄 (예: 야간 {이번주야간}번째 · 이달 지출 {원(이달지출)})")
        LogicVerb.ADD_MONEY, LogicVerb.ADD_INCOME -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(act.arg, { onChange(act.copy(arg = it.take(20))) }, Modifier.weight(1f), singleLine = true, label = { Text("분류") })
            OutlinedTextField(act.amount?.toString() ?: "", { s -> onChange(act.copy(amount = s.filter(Char::isDigit).take(9).toLongOrNull())) },
                Modifier.weight(1f), singleLine = true, label = { Text("금액(원)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    if (act.verb.writes) Text("⚠ 메모나 가계부를 바꾸는 동작이에요. 할 때마다 바로 되돌리기가 나와요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- 규칙·단추 창 ----

@Composable
fun RuleDialog(start: LogicRule?, scope: FormulaScope, onDone: (LogicRule) -> Unit, onDismiss: () -> Unit,
    /** PC D10: the other rules, so a new one with the same name gets '(2)'; PC D11: one that does the same is asked about once. */
    others: List<LogicRule> = emptyList()) {
    val taken = others.map { it.name }.toSet()
    var r by remember { mutableStateOf(start ?: LogicRule("", RuleTrigger.START, min = -60, role = "night", act = LogicAct(LogicVerb.NOTIFY, notifyWords(RuleTrigger.START, "night")))) }
    var tried by remember { mutableStateOf<String?>(null) }
    // PC D11 (사용성 낮): the key of a rule already asked about ("같은 일을 하는 규칙이 이미 있어요"), so the second 저장 saves.
    var askedSame by remember { mutableStateOf<String?>(null) }
    // The red line goes once what it asked for is done.
    LaunchedEffect(r) {
        // Changed after the 같은 규칙 question: asked again only if it is still the same.
        if (askedSame != null && askedSame != r.copy(at = if (r.on == RuleTrigger.DAILY) timeOf(r.at.orEmpty()) else r.at).key) askedSame = null
        if (tried != null && askedSame == null) tried = ruleProblem(r)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) "규칙 만들기" else "규칙 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // PC D10 (낮): '7시'를 넣는 즉시 요약은 07:00으로.
                Text(r.copy(at = if (r.on == RuleTrigger.DAILY) timeOf(r.at.orEmpty()) ?: r.at else r.at).say(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(r.name, { r = r.copy(name = it.take(20)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("이름") })
                Text("언제", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleTrigger.entries.forEach { t -> FilterChip(selected = r.on == t, onClick = { r = followWords(r, whenFor(r, t)) }, label = { Text(t.label) }) }
                }
                when (r.on) {
                    RuleTrigger.START, RuleTrigger.END -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = r.role == null, onClick = { r = followWords(r, r.copy(role = null)) }, label = { Text("모든 근무") })
                            LogicRule.ROLE_WORDS.filterKeys { it != "off" }.forEach { (k, w) ->
                                FilterChip(selected = r.role == k, onClick = { r = followWords(r, r.copy(role = k)) }, label = { Text(w) })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // PC D7 (사용성 중): 근무 끝은 '끝날 때'부터, 시작은 '시작할 때'를 바로 앞에.
                            (if (r.on == RuleTrigger.END) listOf(0, 30, 60, -30, -60, -180) else listOf(-180, -60, -30, 0, 30, 60)).forEach { m ->
                                FilterChip(selected = r.min == m, onClick = { r = r.copy(min = m) },
                                    label = { Text(if (m != 0) minutesWord(m) else if (r.on == RuleTrigger.END) "끝날 때" else "시작할 때") })
                            }
                        }
                    }
                    RuleTrigger.DAILY -> OutlinedTextField(r.at.orEmpty(), { s -> r = r.copy(at = s.take(5)) }, singleLine = true, label = { Text("시각 (예: 07:00)") },
                        isError = timeOf(r.at.orEmpty()) == null,
                        supportingText = { if (timeOf(r.at.orEmpty()) == null) Text("시각을 07:00처럼 넣어 주세요") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    RuleTrigger.HOLD -> Text("화면이나 메모 틀을 여는 규칙이 맞는 날에는 길게 눌러도 빠른 설정 대신 그것이 열려요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Unit
                }
                Text("조건 (이럴 때만)", style = MaterialTheme.typography.labelLarge)
                ConditionEditor(r.cond, { r = r.copy(cond = it) }, scope, empty = "조건 없이 언제나")
                ActEditor(r.act, { r = r.copy(act = it) }, scope)
            }
        },
        confirmButton = {
            // PC D9 (높): 저장은 늘 눌리고, 아직 안 되면 왜인지 빨갛게 말해요.
            Column(horizontalAlignment = Alignment.End) {
                tried?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) }
                TextButton(onClick = {
                    val why = ruleProblem(r)
                    if (why != null) tried = why
                    else r.copy(at = if (r.on == RuleTrigger.DAILY) timeOf(r.at.orEmpty()) else r.at).let { done ->
                        val same = others.firstOrNull { it.key == done.key }
                        // PC D14 (사용성 낮): 한 번에 100만 원이 넘게 가계부에 넣는 내 규칙도 한 번 물어요 (매일 쌓이지 않게).
                        val warn = listOfNotNull(same?.let(::sameSay), bigSay(done))
                        if (warn.isNotEmpty() && askedSame != done.key) { askedSame = done.key; tried = warn.joinToString(" ") }
                        else onDone(done.copy(name = ruleName(done, taken)))
                    }
                }) { Text("저장") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun ButtonDialog(start: LogicButton?, scope: FormulaScope, onDone: (LogicButton) -> Unit, onDismiss: () -> Unit) {
    var b by remember { mutableStateOf(start ?: LogicButton("인수인계", "📝", ButtonPlace.DAY, LogicAct(LogicVerb.MEMO_TEMPLATE))) }
    var conditional by remember { mutableStateOf(b.show.isNotEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) "단추 만들기" else "단추 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(b.say(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(b.emoji.orEmpty(), { b = b.copy(emoji = it.take(4).ifBlank { null }) }, Modifier.width(80.dp), singleLine = true, label = { Text("그림") })
                    OutlinedTextField(b.label, { b = b.copy(label = it.take(16)) }, Modifier.weight(1f), singleLine = true, label = { Text("단추 글") })
                }
                Text("어디에", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ButtonPlace.entries.forEach { p -> FilterChip(selected = b.place == p, onClick = { b = b.copy(place = p) }, label = { Text(p.label) }) }
                }
                if (b.place == ButtonPlace.DECOR) Text("칩 줄에는 안 나와요. 스튜디오 › 꾸밈 층 › 고치기 › 누르면에서 그림·글상자에 이 단추를 이어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [RUL-22] PC D17: 스위치를 켜도 조건 칸이 대화 상자 아래로 밀려 안 보였어요. 스위치를 '어디에' 바로 아래로 올리고,
                // 켜면 근무 조건 한 줄을 바로 넣어 고를 칸이 곧장 보이게 해요.
                Row(Modifier.fillMaxWidth().toggleable(conditional, role = Role.Switch) {
                    conditional = it
                    b = b.copy(show = if (it) EasyWhen(listOf(EasyCond(FormulaName.ROLE.ko, EasyCond.Op.IS, "야간"))).toFormula() else "")
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text("조건이 맞는 날에만 보이기", Modifier.weight(1f))
                    Switch(conditional, null)
                }
                if (conditional) ConditionEditor(b.show, { b = b.copy(show = it) }, scope, empty = "조건이 없으면 언제나 보여요")
                ActEditor(b.act, { b = b.copy(act = it) }, scope)
            }
        },
        confirmButton = {
            val ok = b.label.isNotBlank() && (b.show.isEmpty() || Formula.problem(b.show) == null) && (b.act.verb !in MONEY_VERBS || b.act.amount != null) && (b.act.verb != LogicVerb.MEMO_LINE || b.act.arg.isNotBlank())
            TextButton(onClick = { onDone(b) }, enabled = ok) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** Which half of the editor a studio tool shows. */
enum class LogicShow { ALL, RULES, BUTTONS }

/** The 설정 section title, also in 설정 검색. */
const val LOGIC_ROW = "수식·규칙·버튼"

/** "다음: 11/13(목) 18:00", or why there is none, for a rule that runs at a time; null for the others. */
internal fun nextLine(r: LogicRule, facts: com.bamnat.core.formula.DayFactsBook?): String? {
    if (r.on !in setOf(RuleTrigger.DAILY, RuleTrigger.START, RuleTrigger.END)) return null
    facts ?: return null
    val at = LogicRunner.nextTime(r, com.bamnat.core.time.Today.dateTime(), facts)?.first
        ?: return "다음: 2주 안에 ${r.role?.let { LogicRule.ROLE_WORDS[it] } ?: ""} 근무가 없어요".replace("  ", " ")
    val wd = "월화수목금토일"[at.dayOfWeek.value - 1]
    return "다음: ${at.monthValue}/${at.dayOfMonth}($wd) ${"%02d:%02d".format(at.hour, at.minute)}"
}

private val MONEY_VERBS = setOf(LogicVerb.ADD_MONEY, LogicVerb.ADD_INCOME)

/** The 1.36 build 3 fixed 알림 글, read as untouched so an old draft also follows. */
private const val OLD_NOTIFY = "{근무시작}에 야간 근무예요"

/** [RUL-06] PC 3차: an 알림 글 the user has not touched follows 언제 and 근무 (주간·근무 끝 규칙에 '야간 근무예요'가 오지 않게). */
internal fun followWords(old: LogicRule, new: LogicRule): LogicRule {
    val untouched = old.act.verb == LogicVerb.NOTIFY && (old.act.arg.isBlank() || old.act.arg == notifyWords(old.on, old.role) || old.act.arg == OLD_NOTIFY)
    return if (untouched) new.copy(act = new.act.copy(arg = notifyWords(new.on, new.role))) else new
}

/**
 * PC D7 (사용성 중): 언제를 바꾸면 그에 맞는 때부터. '근무 끝'은 끝날 때 (시작 규칙의 '1시간 전'이 남지 않게), '근무 시작'으로
 * 돌아오면 처음처럼 1시간 전.
 */
internal fun whenFor(r: LogicRule, t: RuleTrigger): LogicRule = when {
    t == r.on -> r
    t == RuleTrigger.END -> r.copy(on = t, min = 0)
    t == RuleTrigger.START && r.on == RuleTrigger.END && r.min == 0 -> r.copy(on = t, min = -60)
    t == RuleTrigger.START -> r.copy(on = t)
    // PC D9 (높): '매일'은 보이는 시각이 실제 값이에요 (흐린 21:00인데 요약은 09:00, 저장은 말없이 안 되던 것). 몇 분 전·뒤는 근무 시작·끝에만.
    t == RuleTrigger.DAILY -> r.copy(on = t, at = timeOf(r.at.orEmpty()) ?: "21:00", min = 0)
    else -> r.copy(on = t, min = 0)
}

/**
 * PC D10: the name a rule is saved with. Left empty it is said from 언제 and 무엇을 ("매일 07:00 알림", not the 알림 글 with one quote
 * left); one the same as another rule's gets "(2)", "(3)".
 */
internal fun ruleName(r: LogicRule, taken: Set<String>): String {
    val base = r.name.trim().ifEmpty { "${r.sayWhen()} ${VERB_SHORT[r.act.verb] ?: r.act.verb.label}".take(20) }
    if (base !in taken) return base
    return (2..99).asSequence().map { n -> "${base.take(20 - " ($n)".length)} ($n)" }.first { it !in taken }
}

private val VERB_SHORT = mapOf(
    LogicVerb.NOTIFY to "알림", LogicVerb.MEMO_TEMPLATE to "메모 틀", LogicVerb.SCREEN to "화면 열기", LogicVerb.NEW_MEMO to "메모",
    LogicVerb.CHECK_RESET to "체크 비우기", LogicVerb.ADD_MONEY to "지출 넣기", LogicVerb.MEMO_LINE to "메모 한 줄", LogicVerb.ADD_INCOME to "수입 넣기",
)

/** PC D14: said once when a rule puts more than 100만 원 in the 가계부 each time it runs. */
internal fun bigSay(r: LogicRule): String? = r.act.amount?.takeIf { r.act.verb in MONEY_VERBS && it > com.bamnat.core.formula.Logic.RECEIVED_MAX_WON }?.let { a ->
    "돌 때마다 가계부에 ${"%,d".format(a)}원을 넣는 규칙이에요. 맞으면 한 번 더 저장을 눌러요"
}

/** PC D11: said once when a rule being saved does what [same] already does; 저장 again keeps both. */
internal fun sameSay(same: LogicRule) = "같은 일을 하는 ‘${same.name}’ 규칙이 이미 있어요. 그래도 만들려면 한 번 더 저장을 눌러요."

/** "7:00", "700", "0700", "07:00" → "07:00"; null when it is not a time of day. */
internal fun timeOf(s: String): String? {
    val m = Regex("^\\s*(\\d{1,2})\\s*[:시.]?\\s*(\\d{2})?\\s*분?\\s*$").find(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
}

/** PC D9 (높): why 저장 does nothing yet, said in red when pressed (no silent failure). */
internal fun ruleProblem(r: LogicRule): String? = when {
    r.on == RuleTrigger.DAILY && timeOf(r.at.orEmpty()) == null -> "시각을 07:00처럼 넣어 주세요"
    r.cond.isNotEmpty() && Formula.problem(r.cond) != null -> "조건 수식을 고쳐 주세요"
    r.act.verb in MONEY_VERBS && r.act.amount == null -> "금액을 넣어 주세요"
    r.act.verb == LogicVerb.MEMO_LINE && r.act.arg.isBlank() -> "메모에 더할 한 줄을 써 주세요"
    else -> null
}

/** [RUL-06] The 알림 글 a new rule starts with, said for its 언제 and 근무: "{근무끝}에 주간 근무가 끝나요". */
internal fun notifyWords(on: RuleTrigger, role: String?): String {
    val w = role?.let { LogicRule.ROLE_WORDS[it] }?.let { "$it " }.orEmpty()
    return when (on) {
        RuleTrigger.START -> "{근무시작}에 ${w}근무가 시작돼요"
        RuleTrigger.END -> "{근무끝}에 ${w}근무가 끝나요"
        RuleTrigger.DAILY -> "오늘 {근무} · 확인할 시간이에요"
        RuleTrigger.MEMO -> "메모를 적었어요"
        RuleTrigger.MONEY -> "이달 지출 {원(이달지출)}이에요"
        else -> "{근무} 날이에요"
    }
}

/** "1시간 전", "30분 전", "바로", "30분 뒤", "1시간 뒤". */
internal fun minutesWord(m: Int): String = when {
    m == 0 -> "바로"
    m % 60 == 0 -> "${kotlin.math.abs(m) / 60}시간 " + if (m < 0) "전" else "뒤"
    else -> "${kotlin.math.abs(m)}분 " + if (m < 0) "전" else "뒤"
}

/**
 * The buttons and rules, each in one line with what it does. [phone] true is 설정's list: each has its switch (끌 수 있게) and how many
 * times it ran in the last 7 days; the studio's list (false) edits the look being made.
 */
@Composable
fun LogicEditor(
    logic: Logic, onChange: (Logic) -> Unit, phone: Boolean = false, day: LocalDate = com.bamnat.core.time.Today.date(),
    show: LogicShow = LogicShow.ALL,
    /** 설정's list: switches and what happened; the editing is in the studio (스튜디오에서 열기). */
    readOnly: Boolean = false,
    /** Whether a 조건 is also a 조건부 꾸미기 line, and the way there. */
    usedInParts: (String) -> Boolean = { false },
    onOpenParts: (() -> Unit)? = null,
) {
    val c = LocalContext.current.appContainer
    val facts = LocalDayFacts.current ?: rememberAppDayFacts()
    val scope = remember(facts, day) { facts?.scope(day) ?: FormulaNames.SAMPLE }
    var editRule by remember { mutableStateOf<Int?>(null) }
    var editButton by remember { mutableStateOf<Int?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    // PC D9 (사용성 낮): a received rule that writes is switched on after one look at what it will do.
    var askOn by remember { mutableStateOf<LogicRule?>(null) }
    // [RUL-06] PC 3차 사용성 제안: the lines read again whenever the screen shows, and each minute while it stays open (울린 뒤 바로 '했어요').
    if (phone) {
        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { bump++ }
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); bump++ } }
    }
    val store = remember { LogicStore(c.appContext) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (show != LogicShow.RULES) {
        Text("단추", style = MaterialTheme.typography.titleSmall)
        if (logic.buttons.isEmpty()) Text("하루 화면·메모장·달력에 단추를 놓으면 한 번 눌러 메모 틀을 열거나 가계부에 넣을 수 있어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logic.buttons.forEachIndexed { i, b ->
            val sameB = remember(bump, b.id) { store.sameButton(b.id)?.let { "받은 ‘$it’${com.bamnat.app.ui.Fmt.wa(it)} 같음" } }
            LineCard(b.say(), listOfNotNull(sameB, if (phone) "지난 7일 ${store.runs(b.label)}번" else null).joinToString(" · ").ifEmpty { null }, null, null,
                onEdit = { editButton = i }.takeIf { !readOnly }, onRemove = { onChange(logic.copy(buttons = logic.buttons.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly })
        }
        if (!readOnly && logic.buttons.size < Logic.MAX_BUTTONS) TextButton(onClick = { editButton = -1 }) { Text("+ 단추 만들기") }
        }

        if (show != LogicShow.BUTTONS) {
        Text("규칙", style = MaterialTheme.typography.titleSmall)
        if (logic.rules.isEmpty()) Text("‘언제 → 이럴 때만 → 무엇을’로 저절로 돌게 해요. 예: 야간 근무 1시간 전에 알림, 날짜를 누르면 그날 메모 틀 열기",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logic.rules.forEachIndexed { i, r ->
            val on = remember(bump, r.key) { !store.isOff(r.key) }
            val came = remember(bump, r.key) { store.cameOff(r.key) }
            val missed = remember(bump, r.key) { phone && store.missed(r.key) }
            val got = remember(bump, r.key) { store.got(r.key) }
            // PC 1차 확인 (사용성 제안 높): when it comes next and what happened last time, so a rule that did not run says why.
            val note = remember(bump, r, facts) {
                // PC D7: a received rule kept once because this one does the same.
                val same = store.sameRule(r.key)?.let { "받은 ‘$it’${com.bamnat.app.ui.Fmt.wa(it)} 같음" }
                // PC D12 (사용성 낮): 받은 규칙은 작게 표시해요.
                val from = "받은 규칙".takeIf { got }
                if (!phone) listOfNotNull(from, same).joinToString(" · ").ifEmpty { null } else listOfNotNull(
                    from,
                    same,
                    // PC D8 (사용성 낮): 받은 쓰기 규칙이 왜 꺼져 있는지.
                    if (!on) (if (store.cameOff(r.key)) "받은 규칙이라 꺼진 채로 들어왔어요" else "꺼져 있어요") else nextLine(r, facts),
                    store.lastResult(r.key)?.let { "지난번 $it" },
                    "지난 7일 ${store.runs(r.name)}번",
                ).joinToString(" · ")
            }
            // PC D10: rules made before names were kept apart show which is which.
            val nth = logic.rules.take(i).count { it.name == r.name }
            LineCard("${r.name}${if (nth > 0) " (${nth + 1})" else ""}: ${r.say()}", note,
                if (phone) on else null, { v -> if (v && came) askOn = r else { store.setOff(r.key, !v); bump++; LogicRunner.schedule(c) } },
                onEdit = { editRule = i }.takeIf { !readOnly }, onRemove = { onChange(logic.copy(rules = logic.rules.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly },
                action = when {
                    phone && !on && came -> "켜기" to { askOn = r }
                    // PC D12 (사용성 중): 놓친 규칙은 그 자리에서 한 번 해요.
                    phone && on && missed -> "지금 한 번 하기" to { c.appScope.launch { LogicRunner.runNow(c, r); bump++ } }
                    else -> null
                })
            // [RUL-06] 이 조건을 쓰는 곳: the same 조건 also decorates days.
            if (r.cond.isNotEmpty() && usedInParts(r.cond)) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 조건은 조건부 꾸미기에도 쓰여요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 12.dp))
                onOpenParts?.let { go -> TextButton(onClick = go) { Text("꾸미기 보기") } }
            }
        }
        if (!readOnly && logic.rules.size < Logic.MAX_RULES) TextButton(onClick = { editRule = -1 }) { Text("+ 규칙 만들기") }
        }
        if (phone && store.undo != null) OutlinedButton(onClick = { c.appScope.launch { LogicRunner.undo(c); bump++ } }) { Text("마지막 동작 되돌리기") }
        askOn?.let { r ->
            AlertDialog(onDismissRequest = { askOn = null },
                title = { Text("받은 규칙을 켤까요?") },
                text = { Text("‘${r.name}’${com.bamnat.app.ui.Fmt.eun(r.name)} ${r.sayWhen()} ${r.act.say()}를 해요. 메모나 가계부를 바꾸는 규칙이에요. 바뀐 것은 아래 '규칙이 쓴 것'에서 되돌릴 수 있어요.") },
                confirmButton = { TextButton(onClick = { store.setOff(r.key, false); askOn = null; bump++; LogicRunner.schedule(c) }) { Text("켜기") } },
                dismissButton = { TextButton(onClick = { askOn = null }) { Text("그대로 두기") } })
        }
        // 1.36 [SEC-12] 보안 점검 M4 '규칙이 쓴 것': what rules and buttons wrote in 7 days, in a line each, and all of it taken back at once.
        if (phone) {
            val written = remember(bump) { store.written() }
            var open by remember { mutableStateOf(false) }
            var asking by remember { mutableStateOf(false) }
            if (written.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("규칙이 쓴 것 (최근 7일) ${written.size}개", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { open = !open }) { Text(if (open) "접기" else "보기") }
                    TextButton(onClick = { asking = true }) { Text("모두 되돌리기") }
                }
                if (open) written.asReversed().forEach { o ->
                    val at = java.time.Instant.ofEpochMilli(o.optLong("at")).atZone(com.bamnat.core.time.Today.zone())
                    Text("${at.monthValue}/${at.dayOfMonth} ${"%02d:%02d".format(at.hour, at.minute)} · ${o.optString("who")}: ${o.optString("say")}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (asking) androidx.compose.material3.AlertDialog(
                onDismissRequest = { asking = false },
                title = { Text("규칙이 쓴 것을 모두 되돌릴까요?") },
                text = { Text("최근 7일 동안 규칙과 단추가 만든 메모·메모 줄·가계부 ${written.size}개를 지우고, 비운 체크는 다시 채워요. 직접 쓴 것은 그대로예요") },
                confirmButton = { TextButton(onClick = { asking = false; c.appScope.launch { val n = LogicRunner.undoAll(c); bump++; LogicRunner.say("${n}개를 되돌렸어요") } }) { Text("모두 되돌리기") } },
                dismissButton = { TextButton(onClick = { asking = false }) { Text("닫기") } },
            )
        }
    }
    editRule?.let { i ->
        RuleDialog(logic.rules.getOrNull(i), scope, others = logic.rules.filterIndexed { j, _ -> j != i }, onDone = { r ->
            onChange(logic.copy(rules = if (i < 0) logic.rules + r else logic.rules.toMutableList().also { it[i] = r })); editRule = null
        }, onDismiss = { editRule = null })
    }
    editButton?.let { i ->
        ButtonDialog(logic.buttons.getOrNull(i), scope, { b ->
            // 누락 점검 N8: a new button gets its own id (꾸밈 층 tap); an edited one keeps the one it had, so a 꾸밈 stays joined.
            val keep = logic.buttons.getOrNull(i)?.id
            val b2 = if (keep != null) b.copy(givenId = keep) else b.copy(givenId = LogicButton.newId(logic.buttons.map { it.id }))
            onChange(logic.copy(buttons = if (i < 0) logic.buttons + b2 else logic.buttons.toMutableList().also { it[i] = b2 })); editButton = null
        }, { editButton = null })
    }
}

@Composable
private fun LineCard(text: String, note: String?, on: Boolean?, onSwitch: ((Boolean) -> Unit)?, onEdit: (() -> Unit)?, onRemove: (() -> Unit)?,
    /** PC D8: one more thing to do on the line (받은 규칙 '켜기'). */
    action: Pair<String, () -> Unit>? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                action?.let { (w, go) -> TextButton(onClick = go, modifier = Modifier.heightIn(min = 48.dp)) { Text(w) } }
            }
            if (on != null && onSwitch != null) Switch(on, onSwitch, Modifier.semantics { contentDescription = if (on) "규칙 켜짐" else "규칙 꺼짐" })
            onEdit?.let { TextButton(onClick = it) { Text("고치기") } }
            onRemove?.let { TextButton(onClick = it) { Text("빼기") } }
        }
    }
}
