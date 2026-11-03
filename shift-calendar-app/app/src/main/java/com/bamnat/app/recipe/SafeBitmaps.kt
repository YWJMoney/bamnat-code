package com.bamnat.app.recipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 1.36 [SEC-04] 보안 점검 M1: a picture that came from the server (a recipe's cover or look) is read for its size first and opened
 * only when it is a picture the app could have made. Before, the bytes were opened as they were: a WebP of one colour, 16383 px a
 * side, is a few KB yet asks about a gigabyte, and the phone that opened the 장터 stopped. The server refuses such pictures too
 * (rcWebpSize); this is the second wall for what is already kept or comes from an older server.
 */
object SafeBitmaps {
    /** The largest side the app makes (달력 배경 1080) with room; anything past it is not ours. */
    const val MAX_SIDE = 2048

    /** What [decode] does with a picture of [w] × [h]: null to refuse, else the sample size that brings it to [maxSide]. */
    fun sampleFor(w: Int, h: Int, maxSide: Int = MAX_SIDE, refuseOver: Int = 4 * MAX_SIDE): Int? {
        if (w <= 0 || h <= 0 || w > refuseOver || h > refuseOver) return null
        var sample = 1
        while (maxOf(w, h) / sample > maxSide) sample *= 2
        return sample
    }

    fun decode(bytes: ByteArray, maxSide: Int = MAX_SIDE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = sampleFor(bounds.outWidth, bounds.outHeight, maxSide) ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun decodeFile(path: String, maxSide: Int = MAX_SIDE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = sampleFor(bounds.outWidth, bounds.outHeight, maxSide) ?: return null
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** A photo the user picked, brought to [maxSide] as it is read (a 50 MP photo would otherwise take 200 MB at once). */
    fun decodeUri(context: android.content.Context, uri: android.net.Uri, maxSide: Int = MAX_SIDE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleFor(bounds.outWidth, bounds.outHeight, maxSide, refuseOver = Int.MAX_VALUE) ?: return null
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()
}
