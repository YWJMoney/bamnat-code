package com.bamnat.app.ui.theme

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * [MAT-01] 1.36 누락 점검 N10 진짜 유리. A glass part shows what lies behind it blurred, as frosted glass does, on every Android: the
 * screen's background (its colour, fill, picture and veil) is drawn once, small ([SCALE] of the window), and blurred on the phone's CPU
 * at each glass's blur; a glass part draws the piece of it that lies under it, then its tint, its light and its frost on top. Cards
 * between the glass and the background are not seen through (the glass looks onto the wallpaper, like the phone's own frosted bars).
 */
class Backdrop internal constructor(private val image: ImageBitmap, val origin: Offset) {
    private val blurred = HashMap<Int, ImageBitmap>()

    /** The background blurred by [radius] of its own (small) pixels, made once and kept. */
    fun blurred(radius: Int): ImageBitmap = synchronized(blurred) { blurred.getOrPut(radius) { BoxBlur.blur(image, radius) } }

    companion object {
        /** The backdrop is kept at an eighth of the screen's pixels: blurred, nothing finer shows anyway, and it costs little. */
        const val SCALE = 0.125f

        /** Draws [paint] into a small picture of a box [size] window pixels big, at window position [origin]. */
        fun make(size: IntSize, origin: Offset, density: Float, paint: DrawScope.() -> Unit): Backdrop? {
            if (size.width <= 0 || size.height <= 0) return null
            val w = (size.width * SCALE).roundToInt().coerceAtLeast(1)
            val h = (size.height * SCALE).roundToInt().coerceAtLeast(1)
            val image = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(density * SCALE), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), paint)
            return Backdrop(image, origin)
        }
    }
}

/**
 * Where glass parts find what is behind them: the screen's background ([screen], from [ScreenSkin]) and, over it, a page's own picture
 * ([page], the calendar's 그림 꾸미기), which wins while it is there.
 */
class BackdropHolder {
    var screen by mutableStateOf<Backdrop?>(null)
    var page by mutableStateOf<Backdrop?>(null)
    val current: Backdrop? get() = page ?: screen
}

val LocalBackdrop = staticCompositionLocalOf<BackdropHolder?> { null }

/** The glass radius in the backdrop's own pixels for a [Fill.Glass] blur of [blurDp] dp. */
internal fun glassRadius(blurDp: Int, density: Float): Int = (blurDp * density * Backdrop.SCALE).roundToInt().coerceIn(0, 24)

/** Draws the piece of [backdrop] under a part at window position [at], blurred by [blurDp], over the whole of this scope (the caller clips). */
internal fun DrawScope.drawBackdrop(backdrop: Backdrop, at: Offset, blurDp: Int, alpha: Float = 1f) {
    val image = backdrop.blurred(glassRadius(blurDp, density))
    withTransform({
        translate(backdrop.origin.x - at.x, backdrop.origin.y - at.y)
        scale(1f / Backdrop.SCALE, 1f / Backdrop.SCALE, pivot = Offset.Zero)
    }) { drawImage(image, alpha = alpha) }
}

/**
 * A page's own background (the calendar's picture) as the glass's backdrop while it is on screen: [paint] draws it over a box the size of
 * the one this modifier is on.
 */
fun Modifier.pageBackdrop(key: Any?, paint: DrawScope.() -> Unit): Modifier = composed {
    val holder = LocalBackdrop.current ?: return@composed Modifier
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var placed by remember { mutableStateOf<Pair<IntSize, Offset>?>(null) }
    val made = remember(key, placed, density) { placed?.let { (s, o) -> Backdrop.make(s, o, density, paint) } }
    DisposableEffect(holder, made) {
        holder.page = made
        onDispose { if (holder.page === made) holder.page = null }
    }
    Modifier.onGloballyPositioned { c -> (c.size to c.positionInWindow()).let { if (it != placed) placed = it } }
}

/** A plain three-pass box blur (close to a Gaussian) on a small ARGB picture, with its edges held. */
internal object BoxBlur {
    fun blur(src: ImageBitmap, radius: Int): ImageBitmap {
        if (radius <= 0) return src
        val bmp = src.asAndroidBitmap()
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        val r = radius.coerceAtMost(maxOf(w, h))
        repeat(3) {
            pass(a, b, w, h, r, horizontal = true)
            pass(b, a, w, h, r, horizontal = false)
        }
        val out = androidx.core.graphics.createBitmap(w, h)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out.asImageBitmap()
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val span = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + k else k * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[at(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[at(i)] = ((sa / span) shl 24) or ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
                val out = src[at(i - r)]
                val inn = src[at(i + r + 1)]
                sa += (inn ushr 24) - (out ushr 24)
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
