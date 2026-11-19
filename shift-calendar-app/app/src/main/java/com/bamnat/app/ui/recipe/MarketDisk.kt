package com.bamnat.app.ui.recipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 1.39 [MKP-02] 장터 그림·작품을 폰에 두기 (사용자 2026-11-07 "장터에서 테마 로딩이 너무 느린데"): a 장터 card's drawn picture and a work's
 * written form, kept in the app's cache folder, so opening the 장터 again (or after the app was closed) shows the pictures at once
 * instead of reading every work and drawing every example phone again.
 *
 * - A work's written form never changes for its code@version, so it is kept as it came (and checked again by its hash when read).
 * - A picture is kept per card key (code@version, kind of work, dark, width) under this app's versionCode: an update that draws the
 *   example phone differently starts afresh. It is only kept when every picture and font the work names was on the phone when it was
 *   drawn (else it stays in memory for this time only, and is drawn again next time).
 * - At most [MAX_BYTES] in all and [MAX_DAYS] old; the oldest go first. The system may also clear the cache folder: then it is drawn again.
 */
internal object MarketDisk {
    const val MAX_BYTES = 40L * 1024 * 1024
    const val MAX_DAYS = 14
    private const val QUALITY = 90
    @Volatile private var writes = 0

    private fun root(context: Context) = File(context.cacheDir, "market")
    /** 1.39 [MKP-06] Raised when a card is drawn differently within one app version (2: in its own fonts), so older pictures go. */
    private const val DRAW = 2
    private fun shotDir(context: Context) = File(root(context), "shot-${com.bamnat.app.BuildConfig.VERSION_CODE}-$DRAW")
    private fun bodyDir(context: Context) = File(root(context), "body")

    private fun name(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The picture kept for card [key], or null. */
    suspend fun shot(context: Context, key: String): ImageBitmap? = read(File(shotDir(context), name(key) + ".webp"))

    /**
     * 2.0.0 [RVM-21] MKT-32 The picture kept for card [key] drawn in the phone's own fonts (its open fonts were not on the phone), or null: shown
     * at once instead of drawing the card again every time, and drawn again in its own fonts once they are here ([standInFile]).
     */
    suspend fun standIn(context: Context, key: String): ImageBitmap? = read(standInFile(context, key))

    private fun standInFile(context: Context, key: String) = File(shotDir(context), name(key) + ".s.webp")

    private suspend fun read(f: File): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!f.isFile) return@withContext null
        if (f.lastModified() < com.bamnat.core.time.Today.millis() - MAX_DAYS * DAY) { f.delete(); return@withContext null }
        runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    /** Keeps card [key]'s picture; [standIn]: drawn in the phone's fonts ([Kept.STAND_IN]). A real one takes the stand-in's place. */
    suspend fun keepShot(context: Context, key: String, picture: ImageBitmap, standIn: Boolean = false) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = shotDir(context).apply { mkdirs() }
            val bmp = picture.asAndroidBitmap().let { b ->
                // A picture read back from a layer can live on the graphics card; it is copied out before it is written.
                if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
            }
            val tmp = File(dir, name(key) + ".tmp")
            tmp.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                bmp.compress(format, QUALITY, out)
            }
            (if (standIn) standInFile(context, key) else File(dir, name(key) + ".webp")).let { f -> tmp.renameTo(f); f.stamp() }
            if (!standIn) standInFile(context, key).delete()
            tidyNow(context)
        }
    }

    /** A work's written form (hash, body) kept for "code@version", or null. */
    suspend fun body(context: Context, codeVersion: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val f = File(bodyDir(context), name(codeVersion) + ".json")
        if (!f.isFile) return@withContext null
        runCatching { org.json.JSONObject(f.readText()).let { it.getString("hash") to it.getString("body") } }.getOrNull()
    }

    suspend fun keepBody(context: Context, codeVersion: String, hash: String, body: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = bodyDir(context).apply { mkdirs() }
            val f = File(dir, name(codeVersion) + ".json")
            if (!f.isFile) {
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeText(org.json.JSONObject().put("hash", hash).put("body", body).toString())
                tmp.renameTo(f)
                f.stamp()
                tidyNow(context)
            }
        }
    }

    // ---- 1.39 [MKP-07] 장터 목록을 폰에 두기 (사용자 2026-11-08 "아직도 장터 로딩이 너무 느려") ----

    private fun listDir(context: Context) = File(root(context), "list")

    /**
     * The list last read for [key] (구간·칸·찾기·근무 유형) and when, or null: shown at once when the 장터 is opened after the app was
     * closed, while it is read again. Kept [MAX_DAYS] at most, like the rest.
     */
    suspend fun list(context: Context, key: String): Pair<com.bamnat.app.recipe.RecipePage, Long>? = withContext(Dispatchers.IO) {
        val f = File(listDir(context), name(key) + ".json")
        if (!f.isFile) return@withContext null
        val at = f.lastModified()
        if (at < com.bamnat.core.time.Today.millis() - MAX_DAYS * DAY) return@withContext null
        runCatching { com.bamnat.app.recipe.RecipeClient.pageOf(org.json.JSONObject(f.readText())) to at }.getOrNull()
    }

    suspend fun keepList(context: Context, key: String, raw: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = listDir(context).apply { mkdirs() }
            val tmp = File(dir, name(key) + ".tmp")
            tmp.writeText(raw)
            File(dir, name(key) + ".json").let { f -> tmp.renameTo(f); f.stamp() }
            tidyNow(context)
        }
    }

    /**
     * 2.0.0 [RVR-24] The file's time is written from Today too: its age is read with Today ([MAX_DAYS]), so a time-travel test run
     * (only Today moves, 검토 I60b-01) sees one clock. On a phone it is the same moment the file was written.
     */
    private fun File.stamp() { setLastModified(com.bamnat.core.time.Today.millis()) }

    /** Every 20th write: old app versions' pictures, files past [MAX_DAYS], then the oldest past [MAX_BYTES]. */
    private fun tidyNow(context: Context) {
        if (++writes % 20 != 1) return
        val root = root(context)
        val now = com.bamnat.core.time.Today.millis()
        root.listFiles()?.filter { it.isDirectory && it.name.startsWith("shot-") && it.name != shotDir(context).name }?.forEach { it.deleteRecursively() }
        val files = root.walkTopDown().filter { it.isFile }.toMutableList()
        files.filter { it.lastModified() < now - MAX_DAYS * DAY || it.name.endsWith(".tmp") && it.lastModified() < now - 60_000 }
            .forEach { it.delete(); files.remove(it) }
        var total = files.sumOf { it.length() }
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /**
     * Whether everything [body] names that is fetched apart (pictures by their 16-letter id, shared fonts) is on the phone, so a
     * picture drawn now is the work as it really looks and can be kept.
     */
    fun complete(context: Context, body: String): Boolean = kept(context, body) == Kept.FULL

    /** 2.0.0 [RVM-21] MKT-32 How a card picture drawn now may be kept. */
    enum class Kept {
        /** Everything it names was here: the work as it really looks. */
        FULL,
        /** Its pictures were here, not all its open fonts: drawn in the phone's closest fonts, kept until they come. */
        STAND_IN,
    }

    /** How a picture of [body] drawn now is kept, or null (a picture it names was not here yet: drawn again next time). */
    fun kept(context: Context, body: String): Kept? {
        val pictures = PICTURE.findAll(body).map { it.groupValues[1] }.toSet()
        if (pictures.any { !com.bamnat.app.recipe.LookImages.has(context, it) }) return null
        if (!com.bamnat.app.recipe.RecipeFonts.idsIn(body).all { com.bamnat.app.ui.theme.UserFonts.file(context, it) != null }) return null
        // 1.39 [MKP-06] and every open font it names, whole or as a card font. 2.0.0 [RVM-21] MKT-32 (151 of the 1.39 app's 280 works name one):
        // without them it is still kept, as a stand-in, so a 장터 opened offline or before its fonts come is not drawn again card by card.
        return if (MarketFonts.complete(context, MarketFonts.ids(body))) Kept.FULL else Kept.STAND_IN
    }

    private val PICTURE = Regex("\"([0-9a-f]{16})\"")
    private const val DAY = 24L * 60 * 60 * 1000
}
