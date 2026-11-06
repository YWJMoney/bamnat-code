package com.bamnat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color
import com.bamnat.core.design.ChartEmphasis

/** One bar: [label] under it and in the bubble, [text] the value as the user reads it ("6시간 30분"). */
data class ChartBar(val label: String, val value: Double, val tone: Tone = Tone.Plain, val text: String = value.toString())

/** The one line across the chart: an average, a target or a plan. */
data class Baseline(val value: Double, val label: String, val dashed: Boolean = true)

/**
 * 1.38 [DSG-04] 그래프 규칙, one chart for every statistic: bars in one colour, only the highest and the lowest in their
 * meaning colour ([ChartBar.tone]), one dashed [baseline], and a tap shows one bubble that moves to the next bar tapped.
 * The number on top (DSG-01 [HeroNumber]) is the screen's own, above this.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    baseline: Baseline?,
    onPick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    firstLabel: String? = bars.firstOrNull()?.label,
    lastLabel: String? = bars.lastOrNull()?.label,
) {
    var picked by rememberSaveable(bars.size) { mutableStateOf<Int?>(null) }
    val emphasis = ChartEmphasis.pick(bars.map { it.value })
    val primary = MaterialTheme.colorScheme.primary
    val pickedColor = MaterialTheme.colorScheme.onSurface
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val line = MaterialTheme.colorScheme.outline
    val tones = bars.mapIndexed { i, b ->
        if (b.tone != Tone.Plain && (i == emphasis.maxIndex || i == emphasis.minIndex)) b.tone.color() else primary
    }
    val max = maxOf(bars.maxOfOrNull { it.value } ?: 0.0, (baseline?.value ?: 0.0) * 1.1).coerceAtLeast(1.0)
    val described = bars.filter { it.value > 0 }.joinToString(", ") { "${it.label} ${it.text}" }.ifEmpty { "기록 없음" }
    Column(modifier.fillMaxWidth()) {
        // The bubble: one at a time, over the chart so the bars do not move.
        val bubble = picked?.let(bars::getOrNull)
        Text(bubble?.let { "${it.label} · ${it.text}" } ?: " ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Canvas(Modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = described + (baseline?.let { ", ${it.label}" } ?: "") }
            .pointerInput(bars) {
                detectTapGestures { p ->
                    if (bars.isEmpty()) return@detectTapGestures
                    val i = (p.x / (size.width.toFloat() / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                    picked = if (picked == i) null else i
                    onPick(i)
                }
            }) {
            if (bars.isEmpty()) return@Canvas
            val gap = (if (bars.size > 10) 4 else 8).dp.toPx()
            val w = (size.width - gap * (bars.size - 1)) / bars.size
            bars.forEachIndexed { i, b ->
                val h = if (b.value <= 0) 4.dp.toPx() else (size.height * b.value / max).toFloat()
                drawRoundRect(
                    color = if (b.value <= 0) empty else if (i == picked) pickedColor else tones[i],
                    topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            baseline?.let { bl ->
                val y = (size.height - size.height * bl.value / max).toFloat()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = if (bl.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(firstLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            baseline?.let {
                Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            }
            Text(lastLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
