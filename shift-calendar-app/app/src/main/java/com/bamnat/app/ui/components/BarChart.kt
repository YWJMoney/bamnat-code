package com.bamnat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.ceil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
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
data class ChartBar(val label: String, val value: Double, val tone: Tone = Tone.Plain, val text: String = value.toString(),
    /** 1.39 [DSG-04] A bar whose colour is its meaning on its own (손익의 빨강·파랑), not one of the two that stand out. */
    val color: androidx.compose.ui.graphics.Color? = null)

/** The one line across the chart: an average, a target or a plan. */
data class Baseline(val value: Double, val label: String, val dashed: Boolean = true)

/**
 * 1.38 [DSG-04] 그래프 규칙, one chart for every statistic: bars in one colour, only the highest and the lowest in their
 * meaning colour ([ChartBar.tone]), one dashed [baseline], and a tap shows one bubble that moves to the next bar tapped.
 * The number on top (DSG-01 [HeroNumber]) is the screen's own, above this.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BarChart(
    bars: List<ChartBar>,
    baseline: Baseline?,
    onPick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    firstLabel: String? = bars.firstOrNull()?.label,
    lastLabel: String? = bars.lastOrNull()?.label,
    /** 1.39 [DSG-04][FLOW-10] Each bar's own label under it (a few bars: 근무별 평균, 6개월), else the first and last only. */
    labels: Boolean = bars.size <= 7,
    /** 1.39 [FLOW-10] Each bar's [ChartBar.text] under its label, for a few bars read at a glance. */
    values: Boolean = false,
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
    // 1.39 [DSG-04] Values below zero (손익) hang from a zero line in the middle; otherwise the bars stand on the bottom.
    val signed = bars.any { it.value < 0 }
    val max = maxOf(bars.maxOfOrNull { kotlin.math.abs(it.value) } ?: 0.0, (baseline?.value ?: 0.0) * 1.1).coerceAtLeast(1.0)
    val described = bars.filter { it.value != 0.0 }.joinToString(", ") { "${it.label} ${it.text}" }.ifEmpty { "기록 없음" }
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
            val zero = if (signed) size.height / 2 else size.height
            val room = if (signed) size.height / 2 else size.height
            if (signed) drawLine(line, Offset(0f, zero), Offset(size.width, zero), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, b ->
                val none = if (signed) b.value == 0.0 else b.value <= 0
                val h = if (none) 4.dp.toPx() else (room * kotlin.math.abs(b.value) / max).toFloat()
                val top = if (b.value < 0) zero else zero - h
                drawRoundRect(
                    color = if (none) empty else if (i == picked) pickedColor else b.color ?: tones[i],
                    topLeft = Offset(i * (w + gap), if (none && signed) zero - h / 2 else top), size = Size(w, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            baseline?.let { bl ->
                val y = (zero - room * bl.value / max).toFloat()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = if (bl.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
            }
        }
        if (labels && bars.isNotEmpty()) {
            val spacing = if (bars.size > 10) 4.dp else 8.dp
            val labelStyle = MaterialTheme.typography.labelSmall
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                // 2.0.0 [RVK-34] Twelve months on the folded phone: '10월' had 22dp under its bar and stood on two lines. When the
                // widest label does not fit its bar on one line, it is said under every 2nd (3rd …) bar, on one line, centred on that
                // bar; the bubble still says every bar. A few bars keep their two-line labels.
                val gap = with(LocalDensity.current) { spacing.toPx() }
                val cell = (constraints.maxWidth - gap * (bars.size - 1)) / bars.size
                val widest = bars.maxOf { measurer.measure(it.label, labelStyle, maxLines = 1).size.width }
                val step = if (bars.size <= 7 || widest <= cell) 1 else ceil((widest + gap) / (cell + gap)).toInt().coerceAtLeast(1)
                // 2.0.0 [RVK-36] (총괄 HQ-63 ①) A value with a word wider than its bar ('+18.3만' at 200%) broke in the middle of the
                // number: then the values go under the chart instead, '5월 +18.3만' each whole, as many to a line as fit. '7시간 30분' still
                // takes two lines under its bar, broken at the space.
                val valueStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                val valuesUnder = values && bars.any { b -> b.text.split(' ').any { w -> measurer.measure(w, valueStyle, maxLines = 1).size.width > cell } }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    bars.forEachIndexed { i, b ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (step == 1) {
                                Text(b.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center, maxLines = 2)
                                if (values && !valuesUnder) Text(b.text, style = valueStyle, textAlign = TextAlign.Center, maxLines = 2,
                                    color = if (b.tone != Tone.Plain) b.tone.color() else Color.Unspecified)
                            } else if (i % step == 0) {
                                Text(b.label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
                                if (values && !valuesUnder) Text(b.text, style = valueStyle, maxLines = 1,
                                    softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true),
                                    color = if (b.tone != Tone.Plain) b.tone.color() else Color.Unspecified)
                            }
                        }
                    }
                }
                if (valuesUnder) androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    bars.forEach { b ->
                        Row {
                            Text("${b.label} ", style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                            Text(b.text, style = valueStyle, maxLines = 1, softWrap = false,
                                color = if (b.tone != Tone.Plain) b.tone.color() else Color.Unspecified)
                        }
                    }
                }
                }
            }
            baseline?.let {
                Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            return@Column
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
