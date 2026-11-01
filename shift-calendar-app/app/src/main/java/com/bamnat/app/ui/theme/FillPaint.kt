package com.bamnat.app.ui.theme

import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import com.bamnat.core.recipe.Fill
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * [MAT-01] 1.36 채우기를 그려요: every [Fill] the core knows, on any part, inside [shape]. Gradients are Compose brushes; 무늬 and 질감
 * are small tiles the app draws itself (no picture in the app or the recipe) repeated; 유리 is a frosted wash; 셰이더 is one the app
 * ships (AGSL, Android 13+) and under 13, or when [animate] is false or 움직임 is off, the same colours as a still gradient.
 */
fun Modifier.fillWith(fill: Fill?, shape: Shape = RectangleShape, alpha: Float = 1f, animate: Boolean = true): Modifier =
    if (fill == null) this else composed {
        val density = LocalDensity.current.density
        val tile = remember(fill, density) { FillTiles.tile(fill, density) }
        val moving = animate && fill is Fill.Shader && fill.speed > 0 && Build.VERSION.SDK_INT >= 33 && !LocalMotionOff.current
        val time: State<Float> = if (moving) {
            rememberInfiniteTransition(label = "fill").animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "t")
        } else remember { mutableFloatStateOf(0f) }
        val shader = remember(fill) { if (fill is Fill.Shader && Build.VERSION.SDK_INT >= 33) FillShaders.make(fill) else null }
        drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            clipPath(path) { paintFill(fill, tile, shader, if (fill is Fill.Shader) time.value * fill.speed / 3f else 0f, alpha) }
        }
    }

/** Whether 움직임 끄기 or the phone's 'remove animations' is on (MainActivity provides it). */
val LocalMotionOff = androidx.compose.runtime.staticCompositionLocalOf { false }

/** [MAT-07] 움직임 as the user set it (already [com.bamnat.core.recipe.Motion.effective]). */
val LocalMotion = androidx.compose.runtime.staticCompositionLocalOf { com.bamnat.core.recipe.Motion() }

/** Draws [fill] over the whole of this scope (the caller clips). */
internal fun DrawScope.paintFill(fill: Fill, tile: ImageBitmap?, shader: Any?, seconds: Float, alpha: Float) {
    when (fill) {
        is Fill.Solid -> drawRect(color(fill.color), alpha = alpha)
        is Fill.Linear -> drawRect(linearBrush(fill, size), alpha = alpha)
        is Fill.Radial -> {
            val c = Offset(size.width * fill.cx / 100f, size.height * fill.cy / 100f)
            val r = (minOf(size.width, size.height) * fill.radius / 100f).coerceAtLeast(1f)
            drawRect(Brush.radialGradient(colorStops = stops(fill), center = c, radius = r), alpha = alpha)
        }
        is Fill.Sweep -> {
            val c = Offset(size.width * fill.cx / 100f, size.height * fill.cy / 100f)
            val big = hypot(size.width, size.height)
            rotate(fill.turn.toFloat() - 90f, pivot = c) {
                drawRect(Brush.sweepGradient(colorStops = stops(fill), center = c), topLeft = Offset(c.x - big, c.y - big), size = Size(big * 2, big * 2), alpha = alpha)
            }
        }
        is Fill.Pattern -> {
            drawRect(color(fill.ground), alpha = alpha)
            if (tile != null) {
                val big = hypot(size.width, size.height)
                val c = center
                rotate(fill.angle.toFloat(), pivot = c) {
                    drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), topLeft = Offset(c.x - big / 2, c.y - big / 2),
                        size = Size(big, big), alpha = alpha)
                }
            }
        }
        is Fill.Texture -> {
            drawRect(color(fill.tint), alpha = alpha)
            if (tile != null) drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = alpha)
        }
        is Fill.Glass -> {
            drawRect(color(fill.tint), alpha = alpha)
            // 유리: a highlight from the top and a fine frost, so it reads as glass over whatever is under it.
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.45f to Color.White.copy(alpha = 0.06f), 1f to Color.Transparent), alpha = alpha)
            if (tile != null) drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = alpha * (fill.blur / 40f).coerceIn(0.2f, 1f))
        }
        is Fill.Shader -> {
            // A software canvas (a screenshot, some captures) cannot draw a RuntimeShader: it gets the still gradient.
            if (shader != null && Build.VERSION.SDK_INT >= 33 && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                FillShaders.update(shader, fill, size.width, size.height, seconds)
                drawRect(ShaderBrush(shader as android.graphics.Shader), alpha = alpha)
            } else drawRect(linearBrush(fill.still(), size), alpha = alpha)
        }
    }
}

private fun color(c: Long) = Color(c.toInt())

private fun stops(g: Fill.Gradient): Array<Pair<Float, Color>> = g.places().zip(g.stops).map { (p, s) -> p to color(s.color) }.toTypedArray()

/**
 * A linear gradient at [Fill.Linear.angle]: 0 위→아래, 45 ↘, 90 왼→오른, 135 ↗ …, its ends at the box's far corners so the colours fill it.
 */
internal fun linearBrush(fill: Fill.Linear, size: Size): Brush {
    val a = fill.angle * PI / 180.0
    val dx = sin(a).toFloat()
    val dy = cos(a).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val c = Offset(size.width / 2, size.height / 2)
    return Brush.linearGradient(colorStops = stops(fill), start = Offset(c.x - dx * half, c.y - dy * half), end = Offset(c.x + dx * half, c.y + dy * half))
}

/** [MAT-02] A brush for a line in [fill]: gradients as they are, anything else (무늬·질감·유리·셰이더) in its average colour. */
internal fun lineBrushOf(fill: Fill, size: Size): Brush = when (fill) {
    is Fill.Linear -> linearBrush(fill, size)
    is Fill.Radial -> Brush.radialGradient(colorStops = stops(fill), center = Offset(size.width * fill.cx / 100f, size.height * fill.cy / 100f),
        radius = (minOf(size.width, size.height) * fill.radius / 100f).coerceAtLeast(1f))
    is Fill.Sweep -> Brush.sweepGradient(colorStops = stops(fill), center = Offset(size.width * fill.cx / 100f, size.height * fill.cy / 100f))
    else -> androidx.compose.ui.graphics.SolidColor(color(fill.average))
}

/** A [Shape] for a fill preview swatch with no outline of its own. */
internal object SwatchShape : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline =
        Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
}

/** [MAT-01] 무늬·질감·유리 tiles, made once per fill and screen density and kept. */
internal object FillTiles {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 48
    }

    fun tile(fill: Fill, density: Float): ImageBitmap? {
        val key = fill.encode() + "@" + density
        synchronized(cache) { cache[key]?.let { return it } }
        val made = when (fill) {
            is Fill.Pattern -> pattern(fill, density)
            is Fill.Texture -> texture(fill.kind, fill.strength, 160)
            is Fill.Glass -> texture("noise", 18, 96)
            else -> null
        } ?: return null
        synchronized(cache) { cache[key] = made }
        return made
    }

    /** One tile of a pattern in its ink on a clear ground (the ground is drawn under it). */
    fun pattern(p: Fill.Pattern, density: Float): ImageBitmap {
        val side = (22f * density * p.size / 100f).toInt().coerceIn(6, 512)
        val bmp = createBitmap(side, side)
        val cv = android.graphics.Canvas(bmp)
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = p.ink.toInt() }
        val line = android.graphics.Paint(ink).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = side * 0.08f; strokeCap = android.graphics.Paint.Cap.ROUND }
        val s = side.toFloat()
        when (p.kind) {
            "dots" -> cv.drawCircle(s / 2, s / 2, s * 0.12f, ink)
            "bigdots" -> cv.drawCircle(s / 2, s / 2, s * 0.28f, ink)
            "stripes" -> cv.drawRect(0f, 0f, s, s * 0.4f, ink)
            "thin" -> cv.drawRect(0f, 0f, s, s * 0.12f, ink)
            "check" -> { cv.drawRect(0f, 0f, s / 2, s / 2, ink); cv.drawRect(s / 2, s / 2, s, s, ink) }
            "grid" -> { line.strokeWidth = s * 0.06f; cv.drawLine(0f, 0f, s, 0f, line); cv.drawLine(0f, 0f, 0f, s, line) }
            "wave" -> {
                val path = android.graphics.Path().apply {
                    moveTo(0f, s / 2)
                    for (i in 1..16) { val x = s * i / 16f; lineTo(x, s / 2 + sin(2 * PI * i / 16f).toFloat() * s * 0.18f) }
                }
                cv.drawPath(path, line)
            }
            "zigzag" -> cv.drawPath(android.graphics.Path().apply { moveTo(0f, s * 0.65f); lineTo(s / 2, s * 0.35f); lineTo(s, s * 0.65f) }, line)
            "heart" -> cv.drawPath(heartPath(s / 2, s / 2, s * 0.32f), ink)
            "star" -> cv.drawPath(starPath(s / 2, s / 2, s * 0.32f, s * 0.14f, 5), ink)
            "cross" -> { cv.drawRect(s * 0.42f, s * 0.22f, s * 0.58f, s * 0.78f, ink); cv.drawRect(s * 0.22f, s * 0.42f, s * 0.78f, s * 0.58f, ink) }
            "diamond" -> cv.drawPath(android.graphics.Path().apply { moveTo(s / 2, s * 0.15f); lineTo(s * 0.85f, s / 2); lineTo(s / 2, s * 0.85f); lineTo(s * 0.15f, s / 2); close() }, ink)
            "brick" -> {
                line.strokeWidth = s * 0.05f
                cv.drawLine(0f, 0f, s, 0f, line); cv.drawLine(0f, s / 2, s, s / 2, line)
                cv.drawLine(0f, 0f, 0f, s / 2, line); cv.drawLine(s / 2, s / 2, s / 2, s, line)
            }
            "scale" -> {
                line.strokeWidth = s * 0.05f
                cv.drawCircle(0f, s, s / 2, line); cv.drawCircle(s, s, s / 2, line); cv.drawCircle(s / 2, s / 2, s / 2, line)
            }
            "plaid" -> {
                val soft = android.graphics.Paint(ink).apply { alpha = (android.graphics.Color.alpha(p.ink.toInt()) * 0.55f).toInt() }
                cv.drawRect(0f, s * 0.3f, s, s * 0.55f, soft); cv.drawRect(s * 0.3f, 0f, s * 0.55f, s, soft)
                cv.drawRect(0f, s * 0.8f, s, s * 0.86f, ink); cv.drawRect(s * 0.8f, 0f, s * 0.86f, s, ink)
            }
            "confetti" -> {
                val r = Random(7)
                repeat(6) {
                    val x = r.nextFloat() * s; val y = r.nextFloat() * s
                    if (it % 2 == 0) cv.drawCircle(x, y, s * 0.05f, ink)
                    else cv.withRotation(r.nextFloat() * 180f, x, y) { drawRect(x - s * 0.07f, y - s * 0.025f, x + s * 0.07f, y + s * 0.025f, ink) }
                }
            }
        }
        return bmp.asImageBitmap()
    }

    private fun heartPath(cx: Float, cy: Float, r: Float) = android.graphics.Path().apply {
        moveTo(cx, cy + r * 0.9f)
        cubicTo(cx - r * 1.6f, cy - r * 0.1f, cx - r * 0.7f, cy - r * 1.3f, cx, cy - r * 0.45f)
        cubicTo(cx + r * 0.7f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.1f, cx, cy + r * 0.9f)
        close()
    }

    fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int) = android.graphics.Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + PI * i / points
            val x = cx + (cos(a) * r).toFloat(); val y = cy + (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /**
     * A seamless tile of light and shade (black and white with alpha, laid over the tint): each kind from fixed numbers, so the same
     * texture looks the same on every phone. [strength] 0..100 is how deep the shade goes.
     */
    fun texture(kind: String, strength: Int, side: Int): ImageBitmap {
        val r = Random(kind.hashCode())
        val n = side
        val px = IntArray(n * n)
        val lattice = 8
        val grid = Array(lattice) { FloatArray(lattice) { r.nextFloat() * 2 - 1 } }
        fun smooth(x: Int, y: Int, cells: Int): Float {
            val fx = x.toFloat() * cells / n; val fy = y.toFloat() * cells / n
            val x0 = fx.toInt(); val y0 = fy.toInt(); val tx = fx - x0; val ty = fy - y0
            fun g(i: Int, j: Int) = grid[(i % cells + cells) % cells % lattice][(j % cells + cells) % cells % lattice]
            val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
            val a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * sx
            val b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * sx
            return a + (b - a) * sy
        }
        val rows = FloatArray(n) { r.nextFloat() * 2 - 1 }
        val cols = FloatArray(n) { r.nextFloat() * 2 - 1 }
        for (y in 0 until n) for (x in 0 until n) {
            val white = r.nextFloat() * 2 - 1
            val v = when (kind) {
                "grain" -> if (r.nextFloat() < 0.3f) white else 0f
                "paper" -> smooth(x, y, 8) * 0.5f + white * 0.35f
                "linen" -> rows[y] * 0.45f + cols[x] * 0.45f + white * 0.15f
                "canvas" -> (sin(x * PI / 2).toFloat() * sin(y * PI / 2).toFloat()) * 0.5f + white * 0.3f
                "kraft" -> smooth(x, y, 4) * 0.4f + white * 0.3f + (if (r.nextFloat() < 0.01f) -1f else 0f)
                "felt" -> smooth(x, y, 4) * 0.7f + smooth(x, y, 8) * 0.3f
                "concrete" -> smooth(x, y, 4) * 0.5f + (if (r.nextFloat() < 0.04f) white else white * 0.2f)
                else -> white
            }.coerceIn(-1f, 1f)
            val a = (abs(v) * strength * 2.0f).toInt().coerceIn(0, 200)
            px[y * n + x] = if (v >= 0) android.graphics.Color.argb(a, 255, 255, 255) else android.graphics.Color.argb(a, 0, 0, 0)
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
