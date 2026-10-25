package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayPhotos
import com.bamnat.core.pay.RuleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [READ-08] The two photos sent together (사용자 2026-10-25): a payslip and a page of the company's pay rules, both
 * photographed tilted and read by a real OCR (`tools/payslip_fixtures.py`). The rules page must give the rules its typed
 * text gives, and the two photos must be told apart in either order.
 */
class PayRulesPhotoFixturesTest {
    private fun text(file: String): Pair<String, List<String>> {
        val raw = javaClass.classLoader.getResourceAsStream("payslip-photos/$file")!!.bufferedReader(Charsets.UTF_8).readText()
        val lines = mutableListOf<OcrLine>()
        val typed = mutableListOf<String>()
        raw.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
            val f = l.split('\t')
            when (f[0]) {
                "rule" -> typed += f[1]
                "line" -> lines += OcrLine(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
            }
        }
        return OcrRows.join(lines) to typed
    }

    private fun rulesFiles() = java.io.File(javaClass.classLoader.getResource("payslip-photos")!!.toURI()).list()!!.filter { it.startsWith("rules-") }.sorted()

    @Test fun rulesPagePhotoGivesTheTypedRules() {
        assertTrue(rulesFiles().size >= 2)
        rulesFiles().forEach { file ->
            val (read, typed) = text(file)
            val want = RuleText.read(typed.joinToString("\n")).map { it.change.summary }.toSet()
            val got = RuleText.read(read).map { it.change.summary }.toSet()
            println("READ-08 $file typed ${want.size} photo ${got.size}: missed ${want - got}, extra ${got - want}\n$read")
            assertTrue("$file: typed text gives rules", want.size >= 5)
            assertTrue("$file missed ${want - got}", (want - got).size <= 1)
        }
    }

    @Test fun twoPhotosToldApartEitherOrder() {
        val slip = text("03-side-tilt2.tsv").first
        val rules = text("rules-01-tilt2.tsv").first
        listOf(listOf(slip, rules), listOf(rules, slip)).forEach { texts ->
            val p = PayPhotos.read(texts)
            assertEquals(texts.indexOf(slip), p.slipIndex)
            assertEquals(2_508_000L, p.slip!!.amount(com.bamnat.core.pay.SlipRole.BASE))
            assertTrue(p.rules.size >= 5)
            // 389,500 of 3,246,000 taken off: 12.0%.
            assertEquals(0.12, p.deductionRate!!, 0.001)
        }
    }

    @Test fun onePhotoOfRulesAlone() {
        val p = PayPhotos.read(listOf(text("rules-02-tilt3.tsv").first))
        assertEquals(-1, p.slipIndex)
        assertTrue(p.rules.isNotEmpty())
    }
}
