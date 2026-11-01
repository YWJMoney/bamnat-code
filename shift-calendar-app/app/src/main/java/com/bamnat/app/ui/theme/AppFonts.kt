package com.bamnat.app.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.bamnat.app.R
import com.bamnat.core.recipe.FontCatalog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * [MAT-05][MAT-06] 1.36 글꼴: what each [FontCatalog] id is drawn in. The phone's own four are always there; the open fonts come down
 * through Google Play services' font provider the first time one is drawn (and are kept by Play services for every app), the text
 * standing in the phone's font until then; the user's own fonts are files under `files/fonts`.
 */
object AppFonts {
    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )
    private val cache = ConcurrentHashMap<String, FontFamily>()

    /** [id]'s family, or null for the app's own (no font chosen). */
    fun family(context: Context, id: String?): FontFamily? {
        if (id == null) return null
        cache[id]?.let { return it }
        val made = when (id) {
            "sans", "round" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            "hand" -> FontFamily.Cursive
            else -> FontCatalog.byId(id)?.family?.let { name ->
                val g = GoogleFont(name, bestEffort = true)
                FontFamily(Font(g, provider, FontWeight.Normal), Font(g, provider, FontWeight.Bold))
            } ?: UserFonts.file(context, id)?.let { f ->
                runCatching { FontFamily(androidx.compose.ui.text.font.Font(f)) }.getOrNull()
            } ?: family(context, UserFonts.fallbackOf(context, id) ?: "sans")
        } ?: FontFamily.SansSerif
        cache[id] = made
        return made
    }

    fun forget(id: String) { cache.remove(id) }
}

/** [id]'s family for this screen, kept across recompositions. */
@Composable
fun rememberFontFamily(id: String?): FontFamily? {
    val ctx = LocalContext.current
    return remember(id) { AppFonts.family(ctx, id) }
}

/** Every text style of [this] in [family] (sizes, weights and spacing stay). */
fun Typography.inFamily(family: FontFamily?): Typography = if (family == null) this else Typography(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * [MAT-06] 1.36 내 글꼴: TTF·OTF files the user brought, kept on this phone only under `files/fonts/<id>.ttf` with a line about each
 * (`<id>.json`: its name, the app font most like it, and whether its maker let others use it — OFL 등, 직접 밝힘). A recipe names one by
 * its id and its stand-in; a phone without the file draws the stand-in.
 */
object UserFonts {
    data class Mine(val id: String, val name: String, val fallback: String, val open: Boolean, val bytes: Long)

    sealed interface Result {
        data class Added(val font: Mine) : Result
        data class Problem(val text: String) : Result
    }

    private fun dir(c: Context) = File(c.filesDir, "fonts").apply { mkdirs() }

    fun file(c: Context, id: String): File? = if (!FontCatalog.isUser(id)) null else File(dir(c), "$id.ttf").takeIf { it.isFile }

    fun list(c: Context): List<Mine> = dir(c).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
        runCatching {
            val o = JSONObject(f.readText())
            val id = f.name.removeSuffix(".json")
            Mine(id, o.optString("name", "내 글꼴"), o.optString("fallback", "sans"), o.optBoolean("open"), File(dir(c), "$id.ttf").length())
        }.getOrNull()?.takeIf { file(c, it.id) != null }
    }.sortedBy { it.name }

    fun ids(c: Context): Set<String> = list(c).map { it.id }.toSet()

    fun fallbackOf(c: Context, id: String): String? = runCatching { JSONObject(File(dir(c), "$id.json").readText()).optString("fallback") }.getOrNull()
        ?.takeIf { FontCatalog.byId(it) != null }

    /**
     * Copies the font at [uri] in: at most [FontCatalog.USER_MAX_BYTES] and [FontCatalog.USER_MAX_COUNT] fonts, and only a file the
     * phone can read as a font (else it is left out and the user told why). The same file twice is the same font.
     */
    fun add(c: Context, uri: Uri, name: String?): Result {
        if (list(c).size >= FontCatalog.USER_MAX_COUNT) return Result.Problem("내 글꼴은 ${FontCatalog.USER_MAX_COUNT}개까지 넣을 수 있어요. 안 쓰는 글꼴을 빼 주세요")
        val bytes = runCatching {
            c.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > FontCatalog.USER_MAX_BYTES) return Result.Problem("글꼴 파일이 너무 커요. 20MB까지 넣을 수 있어요")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return Result.Problem("파일을 열지 못했어요")
        if (!looksLikeFont(bytes)) return Result.Problem("TTF·OTF 글꼴 파일이 아니에요")
        val id = "u" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val ttf = File(dir(c), "$id.ttf")
        ttf.writeBytes(bytes)
        val ok = runCatching { android.graphics.Typeface.Builder(ttf).build() != null }.getOrDefault(false)
        if (!ok) { ttf.delete(); return Result.Problem("이 글꼴은 휴대폰이 읽지 못해요") }
        val shown = name?.substringBeforeLast('.')?.take(30)?.ifBlank { null } ?: "내 글꼴"
        val guess = guessFallback(shown)
        File(dir(c), "$id.json").writeText(JSONObject().put("name", shown).put("fallback", guess).put("open", false).toString())
        AppFonts.forget(id)
        return Result.Added(Mine(id, shown, guess, false, bytes.size.toLong()))
    }

    /** Whether the maker lets others use it (OFL·공개 글꼴), as the user says; only such a font may be shared. */
    fun setOpen(c: Context, id: String, open: Boolean) = edit(c, id) { it.put("open", open) }

    fun setFallback(c: Context, id: String, fallback: String) = edit(c, id) { it.put("fallback", fallback) }.also { AppFonts.forget(id) }

    fun remove(c: Context, id: String) {
        File(dir(c), "$id.ttf").delete(); File(dir(c), "$id.json").delete(); AppFonts.forget(id)
    }

    private fun edit(c: Context, id: String, f: (JSONObject) -> Unit) {
        val j = File(dir(c), "$id.json")
        val o = runCatching { JSONObject(j.readText()) }.getOrNull() ?: return
        f(o)
        j.writeText(o.toString())
    }

    /** TrueType (00 01 00 00, 'true'), OpenType ('OTTO') or a collection ('ttcf'). */
    internal fun looksLikeFont(b: ByteArray): Boolean {
        if (b.size < 12) return false
        val head = String(b, 0, 4, Charsets.ISO_8859_1)
        return head == "OTTO" || head == "true" || head == "ttcf" || (b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0)
    }

    /** The app font most like a font named [name] (손글씨·명조·코딩 by its name), for those who do not have it. */
    internal fun guessFallback(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("명조", "바탕", "serif", "myeongjo", "batang").any { it in n } -> "serif"
            listOf("손글씨", "펜", "붓", "hand", "script", "pen", "brush").any { it in n } -> "hand"
            listOf("mono", "coding", "코딩", "고정").any { it in n } -> "mono"
            else -> "sans"
        }
    }
}
