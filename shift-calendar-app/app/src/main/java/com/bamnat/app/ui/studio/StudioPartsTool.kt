package com.bamnat.app.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.settings.Chips
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.app.ui.settings.LabeledSlider
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.BlueprintParts

/**
 * [BLU-06] 1.36 스튜디오 '부품' 도구 (계획 갈래 A 866, 제안 11·5): pick one part of the screen (달력 머리, 날짜 숫자, 탭 막대 …) and the
 * state it is for (기본, 오늘, 일요일, 야간 근무 …), then the same style fields for every part — 채우기·선·모서리·그림자·여백·글자·투명도 —
 * and, for a part that holds others, their order and direction. Every change goes into the draft's `parts` (one step of 되돌리기), and
 * the example above draws it at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartsTool(
    group: String,
    parts: String,
    focus: String? = null,
    focusOn: String? = null,
    /** The part and state being changed, for the line kept above the tools while they scroll (PC 1차 사용성 제안). */
    onFocus: (id: String, on: String?) -> Unit = { _, _ -> },
    /** [BLU-07] 이 부품만 올리기: the unit of the part being changed; null hides the button. */
    onUploadPart: ((unit: String) -> Unit)? = null,
    /** [RUL-06] 스튜디오 › 규칙·단추 › 조건부 꾸미기: the condition row opens first. */
    conditionFirst: Boolean = false,
    /** PC 확인 1.37 A8: goes up each time 조건으로 꾸미기 is asked for, so the condition opens and comes into view even when this was open. */
    conditionAsk: Int = 0,
    /** The rules whose 조건 is this same formula (이 조건을 쓰는 곳). */
    rulesUsing: (String) -> List<String> = { emptyList() },
    onOpenRules: (() -> Unit)? = null,
    /** [LAY-08] 1.36 고급 보기 (누락 점검 N5): 숫자 칸·여러 부품 한꺼번에·색 변수. */
    advanced: Boolean = false,
    /** [LAY-08] PC 확인 C4: how many parts the next change goes to, for the line kept above ('3곳 함께 고치는 중'). */
    onTogether: (Int) -> Unit = {},
    /** [CNV-11] 1.37: the condition being changed here, for the canvas's tool line to write the same line. */
    onWhen: (String?) -> Unit = {},
    onChange: (String) -> Unit,
) {
    val bp = remember(parts) { Blueprint.decode(parts) }
    val inGroup = remember(group) { BlueprintParts.IDS.filter { it == group || it.startsWith("$group.") || (group == "app" && it.startsWith("app")) } }
    // A part pressed in the example (갈래 C '누른 요소 바로 고치기') opens here even when it sits outside this area's group.
    val ids = if (focus != null && focus in BlueprintParts.IDS && focus !in inGroup) inGroup + focus else inGroup
    var id by rememberSaveable(group) { mutableStateOf(focus?.takeIf { it in ids } ?: ids.firstOrNull { it.contains('.') } ?: ids.first()) }
    var on by rememberSaveable(group) { mutableStateOf(focusOn?.takeIf { it in BlueprintParts.STATES }) }
    LaunchedEffect(focus, focusOn) {
        if (focus != null && focus in ids) { id = focus; on = focusOn?.takeIf { it in BlueprintParts.STATES } }
    }
    LaunchedEffect(id, on) { onFocus(id, on) }
    // [LAY-08] 여러 부품 한꺼번에 (고급 보기): the parts ticked here all take the next change, in the same state.
    var many by rememberSaveable(group) { mutableStateOf(false) }
    var multi by rememberSaveable(group) { mutableStateOf(listOf<String>()) }
    LaunchedEffect(many, multi) { onTogether(if (many) multi.size + 1 else 1) }
    // [BLU-09] 누락 점검 N4: 미리 보기에서 누른 곳이 바로 열려요.
    Text("화면을 부품으로 나눠 하나씩 꾸며요. 비워 둔 칸은 지금 모습 그대로예요. 위 미리 보기에서 고칠 곳을 누르면 그 부품이 열리고, 같은 곳을 한 번 더 누르면 그 바깥 부품이 열려요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // PC 확인 C6 1.36 사용성 제안 (여러 부품 스위치를 못 찾았어요): '여럿 고르기' beside the parts, without 고급 보기.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = many, onClick = { many = !many; if (!many) multi = emptyList() },
            label = { Text(if (many) "여럿 고르기 · ${multi.size + 1}곳" else "여럿 고르기") })
        // PC 확인 C7 1.36 사용성 제안 (처음 부품이 같이 세어지는 줄 몰랐어요): the part already open is the first of them, said by name.
        val first = BlueprintParts.LABELS[id] ?: id
        if (many) Text(if (multi.isEmpty()) "지금 연 ‘$first’${com.bamnat.app.ui.Fmt.iga(first)} 첫 곳이에요. 칩을 눌러 더해요"
            else "‘$first’${com.bamnat.app.ui.Fmt.wa(first)} 칩 ${multi.size}개. 고친 값이 고른 곳 모두에 들어가요", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
    // PC 확인 efc52400 사용성 제안: 달력 has some 30 parts and 타임라인 근무 막대 sat at the very bottom; a row of 묶음 (달력 머리, 날짜 칸,
    // 타임라인 판 …) on top narrows the chips to one family.
    val families = remember(group) { ids.filter { it != group && it.count { c -> c == '.' } == 1 && BlueprintParts.CHILDREN[it].orEmpty().isNotEmpty() } }
    var family by rememberSaveable(group) { mutableStateOf<String?>(null) }
    if (families.size >= 3) com.bamnat.app.ui.components.FoldRow("studio.families", what = "부품 묶음") {
        FilterChip(selected = family == null, onClick = { family = null }, label = { Text("모두") })
        families.forEach { f ->
            FilterChip(selected = family == f, onClick = { family = f; if (!(id == f || id.startsWith("$f."))) id = f },
                label = { Text(BlueprintParts.LABELS[f] ?: f) })
        }
    }
    val shown = family?.let { f -> ids.filter { it == f || it.startsWith("$f.") } } ?: ids
    // 어느 부품, 묶음마다 제목을 달아서 (PC 1차 사용성 제안): 달력 머리 · 요일 줄 · 날짜 칸 …
    shown.groupBy { it.split('.').take(2).joinToString(".") }.forEach { (head, list) ->
        if (list.size > 1) Text(BlueprintParts.LABELS[head] ?: head, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEach { p ->
                val touched = bp.lines.any { it["id"] == p }
                FilterChip(selected = if (many) p in multi || p == id else p == id, onClick = {
                    if (many) { if (p != id) multi = if (p in multi) multi - p else multi + p }
                    else if (id != p) { id = p; on = null }
                },
                    label = { Text((if (touched) "● " else "") + (BlueprintParts.LABELS[p] ?: p)) },
                    modifier = Modifier.semantics { if (touched) contentDescription = "${BlueprintParts.LABELS[p]}, 꾸밈 있음" })
            }
        }
    }
    // 어떤 때
    val states = statesFor(id)
    if (states.isNotEmpty()) com.bamnat.app.ui.components.FoldRow("studio.states", what = "어떤 때") {
        (listOf<String?>(null) + states).forEach { st ->
            FilterChip(selected = st == on, onClick = { on = st }, label = { Text(stateWord(id, st)) })
        }
    }
    // [BLU-12] 어두운 화면 colours show in the example once it is seen dark.
    if (on == "dark") Text("화면 바탕이 어두울 때만 쓰는 색이에요. 미리 보기 위 ‘어두운 화면’을 켜면 보여요. 화면 색·배경을 밝게 정한 화면은 어두운 휴대폰에서도 밝은 쪽 색 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // [TAB-07] PC 확인 b81b643f: the 판 부품 (근무 띠·타임라인·보드·표) drew nowhere in the month example, so they show their own small
    // example here, and the box says which part it is.
    // PC 확인 9e234d9e 사용성 제안: picking a 판 부품 brings its example up into view (it sat far under the chips).
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var firstId by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        if (firstId == null) firstId = id else if (firstId != id) { kotlinx.coroutines.delay(120); bring.bringIntoView(); firstId = "" }
    }
    androidx.compose.foundation.layout.Box(Modifier.bringIntoViewRequester(bring)) { BoardPartSample(id, bp) }
    Text("${BlueprintParts.LABELS[id] ?: id} 꾸미기" + (on?.let { " · ${STATE_WORDS[it]}" } ?: ""), style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() })
    // [RUL-02] 1.36 조건부 꾸미기: "이럴 때만" (야간이고 메모에 #인수인계가 있으면 …), as a table to pick from or a 수식.
    val conds = remember(bp, id, on) { bp.lines.filter { it["id"] == id && it["on"] == on }.mapNotNull { it["when"] as? String }.distinct() }
    // [FXR-03] Only a part drawn for a day takes a condition (and is offered one); a part with none keeps the lines it already has
    // in view, saying they do nothing there, so they can be taken off.
    val dated = BlueprintParts.dated(id)
    var cond by rememberSaveable(id, on) { mutableStateOf<String?>(if (conditionFirst && dated) conds.firstOrNull() ?: "" else null) }
    if (!dated && conds.isNotEmpty()) Text("이 부품은 날짜가 없어서 조건이 맞을 때가 없어요. 조건으로 꾸민 모습은 날짜 칸·메모 카드·가계부 한 줄 같은 곳에서 써요",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (dated || conds.isNotEmpty()) com.bamnat.app.ui.components.FoldRow("studio.conds", what = "조건") {
        FilterChip(selected = cond == null, onClick = { cond = null }, label = { Text("조건 없이") })
        conds.forEach { w ->
            FilterChip(selected = cond == w, onClick = { cond = w },
                label = { Text("만약 " + (com.bamnat.core.formula.EasyWhen.read(w)?.say() ?: w).take(18), maxLines = 1) })
        }
        if (dated && cond?.isBlank() != true) FilterChip(selected = false, onClick = { cond = "" }, label = { Text("＋ 조건으로 꾸미기") })
    }
    // PC 확인 1.37 A8 (MED): 메모 제목 › 언제 › ‘＋ 조건으로 꾸미기’ showed the part's chips and sample, the condition far below them.
    // The condition starts (also when this part was already open) and its editor is brought to the top of the panel.
    val condBring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(conditionAsk, id) {
        if (conditionFirst && dated && conditionAsk > 0) {
            if (cond == null) cond = conds.firstOrNull() ?: ""
            kotlinx.coroutines.delay(150); condBring.bringIntoView()
        }
    }
    cond?.let { now -> androidx.compose.foundation.layout.Column(Modifier.bringIntoViewRequester(condBring), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.bamnat.app.logic.ConditionEditor(now, { next ->
            val ok = next.isNotBlank() && com.bamnat.core.formula.Logic.formulaProblem(next) == null
            if (ok && now.isNotBlank() && next != now) {
                val moved = bp.lines.map { if (it["id"] == id && it["on"] == on && it["when"] == now) it + ("when" to next) else it }
                runCatching { Blueprint(Blueprint.clean(moved)).encode() }.getOrNull()?.let(onChange)
            }
            cond = next
        }, com.bamnat.app.logic.LocalDayFacts.current?.scope(com.bamnat.core.time.Today.date()) ?: com.bamnat.core.formula.FormulaNames.SAMPLE,
            empty = "조건을 골라 주세요", lead = condLead(id), start = condStart(id))
        if (now.isBlank() || com.bamnat.core.formula.Logic.formulaProblem(now) != null) Text("조건을 다 고르면 아래에서 그때의 모습을 꾸며요",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // [RUL-06] 이 조건을 쓰는 곳: the same formula in 규칙, to go there and back.
        val users = remember(now, parts) { if (now.isBlank()) emptyList() else rulesUsing(now) }
        if (users.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("같은 조건을 쓰는 규칙: " + users.joinToString(", ") { "‘$it’" }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            onOpenRules?.let { go -> TextButton(onClick = go) { Text("규칙 보기") } }
        }
    } }
    val whenNow = cond?.takeIf { it.isNotBlank() && com.bamnat.core.formula.Logic.formulaProblem(it) == null }
    // The first one is said only when a condition opened first (else the canvas keeps the one it had).
    val whenSaid = remember(id, on) { booleanArrayOf(conditionFirst) }
    LaunchedEffect(whenNow) { if (whenSaid[0]) onWhen(whenNow); whenSaid[0] = true }
    if (cond != null && whenNow == null) return
    val line: Map<String, Any?> = bp.lines.firstOrNull { it["id"] == id && it["on"] == on && it["when"] == whenNow } ?: emptyMap()
    fun setAll(vararg kv: Pair<String, Any?>) {
        // [LAY-08] With 여러 부품 on, the same fields go to every ticked part (each keeps its other fields). [RUL-02] a 조건 line stays its own.
        val targets = if (many) (listOf(id) + multi).distinct() else listOf(id)
        var all = bp.lines
        for (t in targets) {
            val cur = all.firstOrNull { it["id"] == t && it["on"] == on && it["when"] == whenNow } ?: emptyMap()
            val next = (cur + ("id" to t) + (on?.let { mapOf("on" to it) } ?: emptyMap()) + (whenNow?.let { mapOf("when" to it) } ?: emptyMap()) + kv).filterValues { it != null }
            val others = all.filterNot { it["id"] == t && it["on"] == on && it["when"] == whenNow }
            all = if (next.keys.all { it == "id" || it == "on" || it == "when" }) others else others + next
        }
        val text = runCatching { Blueprint(Blueprint.clean(all)).encode() }.getOrNull() ?: return
        onChange(if (all.isEmpty()) "" else text)
    }
    fun set(k: String, v: Any?) = setAll(k to v)
    fun num(k: String): Float? = (line[k] as? Number)?.toFloat()
    fun col(k: String): Long? = line[k] as? Long
    // PC 확인 46b9004b 사용성 제안: the 막대 keep one 진하기 for "근무 색 그대로" and one for "한 가지 색으로", so going back and forth
    // brings each one back instead of starting again from 100%.
    var otherA by remember(id, on) { mutableStateOf<Long?>(null) }

    // [MAT-01] PC 14차 사용성 제안: 칸이 많아 원하는 묶음까지 길게 내려야 해서, 위에 묶음 바로 가기.
    // 하나를 고르면 그 묶음만 보여요(스크롤로 데려가는 방식은 이 편집 칸에서 믿을 만하지 않았어요). 다른 부품으로 가도 고른 묶음 그대로.
    val kids = BlueprintParts.CHILDREN[id].orEmpty()
    val groups = listOf("채우기", "선", "모서리", "그림자·여백", "글자", "투명도·숨기기") + (if (kids.size > 1) listOf("배치 (안에 든 것)") else emptyList())
    var only by rememberSaveable(group) { mutableStateOf<String?>(null) }
    if (only != null && only !in groups) only = null
    // PC 15차: 옆으로 밀지 않아도 다 보이게 감싸서.
    Text("바로 가기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
    FlowRow(Modifier.semantics { contentDescription = "묶음 바로 가기" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (listOf<String?>(null) + groups).forEach { g ->
            FilterChip(selected = only == g, onClick = { only = g }, label = { Text(g?.substringBefore(" (") ?: "전체") })
        }
    }
    @Composable
    fun Group(title: String, content: @Composable () -> Unit) {
        if (only == null || only == title) com.bamnat.app.ui.settings.Group(title, content)
    }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (advanced) PartsAdvanced(bp, id, on, line, many, multi, onMany = { many = it; if (!it) multi = emptyList() },
            set = ::set, onAll = { text -> onChange(text) })
        Group("채우기") {
            // PC 확인 e262702a 사용성 제안: a 막대 given one colour can go back to each shift's own at one tap.
            // PC 확인 5c21f3bb (1절 3번): 색 고르기의 '처음대로'도 근무 색 그대로로 가는 길이라, 그때도 두 진하기를 바꿔 끼워요.
            fun pickFill(next: Long?) {
                if (id == "cal.line.bar" && (col("fill") == null) != (next == null)) {
                    val was = (line["fillA"] as? Number)?.toLong(); setAll("fill" to next, "fillA" to otherA); otherA = was
                } else setAll("fill" to next)
            }
            if (id == "cal.line.bar") FilterChip(selected = col("fill") == null, onClick = { if (col("fill") != null) pickFill(null) },
                label = { Text("근무 색 그대로") })
            ColorPick(if (id == "cal.line.bar") "한 가지 색으로" else "색", col("fill")) { pickFill(it) }
            if (col("fill") != null) LabeledSlider("진하기", num("fillA") ?: 100f, 0f..100f, "%") { set("fillA", it.toLong()) }
            // PC 확인 fc2db3b0 사용성 제안: one colour for every 막대 hid which shift is which; with no 색 the 막대 keep their shift's colour and
            // this sets how strong.
            else if (id == "cal.line.bar") LabeledSlider("근무 색 그대로 · 진하기", num("fillA") ?: 90f, 20f..100f, "%") { set("fillA", it.toLong()) }
        }
        Group("선") {
            Chips(listOf(null to "처음대로", "none" to "없음", "solid" to "실선", "dash" to "끊은 선", "dot" to "점선", "double" to "두 줄"), line["line"] as? String) { set("line", it) }
            if (line["line"] != null && line["line"] != "none") {
                LabeledSlider("두께", num("lineW") ?: 1f, 0f..8f, "dp", unset = if (line["lineW"] == null) "처음대로" else null) { set("lineW", (Math.round(it * 2) / 2.0).let { d -> if (d % 1 == 0.0) d.toLong() else d }) }
                ColorPick("선 색", col("lineC")) { set("lineC", it) }
            }
        }
        Group("모서리") {
            var each by remember(id, on) { mutableStateOf(listOf("rTL", "rTR", "rBR", "rBL").any { line[it] != null }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("네 귀퉁이 따로", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(each, { each = it })
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            // '네모' in one tap; the slider starts from the corners the part has now and goes one dp at a time (PC 3차).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = num("r") == 0f, onClick = { set("r", 0L) }, label = { Text("네모") })
                FilterChip(selected = line["r"] == null, onClick = { set("r", null) }, label = { Text("처음대로") })
            }
            if (!each) LabeledSlider("둥글기", num("r") ?: cornerNow(id), 0f..48f, "dp", unset = if (line["r"] == null) cornerNote(id) else null) { set("r", it.toLong()) }
            else listOf("rTL" to "왼쪽 위", "rTR" to "오른쪽 위", "rBL" to "왼쪽 아래", "rBR" to "오른쪽 아래").forEach { (k, w) ->
                LabeledSlider(w, num(k) ?: num("r") ?: cornerNow(id), 0f..48f, "dp", unset = if (line[k] == null && line["r"] == null) cornerNote(id) else null) { set(k, it.toLong()) }
            }
        }
        Group("그림자·여백") {
            LabeledSlider("그림자", num("shadow") ?: 0f, 0f..24f, "dp", unset = if (line["shadow"] == null) "처음대로" else null) { set("shadow", it.toLong().takeIf { v -> v > 0 }) }
            if ((num("shadow") ?: 0f) > 0f) ColorPick("그림자 색", col("shadowC")) { set("shadowC", it) }
            LabeledSlider("안 여백", num("pad") ?: 0f, 0f..32f, "dp", unset = if (line["pad"] == null) "처음대로" else null) { set("pad", it.toLong()) }
            LabeledSlider("바깥 간격", num("out") ?: 0f, 0f..32f, "dp", unset = if (line["out"] == null) "처음대로" else null) { set("out", it.toLong().takeIf { v -> v > 0 }) }
        }
        Group("글자") {
            // PC 15차: 작은 미리 보기에선 밑줄·그림자가 잘 안 보여서, 이 부품 글자를 크게.
            val big = remember(bp, id, on) { bp.style(id, on?.let { setOf(it) } ?: emptySet()).copy(size = null, lines = null) }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(12.dp)
                .semantics { contentDescription = "글자 크게 보기" }, contentAlignment = Alignment.Center) {
                com.bamnat.app.ui.parts.PartText(if (id.contains("date")) "15  28" else "가나다 Aa 15", big,
                    MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Chips(listOf(null to "처음대로") + FONT_WORDS, line["font"] as? String) { set("font", it) }
            LabeledSlider("크기", num("size") ?: 14f, 6f..48f, "sp", unset = if (line["size"] == null) "처음대로" else null) { set("size", it.toLong()) }
            Chips(listOf(null to "처음대로", 300L to "가늘게", 400L to "보통", 700L to "굵게", 900L to "아주 굵게"), (line["weight"] as? Number)?.toLong()) { set("weight", it) }
            ColorPick("글자 색", col("ink")) { set("ink", it) }
            // [BLU-06] 글자 색과 채우기가 비슷하면 알려요 (WCAG 4.5:1). 글자 색을 비워 두면 채우기에 맞춰 검정·흰색이 저절로 골라져요.
            val fillNow = col("fill")
            val inkNow = col("ink")
            if (fillNow != null && inkNow != null && com.bamnat.core.layout.Contrast.ratio(fillNow or 0xFF000000L, inkNow or 0xFF000000L) < 4.5) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("채우기와 글자 색이 비슷해 잘 안 보일 수 있어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { set("ink", null) }) { Text("저절로 맞추기") }
                }
            } else if (fillNow != null && inkNow == null) {
                Text("글자 색을 비워 두면 채우기에 맞춰 잘 보이는 색으로 그려요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LabeledSlider("자간", num("track") ?: 0f, -5f..30f, "%", unset = if (line["track"] == null) "처음대로" else null) { set("track", it.toLong()) }
            LabeledSlider("줄 간격", num("leading") ?: 120f, 80f..300f, "%", unset = if (line["leading"] == null) "처음대로" else null) { set("leading", it.toLong()) }
            // PC 15차: '처음대로'가 무엇인지 함께. 어떤 때(상태) 줄에서는 평소 모습을 따르니 '바로'·'줄 없음'으로 되돌릴 수 있게 남겨요.
            Chips(listOf(null to (if (on == null) "처음대로(바로)" else "평소대로")) + (if (on != null || line["italic"] == false) listOf(false to "바로") else emptyList()) +
                listOf(true to "기울임"), line["italic"] as? Boolean) { set("italic", it) }
            LabeledSlider("글자 테두리", num("stroke") ?: 0f, 0f..4f, "dp", unset = if (line["stroke"] == null) "처음대로" else null) { set("stroke", (Math.round(it * 2) / 2.0).takeIf { v -> v > 0 }?.let { d -> if (d % 1 == 0.0) d.toLong() else d }) }
            if ((num("stroke") ?: 0f) > 0f) ColorPick("테두리 색", col("strokeC")) { set("strokeC", it) }
            // [BLU-08] 누락 점검 N11: 글자 그림자와 밑줄·가운데 줄.
            LabeledSlider("글자 그림자", num("tshadow") ?: 0f, 0f..12f, "dp", unset = if (line["tshadow"] == null) "처음대로" else null) { set("tshadow", it.toLong().takeIf { v -> v > 0 }) }
            if ((num("tshadow") ?: 0f) > 0f) {
                ColorPick("그림자 색", col("tshadowC")) { set("tshadowC", it) }
                val where = when { line["tshadowX"] == 0L && line["tshadowY"] == 0L -> "glow"; line["tshadowX"] == 0L -> "down"; line["tshadowX"] == null -> null; else -> "side" }
                Chips(listOf(null to "오른쪽 아래", "down" to "아래로", "glow" to "빛나게(둘레)"), where) { w ->
                    when (w) { "down" -> setAll("tshadowX" to 0L, "tshadowY" to null); "glow" -> setAll("tshadowX" to 0L, "tshadowY" to 0L); else -> setAll("tshadowX" to null, "tshadowY" to null) }
                }
            }
            Chips(listOf(null to (if (on == null) "처음대로(줄 없음)" else "평소대로")) + (if (on != null || line["deco"] == "none") listOf("none" to "줄 없음") else emptyList()) +
                listOf("under" to "밑줄", "strike" to "가운데 줄", "both" to "둘 다"), line["deco"] as? String) { set("deco", it) }
        }
        Group("투명도·숨기기") {
            LabeledSlider("보이는 정도", num("alpha") ?: 100f, 0f..100f, "%", step = 5, unset = if (line["alpha"] == null) "처음대로" else null) { set("alpha", it.toLong().takeIf { v -> v < 100 }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("숨기기", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(line["hide"] == true, { set("hide", it.takeIf { h -> h }) })
            }
            if (id in BlueprintParts.DATA) Text("날짜·근무 같은 정보는 숨겨도 화면 읽기(TalkBack)에는 그대로 읽혀요",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (kids.size > 1) Group("배치 (안에 든 것)") {
            Chips(listOf(null to "처음대로", "col" to "세로로", "row" to "가로로", "stack" to "겹쳐서"), line["dir"] as? String) { set("dir", it) }
            LabeledSlider("사이 간격", num("gap") ?: 1f, 0f..32f, "dp", unset = if (line["gap"] == null) "처음대로" else null) { set("gap", it.toLong()) }
            Chips(listOf(null to "처음대로", "start" to "앞에", "center" to "가운데", "end" to "끝에", "stretch" to "꽉 차게"), line["align"] as? String) { set("align", it) }
            Chips(listOf(null to "처음대로", "start" to "앞부터", "center" to "가운데로", "end" to "뒤로", "between" to "양 끝 맞춤", "around" to "고르게"), line["justify"] as? String) { set("justify", it) }
            val order = remember(bp, id, on) { bp.childOrder(id, on?.let { setOf(it) } ?: emptySet()) }
            Text("차례 (위가 먼저)", style = MaterialTheme.typography.bodyMedium)
            order.forEachIndexed { i, k ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. " + (BlueprintParts.LABELS["$id.$k"] ?: k), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { set("order", order.toMutableList().apply { add(i - 1, removeAt(i)) }.joinToString(",")) }, enabled = i > 0) { Text("위로") }
                    TextButton(onClick = { set("order", order.toMutableList().apply { add(i + 1, removeAt(i)) }.joinToString(",")) }, enabled = i < order.lastIndex) { Text("아래로") }
                }
            }
        }
        if (only != null) TextButton(onClick = { only = null }) { Text("다른 묶음도 보기 (전체)") }
        if (line.keys.any { it != "id" && it != "on" }) OutlinedButton(onClick = {
            val others = bp.lines.filterNot { it["id"] == id && it["on"] == on }
            onChange(if (others.isEmpty()) "" else Blueprint(others).encode())
        }, modifier = Modifier.fillMaxWidth()) { Text("이 부품 처음대로 (${stateWord(id, on)})") }
        // [BLU-07] 1.36 이 부품만 올리기 (사용자 2026-11-01): 장터 '부품'에 이 부품(과 그 속)만, 다른 분이 스튜디오에서 얹어 써요.
        val unit = BlueprintParts.unitOf(id)
        if (onUploadPart != null && bp.lines.any { BlueprintParts.unitOf(it["id"] as? String ?: "") == unit }) {
            OutlinedButton(onClick = { onUploadPart(unit) }, modifier = Modifier.fillMaxWidth()) { Text("‘${BlueprintParts.LABELS[unit] ?: unit}’ 부품만 장터에 올리기") }
            Text("이 부품과 그 안의 부품 꾸밈만 올라가요. 받는 분은 장터에서 담아 두고 스튜디오 보관함에서 얹어 써요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The states that mean something for a part. */
/** The corners as drawn before the blueprint sets them, where the slider starts. */
private fun cornerNow(id: String): Float = when (id) {
    "cal.cell.mark" -> 5f; "cal.cell.date", "app.tab" -> 24f; "cal.head.title", "app.chip" -> 8f; "cal.head", "app.card" -> 12f; "memo.card", "app.sheet" -> 16f; else -> 0f
}

/** What a part's corners are before the blueprint sets them, said beside 둥글기 (PC 2차: "꾸미기 전에도 0dp로 보여요"). */
private fun cornerNote(id: String): String = "처음대로 · 지금 " + when (id) {
    "cal.cell.mark" -> "약 5dp"; "cal.cell.date" -> "동그라미"; "cal.head.title", "app.chip" -> "8dp"; "cal.head", "app.card" -> "12dp"
    "memo.card", "app.sheet" -> "16dp"; "app.tab" -> "알약"; else -> "네모(0dp)"
}

internal fun partWords(id: String, on: String?): String =
    (BlueprintParts.LABELS[id] ?: id) + (on?.let { " · " + stateWord(id, it) } ?: "")

// [BLU-12] Every part has 어두운 화면 last: its colours when the phone (or the widget) is dark.
internal fun statesFor(id: String): List<String> = statesOf(id) + "dark"

private fun statesOf(id: String): List<String> = when {
    // The shift a mark shows comes first there (PC 3차: '야간 근무'가 줄 끝에 있어 밀어야 보여요).
    id == "cal.cell.mark" -> listOf("role.day", "role.eve", "role.night", "role.long", "role.off", "today", "sat", "sun", "holiday", "dim", "sel")
    id.startsWith("cal.cell") -> listOf("today", "sat", "sun", "holiday", "dim", "sel", "role.day", "role.eve", "role.night", "role.long", "role.off")
    id == "cal.week.day" -> listOf("sat", "sun")
    id == "app.tab" || id == "app.chip" -> listOf("sel")
    id == "money.amount" || id == "money.row" || id == "pay.row" -> listOf("in", "out")
    // [BLU-10] 위젯 칸 is a day like the calendar's cells; 위젯 머리 turns red on a public holiday.
    id == "widget.cell" -> listOf("today", "sat", "sun", "holiday", "dim", "role.day", "role.eve", "role.night", "role.long", "role.off")
    id == "widget.head" -> listOf("holiday")
    // 1.39 [TASK-07] 할 일 한 줄은 오늘·늦음·끝냄, 그 동그라미는 늦음·끝냄.
    id == "widget.task" -> listOf("today", "late", "done")
    id == "widget.task.check" -> listOf("late", "done")
    // [TAB-07] 1.36 탭 판 부품.
    id == "cal.band.slot" || id == "cal.line.row" || id == "money.cal.cell" || id == "memo.cal.cell" -> listOf("today", "sel")
    id == "cal.weekly.day" -> listOf("today", "sel", "sat", "sun", "dim")
    id == "cal.year.month" -> listOf("today")
    else -> emptyList()
}

/** A state's words on [id]: 급여 한 줄's in·out are 더하는·빼는 항목. */
internal fun stateWord(id: String, st: String?): String = when {
    id.startsWith("pay.") && st == "in" -> "더하는 항목"
    id.startsWith("pay.") && st == "out" -> "빼는 항목"
    else -> STATE_WORDS[st] ?: "언제나"
}

internal val STATE_WORDS = mapOf<String?, String>(
    "dark" to "어두운 화면", "today" to "오늘", "sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일", "dim" to "다른 달", "sel" to "고른 것",
    "role.day" to "주간 근무", "role.eve" to "오후·저녁 근무", "role.night" to "야간 근무", "role.long" to "24시간·당직", "role.off" to "쉬는 날",
    "role.other" to "그 밖 근무", "in" to "수입", "out" to "지출",
    // 1.39 [TASK-07] 할 일 위젯.
    "late" to "늦은 할 일", "done" to "끝낸 할 일",
)

private val FONT_WORDS = listOf("sans" to "고딕", "serif" to "명조", "mono" to "고정폭", "hand" to "손글씨", "round" to "둥근")

/** Which parts the studio offers in each of its areas. */
internal fun partsGroupOf(area: String): String = when (area) {
    "MEMO" -> "memo"
    "MONEY" -> "money"
    "PAY" -> "pay"
    "WIDGET" -> "widget"
    "ALL", "SETTINGS" -> "app"
    else -> "cal"
}

/** [LAY-08] The number fields of 고급 보기: field, words, range and whether it takes halves. */
private val NUMBER_FIELDS = listOf(
    Triple("fillA", "채우기 진하기 %", 0f..100f), Triple("lineW", "선 두께 dp", 0f..8f), Triple("r", "둥글기 dp", 0f..48f),
    Triple("rTL", "왼쪽 위 dp", 0f..48f), Triple("rTR", "오른쪽 위 dp", 0f..48f), Triple("rBL", "왼쪽 아래 dp", 0f..48f), Triple("rBR", "오른쪽 아래 dp", 0f..48f),
    Triple("shadow", "그림자 dp", 0f..24f), Triple("pad", "안 여백 dp", 0f..32f), Triple("out", "바깥 간격 dp", 0f..32f),
    Triple("size", "글자 크기 sp", 6f..48f), Triple("weight", "굵기", 100f..900f), Triple("track", "자간 %", -5f..30f), Triple("leading", "줄 간격 %", 80f..300f),
    Triple("stroke", "글자 테두리 dp", 0f..4f), Triple("alpha", "보이는 정도 %", 0f..100f), Triple("gap", "사이 간격 dp", 0f..32f),
)
private val HALF_FIELDS = setOf("lineW", "stroke")

/** The colour fields a part line can hold, for 색 변수. */
internal val PART_COLOR_FIELDS = listOf("fill" to "채우기", "lineC" to "선", "ink" to "글자", "shadowC" to "그림자", "strokeC" to "글자 테두리")

/**
 * [LAY-08] 1.36 부품 고급 보기 (누락 점검 N5, 제안 30, 2차 5-8): 여러 부품을 한꺼번에, every value as a number, and 색 변수 — a colour
 * the draft's parts share, shown once with the parts that use it: changing it changes them all, and this part's 채우기·글자·선 can be
 * tied to it in one tap. (A recipe sent up turns such a shared colour into its token.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartsAdvanced(bp: Blueprint, id: String, on: String?, line: Map<String, Any?>, many: Boolean, multi: List<String>,
                          onMany: (Boolean) -> Unit, set: (String, Any?) -> Unit, onAll: (String) -> Unit) {
    com.bamnat.app.ui.settings.Group("고급 보기") {
        // PC 확인 C4 (글자 줄을 눌러도 안 켜져요): the whole row turns it on and off.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = many, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onMany)) {
            Column(Modifier.weight(1f)) {
                Text("여러 부품 한꺼번에", style = MaterialTheme.typography.bodyMedium)
                Text(if (many) "위 부품 칩을 눌러 더해요. 고친 값이 ${multi.size + 1}곳에 같이 들어가요" else "켜면 부품 칩을 여러 개 골라 같은 값을 넣어요",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(many, null)
        }
        Text("숫자로 맞추기", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NUMBER_FIELDS.forEach { (k, words, range) ->
                PartNumber(words, (line[k] as? Number)?.toDouble(), range, half = k in HALF_FIELDS) { v ->
                    set(k, v?.let { d -> if (k in HALF_FIELDS && d % 1 != 0.0) d else d.toLong() })
                }
            }
        }
        // 색 변수: every colour the parts use, with where.
        val used = remember(bp) {
            bp.lines.flatMap { l -> PART_COLOR_FIELDS.mapNotNull { (k, w) -> (l[k] as? Long)?.let { c -> c to "${partWords(l["id"] as? String ?: "", l["on"] as? String)} $w" } } }
                .groupBy({ it.first }, { it.second })
        }
        Text("색 변수", style = MaterialTheme.typography.labelLarge)
        if (used.isEmpty()) Text("부품에 색을 넣으면 여기 모여요. 같은 색을 쓰는 곳을 한 번에 바꿔요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var open by remember(bp) { mutableStateOf<Long?>(null) }
        used.entries.sortedByDescending { it.value.size }.forEach { (c, where) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Box(Modifier.padding(end = 8.dp).size(24.dp)
                    .background(androidx.compose.ui.graphics.Color(c.toInt()), androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape))
                Column(Modifier.weight(1f)) {
                    // PC 확인 C4 (16진 값만 보여요): the colour said in words, the number small after it.
                    Text(com.bamnat.app.ui.settings.colorName(c), style = MaterialTheme.typography.bodyMedium)
                    Text("#" + "%06X".format(c and 0xFFFFFF), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${where.size}곳 · " + where.take(3).joinToString(", ") + if (where.size > 3) " …" else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                TextButton(onClick = { open = if (open == c) null else c }) { Text(if (open == c) "닫기" else "모두 바꾸기") }
            }
            if (open == c) ColorPick("${where.size}곳을 함께", c, allowNone = false) { n ->
                if (n == null) return@ColorPick
                val next = bp.lines.map { l -> l.mapValues { (k, v) -> if (PART_COLOR_FIELDS.any { it.first == k } && v == c) n else v } }
                onAll(Blueprint(Blueprint.clean(next)).encode()); open = n
            }
            // This part's colours tied to it in one tap.
            com.bamnat.app.ui.components.FoldRow("studio.tie", what = "색 잇기") {
                listOf("fill" to "채우기에", "ink" to "글자에", "lineC" to "선에").forEach { (k, w) ->
                    val tied = line[k] == c
                    FilterChip(selected = tied, onClick = { set(k, if (tied) null else c) }, label = { Text("${partWords(id, on)} $w") })
                }
            }
        }
    }
}

/** A number box for one field: empty is 처음대로; out of [range] is kept to it. */
@Composable
private fun PartNumber(label: String, value: Double?, range: ClosedFloatingPointRange<Float>, half: Boolean, onSet: (Double?) -> Unit) {
    fun show(v: Double?) = v?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
    var text by remember(value) { mutableStateOf(show(value)) }
    androidx.compose.material3.OutlinedTextField(text, { t ->
        text = t.filter { it.isDigit() || it == '.' || it == '-' }.take(6)
        if (text.isEmpty()) onSet(null)
        else text.toDoubleOrNull()?.let { v ->
            val k = v.coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
            onSet(if (half) Math.round(k * 2) / 2.0 else Math.round(k).toDouble())
        }
    }, label = { Text(label) }, placeholder = { Text("처음대로") }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.width(150.dp))
}

/** PC 확인 1.37 A9: a 메모 part's condition starts from the memo's own values (고정·체크), a 가계부 line's from its own and the money. */
internal fun condLead(id: String): List<com.bamnat.core.formula.FormulaName.Group> = when {
    id.startsWith("memo") -> listOf(com.bamnat.core.formula.FormulaName.Group.ROW, com.bamnat.core.formula.FormulaName.Group.MEMO)
    id.startsWith("money") -> listOf(com.bamnat.core.formula.FormulaName.Group.ROW, com.bamnat.core.formula.FormulaName.Group.MONEY)
    else -> emptyList()
}

internal fun condStart(id: String): com.bamnat.core.formula.FormulaName? = when {
    id.startsWith("memo") -> com.bamnat.core.formula.FormulaName.PINNED
    else -> condLead(id).firstOrNull()?.let { g -> com.bamnat.core.formula.FormulaName.entries.firstOrNull { it.group == g } }
}
