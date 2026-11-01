package com.bamnat.app.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bamnat.core.recipe.FontCatalog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * [MAT-05][MAT-06] 1.36 글꼴: what each [FontCatalog] id is drawn in. The phone's own four are always there; the open fonts (SIL OFL·
 * Apache) come down from Google Fonts the first time one is drawn and are kept under `files/fonts/open` ([OpenFonts]), the text standing
 * in the closest phone font until then; the user's own fonts are files under `files/fonts`.
 *
 * PC 확인 2차 (daf5b9bd): Google Play services' font provider answered 'not found' in two seconds for 본명조 on a phone with Play
 * services and the internet, so the app no longer goes through it — it fetches the font files itself and can say why one failed.
 */
@android.annotation.SuppressLint("StaticFieldLeak") // only the application context (see init)
object AppFonts {
    private val cache = ConcurrentHashMap<String, FontFamily>()
    // The application context only (init takes applicationContext), so nothing short-lived is kept.
    @android.annotation.SuppressLint("StaticFieldLeak")
    @Volatile private var app: Context? = null

    /** Called once at start so drawing that has no context of its own (부품 글자) can still reach the user's font files. */
    fun init(c: Context) { app = c.applicationContext }

    /** [id]'s family with the context from [init]; without it a user font stands in as the sans. */
    fun family(id: String?): FontFamily? = family(app, id)

    /** [id]'s family, or null for the app's own (no font chosen). */
    fun family(context: Context?, id: String?): FontFamily? {
        if (id == null) return null
        cache[id]?.let { return it }
        val made = when (id) {
            "sans", "round" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            "hand" -> FontFamily.Cursive
            else -> FontCatalog.byId(id)?.takeIf { it.family != null }?.let { f ->
                // An open font: its files once they are down, else the closest phone font (not kept, so the files are used once there).
                val regular = context?.let { OpenFonts.file(it, id, bold = false) }
                if (regular == null) return standIn(f)
                val bold = OpenFonts.file(context, id, bold = true)
                runCatching {
                    FontFamily(listOfNotNull(androidx.compose.ui.text.font.Font(regular, FontWeight.Normal),
                        bold?.let { androidx.compose.ui.text.font.Font(it, FontWeight.Bold) }))
                }.getOrNull() ?: return standIn(f)
            } ?: context?.let { UserFonts.file(it, id) }?.let { f ->
                runCatching { FontFamily(androidx.compose.ui.text.font.Font(f)) }.getOrNull()
            } ?: family(context, context?.let { UserFonts.fallbackOf(it, id) } ?: "sans")
        } ?: FontFamily.SansSerif
        if (context != null || !FontCatalog.isUser(id)) cache[id] = made
        return made
    }

    fun forget(id: String) { cache.remove(id) }

    /** The phone font closest to an open font not down yet: 명조 → serif, 손글씨 → cursive, 코딩 → mono, else sans. */
    fun standIn(f: FontCatalog.Font): FontFamily = when (f.group) {
        FontCatalog.Group.SERIF -> FontFamily.Serif
        FontCatalog.Group.HAND -> FontFamily.Cursive
        FontCatalog.Group.MONO -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    /** The phone's own font id closest to [id] (for 비슷한 앱 글꼴로 쓰기). */
    fun standInId(id: String): String = when (FontCatalog.byId(id)?.group) {
        FontCatalog.Group.SERIF -> "serif"; FontCatalog.Group.HAND -> "hand"; FontCatalog.Group.MONO -> "mono"; else -> "sans"
    }

    /** Why the last try for an open font failed, in a line for the user. */
    val reasons = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** [MAT-05] PC 확인 1차: whether an open font has come down yet, shown as 받는 중… · 받았어요 · 못 받았어요 · 다시. */
    enum class Load { LOADING, READY, FAILED }

    /** Each open font's state since the app started; a font not here is not asked for yet. */
    val loads = androidx.compose.runtime.mutableStateMapOf<String, Load>()

    /** Whether [id] is one of the open fonts that come down (not the phone's own nor the user's). */
    fun downloads(id: String?): Boolean = id != null && FontCatalog.byId(id)?.family != null

    /**
     * Brings [id] down now (at most two minutes; 본명조 is 14 MB) and keeps it; every text in that font draws in it after. Offline, a font Google Fonts
     * does not have, or a full phone ends in [Load.FAILED] with the reason in [reasons].
     */
    suspend fun fetch(context: Context, id: String) {
        if (!downloads(id) || loads[id] == Load.LOADING) return
        if (OpenFonts.file(context, id, bold = false) != null) { loads[id] = Load.READY; return }
        loads[id] = Load.LOADING
        reasons.remove(id)
        val why = runCatching { kotlinx.coroutines.withTimeout(120_000) { OpenFonts.download(context, id) } }
            .fold({ it }, { if (it is kotlinx.coroutines.TimeoutCancellationException) "시간이 오래 걸려 멈췄어요" else OpenFonts.reasonOf(it) })
        forget(id)
        if (why == null) loads[id] = Load.READY else { reasons[id] = why; loads[id] = Load.FAILED }
    }

    /**
     * [MAT-05] PC 확인 1차 (사용성 제안 "견본을 그림으로 미리"): the sample line drawn in [id] ahead of time, so the list shows each
     * font before it comes down (0 for none). The pictures are made from the fonts' own files (all under the SIL OFL or Apache).
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    fun sampleImage(context: Context, id: String): Int = if (!downloads(id)) 0
        else context.resources.getIdentifier("font_sample_" + id.replace('-', '_'), "drawable", context.packageName)
}

/** [id]'s family for this screen, kept across recompositions; an open font not down yet starts coming down and is drawn once it is. */
@Composable
fun rememberFontFamily(id: String?, fetch: Boolean = true): FontFamily? {
    val ctx = LocalContext.current
    val load = id?.let { AppFonts.loads[it] }
    if (fetch && id != null && AppFonts.downloads(id) && load == null) androidx.compose.runtime.LaunchedEffect(id) { AppFonts.fetch(ctx, id) }
    return remember(id, load) { AppFonts.family(ctx, id) }
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
    data class Mine(val id: String, val name: String, val fallback: String, val open: Boolean, val bytes: Long, val received: Boolean = false)

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
            Mine(id, o.optString("name", "내 글꼴"), o.optString("fallback", "sans"), o.optBoolean("open"), File(dir(c), "$id.ttf").length(), o.optBoolean("received"))
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
        return addBytes(c, bytes, name?.substringBeforeLast('.'), open = false)
    }

    /**
     * [bytes] kept as a font named [name]: checked as a font the phone can read. [open] is what the user said of its licence, or
     * true for a font that came with a recipe (its sender said so and the server keeps only such fonts).
     */
    fun addBytes(c: Context, bytes: ByteArray, name: String?, open: Boolean, received: Boolean = false): Result {
        if (!looksLikeFont(bytes)) return Result.Problem("TTF·OTF 글꼴 파일이 아니에요")
        val id = idOf(bytes)
        file(c, id)?.let { list(c).firstOrNull { m -> m.id == id }?.let { m -> return Result.Added(m) } }
        if (list(c).size >= FontCatalog.USER_MAX_COUNT) return Result.Problem("내 글꼴은 ${FontCatalog.USER_MAX_COUNT}개까지 넣을 수 있어요. 안 쓰는 글꼴을 빼 주세요")
        val ttf = File(dir(c), "$id.ttf")
        ttf.writeBytes(bytes)
        val ok = runCatching { android.graphics.Typeface.Builder(ttf).build() != null }.getOrDefault(false)
        if (!ok) { ttf.delete(); return Result.Problem("이 글꼴은 휴대폰이 읽지 못해요") }
        val shown = name?.take(30)?.ifBlank { null } ?: "내 글꼴"
        val guess = guessFallback(shown)
        File(dir(c), "$id.json").writeText(JSONObject().put("name", shown).put("fallback", guess).put("open", open).put("received", received).toString())
        AppFonts.forget(id)
        return Result.Added(Mine(id, shown, guess, open, bytes.size.toLong(), received))
    }

    /** "u" + the first 16 hex of the file's SHA-256: the same on every phone and on the server. */
    fun idOf(bytes: ByteArray): String = "u" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)

    /** Whether [id] was already sent to the server (so it is not sent again). */
    fun sent(c: Context, id: String): Boolean = runCatching { JSONObject(File(dir(c), "$id.json").readText()).optBoolean("sent") }.getOrDefault(false)
    fun markSent(c: Context, id: String) = edit(c, id) { it.put("sent", true) }

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
