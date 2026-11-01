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
        id == "memo.prop" || id.startsWith("memo.board") -> "memoBoard"
        id.startsWith("memo.table") -> "memoTable"
        id.startsWith("money.cal") -> "moneyCal"
        id.startsWith("money.board") -> "moneyBoard"
        id.startsWith("money.table") -> "moneyTable"
        else -> return
    }
    CompositionLocalProvider(LocalBlueprint provides bp) {
        Column(
            Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(10.dp)
                .semantics { contentDescription = "판 예시" },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("예시 · 지어낸 내용이에요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (kind) {
                "band" -> BandSample()
                "line" -> LineSample()
                "memoBoard" -> MemoBoardSample()
                "memoTable" -> MemoTableSample()
                "moneyCal" -> MoneyCalSample()
                "moneyBoard" -> MoneyBoardSample()
                else -> MoneyTableSample()
            }
        }
    }
}

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
