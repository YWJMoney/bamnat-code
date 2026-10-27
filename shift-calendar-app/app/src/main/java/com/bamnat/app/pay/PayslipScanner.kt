package com.bamnat.app.pay

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.bamnat.app.imports.ImagePrep
import com.bamnat.core.imports.PhotoQuality
import com.bamnat.core.imports.PhotoVariants
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.FillVote
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipVote
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [PAYS-05][PAYX-04] Reads the text of a payslip or a page of rules from a photo, on the phone: ML Kit's Korean
 * model, which Google Play services keeps on the phone ([OcrModel]), so the photo and its text never leave the
 * device. Lines are put back on their rows
 * ([OcrRows]) so a table reads name by amount.
 * [READ-07] 1.25: the photo is made upright and no bigger than reading needs first ([ImagePrep]), and each line keeps
 * its slope, so a payslip photographed 2~4° off is put back row by row (기본급 beside its own amount, not the next row's).
 * [READ-13] 1.27 순번 446: read as taken and made over several ways ([ImagePrep.variant]), the readings put together
 * line by line ([SlipVote]: an amount read two ways is asked) and fill by fill for a 체결 캡처 ([FillVote]).
 * 1.30 AV (사용자 2026-10-27 "사진마다 읽는 부분이 다르다"): a photo read on its side is turned and read again ([PHOTO-25]);
 * a monitor's moiré is smoothed before every made-over reading ([PHOTO-25]); small letters are worked at the photo's own size
 * and read bigger ([PHOTO-21]); the company's payslip as the user confirmed it before ([SlipTemplate], [ReadFixBook]) fills in
 * what this photo lost ([PHOTO-24]); a page through the scanner counts as straight ([PHOTO-20]).
 */
object PayslipScanner {
    /**
     * One photo read: the text to show or read rules from ([text], the best reading), the payslip put together from all
     * readings ([slip]), the 체결 put together ([fills]), a screen-sized copy of the photo for "사진과 같은가요?" (kept while
     * the screen is open only), and whether the photo is good enough ([quality]).
     */
    class Scan(
        val text: String, val texts: List<String>, val slip: SlipVote.Result, val fills: List<FillSheetReader.Fill>,
        val image: Bitmap?, val quality: PhotoQuality.Report?,
        /**
         * [PHOTO-24] 1.30: the words of the photo as taken, with their boxes (the first reading): what the company's payslip
         * template is learned from once the user confirms the lines ([ReadFixBook.learnTemplate]). In memory only.
         */
        val boxes: List<OcrLine> = emptyList(),
        /** [PHOTO-26] 1.30: the photos put together, one or more ([combine]). */
        val pages: Int = 1,
    ) {
        val ways: Int get() = texts.size
    }

    /**
     * [PHOTO-26] 1.30 AV: two photos of one long payslip put together ([SlipReading.pages]): the lines of both, a line both
     * show once. The first photo stays the one shown; its words stay what a template is learned from.
     */
    fun combine(first: Scan, next: Scan): Scan {
        val slip = SlipReading.pages(listOf(first.slip, next.slip))
        val text = listOf(first.text, next.text).filter { it.isNotBlank() }.joinToString("\n")
        val quality = listOfNotNull(first.quality, next.quality).minByOrNull { it.score }
        return Scan(text, first.texts + next.texts, slip, first.fills + next.fills, first.image ?: next.image, quality, first.boxes, first.pages + next.pages)
    }

    /** Readings the tests give instead of ML Kit (one text per made-over picture), which does not run on a computer. */
    @VisibleForTesting
    var fake: (suspend (Uri) -> List<String>)? = null

    /** The picture behind [fake]'s texts. */
    @VisibleForTesting
    var fakeImage: (() -> Bitmap)? = null

    /** Photos taken for reading go here and are deleted once read (FileProvider path "scan"). */
    fun scanFile(context: Context): File = File(context.cacheDir, "scan").apply { mkdirs() }.resolve("payslip.jpg")

    suspend fun read(context: Context, uri: Uri): String = scan(context, uri).text

    /**
     * [progress] hears (reading n, of m) for "n/m번째로 읽는 중"; m is 0 until the photo's look is known. [scanned]: the page
     * came through the document scanner, straightened ([PHOTO-20]).
     */
    suspend fun scan(context: Context, uri: Uri, scanned: Boolean = false, progress: (Int, Int) -> Unit = { _, _ -> }): Scan = withContext(Dispatchers.Default) {
        // [PHOTO-24] The company's payslips the user confirmed before (on the phone only).
        val templates = runCatching { com.bamnat.app.data.ReadFixBook.get(context).templates() }.getOrDefault(emptyList())
        fake?.let { f -> return@withContext put(f(uri), fakeImage?.invoke(), null, fakeBoxes?.invoke() ?: emptyList(), templates) }
        var photo = ImagePrep.load(context, uri)
        progress(1, 0)
        var first = OcrModel.read(context, InputImage.fromBitmap(photo, 0))
        var firstLines = lines(first)
        // [PHOTO-25] 1.30: read on its side (few letters in tall boxes): a quarter turn each way, the one with more words kept.
        if (OcrRows.sideways(firstLines)) {
            for (deg in listOf(90f, 270f)) {
                val turned = runCatching { ImagePrep.turned(photo, deg) }.getOrNull() ?: continue
                val t = runCatching { OcrModel.read(context, InputImage.fromBitmap(turned, 0)) }.getOrNull()
                val l = t?.let(::lines).orEmpty()
                if (t != null && words(l) > words(firstLines) * 2 && !OcrRows.sideways(l)) {
                    if (photo !== turned) photo.recycle()
                    photo = turned; first = t; firstLines = l
                    break
                }
                turned.recycle()
            }
        }
        val texts = mutableListOf(join(first, firstLines))
        // [PHOTO-01] 1.29: each reading's words with their boxes, for reading the payslip as the table it is (SlipReading).
        val boxes = mutableListOf(words(first))
        val letter = PhotoVariants.median(firstLines.map { (it.bottom - it.top).coerceAtLeast(1) })
        val plan = ImagePrep.plan(photo, letter)
        val box = if (firstLines.isEmpty()) null else android.graphics.Rect(firstLines.minOf { it.left }, firstLines.minOf { it.top },
            firstLines.maxOf { it.right }, firstLines.maxOf { it.bottom })
        val more = plan.kinds.filter { it != PhotoVariants.Kind.ORIG }
        more.forEachIndexed { i, kind ->
            progress(i + 2, more.size + 1)
            val v = ImagePrep.variant(photo, kind, box, letter, plan.moire) ?: return@forEachIndexed
            val t = runCatching { OcrModel.read(context, InputImage.fromBitmap(v.bitmap, 0)) }.getOrNull()
            if (v.bitmap !== photo) v.bitmap.recycle()
            // Each reading's words go back on the photo's own pixels, so a doubled crop's letters measure as the photo's.
            if (t != null) join(t, lines(t)).takeIf { it.isNotBlank() }?.let { texts += it; boxes += onPhoto(words(t), v) }
        }
        val (tilt, keystone) = OcrRows.lean(firstLines)
        val quality = PhotoQuality.of(plan.stats, letter, words(firstLines), tilt, scanned = scanned, keystone = keystone)
        put(texts, ImagePrep.forScreen(photo).first, quality, boxes, templates)
    }

    private fun words(lines: List<OcrLine>): Int = lines.sumOf { l -> l.text.split(' ').count { it.isNotBlank() } }

    /** [PHOTO-24] The words with boxes the tests give with [fake]'s texts (one list a reading), or none. */
    @VisibleForTesting
    var fakeBoxes: (() -> List<List<OcrLine>>)? = null

    /** The readings put together. */
    @VisibleForTesting
    internal fun put(
        texts: List<String>, image: Bitmap?, quality: PhotoQuality.Report?, boxes: List<List<OcrLine>> = emptyList(),
        templates: List<com.bamnat.core.pay.SlipTemplate> = emptyList(),
    ): Scan {
        val read = texts.filter { it.isNotBlank() }
        // [PHOTO-01][PHOTO-03] 1.29: with the words' boxes, each reading is read as a table and as text, the better kept, the
        // readings voted and checked against the payslip's own sums; text alone (the tests' fake) is read as 1.28 did.
        val slip = if (boxes.any { it.isNotEmpty() }) SlipReading.read(boxes, templates) else SlipVote.merge(read)
        val fills = FillVote.merge(read.map { FillSheetReader.read(it) })
        // The text shown and read for rules: the payslip's own reading when it has amounts, else the one most rules read
        // from (a page of 취업규칙), more Korean letters on a tie.
        val text = if (slip.read.lines.isNotEmpty()) slip.text
            else read.maxWithOrNull(compareBy<String> { RuleText.read(it).size }.thenBy { t -> t.count { it in '가'..'힣' } }) ?: ""
        return Scan(text, read, slip, fills, image, quality, boxes.firstOrNull().orEmpty())
    }

    private fun lines(text: Text): List<OcrLine> = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
        val box = l.boundingBox ?: return@mapNotNull null
        OcrLine(l.text, box.left, box.top, box.right, box.bottom, angle = l.angle)
    }

    /** ML Kit's words (elements), each with its box and its line's slope. */
    private fun words(text: Text): List<OcrLine> = text.textBlocks.flatMap { it.lines }.flatMap { l ->
        l.elements.mapNotNull { e ->
            val b = e.boundingBox ?: return@mapNotNull null
            OcrLine(e.text, b.left, b.top, b.right, b.bottom, angle = l.angle)
        }
    }

    /** [words] read on made-over picture [v], put back on the photo's pixels. */
    @VisibleForTesting
    internal fun onPhoto(words: List<OcrLine>, v: ImagePrep.Variant): List<OcrLine> =
        words.map { it.copy(left = v.x(it.left), top = v.y(it.top), right = v.x(it.right), bottom = v.y(it.bottom)) }

    private fun join(text: Text, lines: List<OcrLine>): String = if (lines.isEmpty()) text.text else OcrRows.join(lines)

    /** Deletes a photo taken for reading. */
    fun clear(context: Context) {
        runCatching { scanFile(context).delete() }
    }
}
