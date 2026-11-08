package com.bamnat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * 1.39 [FLOW-31] One small figure of a summary: what it is, the number (its unit small, [numberWithUnit]), its meaning colour and,
 * when given, one line of what it means, opened with ›.
 */
data class StatCell(val label: String, val value: String, val unit: String = "", val tone: Tone = Tone.Plain, val meaning: String? = null)

/**
 * 1.39 [FLOW-31] 요약 2×2 칸 + 뜻 보기 (흐름·디자인 계획 9-3, 10-5): the four figures under a page's one number, as 2×2 on a phone
 * and 4 in a row when unfolded (600dp and wider). A cell with a meaning has › and a tap says it in one line under the grid; another
 * tap or another cell changes it. Same frame for 급여 (기본·수당·일한 시간·일한 날), 가계부 and 수면.
 */
@Composable
fun StatGrid(cells: List<StatCell>, modifier: Modifier = Modifier) {
    if (cells.isEmpty()) return
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 600.dp) cells.size.coerceAtMost(4) else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.chunked(perRow).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { c, cell ->
                        val i = r * perRow + c
                        StatBox(cell, opened = open == i, onTap = cell.meaning?.let { { open = if (open == i) null else i } }, modifier = Modifier.weight(1f))
                    }
                    repeat(perRow - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            open?.let { cells.getOrNull(it) }?.meaning?.let { m ->
                Text("${cells[open!!].label}: $m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
private fun StatBox(cell: StatCell, opened: Boolean, onTap: (() -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row {
                Text(cell.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (onTap != null) Text(if (opened) "⌄" else "›", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
            Text(numberWithUnit(cell.value + cell.unit), style = MaterialTheme.typography.titleLarge.merge(NumberStyle).copy(fontWeight = FontWeight.Bold),
                color = if (cell.tone == Tone.Plain) scheme.onSurface else cell.tone.color(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val says = Modifier.semantics { contentDescription = "${cell.label} ${cell.value}${cell.unit}" + if (onTap != null) ", 눌러서 뜻 보기" else "" }
    if (onTap != null) Surface(onClick = onTap, shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (opened) scheme.primary else scheme.outlineVariant), modifier = modifier.then(says)) { content() }
    else Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerLow, border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.then(says)) { content() }
}
