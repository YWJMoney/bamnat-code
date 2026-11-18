package com.bamnat.app.logic

import com.bamnat.app.appContainer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bamnat.core.formula.Block
import com.bamnat.core.formula.Formula
import com.bamnat.core.formula.FormulaBlocks
import com.bamnat.core.formula.FormulaName
import com.bamnat.core.formula.FormulaNames
import com.bamnat.core.formula.FormulaScope

/**
 * 누락 점검 N7 · 계획 5장 쓰는 법 2 '블록' (숙련자용): the formula as blocks that fit into each other. 빈칸을 누르면 블록을 골라 넣고, 블록의
 * ⋮로 그리고·또는·아님·만약으로 감싸거나 지워요. The blocks write the same text as 수식으로 (아래 줄), and it is handed on only when every
 * 빈칸 is filled, so a half-made block never becomes a broken rule.
 */
@Composable
fun BlockEditor(value: String, onChange: (String) -> Unit, scope: FormulaScope, modifier: Modifier = Modifier) {
    // The blocks being made; holes stay here until they are filled. A new value from outside (another view) starts again from it.
    var draft by remember(value) { mutableStateOf(FormulaBlocks.read(value)) }
    val d = draft
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d == null) {
            Text("이 수식에 틀린 곳이 있어 블록으로 열 수 없어요. '수식으로'에서 고친 뒤 다시 와 주세요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            return@Column
        }
        BlockView(d, { b ->
            draft = b
            when {
                b == Block.Hole -> onChange("")
                b.complete -> onChange(b.text())
            }
        }, 0)
        val holes = remember(d) { countHoles(d) }
        Text(
            if (holes > 0) "빈칸 ${holes}개를 채우면 규칙에 들어가요 · ${d.text()}" else if (d == Block.Hole) "빈칸을 눌러 첫 블록을 넣어요" else "수식: ${d.text()}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun countHoles(b: Block): Int = when (b) {
    Block.Hole -> 1
    is Block.Op -> countHoles(b.a) + countHoles(b.b)
    is Block.Not -> countHoles(b.x)
    is Block.Neg -> countHoles(b.x)
    is Block.Call -> b.args.sumOf(::countHoles)
    else -> 0
}

/** a 그리고 b 그리고 c as one list (shown as one block with three rows). */
private fun flat(b: Block, op: String): List<Block> = if (b is Block.Op && b.op == op) flat(b.a, op) + flat(b.b, op) else listOf(b)

private fun joined(op: String, items: List<Block>): Block = items.reduce { acc, x -> Block.Op(op, acc, x) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockView(b: Block, onSet: (Block) -> Unit, depth: Int) {
    if (b == Block.Hole) { HoleButton(onSet); return }
    val tone = when (b) {
        is Block.Op -> if (b.op in FormulaBlocks.JOIN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        is Block.Call, is Block.Not -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = tone, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "블록 ${b.label()}" }) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (b) {
                is Block.Op -> if (b.op in FormulaBlocks.JOIN) {
                    val items = flat(b, b.op)
                    Header(if (b.op == "and") "모두 맞으면 (그리고)" else "하나라도 맞으면 (또는)", b, onSet) {
                        DropdownMenuItem(text = { Text(if (b.op == "and") "또는으로 바꾸기" else "그리고로 바꾸기") },
                            onClick = { it(); onSet(joined(if (b.op == "and") "or" else "and", items)) })
                    }
                    items.forEachIndexed { i, x ->
                        if (i > 0) Text(FormulaBlocks.opWord(b.op), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        BlockView(x, { n ->
                            // 지우기 on one of three or more takes it out of the list; with two it leaves the 빈칸 to fill.
                            val next = if (n == Block.Hole && items.size > 2) items.filterIndexed { j, _ -> j != i } else items.toMutableList().also { it[i] = n }
                            onSet(joined(b.op, next))
                        }, depth + 1)
                    }
                    TextButton(onClick = { onSet(joined(b.op, items + Block.Hole)) }) { Text("+ 조건 하나 더") }
                } else if (simple(b.a) && simple(b.b)) {
                    // [근무종류] [같음 ▾] [야간] on one line (PC가 보기 전 화면 사진: 비교 하나가 세 칸 높이였어요).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                            Leaf(b.a) { onSet(b.copy(a = it)) }
                            OpChip(b) { onSet(b.copy(op = it)) }
                            // PC D6 (사용성 중): 근무종류·요일·일 유형이면 오른쪽은 고르는 칩 (주간·야간·휴무 …).
                            // 2.0.0 [RVS-10] 검토 STU-27: 근무종류 chips are the work's own kinds, in its words.
                            val style = androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.current.workStyle
                            val choices = (b.a as? Block.Name)?.let { choicesFor(it.name, style, (b.b as? Block.Text)?.s) }
                            if (choices != null && (b.b is Block.Text || b.b == Block.Hole)) choices.forEach { (v, w) ->
                                com.bamnat.app.ui.components.FilterChip(selected = (b.b as? Block.Text)?.s == v, onClick = { onSet(b.copy(b = Block.Text(v))) }, label = { Text(w) })
                            } else Leaf(b.b) { onSet(b.copy(b = it)) }
                        }
                        BlockMenu(b, onSet) { close ->
                            DropdownMenuItem(text = { Text("왼쪽 비우기 (다른 블록 넣기)") }, onClick = { close(); onSet(b.copy(a = Block.Hole)) })
                            DropdownMenuItem(text = { Text("오른쪽 비우기 (다른 블록 넣기)") }, onClick = { close(); onSet(b.copy(b = Block.Hole)) })
                        }
                    }
                } else {
                    Header(FormulaBlocks.opWord(b.op), b, onSet) { close ->
                        val family = if (b.op in FormulaBlocks.COMPARE) FormulaBlocks.COMPARE else FormulaBlocks.MATH
                        family.forEach { (k, w) -> DropdownMenuItem(text = { Text("$w ($k)") }, onClick = { close(); onSet(b.copy(op = k)) }) }
                    }
                    BlockView(b.a, { onSet(b.copy(a = it)) }, depth + 1)
                    BlockView(b.b, { onSet(b.copy(b = it)) }, depth + 1)
                }
                is Block.Not -> {
                    Header("아님 (뒤집기)", b, onSet) { close -> DropdownMenuItem(text = { Text("아님 풀기") }, onClick = { close(); onSet(b.x) }) }
                    BlockView(b.x, { onSet(Block.Not(it)) }, depth + 1)
                }
                is Block.Neg -> {
                    Header("빼기 (음수)", b, onSet) { close -> DropdownMenuItem(text = { Text("음수 풀기") }, onClick = { close(); onSet(b.x) }) }
                    BlockView(b.x, { onSet(Block.Neg(it)) }, depth + 1)
                }
                is Block.Call -> CallBlock(b, onSet, depth)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Leaf(b, onSet)
                    Text((b as? Block.Name)?.let { FormulaNames.of(it.name)?.help }.orEmpty(), Modifier.weight(1f).padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    BlockMenu(b, onSet)
                }
            }
        }
    }
}

/** A value with nothing inside it, drawn small in a line. */
private fun simple(b: Block) = b is Block.Name || b is Block.Num || b is Block.Text || b is Block.Bool || b == Block.None || b == Block.Hole

@Composable
private fun Leaf(b: Block, onSet: (Block) -> Unit) {
    when (b) {
        is Block.Name -> NameChip(b.name) { onSet(Block.Name(it)) }
        is Block.Text -> OutlinedTextField(b.s, { onSet(Block.Text(it.take(Formula.MAX_TEXT))) }, Modifier.width(132.dp), singleLine = true,
            placeholder = { Text("글") }, textStyle = MaterialTheme.typography.bodyMedium)
        is Block.Num -> {
            var t by remember(b) { mutableStateOf(Formula.numText(b.v)) }
            OutlinedTextField(t, { v -> t = v; v.replace(",", "").toDoubleOrNull()?.let { onSet(Block.Num(it)) } }, Modifier.width(112.dp), singleLine = true,
                placeholder = { Text("숫자") }, textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        is Block.Bool -> AssistChip(onClick = { onSet(Block.Bool(!b.v)) }, label = { Text(if (b.v) "참" else "거짓") })
        Block.Hole -> {
            var open by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { open = true }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text("＋ 빈칸") }
            if (open) Palette({ open = false }) { onSet(it); open = false }
        }
        else -> AssistChip(onClick = {}, label = { Text(b.label()) })
    }
}

@Composable
private fun OpChip(b: Block.Op, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val family = if (b.op in FormulaBlocks.COMPARE) FormulaBlocks.COMPARE else FormulaBlocks.MATH
    AssistChip(onClick = { open = true }, label = { Text(FormulaBlocks.opWord(b.op) + " ▾") })
    DropdownMenu(open, { open = false }) {
        family.forEach { (k, w) -> DropdownMenuItem(text = { Text("$w ($k)") }, onClick = { open = false; onPick(k) }) }
    }
}

/** 만약 조건 이면 값 / 아니고 만약 … / 나머지 값; 고르기 값 / 이 값이면 …; any other function its values in order. */
@Composable
private fun CallBlock(b: Block.Call, onSet: (Block) -> Unit, depth: Int) {
    val range = FormulaBlocks.arity(b.fn) ?: (b.args.size..b.args.size)
    val help = FormulaNames.FUNCTIONS.firstOrNull { it.ko == b.fn }?.help.orEmpty()
    Header(b.fn, b, onSet) { }
    if (help.isNotEmpty()) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    fun set(i: Int, n: Block) = onSet(b.copy(args = b.args.toMutableList().also { it[i] = n }))
    when (b.fn) {
        "만약" -> {
            b.args.forEachIndexed { i, x ->
                val last = i == b.args.lastIndex && b.args.size % 2 == 1
                val word = when {
                    last -> "나머지는"
                    i % 2 == 0 -> if (i == 0) "만약" else "아니고 만약"
                    else -> "이면"
                }
                Text(word, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                BlockView(x, { set(i, it) }, depth + 1)
            }
            Row {
                if (b.args.size + 2 <= range.last) TextButton(onClick = {
                    val at = if (b.args.size % 2 == 1) b.args.lastIndex else b.args.size
                    onSet(b.copy(args = b.args.toMutableList().also { it.addAll(at, listOf(Block.Hole, Block.Hole)) }))
                }) { Text("+ 아니고 만약") }
                if (b.args.size - 2 >= range.first) TextButton(onClick = {
                    val at = if (b.args.size % 2 == 1) b.args.lastIndex - 2 else b.args.size - 2
                    onSet(b.copy(args = b.args.toMutableList().also { it.removeAt(at); it.removeAt(at) }))
                }) { Text("− 마지막 조건 빼기") }
            }
        }
        "고르기" -> {
            b.args.forEachIndexed { i, x ->
                val last = i == b.args.lastIndex && b.args.size % 2 == 0
                val word = when {
                    i == 0 -> "이 값이"
                    last -> "나머지는"
                    i % 2 == 1 -> "이것과 같으면"
                    else -> "이 값"
                }
                Text(word, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                BlockView(x, { set(i, it) }, depth + 1)
            }
            if (b.args.size + 2 <= range.last) TextButton(onClick = {
                val at = if (b.args.size % 2 == 0) b.args.lastIndex else b.args.size
                onSet(b.copy(args = b.args.toMutableList().also { it.addAll(at, listOf(Block.Hole, Block.Hole)) }))
            }) { Text("+ 경우 하나 더") }
        }
        else -> if (b.args.all(::simple)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { b.args.forEachIndexed { i, x -> Leaf(x) { set(i, it) } } }
            Row {
                if (b.args.size < range.last) TextButton(onClick = { onSet(b.copy(args = b.args + Block.Hole)) }) { Text("+ 값 더하기") }
                if (b.args.size > range.first) TextButton(onClick = { onSet(b.copy(args = b.args.dropLast(1))) }) { Text("− 마지막 값 빼기") }
            }
        } else {
            b.args.forEachIndexed { i, x ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.padding(top = 10.dp, end = 4.dp), style = MaterialTheme.typography.labelSmall)
                    Column(Modifier.weight(1f)) { BlockView(x, { set(i, it) }, depth + 1) }
                }
            }
            Row {
                if (b.args.size < range.last) TextButton(onClick = { onSet(b.copy(args = b.args + Block.Hole)) }) { Text("+ 값 더하기") }
                if (b.args.size > range.first) TextButton(onClick = { onSet(b.copy(args = b.args.dropLast(1))) }) { Text("− 마지막 값 빼기") }
            }
        }
    }
}

/** A block's title and its ⋮ (감싸기·바꾸기·지우기), with [extra] items first. */
@Composable
private fun Header(title: String, b: Block, onSet: (Block) -> Unit, extra: @Composable (close: () -> Unit) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        BlockMenu(b, onSet, extra)
    }
}

@Composable
private fun BlockMenu(b: Block, onSet: (Block) -> Unit, extra: @Composable (close: () -> Unit) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "${b.label()} 블록 메뉴" }) { Text("⋮") }
    DropdownMenu(open, { open = false }) {
        extra { open = false }
        DropdownMenuItem(text = { Text("그리고로 묶기 (조건 더하기)") }, onClick = { open = false; onSet(Block.Op("and", b, Block.Hole)) })
        DropdownMenuItem(text = { Text("또는으로 묶기") }, onClick = { open = false; onSet(Block.Op("or", b, Block.Hole)) })
        DropdownMenuItem(text = { Text("아님으로 감싸기") }, onClick = { open = false; onSet(Block.Not(b)) })
        DropdownMenuItem(text = { Text("만약으로 감싸기 (이 조건이면…)") }, onClick = { open = false; onSet(Block.Call("만약", listOf(b, Block.Hole, Block.Hole))) })
        DropdownMenuItem(text = { Text("비교로 감싸기 (이 값 = …)") }, onClick = { open = false; onSet(Block.Op("=", b, Block.Hole)) })
        DropdownMenuItem(text = { Text("다른 블록으로 바꾸기") }, onClick = { open = false; replace = true })
        DropdownMenuItem(text = { Text("지우기 (빈칸으로)") }, onClick = { open = false; onSet(Block.Hole) })
    }
    if (replace) Palette({ replace = false }) { onSet(it); replace = false }
}

@Composable
private fun HoleButton(onSet: (Block) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text("＋ 빈칸: 블록 넣기") }
    if (open) Palette({ open = false }) { onSet(it); open = false }
}

/** 블록 고르기: 값 이름 (묶음별), 숫자·글·참·거짓, 조건 (비교·그리고·또는·아님), 함수. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Palette(onDismiss: () -> Unit, onPick: (Block) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("블록 고르기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Section("조건") {
                    AssistChip(onClick = { onPick(FormulaBlocks.compare()) }, label = { Text("비교 (□ = □)") })
                    AssistChip(onClick = { onPick(FormulaBlocks.join("and")) }, label = { Text("그리고") })
                    AssistChip(onClick = { onPick(FormulaBlocks.join("or")) }, label = { Text("또는") })
                    AssistChip(onClick = { onPick(Block.Not(Block.Hole)) }, label = { Text("아님") })
                    AssistChip(onClick = { onPick(FormulaBlocks.call("만약")) }, label = { Text("만약 … 이면 … 나머지") })
                    AssistChip(onClick = { onPick(FormulaBlocks.call("포함")) }, label = { Text("포함 (꼬리표에 …)") })
                    AssistChip(onClick = { onPick(FormulaBlocks.call("사이")) }, label = { Text("사이 (8~12)") })
                }
                Section("값 직접") {
                    AssistChip(onClick = { onPick(Block.Num(0.0)) }, label = { Text("숫자") })
                    AssistChip(onClick = { onPick(Block.Text("")) }, label = { Text("글") })
                    AssistChip(onClick = { onPick(Block.Bool(true)) }, label = { Text("참") })
                    AssistChip(onClick = { onPick(Block.Bool(false)) }, label = { Text("거짓") })
                    AssistChip(onClick = { onPick(Block.None) }, label = { Text("없음") })
                    AssistChip(onClick = { onPick(Block.Op("+", Block.Hole, Block.Hole)) }, label = { Text("계산 (□ + □)") })
                }
                FormulaName.Group.entries.filter { it != FormulaName.Group.ROW }.forEach { g ->
                    Section("값 · ${g.label}") {
                        FormulaName.entries.filter { it.group == g }.forEach { n -> AssistChip(onClick = { onPick(Block.Name(n.ko)) }, label = { Text(n.ko) }) }
                    }
                }
                Section("함수") {
                    FormulaNames.FUNCTIONS.filter { it.ko !in setOf("만약", "포함", "사이", "아님") }.forEach { f ->
                        AssistChip(onClick = { onPick(FormulaBlocks.call(f.ko)) }, label = { Text(f.ko) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, chips: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
}

@Composable
private fun NameChip(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    AssistChip(onClick = { open = true }, label = { Text(current) })
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
