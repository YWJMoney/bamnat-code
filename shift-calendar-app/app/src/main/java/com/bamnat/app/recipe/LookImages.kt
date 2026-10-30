package com.bamnat.app.recipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import com.bamnat.app.AppContainer
import com.bamnat.core.recipe.PhotoLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * [RCX-30] 1.35 그림 꾸미기 pictures. A photo the user picks is cut to its place's shape where they put the frame, made small and
 * written again as WebP on the phone (so no EXIF, place or camera is kept), and kept under files/looks/<id>.webp, the id being
 * the first 16 hex of its SHA-256. A picture that came in a recipe is kept under the server's id. Nothing leaves the phone
 * unless the user puts the look in a recipe ([upload]); others see it once the operator looked at it.
 */
object LookImages {
    /** Each place's shape (width by height) and its size on the long side. */
    enum class Slot(val w: Int, val h: Int, val side: Int, val label: String) {
        BG(9, 19, 1080, "달력 배경"),
        HEAD(3, 1, 1080, "머리 그림"),
        ICON(1, 1, 192, "근무 그림"),
        WIDGET(2, 1, 800, "위젯 배경"),
    }

    const val MAX_BYTES = 160 * 1024
    private val cache = LruCache<String, Bitmap>(16)

    fun dir(context: Context): File = File(context.filesDir, "looks")
    fun file(context: Context, id: String): File = File(dir(context), "$id.webp")
    fun has(context: Context, id: String?): Boolean = PhotoLook.isId(id) && file(context, id!!).isFile

    /**
     * [uri] cut to [slot]'s shape: [crop] is the part of the picture to keep as fractions of its width and height (left, top,
     * right, bottom), from the frame the user moved; null is the middle. Returns the new picture's id, or null when the photo
     * cannot be read or is too small.
     */
    suspend fun import(context: Context, uri: Uri, slot: Slot, crop: RectF? = null): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Only the size is read here, and decodeStream gives null for that, so whether the photo opened is the stream's.
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return@withContext null
        if (bounds.outWidth < 64 || bounds.outHeight < 64) return@withContext null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= slot.side && bounds.outHeight / (sample * 2) >= slot.side) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val turned = upright(context, uri, raw)
        val webp = encode(cut(turned, slot, crop), slot) ?: return@withContext null
        save(context, webp)
    }

    /** [uri] for the frame on screen: about 1600 px, turned upright like [import] turns it, so the frame's fractions match. */
    suspend fun preview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?.let { upright(context, uri, it) }
        }.getOrNull()
    }

    /** The photo turned the way the camera was held (the EXIF itself is not kept). */
    private fun upright(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f
                    3 -> 180f
                    8 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** The part of [bmp] in [crop] (or the middle), at [slot]'s shape and size. */
    fun cut(bmp: Bitmap, slot: Slot, crop: RectF? = null): Bitmap {
        val aspect = slot.w.toFloat() / slot.h
        val (cw, ch) = if (bmp.width.toFloat() / bmp.height > aspect) (bmp.height * aspect).toInt() to bmp.height
        else bmp.width to (bmp.width / aspect).toInt()
        val src = crop?.let {
            val l = (it.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val t = (it.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            Rect(l, t, (it.right * bmp.width).toInt().coerceIn(l + 1, bmp.width), (it.bottom * bmp.height).toInt().coerceIn(t + 1, bmp.height))
        } ?: Rect((bmp.width - cw) / 2, (bmp.height - ch) / 2, (bmp.width + cw) / 2, (bmp.height + ch) / 2)
        val outW = if (slot.w >= slot.h) slot.side else slot.side * slot.w / slot.h
        val outH = if (slot.w >= slot.h) slot.side * slot.h / slot.w else slot.side
        val out = Bitmap.createBitmap(minOf(outW, src.width().coerceAtLeast(64)), minOf(outH, src.height().coerceAtLeast(64)), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, src, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** WebP, quality lowered until it fits [MAX_BYTES]. */
    private fun encode(bmp: Bitmap, slot: Slot): ByteArray? {
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        for (q in if (slot == Slot.ICON) listOf(90, 75) else listOf(82, 70, 58, 45, 32)) {
            val out = ByteArrayOutputStream()
            bmp.compress(format, q, out)
            if (out.size() <= MAX_BYTES) return out.toByteArray()
        }
        return null
    }

    private fun save(context: Context, webp: ByteArray, id: String = idOf(webp)): String {
        dir(context).mkdirs()
        val f = file(context, id)
        if (!f.isFile) File(dir(context), "$id.tmp").apply { writeBytes(webp); renameTo(f) }
        return id
    }

    fun idOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)

    /** The picture [id] for drawing, from memory or its file; null when the phone does not have it. */
    suspend fun bitmap(context: Context, id: String?): Bitmap? {
        if (!PhotoLook.isId(id)) return null
        cache.get(id!!)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, id)
            if (!f.isFile) null else runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()?.also { cache.put(id, it) }
        }
    }

    /** How light [bmp] is on the whole (0 dark … 1 light), so text over it can be dark or light. */
    fun lightness(bmp: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var sum = 0f
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = small.getPixel(x, y)
            sum += (0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)) / 255f
        }
        return sum / 256f
    }

    /**
     * A picture of a recipe the phone does not have yet, from the server (only one the operator let show, or the user's own).
     * Kept under its id; true when the phone has it after.
     */
    suspend fun fetch(c: AppContainer, id: String): Boolean {
        if (has(c.appContext, id)) return true
        val url = c.recipeApi.imageUrl(id) ?: return false
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("X-Device", c.companyStore.device)
                try {
                    if (conn.responseCode != 200) null else conn.inputStream.use { com.bamnat.app.recipe.readAtMost(it, MAX_BYTES + 1) }.takeIf { it.size <= MAX_BYTES }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        } ?: return false
        if (withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } == null) return false
        save(c.appContext, bytes, id)
        return true
    }

    /** The phone's picture [id] sent for a recipe; the server's id for it. */
    suspend fun upload(c: AppContainer, id: String): String {
        val bytes = withContext(Dispatchers.IO) { file(c.appContext, id).readBytes() }
        val server = c.recipeApi.image(c.companyStore.device, bytes)
        // Kept under the server's id too, so the recipe and this phone point at the same file.
        if (server != id) save(c.appContext, bytes, server)
        return server
    }

    /**
     * Lets go of pictures the look no longer uses ([used]) once they are 8 days old, so a recipe's 되돌리기 (7 days) still finds
     * the pictures it put back.
     */
    suspend fun tidy(context: Context, used: Set<String>, now: Long = com.bamnat.core.time.Today.millis()) = withContext(Dispatchers.IO) {
        dir(context).listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".webp").removeSuffix(".tmp")
            if (id !in used && now - f.lastModified() > 8L * 24 * 60 * 60 * 1000) { f.delete(); cache.remove(id) }
        }
    }
}
