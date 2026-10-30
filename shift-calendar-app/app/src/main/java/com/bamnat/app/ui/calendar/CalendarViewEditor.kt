package com.bamnat.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.RecipeDescribe

/** The words of 달력 모양's choices (core RecipeDescribe.VIEW_NAMES, "size.tall" → "칸 높게"), without the field's own name. */
private fun choice(field: String, v: String): String = when ("$field.$v") {
    "size.short" -> "낮게"; "size.normal" -> "보통"; "size.tall" -> "높게"; "size.huge" -> "아주 높게"
    "badge.big" -> "크게"; "badge.small" -> "작게"; "badge.line" -> "색 띠"
    "date.center" -> "가운데"; "date.left" -> "왼쪽 위"
    "text.small" -> "작게"; "text.normal" -> "보통"; "text.large" -> "크게"
    "grid.none" -> "없음"; "grid.line" -> "칸마다 선"
    else -> RecipeDescribe.VIEW_NAMES["$field.$v"] ?: v
}

/**
 * [CVW-02] 1.35 달력 모양 고르기 (설정 › 달력 화면 꾸미기, 꾸미기 스튜디오): ready-made looks first, then each choice, the memo lines and
 * the order of the lines under a day's mark, with a sample week that follows at once. [lines] are the cells' own switches (근무 시간,
 * 음력, 쓴 돈) shown as on in the sample; null leaves them as the sample's own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarViewEditor(view: CalendarView, onChange: (CalendarView) -> Unit, modifier: Modifier = Modifier, sample: @Composable () -> Unit = { CalendarViewSample(view) }) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("달력 모양", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            if (!view.isDefault) TextButton(onClick = { onChange(CalendarView()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("처음대로") }
        }
        Text("칸 높이, 근무 표시, 날짜 자리, 칸에 보일 메모 줄과 순서를 정해요. 메모를 크게 보는 달력도 만들 수 있어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sample()
        Text("바로 고르기", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarView.PRESETS.forEach { (name, p) -> FilterChip(selected = view == p, onClick = { onChange(p) }, label = { Text(name) }) }
        }
        Pick("칸 높이", "size", CalendarView.SIZES, view.size ?: "normal") { onChange(view.copy(size = it.takeIf { v -> v != "normal" })) }
        Pick("근무 표시", "badge", CalendarView.BADGES, view.badge ?: "big") { onChange(view.copy(badge = it.takeIf { v -> v != "big" })) }
        Pick("날짜 자리", "date", CalendarView.DATES, view.date ?: "center") { onChange(view.copy(date = it.takeIf { v -> v != "center" })) }
        Pick("칸 글씨", "text", CalendarView.TEXTS, view.text ?: "normal") { onChange(view.copy(text = it.takeIf { v -> v != "normal" })) }
        Pick("칸 선", "grid", CalendarView.GRIDS, view.grid ?: "none") { onChange(view.copy(grid = it.takeIf { v -> v != "none" })) }
        Column {
            Text("칸에 보일 메모", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view.memo == null, onClick = { onChange(view.copy(memo = null)) }, label = { Text("첫 글자만") })
                FilterChip(selected = view.memo == 0, onClick = { onChange(view.copy(memo = 0)) }, label = { Text("안 보임") })
                (1..CalendarView.MAX_MEMO).forEach { n -> FilterChip(selected = view.memo == n, onClick = { onChange(view.copy(memo = n)) }, label = { Text("${n}줄") }) }
            }
        }
        Text("날짜 밑 줄 순서", style = MaterialTheme.typography.bodyMedium)
        Text("위에 있을수록 먼저 보여요. 근무 시간·음력·쓴 돈은 아래 스위치로 켜요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val lines = view.lines
        lines.forEachIndexed { i, id ->
            val label = RecipeDescribe.LINE_NAMES[id] ?: id
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. $label", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onChange(view.copy(order = com.bamnat.core.layout.PageLayout.move(lines, i, -1))) }, enabled = i > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, "$label 위로")
                }
                IconButton(onClick = { onChange(view.copy(order = com.bamnat.core.layout.PageLayout.move(lines, i, 1))) }, enabled = i < lines.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, "$label 아래로")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pick(label: String, field: String, values: List<String>, value: String, onPick: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { v -> FilterChip(selected = v == value, onClick = { onPick(v) }, label = { Text(choice(field, v)) }) }
        }
    }
}

/** One example day: its mark's letter and colour, a memo or two, hours and a holiday. */
data class SampleDay(val label: String, val color: Long, val memos: List<String> = emptyList(), val hours: String? = null, val holiday: String? = null, val off: Boolean = false)

private val SAMPLE = listOf(
    SampleDay("주", 0xFFEF6C00, listOf("병원 예약", "택배"), "8h"),
    SampleDay("주", 0xFFEF6C00, hours = "8h"),
    SampleDay("야", 0xFF3949AB, listOf("인수인계"), "10h"),
    SampleDay("야", 0xFF3949AB, hours = "10h", holiday = "개천절"),
    SampleDay("휴", 0xFF757575, listOf("가족 모임", "장보기", "헬스"), off = true),
    SampleDay("휴", 0xFF757575, off = true),
    SampleDay("주", 0xFFEF6C00, listOf("월급날"), "8h"),
)

/**
 * [CVW-02] A week drawn the way [view] says, with [days] (example ones by default) and the calendar's [cell] wash: the same rules as
 * the month's cells (date place, mark or bar, lines in order, memo lines), so what is picked here is what the calendar does.
 */
@Composable
fun CalendarViewSample(view: CalendarView, days: List<SampleDay> = SAMPLE, cell: String? = null, today: Color? = null, rows: Int = 1, start: Int = 6) {
    val scheme = MaterialTheme.colorScheme
    val cal = LocalCalendarColors.current
    val height = when (view.size) { "short" -> 46.dp; "tall" -> 86.dp; "huge" -> 120.dp; else -> 60.dp }
    val bump = when (view.text) { "small" -> -1; "large" -> 1; else -> 0 }
    val left = view.date == "left"
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "달력 모양 보기" }) {
        for (r in 0 until rows) Row(Modifier.fillMaxWidth().height(height)) {
            for (c in 0 until 7) {
                val n = start + r * 7 + c
                val d = days[(r * 7 + c) % days.size]
                val color = d.color.asColor()
                val wash = when (cell) { "fill" -> color.copy(alpha = 0.85f); "dot" -> Color.Transparent; else -> if (d.off) Color.Transparent else color.copy(alpha = 0.16f) }
                val shape = if (view.grid == "line") RoundedCornerShape(2.dp) else RoundedCornerShape(8.dp)
                Box(
                    Modifier.weight(1f).fillMaxSize().padding(if (view.grid == "line") 0.5.dp else 1.dp)
                        .then(if (view.grid == "line") Modifier.border(0.6.dp, scheme.outlineVariant, shape) else Modifier)
                        .background(wash, shape),
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalAlignment = if (left) Alignment.Start else Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        val isToday = r == 0 && c == 2
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (left) Arrangement.Start else Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size((16 + bump * 2).dp).background(if (isToday) today ?: scheme.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text("$n", fontSize = (10 + bump).sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    color = when { isToday -> contentColorOn(today ?: scheme.primary); cell == "fill" && !d.off -> contentColorOn(color)
                                        d.holiday != null || c == 0 -> cal.sunday; c == 6 -> cal.saturday; else -> scheme.onSurface })
                            }
                            if (left) Spacer(Modifier.weight(1f))
                            if (left && view.badge != "line") SampleMark(d, if (view.badge == null || view.badge == "big") 15 else 12, cell)
                        }
                        when {
                            view.badge == "line" -> if (!d.off) Box(Modifier.fillMaxWidth().height(3.dp).background(color, RoundedCornerShape(2.dp)))
                            !left -> SampleMark(d, if (view.badge == "small") 12 else 16, cell)
                        }
                        val custom = !view.isDefault
                        val texts = buildMap {
                            d.holiday?.let { put("holiday", it) }
                            when {
                                view.memo != null && view.memo!! > 0 -> d.memos.take(view.memo!!).joinToString("\n").ifEmpty { null }
                                view.memo == null -> d.memos.firstOrNull()?.take(4)
                                else -> null
                            }?.let { put("memo", it) }
                            d.hours?.let { put("hours", it) }
                        }
                        val order = if (custom) view.lines else listOf("holiday", "memo", "hours")
                        val shown = if (custom) order.filter { it in texts } else order.filter { it in texts }.take(1)
                        shown.forEach { id ->
                            texts.getValue(id).split('\n').forEach { line ->
                                Text(line, fontSize = (8 + bump).sp, lineHeight = (9.5f + bump).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = if (left) TextAlign.Start else TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                    color = when (id) { "memo" -> cal.memoDot; "holiday" -> cal.sunday; else -> scheme.onSurfaceVariant })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleMark(d: SampleDay, size: Int, cell: String?) {
    val color = d.color.asColor()
    Box(Modifier.size(size.dp).background(if (d.off) Color.Transparent else color, RoundedCornerShape(4.dp))
        .then(if (d.off) Modifier.border(1.dp, color, RoundedCornerShape(4.dp)) else Modifier), contentAlignment = Alignment.Center) {
        Text(d.label, fontSize = (size * 0.6f).sp, lineHeight = (size * 0.6f).sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (d.off) color else contentColorOn(color))
    }
    if (cell == "dot") Spacer(Modifier.width(0.dp))
}
