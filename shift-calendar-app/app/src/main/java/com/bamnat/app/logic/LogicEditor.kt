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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import com.bamnat.app.ui.components.DropdownMenu
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
import androidx.compose.foundation.clickable
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
import com.bamnat.core.formula.MAX_ELSE
import com.bamnat.core.formula.MAX_MORE
import com.bamnat.core.formula.Parsed
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.formula.pro
import java.time.LocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete

/**
 * [RUL-06] 1.36 886 수식·규칙 편집기 (사용자 2026-11-01 "다중 조건식도 가능하게, 다만 사용자가 편하게"): one condition in two views that
 * open the same value. 쉬운 보기 is a table of picks — [근무] [이(가)] [야간] — with rows joined by 그리고 or 또는; 고급 보기 is the formula
 * itself, with names to tap in (자동 완성), the place of a mistake underlined and said, and what it gives on a real day.
 */
@Composable
fun ConditionEditor(value: String, onChange: (String) -> Unit, scope: FormulaScope, modifier: Modifier = Modifier, empty: String = "언제나",
    /** PC 확인 1.37 A9 (메모 제목의 조건이 '근무가…'로 시작): the values of the thing being dressed first (그 메모의 고정·체크…), [lead]'s
     *  first name for a new row; 한 장·한 줄 values are offered only when ROW is in [lead]. */
    lead: List<FormulaName.Group> = emptyList(), start: FormulaName? = null) {
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
        // 1.39 [FX-16] 조건 고르개 (무료): 자주 쓰는 조건을 한 번에. 만들어진 수식은 아래 보기에 그대로 보여 배우게 돼요.
        QuickConds(value, onChange)
        // PC 확인 8d0cddf6 (규칙 창에서 수식 칸 아래 줄이 창 밖이라 안 보였어요): the 받는 칸 values first, above the condition, in every view.
        InputValuesLine(value, scope)
        if (view == 1) {
            BlockEditor(value, onChange, scope)
            PreviewLine(value, scope)
        } else if (view == 2 || easy == null) {
            FormulaField(value, onChange, scope, inputs = false)
            if (easy == null && value.isNotBlank()) Text("이 수식은 표로 나타낼 수 없어 블록이나 수식으로 보여요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            EasyTable(easy, { onChange(it.toFormula().let { f -> if (it.conds.isEmpty()) "" else f }) }, empty, lead, start)
            PreviewLine(value, scope)
        }
    }
}

@Composable
private fun EasyTable(w: EasyWhen, onChange: (EasyWhen) -> Unit, empty: String, lead: List<FormulaName.Group> = emptyList(), start: FormulaName? = null) {
    if (w.conds.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    w.conds.forEachIndexed { i, c ->
        if (i > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !w.any, onClick = { onChange(w.copy(any = false)) }, label = { Text("그리고") })
            FilterChip(selected = w.any, onClick = { onChange(w.copy(any = true)) }, label = { Text("또는") })
        }
        CondRow(c, { n -> onChange(w.copy(conds = w.conds.toMutableList().also { it[i] = n })) },
            { onChange(w.copy(conds = w.conds.filterIndexed { j, _ -> j != i })) }, lead)
    }
    TextButton(onClick = {
        val first = start ?: lead.firstOrNull()?.let { g -> FormulaName.entries.firstOrNull { it.group == g } }
        val add = first?.let { EasyCond(it.ko, EasyCond.Op.forType(it.type).first(), "") } ?: EasyCond(FormulaName.SHIFT.ko, EasyCond.Op.IS, "")
        onChange(w.copy(conds = w.conds + add))
    }) {
        Text(if (w.conds.isEmpty()) "+ 조건 넣기" else "+ 조건 더하기")
    }
}

/** One row: [값 고르기] [어떻게] [무엇]. The value list follows the name (근무종류 → 주간·야간…, 요일 → 월~일). */
@Composable
private fun CondRow(c: EasyCond, onChange: (EasyCond) -> Unit, onRemove: () -> Unit, lead: List<FormulaName.Group> = emptyList()) {
    val name = FormulaNames.of(c.name)
    val type = name?.type ?: FormulaName.Type.TEXT
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NamePicker(c.name, lead) { n ->
                    val t = FormulaNames.of(n)?.type ?: FormulaName.Type.TEXT
                    onChange(EasyCond(n, EasyCond.Op.forType(t).first(), ""))
                }
                OpPicker(c.op, EasyCond.Op.forType(type)) { onChange(c.copy(op = it)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = "이 조건 빼기" }) { Text("빼기") }
            }
            if (c.op.needsValue) {
                val choices = choicesFor(c.name, LocalContext.current.appContainer.settingsRepository.current.workStyle, c.value)
                if (choices != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { (v, w) -> FilterChip(selected = c.value == v, onClick = { onChange(c.copy(value = v)) }, label = { Text(w) }) }
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

/**
 * 1.39 [FX-16] 자주 쓰는 조건: 야간인 날·주말·쉬는 날·공휴일, 그리고 작품의 값 칸마다 '목표 못 채움'·'3번 연속'. 누르면 지금 조건을 바꾸고,
 * 이미 있으면 '그리고'로 이어요.
 */
@Composable
private fun QuickConds(value: String, onChange: (String) -> Unit) {
    val fx = LocalFxLogic.current
    // 2.0.0 [RVS-10] 검토 STU-27: '야간인 날' only where there are nights; a day off and a working day in the work's own words.
    val style = LocalContext.current.appContainer.settingsRepository.current.workStyle
    val picks = remember(fx.values, style) {
        listOfNotNull(("야간인 날" to "${FormulaName.ROLE.ko} = \"야간\"").takeIf { "night" in com.bamnat.core.work.TypeWords.roles(style) },
            "주말" to FormulaName.WEEKEND.ko, (com.bamnat.core.work.TypeWords.roleWord(style, "off") ?: "쉬는 날") to FormulaName.OFF.ko,
            (com.bamnat.core.work.TypeWords.roleWord(style, "day") ?: "일하는 날") to "아님 ${FormulaName.OFF.ko}", "공휴일" to FormulaName.HOLIDAY.ko) +
            fx.values.filter { it.goal != null && it.kind == com.bamnat.core.formula.ValueKind.NUM }.flatMap { v ->
                val g = com.bamnat.core.formula.Formula.numText(v.goal!!)
                listOf("${v.name} 목표 못 채움 ✦" to "값(\"${v.name}\") < $g", "${v.name} 3번 연속 ✦" to "연속(\"${v.name}\") >= 3")
            }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        picks.forEach { (label, f) ->
            AssistChip(onClick = { onChange(if (value.isBlank()) f else if (f in value) value else "$value 그리고 $f") }, label = { Text(label) })
        }
    }
}

/**
 * 2.0.0 [RVS-10] 검토 STU-27: [CHOICES] as (값, 칩 글) for [style]: 근무종류 offers only the kinds of 근무 of that work, said in its words
 * (가게는 '영업일' 칩이 "주간"을 넣어요, 값은 수식의 말 그대로), and keeps [keep] (a work made elsewhere) so it can still be seen.
 */
internal fun choicesFor(name: String, style: com.bamnat.core.work.WorkStyle?, keep: String? = null): List<Pair<String, String>>? {
    val all = CHOICES[name] ?: return null
    if (name != FormulaName.ROLE.ko) return all.map { it to it }
    val roles = com.bamnat.core.work.TypeWords.roles(style)
    return com.bamnat.core.formula.DayFactsBook.ROLE_WORDS.filter { (k, v) -> k in roles || v == keep }
        .map { (k, v) -> v to (com.bamnat.core.work.TypeWords.roleWord(style, k) ?: v) }
}

/** Values a name usually takes, as chips (no typing). */
internal val CHOICES: Map<String, List<String>> = mapOf(
    FormulaName.ROLE.ko to listOf("주간", "저녁", "야간", "당직", "휴무", "기타"),
    FormulaName.WEEKDAY.ko to listOf("월", "화", "수", "목", "금", "토", "일"),
    FormulaName.STYLE.ko to listOf("교대", "일근", "알바", "현장", "자영업", "투자"),
)

@Composable
private fun NamePicker(current: String, lead: List<FormulaName.Group> = emptyList(), onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(current) }
    DropdownMenu(open, { open = false }) {
        (lead + FormulaName.Group.entries.filter { it != FormulaName.Group.ROW && it !in lead }).forEach { g ->
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
fun FormulaField(value: String, onChange: (String) -> Unit, scope: FormulaScope, text: Boolean = false, label: String = "수식",
    /** The 받는 칸 line under the field; [ConditionEditor] says it once above its views instead. */
    inputs: Boolean = true) {
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
    // 1.37 PC 5차: 치는 중에 나오는 이름 칩에 설명이 없었어요(‘급여 설정 전에는 비어 있어요’도 못 찾음). 고를 이름의 설명을 한 줄씩.
    offers.mapNotNull { o -> FormulaNames.of(o.trim().trimEnd('('))?.let { "${it.ko}: ${it.help}" } }.take(3).forEach {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (inputs) InputValuesLine(value, scope)
    if (!text) PreviewLine(value, scope)
}

/**
 * PC 확인 5c21f3bb (적립일 17을 넣었는데 규칙에는 칸("적립일", 10)만 보여요): the 10 is only the fallback; the value put in when the
 * recipe went on is what the formula reads, said here.
 */
@Composable
private fun InputValuesLine(value: String, scope: FormulaScope) {
    val labels = remember(value) { com.bamnat.core.formula.FormulaInputs.labels(value) }
    if (labels.isEmpty()) return
    val said = labels.joinToString(" · ") { l -> scope.input(l)?.let { "$l ${it.text}" } ?: "$l (넣은 값 없음, 괄호 안 기본값)" }
    Text("받는 칸 지금 값: $said", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ActEditor(act: LogicAct, onChange: (LogicAct) -> Unit, scope: FormulaScope, title: String? = "무엇을") {
    val c = LocalContext.current.appContainer
    // 1.39 [FX-03][FX-05] the work being edited: its 값 칸 and buttons.
    val fx = LocalFxLogic.current
    title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
    // PC D6 (사용성 중): 8가지가 한눈에 보이게 여러 줄로 (옆으로 밀어야 보이던 쓰기 동작을 못 찾았어요).
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LogicVerb.entries.forEach { v ->
            FilterChip(selected = act.verb == v, onClick = {
                    onChange(LogicAct(v, when (v) { LogicVerb.SCREEN -> "memo"; LogicVerb.VAL_ADD, LogicVerb.VAL_SET -> fx.values.firstOrNull { it.kind == com.bamnat.core.formula.ValueKind.NUM }?.name.orEmpty()
                        LogicVerb.VAL_STAMP -> fx.values.firstOrNull { it.kind == com.bamnat.core.formula.ValueKind.TIME }?.name.orEmpty()
                        LogicVerb.VAL_TEXT -> fx.values.firstOrNull { it.kind == com.bamnat.core.formula.ValueKind.TEXT }?.name.orEmpty(); else -> "" }, null,
                        by = if (v == LogicVerb.VAL_ADD) 1.0 else null))
                },
                // 1.39 [FX-01] ✦ = 프로 기능 장치 (만들기·미리 보기는 무료).
                label = { Text(v.label + (if (v.writes) " ⚠" else "") + (if (v.pro) " ✦" else "")) })
        }
    }
    when (act.verb) {
        LogicVerb.NOTIFY -> {
            FormulaField(act.arg, { onChange(act.copy(arg = it.take(120))) }, scope, text = true, label = "알림 글")
            // 1.39 [FX-05] 알림 단추: up to two of the work's buttons on the 알림, pressed without opening the app.
            val buttons = fx.buttons
            Text("알림에 단추 달기 ✦ (${LogicAct.MAX_THEN}개까지)", style = MaterialTheme.typography.labelLarge)
            if (buttons.isEmpty()) Text("먼저 단추를 만들면 알림에서 바로 눌러요(예: 물 한 잔, 가계부에 넣기)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                buttons.forEach { b ->
                    val on = b.id in act.then
                    FilterChip(selected = on, enabled = on || act.then.size < LogicAct.MAX_THEN,
                        onClick = { onChange(act.copy(then = if (on) act.then - b.id else act.then + b.id)) },
                        label = { Text(listOfNotNull(b.emoji, b.label).joinToString(" ")) })
                }
            }
            // 1.39 [FX-15] '10분 뒤 다시'·'1시간 뒤 다시': 무료 (1.38 메모 알림에도 있던 미루기).
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LogicAct.AGAIN.forEach { (k, m) ->
                    val on = k in act.then
                    FilterChip(selected = on, enabled = on || act.then.size < LogicAct.MAX_THEN,
                        onClick = { onChange(act.copy(then = if (on) act.then - k else act.then + k)) },
                        label = { Text("${LogicAct.afterWords(m)} 뒤 다시") })
                }
            }
        }
        // 1.39 [FX-03] 값 칸: which number, and by how much (or to what).
        LogicVerb.VAL_ADD, LogicVerb.VAL_SET -> {
            val values = fx.values
            if (values.isEmpty()) Text("먼저 아래 ‘값 칸’에서 셀 것(예: 물 잔, 배달 건수)을 만들어요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { v -> FilterChip(selected = act.arg == v.name, onClick = { onChange(act.copy(arg = v.name)) }, label = { Text(v.name) }) }
            }
            var text by remember(act.verb) { mutableStateOf(act.by?.let(com.bamnat.core.formula.Formula::numText) ?: "") }
            OutlinedTextField(text, { s ->
                text = s.filter { it.isDigit() || it == '-' || it == '.' }.take(10)
                onChange(act.copy(by = text.toDoubleOrNull()?.coerceIn(-LogicAct.MAX_BY, LogicAct.MAX_BY)))
            }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (act.verb == LogicVerb.VAL_ADD) "더할 수 (−1은 하나 빼기)" else "이 수로 정하기 (0이면 처음으로)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        // 1.39 [FX-13] 시각·글 값 칸.
        LogicVerb.VAL_STAMP, LogicVerb.VAL_TEXT -> {
            val kind = if (act.verb == LogicVerb.VAL_STAMP) com.bamnat.core.formula.ValueKind.TIME else com.bamnat.core.formula.ValueKind.TEXT
            val values = fx.values.filter { it.kind == kind }
            if (values.isEmpty()) Text("먼저 아래 ‘값 칸’에서 ${kind.label} 값 칸(예: ${if (kind == com.bamnat.core.formula.ValueKind.TIME) "약 먹은 때" else "오늘 기분"})을 만들어요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEach { v -> FilterChip(selected = act.arg == v.name, onClick = { onChange(act.copy(arg = v.name)) }, label = { Text(v.name) }) }
            }
            if (act.verb == LogicVerb.VAL_TEXT) FormulaField(act.text, { onChange(act.copy(text = it.take(LogicAct.MAX_TEXT))) }, scope, text = true, label = "적을 글 (24자, {수식} 가능)")
        }
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
            LogicVerb.SCREENS.forEach { (k, label) -> FilterChip(selected = act.arg == k, onClick = { onChange(act.copy(arg = k)) },
                label = { Text(label + if (k in LogicVerb.NEW_SCREENS) " ✦" else "") }) }
        }
        LogicVerb.MEMO_LINE -> FormulaField(act.arg, { onChange(act.copy(arg = it.take(120))) }, scope, text = true, label = "더할 줄 (예: 야간 {이번주야간}번째 · 이달 지출 {원(이달지출)})")
        // 1.37 PC 4차: 반 폭 칸의 긴 이름표가 두 줄↔한 줄로 바뀌며 칸이 움직여 금액이 이름 칸에 들어갔어요("Trip10000").
        // 두 칸을 한 줄씩, 이름표는 짧게.
        LogicVerb.ADD_MONEY, LogicVerb.ADD_INCOME, LogicVerb.ENVELOPE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(act.arg, { onChange(act.copy(arg = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (act.verb == LogicVerb.ENVELOPE) "봉투 이름" else "분류") },
                placeholder = if (act.verb == LogicVerb.ENVELOPE) ({ Text("비우면 맨 위 봉투") }) else null)
            OutlinedTextField(act.amount?.toString() ?: "", { s -> onChange(act.copy(amount = s.filter(Char::isDigit).take(9).toLongOrNull())) },
                Modifier.fillMaxWidth(), singleLine = true, label = { Text("금액(원)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    if (act.verb.writes) Text("⚠ 메모나 가계부를 바꾸는 동작이에요. 할 때마다 바로 되돌리기가 나와요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 1.39 [FX-15] 기다렸다 하기 ✦: 바로, 또는 몇 분 뒤에 (화면·메모 틀을 여는 동작은 바로만).
    if (act.verb != LogicVerb.SCREEN && act.verb != LogicVerb.MEMO_TEMPLATE) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = act.after == null, onClick = { onChange(act.copy(after = null)) }, label = { Text("바로") })
        AFTER_PICKS.forEach { m -> FilterChip(selected = act.after == m, onClick = { onChange(act.copy(after = m)) }, label = { Text("${LogicAct.afterWords(m)} 뒤 ✦") }) }
    }
}

/** 1.39 [FX-15] 기다렸다 하기의 고르기. */
private val AFTER_PICKS = listOf(3, 4, 5, 10, 30, 60, 120, 180)

/**
 * 1.39 [FX-11] 동작 줄 ✦: 이어서 할 일 (또는 '아니면' 할 일), 한 줄에 [max]개까지. 각 동작은 위 '무엇을'과 같은 칸으로 고쳐요.
 */
@Composable
private fun StepsEditor(title: String, hint: String, steps: List<LogicAct>, max: Int, onChange: (List<LogicAct>) -> Unit, scope: FormulaScope) {
    Text("$title ✦", style = MaterialTheme.typography.labelLarge)
    if (steps.isEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    steps.forEachIndexed { i, a ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${a.say()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(steps.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.semantics { contentDescription = "이 동작 빼기" }) { Text("빼기") }
                }
                ActEditor(a, { n -> onChange(steps.toMutableList().also { it[i] = n }) }, scope, title = null)
            }
        }
    }
    if (steps.size < max) TextButton(onClick = { onChange(steps + LogicAct(LogicVerb.NOTIFY, "")) }) { Text(if (steps.isEmpty()) "+ 동작 넣기" else "+ 동작 더하기") }
}

// ---- 규칙·단추 창 ----

@Composable
fun RuleDialog(start: LogicRule?, scope: FormulaScope, onDone: (LogicRule) -> Unit, onDismiss: () -> Unit,
    /** PC D10: the other rules, so a new one with the same name gets '(2)'; PC D11: one that does the same is asked about once. */
    others: List<LogicRule> = emptyList()) {
    val taken = others.map { it.name }.toSet()
    val fxValues = LocalFxLogic.current.values
    // 1.38 PC 11차 식당 둘러보기 ('야간'이 가게에 보임): a new rule starts on 야간 for 교대 only, on any work elsewhere.
    val style = androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.current.workStyle
    val firstRole = if (style == null || style == com.bamnat.core.work.WorkStyle.SHIFT) "night" else null
    var r by remember { mutableStateOf(start ?: LogicRule("", RuleTrigger.START, min = -60, role = firstRole, act = LogicAct(LogicVerb.NOTIFY, notifyWords(RuleTrigger.START, firstRole)))) }
    var tried by remember { mutableStateOf<String?>(null) }
    // PC D11 (사용성 낮): the key of a rule already asked about ("같은 일을 하는 규칙이 이미 있어요"), so the second 저장 saves.
    var askedSame by remember { mutableStateOf<String?>(null) }
    // The red line goes once what it asked for is done.
    LaunchedEffect(r) {
        // Changed after the 같은 규칙 question: asked again only if it is still the same.
        if (askedSame != null && askedSame != ruleDone(r).key) askedSame = null
        if (tried != null && askedSame == null) tried = ruleProblem(r)
    }
    // 1.37 PC 4차: 뒤로 가기로 만들던 규칙이 말없이 사라졌어요. 바꾼 게 있으면 먼저 물어요.
    val first = remember { r }
    var asking by remember { mutableStateOf(false) }
    if (asking) com.bamnat.app.ui.components.DiscardAsk(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
    AlertDialog(
        onDismissRequest = { if (r != first) asking = true else onDismiss() },
        title = { Text(if (start == null) "규칙 만들기" else "규칙 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(r.name, { r = r.copy(name = it.take(20)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("이름") })
                Text("언제", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleTrigger.entries.forEach { t -> FilterChip(selected = r.on == t, onClick = { r = followWords(r, whenFor(r, t, fxValues)) },
                        label = { Text(t.label + if (t == RuleTrigger.VALUE || t == RuleTrigger.EVERY || t == RuleTrigger.ALARM_OFF) " ✦" else "") }) }
                }
                when (r.on) {
                    RuleTrigger.START, RuleTrigger.END -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 2.0.0 [RVS-10] 검토 STU-27: only the kinds of 근무 of this work (가게·투자·현장은 '모든 근무' 하나), in its words.
                            FilterChip(selected = r.role == null, onClick = { r = followWords(r, r.copy(role = null)) },
                                label = { Text(com.bamnat.core.work.TypeWords.say(style, "모든 근무")) })
                            LogicRule.ROLE_WORDS.filterKeys { it != "off" && (it in com.bamnat.core.work.TypeWords.roles(style) || it == r.role) }
                                .takeIf { it.size > 1 || r.role != null }?.forEach { (k, w) ->
                                    FilterChip(selected = r.role == k, onClick = { r = followWords(r, r.copy(role = k)) },
                                        label = { Text(com.bamnat.core.work.TypeWords.roleWord(style, k) ?: w) })
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
                    // 1.39 [FX-21]
                    RuleTrigger.ALARM_OFF -> Text("근무 알람을 끌 때마다 돌아요(메모 일정 알람은 빼요). 예: 일어나면 물 한 잔 +1, 수면 기록 열기",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RuleTrigger.HOLD -> Text("화면이나 메모 틀을 여는 규칙이 맞는 날에는 길게 눌러도 빠른 설정 대신 그것이 열려요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 1.39 [FX-12] 값이 바뀌면: 어느 값 칸, 목표에 처음 닿을 때만.
                    RuleTrigger.VALUE -> {
                        if (fxValues.isEmpty()) Text("먼저 아래 ‘값 칸’을 만들어요(예: 물 잔)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fxValues.forEach { v -> FilterChip(selected = r.of == v.name, onClick = { r = r.copy(of = v.name, goal = r.goal && v.goal != null) }, label = { Text(v.name) }) }
                        }
                        val target = fxValues.firstOrNull { it.name == r.of }
                        if (target?.goal != null) Row(Modifier.fillMaxWidth().toggleable(r.goal, role = Role.Switch) { r = r.copy(goal = it) },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("목표(${target.show(target.goal!!)})에 처음 닿을 때만", Modifier.weight(1f))
                            Switch(r.goal, null)
                        }
                        Text("조건에서 값(\"${r.of ?: "이름"}\")으로 바뀐 값을 읽어요. 규칙이 다시 값을 바꾸면 3번까지만 이어 돌아요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 1.39 [FX-15] 몇 분마다: 간격, 그리고 하루 종일 · 시간 사이 · 근무 중.
                    RuleTrigger.EVERY -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30, 60, 90, 120, 180, 240).forEach { m -> FilterChip(selected = r.min == m, onClick = { r = r.copy(min = m) }, label = { Text("${LogicAct.afterWords(m)}마다") }) }
                        }
                        val span = when { r.inWork -> 2; r.at != null || r.until != null -> 1; else -> 0 }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = span == 0, onClick = { r = r.copy(at = null, until = null, inWork = false) }, label = { Text("하루 종일") })
                            FilterChip(selected = span == 1, onClick = { r = r.copy(at = r.at ?: "09:00", until = r.until ?: "21:00", inWork = false) }, label = { Text("시간 사이") })
                            FilterChip(selected = span == 2, onClick = { r = r.copy(at = null, until = null, inWork = true) }, label = { Text("근무 중에만") })
                        }
                        if (span == 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(r.at.orEmpty(), { s -> r = r.copy(at = s.take(5)) }, Modifier.weight(1f), singleLine = true, label = { Text("부터") },
                                isError = timeOf(r.at.orEmpty()) == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(r.until.orEmpty(), { s -> r = r.copy(until = s.take(5)) }, Modifier.weight(1f), singleLine = true, label = { Text("까지") },
                                isError = timeOf(r.until.orEmpty()) == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Text("하루 ${LogicRule.EVERY_DAY_MAX}번까지만 돌아요. 물 마시기·스트레칭·순찰 확인처럼 쓰여요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Unit
                }
                Text("조건 (이럴 때만)", style = MaterialTheme.typography.labelLarge)
                ConditionEditor(r.cond, { r = r.copy(cond = it) }, scope, empty = "조건 없이 언제나")
                ActEditor(r.act, { r = r.copy(act = it) }, scope)
                // 1.39 [FX-11] 동작 줄과 '아니면'.
                StepsEditor("그리고 이어서", "한 번에 여러 일을 해요(예: 알림 + 값 +1 + 메모 한 줄)", r.more, MAX_MORE, { r = r.copy(more = it) }, scope)
                if (r.cond.isNotEmpty()) StepsEditor("조건이 아니면", "조건이 맞지 않는 날에 할 일(비우면 아무것도 안 해요)", r.otherwise, MAX_ELSE, { r = r.copy(otherwise = it) }, scope)
                // PC D10 (낮): '7시'를 넣는 즉시 요약은 07:00으로. 1.37 PC 4차: 요약이 위에서 늘면 아래 칸이 밀려 맨 아래로 옮겼어요.
                Text(ruleDone(r).say(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
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
                    else ruleDone(r).let { done ->
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
    // 1.37 PC 4차: 규칙 창과 같은 구조라 같이 (뒤로 가기 전에 묻기, 요약은 맨 아래).
    val first = remember { b }
    var asking by remember { mutableStateOf(false) }
    if (asking) com.bamnat.app.ui.components.DiscardAsk(onKeep = { asking = false }, onDiscard = { asking = false; onDismiss() })
    AlertDialog(
        onDismissRequest = { if (b != first) asking = true else onDismiss() },
        title = { Text(if (start == null) "단추 만들기" else "단추 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(b.emoji.orEmpty(), { b = b.copy(emoji = it.take(4).ifBlank { null }) }, Modifier.width(80.dp), singleLine = true, label = { Text("그림") })
                    OutlinedTextField(b.label, { b = b.copy(label = it.take(16)) }, Modifier.weight(1f), singleLine = true, label = { Text("단추 글") })
                }
                Text("어디에", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ButtonPlace.entries.forEach { p -> FilterChip(selected = b.place == p, onClick = { b = b.copy(place = p) }, label = { Text(p.label + if (p.outside) " ✦" else "") }) }
                }
                // 1.39 [FX-04] 밖에서 누르기: where the user finds it.
                when (b.place) {
                    ButtonPlace.WIDGET -> "홈 화면을 길게 눌러 위젯 › 밤낮 › ‘밤낮 단추’를 놓으면 앱을 열지 않고 눌러요(4개까지)"
                    ButtonPlace.TILE -> "알림 막대를 내려 연필(편집)에서 ‘밤낮 단추’ 타일을 넣어요. 첫 단추 하나가 타일이 돼요"
                    ButtonPlace.SHORTCUT -> "밤낮 아이콘을 길게 누르면 메뉴에 나와요(4개까지)"
                    else -> null
                }?.let { Text("$it · ✦ 프로 기능 장치", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (b.place == ButtonPlace.DECOR) Text("칩 줄에는 안 나와요. 스튜디오 › 꾸밈 층 › 고치기 › 누르면에서 그림·글상자에 이 단추를 이어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [RUL-22] PC D17: 스위치를 켜도 조건 칸이 대화 상자 아래로 밀려 안 보였어요. 스위치를 '어디에' 바로 아래로 올리고,
                // 켜면 근무 조건 한 줄을 바로 넣어 고를 칸이 곧장 보이게 해요.
                Row(Modifier.fillMaxWidth().toggleable(conditional, role = Role.Switch) {
                    conditional = it
                    b = b.copy(show = if (it) EasyWhen(listOf(EasyCond(FormulaName.ROLE.ko, EasyCond.Op.IS, ""))).toFormula() else "")
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text("조건이 맞는 날에만 보이기", Modifier.weight(1f))
                    Switch(conditional, null)
                }
                if (conditional) {
                    ConditionEditor(b.show, { b = b.copy(show = it) }, scope, empty = "조건이 없으면 언제나 보여요")
                    val facts = LocalDayFacts.current ?: rememberAppDayFacts()
                    val today = remember { com.bamnat.core.time.Today.date() }
                    remember(b.show, facts) {
                        if (unpicked(b.show)) "값을 골라 주세요(예: 근무종류 칩에서 야간)"
                        else facts?.takeIf { b.show.isNotBlank() }?.let { f -> nextShow(b, f, today).replace("다음 ", "다음에 보이는 날 ") }
                    }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ActEditor(b.act, { b = b.copy(act = it) }, scope)
                // 1.39 [FX-11] 누를 때 조건 따지기, 동작 줄과 '아니면'.
                StepsEditor("그리고 이어서", "한 번 눌러 여러 일을 해요(예: 값 +1 + 메모 한 줄)", b.more, MAX_MORE, { b = b.copy(more = it) }, scope)
                Row(Modifier.fillMaxWidth().toggleable(b.cond.isNotEmpty(), role = Role.Switch) {
                    b = if (it) b.copy(cond = EasyWhen(listOf(EasyCond(FormulaName.ROLE.ko, EasyCond.Op.IS, ""))).toFormula()) else b.copy(cond = "", otherwise = emptyList())
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text("누를 때 조건을 따져 다르게 하기 ✦", Modifier.weight(1f))
                    Switch(b.cond.isNotEmpty(), null)
                }
                if (b.cond.isNotEmpty()) {
                    ConditionEditor(b.cond, { b = b.copy(cond = it) }, scope, empty = "조건을 넣어요")
                    StepsEditor("조건이 아니면", "조건이 맞지 않을 때 할 일(비우면 아무것도 안 해요)", b.otherwise, MAX_ELSE, { b = b.copy(otherwise = it) }, scope)
                }
                Text(b.say(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            val ok = b.label.isNotBlank() && (b.show.isEmpty() || Formula.problem(b.show) == null && !unpicked(b.show)) &&
                (b.cond.isEmpty() || Formula.problem(b.cond) == null && !unpicked(b.cond)) && b.acts.all { actProblem(it) == null }
            TextButton(onClick = { onDone(b) }, enabled = ok) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** Which half of the editor a studio tool shows. */
enum class LogicShow { ALL, RULES, BUTTONS }

/** The 설정 section title, also in 설정 검색. */
const val LOGIC_ROW = "수식·규칙·버튼"

/**
 * [RUL-23] PC D18 사용성 제안: a button with a 보이기 조건, said for [day] — "오늘은 숨김(조건) · 다음 11/5(수)" or "오늘 보여요 · …",
 * looking up to 31 days ahead for the next day it shows; null when it has no 조건 or the facts are not read yet.
 */
internal fun showLine(b: LogicButton, facts: com.bamnat.core.formula.DayFactsBook?, day: LocalDate): String? {
    if (b.show.isBlank()) return null
    facts ?: return null
    return if (holds(b, facts, day)) "오늘 보여요" else "오늘은 숨김(조건) · ${nextShow(b, facts, day)}"
}

/** [RUL-24] PC D19: a 쉬운 보기 row still waiting for its value (막 켠 조건) — said instead of a 다음 날, and not saved yet. */
internal fun unpicked(show: String): Boolean = EasyWhen.read(show)?.conds?.any { it.op.needsValue && it.value.isBlank() } == true

private fun holds(b: LogicButton, facts: com.bamnat.core.formula.DayFactsBook, d: LocalDate) =
    runCatching { Formula.eval(b.show, facts.scope(d))?.truthy == true }.getOrDefault(false)

/** "다음 11/5(수)": the next day after [day] (31 days at most) that [b] shows. */
internal fun nextShow(b: LogicButton, facts: com.bamnat.core.formula.DayFactsBook, day: LocalDate): String =
    (1L..31L).map { day.plusDays(it) }.firstOrNull { holds(b, facts, it) }
        ?.let { "다음 ${it.monthValue}/${it.dayOfMonth}(${"월화수목금토일"[it.dayOfWeek.value - 1]})" } ?: "한 달 안에 보이는 날이 없어요"

/** "다음: 11/13(목) 18:00", or why there is none, for a rule that runs at a time; null for the others. */
internal fun nextLine(r: LogicRule, facts: com.bamnat.core.formula.DayFactsBook?): String? {
    if (r.on !in setOf(RuleTrigger.DAILY, RuleTrigger.START, RuleTrigger.END)) return null
    facts ?: return null
    val at = LogicRunner.nextTime(r, com.bamnat.core.time.Today.dateTime(), facts)?.first
        ?: return "다음: 2주 안에 ${r.role?.let { LogicRule.ROLE_WORDS[it] } ?: ""} 근무가 없어요".replace("  ", " ")
    val wd = "월화수목금토일"[at.dayOfWeek.value - 1]
    return "다음: ${at.monthValue}/${at.dayOfMonth}($wd) ${"%02d:%02d".format(at.hour, at.minute)}"
}

private val MONEY_VERBS = setOf(LogicVerb.ADD_MONEY, LogicVerb.ADD_INCOME, LogicVerb.ENVELOPE)

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
internal fun whenFor(r: LogicRule, t: RuleTrigger, values: List<com.bamnat.core.formula.LogicValue> = emptyList()): LogicRule = when {
    t == r.on -> r
    t == RuleTrigger.END -> r.copy(on = t, min = 0)
    t == RuleTrigger.START && r.on == RuleTrigger.END && r.min == 0 -> r.copy(on = t, min = -60)
    t == RuleTrigger.START -> r.copy(on = t)
    // PC D9 (높): '매일'은 보이는 시각이 실제 값이에요 (흐린 21:00인데 요약은 09:00, 저장은 말없이 안 되던 것). 몇 분 전·뒤는 근무 시작·끝에만.
    t == RuleTrigger.DAILY -> r.copy(on = t, at = timeOf(r.at.orEmpty()) ?: "21:00", min = 0)
    // 1.39 [FX-12][FX-15]
    t == RuleTrigger.VALUE -> r.copy(on = t, min = 0, of = r.of ?: values.firstOrNull()?.name)
    t == RuleTrigger.EVERY -> r.copy(on = t, min = 60, at = null, until = null)
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
    LogicVerb.ENVELOPE to "봉투 넣기",
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
    r.on == RuleTrigger.VALUE && r.of == null -> "어느 값 칸이 바뀌면 할지 골라 주세요"
    r.on == RuleTrigger.EVERY && !r.inWork && (r.at != null || r.until != null) && (timeOf(r.at.orEmpty()) == null || timeOf(r.until.orEmpty()) == null) -> "부터·까지 시각을 07:00처럼 넣어 주세요"
    r.cond.isNotEmpty() && Formula.problem(r.cond) != null -> "조건 수식을 고쳐 주세요"
    else -> r.acts.firstNotNullOfOrNull(::actProblem)
}

/** Why one 동작 cannot be saved yet. */
internal fun actProblem(a: LogicAct): String? = when {
    a.verb in MONEY_VERBS && a.amount == null -> "금액을 넣어 주세요"
    a.verb.pro && !com.bamnat.core.formula.LogicValue.isName(a.arg) -> "값 칸을 골라 주세요"
    a.verb == LogicVerb.MEMO_LINE && a.arg.isBlank() -> "메모에 더할 한 줄을 써 주세요"
    a.verb == LogicVerb.NOTIFY && a.arg.isBlank() -> "알림 글을 써 주세요"
    a.verb == LogicVerb.VAL_TEXT && a.text.isBlank() -> "적을 글을 써 주세요"
    else -> null
}

/**
 * The rule as saved: 시각 said as 07:00, and only the 언제 fields its trigger reads (값이 바뀌면 → 값 칸, 몇 분마다 → 사이·근무 중), '아니면'
 * only with a 조건.
 */
internal fun ruleDone(r: LogicRule): LogicRule = r.copy(
    at = when (r.on) {
        RuleTrigger.DAILY -> timeOf(r.at.orEmpty()) ?: r.at
        RuleTrigger.EVERY -> if (r.inWork) null else r.at?.let { timeOf(it) ?: it }
        else -> r.at
    },
    until = if (r.on == RuleTrigger.EVERY && !r.inWork) r.until?.let { timeOf(it) ?: it } else null,
    inWork = r.on == RuleTrigger.EVERY && r.inWork,
    of = if (r.on == RuleTrigger.VALUE) r.of else null,
    goal = r.on == RuleTrigger.VALUE && r.goal,
    otherwise = if (r.cond.isEmpty()) emptyList() else r.otherwise,
)

/** [RUL-06] The 알림 글 a new rule starts with, said for its 언제 and 근무: "{근무끝}에 주간 근무가 끝나요". */
internal fun notifyWords(on: RuleTrigger, role: String?): String {
    val w = role?.let { LogicRule.ROLE_WORDS[it] }?.let { "$it " }.orEmpty()
    return when (on) {
        RuleTrigger.START -> "{근무시작}에 ${w}근무가 시작돼요"
        RuleTrigger.END -> "{근무끝}에 ${w}근무가 끝나요"
        RuleTrigger.DAILY -> "오늘 {근무} · 확인할 시간이에요"
        RuleTrigger.MEMO -> "메모를 적었어요"
        RuleTrigger.MONEY -> "이달 지출 {원(이달지출)}이에요"
        RuleTrigger.VALUE -> "값이 바뀌었어요"
        RuleTrigger.EVERY -> "잠깐 쉬어 갈 시간이에요"
        RuleTrigger.ALARM_OFF -> "좋은 아침이에요. 오늘 {근무}예요"
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
    /** [RUL-24] PC D19: 설정's read-only lines open the studio's 규칙·단추 when tapped (줄을 눌러도 아무 일이 없었어요). */
    onOpen: (() -> Unit)? = null,
) = androidx.compose.runtime.CompositionLocalProvider(LocalFxLogic provides logic) {
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
    // [DRW-03] 1.37 넣었어요 · 되돌리기: a rule switched on says so, with 되돌리기 (off again), as a theme put on does.
    fun saidOn(r: LogicRule) = com.bamnat.app.ui.PutOnNotice.say("‘${r.name}’ 규칙을 켰어요. ${nextLine(r, facts) ?: "조건이 맞으면 저절로 해요"}".trim()) {
        store.setOff(r.key, true); bump++; LogicRunner.schedule(c); true
    }
    var editValue by remember { mutableStateOf<Int?>(null) }
    // 1.39 PC 5차 (스튜디오 규칙 목록 '{값("야간")}'): the lines say their blanks as this phone's day would fill them, as 미리 돌려 보기 does.
    val words = remember(facts, day, bump) { facts?.let { f -> runCatching { f.scope(day) }.getOrNull() } }
    fun plain(text: String) = com.bamnat.app.ui.recipe.sample(text, words)
    var history by remember { mutableStateOf<com.bamnat.core.formula.LogicValue?>(null) }
    history?.let { v -> ValueHistory(v, { history = null }) { bump++ } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1.39 [FX-01] 프로 문: what of this work is a 프로 기능 장치, and whether it runs on this phone.
        FxDoor(logic)
        // 1.39 [FX-20] 예시로 시작 · [FX-18] 일주일 미리 돌려 보기 (둘 다 무료, 만드는 곳에서만).
        if (!readOnly && !phone) FxStarters(logic, onChange, facts, day)
        if (show != LogicShow.RULES) {
        Text("단추", style = MaterialTheme.typography.titleSmall)
        if (logic.buttons.isEmpty()) Text("하루 화면·메모장·달력에 단추를 놓으면 한 번 눌러 메모 틀을 열거나 가계부에 넣을 수 있어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logic.buttons.forEachIndexed { i, b ->
            val sameB = remember(bump, b.id) { store.sameButton(b.id)?.let { "받은 ‘$it’${com.bamnat.app.ui.Fmt.wa(it)} 같음" } }
            val shows = remember(bump, b.show, facts, day) { showLine(b, facts, day) }
            // [RVM-20] PC-61: the 단추 by its name and place, then what a press does in a few words.
            LineCard(listOfNotNull(b.emoji, b.label).joinToString(" ") + " · " + b.place.label, plain(b.brief()),
                null, null, more = listOfNotNull(shows, sameB, if (phone) "지난 7일 ${store.runs(b.label)}번" else null).joinToString(" · ").ifEmpty { null },
                onEdit = if (readOnly) onOpen else ({ editButton = i }), onRemove = { onChange(logic.copy(buttons = logic.buttons.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly })
        }
        if (!readOnly && logic.buttons.size < Logic.MAX_BUTTONS) TextButton(onClick = { editButton = -1 }) { Text("+ 단추 만들기") }
        }

        if (show != LogicShow.BUTTONS) {
        Text("규칙", style = MaterialTheme.typography.titleSmall)
        // 2.0.0 [RVH-41] 검토 T30-84: the example in the kind's words (an investor has no 근무 to go by).
        val style = com.bamnat.app.ui.flags.rememberScreenStyle()
        if (logic.rules.isEmpty()) Text("‘언제 → 이럴 때만 → 무엇을’로 저절로 돌게 해요. 예: " + when (style) {
                com.bamnat.core.work.WorkStyle.INVEST -> ""
                com.bamnat.core.work.WorkStyle.BUSINESS -> "영업 시작 1시간 전에 알림, "
                com.bamnat.core.work.WorkStyle.FIELD -> "일정 시작 1시간 전에 알림, "
                null, com.bamnat.core.work.WorkStyle.SHIFT -> "야간 근무 1시간 전에 알림, "
                else -> "근무 1시간 전에 알림, "
            } + "날짜를 누르면 그날 메모 틀 열기",
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
            // [RVM-20] PC-61 (기계 말투, '켜기' 글 단추와 스위치가 둘 다): the name on one line, what it does in a few words on the next, and
            // on and off by the switch alone (a 받은 규칙 asks first there).
            LineCard("${r.name}${if (nth > 0) " (${nth + 1})" else ""}", plain(r.brief()),
                if (phone) on else null, { v -> if (v && came) askOn = r else { store.setOff(r.key, !v); bump++; LogicRunner.schedule(c); if (v) saidOn(r) } },
                more = note,
                onEdit = if (readOnly) onOpen else ({ editRule = i }), onRemove = { onChange(logic.copy(rules = logic.rules.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly },
                action = when {
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
        // 1.39 [FX-03] 값 칸: numbers the work counts (물 잔, 배달 건수), read by 값("이름") and shown on the 단추 위젯.
        Text("값 칸 ✦", style = MaterialTheme.typography.titleSmall)
        if (logic.values.isEmpty()) Text("작품이 스스로 세는 숫자예요. 단추로 +1 하고, 매일·매주·매달 저절로 처음으로 돌아가요. 수식에서는 값(\"물 잔\")으로 읽어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        logic.values.forEachIndexed { i, v ->
            val now = remember(bump, v) { FxValues.now(v.name) }
            LineCard(v.say(), now?.let { "지금 ${v.show(it)}" }, null, null, onEdit = if (readOnly) onOpen else ({ editValue = i }),
                onRemove = { onChange(logic.copy(values = logic.values.filterIndexed { j, _ -> j != i })) }.takeIf { !readOnly },
                // 1.39 [FX-26] 값 칸 기록 보기·고치기 (설정의 목록에서).
                action = if (phone && v.kind == com.bamnat.core.formula.ValueKind.NUM && v.reset != com.bamnat.core.formula.ValueReset.NEVER) "기록 보기" to { history = v } else null)
        }
        if (!readOnly && logic.values.size < com.bamnat.core.formula.LogicValue.MAX) TextButton(onClick = { editValue = -1 }) { Text("+ 값 칸 만들기") }
        if (phone && store.undo != null) OutlinedButton(onClick = { c.appScope.launch { LogicRunner.undo(c); bump++ } }) { Text("마지막 동작 되돌리기") }
        askOn?.let { r ->
            AlertDialog(onDismissRequest = { askOn = null },
                title = { Text("받은 규칙을 켤까요?") },
                // [RVM-23] STU-23: every step and its condition (not the first step alone), and the 쓰기 line only when a step writes.
                text = { Text("‘${r.name}’: ${r.say()}\n\n" + (if (r.acts.any { it.verb.writes }) "메모나 가계부를 바꾸는 규칙이에요. 바뀐 것은 아래 '규칙이 쓴 것'에서 되돌릴 수 있어요." else "켜면 바로 움직여요. 끄면 멈춰요.")) },
                confirmButton = { TextButton(onClick = { store.setOff(r.key, false); askOn = null; bump++; LogicRunner.schedule(c); saidOn(r) }) { Text("켜기") } },
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
            // 1.39 [FX-06] B-3 '왜 안 됐지': every run and skip with why, newest first (조건·꺼 둠·프로 기능 장치·놓침).
            val log = remember(bump) { store.log() }
            var logOpen by remember { mutableStateOf(false) }
            if (log.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("최근 기록 (왜 안 됐지?) ${log.size}줄", style = MaterialTheme.typography.bodyMedium)
                        val skipped = log.count { !it.what.endsWith("를 했어요") }
                        if (!logOpen && skipped > 0) Text("안 한 것 ${skipped}번 · 마지막: ${log.last { !it.what.endsWith("를 했어요") }.let { "${it.who} ${it.what}" }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    TextButton(onClick = { logOpen = !logOpen }) { Text(if (logOpen) "접기" else "보기") }
                    if (logOpen) TextButton(onClick = { store.clearLog(); bump++ }) { Text("비우기") }
                }
                if (logOpen) log.asReversed().forEach { l ->
                    val at = java.time.Instant.ofEpochMilli(l.at).atZone(com.bamnat.core.time.Today.zone())
                    val ran = l.what.endsWith("를 했어요")
                    Text("${at.monthValue}/${at.dayOfMonth} ${"%02d:%02d".format(at.hour, at.minute)} · ${l.who}: ${l.what}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ran) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary)
                }
            }
            if (asking) com.bamnat.app.ui.components.AlertDialog(
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
    editValue?.let { i ->
        ValueDialog(logic.values.getOrNull(i), taken = logic.values.filterIndexed { j, _ -> j != i }.map { it.name }.toSet(), onDone = { v ->
            // A 값 칸 renamed: the buttons and rules that counted it follow.
            val old = logic.values.getOrNull(i)?.name
            fun follow(a: LogicAct) = if (old != null && old != v.name && a.verb.pro && a.arg == old) a.copy(arg = v.name) else a
            fun follow(l: List<LogicAct>) = l.map(::follow)
            onChange(logic.copy(values = if (i < 0) logic.values + v else logic.values.toMutableList().also { it[i] = v },
                buttons = logic.buttons.map { it.copy(act = follow(it.act), more = follow(it.more), otherwise = follow(it.otherwise)) },
                rules = logic.rules.map { it.copy(act = follow(it.act), more = follow(it.more), otherwise = follow(it.otherwise),
                    of = if (old != null && it.of == old) v.name else it.of) }))
            editValue = null
        }, onDismiss = { editValue = null })
    }
}

/**
 * 1.39 [FX-20] 예시로 시작: 여섯 가지 기능 예시를 눌러 값 칸·단추·규칙을 한꺼번에 넣고, [FX-18] 일주일 미리 돌려 보기로 내 근무표에서 7일
 * 동안 언제 무엇을 할지(값 칸이 어떻게 바뀔지) 봐요. 아무것도 실제로 하지 않아요.
 */
@Composable
private fun FxStarters(logic: Logic, onChange: (Logic) -> Unit, facts: com.bamnat.core.formula.DayFactsBook?, day: LocalDate) {
    var week by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf<String?>(null) }
    var put by remember { mutableStateOf<String?>(null) }
    Text("예시로 시작", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.bamnat.core.formula.FxExamples.ALL.forEach { e ->
            AssistChip(onClick = {
                val next = com.bamnat.core.formula.FxExamples.into(logic, e)
                if (next == null) full = "자리가 모자라요. 단추 ${Logic.MAX_BUTTONS}개·규칙 ${Logic.MAX_RULES}개·값 칸 ${com.bamnat.core.formula.LogicValue.MAX}개까지예요"
                // 4차 PC: the runner's message is not shown in the 스튜디오, so the line stays right under the chips.
                else { full = null; onChange(next); put = "‘${e.title}’ 예시를 넣었어요. ${e.say}" }
            }, label = { Text("${e.emoji} ${e.title}") })
        }
    }
    full?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (full == null) put?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    if (logic.rules.isNotEmpty() && facts != null) OutlinedButton(onClick = { week = true }) { Text("일주일 미리 돌려 보기") }
    if (week && facts != null) {
        val w = remember(logic, facts, day) { com.bamnat.core.formula.LogicWeek.run(logic, facts, day) }
        AlertDialog(onDismissRequest = { week = false },
            title = { Text("일주일 미리 돌려 보기") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("내 근무표로 ${day.monthValue}/${day.dayOfMonth}부터 7일을 빨리 돌려 봤어요. 실제로는 아무것도 하지 않아요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (w.lines.isEmpty()) Text("7일 동안 저절로 도는 때가 없어요. 근무 시작·끝, 매일, 몇 분마다 규칙이 돌아요")
                    w.lines.groupBy { it.date }.forEach { (d, ls) ->
                        Text("${d.monthValue}/${d.dayOfMonth}(${"월화수목금토일"[d.dayOfWeek.value - 1]})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        ls.forEach { l ->
                            Text("${l.at?.let { "%02d:%02d".format(it.hour, it.minute) } ?: ""} ${l.who}: ${l.what}".trim(), style = MaterialTheme.typography.bodySmall,
                                color = if (l.ran) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (w.lines.size >= com.bamnat.core.formula.LogicWeek.MAX_LINES) Text("너무 많아 앞의 ${com.bamnat.core.formula.LogicWeek.MAX_LINES}줄만 보여요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    if (w.values.isNotEmpty()) Text("7일째 값 칸: " + w.values.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    w.waiting.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            confirmButton = { TextButton(onClick = { week = false }) { Text("닫기") } })
    }
}

/** 1.39 [FX-03][FX-05] The work open in the editor, for the 무엇을 part (its 값 칸 and buttons). */
internal val LocalFxLogic = androidx.compose.runtime.compositionLocalOf { Logic() }

/**
 * 1.39 [FX-01] 프로 문 card (사용자 2026-11-07 "무료 프로의 구분은 확실히"): which 프로 기능 장치 the work has, that making and previewing
 * them is free, and how they run here — 프로, 하루 써 보기 (once, [com.bamnat.core.formula.FxTaste.OWN] for one's own), or resting.
 * Nothing shows for a work of only free things.
 */
@Composable
fun FxDoor(logic: Logic, key: String = com.bamnat.core.formula.FxTaste.OWN) {
    val c = LocalContext.current.appContainer
    val parts = remember(logic) { com.bamnat.core.formula.FxPro.uses(logic) }
    if (parts.isEmpty()) return
    val gate by c.featureGate.state.collectAsState()
    val taste by FxAccess.taste(c).collectAsState()
    val now = com.bamnat.core.time.Today.instant()
    val pro = remember(gate) { FxAccess.pro(c) }
    val status = remember(taste, gate) { FxAccess.status(c, key, now) }
    val running = remember(taste) { taste?.running(now) }
    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 2.0.0 [RVM-12] PC-59·61: the 프로 문 in the words of the one who uses it (설정 › 수식·규칙·버튼 too); each ✦ part is told on its button or rule.
            Text("✦ 프로 기능 · ${com.bamnat.core.formula.FxPro.plain(parts)}", style = MaterialTheme.typography.titleSmall)
            Text(when {
                pro -> "프로라 이 기능이 모두 돌아요"
                running != null -> "하루 써 보기 중이에요 · ${fxUntil(running.second)}까지 모두 돌아요"
                else -> "만들기와 미리 보기는 무료예요. 내 앱에서 돌리고 장터에 올리는 건 프로예요. 그동안 ✦ 없는 단추·규칙은 그대로 돌아요"
            }, style = MaterialTheme.typography.bodySmall)
            if (!pro && running == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status is com.bamnat.core.formula.FxTaste.Status.Ready) androidx.compose.material3.Button(onClick = {
                    if (FxAccess.start(c, key)) LogicRunner.say("하루 동안 프로 기능 장치를 써 봐요. 끝나면 한 일을 알려 드려요")
                }) { Text("하루 써 보기") }
                OutlinedButton(onClick = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PRO) }) { Text("프로 보기") }
            }
        }
    }
}

/** "오늘 21:40", "내일 09:10". */
internal fun fxUntil(at: java.time.Instant): String {
    val z = at.atZone(com.bamnat.core.time.Today.zone())
    val day = if (z.toLocalDate() == com.bamnat.core.time.Today.date()) "오늘" else "내일"
    return "$day ${"%02d:%02d".format(z.hour, z.minute)}"
}

/**
 * 1.39 [FX-26] 값 칸 기록: 지난 14기간 막대(목표선), 연속·합계, 그리고 잘못 누른 날 바로잡기(그 기간을 눌러 숫자 고치기).
 */
@Composable
private fun ValueHistory(v: com.bamnat.core.formula.LogicValue, onClose: () -> Unit, onFixed: () -> Unit) {
    val c = LocalContext.current.appContainer
    val all by FxValues.flow(c).collectAsState()
    val today = remember { com.bamnat.core.time.Today.date() }
    val kept = all?.get(v.name)
    val n = 14
    val days = remember(today) { (n - 1 downTo 0).map { v.reset.back(today, it) } }
    val nums = days.map { com.bamnat.core.formula.ValueBook.on(kept, v, it, today) }
    val top = (nums.filterNotNull() + listOfNotNull(v.goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    var fixing by remember { mutableStateOf<LocalDate?>(null) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text("‘${v.name}’ 기록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val s = com.bamnat.core.formula.ValueSeries(v, kept, today, com.bamnat.core.time.Today.dateTime())
                Text(listOfNotNull(
                    v.goal?.let { "목표 연속 ${s.streak()}번" },
                    "최근 7${if (v.reset == com.bamnat.core.formula.ValueReset.DAY) "일" else "번"} 합계 ${com.bamnat.core.formula.Formula.numText(s.total(7))}${v.unit}",
                ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEachIndexed { i, d ->
                        val x = nums[i]
                        val reached = v.goal != null && x != null && x >= v.goal!!
                        Column(Modifier.weight(1f).clickable(onClickLabel = "${d.monthValue}/${d.dayOfMonth} 고치기") { fixing = d }
                            .semantics { contentDescription = "${d.monthValue}/${d.dayOfMonth} ${x?.let { com.bamnat.core.formula.Formula.numText(it) } ?: "기록 없음"}" },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()
                                .height(((x ?: 0.0) / top * 80).coerceIn(2.0, 80.0).dp)
                                .background(if (x == null) MaterialTheme.colorScheme.surfaceVariant else if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary))
                        }
                    }
                }
                Text("${days.first().monthValue}/${days.first().dayOfMonth} ~ 오늘 · 막대를 누르면 그날 숫자를 고쳐요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                fixing?.let { d ->
                    var text by remember(d) { mutableStateOf(com.bamnat.core.formula.ValueBook.on(kept, v, d, today)?.let(com.bamnat.core.formula.Formula::numText).orEmpty()) }
                    OutlinedTextField(text, { t -> text = t.filter { it.isDigit() || it == '.' || it == '-' }.take(10) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("${d.monthValue}/${d.dayOfMonth} 숫자") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextButton(onClick = {
                        text.toDoubleOrNull()?.let { to -> if (FxValues.fix(c, v.name, d, to)) { fixing = null; onFixed(); LogicRunner.say("${d.monthValue}/${d.dayOfMonth} ‘${v.name}’를 고쳤어요") } }
                    }, enabled = text.toDoubleOrNull() != null) { Text("고치기") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } })
}

/** 1.39 [FX-03] 값 칸 만들기·고치기. */
@Composable
private fun ValueDialog(start: com.bamnat.core.formula.LogicValue?, taken: Set<String>, onDone: (com.bamnat.core.formula.LogicValue) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(start ?: com.bamnat.core.formula.LogicValue("물 잔", com.bamnat.core.formula.ValueReset.DAY, goal = 8.0, unit = "잔")) }
    var goal by remember { mutableStateOf(v.goal?.let(com.bamnat.core.formula.Formula::numText).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) "값 칸 만들기" else "값 칸 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(v.name, { v = v.copy(name = it.take(12)) }, Modifier.weight(1f), singleLine = true, label = { Text("이름") })
                    OutlinedTextField(v.unit, { v = v.copy(unit = it.take(com.bamnat.core.formula.LogicValue.MAX_UNIT)) }, Modifier.width(88.dp), singleLine = true, label = { Text("단위") })
                }
                // 1.39 [FX-13] 숫자·시각·글.
                Text("무엇을 적나요", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bamnat.core.formula.ValueKind.entries.forEach { k -> FilterChip(selected = v.kind == k, onClick = {
                        v = if (k == com.bamnat.core.formula.ValueKind.NUM) v.copy(kind = k) else v.copy(kind = k, goal = null, unit = ""); if (k != com.bamnat.core.formula.ValueKind.NUM) goal = ""
                    }, label = { Text(k.label + when (k) { com.bamnat.core.formula.ValueKind.TIME -> " (약 먹은 때)"; com.bamnat.core.formula.ValueKind.TEXT -> " (오늘 기분)"; else -> " (물 잔)" }) }) }
                }
                Text("처음으로 돌아가기", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bamnat.core.formula.ValueReset.entries.forEach { r -> FilterChip(selected = v.reset == r, onClick = { v = v.copy(reset = r) }, label = { Text(r.label) }) }
                }
                if (v.kind == com.bamnat.core.formula.ValueKind.NUM) OutlinedTextField(goal, { s -> goal = s.filter { it.isDigit() || it == '.' }.take(9); v = v.copy(goal = goal.toDoubleOrNull()?.takeIf { it > 0 }) },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("목표 (비우면 없음)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text(v.say(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (v.name in taken) Text("같은 이름의 값 칸이 이미 있어요", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(v.copy(name = v.name.trim())) }, enabled = com.bamnat.core.formula.LogicValue.isName(v.name.trim()) && v.name.trim() !in taken) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun LineCard(text: String, note: String?, on: Boolean?, onSwitch: ((Boolean) -> Unit)?, onEdit: (() -> Unit)? = null, onRemove: (() -> Unit)? = null,
    /** PC D8: one more thing to do on the line (놓친 규칙 '지금 한 번 하기'). */
    action: Pair<String, () -> Unit>? = null,
    /** [RVM-20] PC-61: when it comes next, what happened last time, 지난 7일 (smaller, under what it does). */
    more: String? = null) {
    Card(Modifier.fillMaxWidth().then(if (onEdit != null) Modifier.clickable(onClickLabel = "고치기", onClick = onEdit) else Modifier)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                more?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                // [RVM-20] 총괄 #462 (줄마다 '고치기' 글자 단추가 줄지어 8개, 조건 2): the line's one more thing is a small chip, not a 글자 단추.
                action?.let { (w, go) ->
                    androidx.compose.material3.AssistChip(onClick = go, label = { Text(w) }, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (on != null && onSwitch != null) Switch(on, onSwitch, Modifier.semantics { contentDescription = if (on) "규칙 켜짐" else "규칙 꺼짐" })
            // The whole line opens it (the card's click, 'onClickLabel 고치기'); the arrow only says so.
            if (onEdit != null) androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            onRemove?.let {
                androidx.compose.material3.IconButton(onClick = it) {
                    androidx.compose.material3.Icon(Icons.Outlined.Delete, contentDescription = "빼기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
