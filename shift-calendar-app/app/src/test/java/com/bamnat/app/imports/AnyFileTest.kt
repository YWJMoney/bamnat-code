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
    }

    @Test fun aRosterFileSharedFromAMessengerOpensTheRoster() {
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x/roster.pdf"))
        assertTrue(IncomingRoster.sharedOf(i) is IncomingRoster.Shared.File)
        val photo = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x/a.jpg"))
        assertTrue(IncomingRoster.sharedOf(photo) is IncomingRoster.Shared.Photo)
    }
}
