package com.bamnat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 1.38 [FLOW-26] 칸이's seven faces (흐름·디자인 계획 10-3). [Point] is the one that shows the way on an empty screen. */
enum class KaniMood(val label: String) {
    Basic("기본"), Done("해냈어요"), Stretch("기지개"), Sleepy("졸려요"), Off("쉬는 날"), Point("여기예요"), Worry("걱정돼요");

    /** The headband's own day when no shift colour is given: sun, moon or leaf. */
    internal val day: KaniDay get() = when (this) { Sleepy -> KaniDay.NIGHT; Off -> KaniDay.OFF; else -> KaniDay.DAY }
}

internal enum class KaniDay(val band: Color) { DAY(Color(0xFFE9B95A)), NIGHT(Color(0xFF34418F)), OFF(Color(0xFF43B58A)) }

private val Ink = Color(0xFF15192B)
private val InkNight = Color(0xFFC9D1F5)
private val Cream = Color(0xFFF7F1E6)
private val Cheek = Color(0xFFFF9EA0)

/**
 * 1.38 [FLOW-26] 칸이: a day of the calendar with a face. Its headband is the day's shift colour ([band]; the mood's own
 * sun, moon or leaf colour when none is given), with the two calendar rings on top. [night] lightens the outline for a
 * dark page. Drawn from the same shapes as `docs/brand/kani/kani-<mood>.svg` (viewBox 130), so it stays sharp at any [size]
 * and needs no image files. A screen reader hears "칸이, 해냈어요". Callers check [com.bamnat.app.data.AppSettings.showKani].
 */
@Composable
fun Kani(
    mood: KaniMood,
    night: Boolean = false,
    band: Color = Color.Unspecified,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    val shapes = remember(mood) { KaniShapes.of(mood) }
    val outline = if (night) InkNight else Ink
    val headband = if (band == Color.Unspecified) mood.day.band else band
    Canvas(modifier.size(size).semantics { contentDescription = "칸이, ${mood.label}"; role = Role.Image }) {
        val k = this.size.minDimension / 130f
        scale(k, k, pivot = Offset.Zero) { drawKani(shapes, mood, outline, headband) }
    }
}

private fun DrawScope.drawKani(s: KaniShapes, mood: KaniMood, outline: Color, band: Color) {
    val round = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    // Arms, then feet, behind the body.
    s.arms.forEach { drawPath(it, outline, style = round(4f)) }
    drawOval(outline, Offset(38f, 111f), Size(22f, 10f))
    drawOval(outline, Offset(70f, 111f), Size(22f, 10f))
    val body = RoundRect(Rect(21f, 24f, 109f, 112f), CornerRadius(24f))
    val bodyPath = Path().apply { addRoundRect(body) }
    drawPath(bodyPath, Cream)
    clipPath(bodyPath) { drawRect(band, Offset(21f, 24f), Size(88f, 27f)) }
    when (mood.day) {
        KaniDay.DAY -> drawCircle(Color(0xFFFFF3C4), 5.5f, Offset(88f, 37f))
        KaniDay.NIGHT -> drawPath(KaniShapes.moon, Color(0xFFFFE9A8))
        KaniDay.OFF -> {
            drawPath(KaniShapes.leaf, Color(0xFFE6FFF2))
            drawPath(KaniShapes.leafVein, KaniDay.OFF.band, style = round(1.6f))
        }
    }
    drawPath(bodyPath, outline, style = Stroke(3.5f))
    // The calendar's two rings.
    drawRoundRect(outline, Offset(44f, 14f), Size(9f, 20f), CornerRadius(4.5f))
    drawRoundRect(outline, Offset(77f, 14f), Size(9f, 20f), CornerRadius(4.5f))
    drawOval(Cheek.copy(alpha = 0.7f), Offset(31.5f, 84.2f), Size(13f, 7.6f))
    drawOval(Cheek.copy(alpha = 0.7f), Offset(85.5f, 84.2f), Size(13f, 7.6f))
    s.dots.forEach { (c, r) -> drawCircle(Ink, r, c) }
    s.fills.forEach { drawPath(it, Ink) }
    s.lines.forEach { (p, w) -> drawPath(p, Ink, style = round(w)) }
    s.extra(this)
}

/** The face and arms of one mood, parsed once from the brand SVG's own path data. */
private class KaniShapes(
    val arms: List<Path>,
    val dots: List<Pair<Offset, Float>> = emptyList(),
    val fills: List<Path> = emptyList(),
    val lines: List<Pair<Path, Float>> = emptyList(),
    val extra: DrawScope.() -> Unit = {},
) {
    companion object {
        private fun p(d: String): Path = PathParser().parsePathString(d).toPath()
        private val armsDown = listOf(p("M20 80q-9 6-7 15"), p("M110 80q9 6 7 15"))
        private val armsUp = listOf(p("M20 70q-12-10-9-24"), p("M110 70q12-10 9-24"))
        private val armsOne = listOf(p("M20 80q-9 6-7 15"), p("M110 70q12-10 9-24"))
        private val armsPoint = listOf(p("M20 80q-9 6-7 15"), p("M110 82q10 4 14 14"))
        val moon = p("M90 31a7 7 0 1 0 0 12a5.5 5.5 0 1 1 0-12z")
        val leaf = p("M84 41c0-6 5-10 10-10c0 6-4 10-10 10z")
        val leafVein = p("M84 41l6-6")
        private val gold = Color(0xFFE9B95A)

        fun of(mood: KaniMood): KaniShapes = when (mood) {
            KaniMood.Basic -> KaniShapes(armsDown, dots = listOf(Offset(51f, 76f) to 5f, Offset(79f, 76f) to 5f),
                lines = listOf(p("M57 86q8 7 16 0") to 3.5f))
            KaniMood.Sleepy -> KaniShapes(armsDown, fills = listOf(p("M61.5 90a3.5 4 0 1 0 7 0a3.5 4 0 1 0 -7 0z")),
                lines = listOf(p("M45 77q6 5 12 0") to 3.5f, p("M73 77q6 5 12 0") to 3.5f)) {
                // "z z" drawn as strokes, so no font is needed.
                val zBlue = Color(0xFF7C8BD6)
                drawPath(p("M104 11h9l-9 11h9"), zBlue, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p("M115 3h6l-6 7h6"), zBlue, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            KaniMood.Stretch -> KaniShapes(armsUp, fills = listOf(p("M57 85q8 10 16 0z")),
                lines = listOf(p("M45 78q6-6 12 0") to 3.5f, p("M73 78q6-6 12 0") to 3.5f)) {
                listOf("M8 30l-6-4", "M6 42h-6", "M122 30l6-4", "M124 42h6").forEach {
                    drawPath(p(it), gold, style = Stroke(3f, cap = StrokeCap.Round))
                }
            }
            KaniMood.Done -> KaniShapes(armsOne, dots = listOf(Offset(51f, 76f) to 5f),
                lines = listOf(p("M73 77q6-5 12 0") to 3.5f, p("M56 85q9 9 18 0") to 3.5f)) {
                drawPath(p("M12 22l2.5 6l6 2.5l-6 2.5l-2.5 6l-2.5-6l-6-2.5l6-2.5z"), gold)
                drawPath(p("M112 8l1.8 4.2l4.2 1.8l-4.2 1.8l-1.8 4.2l-1.8-4.2l-4.2-1.8l4.2-1.8z"), gold)
            }
            KaniMood.Worry -> KaniShapes(armsDown, dots = listOf(Offset(51f, 78f) to 4.5f, Offset(79f, 78f) to 4.5f),
                lines = listOf(p("M44 70l12-4") to 3f, p("M86 70l-12-4") to 3f, p("M57 90q4-4 8 0t8 0") to 3.5f)) {
                val drop = p("M100 56q4 7 0 10q-4-3 0-10z")
                drawPath(drop, Color(0xFF8EC5FF))
                drawPath(drop, Color(0xFF2A2F45), style = Stroke(1.5f))
            }
            KaniMood.Point -> KaniShapes(armsPoint, dots = listOf(Offset(54f, 77f) to 5f, Offset(82f, 77f) to 5f),
                lines = listOf(p("M60 87q7 5 14 0") to 3.5f))
            KaniMood.Off -> KaniShapes(armsDown, lines = listOf(p("M45 76h12") to 3.5f, p("M73 76h12") to 3.5f,
                p("M57 86q8 7 16 0") to 3.5f))
        }
    }
}

/** 1.38 [FLOW-26] 설정 › 화면 › 칸이 보이기, handed down from MainActivity; false hides 칸이 everywhere. */
val LocalShowKani = androidx.compose.runtime.staticCompositionLocalOf { true }
