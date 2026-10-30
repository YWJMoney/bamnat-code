package com.bamnat.app.recipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.bamnat.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [RCX-15] 1.35 그림 표지. Reading: a picture the server let show, kept in memory (a few dozen). Making: any photo the user picks is
 * made small on the phone (512 px on the long side) and written again as WebP, so what goes out has no EXIF, no place and no
 * camera, and is never more than 64 KB.
 */
object CoverImages {
    private const val MAX_BYTES = 64 * 1024
    private const val SIDE = 512
    private val cache = LruCache<String, Bitmap>(40)

    suspend fun load(c: AppContainer, id: String): Bitmap? {
        cache.get(id)?.let { return it }
        val url = c.recipeApi.imageUrl(id) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("X-Device", c.companyStore.device)
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    val bytes = conn.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
                    if (bytes.size > MAX_BYTES) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()?.also { cache.put(id, it) }
        }
    }

    /** A picked photo as a cover's WebP: square-cropped from the middle, [SIDE] px, quality lowered until it fits. */
    suspend fun encode(source: Bitmap): ByteArray? = withContext(Dispatchers.Default) {
        val side = minOf(source.width, source.height)
        if (side < 64) return@withContext null
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        for (q in listOf(80, 65, 50, 35, 20)) {
            val out = ByteArrayOutputStream()
            small.compress(format, q, out)
            if (out.size() <= MAX_BYTES) return@withContext out.toByteArray()
        }
        null
    }

    fun put(id: String, bitmap: Bitmap) { cache.put(id, bitmap) }
}
