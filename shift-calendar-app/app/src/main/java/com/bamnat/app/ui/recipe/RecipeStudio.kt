package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.TopLevel
import com.bamnat.app.ui.calendar.CalendarPart
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.memo.MemoPart
import com.bamnat.app.ui.money.MoneyPart
import com.bamnat.app.ui.stats.StatsPart
import com.bamnat.app.ui.theme.shapesFor
import com.bamnat.core.layout.PageLayout
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.Field
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.Ref
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.recipe.WEEK_DAYS
import com.bamnat.core.work.WorkStyle

/**
 * 1.35 꾸미기 스튜디오 (사용자 2026-10-30 "직접 실제 어플 모양을 보면서 커스텀 하고 삭제하고 추가하고"): the recipe is made on an
 * example app, never on mine. A phone in the middle shows each bottom tab with example days and numbers in the recipe's look; under
 * it, what that tab's screen can change — moved, hidden, added back, coloured — and the phone follows at once. Pieces without a
 * picture (알람, 메모 틀, 급여·현장·가게 규칙, 투자 원칙) are edited there as forms built from the piece catalog.
 */
@Composable
internal fun RecipeStudio(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    var tab by rememberSaveable { mutableStateOf(TabOrder.CALENDAR) }
    val r = s.recipe
    StudioStart(s, vm)
    Text("예시 앱에서 바로 꾸며요. 내 앱은 바뀌지 않아요", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    MockPhone(r, vm.style, tab) { tab = it }
    Text(TopLevel.entries.firstOrNull { it.route == tab }?.label.orEmpty() + " 화면에서 고칠 것", style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
    StudioEditors(r, vm, tab)
}

/** Where a recipe starts: what I have now, one of the 보여주기 판, or nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioStart(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    var open by rememberSaveable { mutableStateOf(s.recipe.pieces.isEmpty()) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("어디서 시작할까요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { open = !open }) { Text(if (open) "접기" else "다시 고르기") }
            }
            if (!open) return@Column
            Text("어느 쪽에서 시작해도 내 앱은 그대로예요. 다 만든 뒤 내 앱에 넣을지는 따로 골라요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.showcases.isNotEmpty()) {
                Text("이런 느낌으로 시작", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.showcases.forEach { sc ->
                        OutlinedCard(Modifier.width(150.dp).clickable(onClickLabel = "이 판으로 시작") { vm.startFrom(sc); open = false }) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RecipeCover(sc.cover, size = 40.dp)
                                Text(sc.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(sc.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.startMine(); open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 지금 설정에서") }
                OutlinedButton(onClick = { vm.startBlank(); open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("빈 판에서") }
            }
        }
    }
}

// ---- the example phone ----

/** The example app: the chosen tab's screen and the bottom tabs in the recipe's order, with its corners. */
@Composable
private fun MockPhone(r: Recipe, style: WorkStyle?, tab: String, onTab: (String) -> Unit) {
    val resolved = remember(r) { r.pieces.associate { p -> p.kind to runCatching { RecipeResolve.piece(r, p, RecipeResolve.answers(r, emptyMap())) }.getOrDefault(p.values) } }
    val corners = resolved[PieceKind.PHOTO_LOOK]?.get("corners") as? String
    @Suppress("UNCHECKED_CAST")
    val tabs = TabOrder.of((resolved[PieceKind.LAYOUT]?.get("tabs") as? List<String>).orEmpty())
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography, shapes = shapesFor(corners)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 340.dp).fillMaxWidth().height(560.dp)
                    .border(6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(32.dp))
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
                    .semantics { contentDescription = "예시 앱 화면" },
            ) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (tab) {
                        TabOrder.CALENDAR -> MockCalendar(resolved, style)
                        TabOrder.MONEY -> MockParts(resolved[PieceKind.CARDS]?.get("money") as? String, MoneyPart.entries.map { Triple(it.id, it.label, it.what) })
                        TabOrder.MEMOS -> MockMemos(resolved)
                        TabOrder.STATS -> MockParts(resolved[PieceKind.CARDS]?.get("stats") as? String, StatsPart.entries.map { Triple(it.id, it.label, it.what) }, bars = true)
                        TabOrder.PLAZA -> MockNote("광장", "광장은 모두 같은 모양이에요. 여기서 꾸밀 것은 없어요")
                        else -> MockSettings(resolved)
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    tabs.forEach { key ->
                        val t = TopLevel.entries.firstOrNull { it.route == key } ?: return@forEach
                        val on = key == tab
                        Column(Modifier.clickable(onClickLabel = "${t.label} 화면 보기") { onTab(key) }.semantics { selected = on }.padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) {
                                Icon(if (on) t.selectedIcon else t.icon, null, Modifier.size(20.dp))
                            }
                            Text(t.label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The kinds of day an example calendar shows when the recipe brings none: what this kind of work usually has. */
private fun exampleDays(style: WorkStyle?): List<Map<String, Any?>> = when (style) {
    WorkStyle.SHIFT, null -> listOf(day("주간", "day", "주", 0xFFEF6C00), day("야간", "night", "야", 0xFF3949AB), day("휴무", "off", "휴", 0xFF757575))
    WorkStyle.HOURLY -> listOf(day("근무", "day", "근", 0xFF2E7D32), day("휴무", "off", "휴", 0xFF757575))
    else -> listOf(day("근무", "day", "근", 0xFF1E88E5), day("휴무", "off", "휴", 0xFF757575))
}

private fun day(name: String, role: String, label: String, color: Long) = mapOf("name" to name, "role" to role, "label" to label, "color" to color)

@Suppress("UNCHECKED_CAST")
@Composable
private fun MockCalendar(resolved: Map<PieceKind, Map<String, Any?>>, style: WorkStyle?) {
    val line = resolved[PieceKind.LAYOUT]?.get("calendar") as? String
    val placed = PageLayout.decode(line).arrange(CalendarPart.entries.toList()) { it.id }.filter { it.shown }
    placed.filter { it.part.above && it.part != CalendarPart.SUMMARY && it.part != CalendarPart.COUNTS }.forEach { MockCard(it.part.label, it.part.what) }
    if (placed.any { it.part == CalendarPart.SUMMARY }) MockSummary()
    val days = (resolved[PieceKind.SHIFT_LOOK]?.get("items") as? List<Map<String, Any?>>)?.takeIf { it.isNotEmpty() } ?: exampleDays(style)
    if (placed.any { it.part == CalendarPart.COUNTS }) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d -> Text("${d["label"]} ${if (d["role"] == "off") 8 else 11}", style = MaterialTheme.typography.labelSmall,
            color = ((d["color"] as? Long) ?: 0xFF757575L).asColor()) }
    }
    MockMonth(days, resolved[PieceKind.CAL_THEME].orEmpty())
    placed.filter { !it.part.above }.forEach { MockCard(it.part.label, it.part.what) }
}

/** An example month: the kinds of day in turn (2 days each), today ringed, 토·일 in the theme's colours. */
@Composable
private fun MockMonth(days: List<Map<String, Any?>>, theme: Map<String, Any?>) {
    val cell = theme["cell"] as? String ?: "tint"
    val today = (theme["today"] as? Long)?.asColor() ?: MaterialTheme.colorScheme.primary
    val sat = (theme["sat"] as? Long)?.asColor() ?: Color(0xFF1565C0)
    val sun = (theme["sun"] as? Long)?.asColor() ?: Color(0xFFC62828)
    val names = listOf("일", "월", "화", "수", "목", "금", "토")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("11월", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row { names.forEachIndexed { i, n -> Text(n, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
            color = when (i) { 0 -> sun; 6 -> sat; else -> MaterialTheme.colorScheme.onSurfaceVariant }) } }
        for (week in 0 until 5) Row {
            for (dow in 0 until 7) {
                val n = week * 7 + dow - 5 + 1
                Box(Modifier.weight(1f).aspectRatio(0.8f).padding(1.dp)) {
                    if (n in 1..30) {
                        val d = days[((n - 1) / 2) % days.size]
                        val color = ((d["color"] as? Long) ?: 0xFF757575L)
                        val bg = when (cell) { "fill" -> color.asColor().copy(alpha = 0.85f); "dot" -> Color.Transparent; else -> color.asColor().copy(alpha = 0.16f) }
                        Column(Modifier.fillMaxSize().background(bg, MaterialTheme.shapes.extraSmall)
                            .then(if (n == 12) Modifier.border(2.dp, today, MaterialTheme.shapes.extraSmall) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontSize = 9.sp, color = when {
                                cell == "fill" -> contentColorOn(color)
                                dow == 0 -> sun; dow == 6 -> sat
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                            if (cell == "dot") Box(Modifier.size(6.dp).background(color.asColor(), CircleShape))
                            else Text((d["emoji"] as? String) ?: d["label"].toString(), fontSize = 10.sp, maxLines = 1,
                                color = if (cell == "fill") contentColorOn(color) else color.asColor(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MockSummary() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("근무" to "18일", "시간" to "162h", "급여" to "예상").forEach { (k, v) ->
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(6.dp)) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(v, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun MockCard(title: String, what: String, bars: Boolean = false) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(what, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (bars) Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                listOf(0.5f, 0.8f, 0.65f, 1f, 0.7f).forEach { h -> Box(Modifier.width(14.dp).fillMaxSize(h).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)) }
            }
        }
    }
}

@Composable
private fun MockParts(line: String?, parts: List<Triple<String, String, String>>, bars: Boolean = false) {
    val placed = PageLayout.decode(line).arrange(parts) { it.first }.filter { it.shown }
    placed.forEach { (p, _, _) -> MockCard(p.second, p.third, bars && p.first in setOf("weeks", "trend", "year", "three")) }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun MockMemos(resolved: Map<PieceKind, Map<String, Any?>>) {
    val placed = PageLayout.decode(resolved[PieceKind.CARDS]?.get("memo") as? String).arrange(MemoPart.entries.toList()) { it.id }.filter { it.shown }
    placed.forEach { p ->
        when (p.part) {
            MemoPart.SEARCH -> Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge).padding(10.dp)) {
                Text("🔍 제목·내용·#태그 찾기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MemoPart.NOTEBOOKS -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("전체", "근무", "개인").forEach { Text(it, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall) }
            }
            else -> Unit
        }
    }
    val tpls = (resolved[PieceKind.MEMO_TEMPLATES]?.get("items") as? List<Map<String, Any?>>).orEmpty()
    val journal = (resolved[PieceKind.INVEST]?.get("journal") as? List<String>).orEmpty()
    if (tpls.isEmpty() && journal.isEmpty()) MockNote("메모", "메모 틀을 더하면 '틀에서 시작'에 여기처럼 보여요")
    tpls.forEach { t ->
        MockCard("${t["emoji"] ?: "📝"} ${t["title"]}" + ((t["repeat"] as? String)?.let { " · ${repeatLabel(it)}" } ?: ""),
            (t["check"] as? List<String>)?.joinToString(" · ") { "☐ $it" }?.ifEmpty { null } ?: (t["body"] as? String).orEmpty())
    }
    if (journal.isNotEmpty()) MockCard("📒 투자일지", journal.joinToString(" · "))
}

@Composable
private fun MockSettings(resolved: Map<PieceKind, Map<String, Any?>>) {
    val w = resolved[PieceKind.WIDGET]
    MockNote("설정", "아래 탭 순서, 위젯 모양, 모서리를 여기서 바꿔요")
    if (w != null) {
        val alpha = ((w["alpha"] as? Number)?.toInt() ?: 100).coerceIn(30, 100)
        val dark = w["theme"] == "dark"
        Box(Modifier.fillMaxWidth().height(70.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFF26A69A))), MaterialTheme.shapes.medium).padding(10.dp)) {
            Column(Modifier.fillMaxSize().background((if (dark) Color(0xFF1E1E24) else Color(0xFFFAFAFA)).copy(alpha = alpha / 100f), MaterialTheme.shapes.small).padding(8.dp)) {
                Text("위젯 · 오늘 주간 07:00", style = MaterialTheme.typography.labelMedium, color = if (dark) Color.White else Color(0xFF1B1B1F))
                if (w["memo"] == true) Text("메모 한 줄", style = MaterialTheme.typography.labelSmall, color = if (dark) Color.White else Color(0xFF1B1B1F))
            }
        }
    }
}

@Composable
private fun MockNote(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun repeatLabel(r: String) = when {
    r.startsWith("M") -> "매달 ${r.drop(1)}일"
    r.startsWith("W") -> "매주 " + listOf("월", "화", "수", "목", "금", "토", "일").getOrElse((r.drop(1).toIntOrNull() ?: 1) - 1) { "" } + "요일"
    else -> r
}

// ---- what each tab's screen can change ----

/** The pieces a tab's screen shows, in the order they are edited there. */
private fun piecesOn(tab: String): List<PieceKind> = when (tab) {
    TabOrder.CALENDAR -> listOf(PieceKind.SHIFT_LOOK, PieceKind.CAL_THEME, PieceKind.ALARMS)
    TabOrder.MONEY -> listOf(PieceKind.SHOP, PieceKind.FIELD_SHEET)
    TabOrder.MEMOS -> listOf(PieceKind.MEMO_TEMPLATES, PieceKind.INVEST)
    TabOrder.STATS -> listOf(PieceKind.HOURLY_RULES)
    TabOrder.SETTINGS -> listOf(PieceKind.WIDGET)
    else -> emptyList()
}

@Composable
private fun StudioEditors(r: Recipe, vm: RecipeMakeViewModel, tab: String) {
    val fits: (PieceKind) -> Boolean = { k -> vm.style == null || vm.style in k.styles }
    @Suppress("UNCHECKED_CAST")
    when (tab) {
        TabOrder.CALENDAR -> PartsEditor("달력 화면 카드", CalendarPart.entries.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.LAYOUT)?.values?.get("calendar") as? String) { line -> vm.setValue(PieceKind.LAYOUT, "calendar", line) }
        TabOrder.MONEY -> PartsEditor("가계부 카드", MoneyPart.entries.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("money") as? String) { line -> vm.setValue(PieceKind.CARDS, "money", line) }
        TabOrder.MEMOS -> PartsEditor("메모 화면", MemoPart.entries.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("memo") as? String) { line -> vm.setValue(PieceKind.CARDS, "memo", line) }
        TabOrder.STATS -> PartsEditor("통계 카드", StatsPart.entries.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("stats") as? String) { line -> vm.setValue(PieceKind.CARDS, "stats", line) }
        TabOrder.SETTINGS -> {
            TabsEditor((r.piece(PieceKind.LAYOUT)?.values?.get("tabs") as? List<String>).orEmpty()) { list -> vm.setValue(PieceKind.LAYOUT, "tabs", list) }
            CornersEditor(r.piece(PieceKind.PHOTO_LOOK)?.values?.get("corners") as? String) { c -> vm.setValue(PieceKind.PHOTO_LOOK, "corners", c) }
        }
        else -> Unit
    }
    piecesOn(tab).filter(fits).forEach { k -> PieceSection(r, k, vm) }
    if (tab == TabOrder.CALENDAR) ThemeTokens(r) { f -> vm.edit(f) }
}

/** A page's parts in order: up and down for the movable ones, shown or hidden for each, and 처음대로. */
@Composable
private fun PartsEditor(title: String, parts: List<Triple<String, String, Boolean>>, line: String?, onChange: (String?) -> Unit) {
    val placed = PageLayout.decode(line).arrange(parts) { it.first }
    StudioCard(title, if (line == null) "처음 모양이에요. 바꾸면 레시피에 들어가요" else "바꾼 순서와 숨김이 레시피에 들어가요",
        onReset = line?.let { { onChange(null) } }) {
        placed.forEachIndexed { i, p ->
            val (id, label, movable) = p.part
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = if (p.shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (movable) {
                    IconButton(onClick = { onChange(PageLayout.of(PageLayout.move(placed, i, -1)) { it.first }.encode()) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "$label 위로")
                    }
                    IconButton(onClick = { onChange(PageLayout.of(PageLayout.move(placed, i, 1)) { it.first }.encode()) }, enabled = i < placed.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "$label 아래로")
                    }
                }
                Switch(p.shown, { on -> onChange(PageLayout.of(placed.map { if (it.part.first == id) it.copy(shown = on) else it }) { it.first }.encode()) },
                    modifier = Modifier.semantics { contentDescription = "$label 보이기" })
            }
        }
    }
}

@Composable
private fun TabsEditor(saved: List<String>, onChange: (List<String>?) -> Unit) {
    val order = TabOrder.of(saved)
    StudioCard("아래 탭 순서", "맨 앞 탭이 앱을 열면 처음 보이는 화면이에요", onReset = saved.takeIf { it.isNotEmpty() }?.let { { onChange(null) } }) {
        order.forEachIndexed { i, key ->
            val t = TopLevel.entries.firstOrNull { it.route == key } ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(t.label, Modifier.weight(1f))
                IconButton(onClick = { onChange(TabOrder.toSave(TabOrder.move(order, key, i - 1)).ifEmpty { null }) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "${t.label} 앞으로") }
                IconButton(onClick = { onChange(TabOrder.toSave(TabOrder.move(order, key, i + 1)).ifEmpty { null }) }, enabled = i < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "${t.label} 뒤로") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CornersEditor(corners: String?, onChange: (String?) -> Unit) {
    StudioCard("모서리", "카드와 버튼의 둥근 정도예요", onReset = corners?.let { { onChange(null) } }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("round" to "둥글게", "soft" to "보통", "square" to "각지게").forEach { (k, label) ->
                FilterChip(corners == k, { onChange(k) }, label = { Text(label) }, shape = shapesFor(k).small)
            }
        }
    }
}

/** One piece on its own: in the recipe or not, and its fields when it is. */
@Composable
private fun PieceSection(r: Recipe, kind: PieceKind, vm: RecipeMakeViewModel) {
    val piece = r.piece(kind)
    StudioCard(kind.label, if (piece == null) "넣지 않았어요. 켜면 예시 값으로 시작해요" else "받는 분이 고를 수 있는 한 묶음이에요",
        trailing = { Switch(piece != null, { on -> vm.setPiece(kind, if (on) vm.starter(kind) else null) }, modifier = Modifier.semantics { contentDescription = "${kind.label} 넣기" }) }) {
        if (piece != null) FieldsForm(kind.fields.filter(::studioField), piece.values, kind) { k, v -> vm.setValue(kind, k, v) }
    }
}

/** Fields the studio edits by hand: the pictures (그림 꾸미기) come from the settings screen, the company file from 회사 화면. */
private fun studioField(f: Field): Boolean = when (f) {
    is Field.Text -> f.guard || f.key == "body"
    is Field.Items -> f.key != "icons"
    else -> true
}

@Composable
private fun StudioCard(title: String, sub: String, onReset: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onReset?.let { TextButton(onClick = it) { Text("처음대로") } }
                trailing?.invoke()
            }
            body()
        }
    }
}

// ---- a form from the piece catalog ----

private val KEY_LABELS = com.bamnat.core.recipe.RecipeWords.KEYS
private val PICK_LABELS = com.bamnat.core.recipe.RecipeWords.PICKS

@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
private fun FieldsForm(fields: List<Field>, values: Map<String, Any?>, kind: PieceKind, onChange: (String, Any?) -> Unit) {
    fields.forEach { f ->
        val label = KEY_LABELS[f.key] ?: f.key
        val v = values[f.key]
        when (f) {
            is Field.Text -> OutlinedTextField((v as? String).orEmpty(), { t -> onChange(f.key, t.take(f.max).ifEmpty { if (f.optional) null else t }) },
                label = { Text(label) }, singleLine = !f.lines, minLines = if (f.lines) 3 else 1, modifier = Modifier.fillMaxWidth())
            is Field.Num -> NumberRow(label, (v as? Number)?.toDouble(), f.decimals, f.lo, f.hi) { d -> onChange(f.key, d?.let { if (f.decimals == 0) it.toLong() else it }) }
            is Field.Money -> Text(if (v is Ref.Input || v == null) "$label: 받는 분이 직접 넣어요" else "$label: 모두에게 같은 금액",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Field.Color -> ColorSwatch((v as? Long) ?: 0xFF5C6BC0L, label + if (v is Ref) " (테마·받는 분 색)" else "", { c -> onChange(f.key, c) })
            is Field.Emoji -> {
                var pick by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { pick = true }) { Text("$label: ${(v as? String) ?: "없음"}") }
                if (pick) EmojiPickerDialog(onPick = { e -> onChange(f.key, e); pick = false }, onDismiss = { pick = false }, onClear = { onChange(f.key, null); pick = false })
            }
            is Field.Flag -> Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Switch(v == true, { on -> onChange(f.key, on) })
            }
            is Field.Pick -> {
                Text(label, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.values.forEach { p -> FilterChip(v == p, { onChange(f.key, if (v == p && f.optional) null else p) }, label = { Text(PICK_LABELS[p] ?: p) }) }
                }
            }
            is Field.Clock -> OutlinedTextField((v as? String).orEmpty(), { t ->
                val clean = t.filter { it.isDigit() || it == ':' }.take(5)
                onChange(f.key, clean.takeIf { Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(it) } ?: if (clean.isEmpty() && f.optional) null else v)
            }, label = { Text("$label (예: 09:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            is Field.Texts -> TextsEditor(label, (v as? List<String>).orEmpty(), f.max, f.itemMax) { onChange(f.key, it) }
            is Field.Nums -> {
                var text by remember(kind, f.key) { mutableStateOf((v as? List<Number>).orEmpty().joinToString(", ") { n -> n.toDouble().let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() } }) }
                OutlinedTextField(text, { t ->
                    text = t
                    val list = t.split(',', ' ').mapNotNull { it.trim().toDoubleOrNull() }.filter { it in f.lo..f.hi }.take(f.max)
                    onChange(f.key, list.map { if (f.decimals == 0) it.toLong() else it })
                }, label = { Text("$label (쉼표로 나눠요)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            is Field.Days -> {
                Text(label, style = MaterialTheme.typography.labelLarge)
                val on = (v as? List<String>).orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK_DAYS.forEach { d -> FilterChip(d in on, { onChange(f.key, WEEK_DAYS.filter { x -> if (x == d) d !in on else x in on }) }, label = { Text(PICK_LABELS.getValue(d)) }) }
                }
            }
            is Field.Items -> ItemsEditor(label, f, (v as? List<Map<String, Any?>>).orEmpty(), kind) { onChange(f.key, it) }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Double?, decimals: Int, lo: Double, hi: Double, onChange: (Double?) -> Unit) {
    var text by remember(label) { mutableStateOf(value?.let { if (decimals == 0) it.toLong().toString() else it.toString() }.orEmpty()) }
    OutlinedTextField(text, { t ->
        text = t.filter { it.isDigit() || (decimals > 0 && it == '.') }.take(8)
        onChange(text.toDoubleOrNull()?.coerceIn(lo, hi))
    }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${fmt(lo)}~${fmt(hi)}") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
}

private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

@Composable
private fun TextsEditor(label: String, list: List<String>, max: Int, itemMax: Int, onChange: (List<String>) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    list.forEachIndexed { i, t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(t, { s -> onChange(list.toMutableList().apply { set(i, s.take(itemMax)) }) }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(list.toMutableList().apply { removeAt(i) }) }) { Icon(Icons.Filled.Close, "$label ${i + 1}번 빼기") }
        }
    }
    if (list.size < max) TextButton(onClick = { onChange(list + "") }) { Icon(Icons.Filled.Add, null); Text(" 더하기") }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ItemsEditor(label: String, f: Field.Items, list: List<Map<String, Any?>>, kind: PieceKind, onChange: (List<Map<String, Any?>>) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    list.forEachIndexed { i, item ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(((item["name"] ?: item["title"] ?: item["text"] ?: item["role"]?.let { PICK_LABELS[it] }) as? String) ?: "${i + 1}번",
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (list.size > f.min) IconButton(onClick = { onChange(list.toMutableList().apply { removeAt(i) }) }) { Icon(Icons.Filled.Close, "${i + 1}번 빼기") }
                }
                FieldsForm(f.fields.filter(::studioField), item, kind) { k, v -> onChange(list.toMutableList().apply { set(i, (item + (k to v)).filterValues { x -> x != null }) }) }
            }
        }
    }
    if (list.size < f.max) TextButton(onClick = { onChange(list + newItem(f)) }) { Icon(Icons.Filled.Add, null); Text(" 더하기") }
}

/** A new row of [f]: what each field needs to be valid, so it can be filled in from there. */
private fun newItem(f: Field.Items): Map<String, Any?> = f.fields.mapNotNull { x ->
    when (x) {
        is Field.Text -> if (x.optional) null else x.key to if (x.min > 0) (if (x.key == "label") "새" else "새로") else ""
        is Field.Pick -> if (x.optional) null else x.key to x.values.first()
        is Field.Color -> if (x.optional) null else x.key to 0xFF5C6BC0L
        is Field.Nums -> x.key to listOf(60L)
        is Field.Num -> if (x.optional) null else x.key to x.lo.coerceAtLeast(1.0).let { if (x.decimals == 0) it.toLong() else it }
        is Field.Clock -> if (x.optional) null else x.key to "18:00"
        is Field.Texts -> x.key to emptyList<String>()
        else -> null
    }
}.toMap()
