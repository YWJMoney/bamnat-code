package com.bamnat.app.ui.memo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bamnat.core.memo.MemoPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * [MPH-01][MPH-03] 1.38 메모 사진 files. A photo put in a memo is copied into the app's own folder (files/memo-photos), picked with
 * the system photo picker or taken with the camera, so no permission is asked. Each is made upright, at most [MAX_SIDE] px on its long
 * side and written again as JPEG: what is kept has no EXIF (no place, no time, no phone) and takes about 0.3~0.6 MB.
 *
 * A memo keeps only the file's name ([MemoPhotos]). A file is never deleted when a photo is taken out of a memo (되돌리기, 휴지통 and
 * 복제 still point at it); [sweep] deletes the files no memo points at any more, a day after they were last written.
 *
 * 백업: 밤낮's backup file carries them while 사진도 백업 is on ([com.bamnat.app.data.BackupPhotos]); Android's cloud backup leaves them out
 * (25 MB 한도, data_extraction_rules.xml), a phone-to-phone transfer takes them.
 */
object MemoPhotoFiles {
    const val MAX_SIDE = 1600
    private const val QUALITY = 85

    /** Files older than this that no memo points at go in [sweep] (a photo just put in is not swept before its memo is saved). */
    private const val SWEEP_AFTER_MILLIS = 24L * 60 * 60 * 1000

    fun dir(context: Context) = File(context.filesDir, "memo-photos")

    fun file(context: Context, name: String): File? = name.takeIf(MemoPhotos::validName)?.let { File(dir(context), it) }

    /** Copies the picked photo in as a new file; its name, or null when it could not be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = com.bamnat.app.imports.ImagePrep.load(context, uri, MAX_SIDE)
            val name = UUID.randomUUID().toString() + ".jpg"
            val dir = dir(context).apply { mkdirs() }
            val part = File(dir, "$name.part")
            part.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            if (!part.renameTo(File(dir, name))) { part.delete(); return@runCatching null }
            name
        }.getOrNull()
    }

    /** Where the camera writes a photo before [import] takes it in (cache/scan is shared with the FileProvider). */
    fun cameraFile(context: Context): File = File(context.cacheDir, "scan/memo-shot.jpg").apply { parentFile?.mkdirs(); delete() }

    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** The photo drawn about [side] px wide (a phone's width at most), from memory when it was just drawn. */
    suspend fun load(context: Context, name: String, side: Int): ImageBitmap? {
        val key = "$name@${bucket(side)}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, name)?.takeIf { it.isFile } ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= bucket(side)) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** The photo's width : height, read from its header only; null when it is gone. */
    suspend fun ratio(context: Context, name: String): Float? = withContext(Dispatchers.IO) {
        val f = file(context, name)?.takeIf { it.isFile } ?: return@withContext null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
    }

    /** Sizes drawn are rounded up to a few steps, so a photo resized by a finger is not read again at every pixel. */
    private fun bucket(side: Int): Int = when {
        side <= 240 -> 240
        side <= 480 -> 480
        side <= 800 -> 800
        else -> MAX_SIDE
    }

    /**
     * [MPH-04] The share of a memo: plain words, or with [names] the photos too (read only for the app that receives them, through
     * the FileProvider's memo-photos path).
     */
    fun shareIntent(context: Context, names: List<String>): android.content.Intent {
        val uris = names.mapNotNull { n -> file(context, n)?.takeIf { it.isFile } }.mapNotNull { f ->
            runCatching { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", f) }.getOrNull()
        }
        if (uris.isEmpty()) return android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        val intent = if (uris.size == 1) android.content.Intent(android.content.Intent.ACTION_SEND).putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
        else android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
        return intent.setType("image/jpeg").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION).also {
            it.clipData = android.content.ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { u -> addItem(android.content.ClipData.Item(u)) } }
        }
    }

    /** How many photo files the memos keep and how much room they take, for 설정 › 백업 and the editor's note. */
    fun size(context: Context): Pair<Int, Long> {
        val files = dir(context).listFiles { f -> f.isFile && MemoPhotos.validName(f.name) }.orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    /**
     * Deletes the photo files that none of [used] (every memo's photos, the trash's too) names, and the half-written ones, once they are a
     * day old. How many went.
     */
    fun sweep(context: Context, used: Set<String>, now: Long = com.bamnat.core.time.Today.millis()): Int {
        val files = dir(context).listFiles().orEmpty()
        var n = 0
        for (f in files) {
            if (!f.isFile || f.name in used) continue
            if (now - f.lastModified() < SWEEP_AFTER_MILLIS) continue
            if (f.delete()) n++
        }
        return n
    }
}
