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
    ) {
        val ways: Int get() = texts.size
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

    /** [progress] hears (reading n, of m) for "n/m번째로 읽는 중"; m is 0 until the photo's look is known. */
    suspend fun scan(context: Context, uri: Uri, progress: (Int, Int) -> Unit = { _, _ -> }): Scan = withContext(Dispatchers.Default) {
        fake?.let { f -> return@withContext put(f(uri), fakeImage?.invoke(), null) }
        val photo = ImagePrep.load(context, uri)
        progress(1, 0)
        val first = OcrModel.read(context, InputImage.fromBitmap(photo, 0))
        val firstLines = lines(first)
        val texts = mutableListOf(join(first, firstLines))
        // [PHOTO-01] 1.29: each reading's words with their boxes, for reading the payslip as the table it is (SlipReading).
        val boxes = mutableListOf(words(first))
        val letter = PhotoVariants.median(firstLines.map { (it.bottom - it.top).coerceAtLeast(1) })
        val (stats, kinds) = ImagePrep.plan(photo, letter)
        val box = if (firstLines.isEmpty()) null else android.graphics.Rect(firstLines.minOf { it.left }, firstLines.minOf { it.top },
            firstLines.maxOf { it.right }, firstLines.maxOf { it.bottom })
        val more = kinds.filter { it != PhotoVariants.Kind.ORIG }
        more.forEachIndexed { i, kind ->
            progress(i + 2, more.size + 1)
            val v = ImagePrep.variant(photo, kind, box) ?: return@forEachIndexed
            val t = runCatching { OcrModel.read(context, InputImage.fromBitmap(v.bitmap, 0)) }.getOrNull()
            if (v.bitmap !== photo) v.bitmap.recycle()
            // Each reading's words go back on the photo's own pixels, so a doubled crop's letters measure as the photo's.
            if (t != null) join(t, lines(t)).takeIf { it.isNotBlank() }?.let { texts += it; boxes += onPhoto(words(t), v) }
        }
        val quality = PhotoQuality.of(stats, letter, firstLines.sumOf { l -> l.text.split(' ').count { it.isNotBlank() } }, 0.0)
        put(texts, ImagePrep.forScreen(photo).first, quality, boxes)
    }

    /** The readings put together. */
    @VisibleForTesting
    internal fun put(texts: List<String>, image: Bitmap?, quality: PhotoQuality.Report?, boxes: List<List<OcrLine>> = emptyList()): Scan {
        val read = texts.filter { it.isNotBlank() }
        // [PHOTO-01][PHOTO-03] 1.29: with the words' boxes, each reading is read as a table and as text, the better kept, the
        // readings voted and checked against the payslip's own sums; text alone (the tests' fake) is read as 1.28 did.
        val slip = if (boxes.any { it.isNotEmpty() }) SlipReading.read(boxes) else SlipVote.merge(read)
        val fills = FillVote.merge(read.map { FillSheetReader.read(it) })
        // The text shown and read for rules: the payslip's own reading when it has amounts, else the one most rules read
        // from (a page of 취업규칙), more Korean letters on a tie.
        val text = if (slip.read.lines.isNotEmpty()) slip.text
            else read.maxWithOrNull(compareBy<String> { RuleText.read(it).size }.thenBy { t -> t.count { it in '가'..'힣' } }) ?: ""
        return Scan(text, read, slip, fills, image, quality)
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
