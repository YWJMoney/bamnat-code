package com.bamnat.app.ui.decor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LAY-02] 1.36 요소 모음의 도형: each of core [com.bamnat.core.recipe.DecorGlyphs.SHAPE_LIST] drawn by the app from its outline in a
 * box of [w]×[h] px. [STROKES] are lines (drawn with the line's width, never filled).
 */
internal object DecorShapes {
    /** Shapes that are lines: drawn as a stroke. */
    val STROKES = setOf("ring", "frame", "line_h", "line_v", "line_d", "dash_h", "zigzag", "squiggle", "check", "underline", "circle_mark", "snow")

    /** [id]'s outline in a [w]×[h] box; [r] corners in px where the shape has corners. */
    fun path(id: String, w: Float, h: Float, r: Float = 0f): Path {
        val p = Path()
        fun pt(x: Float, y: Float) = Offset(x * w, y * h)
        fun poly(vararg xy: Float) {
            for (i in xy.indices step 2) { val o = pt(xy[i], xy[i + 1]); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            p.close()
        }
        fun line(vararg xy: Float) {
            for (i in xy.indices step 2) { val o = pt(xy[i], xy[i + 1]); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
        }
        fun regular(n: Int, start: Double = -PI / 2) {
            val pts = (0 until n).flatMap { i -> val a = start + 2 * PI * i / n; listOf((0.5 + 0.5 * cos(a)).toFloat(), (0.5 + 0.5 * sin(a)).toFloat()) }
            poly(*pts.toFloatArray())
        }
        fun star(n: Int, inner: Double) {
            val pts = (0 until n * 2).flatMap { i ->
                val a = -PI / 2 + PI * i / n
                val rr = if (i % 2 == 0) 0.5 else 0.5 * inner
                listOf((0.5 + rr * cos(a)).toFloat(), (0.5 + rr * sin(a)).toFloat())
            }
            poly(*pts.toFloatArray())
        }
        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = p.cubicTo(x1 * w, y1 * h, x2 * w, y2 * h, x3 * w, y3 * h)
        fun move(x: Float, y: Float) = p.moveTo(x * w, y * h)
        fun oval(l: Float, t: Float, rr: Float, b: Float) = p.addOval(Rect(l * w, t * h, rr * w, b * h))
        fun smooth(radii: List<Double>) {
            // A closed curve through points round the centre (Catmull-Rom as cubic Béziers), for the 말랑 shapes.
            val n = radii.size
            val pts = radii.mapIndexed { i, rr -> val a = -PI / 2 + 2 * PI * i / n; Offset((0.5 + 0.5 * rr * cos(a)).toFloat(), (0.5 + 0.5 * rr * sin(a)).toFloat()) }
            p.moveTo(pts[0].x * w, pts[0].y * h)
            for (i in 0 until n) {
                val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
                cubic(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
            p.close()
        }
        val box = Rect(0f, 0f, w, h)
        when (id) {
            "rect" -> if (r > 0f) p.addRoundRect(RoundRect(box, CornerRadius(r))) else p.addRect(box)
            "round" -> p.addRoundRect(RoundRect(box, CornerRadius(if (r > 0f) r else minOf(w, h) * 0.25f)))
            "frame" -> if (r > 0f) p.addRoundRect(RoundRect(box, CornerRadius(r))) else p.addRect(box)
            "circle", "ring" -> p.addOval(box)
            "pill" -> p.addRoundRect(RoundRect(box, CornerRadius(minOf(w, h) / 2)))
            "tri" -> poly(0.5f, 0f, 1f, 1f, 0f, 1f)
            "rtri" -> poly(0f, 0f, 1f, 1f, 0f, 1f)
            "diamond" -> poly(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
            "pent" -> regular(5)
            "hex" -> regular(6, 0.0)
            "oct" -> regular(8, PI / 8)
            "semi" -> { move(0f, 1f); p.arcTo(Rect(0f, 0f, w, 2 * h), 180f, 180f, false); p.close() }
            "quarter" -> { move(1f, 1f); p.lineTo(0f, h); p.arcTo(Rect(0f, 0f, 2 * w, 2 * h), 180f, 90f, false); p.close() }
            "arch" -> { move(0f, 1f); p.lineTo(0f, h * 0.5f); p.arcTo(Rect(0f, 0f, w, h), 180f, 180f, false); p.lineTo(w, h); p.close() }
            "para" -> poly(0.25f, 0f, 1f, 0f, 0.75f, 1f, 0f, 1f)
            "trap" -> poly(0.2f, 0f, 0.8f, 0f, 1f, 1f, 0f, 1f)
            "plus" -> poly(0.33f, 0f, 0.67f, 0f, 0.67f, 0.33f, 1f, 0.33f, 1f, 0.67f, 0.67f, 0.67f, 0.67f, 1f, 0.33f, 1f, 0.33f, 0.67f, 0f, 0.67f, 0f, 0.33f, 0.33f, 0.33f)
            "cross" -> poly(0.2f, 0f, 0.5f, 0.3f, 0.8f, 0f, 1f, 0.2f, 0.7f, 0.5f, 1f, 0.8f, 0.8f, 1f, 0.5f, 0.7f, 0.2f, 1f, 0f, 0.8f, 0.3f, 0.5f, 0f, 0.2f)
            "star4" -> star(4, 0.38)
            "star5" -> star(5, 0.42)
            "star6" -> star(6, 0.55)
            "star8" -> star(8, 0.6)
            "burst" -> star(12, 0.72)
            "seal" -> star(16, 0.86)
            "sparkle" -> { move(0.5f, 0f); p.quadraticTo(0.5f * w, 0.5f * h, w, 0.5f * h); p.quadraticTo(0.5f * w, 0.5f * h, 0.5f * w, h)
                p.quadraticTo(0.5f * w, 0.5f * h, 0f, 0.5f * h); p.quadraticTo(0.5f * w, 0.5f * h, 0.5f * w, 0f); p.close() }
            "heart" -> { move(0.5f, 0.28f); cubic(0.5f, 0.05f, 0.05f, 0f, 0.05f, 0.35f); cubic(0.05f, 0.6f, 0.35f, 0.8f, 0.5f, 1f)
                cubic(0.65f, 0.8f, 0.95f, 0.6f, 0.95f, 0.35f); cubic(0.95f, 0f, 0.5f, 0.05f, 0.5f, 0.28f); p.close() }
            "drop" -> { move(0.5f, 0f); cubic(0.35f, 0.25f, 0.1f, 0.45f, 0.1f, 0.65f); cubic(0.1f, 0.88f, 0.3f, 1f, 0.5f, 1f)
                cubic(0.7f, 1f, 0.9f, 0.88f, 0.9f, 0.65f); cubic(0.9f, 0.45f, 0.65f, 0.25f, 0.5f, 0f); p.close() }
            "moon" -> {
                val a = Path().apply { addOval(box) }
                val b = Path().apply { addOval(Rect(w * 0.3f, -h * 0.1f, w * 1.2f, h * 0.85f)) }
                p.op(a, b, PathOperation.Difference)
            }
            "sun" -> { oval(0.25f, 0.25f, 0.75f, 0.75f)
                for (i in 0 until 8) {
                    val a = 2 * PI * i / 8
                    fun at(rr: Double, d: Double) = Offset((0.5 + rr * cos(a + d)).toFloat(), (0.5 + rr * sin(a + d)).toFloat())
                    val tip = at(0.5, 0.0); val l = at(0.31, 0.18); val rt = at(0.31, -0.18)
                    poly(tip.x, tip.y, l.x, l.y, rt.x, rt.y)
                } }
            "cloud" -> { oval(0f, 0.4f, 0.42f, 0.9f); oval(0.2f, 0.12f, 0.68f, 0.72f); oval(0.5f, 0.32f, 1f, 0.88f); p.addRect(Rect(0.2f * w, 0.6f * h, 0.8f * w, 0.9f * h)) }
            "leaf" -> { move(0.08f, 0.92f); cubic(0.08f, 0.3f, 0.5f, 0.05f, 0.95f, 0.05f); cubic(0.95f, 0.5f, 0.7f, 0.92f, 0.08f, 0.92f); p.close() }
            "flower" -> { for (i in 0 until 6) { val a = 2 * PI * i / 6; val cx = (0.5 + 0.27 * cos(a)).toFloat(); val cy = (0.5 + 0.27 * sin(a)).toFloat()
                oval(cx - 0.22f, cy - 0.22f, cx + 0.22f, cy + 0.22f) }; oval(0.32f, 0.32f, 0.68f, 0.68f) }
            "wave" -> { move(0f, 0.5f); cubic(0.125f, 0.25f, 0.25f, 0.25f, 0.375f, 0.5f); cubic(0.5f, 0.75f, 0.625f, 0.75f, 0.75f, 0.5f)
                cubic(0.85f, 0.3f, 0.95f, 0.3f, 1f, 0.45f); p.lineTo(w, h); p.lineTo(0f, h); p.close() }
            "mountain" -> poly(0f, 1f, 0.35f, 0.25f, 0.55f, 0.6f, 0.72f, 0.4f, 1f, 1f)
            "snow" -> for (i in 0 until 3) { val a = PI * i / 3
                val x1 = (0.5 + 0.5 * cos(a)).toFloat(); val y1 = (0.5 + 0.5 * sin(a)).toFloat()
                line(x1, y1, 1f - x1, 1f - y1) }
            "blob1" -> smooth(listOf(1.0, 0.82, 0.95, 0.78, 1.0, 0.86, 0.92, 0.8))
            "blob2" -> smooth(listOf(0.9, 1.0, 0.7, 0.95, 0.8, 1.0, 0.75))
            "blob3" -> smooth(listOf(1.0, 0.65, 1.0, 0.7, 0.95, 0.68))
            "bubble" -> { p.addRoundRect(RoundRect(Rect(0f, 0f, w, h * 0.78f), CornerRadius(if (r > 0f) r else minOf(w, h) * 0.2f)))
                poly(0.2f, 0.7f, 0.38f, 0.7f, 0.14f, 1f) }
            "bubble_r" -> { p.addRoundRect(RoundRect(Rect(0f, 0f, w, h * 0.78f), CornerRadius(if (r > 0f) r else minOf(w, h) * 0.2f)))
                poly(0.62f, 0.7f, 0.8f, 0.7f, 0.86f, 1f) }
            "think" -> { oval(0f, 0f, 1f, 0.74f); oval(0.14f, 0.76f, 0.28f, 0.88f); oval(0.04f, 0.9f, 0.12f, 1f) }
            "tag" -> poly(0f, 0f, 0.72f, 0f, 1f, 0.5f, 0.72f, 1f, 0f, 1f)
            "ribbon" -> poly(0f, 0.2f, 1f, 0.2f, 0.88f, 0.5f, 1f, 0.8f, 0f, 0.8f, 0.12f, 0.5f)
            "bookmark" -> poly(0f, 0f, 1f, 0f, 1f, 1f, 0.5f, 0.72f, 0f, 1f)
            "shield" -> { move(0.5f, 0f); p.lineTo(w, 0.15f * h); cubic(1f, 0.6f, 0.8f, 0.86f, 0.5f, 1f); cubic(0.2f, 0.86f, 0f, 0.6f, 0f, 0.15f); p.close() }
            "badge" -> { oval(0.15f, 0f, 0.85f, 0.7f); poly(0.3f, 0.55f, 0.48f, 0.62f, 0.38f, 1f, 0.3f, 0.88f, 0.22f, 0.95f); poly(0.7f, 0.55f, 0.52f, 0.62f, 0.62f, 1f, 0.7f, 0.88f, 0.78f, 0.95f) }
            "pin" -> { move(0.5f, 1f); cubic(0.2f, 0.65f, 0.1f, 0.5f, 0.1f, 0.38f); cubic(0.1f, 0.15f, 0.28f, 0f, 0.5f, 0f)
                cubic(0.72f, 0f, 0.9f, 0.15f, 0.9f, 0.38f); cubic(0.9f, 0.5f, 0.8f, 0.65f, 0.5f, 1f); p.close() }
            "check" -> line(0.08f, 0.55f, 0.38f, 0.85f, 0.92f, 0.15f)
            "tape" -> poly(0f, 0f, 0.05f, 0.25f, 0f, 0.5f, 0.05f, 0.75f, 0f, 1f, 1f, 1f, 0.95f, 0.75f, 1f, 0.5f, 0.95f, 0.25f, 1f, 0f)
            "corner" -> poly(0f, 0f, 1f, 0f, 1f, 0.2f, 0.2f, 0.2f, 0.2f, 1f, 0f, 1f)
            "line_h", "dash_h" -> line(0f, 0.5f, 1f, 0.5f)
            "line_v" -> line(0.5f, 0f, 0.5f, 1f)
            "line_d" -> line(0f, 1f, 1f, 0f)
            "zigzag" -> line(0f, 0.7f, 0.125f, 0.3f, 0.25f, 0.7f, 0.375f, 0.3f, 0.5f, 0.7f, 0.625f, 0.3f, 0.75f, 0.7f, 0.875f, 0.3f, 1f, 0.7f)
            "squiggle" -> { move(0f, 0.5f); cubic(0.125f, 0.1f, 0.25f, 0.1f, 0.375f, 0.5f); cubic(0.5f, 0.9f, 0.625f, 0.9f, 0.75f, 0.5f); cubic(0.85f, 0.15f, 0.95f, 0.2f, 1f, 0.4f) }
            "underline" -> { move(0f, 0.65f); cubic(0.3f, 0.35f, 0.7f, 0.75f, 1f, 0.4f) }
            "circle_mark" -> { p.arcTo(Rect(0f, h * 0.05f, w * 0.98f, h), 200f, 330f, true); p.lineTo(w * 0.32f, h * 0.02f) }
            "arrow_r" -> poly(0f, 0.35f, 0.6f, 0.35f, 0.6f, 0.08f, 1f, 0.5f, 0.6f, 0.92f, 0.6f, 0.65f, 0f, 0.65f)
            "arrow_l" -> poly(1f, 0.35f, 0.4f, 0.35f, 0.4f, 0.08f, 0f, 0.5f, 0.4f, 0.92f, 0.4f, 0.65f, 1f, 0.65f)
            "arrow_u" -> poly(0.35f, 1f, 0.35f, 0.4f, 0.08f, 0.4f, 0.5f, 0f, 0.92f, 0.4f, 0.65f, 0.4f, 0.65f, 1f)
            "arrow_d" -> poly(0.35f, 0f, 0.35f, 0.6f, 0.08f, 0.6f, 0.5f, 1f, 0.92f, 0.6f, 0.65f, 0.6f, 0.65f, 0f)
            "chevron" -> poly(0f, 0f, 0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 1f, 0.5f, 0.5f)
            else -> p.addRect(box)
        }
        return p
    }
}
