package com.bamnat.app.ui.recipe

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * Now a card gets each font as only the letters an example phone shows ([CHARS], Google Fonts' `text=`: about 0.2~0.3 MB for a Korean font, 20 KB for a Latin one), kept in the
 * 장터 cache folder and shared by every card that names that font. A letter outside [CHARS] (a name only that work uses) is drawn in
 * the phone's font, as Android does for any letter a font lacks. The whole font still comes down where the user picks or wears it.
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
            if (!regular.isFile) gate.withPermit {
                val bytes = listOf(400, 500, 300).firstNotNullOfOrNull { OpenFonts.subset(id, it, letters) } ?: return@withContext null
                keep(regular, bytes)
                OpenFonts.subset(id, 700, letters)?.let { keep(file(c, id, true), it) }
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
