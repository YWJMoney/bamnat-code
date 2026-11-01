package com.bamnat.app.ui.theme

import android.content.Context
import com.bamnat.core.recipe.FontCatalog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [MAT-05] 1.36 공개 글꼴 받기 (PC 확인 2차). The open fonts of [FontCatalog] (SIL OFL·Apache) come from Google Fonts: the CSS API names
 * the TrueType file of a family and weight, and the file is kept under `files/fonts/open/<id>-<regular|bold>.ttf` for good. Only the
 * family's name goes out (no device id, nothing of the user's); Google sees the phone's address as for any web request.
 */
object OpenFonts {
    private const val CSS = "https://fonts.googleapis.com/css2?family="
    /** The CSS API answers with TrueType (not WOFF2) to an old browser's name, which Android can read. */
    private const val AGENT = "Mozilla/4.0"
    /** The biggest Korean families are about 10 MB a weight. */
    private const val MAX_BYTES = 24 * 1024 * 1024
    private val HOSTS = setOf("fonts.gstatic.com")
    private const val BOLD_UP_TO = 6 * 1024 * 1024

    private fun dir(c: Context) = File(c.filesDir, "fonts/open").apply { mkdirs() }

    private fun name(id: String, bold: Boolean) = "$id-${if (bold) "bold" else "regular"}.ttf"

    /** The kept file of [id] (regular or bold), or null while it is not down. */
    fun file(c: Context, id: String, bold: Boolean): File? =
        if (FontCatalog.byId(id)?.family == null) null else File(dir(c), name(id, bold)).takeIf { it.isFile && it.length() > 0 }

    /** Every kept open font, for 저장 공간 and tests. */
    fun kept(c: Context): List<File> = dir(c).listFiles().orEmpty().filter { it.extension == "ttf" }

    /**
     * Brings [id] down: the regular weight (or the family's nearest, some have no 400) and, if there is one, the bold. Null when the
     * regular is kept; else the reason for the user.
     */
    suspend fun download(c: Context, id: String, progress: (Long, Long) -> Unit = { _, _ -> }): String? = withContext(Dispatchers.IO) {
        val family = FontCatalog.byId(id)?.family ?: return@withContext "이 글꼴은 받을 수 없어요"
        var last: String? = null
        val regular = listOf(400, 500, 300).firstNotNullOfOrNull { w -> fetchWeight(family, w, progress).also { (_, why) -> last = why ?: last }.first }
            ?: return@withContext last ?: "이 글꼴을 찾지 못했어요"
        save(c, id, false, regular)?.let { return@withContext it }
        // The bold is a bonus: without it the phone makes the regular heavier. A big Korean family (본명조 is 14 MB a weight) keeps
        // to its regular, so a font never costs the phone twice that.
        if (regular.size <= BOLD_UP_TO) fetchWeight(family, 700).first?.let { save(c, id, true, it) }
        null
    }

    private fun save(c: Context, id: String, bold: Boolean, bytes: ByteArray): String? = try {
        val out = File(dir(c), name(id, bold))
        val tmp = File(out.path + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(out)) { tmp.delete(); "저장하지 못했어요" } else null
    } catch (e: IOException) {
        "휴대폰 저장 공간이 모자라요"
    }

    /** One weight's file, or null and why. */
    private fun fetchWeight(family: String, weight: Int, progress: (Long, Long) -> Unit = { _, _ -> }): Pair<ByteArray?, String?> {
        val css = get(CSS + URLEncoder.encode(family, "UTF-8").replace("+", "%20") + ":wght@" + weight, 64 * 1024)
            ?: return null to "이 글꼴을 찾지 못했어요"
        val url = Regex("""url\((https://[^)]+)\)""").find(String(css, Charsets.UTF_8))?.groupValues?.get(1)
            ?: return null to "이 글꼴을 찾지 못했어요"
        if (URL(url).host !in HOSTS) return null to "이 글꼴을 찾지 못했어요"
        val bytes = get(url, MAX_BYTES, progress) ?: return null to "글꼴 파일을 받지 못했어요"
        if (!UserFonts.looksLikeFont(bytes)) return null to "받은 파일이 글꼴이 아니에요"
        return bytes to null
    }

    /** The body of a 200 answer at most [max] bytes long, null for any other answer; network trouble is thrown. */
    private fun get(url: String, max: Int, progress: (Long, Long) -> Unit = { _, _ -> }): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", AGENT)
        return try {
            if (conn.responseCode != 200) return null
            val total = conn.contentLengthLong
            if (total > max) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(if (total > 0) total.toInt() else 64 * 1024)
                val buf = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    read += n
                    if (read > max) return null
                    out.write(buf, 0, n)
                    progress(read, total)
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Why a try failed, for the user; [time] is true when the phone's date looks wrong (the server's certificate is not valid yet or any more). */
    data class Reason(val text: String, val time: Boolean = false)

    /**
     * A thrown failure in a line for the user. PC 확인 3차: with the phone's date ten days ahead the secure connection failed and the
     * user only read 'could not', so the whole chain of causes is looked through for a certificate or date problem.
     */
    fun reasonOf(e: Throwable): Reason {
        val chain = generateSequence(e) { it.cause }.take(8).toList()
        val time = chain.any { it is java.security.cert.CertificateExpiredException || it is java.security.cert.CertificateNotYetValidException } ||
            chain.any { c -> c.message.orEmpty().let { m -> listOf("not yet valid", "expired", "NotBefore", "NotAfter", "validity").any { it in m } } }
        return when {
            time -> Reason("휴대폰 날짜·시간이 맞지 않아 보안 연결을 못 맺었어요. 날짜·시간을 확인해 주세요", time = true)
            chain.any { it is java.security.cert.CertificateException || it is javax.net.ssl.SSLException || it is java.security.cert.CertPathValidatorException } ->
                Reason("보안 연결을 맺지 못했어요. 휴대폰 날짜·시간이 맞는지 확인해 주세요", time = true)
            chain.any { it is UnknownHostException } -> Reason("인터넷에 연결되지 않았어요")
            chain.any { it is SocketTimeoutException } -> Reason("인터넷이 느려 받지 못했어요")
            chain.any { it is IOException } -> Reason("인터넷 연결이 끊겼어요")
            else -> Reason("글꼴을 받지 못했어요 (${e.javaClass.simpleName})")
        }
    }
}
