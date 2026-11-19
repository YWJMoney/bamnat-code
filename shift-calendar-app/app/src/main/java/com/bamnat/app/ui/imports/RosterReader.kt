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
import com.bamnat.core.imports.TimeSheet
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
    /** 2.0.0 [RVL-32] (검토 U07b-01) A reading that failed: the one thing its cause asks for (와이파이, 저장 공간, Play 서비스, 다시). */
    val fix: com.bamnat.app.pay.OcrModel.Fix? = null,
    /** 2.0.0 [RVL-41] (검토 U07b-10) A locked PDF: its password is asked where the file was picked ([PdfPasswordDialog]). */
    val locked: com.bamnat.app.imports.PdfLock? = null,
    /** 2.0.0 [RVL-75] (검토 I07ba-02) What the roster's row of weekdays says of the month read: another month, or columns a day off. */
    val weekdays: com.bamnat.core.imports.WeekdayCheck.Verdict? = null,
    /** 2.0.0 [RVL-81] (검토 I07bc-03) A photo that read, but would read better taken again: the one thing to say right after it. */
    val check: com.bamnat.core.imports.PhotoQuality.Issue? = null,
)

object RosterReader {
    /** [READ-30] Dates as the user reads them: "10월 26~31일", "10월 30일, 12월 1~2일". */
    fun span(dates: Collection<LocalDate>): String = dates.sorted().groupBy { YearMonth.from(it) }.entries.joinToString(", ") { (m, ds) ->
        val runs = mutableListOf<IntRange>()
        for (d in ds.map { it.dayOfMonth }) if (runs.isNotEmpty() && runs.last().last == d - 1) runs[runs.size - 1] = runs.last().first..d else runs += d..d
        "${m.monthValue}월 " + runs.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}~${it.last}" } + "일"
    }

    /** [READ-34] A file whose words read as a payslip: a 지급 총액 and at least three amount lines. */
    fun isPayslip(sheets: List<Sheets.Sheet>): Boolean = sheets.any { sh ->
        val r = com.bamnat.core.pay.PayslipReader.read(sh.rows.joinToString("\n") { it.joinToString(" ") })
        r.gross != null && r.lines.count { it.amount != null } >= 3
    }

    const val PAYSLIP_HERE = "이 파일은 근무표가 아니라 급여명세서예요. 급여 화면의 '명세서로 채우기'에서 'PDF로 받은 명세서 고르기'로 넣으면 모든 줄을 그대로 읽어요."

    const val NOTHING = "날짜 줄(1 2 3 …)과 근무 줄을 찾지 못했어요. 표 전체가 반듯하게 나오게 다시 찍어 보세요."

    /** 2.0.0 [RVL-72] (검토 I07bb-02) 알바·현장: what a 시간표·공수표 needs, in its own words. */
    const val NOTHING_TIMED = "날짜(1 2 3 … 또는 월 화 수 …)와 시간(09:00-15:00)이나 공수를 찾지 못했어요. 표 전체가 반듯하게 나오게 다시 찍어 보세요."

    /**
     * 2.0.0 [RVL-72] (검토 T07b-01·T07b-02·I07bb-02) A 알바 시간표 or 현장 공수표 read as times or 공수, when that reads more than the codes
     * did: a line of "09:00-15:00" is not a line of unknown codes. [first] (알바·현장 달력) takes the times whenever they read.
     */
    internal fun timed(read: TimeSheet.Read?, codes: List<RosterRow>, first: Boolean): Boolean {
        val rows = read?.rows.orEmpty()
        if (rows.isEmpty()) return false
        if (first) return true
        val times = rows.maxOf { it.cells.size + it.weekly.size }
        val known = codes.maxOfOrNull { r -> r.cells.values.count { it !is com.bamnat.core.imports.DutyCell.Unknown } } ?: 0
        return times > known
    }

    suspend fun photo(
        context: Context, uri: Uri, codes: DutyCodes, near: LocalDate,
        /** 2.0.0 [RVL-32] (검토 SCAN-04) The reading model coming down before the first reading (percent, null while unknown). */
        preparing: (Int?) -> Unit = {},
        progress: (Int, Int) -> Unit = { _, _ -> },
        /** 2.0.0 [RVL-72] An 알바·현장 calendar: a 시간표·공수표 is what it is likely given. */
        timeFirst: Boolean = false,
    ): RosterResult {
        val scan = runCatching { RosterScanner.scan(context, uri, codes, preparing, progress) }
        val failed = scan.exceptionOrNull()
        if (failed is kotlinx.coroutines.CancellationException) throw failed
        // 1.36 PC W10: the reason in words and the cause chain, as the payslip photo says it. 2.0.0 [RVL-32] with its one fix.
        val error = failed?.let { com.bamnat.app.pay.OcrModel.said(context, it) }
        val s = scan.getOrNull()
        val list = s?.words.orEmpty()
        // [ROST-04] "10월 근무표" over the table, as it reads from the photo.
        val month = DutyRoster.monthIn(list.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        // 2.0.0 [RVL-24] (검토 SCAN-10) A 4주 듀티's days of the month before, as dates, beside the file's (함께 넣기·빼기).
        val coded = s?.read?.rows.orEmpty().map { it.placed(month ?: YearMonth.from(near)) }
        // 2.0.0 [RVL-72] The photo's words read as times or 공수 when that reads more.
        val times = if (s == null) null else TimeSheet.readWords(list, month, near)
        val rows = if (timed(times, coded, timeFirst)) times!!.rows else coded
        // [READ-14] A photo too dark, blurred or small to read well says so, with what to do, even when it read something.
        val retake = s?.quality?.takeIf { it.retake }?.line
        val none = if (timeFirst && s?.read?.problem != RosterPhotoRead.Problem.NO_TEXT) NOTHING_TIMED else problem(s?.read?.problem)
        return RosterResult(rows, month ?: times?.month.takeIf { rows === times?.rows }, RosterFrom.PHOTO,
            error ?: none.takeIf { rows.isEmpty() }?.let { p -> retake?.let { "$p $it" } ?: p }, s,
            advice = retake.takeIf { rows.isNotEmpty() }, fix = failed?.let { com.bamnat.app.pay.OcrModel.fix(it) },
            check = s?.quality?.check?.takeIf { rows.isNotEmpty() },
            weekdays = if (rows.isEmpty() || rows === times?.rows) null else weekdays(com.bamnat.core.imports.WeekdayCheck.ofWords(list), month, near))
    }

    /** [READ-04] The photo read again along the lines the user tapped: the row of dates (if any) and their own line. */
    fun tapped(scan: RosterScanner.Scan, codes: DutyCodes, hint: RosterHint, near: LocalDate): RosterResult {
        val month = DutyRoster.monthIn(scan.words.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        val rows = DutyRoster.read(scan.words, codes, hint).map { it.placed(month ?: YearMonth.from(near)) }
        return RosterResult(rows, month, RosterFrom.PHOTO, TAPPED_NOTHING.takeIf { rows.isEmpty() }, scan)
    }

    /** 2.0.0 [RVL-80] (검토 I07bc-02) Where [hint] tapped on [scan]'s table: the row of days and the user's line, per mille of its height. */
    fun taps(scan: RosterScanner.Scan, hint: RosterHint): Pair<Int?, Int?>? {
        val w = scan.words.takeIf { it.isNotEmpty() } ?: return null
        // Measured on the table laid level: a name tapped at the left edge of a turned photo is its line's place, as the middle is.
        val my = hint.myRow?.let { com.bamnat.core.imports.RosterForm.at(it.first, it.second, w) } ?: return null
        return hint.dayRow?.let { com.bamnat.core.imports.RosterForm.at(it.first, it.second, w) } to my
    }

    /** 2.0.0 [RVL-80] Last month's taps ([form]) on [scan]'s table, or null when last month's sheet needed none. */
    fun formHint(scan: RosterScanner.Scan, form: com.bamnat.core.imports.RosterForm?): RosterHint? {
        val w = scan.words.takeIf { it.isNotEmpty() } ?: return null
        return com.bamnat.core.imports.RosterForm.hint(form, w)
    }

    /** [READ-05] Why a photo gave nothing, and what to do next. */
    fun problem(p: RosterPhotoRead.Problem?): String = when (p) {
        RosterPhotoRead.Problem.NO_TEXT -> "사진에서 글자를 못 찾았어요. 표가 화면 가득, 밝은 곳에서 찍힌 사진을 골라 주세요."
        // 2.0.0 [RVL-33] (검토 SCAN-20·U07b-14) By the name of the button right under it.
        RosterPhotoRead.Problem.NO_DAYS -> "날짜 줄(1 2 3 …)을 찾지 못했어요. 아래 ‘$TAP_PHOTO’로 날짜 줄과 내 줄을 눌러 주면 그 줄을 읽어요."
        RosterPhotoRead.Problem.NO_DUTIES -> "날짜는 찾았는데 근무 줄을 찾지 못했어요. 아래 ‘$TAP_PHOTO’로 내 줄을 눌러 주세요."
        null -> NOTHING
    }

    const val TAPPED_NOTHING = "누른 곳에서 근무를 찾지 못했어요. 내 이름이나 근무 글자 위를 눌러 주세요."

    /**
     * [IMPORT-03][READ-30] A roster file: [AnyFile] tells a table (.xlsx·.xls·.ods·HTML·CSV), a PDF (its text read as the table,
     * or a scanned one read page by page as photos) and a picture apart, whatever the file's name says.
     */
    suspend fun file(
        context: Context, uri: Uri, codes: DutyCodes, near: LocalDate,
        /** 2.0.0 [RVL-41] The password typed for a locked PDF, for this reading only. */
        password: CharArray? = null,
        preparing: (Int?) -> Unit = {},
        progress: (Int, Int) -> Unit = { _, _ -> },
        timeFirst: Boolean = false,
    ): RosterResult {
        val opened = runCatching { com.bamnat.app.imports.AnyFile.open(context, uri, password) }
        opened.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return RosterResult(emptyList(), null, RosterFrom.FILE, (e as? Sheets.SheetException)?.message ?: FILE_FAILED)
        }
        return when (val o = opened.getOrThrow()) {
            is com.bamnat.app.imports.AnyFile.Opened.Picture -> photo(context, o.uri, codes, near, preparing, progress, timeFirst)
            is com.bamnat.app.imports.AnyFile.Opened.Pages -> pdfPages(context, o.pdf, codes, near, preparing, progress, timeFirst)
            is com.bamnat.app.imports.AnyFile.Opened.Table -> {
                val read = table(o.sheets, codes, near, o.fromPdf, timeFirst)
                // 2.0.0 [RVL-42] (검토 SCAN-15) A PDF whose text held no roster (a table drawn as lines and letters placed one by
                // one, a font read wrongly) is drawn and read as pictures before it says it found nothing.
                if (read.rows.isEmpty() && read.error != PAYSLIP_HERE && o.pdf != null) {
                    val photo = pdfPages(context, o.pdf, codes, near, preparing, progress, timeFirst)
                    if (photo.rows.isNotEmpty()) return photo
                }
                read.copy(advice = read.advice ?: o.cut.takeIf { read.rows.isNotEmpty() })
            }
            is com.bamnat.app.imports.AnyFile.Opened.Locked -> RosterResult(emptyList(), null, RosterFrom.FILE,
                if (o.lock == com.bamnat.app.imports.PdfLock.WRONG) PASSWORD_WRONG else LOCKED, locked = o.lock)
        }
    }

    const val LOCKED = "암호가 걸린 PDF예요. 암호를 넣으면 이 휴대폰 안에서 열어요."
    const val PASSWORD_WRONG = "암호가 맞지 않아요. 다시 넣어 주세요."

    /** 2.0.0 [RVL-43] (검토 SCAN-18·U07b-09) A scanned PDF drawn and read a page at a time; more pages than are read are said. */
    private suspend fun pdfPages(
        context: Context, pdf: com.bamnat.app.imports.AnyFile.Pdf, codes: DutyCodes, near: LocalDate,
        preparing: (Int?) -> Unit, progress: (Int, Int) -> Unit, timeFirst: Boolean = false,
    ): RosterResult {
        val source = try {
            com.bamnat.app.imports.AnyFile.pages(context, pdf)
        } catch (e: Sheets.SheetException) {
            return RosterResult(emptyList(), null, RosterFrom.FILE, e.message ?: FILE_FAILED)
        }
        val cut = com.bamnat.app.imports.AnyFile.cutLine(source.total, source.count)
        val r = pages(context, source, codes, near, preparing, progress, timeFirst)
        return if (cut != null && r.rows.isNotEmpty()) r.copy(advice = listOfNotNull(r.advice, cut).joinToString(" ")) else r
    }

    /** [READ-30] Sheets read as a roster: the sheet that holds it, the month most of its days are in. */
    fun table(sheets: List<Sheets.Sheet>, codes: DutyCodes, near: LocalDate, fromPdf: Boolean = false, timeFirst: Boolean = false): RosterResult {
        val coded = table(sheets, codes, near, fromPdf)
        // 2.0.0 [RVL-72] The sheet that reads best as a 시간표·공수표, taken when it reads more than the codes.
        val times = sheets.mapNotNull { sh -> runCatching { TimeSheet.read(sh.rows, null, near) }.getOrNull() }
            .maxByOrNull { r -> r.rows.sumOf { it.cells.size + it.weekly.size } }
        if (timed(times, coded.rows, timeFirst)) return RosterResult(times!!.rows, times.month, RosterFrom.FILE)
        if (coded.rows.isEmpty()) return if (timeFirst && coded.error != PAYSLIP_HERE) coded.copy(error = NOTHING_TIMED) else coded
        // 2.0.0 [RVL-75] The sheet's row of weekdays, against the month read.
        val pairs = sheets.map { com.bamnat.core.imports.WeekdayCheck.ofRows(it.rows) }.maxByOrNull { it.size }.orEmpty()
        return coded.copy(weekdays = weekdays(pairs, coded.month, near))
    }

    private fun table(sheets: List<Sheets.Sheet>, codes: DutyCodes, near: LocalDate, fromPdf: Boolean): RosterResult {
        val read = runCatching { RosterSheet.readBook(sheets, codes, near = near) }.getOrNull()
            ?: return RosterResult(emptyList(), null, RosterFrom.FILE, when {
                // [READ-34] 1.38: a payslip shared from 카카오톡 lands here (a shared PDF opens 한 달 근무 넣기): said where it goes.
                isPayslip(sheets) -> PAYSLIP_HERE
                fromPdf -> "PDF에서 날짜와 근무를 찾지 못했어요. 표가 든 쪽인지 봐 주세요."
                else -> "날짜와 근무를 찾지 못했어요. 첫 줄에 1 2 3 … 날짜가 있거나, 날짜·근무 열이 있는 표면 돼요."
            })
        // The month the file has most days of.
        val month = read.people.flatMap { p -> p.days.map { YearMonth.from(it.date) } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val rows = read.people.map { p ->
            val days = p.days.filter { YearMonth.from(it.date) == month }
            // [READ-30] 1.38 PC: a 4주 듀티's days in the next or last month go in with it, not dropped.
            val outside = p.days.filter { YearMonth.from(it.date) != month }.associate { it.date to it.cell }
            RosterRow(p.name, days.associate { it.date.dayOfMonth to it.cell }, notes = days.mapNotNull { d -> d.note?.let { d.date.dayOfMonth to it } }.toMap(), outside = outside)
        }.filter { it.cells.isNotEmpty() }
        return RosterResult(rows, month, RosterFrom.FILE, NOTHING.takeIf { rows.isEmpty() })
    }

    /** 2.0.0 [RVL-75] (검토 I07ba-02) The weekdays' verdict on [month] (the screen's when the roster named none). */
    private fun weekdays(pairs: Map<Int, java.time.DayOfWeek>, month: YearMonth?, near: LocalDate) =
        com.bamnat.core.imports.WeekdayCheck.check(pairs, month ?: YearMonth.from(near))

    /** [READ-32] A scanned PDF: each page read as a photo, the lines of all pages together (a name on two pages once). */
    internal suspend fun pages(
        context: Context, pages: List<android.graphics.Bitmap>, codes: DutyCodes, near: LocalDate,
        preparing: (Int?) -> Unit = {}, progress: (Int, Int) -> Unit,
    ): RosterResult = pages(context, com.bamnat.app.imports.AnyFile.Drawn(pages), codes, near, preparing, progress)

    /** 2.0.0 [RVL-43] (검토 SCAN-18) Each page drawn, read and let go before the next is drawn. */
    internal suspend fun pages(
        context: Context, source: com.bamnat.app.imports.AnyFile.PageSource, codes: DutyCodes, near: LocalDate,
        preparing: (Int?) -> Unit = {}, progress: (Int, Int) -> Unit, timeFirst: Boolean = false,
    ): RosterResult {
        val scans = mutableListOf<RosterScanner.Scan>()
        try {
            for (i in 0 until source.count) {
                // 2.0.0 [RVL-32] (검토 U07b-01) Page by page: "3번 중 2번째" is the page being read.
                progress(i + 1, source.count)
                val page = source.page(i)
                try {
                    scans += RosterScanner.scanPicture(context, page, codes, preparing)
                } finally {
                    page.recycle()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 1.39.1 [R391-13] (검토 SCAN-01) A scanned PDF whose reading failed (글자 인식 준비물 not there yet, Play 서비스 old or
            // offline) closed the app: it says why, as a photo does.
            return RosterResult(emptyList(), null, RosterFrom.FILE, com.bamnat.app.pay.OcrModel.said(context, e), fix = com.bamnat.app.pay.OcrModel.fix(e))
        } catch (e: OutOfMemoryError) {
            return RosterResult(emptyList(), null, RosterFrom.FILE, com.bamnat.app.pay.OcrModel.said(context, e))
        } finally {
            source.close()
        }
        val words = scans.flatMap { it.words }
        val month = DutyRoster.monthIn(words.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        // 2.0.0 [RVL-16] (검토 SCAN-08) A name on two pages (1~15일, 16~31일) is one line with both halves, not the first page's half.
        val coded = DutyRoster.joinPages(scans.flatMap { it.read.rows }).map { it.placed(month ?: YearMonth.from(near)) }
        // 2.0.0 [RVL-72] Each page's words as a 시간표·공수표 (one page at a time: their places do not line up across pages).
        val times = scans.mapNotNull { TimeSheet.readWords(it.words, month, near) }.let { all ->
            if (all.isEmpty()) null else TimeSheet.Read(all.flatMap { it.rows }, month ?: all.first().month)
        }
        val timedRead = timed(times, coded, timeFirst)
        val rows = if (timedRead) times!!.rows else coded
        // [READ-32] 1.38 PC: a scan read thinly says what reads every cell instead (a 시간표 has days off between, not thin).
        val days = (month ?: YearMonth.from(near)).lengthOfMonth()
        val thin = !timedRead && rows.isNotEmpty() && rows.maxOf { it.cells.size } < days * 0.8
        return RosterResult(rows, month ?: times?.month.takeIf { timedRead }, RosterFrom.FILE,
            if (rows.isEmpty()) (if (timeFirst) NOTHING_TIMED else "스캔한 PDF에서 근무표를 찾지 못했어요. 표가 반듯하게 나온 쪽인지 봐 주세요.") else null, scans.firstOrNull(),
            weekdays = if (rows.isEmpty() || timedRead) null else weekdays(com.bamnat.core.imports.WeekdayCheck.ofWords(scans.first().words), month, near),
            advice = if (thin) "스캔한 PDF라 다 읽지 못했어요. 원래 엑셀 파일이나 엑셀에서 'PDF로 저장'한 파일을 주시면 모든 칸을 정확히 읽어요." else null)
    }

    private const val FILE_FAILED = "이 파일은 읽지 못했어요. 엑셀·CSV·PDF·한글(.hwpx)·워드(.docx)·사진 파일을 골라 주세요."

    fun text(text: String, codes: DutyCodes, near: LocalDate, timeFirst: Boolean = false): RosterResult {
        val coded = DutyRoster.readText(text, codes)
        // 2.0.0 [RVL-72] "11/3 09:00-15:00", "월 9-15": a 시간표 typed or pasted.
        val times = runCatching { TimeSheet.readText(text, null, near) }.getOrNull()
        if (timed(times, coded, timeFirst)) return RosterResult(times!!.rows, times.month ?: DutyRoster.monthIn(text, near), RosterFrom.TEXT)
        val month = DutyRoster.monthIn(text, near)
        // 2.0.0 [RVL-75] A pasted roster's row of weekdays too.
        val pairs = if (coded.isEmpty()) emptyMap() else com.bamnat.core.imports.WeekdayCheck.ofRows(text.lines().map { l -> l.trim().split(Regex("[\\s,|]+")) })
        return RosterResult(coded, month, RosterFrom.TEXT, (if (timeFirst) NOTHING_TIMED else NOTHING).takeIf { coded.isEmpty() },
            weekdays = weekdays(pairs, month, near))
    }

    /**
     * [ROST-10] True when [text] on the clipboard reads as a roster (a row of days and a line of at least a week of
     * codes), so 한 달 근무 넣기 may offer it. Nothing is kept.
     */
    fun looksLikeRoster(text: String, codes: DutyCodes): Boolean =
        text.length in 10..20_000 && (DutyRoster.readText(text, codes).any { it.cells.size >= 7 } ||
            // 2.0.0 [RVL-72] A pasted 시간표 too (a few days of times, or a week by weekday).
            runCatching { TimeSheet.readText(text) }.getOrNull()?.rows.orEmpty().any { it.cells.size >= 3 || it.weekly.size >= 3 })
}
