package com.bamnat.core

import com.bamnat.core.imports.PdfText
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.YearMonth

/**
 * [READ-34] 1.38 급여명세서 PDF 정답 묶음 (`tools/slip_pdf_bench.py`): the payslips a 회사 ERP, a 급여 프로그램 and a 노무 site save as
 * PDF, read the way the app reads them (the PDF's own letters, [PdfText], put together as a payslip, [SlipReading]), no OCR.
 * 1.37 could not take a PDF payslip at all (the picker took pictures only). The bar: every line and total right, nothing
 * quietly wrong.
 */
class SlipPdfBenchTest {
    private val dir = javaClass.classLoader.getResource("slip-pdf")?.toURI()?.let(::File)

    /** A PDF's words as the payslip reader takes a photo's: one reading per page. */
    private fun read(pdf: ByteArray): SlipVote.Result? {
        val pages = PdfText.pages(pdf) ?: return null
        return SlipReading.pages(pages.map { words -> SlipReading.read(listOf(words.map { OcrLine(it.text, it.left, it.top, it.right, it.bottom) })) })
    }

    private fun hit(name: String, amount: Long, r: PayslipRead): Boolean = when (name) {
        "지급합계" -> r.gross == amount
        "공제합계" -> r.deducted == amount
        "실수령액" -> r.net == amount
        else -> r.lines.any { it.amount == amount && (it.name.replace(" ", "").contains(name.take(2)) || it.role == PayslipReader.roleOf(name)) }
    }

    @Test fun everyPdfPayslipReadsEveryLineAndNothingQuietlyWrong() {
        val d = dir ?: return
        var all = 0; var right = 0; var silent = 0
        for (t in d.listFiles()!!.filter { it.name.endsWith(".truth.tsv") }.sortedBy { it.name }) {
            val case = t.name.removeSuffix(".truth.tsv")
            val rows = t.readLines().map { it.split('\t') }
            val want = rows.filter { it[0] == "item" || it[0] == "total" }.map { it[1] to it[2].toLong() }
            val r = read(File(d, "$case.pdf").readBytes())
            val got = want.count { (n, a) -> r != null && hit(n, a, r.read) }
            val month = rows.first { it[0] == "month" }[1]
            // Quietly wrong: nothing asked, yet the total or the pay is not the payslip's.
            val wrong = r != null && r.sure && (r.read.gross != want.first { it.first == "지급합계" }.second || r.read.net != want.first { it.first == "실수령액" }.second)
            if (wrong) silent++
            println("READ-34 ${case.padEnd(10)} $got/${want.size} sure=${r?.sure} month=${r?.read?.month} " +
                want.filter { (n, a) -> r == null || !hit(n, a, r.read) }.joinToString { "${it.first}:${it.second}" } +
                " | " + r?.read?.lines?.joinToString { "${it.name}=${it.amount}/${it.role}" })
            all += want.size; right += got
            assertEquals("$case month", YearMonth.parse(month), r?.read?.month)
            assertEquals("$case every line", want.size, got)
            assertEquals("$case quietly wrong", false, wrong)
        }
        println("READ-34 total $right/$all silent $silent (1.37: PDF 명세서는 고를 수도 없었어요, 0/$all)")
    }
}
