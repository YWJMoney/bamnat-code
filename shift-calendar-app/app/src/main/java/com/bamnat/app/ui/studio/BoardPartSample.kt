package com.bamnat.app.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.parts.LocalBlueprint
import com.bamnat.app.ui.parts.PartMods
import com.bamnat.app.ui.parts.fillColor
import com.bamnat.core.recipe.Blueprint

/**
 * [TAB-07] 1.36 판 부품 예시 (PC 확인 b81b643f "근무 띠 판을 골라도 예시가 월 달력이라 바뀌는 모습이 안 보여요"): a small 근무 띠, 타임라인,
 * 메모 보드·표 or 가계부 달력·분류 보드·표 with made-up lines, drawn with the draft's parts, shown in the parts tool when one of their parts
 * is picked. Nothing here is the user's data.
 */
@Composable
internal fun BoardPartSample(id: String, bp: Blueprint) {
    val kind = when {
        id.startsWith("cal.band") -> "band"
        id.startsWith("cal.line") -> "line"
        id.startsWith("cal.weekly") -> "weekly"
        id.startsWith("cal.year") -> "year"
        id.startsWith("memo.cal") -> "memoCal"
        // [MPH-06] 1.38 메모 쓰기 종이·종이 줄·메모 사진.
        id.startsWith("memo.page") || id == "memo.photo" -> "memoPage"
        id == "memo.prop" || id.startsWith("memo.board") -> "memoBoard"
        id.startsWith("memo.table") -> "memoTable"
        id.startsWith("money.cal") -> "moneyCal"
        id.startsWith("money.board") -> "moneyBoard"
        id.startsWith("money.table") -> "moneyTable"
        // 1.39 [FLOW-21] 밤 화면·아침 한 장.
        id == "night" || id.startsWith("night.") -> "night"
        else -> return
    }
    CompositionLocalProvider(LocalBlueprint provides bp) {
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)
                .semantics { contentDescription = "판 예시" },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // PC 확인 c9a89b20: the box names its 판, so a 연간 판 example is not taken for the month.
            Text("${BOARD_NAMES[kind]} 예시 · 지어낸 내용이에요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (kind) {
                "band" -> BandSample()
                "line" -> LineSample()
                "weekly" -> WeeklySample()
                "year" -> YearSample()
                "memoCal" -> MemoCalSample()
                "memoPage" -> com.bamnat.app.ui.memo.MemoPageSample()
                "memoBoard" -> MemoBoardSample()
                "memoTable" -> MemoTableSample()
                "moneyCal" -> MoneyCalSample()
                "moneyBoard" -> MoneyBoardSample()
                "night" -> NightSample()
                else -> MoneyTableSample()
            }
        }
    }
}

private val BOARD_NAMES = mapOf("band" to "근무 띠 판", "line" to "타임라인 판", "weekly" to "주 판", "year" to "연간 판", "memoCal" to "메모 달력",
    "memoBoard" to "메모 보드", "memoTable" to "메모 표", "moneyCal" to "가계부 달력", "moneyBoard" to "가계부 분류 보드", "moneyTable" to "가계부 표",
    "night" to "밤 화면·아침 한 장")

private val SHIFTS = listOf("주" to Color(0xFF42A5F5), "주" to Color(0xFF42A5F5), "야" to Color(0xFF5E35B1), "야" to Color(0xFF5E35B1),
    "비" to Color(0xFF90A4AE), "휴" to Color(0xFFEF9A9A), "휴" to Color(0xFFEF9A9A))

@Composable
private fun BandSample() {
    val bp = LocalBlueprint.current
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("cal.band"), RoundedCornerShape(0.dp))), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (d in 0..6) {
                val (name, color) = SHIFTS[(d + week * 7) % SHIFTS.size]
                val states = setOfNotNull("today".takeIf { week == 0 && d == 2 }, "sel".takeIf { week == 1 && d == 4 })
                Box(Modifier.weight(1f).height(30.dp).background(color.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .then(PartMods.box(bp.style("cal.band.slot", states), RoundedCornerShape(6.dp)))) {
                    Text("${week * 7 + d + 1} $name", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

@Composable
private fun LineSample() {
    val bp = LocalBlueprint.current
    val bar = bp.style("cal.line.bar").fillColor
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("cal.line"), RoundedCornerShape(0.dp)))) {
        val rows = listOf(Triple("1 월", 8 * 60 to 17 * 60, false), Triple("2 화", 22 * 60 to 24 * 60, false), Triple("3 수", 0 to 7 * 60, true))
        for ((i, r) in rows.withIndex()) {
            val (label, span, carried) = r
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp)
                .then(PartMods.box(bp.style("cal.line.row", setOfNotNull("today".takeIf { i == 1 })), RoundedCornerShape(8.dp))),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                val color = bar ?: if (span.first >= 20 * 60 || carried) Color(0xFF5E35B1) else Color(0xFF42A5F5)
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    drawRoundRect(grid.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(6f, 6f))
                    drawRoundRect(color.copy(alpha = if (carried) 0.5f else 0.9f), topLeft = Offset(size.width * span.first / 1440f, 0f),
                        size = Size(size.width * (span.second - span.first) / 1440f, size.height), cornerRadius = CornerRadius(6f, 6f))
                }
            }
        }
    }
}

@Composable
private fun MemoBoardSample() {
    val bp = LocalBlueprint.current
    val prop = bp.style("memo.prop")
    Row(Modifier.fillMaxWidth().then(PartMods.box(bp.style("memo.board"), RoundedCornerShape(0.dp))), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((head, cards) in listOf("#가게" to listOf("마감 정리" to "매출 320,000원"), "#장보기" to listOf("주말 장" to "금액 45,000원"))) {
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                .then(PartMods.box(bp.style("memo.board.col"), RoundedCornerShape(12.dp))).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(head, style = PartMods.text(bp.style("memo.board.head"), MaterialTheme.typography.titleSmall))
                for ((title, p) in cards) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(6.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    Text(p, style = PartMods.text(prop, MaterialTheme.typography.labelSmall),
                        modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .then(PartMods.box(prop, RoundedCornerShape(6.dp))).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun SampleTable(tableId: String, headId: String?, rowId: String, head: List<String>, rows: List<List<String>>) {
    val bp = LocalBlueprint.current
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style(tableId), RoundedCornerShape(10.dp)))) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .then(headId?.let { PartMods.box(bp.style(it), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)) } ?: Modifier)) {
            for (h in head) Text(h, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(6.dp))
        }
        for (r in rows) Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).then(PartMods.box(bp.style(rowId), RoundedCornerShape(0.dp))),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            for (c in r) Text(c, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun MemoTableSample() = SampleTable("memo.table", "memo.table.head", "memo.table.row", listOf("제목", "매출", "별점"),
    listOf(listOf("월 마감", "320,000원", "★★★★☆"), listOf("화 마감", "280,000원", "★★★☆☆")))

@Composable
private fun MoneyTableSample() = SampleTable("money.table", null, "money.table.row", listOf("날짜", "분류", "금액"),
    listOf(listOf("11/1", "식비", "-12,000원"), listOf("11/2", "급여", "+2,400,000원")))

@Composable
private fun MoneyCalSample() {
    val bp = LocalBlueprint.current
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("money.cal"), RoundedCornerShape(10.dp))), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (w in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (d in 1..7) {
                val n = w * 7 + d
                val states = setOfNotNull("today".takeIf { n == 3 }, "sel".takeIf { n == 9 })
                Column(Modifier.weight(1f).heightIn(min = 40.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    .then(PartMods.box(bp.style("money.cal.cell", states), RoundedCornerShape(8.dp))).padding(2.dp)) {
                    Text("$n", style = MaterialTheme.typography.labelSmall)
                    if (n % 3 != 0) Text("${n}천", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun MoneyBoardSample() {
    val bp = LocalBlueprint.current
    Row(Modifier.fillMaxWidth().then(PartMods.box(bp.style("money.board"), RoundedCornerShape(0.dp))), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((cat, sum) in listOf("식비" to "나감 182,000원", "교통" to "나감 54,000원")) {
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                .then(PartMods.box(bp.style("money.board.col"), RoundedCornerShape(14.dp))).padding(8.dp)) {
                Text(cat, style = MaterialTheme.typography.titleSmall)
                Text(sum, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** [TAB-04] N1 주 판 예시: one week of seven tall days, today and the picked day in their states. */
@Composable
private fun WeeklySample() {
    val bp = LocalBlueprint.current
    val names = listOf("월", "화", "수", "목", "금", "토", "일")
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("cal.weekly"), RoundedCornerShape(0.dp)))
        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("11월 3일 – 11월 9일 · 근무 5일 · 40시간", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (d in 0..6) {
                val (shift, color) = SHIFTS[d]
                val states = setOfNotNull("today".takeIf { d == 1 }, "sel".takeIf { d == 3 }, "sat".takeIf { d == 5 }, "sun".takeIf { d == 6 })
                Column(Modifier.weight(1f).heightIn(min = 76.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .then(PartMods.box(bp.style("cal.weekly.day", states), RoundedCornerShape(8.dp))).padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${d + 3} ${names[d]}", style = MaterialTheme.typography.labelSmall, color = if (d == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(shift, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(4.dp)))
                    if (d == 1) Text("병원", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** [TAB-04] N1 연간 판 예시: six small months as blocks of shift colour, this month in its state. */
@Composable
private fun YearSample() {
    val bp = LocalBlueprint.current
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("cal.year"), RoundedCornerShape(0.dp))), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("‹  2026년  ›", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("주 120 · 야 96 · 휴 110", style = MaterialTheme.typography.labelMedium)
        for (row in 0..3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 1..3) {
                val m = row * 3 + c
                // PC 확인 9e234d9e: this month has a thin border before any 꾸밈, so '오늘' is seen to be a state of its own.
                Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                    .then(if (m == 11) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .then(PartMods.box(bp.style("cal.year.month", setOfNotNull("today".takeIf { m == 11 })), RoundedCornerShape(10.dp))).padding(5.dp)) {
                    Text("${m}월", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    for (w in 0..2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        for (d in 0..6) Box(Modifier.weight(1f).height(4.dp).background(SHIFTS[(d + w + m) % SHIFTS.size].second.copy(alpha = 0.8f), RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
    }
}

/** [TAB-04] N9 메모 달력 예시: two weeks with memo titles in their days. */
@Composable
private fun MemoCalSample() {
    val bp = LocalBlueprint.current
    val titles = mapOf(2 to "장보기", 5 to "마감 정리", 9 to "병원", 12 to "월세")
    Column(Modifier.fillMaxWidth().then(PartMods.box(bp.style("memo.cal"), RoundedCornerShape(10.dp))), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (w in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (d in 1..7) {
                val n = w * 7 + d
                Column(Modifier.weight(1f).heightIn(min = 44.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    .then(PartMods.box(bp.style("memo.cal.cell", setOfNotNull("today".takeIf { n == 3 }, "sel".takeIf { n == 9 })), RoundedCornerShape(8.dp))).padding(2.dp)) {
                    Text("$n", style = MaterialTheme.typography.labelSmall)
                    titles[n]?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

/** 1.39 [FLOW-21] 밤 화면 예시: the dark screen's date, clock, tomorrow's line, a card and the button, then 아침 한 장, with the draft's night parts. */
@Composable
private fun NightSample() {
    val bp = LocalBlueprint.current
    val ground = bp.style("night")
    // The night palette only (not NightSurface, which also turns the status bar icons light).
    MaterialTheme(colorScheme = com.bamnat.app.ui.theme.NightPalette.scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
            .then(PartMods.box(ground, RoundedCornerShape(10.dp))).padding(10.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("11월 7일 금요일", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = PartMods.text(bp.style("night.date"), MaterialTheme.typography.labelLarge))
            val clock = bp.style("night.clock")
            Text("23:40", color = MaterialTheme.colorScheme.onSurface,
                style = PartMods.text(clock, com.bamnat.app.ui.theme.NumberStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Light)),
                modifier = PartMods.box(clock, RoundedCornerShape(12.dp)))
            val line = bp.style("night.line")
            Text("내일 07:00 주간 · 알람 06:00 켜짐", color = line.ink?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.onSurface,
                style = PartMods.text(line.copy(ink = null), MaterialTheme.typography.bodyMedium))
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .then(PartMods.box(bp.style("night.card"), RoundedCornerShape(12.dp))).padding(8.dp)) {
                Text("잠드는 소리 · 빗소리 30분", style = MaterialTheme.typography.bodySmall)
            }
            val button = bp.style("night.button")
            val buttonFill = button.fillColor ?: MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).background(buttonFill, PartMods.shapeOf(button, RoundedCornerShape(20.dp))),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("잠들었어요", color = button.ink?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.onPrimary,
                    style = PartMods.text(button.copy(ink = null), MaterialTheme.typography.titleSmall))
            }
        }
    }
    val morning = bp.style("night.morning")
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
        .then(PartMods.box(morning, PartMods.shapeOf(morning, RoundedCornerShape(12.dp)))).padding(10.dp)) {
        Text("잘 잤어요? · 아침 한 장", style = PartMods.text(morning, MaterialTheme.typography.titleSmall))
        Text("6시간 40분 · 😴 🙂 😀", style = PartMods.text(morning, MaterialTheme.typography.bodySmall))
    }
}
