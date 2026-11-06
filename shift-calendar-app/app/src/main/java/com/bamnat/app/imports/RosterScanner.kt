package com.bamnat.app.imports

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.bamnat.app.BuildConfig
import com.bamnat.app.pay.OcrModel
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.PhotoQuality
import com.bamnat.core.imports.PhotoVariants
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterWord
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * [SHFT-06] Reads the words of a duty roster photo on the phone with ML Kit's Korean model, which Google
 * Play services keeps on the phone ([OcrModel]): the photo and its text never leave the device. Each word keeps
 * its box so a code can be put under the day it sits beneath ([com.bamnat.core.imports.DutyRoster.read]).
 * [READ-09] 1.25 순번 370: the photo is first made upright and no bigger than reading needs ([ImagePrep]); when the first
 * reading finds a tilted table or no table, the photo turned level with its contrast stretched is read again and the
 * better reading kept. Each word carries its line's slope, so the table's tilt is measured from what was read.
 */
object RosterScanner {
    /**
     * One photo read: the words, what the engine made of them, and the picture they belong to (a screen-sized copy,
     * [scale] from it to the words' pixels) for 사진 위에서 누르기 and 무엇을 읽었는지 보기. Kept while the screen is open only.
     */
    class Scan(
        val words: List<RosterWord>, val read: RosterPhotoRead, val image: Bitmap?, val scale: Float,
        /** [READ-14] Whether the photo is good enough to read well, and why not. */
        val quality: PhotoQuality.Report? = null,
        /** [READ-13] How many ways the photo was read and put together. */
        val ways: Int = 1,
    )

    /** Words the tests give instead of ML Kit, which does not run on a computer (Robolectric). */
    @VisibleForTesting
    var fake: (suspend (Uri) -> List<RosterWord>)? = null

    /**
     * [READ-13] Several readings of one photo the tests give (as the made-over pictures would read), put together as the
     * phone does. Wins over [fake].
     */
    @VisibleForTesting
    var fakeReads: (suspend (Uri) -> List<List<RosterWord>>)? = null

    /** The picture behind [fake]'s words, for screenshots of 사진 위에서 누르기. */
    @VisibleForTesting
    var fakeImage: (() -> Bitmap)? = null

    /**
     * [READ-13] 1.27 순번 446 (사용자 "사진 인식 기술을 비약적으로 향상"): the photo read as taken, then made over several ways
     * ([ImagePrep.variant]: light evened out, contrast, black and white, the table twice the size, a dark screen turned
     * light) and read again, and the readings put together cell by cell ([RosterVote]): a cell lost once comes back, a
     * cell read two ways is yellow. [progress] hears (reading n, of m) for "n/m번째로 읽는 중".
     */
    suspend fun scan(context: Context, uri: Uri, codes: DutyCodes, progress: (Int, Int) -> Unit = { _, _ -> }): Scan = withContext(Dispatchers.Default) {
        fakeReads?.let { f ->
            val reads = f(uri)
            val analysed = reads.map { DutyRoster.analyse(it, codes) }
            val words = reads.getOrElse(RosterVote.anchor(analysed)) { emptyList() }
            return@withContext Scan(words, RosterVote.merge(analysed), fakeImage?.invoke(), 1f, ways = reads.size)
        }
        fake?.let { f ->
            val words = f(uri)
            return@withContext Scan(words, DutyRoster.analyse(words, codes), fakeImage?.invoke(), 1f)
        }
        scanPicture(context, ImagePrep.load(context, uri), codes, progress)
    }

    /** [READ-32] 1.38: a picture already in memory (a scanned PDF's page, [AnyFile.render]) read as a photo is. */
    suspend fun scanPicture(context: Context, photo: Bitmap, codes: DutyCodes, progress: (Int, Int) -> Unit = { _, _ -> }): Scan = withContext(Dispatchers.Default) {
        progress(1, 0)
        val first = words(OcrModel.read(context, photo))
        val firstRead = DutyRoster.analyse(first, codes)
        var base = photo
        var leveled = false
        val words = mutableListOf(first)
        val reads = mutableListOf(firstRead)
        if (firstRead.problem != null || abs(firstRead.tilt) >= SECOND_LOOK_TILT) {
            val level = ImagePrep.leveled(photo, firstRead.tilt)
            val again = runCatching { words(OcrModel.read(context, level.bitmap)) }.getOrNull()
            if (again != null) {
                val againRead = DutyRoster.analyse(again, codes)
                // The level picture's words sit elsewhere than the photo's: one of the two is read on, not both.
                if (score(againRead) > score(firstRead)) {
                    base = level.bitmap; leveled = true
                    words[0] = again; reads[0] = againRead
                }
            }
        }
        val lead = words[0]
        val letter = PhotoVariants.median(lead.map { it.height })
        val (stats, kinds) = ImagePrep.plan(base, letter)
        val box = if (lead.isEmpty()) null else android.graphics.Rect(lead.minOf { it.left }, lead.minOf { it.top }, lead.maxOf { it.right }, lead.maxOf { it.bottom })
        // The level picture is already grey with its contrast stretched.
        val more = kinds.filter { it != PhotoVariants.Kind.ORIG && !(leveled && it == PhotoVariants.Kind.CONTRAST) }
        more.forEachIndexed { i, kind ->
            progress(i + 2, more.size + 1)
            val v = ImagePrep.variant(base, kind, box) ?: return@forEachIndexed
            val got = runCatching { words(OcrModel.read(context, v.bitmap)) }.getOrNull()
            if (v.bitmap !== base) v.bitmap.recycle()
            if (got.isNullOrEmpty()) return@forEachIndexed
            val back = got.map { w -> w.copy(left = v.x(w.left), top = v.y(w.top), right = v.x(w.right), bottom = v.y(w.bottom)) }
            words += back
            reads += DutyRoster.analyse(back, codes)
        }
        // [READ-35] 1.38: the page read once more band by band, each band a few rows high made bigger, when the vote still has
        // cells to look at (a lone D·E·N·O the whole page's readings lost is a big letter in its band).
        if (RosterVote.merge(reads).rows.any { it.doubtful(31).isNotEmpty() } && !ImagePrep.low(context)) {
            progress(more.size + 2, more.size + 2)
            // Twice: bands 5 letters high at 2.5×, then 4 high at 3.5× (photo bench +48 and +8 cells, silent 0 both).
            for ((zoom, high) in STRIP_PASSES) {
                if (ImagePrep.low(context)) break
                strips(context, base, box, letter ?: 0, zoom, high)?.let { band -> words += band; reads += DutyRoster.analyse(band, codes) }
            }
        }
        val anchor = RosterVote.anchor(reads)
        if (BuildConfig.DEBUG) dump(words, anchor)
        val quality = PhotoQuality.of(stats, letter, first.size, firstRead.tilt)
        val (screen, scale) = ImagePrep.forScreen(base)
        Scan(words[anchor], RosterVote.merge(reads), screen, scale, quality, reads.size)
    }

    /**
     * [READ-35] The table read band by band: the paper's light evened out ([PhotoVariants.Kind.FLAT]), cut into bands ~5 letters
     * high that overlap by half, each made [zoomWanted]× bigger and read; a word is kept from the band whose middle half it
     * sits in. Mirror of tools/photo_bench.py `strips`. Null when it could not run (memory, no table found).
     */
    private suspend fun strips(context: Context, base: Bitmap, box: android.graphics.Rect?, letter: Int, zoomWanted: Float, high: Int): List<RosterWord>? {
        if (box == null || letter <= 0) return null
        val v = ImagePrep.variant(base, PhotoVariants.Kind.FLAT, box) ?: return null
        try {
            val flat = v.bitmap
            val h = flat.height
            val band = maxOf((letter / v.toBase * high).toInt(), 24).coerceAtMost(h)
            val step = band / 2
            val out = mutableListOf<RosterWord>()
            var y = 0
            while (true) {
                val hh = minOf(band, h - y)
                val crop = Bitmap.createBitmap(flat, 0, y, flat.width, hh)
                val zoom = minOf(zoomWanted, ImagePrep.BIG_SIDE.toFloat() / flat.width).coerceAtLeast(1f)
                val up = Bitmap.createScaledBitmap(crop, (crop.width * zoom).toInt().coerceAtLeast(1), (crop.height * zoom).toInt().coerceAtLeast(1), true)
                if (crop !== flat && crop !== up) crop.recycle()
                val got = runCatching { words(OcrModel.read(context, up)) }.getOrNull().orEmpty()
                up.recycle()
                val lo = y + if (y == 0) 0 else band / 4
                val hi = y + if (y + band >= h) band else band - band / 4
                for (w in got) {
                    val t = w.top / zoom + y
                    val b = w.bottom / zoom + y
                    if ((t + b) / 2 < lo || (t + b) / 2 >= hi) continue
                    out += w.copy(left = v.x((w.left / zoom).toInt()), right = v.x((w.right / zoom).toInt()), top = v.y(t.toInt()), bottom = v.y(b.toInt()))
                }
                if (y + band >= h || ImagePrep.low(context)) break
                y += step
            }
            return out.takeIf { it.isNotEmpty() }
        } finally {
            if (v.bitmap !== base) v.bitmap.recycle()
        }
    }

    /** Words (elements), not lines: a table's cells close together come back as one line. */
    private fun words(text: Text): List<RosterWord> = text.textBlocks.flatMap { it.lines }.flatMap { line ->
        line.elements.mapNotNull { e ->
            val box = e.boundingBox ?: return@mapNotNull null
            RosterWord(e.text, box.left, box.top, box.right, box.bottom, angle = line.angle)
        }
    }

    /**
     * Debug builds only: every read's words, in the shape of a roster-photos `.ocr.tsv` fixture, so a photo
     * read wrongly on the emulator can be replayed here exactly (`adb logcat -s BamnatOcr`). Nothing in release.
     */
    private fun dump(reads: List<List<RosterWord>>, anchor: Int) {
        reads.forEachIndexed { i, ws ->
            android.util.Log.d(OCR_TAG, "# read $i of ${reads.size}${if (i == anchor) " (anchor)" else ""}")
            ws.chunked(DUMP_LINES).forEach { part ->
                android.util.Log.d(OCR_TAG, part.joinToString("\n") { w ->
                    "word\t${w.left}\t${w.top}\t${w.right}\t${w.bottom}\t${"%.1f".format(java.util.Locale.ROOT, w.angle)}\t${w.text}\t${w.text}"
                })
            }
        }
    }

    private const val OCR_TAG = "BamnatOcr"
    private const val DUMP_LINES = 20

    /** Cells read, the ones in doubt counting half. */
    private fun score(r: RosterPhotoRead): Double = r.rows.sumOf { it.cells.size - it.unsure.size * 0.5 }

    /** From this tilt the leveled photo is read too: ML Kit reads letters turned 1~2° well, a table's columns less so. */
    private const val SECOND_LOOK_TILT = 1.5
    /** [READ-35] How much bigger each band is read, and how many letters high it is. */
    private val STRIP_PASSES = listOf(2.5f to 5, 3.5f to 4)
}
