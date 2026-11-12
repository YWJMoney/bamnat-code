package com.bamnat.app.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.bamnat.core.imports.PdfText
import com.bamnat.core.imports.SheetFormats
import com.bamnat.core.imports.Sheets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [READ-30][READ-32] 1.38 (사용자 2026-11-05 "종이 사진이나 엑셀표·PDF 같은 것 등 어떠한 형식으로든"): any file a roster or a staff
 * list comes in, opened one way for 한 달 근무 넣기, the first setup, and the 리더·사장님 관리 화면 (P3 갈래7).
 *
 * What the file is comes from its first bytes, not its name or type: an "xls" from a 근태 system is often HTML.
 * - a table (.xlsx, .xls, .ods, HTML·XML ".xls", CSV, and 2.0.0 [RVL-29] the tables of a .docx or 한글 .hwpx) → its sheets ([Sheets.book]);
 * - a PDF with its text in it (saved from Excel·한글·a 듀티 program) → the table on each page, read from the file ([PdfText]);
 * - a scanned PDF → each page rendered (at most [MAX_PAGES]) for the photo reader;
 * - a picture → the photo reader.
 * Everything stays on the phone; the copy a PDF needs to be rendered is in the app's cache and deleted at once.
 */
object AnyFile {
    /** What the picker offers for a roster or a staff list: tables, PDF and pictures. */
    val MIME_TYPES = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
        "application/vnd.oasis.opendocument.spreadsheet", "text/csv", "text/comma-separated-values", "text/html", "text/plain",
        "application/pdf", "image/*", "application/octet-stream",
        // 2.0.0 [RVL-29] (검토 SCAN-16·T07b-10) 한글·워드 문서: .hwpx·.docx tables are read; .hwp·.doc say how to save them.
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword",
        "application/haansofthwpx", "application/vnd.hancom.hwpx", "application/hwp+zip", "application/haansofthwp", "application/x-hwp",
        "application/vnd.hancom.hwp",
    )

    const val MAX_PAGES = 6
    /** A PDF may be bigger than a sheet (fonts, a scan): read up to this. */
    const val MAX_PDF = 24 * 1024 * 1024

    sealed interface Opened {
        val name: String
        /** Sheets to read as tables; [fromPdf] when they came from a PDF's text. */
        data class Table(override val name: String, val sheets: List<Sheets.Sheet>, val fromPdf: Boolean = false) : Opened
        /** A scanned PDF: its pages as pictures, for the photo reader. The caller recycles them. */
        data class Pages(override val name: String, val pages: List<Bitmap>) : Opened
        /** A picture: read with the photo reader from its [uri]. */
        data class Picture(override val name: String, val uri: Uri) : Opened
    }

    suspend fun open(context: Context, uri: Uri): Opened = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        val head = resolver.openInputStream(uri)?.use { it.readNBytesCompat(2048) } ?: throw Sheets.SheetException("파일을 열지 못했어요")
        when (SheetFormats.kindOf(head)) {
            SheetFormats.Kind.IMAGE -> Opened.Picture(name, uri)
            SheetFormats.Kind.PDF -> {
                val bytes = bytes(context, uri, MAX_PDF)
                PdfText.sheets(bytes)?.takeIf { s -> s.any { it.rows.size >= 2 } }?.let { Opened.Table(name, it, fromPdf = true) }
                    ?: Opened.Pages(name, render(context, bytes))
            }
            else -> Opened.Table(name, Sheets.book(bytes(context, uri, Sheets.MAX_BYTES)))
        }
    }

    /** The file's bytes, refused past [max]. */
    fun bytes(context: Context, uri: Uri, max: Int): ByteArray = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw Sheets.SheetException("파일이 너무 커요 (${max / 1024 / 1024}MB까지)")
        }
        out.toByteArray()
    } ?: throw Sheets.SheetException("파일을 열지 못했어요")

    /**
     * [READ-32] A scanned PDF's pages as pictures, white behind, the long side [ImagePrep.MAX_SIDE] (a 31-day table's letters
     * 20+ pixels high), half that on a phone short of memory.
     */
    fun render(context: Context, pdf: ByteArray): List<Bitmap> {
        val tmp = File.createTempFile("roster", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(pdf)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    val side = if (ImagePrep.tight(context)) ImagePrep.MIN_SIDE else ImagePrep.MAX_SIDE
                    return (0 until minOf(r.pageCount, MAX_PAGES)).map { i ->
                        r.openPage(i).use { page ->
                            val k = side.toFloat() / maxOf(page.width, page.height)
                            val bmp = Bitmap.createBitmap((page.width * k).toInt().coerceAtLeast(1), (page.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            throw Sheets.SheetException("암호가 걸린 PDF예요. 암호를 풀고 다시 저장해 주세요")
        } catch (e: java.io.IOException) {
            throw Sheets.SheetException("이 PDF는 열지 못했어요")
        } finally {
            tmp.delete()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var at = 0
        while (at < n) { val r = read(buf, at, n - at); if (r < 0) break; at += r }
        return buf.copyOf(at)
    }
}
