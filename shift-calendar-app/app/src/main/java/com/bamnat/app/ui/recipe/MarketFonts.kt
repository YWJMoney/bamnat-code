package com.bamnat.app.ui.recipe

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import com.bamnat.app.ui.theme.AppFonts
import com.bamnat.app.ui.theme.OpenFonts
import com.bamnat.core.recipe.FontCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 1.39 [MKP-06] 장터 카드 글꼴 (사용자 2026-11-08 "아직도 장터 로딩이 너무 느려"). A 장터 card used to start bringing down every open font its
 * work names, whole (주아 2 MB, 고운바탕 8 MB, 본명조 14 MB, and the bold too), while the card itself was drawn in the closest phone font
 * anyway; a page of 테마 pulled megabytes before its pictures were done, and the fonts named by 부품 never showed on a card at all.
 *
 * Now a card gets each font as only the letters an example phone shows ([CHARS]: about 0.1~0.2 MB for a Korean font, 20 KB for a Latin one), kept in the
 * 장터 cache folder and shared by every card that names that font. A letter outside [CHARS] (a name only that work uses) is drawn in
 * the phone's font, as Android does for any letter a font lacks. The whole font still comes down where the user picks or wears it.
 *
 * 2.0.0 [RVM-21] MKT-32 (사용자 2026-11-12 카드 '우리 쪽에서 글꼴 내주기': 장터 모습은 그대로, Google 요청은 글꼴을 고를 때만): these small
 * fonts no longer come from Google Fonts. They are made once from the same open fonts (tools/card_fonts.py: renamed 'BN Card NN' as the
 * SIL OFL asks of a changed copy, their licenses beside them) and put on our public files (GitHub Pages, `bamnat.remoteBaseUrl`
 * `remote/card-fonts/<version>/`); a file is used only when its bytes are the ones this app was built with ([CardFontPins]). A build
 * without public files (the PC's debug build) or a font not there draws in the phone's closest font, as offline.
 */
internal object MarketFonts {
    private const val ASCII = "!\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
    private const val HANGUL =
        "가각간갈감값강같개거건걷걸검것게겨격견결겼경계고곧골곳공과관교구국규균그근금급기길김깊까깬꼭꿀끄끝나난날남났낮내낼넘넣네녁년노누눈눠는늘니님다단달당닿대더덜덟독" +
        "돈돌동된두둘뒤드득든듣들디때떠뗀락람래랜량러런레려력렸령로록료루류률르른를름리린림마막만많말맞매먹먼메며면명모목못무문묻물바밖반받발밤밥방배밸벅번벌범법벨변별볍" +
        "보복본볼봉부분불붙블비빌빨사산상새서석선설섯세셔소속손수순숫쉬쉰쉼스습시식신실심싱썼쓰쓴쓸아안앉않알암압았앞액앱야약양어언얼업었에엔여역연열영예오온올와외요용우" +
        "운울움원월웠위유율으은을음의이익인일읽입있자작잔잘잠잤장재저적전절점정제젝젯조종좋좌주준줄중증지직진집짜짧째쪽찍차찰참찾채책챙처천청체초총최추출충치칙칠침칩칭카" +
        "칸커켜켰크킴타태터턱텀토통퇴투트특튼틀판펼평포폰폼표푹풀품프픈피필하한할함합항해했현호화확환황회획횟후휴흐흡"

    /**
     * Every letter the example phone ([ShotPhone]) draws for the 출시 작품, all kinds of work and faces (MarketCharsTest keeps it so):
     * digits, Latin letters and signs, and the Hangul it shows.
     */
    const val CHARS = ASCII + "·…→←↑↓※%₩×÷−–—°★☆♥○●◆□■△▲▽▼✓✔‘’“”‹›«»▾▴☀☐☑☕⚖⛑✅" + HANGUL


    /** Whether [c] is kept in a card font (a letter or sign a font draws: not a space, a control or half of an emoji). */
    fun drawn(c: Char): Boolean = !c.isWhitespace() && !c.isISOControl() && !c.isSurrogate() && c !in '\uFE00'..'\uFE0F' && c != '\u200D'

    /** [CHARS] as sent: once each, in order, so the same letters always make the same file. */
    private val letters: String by lazy { CHARS.toSortedSet().joinToString("") }
    /** A file name part that changes when [CHARS] does, so a new list of letters brings new files. */
    private val version: String by lazy { Integer.toHexString(letters.hashCode()) }

    private val FONT = Regex("\"\\w*[Ff]ont\"\\s*:\\s*\"([a-z0-9]{2,40})\"")
    private val LOOK_FONT = Regex("(?:^|[|~/\"])f[ft]:([a-z0-9]{2,40})")

    /** The open fonts (those that come down) [body] names, for its parts (`"font"`), screens (`ft:`) and dates (`ff:`). */
    fun ids(body: String): Set<String> = (FONT.findAll(body).map { it.groupValues[1] } + LOOK_FONT.findAll(body).map { it.groupValues[1] })
        .filter { AppFonts.downloads(it) }.toSet()

    private val families = ConcurrentHashMap<String, FontFamily>()
    /** Fonts that could not be had this time the app runs (offline, no such family): not asked again for each card. */
    private val failed = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val gate = Semaphore(2)
    private const val RETRY_MS = 5 * 60_000L

    /** 2.0.0 [RVM-21] MKT-32 Our public files ([com.bamnat.app.BuildConfig.REMOTE_BASE_URL]); blank: no card fonts at all. */
    @androidx.annotation.VisibleForTesting internal var base: String = com.bamnat.app.BuildConfig.REMOTE_BASE_URL
    /** The bytes at an address (a plain GET, at most [MAX_BYTES]), or null. Swapped in tests to count where the 장터 asks. */
    @androidx.annotation.VisibleForTesting internal var get: (String) -> ByteArray? = ::download
    /** The sha256 of every card font this app takes, by "<id>-r"·"<id>-b". */
    @androidx.annotation.VisibleForTesting internal var pins: Map<String, String> = CardFontPins.SHA
    private const val MAX_BYTES = 1_000_000

    /** Where card font [id] ([bold] or not) is on our public files, or null when this build has none or the letters are not the ones made. */
    internal fun address(id: String, bold: Boolean): String? =
        base.takeIf { it.isNotBlank() && version == CardFontPins.VERSION }?.let { it.trimEnd('/') + "/remote/card-fonts/$version/$id-${if (bold) "b" else "r"}.ttf" }

    /** Card font [id] from our public files when it is the one made for this app ([pins]), else null. */
    private fun fetch(id: String, bold: Boolean): ByteArray? {
        val sha = pins["$id-${if (bold) "b" else "r"}"] ?: return null
        val url = address(id, bold) ?: return null
        val bytes = get(url) ?: return null
        val got = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return bytes.takeIf { got == sha && com.bamnat.app.ui.theme.UserFonts.looksLikeFont(it) }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = false
        try {
            if (conn.responseCode != 200 || conn.contentLengthLong > MAX_BYTES) return@runCatching null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun dir(c: Context) = File(c.cacheDir, "market/font")
    private fun file(c: Context, id: String, bold: Boolean) = File(dir(c), "$id-$version-${if (bold) "b" else "r"}.ttf")

    /**
     * The fonts of [ids] a card draws in, each by its plain id: the small one, when the phone does not have the whole font (that one is
     * drawn as it is anywhere). A font not had stays out (the phone's closest is drawn), and [complete] says the picture is not kept.
     */
    suspend fun ready(c: Context, ids: Set<String>): Map<String, FontFamily> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, FontFamily>()
        for (id in ids) {
            if (OpenFonts.file(c, id, bold = false) != null) continue
            (families[id] ?: load(c, id))?.let { out[id] = it }
        }
        return out
    }

    /** Whether every font of [ids] is on the phone, whole or as a card font, so a card drawn now shows its real letters. */
    fun complete(c: Context, ids: Set<String>): Boolean = ids.all { OpenFonts.file(c, it, bold = false) != null || families[it] != null }

    private suspend fun load(c: Context, id: String): FontFamily? = locks.getOrPut(id) { Mutex() }.withLock {
        families[id]?.let { return it }
        failed[id]?.let { at -> if (System.nanoTime() / 1_000_000 - at < RETRY_MS) return null }
        val got = withContext(Dispatchers.IO) {
            val regular = file(c, id, false)
            // 2.0.0 [RVM-21] MKT-32 With Android's 데이터 절약 on a metered network, the cards keep to the phone's fonts (a page of 테마
            // names about 20 fonts, 3~4 MB); they come on Wi-Fi.
            if (!regular.isFile && saving(c)) return@withContext null
            if (!regular.isFile) gate.withPermit {
                // 2.0.0 [RVM-21] MKT-32 From our public files, never Google Fonts (the regular weight was made from 400, else 500 or 300).
                val bytes = fetch(id, bold = false) ?: return@withContext null
                keep(regular, bytes)
                fetch(id, bold = true)?.let { keep(file(c, id, true), it) }
            }
            runCatching {
                val bold = file(c, id, true).takeIf { it.isFile }
                FontFamily(listOfNotNull(Font(regular, FontWeight.Normal), bold?.let { Font(it, FontWeight.Bold) }))
            }.getOrNull()
        }
        if (got == null) { failed[id] = System.nanoTime() / 1_000_000; return null }
        families[id] = got
        AppFonts.cards[AppFonts.CARD + id] = got
        got
    }

    /** Android's 데이터 절약 is on for this app and the network is metered. */
    private fun saving(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(android.net.ConnectivityManager::class.java)
        cm.isActiveNetworkMetered && cm.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(false)

    private fun keep(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) tmp.delete()
    }

    /** A blueprint's lines with each font this card has as its card font ("card:<id>", [AppFonts.CARD]), for [MockPhone]'s parts. */
    fun lines(lines: List<Map<String, Any?>>, fonts: Map<String, FontFamily>): List<Map<String, Any?>> =
        if (fonts.isEmpty()) lines else lines.map { l -> (l["font"] as? String)?.takeIf { it in fonts }?.let { l + ("font" to AppFonts.CARD + it) } ?: l }

    /** For tests: the fonts this run made. */
    internal fun forget() { families.clear(); failed.clear(); AppFonts.cards.clear() }
}

/**
 * 2.0.0 [RVM-21] MKT-32 (사용자 2026-11-12 '장터 모습은 그대로, Google 요청은 글꼴을 고를 때만'): inside the 장터 (목록·작품 상세·
 * 입어 보기·테마 창·만든 분 쪽) and while a theme is tried on the real app, every open font is drawn as a card font from our public files
 * or the phone's closest one; the whole font (from Google Fonts) comes only when the user picks a font or wears or puts in the work.
 * [ids] (the fonts of the work shown) are brought as card fonts and drawn in once they are here; a card inside keeps its own.
 */
@androidx.compose.runtime.Composable
internal fun MarketFontsOf(ids: Set<String>, content: @androidx.compose.runtime.Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val fonts by androidx.compose.runtime.produceState(emptyMap<String, FontFamily>(), ids) { value = MarketFonts.ready(ctx.applicationContext, ids) }
    val outer = com.bamnat.app.ui.theme.LocalCardFonts.current
    val both = androidx.compose.runtime.remember(outer, fonts) { outer.orEmpty() + fonts }
    androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.theme.LocalCardFonts provides both) { content() }
}

/** [MarketFontsOf] for [r] (null: no work of its own, only no whole fonts). */
@androidx.compose.runtime.Composable
internal fun MarketFontsOf(r: com.bamnat.core.recipe.Recipe?, content: @androidx.compose.runtime.Composable () -> Unit) =
    MarketFontsOf(androidx.compose.runtime.remember(r) { r?.let { MarketFonts.ids(com.bamnat.core.recipe.RecipeCodec.write(it)) }.orEmpty() }, content)
