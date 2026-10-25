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
)

object RosterReader {
    const val NOTHING = "날짜 줄(1 2 3 …)과 근무 줄을 찾지 못했어요. 표 전체가 반듯하게 나오게 다시 찍어 보세요."

    suspend fun photo(context: Context, uri: Uri, codes: DutyCodes, near: LocalDate): RosterResult {
        val scan = runCatching { RosterScanner.scan(context, uri, codes) }
        val error = scan.exceptionOrNull()?.let { e ->
            (e as? com.bamnat.app.pay.OcrModel.NotReady)?.message ?: "사진을 열지 못했어요. 다른 사진으로 해 보세요."
        }
        val s = scan.getOrNull()
        val list = s?.words.orEmpty()
        val rows = s?.read?.rows.orEmpty()
        // [ROST-04] "10월 근무표" over the table, as it reads from the photo.
        val month = DutyRoster.monthIn(list.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }, near)
        return RosterResult(rows, month, RosterFrom.PHOTO, error ?: problem(s?.read?.problem).takeIf { rows.isEmpty() }, s)
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

    suspend fun file(context: Context, uri: Uri, codes: DutyCodes, near: LocalDate): RosterResult {
        val result = runCatching {
            val (_, sheet) = SheetFiles.read(context, uri)
            RosterSheet.read(sheet, codes, near = near)
        }
        val read = result.getOrNull() ?: return RosterResult(emptyList(), null, RosterFrom.FILE,
            (result.exceptionOrNull() as? Sheets.SheetException)?.message
                ?: if (result.isFailure) "이 파일은 읽지 못했어요. 엑셀(.xlsx)이나 CSV로 저장한 파일을 골라 주세요."
                else "날짜와 근무를 찾지 못했어요. 첫 줄에 1 2 3 … 날짜가 있거나, 날짜·근무 열이 있는 표면 돼요.")
        // The month the file has most days of.
        val month = read.people.flatMap { p -> p.days.map { YearMonth.from(it.date) } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val rows = read.people.map { p ->
            val days = p.days.filter { YearMonth.from(it.date) == month }
            RosterRow(p.name, days.associate { it.date.dayOfMonth to it.cell }, notes = days.mapNotNull { d -> d.note?.let { d.date.dayOfMonth to it } }.toMap())
        }.filter { it.cells.isNotEmpty() }
        return RosterResult(rows, month, RosterFrom.FILE, NOTHING.takeIf { rows.isEmpty() })
    }

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
