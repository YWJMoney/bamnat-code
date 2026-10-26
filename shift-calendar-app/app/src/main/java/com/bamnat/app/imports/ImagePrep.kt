package com.bamnat.app.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.bamnat.core.imports.PhotoVariants
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * [READ-09] A photo made ready for reading on the phone (1.25 순번 370, 결정 D68 "휴대폰 안에서만"): upright as the camera
 * meant it (EXIF), no bigger than text recognition needs (a 50-megapixel photo read whole ran out of memory or took long),
 * and, for a second try, turned level and its contrast stretched (a shaded or grey photo). Kept in memory while the
 * screen is open only: no copy is saved, and nothing leaves the phone. 듀티표, 명세서 and 회사 규칙 photos all come
 * through here.
 */
object ImagePrep {
    /** Longest side read: a 31-day table's letters stay 20+ pixels high at this size. */
    const val MAX_SIDE = 3200

    /** The photo at [uri], upright and at most [MAX_SIDE] on its longest side. */
    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("사진을 열지 못했어요")
        val turn = runCatching { resolver.openInputStream(uri)?.use { degrees(ExifInterface(it)) } }.getOrNull() ?: 0f
        val scale = (maxSide.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        if (turn == 0f && scale == 1f) return decoded
        val m = Matrix().apply { postScale(scale, scale); postRotate(turn) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it !== decoded) decoded.recycle() }
    }

    private fun degrees(exif: ExifInterface): Float = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    /**
     * [bitmap] turned by -[deg] so its text lies level, grey with its contrast stretched: what the second reading looks
     * at. Where a point of the result came from is [Leveled.toSource].
     */
    fun leveled(bitmap: Bitmap, deg: Double): Leveled {
        val r = Math.toRadians(deg)
        val w = bitmap.width
        val h = bitmap.height
        val outW = (abs(w * cos(r)) + abs(h * sin(r))).toInt().coerceAtLeast(1)
        val outH = (abs(w * sin(r)) + abs(h * cos(r))).toInt().coerceAtLeast(1)
        val m = Matrix().apply {
            postTranslate(-w / 2f, -h / 2f)
            postRotate(-deg.toFloat())
            postTranslate(outW / 2f, outH / 2f)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(CONTRAST) }
        canvas.drawBitmap(bitmap, m, paint)
        val back = Matrix().also { m.invert(it) }
        return Leveled(out, back)
    }

    /** A photo turned level, and the way back to the first photo's pixels. */
    class Leveled(val bitmap: Bitmap, private val back: Matrix) {
        fun toSource(x: Int, y: Int): Pair<Int, Int> {
            val p = floatArrayOf(x.toFloat(), y.toFloat())
            back.mapPoints(p)
            return p[0].toInt() to p[1].toInt()
        }
    }

    /** Grey, darker darks and lighter lights: pale print and shade read better. */
    private val CONTRAST = ColorMatrix().apply {
        setSaturation(0f)
        val c = 1.6f
        val t = (1 - c) * 128f
        postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }

    /**
     * [READ-13] 1.27 순번 446: one way of making [base] over for another reading ([PhotoVariants]): light evened out,
     * contrast stretched, black and white, a dark screen turned light, or the text's part twice the size. The made-over
     * picture's pixel (x, y) is [left] + x × [toBase], [top] + y × [toBase] of [base], so every reading's words are put
     * back on [base] before they are compared. Nothing is saved; each picture is dropped once read.
     */
    class Variant(val kind: PhotoVariants.Kind, val bitmap: Bitmap, val toBase: Float, val left: Int = 0, val top: Int = 0) {
        fun x(v: Int): Int = left + (v * toBase).toInt()
        fun y(v: Int): Int = top + (v * toBase).toInt()
    }

    /** Longest side the made-over pictures are worked at: four arrays of a 2400×1800 picture stay within a phone's memory. */
    const val WORK_SIDE = 2400

    /** Longest side of the twice-the-size picture. */
    const val UP_SIDE = 3600

    /** How [base] looks (on a small copy) and the ways worth reading it, given its letters' height from a first reading. */
    fun plan(base: Bitmap, letterHeight: Int?): Pair<PhotoVariants.Stats, List<PhotoVariants.Kind>> {
        val s = (1200f / max(base.width, base.height)).coerceAtMost(1f)
        val small = if (s < 1f) Bitmap.createScaledBitmap(base, (base.width * s).toInt().coerceAtLeast(1), (base.height * s).toInt().coerceAtLeast(1), true) else base
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val g = PhotoVariants.gray(px, small.width, small.height)
        if (small !== base) small.recycle()
        // Letters measured on [base]; the plan's own small copy does not change what they are.
        return PhotoVariants.stats(g) to PhotoVariants.plan(g, letterHeight)
    }

    /**
     * [base] made over as [kind]; null when not worth it (already big enough to double) or the phone ran short of memory
     * (that way is skipped, the others still read). [text] is where the words of a first reading were, so the
     * twice-the-size picture holds only the table, not the desk around it.
     */
    fun variant(base: Bitmap, kind: PhotoVariants.Kind, text: android.graphics.Rect? = null): Variant? = try {
        when (kind) {
            PhotoVariants.Kind.ORIG -> Variant(kind, base, 1f)
            PhotoVariants.Kind.UP2 -> {
                val r = text?.let { t ->
                    val m = max(t.width(), t.height()) / 20
                    android.graphics.Rect((t.left - m).coerceAtLeast(0), (t.top - m).coerceAtLeast(0),
                        (t.right + m).coerceAtMost(base.width), (t.bottom + m).coerceAtMost(base.height))
                }?.takeIf { it.width() > 8 && it.height() > 8 } ?: android.graphics.Rect(0, 0, base.width, base.height)
                val f = (UP_SIDE.toFloat() / max(r.width(), r.height())).coerceAtMost(2f)
                if (f < 1.4f) null else {
                    val crop = Bitmap.createBitmap(base, r.left, r.top, r.width(), r.height())
                    val big = Bitmap.createScaledBitmap(crop, (r.width() * f).toInt(), (r.height() * f).toInt(), true)
                    if (crop !== base && crop !== big) crop.recycle()
                    val out = over(big) { PhotoVariants.flat(it) }
                    if (big !== out) big.recycle()
                    Variant(kind, out, 1f / f, r.left, r.top)
                }
            }
            else -> {
                val s = (WORK_SIDE.toFloat() / max(base.width, base.height)).coerceAtMost(1f)
                val work = if (s < 1f) Bitmap.createScaledBitmap(base, (base.width * s).toInt().coerceAtLeast(1), (base.height * s).toInt().coerceAtLeast(1), true) else base
                val out = over(work) { PhotoVariants.make(kind, it) }
                if (work !== base) work.recycle()
                Variant(kind, out, 1f / s)
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    }

    /** [b]'s pixels grey, through [f], as a new picture. */
    private fun over(b: Bitmap, f: (PhotoVariants.Gray) -> PhotoVariants.Gray): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val g = f(PhotoVariants.gray(px, b.width, b.height))
        return Bitmap.createBitmap(PhotoVariants.argb(g), g.w, g.h, Bitmap.Config.ARGB_8888)
    }

    /** A copy no wider than [maxSide] for showing on screen, and the factor from it back to [bitmap]'s pixels. */
    fun forScreen(bitmap: Bitmap, maxSide: Int = 1600): Pair<Bitmap, Float> {
        val s = (maxSide.toFloat() / max(bitmap.width, bitmap.height)).coerceAtMost(1f)
        if (s == 1f) return bitmap to 1f
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true) to (1f / s)
    }
}
