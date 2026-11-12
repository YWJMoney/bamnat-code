package com.bamnat.core

import com.bamnat.core.imports.PdfText
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 갈래 L 5차 (`tools/slip_pdf_more.py`): the payslip PDFs 1.38's bench left out, read the way the app reads them.
 * - [RVL-41] (검토 U07b-10·I07bb-01) Locked with a password (a payslip mailed by the company, the 생년월일): RC4 40·128, AES-128,
 *   AES-256 (R5·R6). Without it the file says it is locked; a wrong one says so; the right one (user's or owner's) reads every
 *   line as the unlocked file does. A file locked only against printing opens without asking.
 * - [RVL-42] (검토 SCAN-15) Old Korean fonts (KSCms-UHC, UniKS-UCS2) read as Korean, not Latin marks; Korean bytes under a Latin
 *   font are no text (read as a picture instead); a page turned by /Rotate reads as it is shown.
 * - [RVL-43] (검토 U07b-09) A file of more pages than are read says how many there were.
 */
class SlipPdfMoreTest {
    private val dir = javaClass.classLoader.getResource("slip-pdf-more")?.toURI()?.let(::File)!!
    private val truth = javaClass.classLoader.getResource("slip-pdf/s01-side.truth.tsv")!!.readText().lines().filter { it.isNotBlank() }.map { it.split('\t') }

    private fun bytes(name: String) = File(dir, name).readBytes()

    private fun slip(pages: List<List<RosterWord>>): SlipVote.Result =
        SlipReading.pages(pages.map { words -> SlipReading.read(listOf(words.map { OcrLine(it.text, it.left, it.top, it.right, it.bottom) })) })

    private fun hit(name: String, amount: Long, r: PayslipRead): Boolean = when (name) {
        "지급합계" -> r.gross == amount
        "공제합계" -> r.deducted == amount
        "실수령액" -> r.net == amount
        else -> r.lines.any { it.amount == amount && (it.name.replace(" ", "").contains(name.take(2)) || it.role == PayslipReader.roleOf(name)) }
    }

    /** Every line of s01's payslip, its month and nothing quietly wrong. */
    private fun assertS01(case: String, pages: List<List<RosterWord>>) {
        val r = slip(pages)
        val want = truth.filter { it[0] == "item" || it[0] == "total" }.map { it[1] to it[2].toLong() }
        val missed = want.filterNot { (n, a) -> hit(n, a, r.read) }
        assertEquals("$case every line: $missed | ${r.read.lines.joinToString { "${it.name}=${it.amount}" }}", emptyList<Pair<String, Long>>(), missed)
        assertEquals("$case month", YearMonth.parse(truth.first { it[0] == "month" }[1]), r.read.month)
    }

    @Test fun aLockedPayslipAsksForItsPasswordAndReadsEveryLineWithIt() {
        for (case in listOf("l-rc4-40", "l-rc4-128", "l-aes-128", "l-aes-256-r5", "l-aes-256")) {
            val pdf = bytes("$case.pdf")
            assertEquals(case, PdfText.Opened.Locked(wrong = false), PdfText.open(pdf))
            assertEquals(case, PdfText.Opened.Locked(wrong = true), PdfText.open(pdf, "900102".toCharArray()))
            assertEquals("$case: no password, no words", null, PdfText.pages(pdf))
            val user = PdfText.open(pdf, "900101".toCharArray())
            assertTrue("$case user: $user", user is PdfText.Opened.Words)
            assertS01("$case user", (user as PdfText.Opened.Words).pages)
            // The owner's password opens it too.
            val owner = PdfText.open(pdf, "owner-secret".toCharArray()) as PdfText.Opened.Words
            assertS01("$case owner", owner.pages)
            assertEquals(1, owner.total)
            assertFalse(owner.cut)
        }
    }

    @Test fun aFileLockedOnlyAgainstPrintingOpensWithoutAsking() {
        val o = PdfText.open(bytes("l-owner-only.pdf"))
        assertTrue("$o", o is PdfText.Opened.Words)
        assertS01("owner-only", (o as PdfText.Opened.Words).pages)
        assertTrue(PdfText.sheets(bytes("l-owner-only.pdf"))!!.isNotEmpty())
    }

    /** A locked scan: the password is checked here, then the pages go to the picture reader (the app renders them with it). */
    @Test fun aLockedScanIsCheckedThenReadAsPictures() {
        val pdf = bytes("l-scan-aes-128.pdf")
        assertEquals(PdfText.Opened.Locked(wrong = false), PdfText.open(pdf))
        assertEquals(PdfText.Opened.Locked(wrong = true), PdfText.open(pdf, "000000".toCharArray()))
        assertEquals(PdfText.Opened.NoText(1), PdfText.open(pdf, "900101".toCharArray()))
    }

    @Test fun oldKoreanFontsReadAsKoreanAndBrokenLettersAreNoText() {
        for (case in listOf("k-uhc", "k-ucs2")) {
            val o = PdfText.open(bytes("$case.pdf"))
            assertTrue("$case: $o", o is PdfText.Opened.Words)
            val words = (o as PdfText.Opened.Words).pages
            assertTrue(case, words.flatten().any { it.text == "급여명세서" })
            assertS01(case, words)
        }
        // 1.39: "±Þ¿©¸í¼¼¼­" was read as the payslip's words and nothing was found. Now it is no text: read as a picture.
        assertEquals(PdfText.Opened.NoText(1), PdfText.open(bytes("k-latin.pdf")))
        assertTrue(PdfText.broken(listOf(listOf(RosterWord("±Þ¿©¸í¼¼¼­", 0, 0, 10, 10), RosterWord("3,050,830", 20, 0, 40, 10)))))
        assertFalse(PdfText.broken(listOf(listOf(RosterWord("기본급", 0, 0, 10, 10), RosterWord("3,050,830", 20, 0, 40, 10), RosterWord("Café", 50, 0, 70, 10)))))
    }

    @Test fun aPageTurnedByRotateReadsAsItIsShown() {
        for (case in listOf("r-rotate90", "r-rotate270")) {
            val o = PdfText.open(bytes("$case.pdf")) as PdfText.Opened.Words
            val words = o.pages.single()
            // The title stands on one line across the top, as it is seen.
            val title = words.filter { it.text == "2026년" || it.text == "10월" || it.text == "급여명세서" }.sortedBy { it.left }
            assertEquals(case, listOf("2026년", "10월", "급여명세서"), title.map { it.text })
            assertTrue(case, title.maxOf { it.top } - title.minOf { it.top } < 8)
            // In the top fifth of the page as shown (842 points tall once turned).
            assertTrue("$case ${title.first().top}", title.first().top < PdfText.SCALE * 842 / 5)
            assertS01(case, o.pages)
        }
    }

    @Test fun aFileOfManyPagesSaysHowManyItHad() {
        val three = PdfText.open(bytes("m-three.pdf")) as PdfText.Opened.Words
        assertEquals(3, three.pages.size)
        assertEquals(3, three.total)
        // 22 pages: the first 20 are read and it says there were 22. Each page's stream /Length is a reference.
        val long = PdfText.open(manyPages(22)) as PdfText.Opened.Words
        assertEquals(PdfText.PAGES, long.pages.size)
        assertEquals(22, long.total)
        assertTrue(long.cut)
        assertEquals("page 20", long.pages.last().joinToString(" ") { it.text })
    }

    /**
     * [RVL-44] (검토 SCAN-28·I07bb-04) Three people's October payslips in one file are three payslips, each read whole; 1.39 put
     * them together into one with every line of the three.
     */
    @Test fun aFileOfThreePayslipsIsThreePayslips() {
        val pages = (PdfText.open(bytes("m-three.pdf")) as PdfText.Opened.Words).pages
        val each = pages.map { words -> SlipReading.read(listOf(words.map { OcrLine(it.text, it.left, it.top, it.right, it.bottom) })) }
        val slips = SlipReading.slips(each)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), slips)
        val nets = slips.map { g -> SlipReading.pages(g.map { each[it] }).read.net }
        assertEquals(listOf(3_429_060L, 2_983_150L, 3_105_330L), nets)
        assertS01("m-three first", listOf(pages[0]))
    }

    @Test fun aLongPayslipOnTwoPagesStaysOneAndAnotherMonthIsAnother() {
        val oct = YearMonth.of(2026, 10)
        fun r(month: YearMonth?, vararg lines: com.bamnat.core.pay.SlipLine) = SlipVote.Result(PayslipRead(lines.toList(), month), emptySet(), "")
        val earn = r(oct, com.bamnat.core.pay.SlipLine("기본급", com.bamnat.core.pay.SlipRole.BASE, 2_500_000),
            com.bamnat.core.pay.SlipLine("식대", com.bamnat.core.pay.SlipRole.ALLOWANCE, 200_000),
            com.bamnat.core.pay.SlipLine("지급합계", com.bamnat.core.pay.SlipRole.GROSS, 2_700_000))
        val deduct = r(null, com.bamnat.core.pay.SlipLine("국민연금", com.bamnat.core.pay.SlipRole.DEDUCTION, 112_500),
            com.bamnat.core.pay.SlipLine("공제합계", com.bamnat.core.pay.SlipRole.DEDUCTION_TOTAL, 300_000),
            com.bamnat.core.pay.SlipLine("실수령액", com.bamnat.core.pay.SlipRole.NET, 2_400_000))
        val nov = r(oct.plusMonths(1), com.bamnat.core.pay.SlipLine("기본급", com.bamnat.core.pay.SlipRole.BASE, 2_500_000),
            com.bamnat.core.pay.SlipLine("식대", com.bamnat.core.pay.SlipRole.ALLOWANCE, 200_000),
            com.bamnat.core.pay.SlipLine("지급합계", com.bamnat.core.pay.SlipRole.GROSS, 2_700_000))
        val notes = r(null)
        assertEquals(listOf(listOf(0, 1, 2)), SlipReading.slips(listOf(earn, deduct, notes)))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), SlipReading.slips(listOf(earn, deduct, nov, deduct)))
        assertEquals(listOf(listOf(0)), SlipReading.slips(listOf(earn)))
    }

    /** A plain PDF of [n] pages, "page 1" … "page n", each content stream's length an indirect object. */
    private fun manyPages(n: Int): ByteArray {
        val objs = mutableListOf<String>()
        objs += "<< /Type /Catalog /Pages 2 0 R >>"
        objs += "<< /Type /Pages /Kids [${(0 until n).joinToString(" ") { "${4 + it * 3} 0 R" }}] /Count $n >>"
        objs += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        for (i in 1..n) {
            val body = "BT /F1 12 Tf 72 700 Td (page $i) Tj ET"
            val page = 4 + (i - 1) * 3
            objs += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${page + 1} 0 R >>"
            objs += "<< /Length ${page + 2} 0 R >>\nstream\n$body\nendstream"
            objs += "${body.length}"
        }
        val sb = StringBuilder("%PDF-1.4\n")
        objs.forEachIndexed { i, o -> sb.append("${i + 1} 0 obj\n$o\nendobj\n") }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
