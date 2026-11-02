package com.bamnat.app.ui.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.settings.Chips
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.app.ui.settings.Group
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
internal fun PartsTool(group: String, parts: String, onChange: (String) -> Unit) {
    val bp = remember(parts) { Blueprint.decode(parts) }
    val ids = remember(group) { BlueprintParts.IDS.filter { it == group || it.startsWith("$group.") || (group == "app" && it.startsWith("app")) } }
    var id by rememberSaveable(group) { mutableStateOf(ids.firstOrNull { it.contains('.') } ?: ids.first()) }
    var on by rememberSaveable(id) { mutableStateOf<String?>(null) }
    Text("화면을 부품으로 나눠 하나씩 꾸며요. 비워 둔 칸은 지금 모습 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // PC 확인 efc52400 사용성 제안: 달력 has some 30 parts and 타임라인 근무 막대 sat at the very bottom; a row of 묶음 (달력 머리, 날짜 칸,
    // 타임라인 판 …) on top narrows the chips to one family.
    val families = remember(group) { ids.filter { it != group && it.count { c -> c == '.' } == 1 && BlueprintParts.CHILDREN[it].orEmpty().isNotEmpty() } }
    var family by rememberSaveable(group) { mutableStateOf<String?>(null) }
    if (families.size >= 3) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = family == null, onClick = { family = null }, label = { Text("모두") })
        families.forEach { f ->
            FilterChip(selected = family == f, onClick = { family = f; if (!(id == f || id.startsWith("$f."))) id = f },
                label = { Text(BlueprintParts.LABELS[f] ?: f) })
        }
    }
    val shown = family?.let { f -> ids.filter { it == f || it.startsWith("$f.") } } ?: ids
    // 어느 부품
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { p ->
            val touched = bp.touches(p) && bp.lines.any { it["id"] == p }
            FilterChip(selected = p == id, onClick = { id = p },
                label = { Text((if (touched) "● " else "") + (BlueprintParts.LABELS[p] ?: p)) },
                modifier = Modifier.semantics { if (touched) contentDescription = "${BlueprintParts.LABELS[p]}, 꾸밈 있음" })
        }
    }
    // 어떤 때
    val states = statesFor(id)
    if (states.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<String?>(null) + states).forEach { st ->
            FilterChip(selected = st == on, onClick = { on = st }, label = { Text(STATE_WORDS[st] ?: "언제나") })
        }
    }
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
    val line: Map<String, Any?> = bp.lines.firstOrNull { it["id"] == id && it["on"] == on } ?: emptyMap()
    fun set(vararg kv: Pair<String, Any?>) {
        val next = (line + ("id" to id) + (on?.let { mapOf("on" to it) } ?: emptyMap()) + kv).filterValues { it != null }
        val others = bp.lines.filterNot { it["id"] == id && it["on"] == on }
        val all = if (next.keys.all { it == "id" || it == "on" }) others else others + next
        val text = runCatching { Blueprint(Blueprint.clean(all)).encode() }.getOrNull() ?: return
        onChange(if (all.isEmpty()) "" else text)
    }
    fun set(k: String, v: Any?) = set(k to v)
    fun num(k: String): Float? = (line[k] as? Number)?.toFloat()
    fun col(k: String): Long? = line[k] as? Long
    // PC 확인 46b9004b 사용성 제안: the 막대 keep one 진하기 for "근무 색 그대로" and one for "한 가지 색으로", so going back and forth
    // brings each one back instead of starting again from 100%.
    var otherA by remember(id, on) { mutableStateOf<Long?>(null) }

    // PC 확인 c9a89b20: a swipe along the chips caught the 둥글기 slider just under them; the groups keep more room between them.
    Column(verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 4.dp)) {
        Group("채우기") {
            // PC 확인 e262702a 사용성 제안: a 막대 given one colour can go back to each shift's own at one tap.
            if (id == "cal.line.bar") FilterChip(selected = col("fill") == null, onClick = {
                if (col("fill") != null) { val was = (line["fillA"] as? Number)?.toLong(); set("fill" to null, "fillA" to otherA); otherA = was }
            }, label = { Text("근무 색 그대로") })
            ColorPick(if (id == "cal.line.bar") "한 가지 색으로" else "색", col("fill")) {
                if (id == "cal.line.bar" && col("fill") == null && it != null) {
                    val was = (line["fillA"] as? Number)?.toLong(); set("fill" to it, "fillA" to otherA); otherA = was
                } else set("fill" to it)
            }
            if (col("fill") != null) LabeledSlider("진하기", num("fillA") ?: 100f, 0f..100f, "%") { set("fillA", it.toLong()) }
            // PC 확인 fc2db3b0 사용성 제안: one colour for every 막대 hid which shift is which; with no 색 the 막대 keep their shift's colour and
            // this sets how strong.
            else if (id == "cal.line.bar") LabeledSlider("근무 색 그대로 · 진하기", num("fillA") ?: 90f, 20f..100f, "%") { set("fillA", it.toLong()) }
        }
        Group("선") {
            Chips(listOf(null to "처음대로", "none" to "없음", "solid" to "실선", "dash" to "끊은 선", "dot" to "점선", "double" to "두 줄"), line["line"] as? String) { set("line", it) }
            if (line["line"] != null && line["line"] != "none") {
                LabeledSlider("두께", num("lineW") ?: 1f, 0f..8f, "dp") { set("lineW", (Math.round(it * 2) / 2.0).let { d -> if (d % 1 == 0.0) d.toLong() else d }) }
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
            if (!each) LabeledSlider("둥글기", num("r") ?: 0f, 0f..48f, "dp") { set("r", it.toLong()) }
            else listOf("rTL" to "왼쪽 위", "rTR" to "오른쪽 위", "rBL" to "왼쪽 아래", "rBR" to "오른쪽 아래").forEach { (k, w) ->
                LabeledSlider(w, num(k) ?: num("r") ?: 0f, 0f..48f, "dp") { set(k, it.toLong()) }
            }
        }
        Group("그림자·여백") {
            LabeledSlider("그림자", num("shadow") ?: 0f, 0f..24f, "dp") { set("shadow", it.toLong().takeIf { v -> v > 0 }) }
            if ((num("shadow") ?: 0f) > 0f) ColorPick("그림자 색", col("shadowC")) { set("shadowC", it) }
            LabeledSlider("안 여백", num("pad") ?: 0f, 0f..32f, "dp") { set("pad", it.toLong()) }
            LabeledSlider("바깥 간격", num("out") ?: 0f, 0f..32f, "dp") { set("out", it.toLong().takeIf { v -> v > 0 }) }
        }
        Group("글자") {
            Chips(listOf(null to "처음대로") + FONT_WORDS, line["font"] as? String) { set("font", it) }
            LabeledSlider("크기", num("size") ?: 14f, 6f..48f, "sp") { set("size", it.toLong()) }
            Chips(listOf(null to "처음대로", 300L to "가늘게", 400L to "보통", 700L to "굵게", 900L to "아주 굵게"), (line["weight"] as? Number)?.toLong()) { set("weight", it) }
            ColorPick("글자 색", col("ink")) { set("ink", it) }
            LabeledSlider("자간", num("track") ?: 0f, -5f..30f, "%") { set("track", it.toLong()) }
            LabeledSlider("줄 간격", num("leading") ?: 120f, 80f..300f, "%") { set("leading", it.toLong()) }
            Chips(listOf(null to "처음대로", false to "바로", true to "기울임"), line["italic"] as? Boolean) { set("italic", it) }
            LabeledSlider("글자 테두리", num("stroke") ?: 0f, 0f..4f, "dp") { set("stroke", (Math.round(it * 2) / 2.0).takeIf { v -> v > 0 }?.let { d -> if (d % 1 == 0.0) d.toLong() else d }) }
            if ((num("stroke") ?: 0f) > 0f) ColorPick("테두리 색", col("strokeC")) { set("strokeC", it) }
        }
        Group("투명도·숨기기") {
            LabeledSlider("보이는 정도", num("alpha") ?: 100f, 0f..100f, "%") { set("alpha", it.toLong().takeIf { v -> v < 100 }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("숨기기", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(line["hide"] == true, { set("hide", it.takeIf { h -> h }) })
            }
            if (id in BlueprintParts.DATA) Text("날짜·근무 같은 정보는 숨겨도 화면 읽기(TalkBack)에는 그대로 읽혀요",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val kids = BlueprintParts.CHILDREN[id].orEmpty()
        if (kids.size > 1) Group("배치 (안에 든 것)") {
            Chips(listOf(null to "처음대로", "col" to "세로로", "row" to "가로로", "stack" to "겹쳐서"), line["dir"] as? String) { set("dir", it) }
            LabeledSlider("사이 간격", num("gap") ?: 1f, 0f..32f, "dp") { set("gap", it.toLong()) }
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
        if (line.keys.any { it != "id" && it != "on" }) OutlinedButton(onClick = {
            val others = bp.lines.filterNot { it["id"] == id && it["on"] == on }
            onChange(if (others.isEmpty()) "" else Blueprint(others).encode())
        }, modifier = Modifier.fillMaxWidth()) { Text("이 부품 처음대로 (${STATE_WORDS[on] ?: "언제나"})") }
    }
}

/** The states that mean something for a part. */
private fun statesFor(id: String): List<String> = when {
    id.startsWith("cal.cell") -> listOf("today", "sat", "sun", "holiday", "dim", "sel", "role.day", "role.eve", "role.night", "role.long", "role.off")
    id == "cal.week.day" -> listOf("sat", "sun")
    id == "app.tab" || id == "app.chip" -> listOf("sel")
    id == "money.amount" || id == "money.row" -> listOf("in", "out")
    // [TAB-07] 1.36 탭 판 부품.
    id == "cal.band.slot" || id == "cal.line.row" || id == "money.cal.cell" || id == "memo.cal.cell" -> listOf("today", "sel")
    id == "cal.weekly.day" -> listOf("today", "sel", "sat", "sun", "dim")
    id == "cal.year.month" -> listOf("today")
    else -> emptyList()
}

private val STATE_WORDS = mapOf<String?, String>(
    "today" to "오늘", "sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일", "dim" to "다른 달", "sel" to "고른 것",
    "role.day" to "주간 근무", "role.eve" to "오후·저녁 근무", "role.night" to "야간 근무", "role.long" to "24시간·당직", "role.off" to "쉬는 날",
    "role.other" to "그 밖 근무", "in" to "수입", "out" to "지출",
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
