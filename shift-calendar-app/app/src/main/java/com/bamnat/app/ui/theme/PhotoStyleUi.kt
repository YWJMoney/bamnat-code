package com.bamnat.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.core.recipe.PhotoAdjust
import com.bamnat.core.recipe.PhotoFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** [MAT-03] The colour filter that draws [adjust] (null for the picture as it is). */
fun PhotoAdjust?.colorFilter(): ColorFilter? = this?.takeIf { !it.isDefault }?.let { ColorFilter.colorMatrix(ColorMatrix(it.matrix())) }

/** [MAT-03] Android's blend mode for [PhotoAdjust.blend]. */
fun PhotoAdjust?.blendMode(): BlendMode = when (this?.blend) {
    "multiply" -> BlendMode.Multiply; "screen" -> BlendMode.Screen; "overlay" -> BlendMode.Overlay; "darken" -> BlendMode.Darken
    "lighten" -> BlendMode.Lighten; "softlight" -> BlendMode.Softlight; "color" -> BlendMode.Color; "luminosity" -> BlendMode.Luminosity
    else -> BlendMode.SrcOver
}

/**
 * [MAT-03] Draws [image] cut to fill the box (like ContentScale.Crop) through [adjust]: its colours, its blend with what is under it
 * and its blur (Android 12+; under 12 the picture is sharp).
 */
fun Modifier.adjustedPhoto(image: ImageBitmap, adjust: PhotoAdjust?, alpha: Float = 1f): Modifier =
    (if ((adjust?.blur ?: 0) > 0) this.blur(adjust!!.blur.dp) else this).drawBehind { drawCropped(image, adjust, alpha) }

/** [MAT-03] [image] cut to fill this scope (ContentScale.Crop) through [adjust]'s colours and blend; its blur is the caller's. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropped(image: ImageBitmap, adjust: PhotoAdjust?, alpha: Float = 1f) {
    val iw = image.width.toFloat(); val ih = image.height.toFloat()
    val scale = maxOf(size.width / iw, size.height / ih)
    val sw = size.width / scale; val sh = size.height / scale
    val sx = ((iw - sw) / 2).roundToInt().coerceAtLeast(0); val sy = ((ih - sh) / 2).roundToInt().coerceAtLeast(0)
    drawImage(image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(sw.roundToInt().coerceAtMost(image.width - sx), sh.roundToInt().coerceAtMost(image.height - sy)),
        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)), alpha = alpha, colorFilter = adjust.colorFilter(), blendMode = adjust.blendMode())
}

/** [MAT-04] The shape a picture is cut to; null or unknown is [fallback] (the app's own). */
fun frameShape(frame: String?, fallback: Shape = RoundedCornerShape(6.dp)): Shape {
    PhotoFrame.letters(frame)?.let { word -> return lettersShape(word) }
    return when (frame) {
        null -> fallback
        "rect" -> RectangleShape
        "round" -> RoundedCornerShape(22)
        "circle" -> CircleShape
        "star" -> GenericShape { s, _ -> addPath(FillTiles.starPath(s.width / 2, s.height / 2, minOf(s.width, s.height) / 2, minOf(s.width, s.height) * 0.22f, 5).asComposePath()) }
        "drop" -> GenericShape { s, _ ->
            val w = s.width; val h = s.height
            moveTo(w / 2, 0f)
            cubicTo(w * 0.9f, h * 0.35f, w, h * 0.55f, w, h * 0.66f)
            cubicTo(w, h * 0.88f, w * 0.78f, h, w / 2, h)
            cubicTo(w * 0.22f, h, 0f, h * 0.88f, 0f, h * 0.66f)
            cubicTo(0f, h * 0.55f, w * 0.1f, h * 0.35f, w / 2, 0f)
            close()
        }
        "heart" -> GenericShape { s, _ ->
            val w = s.width; val h = s.height
            moveTo(w / 2, h * 0.95f)
            cubicTo(-w * 0.25f, h * 0.5f, w * 0.1f, -h * 0.15f, w / 2, h * 0.22f)
            cubicTo(w * 0.9f, -h * 0.15f, w * 1.25f, h * 0.5f, w / 2, h * 0.95f)
            close()
        }
        "hexagon" -> polygon(6, -PI / 2)
        "arch" -> GenericShape { s, _ ->
            val w = s.width; val h = s.height
            moveTo(0f, h); lineTo(0f, w / 2)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, w, w), 180f, 180f, false)
            lineTo(w, h); close()
        }
        "cloud" -> GenericShape { s, _ ->
            val w = s.width; val h = s.height
            addOval(androidx.compose.ui.geometry.Rect(0f, h * 0.35f, w * 0.5f, h * 0.9f))
            addOval(androidx.compose.ui.geometry.Rect(w * 0.2f, h * 0.1f, w * 0.75f, h * 0.7f))
            addOval(androidx.compose.ui.geometry.Rect(w * 0.5f, h * 0.3f, w, h * 0.9f))
            addRect(androidx.compose.ui.geometry.Rect(w * 0.2f, h * 0.6f, w * 0.8f, h * 0.9f))
        }
        "flower" -> GenericShape { s, _ ->
            val cx = s.width / 2; val cy = s.height / 2; val r = minOf(s.width, s.height) / 2
            for (i in 0 until 6) {
                val a = 2 * PI * i / 6
                val px = cx + (cos(a) * r * 0.5f).toFloat(); val py = cy + (sin(a) * r * 0.5f).toFloat()
                addOval(androidx.compose.ui.geometry.Rect(px - r * 0.5f, py - r * 0.5f, px + r * 0.5f, py + r * 0.5f))
            }
        }
        "ticket" -> GenericShape { s, _ ->
            val w = s.width; val h = s.height; val n = minOf(w, h) * 0.12f
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h / 2 - n)
            arcTo(androidx.compose.ui.geometry.Rect(w - n, h / 2 - n, w + n, h / 2 + n), 270f, -180f, false)
            lineTo(w, h); lineTo(0f, h); lineTo(0f, h / 2 + n)
            arcTo(androidx.compose.ui.geometry.Rect(-n, h / 2 - n, n, h / 2 + n), 90f, -180f, false)
            close()
        }
        else -> fallback
    }
}

private fun polygon(n: Int, start: Double): Shape = GenericShape { s, _ ->
    val cx = s.width / 2; val cy = s.height / 2; val r = minOf(s.width, s.height) / 2
    for (i in 0 until n) {
        val a = start + 2 * PI * i / n
        val x = cx + (cos(a) * r).toFloat(); val y = cy + (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A picture cut to the letters of [word], as big as the box lets them be. */
private fun lettersShape(word: String): Shape = GenericShape { s: Size, _ ->
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textSize = 100f
    }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(word, 0, word.length, bounds)
    val scale = minOf(s.width / bounds.width().coerceAtLeast(1), s.height / bounds.height().coerceAtLeast(1))
    paint.textSize = 100f * scale
    paint.getTextBounds(word, 0, word.length, bounds)
    val p = android.graphics.Path()
    paint.getTextPath(word, 0, word.length, (s.width - bounds.width()) / 2f - bounds.left, (s.height - bounds.height()) / 2f - bounds.top, p)
    addPath(p.asComposePath())
}
