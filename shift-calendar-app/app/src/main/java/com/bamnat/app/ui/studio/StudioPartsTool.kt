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
internal fun PartsTool(
    group: String,
    parts: String,
    focus: String? = null,
    focusOn: String? = null,
    /** The part and state being changed, for the line kept above the tools while they scroll (PC 1차 사용성 제안). */
    onFocus: (id: String, on: String?) -> Unit = { _, _ -> },
    /** [BLU-07] 이 부품만 올리기: the unit of the part being changed; null hides the button. */
    onUploadPart: ((unit: String) -> Unit)? = null,
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
    // [BLU-09] 누락 점검 N4: 미리 보기에서 누른 곳이 바로 열려요.
    Text("화면을 부품으로 나눠 하나씩 꾸며요. 비워 둔 칸은 지금 모습 그대로예요. 위 미리 보기에서 고칠 곳을 누르면 그 부품이 열리고, 같은 곳을 한 번 더 누르면 그 바깥 부품이 열려요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 어느 부품, 묶음마다 제목을 달아서 (PC 1차 사용성 제안): 달력 머리 · 요일 줄 · 날짜 칸 …
    ids.groupBy { it.split('.').take(2).joinToString(".") }.forEach { (head, list) ->
        if (list.size > 1) Text(BlueprintParts.LABELS[head] ?: head, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            list.forEach { p ->
                val touched = bp.lines.any { it["id"] == p }
                FilterChip(selected = p == id, onClick = { if (id != p) { id = p; on = null } },
                    label = { Text((if (touched) "● " else "") + (BlueprintParts.LABELS[p] ?: p)) },
                    modifier = Modifier.semantics { if (touched) contentDescription = "${BlueprintParts.LABELS[p]}, 꾸밈 있음" })
            }
        }
    }
    // 어떤 때
    val states = statesFor(id)
    if (states.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<String?>(null) + states).forEach { st ->
            FilterChip(selected = st == on, onClick = { on = st }, label = { Text(stateWord(id, st)) })
        }
    }
    // [BLU-12] 어두운 화면 colours show in the example once it is seen dark.
    if (on == "dark") Text("화면 바탕이 어두울 때만 쓰는 색이에요. 미리 보기 위 ‘어두운 화면’을 켜면 보여요. 화면 색·배경을 밝게 정한 화면은 어두운 휴대폰에서도 밝은 쪽 색 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val line: Map<String, Any?> = bp.lines.firstOrNull { it["id"] == id && it["on"] == on } ?: emptyMap()
    fun setAll(vararg kv: Pair<String, Any?>) {
        val next = (line + ("id" to id) + (on?.let { mapOf("on" to it) } ?: emptyMap()) + kv).filterValues { it != null }
        val others = bp.lines.filterNot { it["id"] == id && it["on"] == on }
        val all = if (next.keys.all { it == "id" || it == "on" }) others else others + next
        val text = runCatching { Blueprint(Blueprint.clean(all)).encode() }.getOrNull() ?: return
        onChange(if (all.isEmpty()) "" else text)
    }
    fun set(k: String, v: Any?) = setAll(k to v)
    fun num(k: String): Float? = (line[k] as? Number)?.toFloat()
    fun col(k: String): Long? = line[k] as? Long

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        Group("채우기") {
            ColorPick("색", col("fill")) { set("fill", it) }
            if (col("fill") != null) LabeledSlider("진하기", num("fillA") ?: 100f, 0f..100f, "%") { set("fillA", it.toLong()) }
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
            Chips(listOf(null to "처음대로", false to "바로", true to "기울임"), line["italic"] as? Boolean) { set("italic", it) }
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
            Chips(listOf(null to "처음대로", "none" to "줄 없음", "under" to "밑줄", "strike" to "가운데 줄", "both" to "둘 다"), line["deco"] as? String) { set("deco", it) }
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
        val kids = BlueprintParts.CHILDREN[id].orEmpty()
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
