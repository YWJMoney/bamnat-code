package com.bamnat.app.imports

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.bamnat.app.pay.OcrModel
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterWord
import com.google.mlkit.vision.common.InputImage
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
    class Scan(val words: List<RosterWord>, val read: RosterPhotoRead, val image: Bitmap?, val scale: Float)

    /** Words the tests give instead of ML Kit, which does not run on a computer (Robolectric). */
    @VisibleForTesting
    var fake: (suspend (Uri) -> List<RosterWord>)? = null

    /** The picture behind [fake]'s words, for screenshots of 사진 위에서 누르기. */
    @VisibleForTesting
    var fakeImage: (() -> Bitmap)? = null

    suspend fun scan(context: Context, uri: Uri, codes: DutyCodes): Scan = withContext(Dispatchers.Default) {
        fake?.let { f ->
            val words = f(uri)
            return@withContext Scan(words, DutyRoster.analyse(words, codes), fakeImage?.invoke(), 1f)
        }
        val photo = ImagePrep.load(context, uri)
        val first = words(OcrModel.read(context, InputImage.fromBitmap(photo, 0)))
        val firstRead = DutyRoster.analyse(first, codes)
        var best = Scan(first, firstRead, photo, 1f)
        if (firstRead.problem != null || abs(firstRead.tilt) >= SECOND_LOOK_TILT) {
            val level = ImagePrep.leveled(photo, firstRead.tilt)
            val again = runCatching { words(OcrModel.read(context, InputImage.fromBitmap(level.bitmap, 0))) }.getOrNull()
            if (again != null) {
                val againRead = DutyRoster.analyse(again, codes)
                if (score(againRead) > score(firstRead)) best = Scan(again, againRead, level.bitmap, 1f)
            }
        }
        val (screen, scale) = ImagePrep.forScreen(best.image!!)
        Scan(best.words, best.read, screen, scale)
    }

    /** Words (elements), not lines: a table's cells close together come back as one line. */
    private fun words(text: Text): List<RosterWord> = text.textBlocks.flatMap { it.lines }.flatMap { line ->
        line.elements.mapNotNull { e ->
            val box = e.boundingBox ?: return@mapNotNull null
            RosterWord(e.text, box.left, box.top, box.right, box.bottom, angle = line.angle)
        }
    }

    /** Cells read, the ones in doubt counting half. */
    private fun score(r: RosterPhotoRead): Double = r.rows.sumOf { it.cells.size - it.unsure.size * 0.5 }

    /** From this tilt the leveled photo is read too: ML Kit reads letters turned 1~2° well, a table's columns less so. */
    private const val SECOND_LOOK_TILT = 1.5
}
