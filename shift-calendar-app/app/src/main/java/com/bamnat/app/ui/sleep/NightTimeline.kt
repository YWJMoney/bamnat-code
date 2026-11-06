package com.bamnat.app.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.core.sleep.SoundKind
import java.time.Duration
import java.time.LocalDateTime

/** One piece on the line: work, sleep, or a sound at a moment. */
data class TimelineSpan(val from: LocalDateTime, val to: LocalDateTime, val work: Boolean, val color: Color? = null)
data class TimelineDot(val at: LocalDateTime, val kind: SoundKind, val id: Long)

/**
 * 1.38 [SLEEP-21] 시간 눈금 잠 그래프: one hour a tick, the work and the sleep on the same axis, the night's sounds as dots.
 * A tap says the time there; a tap on a dot picks that sound ([onDot], to play it). The sounds are listed with 듣기 below too.
 */
@Composable
internal fun NightTimeline(from: LocalDateTime, to: LocalDateTime, spans: List<TimelineSpan>, dots: List<TimelineDot>, onDot: (Long) -> Unit) {
    val total = Duration.between(from, to).toMinutes().coerceAtLeast(60).toFloat()
    var told by remember(from) { mutableStateOf<String?>(null) }
    val sleepColor = MaterialTheme.colorScheme.primary
    val workColor = MaterialTheme.colorScheme.tertiary
    val tick = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.onSurface
    fun x(t: LocalDateTime, w: Float) = Duration.between(from, t).toMinutes() / total * w
    val described = spans.joinToString(", ") { "${if (it.work) "근무" else "잠"} ${Fmt.time(it.from.toLocalTime())}–${Fmt.time(it.to.toLocalTime())}" } +
        if (dots.isNotEmpty()) ", 소리 ${dots.size}번" else ""
    Column(Modifier.fillMaxWidth()) {
        Text(told ?: "${Fmt.time(from.toLocalTime())}부터 ${Fmt.time(to.toLocalTime())}까지 · 누르면 그 시각", style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp)
            .semantics { contentDescription = described }
            .pointerInput(from, to, dots) {
                detectTapGestures { p ->
                    val w = size.width.toFloat()
                    val near = dots.minByOrNull { kotlin.math.abs(x(it.at, w) - p.x) }?.takeIf { kotlin.math.abs(x(it.at, w) - p.x) < 24.dp.toPx() }
                    val at = from.plusMinutes((p.x / w * total).toLong())
                    told = if (near != null) "${Fmt.time(near.at.toLocalTime())} ${near.kind.label}" else Fmt.time(at.toLocalTime())
                    near?.let { onDot(it.id) }
                }
            }) {
            val w = size.width
            // Hour ticks.
            var h = from.withMinute(0).plusHours(1)
            while (h.isBefore(to)) {
                val tx = x(h, w)
                drawLine(tick, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = if (h.hour % 6 == 0) 2f else 1f)
                h = h.plusHours(1)
            }
            spans.forEach { s ->
                val a = x(s.from.coerceAtLeast(from), w); val b = x(s.to.coerceAtMost(to), w)
                if (b > a) drawRoundRect(s.color ?: if (s.work) workColor else sleepColor,
                    topLeft = Offset(a, if (s.work) 4.dp.toPx() else size.height / 2), size = Size(b - a, size.height / 2 - 6.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx()))
            }
            dots.forEach { d -> drawCircle(dot, radius = 3.dp.toPx(), center = Offset(x(d.at, w), size.height - 3.dp.toPx())) }
        }
    }
}

private fun LocalDateTime.coerceAtLeast(o: LocalDateTime) = if (isBefore(o)) o else this
private fun LocalDateTime.coerceAtMost(o: LocalDateTime) = if (isAfter(o)) o else this
