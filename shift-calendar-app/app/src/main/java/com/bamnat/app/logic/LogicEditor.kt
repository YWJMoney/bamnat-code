package com.bamnat.app.logic

import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
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
    var advanced by remember { mutableStateOf(easy == null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !advanced, onClick = { if (easy != null) advanced = false }, enabled = easy != null || !advanced,
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("쉬운 보기") }
            SegmentedButton(selected = advanced, onClick = { advanced = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("수식으로") }
        }
        if (advanced || easy == null) {
            FormulaField(value, onChange, scope)
            if (easy == null && value.isNotBlank()) Text("이 수식은 표로 나타낼 수 없어 수식으로만 보여요", style = MaterialTheme.typography.bodySmall,
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
private val CHOICES: Map<String, List<String>> = mapOf(
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
fun RuleDialog(start: LogicRule?, scope: FormulaScope, onDone: (LogicRule) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(start ?: LogicRule("", RuleTrigger.START, min = -60, role = "night", act = LogicAct(LogicVerb.NOTIFY, "{근무시작}에 야간 근무예요"))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) "규칙 만들기" else "규칙 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.say(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(r.name, { r = r.copy(name = it.take(20)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("이름") })
                Text("언제", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleTrigger.entries.forEach { t -> FilterChip(selected = r.on == t, onClick = { r = r.copy(on = t) }, label = { Text(t.label) }) }
                }
                when (r.on) {
                    RuleTrigger.START, RuleTrigger.END -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = r.role == null, onClick = { r = r.copy(role = null) }, label = { Text("모든 근무") })
                            LogicRule.ROLE_WORDS.filterKeys { it != "off" }.forEach { (k, w) ->
                                FilterChip(selected = r.role == k, onClick = { r = r.copy(role = k) }, label = { Text(w) })
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(-180, -60, -30, 0, 30, 60).forEach { m ->
                                FilterChip(selected = r.min == m, onClick = { r = r.copy(min = m) },
                                    label = { Text(minutesWord(m)) })
                            }
                        }
                    }
                    RuleTrigger.DAILY -> OutlinedTextField(r.at ?: "21:00", { s -> r = r.copy(at = s.take(5)) }, singleLine = true, label = { Text("시각 (HH:mm)") },
                        isError = r.at?.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")) == false)
                    else -> Unit
                }
                Text("조건 (이럴 때만)", style = MaterialTheme.typography.labelLarge)
                ConditionEditor(r.cond, { r = r.copy(cond = it) }, scope, empty = "조건 없이 언제나")
                ActEditor(r.act, { r = r.copy(act = it) }, scope)
            }
        },
        confirmButton = {
            val ok = (r.cond.isEmpty() || Formula.problem(r.cond) == null) && (r.act.verb !in MONEY_VERBS || r.act.amount != null) && (r.act.verb != LogicVerb.MEMO_LINE || r.act.arg.isNotBlank()) &&
                (r.on != RuleTrigger.DAILY || r.at?.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")) == true)
            TextButton(onClick = { onDone(r.copy(name = r.name.ifBlank { r.act.say().take(20) })) }, enabled = ok) { Text("저장") }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ButtonPlace.entries.forEach { p -> FilterChip(selected = b.place == p, onClick = { b = b.copy(place = p) }, label = { Text(p.label) }) }
                }
                ActEditor(b.act, { b = b.copy(act = it) }, scope)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("조건이 맞는 날에만 보이기", Modifier.weight(1f))
                    Switch(conditional, { conditional = it; if (!it) b = b.copy(show = "") })
                }
                if (conditional) ConditionEditor(b.show, { b = b.copy(show = it) }, scope)
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
    val store = remember { LogicStore(c.appContext) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (show != LogicShow.RULES) {
        Text("단추", style = MaterialTheme.typography.titleSmall)
        if (logic.buttons.isEmpty()) Text("하루 화면·메모장·달력에 단추를 놓으면 한 번 눌러 메모 틀을 열거나 가계부에 넣을 수 있어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logic.buttons.forEachIndexed { i, b ->
            LineCard(b.say(), if (phone) "지난 7일 ${store.runs(b.label)}번" else null, null, null,
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
            // PC 1차 확인 (사용성 제안 높): when it comes next and what happened last time, so a rule that did not run says why.
            val note = remember(bump, r, facts) {
                if (!phone) null else listOfNotNull(
                    if (!on) "꺼져 있어요" else nextLine(r, facts),
                    store.lastResult(r.key)?.let { "지난번 $it" },
                    "지난 7일 ${store.runs(r.name)}번",
                ).joinToString(" · ")
            }
            LineCard("${r.name}: ${r.say()}", note,
                if (phone) on else null, { v -> store.setOff(r.key, !v); bump++; LogicRunner.schedule(c) },
                onEdit = { editRule = i }.takeIf { !readOnly }, onRemove = { onChange(logic.copy(rules = logic.rules.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly })
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
    }
    editRule?.let { i ->
        RuleDialog(logic.rules.getOrNull(i), scope, { r ->
            onChange(logic.copy(rules = if (i < 0) logic.rules + r else logic.rules.toMutableList().also { it[i] = r })); editRule = null
        }, { editRule = null })
    }
    editButton?.let { i ->
        ButtonDialog(logic.buttons.getOrNull(i), scope, { b ->
            onChange(logic.copy(buttons = if (i < 0) logic.buttons + b else logic.buttons.toMutableList().also { it[i] = b })); editButton = null
        }, { editButton = null })
    }
}

@Composable
private fun LineCard(text: String, note: String?, on: Boolean?, onSwitch: ((Boolean) -> Unit)?, onEdit: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (on != null && onSwitch != null) Switch(on, onSwitch, Modifier.semantics { contentDescription = if (on) "규칙 켜짐" else "규칙 꺼짐" })
            onEdit?.let { TextButton(onClick = it) { Text("고치기") } }
            onRemove?.let { TextButton(onClick = it) { Text("빼기") } }
        }
    }
}
