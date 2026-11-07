package com.bamnat.app.ui.work

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.core.boss.LedgerLine

/**
 * 1.39 [FLOW-24] 사장님 급여 판 (흐름 디자인 3-11 '한 숫자 머리, 강조 둘'): one bar a person for what they cost this month (급여 +
 * 사장님 4대보험·산재), all the same color, longest first; last month's as a dotted mark on the same bar, and only a person past 주 52시간
 * in red. Names and amounts as the ledger has them; nothing more about the person.
 */
@Composable
fun StaffCostBars(lines: List<LedgerLine>, last: Map<String, Long>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val rows = lines.sortedByDescending { it.employerTotal }
    val top = (rows.maxOf { it.employerTotal }.coerceAtLeast(1L)).coerceAtLeast(last.values.maxOrNull() ?: 0L).toFloat()
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val danger = MaterialTheme.colorScheme.error
    val mark = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { l ->
            val over = l.warnings.any { it.key == "week52" }
            val before = last[l.staff.id]
            val words = "${l.staff.name} ${Fmt.won(l.employerTotal)}" + (before?.let { " · 지난달 ${Fmt.won(it)}" } ?: "") + if (over) " · 주 52시간 넘음" else ""
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = words }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(l.staff.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(56.dp),
                    color = if (over) danger else MaterialTheme.colorScheme.onSurface)
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(if (over) danger else bar, size = Size(size.width * (l.employerTotal / top), size.height), cornerRadius = r)
                    before?.takeIf { it > 0 }?.let { b ->
                        val x = (size.width * (b / top)).coerceIn(1f, size.width - 1f)
                        drawLine(mark, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
                    }
                }
                Text(Fmt.won(l.employerTotal), style = MaterialTheme.typography.labelMedium, color = if (over) danger else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("사장님이 내는 몫까지 · 점선은 지난달" + if (rows.any { r -> r.warnings.any { it.key == "week52" } }) " · 빨강은 주 52시간을 넘은 사람" else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
