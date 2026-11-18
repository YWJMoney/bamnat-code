package com.bamnat.app.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
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
 * 2.0.0 [RVL-41] (검토 U07b-10·I07bb-01) A locked PDF asks for its password ([Opened.Locked]) and opens with it, here: the
 * password is held for that one reading ([Pdf]) and never kept. [RVL-42] (검토 SCAN-15) A PDF whose text has no table is drawn
 * and read as pictures too ([Opened.Table.pdf]). [RVL-43] (검토 SCAN-18·U07b-09) A scan is drawn one page at a time ([pages]).
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
        /**
         * Sheets to read as tables; [fromPdf] when they came from a PDF's text. 2.0.0 [RVL-42] [pdf]: that PDF, to draw and read
         * as pictures when its text holds no table. [RVL-43] [cut]: "PDF가 25쪽이라 앞 20쪽만 읽었어요", when it had more pages.
         */
        data class Table(
            override val name: String, val sheets: List<Sheets.Sheet>, val fromPdf: Boolean = false,
            val pdf: Pdf? = null, val cut: String? = null,
        ) : Opened
        /** A scanned PDF, for the photo reader: drawn a page at a time ([pages]); [cut] as for [Table]. */
        data class Pages(override val name: String, val pdf: Pdf, val cut: String? = null) : Opened
        /** A picture: read with the photo reader from its [uri]. */
        data class Picture(override val name: String, val uri: Uri) : Opened
        /** 2.0.0 [RVL-41] A locked PDF: it asks for its password ([lock] says whether the one typed was wrong). */
        data class Locked(override val name: String, val lock: PdfLock) : Opened
    }

    /**
     * 2.0.0 [RVL-41][RVL-43] A PDF to draw: its bytes and the password that opened it, held in memory for this reading only
     * (never written anywhere, never logged).
     */
    class Pdf(val bytes: ByteArray, val password: CharArray? = null)

    /**
     * 2.0.0 [RVL-43] (검토 U07b-09) What is said when a file had more pages than are read: "PDF가 9쪽이라 앞 6쪽만 읽었어요" (a scan, drawn
     * page by page) or 20 (a PDF with its text); null when every page was read.
     */
    fun cutLine(total: Int, read: Int): String? = if (total > read) "PDF가 ${total}쪽이라 앞 ${read}쪽만 읽었어요. 나머지 쪽은 그 쪽만 PDF로 저장해 넣어 주세요." else null

    /** 2.0.0 [RVL-41] The file opened with [password] (null at first: a file locked only for printing opens without one). */
    suspend fun open(context: Context, uri: Uri, password: CharArray? = null): Opened = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        val head = resolver.openInputStream(uri)?.use { it.readNBytesCompat(2048) } ?: throw Sheets.SheetException("파일을 열지 못했어요")
        when (SheetFormats.kindOf(head)) {
            SheetFormats.Kind.IMAGE -> Opened.Picture(name, uri)
            SheetFormats.Kind.PDF -> {
                val bytes = bytes(context, uri, MAX_PDF)
                val pdf = Pdf(bytes, password)
                when (val t = PdfText.open(bytes, password)) {
                    is PdfText.Opened.Locked -> Opened.Locked(name, if (t.wrong) PdfLock.WRONG else PdfLock.ASK)
                    is PdfText.Opened.Words -> PdfText.sheets(t).takeIf { s -> s.any { it.rows.size >= 2 } }
                        ?.let { Opened.Table(name, it, fromPdf = true, pdf = pdf, cut = cutLine(t.total, t.pages.size)) }
                        ?: Opened.Pages(name, pdf, cutLine(t.total, minOf(t.total, MAX_PAGES)))
                    is PdfText.Opened.NoText -> Opened.Pages(name, pdf, t.total?.let { cutLine(it, minOf(it, MAX_PAGES)) })
                }
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
     * 2.0.0 [RVL-43] (검토 SCAN-18) A PDF's pages as pictures, one at a time: each drawn when it is read and recycled by the
     * reader before the next is drawn (1.39 drew up to six pages of 3,200px at once, about 29MB each, and a payslip twice).
     * [count] pages are read, at most [MAX_PAGES] of [total]. Closing deletes the copy the system's renderer needed.
     */
    interface PageSource : java.io.Closeable {
        val count: Int
        val total: Int
        /** Page [i], white behind; the caller recycles it. */
        fun page(i: Int): Bitmap
    }

    /** Pictures already drawn (a test's) as a [PageSource]; what was not taken is recycled on closing. */
    class Drawn(private val pages: List<Bitmap>) : PageSource {
        override val count: Int get() = pages.size
        override val total: Int get() = pages.size
        override fun page(i: Int): Bitmap = pages[i]
        override fun close() = pages.forEach { if (!it.isRecycled) it.recycle() }
    }

    /**
     * [READ-32] A scanned PDF's pages, the long side [ImagePrep.MAX_SIDE] (a 31-day table's letters 20+ pixels high), half that on
     * a phone short of memory. 2.0.0 [RVL-41] A locked one with the password it was opened with: Android 15's own renderer, or the
     * same renderer as an update of Android 12~14 brings it; an older phone says to take a picture of the page instead.
     */
    fun pages(context: Context, pdf: Pdf): PageSource {
        val tmp = File.createTempFile("roster", ".pdf", context.cacheDir)
        var fd: ParcelFileDescriptor? = null
        try {
            tmp.writeBytes(pdf.bytes)
            fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val doc = document(fd, pdf.password)
            val side = if (ImagePrep.tight(context)) ImagePrep.MIN_SIDE else ImagePrep.MAX_SIDE
            val opened = fd
            return object : PageSource {
                override val total: Int = doc.count
                override val count: Int = minOf(total, MAX_PAGES)
                override fun page(i: Int): Bitmap = doc.render(i, side)
                override fun close() {
                    runCatching { doc.close() }
                    runCatching { opened.close() }
                    tmp.delete()
                }
            }
        } catch (e: Throwable) {
            runCatching { fd?.close() }
            tmp.delete()
            throw when (e) {
                is Sheets.SheetException -> e
                // A lock this app cannot open (a certificate's): said, with the way that always works.
                is SecurityException -> Sheets.SheetException(LOCKED_OTHER)
                is java.io.IOException -> Sheets.SheetException("이 PDF는 열지 못했어요")
                else -> e
            }
        }
    }

    /** 2.0.0 [RVL-43] The first page (or [index]) for the screen, or null when it cannot be drawn. */
    fun page(context: Context, pdf: Pdf, index: Int = 0): Bitmap? = runCatching {
        pages(context, pdf).use { s -> if (index < s.count) s.page(index) else null }
    }.getOrNull()

    const val LOCKED_OTHER = "이 PDF는 이 앱이 열 수 없는 방식으로 잠겨 있어요. PDF 화면을 캡처해 사진으로 넣어 주세요."
    const val LOCKED_OLD_PHONE = "이 휴대폰에서는 암호가 걸린 스캔 PDF를 그리지 못해요. PDF 화면을 캡처해 사진으로 넣어 주세요."

    /** The system's PDF renderers, which share no type. */
    private interface Doc : java.io.Closeable {
        val count: Int
        fun render(i: Int, side: Int): Bitmap
    }

    private fun blank(width: Int, height: Int, side: Int): Bitmap {
        val k = side.toFloat() / maxOf(width, height)
        return Bitmap.createBitmap((width * k).toInt().coerceAtLeast(1), (height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            .also { it.eraseColor(Color.WHITE) }
    }

    private class Plain(private val r: PdfRenderer) : Doc {
        override val count: Int get() = r.pageCount
        override fun render(i: Int, side: Int): Bitmap = r.openPage(i).use { p ->
            blank(p.width, p.height, side).also { p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        }
        override fun close() = r.close()
    }

    @androidx.annotation.RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private class PreV(private val r: android.graphics.pdf.PdfRendererPreV) : Doc {
        override val count: Int get() = r.pageCount
        override fun render(i: Int, side: Int): Bitmap = r.openPage(i).use { p ->
            blank(p.width, p.height, side).also {
                p.render(it, null, null, android.graphics.pdf.RenderParams.Builder(android.graphics.pdf.RenderParams.RENDER_MODE_FOR_DISPLAY).build())
            }
        }
        override fun close() = r.close()
    }

    private fun document(fd: ParcelFileDescriptor, password: CharArray?): Doc {
        val pw = password?.takeIf { it.isNotEmpty() } ?: return Plain(PdfRenderer(fd))
        if (Build.VERSION.SDK_INT >= 35) {
            return Plain(PdfRenderer(fd, android.graphics.pdf.LoadParams.Builder().setPassword(String(pw)).build()))
        }
        if (Build.VERSION.SDK_INT >= 31 && android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            return PreV(android.graphics.pdf.PdfRendererPreV(fd, android.graphics.pdf.LoadParams.Builder().setPassword(String(pw)).build()))
        }
        throw Sheets.SheetException(LOCKED_OLD_PHONE)
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var at = 0
        while (at < n) { val r = read(buf, at, n - at); if (r < 0) break; at += r }
        return buf.copyOf(at)
    }
}

/** 2.0.0 [RVL-41] (검토 U07b-10) A locked PDF: its password asked ([ASK]), or the one typed did not open it ([WRONG]). */
enum class PdfLock { ASK, WRONG }
