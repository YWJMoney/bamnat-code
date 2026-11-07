package com.bamnat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.design.DayDialArcs
import com.bamnat.core.design.DialKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1.39 [FLOW-28] 하루 다이얼 (흐름·디자인 계획 9-3, 10-5): the day as a 24-hour ring, 0시 at the top like the logo's day-and-night
 * dial. Work is a thick arc in its shift's colour, a planned sleep a dashed arc and a real sleep a filled one inside it, an alarm a
 * dot on the rim and now a hand from the middle. A night shift is one arc over the top. [says] is what a screen reader hears.
 */
@Composable
fun DayDial(dial: DayDialArcs.Dial, says: String, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val cal = LocalCalendarColors.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline
    val hand = MaterialTheme.colorScheme.onSurface
    val alarm = MaterialTheme.colorScheme.primary
    val fallback = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size).semantics { contentDescription = "하루 다이얼. $says"; role = Role.Image }) {
        val d = this.size.minDimension
        val outer = d * 0.11f
        val inner = d * 0.07f
        val r = d / 2 - outer / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)
        fun angle(hour: Float) = hour / 24f * 360f - 90f
        fun box(radius: Float) = Pair(Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2))
        val (o, s) = box(r)
        drawCircle(track, radius = r, center = center, style = Stroke(width = outer))
        // 0·6·12·18시 ticks inside the ring.
        for (h in 0 until 24 step 6) {
            val a = Math.toRadians(angle(h.toFloat()).toDouble())
            val p1 = Offset(center.x + (r - outer) * cos(a).toFloat(), center.y + (r - outer) * sin(a).toFloat())
            val p2 = Offset(center.x + (r - outer * 1.6f) * cos(a).toFloat(), center.y + (r - outer * 1.6f) * sin(a).toFloat())
            drawLine(tick, p1, p2, strokeWidth = d * 0.02f, cap = StrokeCap.Round)
        }
        val sleepR = r - outer * 0.9f
        val (so, ss) = box(sleepR)
        dial.arcs.forEach { arc ->
            val sweep = arc.sweepHours / 24f * 360f
            when (arc.kind) {
                DialKind.Shift -> drawArc(arc.color?.asColor() ?: fallback, angle(arc.startHour), sweep, false, o, s,
                    style = Stroke(width = outer, cap = StrokeCap.Butt))
                DialKind.SleepPlan -> drawArc(cal.sleep.copy(alpha = 0.8f), angle(arc.startHour), sweep, false, so, ss,
                    style = Stroke(width = inner * 0.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(d * 0.04f, d * 0.035f))))
                DialKind.Sleep -> drawArc(cal.sleep, angle(arc.startHour), sweep, false, so, ss, style = Stroke(width = inner, cap = StrokeCap.Round))
            }
        }
        dial.alarms.forEach { h ->
            val a = Math.toRadians(angle(h).toDouble())
            drawCircle(alarm, radius = d * 0.045f, center = Offset(center.x + (r + outer * 0.1f) * cos(a).toFloat(), center.y + (r + outer * 0.1f) * sin(a).toFloat()))
        }
        val a = Math.toRadians(angle(dial.nowHour).toDouble())
        drawLine(hand, center, Offset(center.x + (r - outer) * cos(a).toFloat(), center.y + (r - outer) * sin(a).toFloat()),
            strokeWidth = d * 0.035f, cap = StrokeCap.Round)
        drawCircle(hand, radius = d * 0.045f, center = center)
    }
}
