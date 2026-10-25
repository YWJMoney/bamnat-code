package com.bamnat.app.pay

import android.content.Context
import android.net.Uri
import com.bamnat.app.imports.ImagePrep
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.google.mlkit.vision.common.InputImage
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
 */
object PayslipScanner {
    /** Photos taken for reading go here and are deleted once read (FileProvider path "scan"). */
    fun scanFile(context: Context): File = File(context.cacheDir, "scan").apply { mkdirs() }.resolve("payslip.jpg")

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(ImagePrep.load(context, uri), 0)
        val text = OcrModel.read(context, image)
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val box = l.boundingBox ?: return@mapNotNull null
            OcrLine(l.text, box.left, box.top, box.right, box.bottom, angle = l.angle)
        }
        if (lines.isEmpty()) text.text else OcrRows.join(lines)
    }

    /** Deletes a photo taken for reading. */
    fun clear(context: Context) {
        runCatching { scanFile(context).delete() }
    }
}
