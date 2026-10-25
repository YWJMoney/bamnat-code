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

    /** A copy no wider than [maxSide] for showing on screen, and the factor from it back to [bitmap]'s pixels. */
    fun forScreen(bitmap: Bitmap, maxSide: Int = 1600): Pair<Bitmap, Float> {
        val s = (maxSide.toFloat() / max(bitmap.width, bitmap.height)).coerceAtMost(1f)
        if (s == 1f) return bitmap to 1f
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true) to (1f / s)
    }
}
