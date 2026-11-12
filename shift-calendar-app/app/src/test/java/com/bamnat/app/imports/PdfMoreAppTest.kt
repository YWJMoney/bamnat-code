package com.bamnat.app.imports

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.imports.RosterReader
import com.bamnat.app.ui.pay.PdfSlips
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyType
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipVote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 2.0.0 갈래 L 5차, where the app takes a PDF (the files are `tools/slip_pdf_more.py`'s, made-up amounts):
 * - [RVL-41] (검토 U07b-10·I07bb-01) A locked payslip asks for its password where it was picked, says so when it is wrong, and
 *   reads every line with the right one; a 근무표 file locked the same way asks the same.
 * - [RVL-44] (검토 SCAN-28·I07bb-04) Three people's October payslips in one file are three to pick from, each named so the three
 *   10월s are told apart; the list waits for the next one, with a ✓ on the one looked at.
 * - [RVL-45] (검토 SCAN-23) Pictures left in the cache by an app closed meanwhile go at the next start; one just taken stays.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PdfMoreAppTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val codes = DutyCodes(listOf(DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(4, "오프", "O", true)))
    private val near = LocalDate.of(2026, 11, 5)

    private fun uri(name: String): Uri {
        val f = File(context.cacheDir, name)
        f.writeBytes(javaClass.classLoader!!.getResourceAsStream("roster-files/$name")!!.readBytes())
        return Uri.fromFile(f)
    }

    @Test fun aLockedPayslipAsksItsPasswordAndReadsWithIt() = runBlocking {
        val pdf = uri("slip-l-aes-128.pdf")
        assertEquals(PayslipScanner.PdfRead.Locked(PdfLock.ASK), PayslipScanner.scanPdf(context, pdf))
        assertEquals(PayslipScanner.PdfRead.Locked(PdfLock.WRONG), PayslipScanner.scanPdf(context, pdf, "900102".toCharArray()))
        val read = PayslipScanner.scanPdf(context, pdf, "900101".toCharArray()) as PayslipScanner.PdfRead.Slips
        val slip = read.scans.single().slip.read
        assertEquals(YearMonth.of(2026, 10), slip.month)
        assertEquals(3_914_820L, slip.gross)
        assertEquals(3_429_060L, slip.net)
        assertNull("one page, all read", read.cut)
    }

    @Test fun aLockedFileForTheRosterAsksTheSame() = runBlocking {
        val pdf = uri("slip-l-aes-128.pdf")
        val asked = RosterReader.file(context, pdf, codes, near)
        assertEquals(PdfLock.ASK, asked.locked)
        assertTrue(asked.rows.isEmpty())
        assertEquals(PdfLock.WRONG, RosterReader.file(context, pdf, codes, near, password = "000000".toCharArray()).locked)
        // Opened, it is a payslip, and says where that goes.
        val opened = RosterReader.file(context, pdf, codes, near, password = "900101".toCharArray())
        assertNull(opened.locked)
        assertEquals(RosterReader.PAYSLIP_HERE, opened.error)
        val o = AnyFile.open(context, pdf)
        assertTrue("$o", o is AnyFile.Opened.Locked && o.lock == PdfLock.ASK)
    }

    @Test fun threePeoplesPayslipsAreThreeToPickFrom() = runBlocking {
        val read = PayslipScanner.scanPdf(context, uri("slip-m-three.pdf")) as PayslipScanner.PdfRead.Slips
        assertEquals(3, read.scans.size)
        assertEquals(listOf(3_429_060L, 2_983_150L, 3_105_330L), read.scans.map { it.slip.read.net })
        // Three 10월s: told apart by place and amount.
        assertEquals(listOf("1번째 명세서 · 10월", "2번째 명세서 · 10월", "3번째 명세서 · 10월"), PdfSlips.labels(read.scans, thisYear = 2026))
        assertEquals("실수령 3,429,060원", PdfSlips.amount(read.scans[0]))
        // Picked: the one shown is that payslip, not the three put together.
        val second = PayslipScanner.shown(context, read, 1)
        assertEquals(2_983_150L, second.slip.read.net)
    }

    @Test fun monthsApartAreNamedByMonth() {
        fun scan(month: YearMonth?, net: Long?) = PayslipScanner.Scan(
            "", emptyList(), SlipVote.Result(PayslipRead(listOfNotNull(net?.let { SlipLine("실수령액", SlipRole.NET, it) }), month), emptySet(), ""),
            emptyList(), null, null,
        )
        val year = listOf(scan(YearMonth.of(2025, 12), 2_400_000), scan(YearMonth.of(2026, 1), 2_410_000), scan(YearMonth.of(2026, 2), null))
        assertEquals(listOf("2025년 12월 명세서", "1월 명세서", "2월 명세서"), PdfSlips.labels(year, thisYear = 2026))
        assertNull(PdfSlips.amount(year[2]))
        // A month not read: by place, the month where there is one.
        assertEquals(listOf("1번째 명세서 · 1월", "2번째 명세서"), PdfSlips.labels(listOf(year[1], scan(null, 1)), thisYear = 2026))
    }

    @Test fun theListWaitsForTheNextOneAndGoesWhenAllAreSeen() = runBlocking {
        PdfSlips.forget()
        val read = PayslipScanner.scanPdf(context, uri("slip-m-three.pdf")) as PayslipScanner.PdfRead.Slips
        val now = PdfSlips.put(read, locked = false)
        assertTrue(now.seen.isEmpty())
        PdfSlips.looked(now.id, 0)
        val again = PdfSlips.get()!!
        assertEquals(setOf(0), again.seen)
        assertEquals(2, again.left)
        PdfSlips.looked(now.id, 1)
        PdfSlips.looked(now.id, 2)
        assertNull("all three seen", PdfSlips.get())
        // A locked file's list keeps no way to draw its pages (its password went with the screen).
        PdfSlips.put(read, locked = true)
        assertEquals(3, PdfSlips.get()!!.read.scans.size)
        PdfSlips.forget()
        assertNull(PdfSlips.get())
    }

    @Test fun picturesLeftBehindGoAtTheNextStart() {
        val scan = File(context.cacheDir, "scan").apply { mkdirs() }
        val old = File(scan, "payslip.jpg").apply { writeText("x") }
        val oldPdf = File(context.cacheDir, "roster123.pdf").apply { writeText("x") }
        val fresh = File(scan, "roster.jpg").apply { writeText("x") }
        val other = File(context.cacheDir, "company_search.json").apply { writeText("{}") }
        val now = System.currentTimeMillis()
        old.setLastModified(now - 2 * ScanCache.KEEP_MS)
        oldPdf.setLastModified(now - 2 * ScanCache.KEEP_MS)
        other.setLastModified(now - 2 * ScanCache.KEEP_MS)
        assertEquals(2, ScanCache.sweep(context, now))
        assertFalse(old.exists())
        assertFalse(oldPdf.exists())
        assertTrue("a photo just taken stays", fresh.exists())
        assertTrue("not a picture for reading", other.exists())
    }
}
