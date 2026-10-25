package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayslipReader
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [READ-07] Payslips photographed the way a phone does (tilted 0~4°, skewed, soft, shaded) and read line by line by a
 * real OCR (`tools/payslip_fixtures.py`; 사용자 2026-10-25 "급여 명세서를 보내도 제대로 인식하지 못하고"): put back on
 * their rows and read as the app does, every amount drawn must come out with its own name. A tilted photo used to put a
 * name beside the next row's amount (기본급 → 국민연금's 135,000).
 */
class PayslipPhotoFixturesTest {
    private class Fixture(val truth: Map<String, Long>, val lines: List<OcrLine>)

    private fun load(file: String, drawn: Boolean): Fixture {
        val text = javaClass.classLoader.getResourceAsStream("payslip-photos/$file")!!.bufferedReader(Charsets.UTF_8).readText()
        val truth = LinkedHashMap<String, Long>()
        val lines = mutableListOf<OcrLine>()
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val f = line.split('\t')
            when (f[0]) {
                "truth" -> truth[f[1]] = f[2].toLong()
                "line" -> lines += OcrLine(if (drawn && f.size > 7 && f[7].isNotEmpty()) f[7] else f[6],
                    f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
            }
        }
        return Fixture(truth, lines)
    }

    private fun files(): List<String> =
        java.io.File(javaClass.classLoader.getResource("payslip-photos")!!.toURI()).list()!!.filter { it.endsWith(".tsv") && !it.startsWith("rules-") }.sorted()

    /** Names whose amount did not come out with them. */
    private fun misses(file: String, drawn: Boolean): List<String> {
        val f = load(file, drawn)
        val read = PayslipReader.read(OcrRows.join(f.lines))
        val miss = f.truth.filter { (name, amount) ->
            read.lines.none { it.amount == amount && (it.name.take(2) == name.take(2) || it.role == PayslipReader.roleOf(name)) }
        }.keys.toList()
        println("READ-07 ${if (drawn) "drawn " else ""}${file.padEnd(22)} ${f.truth.size - miss.size}/${f.truth.size}" +
            if (miss.isEmpty()) "" else " missed $miss · read ${read.lines.map { "${it.name} ${it.amount}" }}")
        return miss
    }

    @Test fun everyAmountWithItsName() {
        val all = files()
        assertTrue(all.size >= 6)
        val bad = all.associateWith { misses(it, drawn = true) }.filterValues { it.isNotEmpty() }
        assertTrue("rows put back wrong: $bad", bad.isEmpty())
    }

    @Test fun netPayEvenWhenItsDigitsAreCut() {
        // 04 and 05 read 실수령액 as "2,856,0": 지급 총액 − 공제 합계 says what it is.
        files().forEach { file ->
            val read = PayslipReader.read(OcrRows.join(load(file, false).lines))
            assertTrue("$file net ${read.net}", read.net == 2_856_500L)
        }
    }

    @Test fun asReadMostAmounts() {
        val all = files()
        val missed = all.sumOf { misses(it, drawn = false).size }
        val total = all.sumOf { load(it, false).truth.size }
        println("READ-07 as read: ${total - missed}/$total")
        assertTrue("$missed of $total amounts missed", missed * 10 <= total)
    }
}
