package com.bamnat.app.imports

import android.content.Context
import android.net.Uri
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterGrid
import com.bamnat.core.imports.Sheets
import com.bamnat.core.imports.StaffSheet
import com.bamnat.core.imports.TeamSheet
import com.bamnat.core.imports.WordTable
import com.bamnat.core.team.TeamShift
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 가져오기 (사용자 2026-11-05 "종이 사진이나 엑셀 표, PDF 등 어떤 형식이든 완벽하게 인식해서 틀에 관계없이"): the 리더's team
 * roster and the 사장님's staff list out of any file [AnyFile] opens (엑셀·CSV·HTML ".xls"·글자 PDF·스캔 PDF·사진), for the
 * 관리 화면's 가져오기. Everything is read on the phone.
 */
object ManageImport {
    sealed interface Got<out T> {
        data class Read<T>(val value: T, val fromPhoto: Boolean) : Got<T>
        data class Failed(val why: String) : Got<Nothing>
    }

    private const val FAILED = "이 파일은 읽지 못했어요. 엑셀·CSV·PDF·사진 파일을 골라 주세요."

    private fun codes(shifts: List<TeamShift>) = DutyCodes(shifts.map { DutyType(it.id, it.name, it.label, it.isOff, it.start) })

    private suspend fun <T> open(context: Context, uri: Uri, then: suspend (AnyFile.Opened) -> Got<T>): Got<T> {
        val opened = runCatching { AnyFile.open(context, uri) }
        opened.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Got.Failed((e as? Sheets.SheetException)?.message ?: FAILED)
        }
        return runCatching { then(opened.getOrThrow()) }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Got.Failed((e as? com.bamnat.app.pay.OcrModel.NotReady)?.message ?: FAILED)
        }
    }

    /** Pictures read one by one (a scanned PDF's pages are recycled after). */
    private suspend fun scans(context: Context, o: AnyFile.Opened, codes: DutyCodes): List<RosterScanner.Scan> = when (o) {
        is AnyFile.Opened.Picture -> listOf(RosterScanner.scan(context, o.uri, codes))
        is AnyFile.Opened.Pages -> try { o.pages.map { RosterScanner.scanPicture(context, it, codes) } } finally { o.pages.forEach { if (!it.isRecycled) it.recycle() } }
        is AnyFile.Opened.Table -> emptyList()
    }

    /**
     * The whole team's roster. [month] is the board's: a photo that names no month is read for it; a table finds its own.
     * [shifts] are the team's kinds of day, so a photo cell read as one of them comes back under the team's own label.
     */
    suspend fun roster(context: Context, uri: Uri, month: YearMonth, shifts: List<TeamShift>, near: LocalDate): Got<RosterGrid> = open(context, uri) { o ->
        when (o) {
            is AnyFile.Opened.Table -> TeamSheet.roster(o.sheets, null, near)?.takeIf { it.rows.isNotEmpty() }?.let { Got.Read(it, false) }
                ?: Got.Failed(if (o.fromPdf) "PDF에서 날짜와 근무를 찾지 못했어요. 표가 든 쪽인지 봐 주세요."
                    else "날짜와 근무를 찾지 못했어요. 첫 줄에 1 2 3 … 날짜가 있고 한 줄에 한 사람인 표면 돼요.")
            else -> {
                val c = codes(shifts)
                val labels = shifts.associate { it.id to it.label }
                val grids = scans(context, o, c).mapNotNull { s ->
                    val text = s.words.sortedWith(compareBy({ it.top }, { it.left })).joinToString(" ") { it.text }
                    TeamSheet.roster(s.read, DutyRoster.monthIn(text, near) ?: month, labels)
                }
                TeamSheet.pages(grids)?.let { Got.Read(it, true) }
                    ?: Got.Failed("사진에서 근무표를 찾지 못했어요. 표 전체가 반듯하게, 밝은 곳에서 나온 사진을 골라 주세요.")
            }
        }
    }

    /** A staff list (이름·시급·근무 요일·시간 …). 연락처·주민번호·계좌 columns are never read ([com.bamnat.core.imports.StaffSheets]). */
    suspend fun staff(context: Context, uri: Uri, near: LocalDate): Got<StaffSheet> = open(context, uri) { o ->
        when (o) {
            is AnyFile.Opened.Table -> StaffSheet.read(o.sheets, near)?.let { Got.Read(it, false) }
            else -> scans(context, o, DutyCodes(emptyList())).mapNotNull { StaffSheet.read(WordTable.rows(it.words), near) }
                .flatMap { it.rows }.takeIf { it.isNotEmpty() }?.let { Got.Read(StaffSheet(it), true) }
        } ?: Got.Failed("직원 이름 줄을 찾지 못했어요. 첫 줄에 '이름'과 '시급'(또는 근무 요일·시간) 같은 칸 이름이 있는 명단이면 돼요.")
    }
}
