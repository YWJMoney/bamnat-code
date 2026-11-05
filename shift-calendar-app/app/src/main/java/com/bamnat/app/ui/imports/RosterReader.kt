package com.bamnat.app.ui.imports

import android.content.Context
import android.net.Uri
import com.bamnat.app.imports.RosterScanner
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.RosterHint
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.imports.RosterSheet
import com.bamnat.core.imports.Sheets
import com.bamnat.core.journal.RosterFrom
import java.time.LocalDate
import java.time.YearMonth

/**
 * [ROST-01][SETUP-15] One month's roster read from a photo, a file or text, the same way for 한 달 근무 넣기 and the first
 * setup: each person's line, the month the roster names (null when it names none), or why nothing was read.
 */
data class RosterResult(
    val rows: List<RosterRow>, val month: YearMonth?, val from: RosterFrom, val error: String? = null,
    /** [READ-04][READ-05] A photo's words and picture, for 사진 위에서 누르기 and 무엇을 읽었는지 보기. */
    val scan: RosterScanner.Scan? = null,
    /** [READ-14] 1.27: the photo read, but poorly: "사진이 흐려요. …" (yellow cells are likely). */
    val advice: String? = null,
)

object RosterReader {
    const val NOTHING = "날짜 줄(1 2 3 …)과 근무 줄을 찾지 못했어요. 표 전체가 반듯하게 나오게 다시 찍어 보세요."

    suspend fun photo(context: Context, uri: Uri, codes: DutyCodes, near: LocalDate, progress: (Int, Int) -> Unit = { _, _ -> }): RosterResult {
        val scan = runCatching { RosterScanner.scan(context, uri, codes, progress) }
        val error = scan.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 1.36 PC W10: the reason in words and the cause chain, as the payslip photo says it.
            (e as? com.bamnat.app.pay.OcrModel.NotReady)?.message
                ?: (com.bamnat.app.pay.OcrModel.why(e).text + " (오류: ${com.bamnat.app.pay.OcrModel.detail(e)})")
        }
        val s = scan.getOrNull()
        val list = s?.words.orEmpty()
        val rows = s?.read?.rows.orEmpty()
        // [ROST-04] "10월 근무표" over the table, as it reads from the photo.
        val month = DutyRoster.monthIn(list.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        // [READ-14] A photo too dark, blurred or small to read well says so, with what to do, even when it read something.
        val retake = s?.quality?.takeIf { it.retake }?.line
        return RosterResult(rows, month, RosterFrom.PHOTO, error ?: problem(s?.read?.problem).takeIf { rows.isEmpty() }?.let { p -> retake?.let { "$p $it" } ?: p }, s,
            advice = retake.takeIf { rows.isNotEmpty() })
    }

    /** [READ-04] The photo read again along the lines the user tapped: the row of dates (if any) and their own line. */
    fun tapped(scan: RosterScanner.Scan, codes: DutyCodes, hint: RosterHint, near: LocalDate): RosterResult {
        val rows = DutyRoster.read(scan.words, codes, hint)
        val month = DutyRoster.monthIn(scan.words.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        return RosterResult(rows, month, RosterFrom.PHOTO, TAPPED_NOTHING.takeIf { rows.isEmpty() }, scan)
    }

    /** [READ-05] Why a photo gave nothing, and what to do next. */
    fun problem(p: RosterPhotoRead.Problem?): String = when (p) {
        RosterPhotoRead.Problem.NO_TEXT -> "사진에서 글자를 찾지 못했어요. 표가 화면 가득, 밝은 곳에서 찍힌 사진을 골라 주세요."
        RosterPhotoRead.Problem.NO_DAYS -> "날짜 줄(1 2 3 …)을 찾지 못했어요. ‘사진 위에서 누르기’로 날짜 줄과 내 줄을 눌러 주면 그 줄을 읽어요."
        RosterPhotoRead.Problem.NO_DUTIES -> "날짜는 찾았는데 근무 줄을 찾지 못했어요. ‘사진 위에서 누르기’로 내 줄을 눌러 주세요."
        null -> NOTHING
    }

    const val TAPPED_NOTHING = "누른 곳에서 근무를 찾지 못했어요. 내 이름이나 근무 글자 위를 눌러 주세요."

    /**
     * [IMPORT-03][READ-30] A roster file: [AnyFile] tells a table (.xlsx·.xls·.ods·HTML·CSV), a PDF (its text read as the table,
     * or a scanned one read page by page as photos) and a picture apart, whatever the file's name says.
     */
    suspend fun file(context: Context, uri: Uri, codes: DutyCodes, near: LocalDate, progress: (Int, Int) -> Unit = { _, _ -> }): RosterResult {
        val opened = runCatching { com.bamnat.app.imports.AnyFile.open(context, uri) }
        opened.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return RosterResult(emptyList(), null, RosterFrom.FILE, (e as? Sheets.SheetException)?.message ?: FILE_FAILED)
        }
        return when (val o = opened.getOrThrow()) {
            is com.bamnat.app.imports.AnyFile.Opened.Picture -> photo(context, o.uri, codes, near, progress)
            is com.bamnat.app.imports.AnyFile.Opened.Pages -> pages(context, o.pages, codes, near, progress)
            is com.bamnat.app.imports.AnyFile.Opened.Table -> table(o.sheets, codes, near, o.fromPdf)
        }
    }

    /** [READ-30] Sheets read as a roster: the sheet that holds it, the month most of its days are in. */
    fun table(sheets: List<Sheets.Sheet>, codes: DutyCodes, near: LocalDate, fromPdf: Boolean = false): RosterResult {
        val read = runCatching { RosterSheet.readBook(sheets, codes, near = near) }.getOrNull()
            ?: return RosterResult(emptyList(), null, RosterFrom.FILE,
                if (fromPdf) "PDF에서 날짜와 근무를 찾지 못했어요. 표가 든 쪽인지 봐 주세요." else "날짜와 근무를 찾지 못했어요. 첫 줄에 1 2 3 … 날짜가 있거나, 날짜·근무 열이 있는 표면 돼요.")
        // The month the file has most days of.
        val month = read.people.flatMap { p -> p.days.map { YearMonth.from(it.date) } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val rows = read.people.map { p ->
            val days = p.days.filter { YearMonth.from(it.date) == month }
            RosterRow(p.name, days.associate { it.date.dayOfMonth to it.cell }, notes = days.mapNotNull { d -> d.note?.let { d.date.dayOfMonth to it } }.toMap())
        }.filter { it.cells.isNotEmpty() }
        return RosterResult(rows, month, RosterFrom.FILE, NOTHING.takeIf { rows.isEmpty() })
    }

    /** [READ-32] A scanned PDF: each page read as a photo, the lines of all pages together (a name on two pages once). */
    private suspend fun pages(context: Context, pages: List<android.graphics.Bitmap>, codes: DutyCodes, near: LocalDate, progress: (Int, Int) -> Unit): RosterResult {
        val scans = mutableListOf<RosterScanner.Scan>()
        try {
            for ((i, page) in pages.withIndex()) {
                scans += RosterScanner.scanPicture(context, page, codes) { n, m -> progress(i * 10 + n, pages.size * 10) }
            }
        } finally {
            pages.forEach { if (!it.isRecycled) it.recycle() }
        }
        val words = scans.flatMap { it.words }
        val month = DutyRoster.monthIn(words.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        val rows = scans.flatMap { it.read.rows }.distinctBy { DutyCodes.norm(it.label).ifEmpty { it.hashCode().toString() } }
        return RosterResult(rows, month, RosterFrom.FILE, if (rows.isEmpty()) "스캔한 PDF에서 근무표를 찾지 못했어요. 표가 반듯하게 나온 쪽인지 봐 주세요." else null, scans.firstOrNull())
    }

    private const val FILE_FAILED = "이 파일은 읽지 못했어요. 엑셀·CSV·PDF·사진 파일을 골라 주세요."

    fun text(text: String, codes: DutyCodes, near: LocalDate): RosterResult {
        val rows = DutyRoster.readText(text, codes)
        return RosterResult(rows, DutyRoster.monthIn(text, near), RosterFrom.TEXT, NOTHING.takeIf { rows.isEmpty() })
    }

    /**
     * [ROST-10] True when [text] on the clipboard reads as a roster (a row of days and a line of at least a week of
     * codes), so 한 달 근무 넣기 may offer it. Nothing is kept.
     */
    fun looksLikeRoster(text: String, codes: DutyCodes): Boolean =
        text.length in 10..20_000 && DutyRoster.readText(text, codes).any { it.cells.size >= 7 }
}
