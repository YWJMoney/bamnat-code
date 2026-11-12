package com.bamnat.app.imports

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.IncomingRoster
import com.bamnat.app.ui.imports.RosterReader
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.TeamSheet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [READ-30][READ-32] 1.38: 한 달 근무 넣기 '엑셀·PDF' and a file shared from 카카오톡 take any roster file — a PDF saved from
 * Excel (its text read, not a picture), the HTML a 근태 system saves as ".xls", a real old .xls and a 4주 듀티 across two
 * months — and read the month on the phone. The files are the bench's (tools/file_bench.py), names made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AnyFileTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true), DutyType(4, "오프", "O", true),
    ))
    private val near = LocalDate.of(2026, 11, 5)

    private fun uri(name: String): Uri {
        val f = File(context.cacheDir, name)
        f.writeBytes(javaClass.classLoader!!.getResourceAsStream("roster-files/$name")!!.readBytes())
        return Uri.fromFile(f)
    }

    @Test fun aPdfSavedFromExcelIsReadAsItsTable() = runBlocking {
        val opened = AnyFile.open(context, uri("p01-pdf.pdf"))
        assertTrue(opened is AnyFile.Opened.Table && opened.fromPdf)
        val r = RosterReader.file(context, uri("p01-pdf.pdf"), codes, near)
        assertNull(r.error)
        assertEquals(YearMonth.of(2026, 11), r.month)
        assertEquals(12, r.rows.size)
        assertTrue(r.rows.all { it.cells.size == 30 && it.cells.values.none { c -> c is DutyCell.Unknown } })
    }

    @Test fun anHtmlXlsAndAnOldXlsReadTheSame() = runBlocking {
        for (name in listOf("h01-html.xls", "b01-biff.xls")) {
            val r = RosterReader.file(context, uri(name), codes, near)
            assertNull(name, r.error)
            assertEquals(name, 8, r.rows.size)
            assertTrue(name, r.rows.all { it.cells.size == 30 })
        }
    }

    @Test fun aFourWeekRosterGivesTheLeaderBothMonths() = runBlocking {
        val opened = AnyFile.open(context, uri("x04-cross-month.xlsx")) as AnyFile.Opened.Table
        val grid = TeamSheet.roster(opened.sheets, near = near)!!
        assertEquals(LocalDate.of(2026, 10, 26), grid.days.first())
        assertEquals(31, grid.days.size)
        // The personal month is the one most of the days are in, all of its 25 days.
        val r = RosterReader.file(context, uri("x04-cross-month.xlsx"), codes, near)
        assertEquals(YearMonth.of(2026, 11), r.month)
        assertTrue(r.rows.all { it.cells.keys == (1..25).toSet() })
        // [READ-30] 1.38 PC: and 10/26~31 go with it, not dropped.
        assertTrue(r.rows.all { it.outside.keys == (26..31).map { d -> LocalDate.of(2026, 10, d) }.toSet() })
        assertEquals("10월 26~31일", RosterReader.span(r.rows.first().outside.keys))
        assertEquals("10월 30일, 12월 1~2일", RosterReader.span(listOf(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 12, 1))))
    }

    @Test fun aRosterFileSharedFromAMessengerOpensTheRoster() {
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x/roster.pdf"))
        assertTrue(IncomingRoster.sharedOf(i) is IncomingRoster.Shared.File)
        val photo = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x/a.jpg"))
        assertTrue(IncomingRoster.sharedOf(photo) is IncomingRoster.Shared.Photo)
    }

    /** [READ-34] 1.38: a payslip PDF (an ERP's, drawn line by line) read from its own letters, every line and total, nothing asked. */
    @Test fun aPayslipPdfIsReadFromItsLetters() = runBlocking {
        val read = com.bamnat.app.pay.PayslipScanner.scanPdf(context, uri("slip-s04-drawn.pdf")) as com.bamnat.app.pay.PayslipScanner.PdfRead.Slips
        // 2.0.0 [RVL-44] One payslip on its pages: one to go on with, not a list to pick from.
        val scan = read.scans.single()
        val r = scan.slip.read
        assertEquals(YearMonth.of(2026, 10), r.month)
        assertEquals(15, r.lines.size)
        assertEquals(3_844_030L, r.gross)
        assertEquals(3_433_520L, r.net)
        assertTrue(scan.slip.sure)
    }

    /** [READ-34] A payslip PDF shared as a roster says it is a payslip and where it goes, instead of "날짜를 찾지 못했어요". */
    @Test fun aPayslipSharedAsARosterSaysWhereItGoes() = runBlocking {
        val r = RosterReader.file(context, uri("slip-s01-side.pdf"), codes, near)
        assertEquals(RosterReader.PAYSLIP_HERE, r.error)
        // A roster PDF is still a roster.
        assertNull(RosterReader.file(context, uri("p01-pdf.pdf"), codes, near).error)
    }

    // 1.39.1 [R391-13] 검토 SCAN-01: a scanned PDF's page whose reading failed (글자 인식 준비물 not there, Play 서비스 old) closed
    // the app; it says why instead.
    @Test fun aScannedPageThatFailsToReadSaysWhy() = kotlinx.coroutines.runBlocking {
        try {
            com.bamnat.app.imports.RosterScanner.fakePicture = { throw com.bamnat.app.pay.OcrModel.NotReady() }
            val page = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
            val r = com.bamnat.app.ui.imports.RosterReader.pages(context, listOf(page), codes, near) { _, _ -> }
            org.junit.Assert.assertTrue(r.rows.isEmpty())
            org.junit.Assert.assertEquals(com.bamnat.app.pay.OcrModel.NotReady().message, r.error)
            org.junit.Assert.assertTrue("the page is let go", page.isRecycled)
            com.bamnat.app.imports.RosterScanner.fakePicture = { throw IllegalStateException("Play 서비스") }
            val other = com.bamnat.app.ui.imports.RosterReader.pages(context, listOf(android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)), codes, near) { _, _ -> }
            org.junit.Assert.assertTrue(other.error.orEmpty().isNotBlank())
        } finally {
            com.bamnat.app.imports.RosterScanner.fakePicture = null
        }
    }
}
