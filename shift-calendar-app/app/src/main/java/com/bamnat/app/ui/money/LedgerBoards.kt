package com.bamnat.app.ui.money

import androidx.compose.foundation.shape.ZeroCornerSize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.core.money.LedgerBoard
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import androidx.compose.ui.semantics.Role

/** [TAB-06] 1.36 가계부 판 칩: 목록 · 달력 · 분류 보드 · 표. The period and filters above stay as they are. */
@Composable
internal fun LedgerBoardChips(board: LedgerBoard, onPick: (LedgerBoard) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in LedgerBoard.entries) {
            FilterChip(selected = board == b, onClick = { onPick(b) }, label = { Text(b.label) },
                modifier = Modifier.semantics { contentDescription = "가계부 ${b.label}${com.bamnat.app.ui.Fmt.ro(b.label)} 보기" })
        }
    }
}

@Composable
private fun signedText(e: MoneyEntry): Pair<String, Color> = when (e.kind) {
    MoneyKind.INCOME -> "+" + Fmt.won(e.amount) to MaterialTheme.colorScheme.primary
    MoneyKind.EXPENSE -> "-" + Fmt.won(e.amount) to MaterialTheme.colorScheme.error
    MoneyKind.TRANSFER -> Fmt.won(e.amount) to MaterialTheme.colorScheme.onSurfaceVariant
}

/** [TAB-06] 가계부 달력: the month with each day's money out (and in) in its cell; a tap picks the day for the lines under it. */
@Composable
internal fun LedgerMonthGrid(month: YearMonth, shown: List<MoneyEntry>, picked: LocalDate?, onPick: (LocalDate) -> Unit,
    /** 1.38 PC 7차: with a period longer than the month, the months it holds, and where ‹ › go. Null for 이 달. */
    within: ClosedRange<YearMonth>? = null, onMonth: (YearMonth) -> Unit = {}) {
    val days = remember(shown, month) { LedgerBoard.days(shown, month) }
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weeks = (0 until 6).map { w -> (0 until 7).map { first.plusDays((w * 7 + it).toLong()) } }.filter { wk -> wk.any { YearMonth.from(it) == month } }
    // [TAB-07] 부품 money.cal · money.cal.cell (오늘, 고른 날).
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val today = com.bamnat.core.time.Today.date()
    Column(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("money.cal"), MaterialTheme.shapes.small)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (within != null) {
            // 기간을 '전체'로 골라도 달력은 한 달씩: 그 기간 안의 달을 여기서 넘겨요.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > within.start) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                }
                Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
                androidx.compose.material3.IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < within.endInclusive) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                }
            }
            Text("달력은 한 달씩 보여요. 화살표로 고른 기간 안의 달을 넘겨요.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            for (d in listOf("일", "월", "화", "수", "목", "금", "토")) Text(d, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        for (week in weeks) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (date in week) {
                val inMonth = YearMonth.from(date) == month
                val day = days[date]
                val on = date == picked
                Column(
                    Modifier.weight(1f).heightIn(min = 54.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                        .then(if (on) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                        .then(com.bamnat.app.ui.parts.PartMods.box(bp.style("money.cal.cell", setOfNotNull("today".takeIf { date == today }, "sel".takeIf { on }), com.bamnat.app.logic.scopeOn(date)), MaterialTheme.shapes.small))
                        .clickable(role = Role.Button, enabled = inMonth) { onPick(date) }
                        .clearAndSetSemantics {
                            contentDescription = "${Fmt.monthDay(date)}" + (day?.let { d -> " 지출 ${Fmt.won(d.expense)}" + (if (d.income > 0) " 수입 ${Fmt.won(d.income)}" else "") } ?: " 기록 없음")
                            selected = on
                        }
                        .padding(3.dp),
                ) {
                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall,
                        color = if (!inMonth) Color.Transparent else MaterialTheme.colorScheme.onSurface)
                    if (inMonth && day != null) {
                        if (day.expense > 0) Text(Fmt.wonShort(day.expense), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                        if (day.income > 0) Text(Fmt.wonShort(day.income), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** [TAB-06] 분류 보드: a column a category, its total on top and its lines under it; a line opens as in the list. */
@Composable
internal fun LedgerCategoryBoard(shown: List<MoneyEntry>, onOpen: (MoneyEntry) -> Unit) {
    val columns = remember(shown) { LedgerBoard.columns(shown) }
    // [TAB-07] 부품 money.board · money.board.col.
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val colPart = bp.style("money.board.col")
    Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("money.board"), RoundedCornerShape(0.dp))).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (col in columns) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(220.dp).then(com.bamnat.app.ui.parts.PartMods.box(colPart, MaterialTheme.shapes.medium))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(col.category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    if (col.expense > 0) Text("나감 ${Fmt.won(col.expense)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    if (col.income > 0) Text("들어옴 ${Fmt.won(col.income)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${col.entries.size}건", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    for (e in col.entries.take(MAX_BOARD_LINES)) {
                        val (amount, color) = signedText(e)
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(e) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(LedgerBoard.what(e).ifBlank { e.category }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Fmt.monthDay(e.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(amount, style = MaterialTheme.typography.labelMedium, color = color)
                        }
                    }
                    if (col.entries.size > MAX_BOARD_LINES) Text("외 ${col.entries.size - MAX_BOARD_LINES}건 · 목록에서 이 분류로 거르면 다 보여요",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private const val MAX_BOARD_LINES = 30

/** [TAB-06] 표: 날짜 · 분류 · 내용 · 금액, a head tap sorts by it (again: the other way). */
@Composable
internal fun LedgerTable(shown: List<MoneyEntry>, sort: Pair<String, Boolean>, onSort: (Pair<String, Boolean>) -> Unit, onOpen: (MoneyEntry) -> Unit) {
    val rows = remember(shown, sort) { LedgerBoard.sortTable(shown, sort.first, sort.second) }
    // [TAB-07] 부품 money.table · money.table.row. PC 확인 b81b643f: the date wrapped to two lines with the right side empty, so the
    // date is short ("11/12", one line) and 내용 takes the room that is left.
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.PartMods.box(bp.style("money.table"), MaterialTheme.shapes.small))) {
    val fixed = 64.dp + 84.dp + 104.dp
    val widths = mapOf("date" to 64.dp, "category" to 84.dp, "what" to maxOf(120.dp, maxWidth - fixed), "amount" to 104.dp)
    val total = widths.values.fold(0.dp) { a, b -> a + b }
    val sideways = rememberScrollState()
    Column(Modifier.fillMaxWidth().horizontalScroll(sideways)) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))) {
            for ((key, label) in LedgerBoard.TABLE_COLUMNS) {
                val on = sort.first == key
                Row(Modifier.width(widths[key]!!).clickable(role = Role.Button) { onSort(key to (if (on) !sort.second else key == "date" || key == "amount")) }.padding(8.dp)
                    .semantics { contentDescription = "$label 열, 눌러 정렬"; selected = on }, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    if (on) Icon(if (sort.second) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(14.dp))
                }
            }
        }
        for (e in rows.take(MAX_TABLE_ROWS)) {
            val (amount, color) = signedText(e)
            Row(Modifier.then(com.bamnat.app.ui.parts.PartMods.box(com.bamnat.app.logic.scopeFor(e.date) { com.bamnat.core.layout.TabFacts.money(e) }.let { bp.style("money.table.row", tableStates(e), it) }, RoundedCornerShape(0.dp))).clickable(role = Role.Button) { onOpen(e) }.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${e.date.monthValue}/${e.date.dayOfMonth}", style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                    modifier = Modifier.width(widths["date"]!!).padding(horizontal = 8.dp).semantics { contentDescription = Fmt.monthDay(e.date) })
                Text(e.category, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(widths["category"]!!).padding(horizontal = 8.dp))
                Text(LedgerBoard.what(e), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(widths["what"]!!).padding(horizontal = 8.dp))
                Text(amount, style = MaterialTheme.typography.bodySmall, color = color, textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                    modifier = Modifier.width(widths["amount"]!!).padding(horizontal = 8.dp))
            }
            HorizontalDivider(Modifier.width(total), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        if (rows.size > MAX_TABLE_ROWS) Text("앞의 ${MAX_TABLE_ROWS}줄만 보여요. 기간을 좁히면 다 보여요.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        Box(Modifier.height(8.dp))
    }
    }
}

private const val MAX_TABLE_ROWS = 300

/** A table line's 들어온 돈·나간 돈, as the list's line takes them (on "in"/"out"). */
private fun tableStates(e: MoneyEntry): Set<String> = setOfNotNull(when (e.kind) { MoneyKind.INCOME -> "in"; MoneyKind.EXPENSE -> "out"; else -> null })
