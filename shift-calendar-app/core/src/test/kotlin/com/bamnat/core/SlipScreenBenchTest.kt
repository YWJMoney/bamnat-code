package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [READ-38] 1.38 사용자 2026-11-06: payslips and 계산 방법 pages photographed off an HR portal's screen (moire, a diagonal
 * watermark, an angle), drawn by tools/slip_screen_bench.py with made-up amounts and read by a real OCR, word by word, each
 * made-over picture. Read here as the app reads them: [SlipReading] over all the readings, [RuleText] on the reading the
 * phone keeps for a rules page (the one most rules read from).
 */
class SlipScreenBenchTest {
    private fun dir(): File? = javaClass.classLoader.getResource("slip-screen")?.toURI()?.let(::File)

    private fun cases(): Map<String, Map<String, List<List<String>>>> =
        dir()?.listFiles()?.filter { it.name.endsWith(".tsv") }?.groupBy { it.name.substringBefore('.') }
            ?.mapValues { (_, fs) -> fs.associate { f -> f.name.split('.')[1] to f.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') } } }
            ?.toSortedMap().orEmpty()

    private fun words(rows: List<List<String>>) = rows.filter { it[0] == "word" }.map { f ->
        OcrLine(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
    }

    /** The variants in the phone's order: the photo as taken first. */
    private fun readings(variants: Map<String, List<List<String>>>) =
        variants.filterKeys { it != "truth" }.entries.sortedBy { if (it.key == "orig") 0 else 1 }.map { words(it.value) }

    @Test fun payslipsOffAScreenReadEveryAmountAndNeverQuietlyWrong() {
        val all = cases().filterKeys { it.startsWith("s") }
        if (all.isEmpty()) return
        var items = 0; var right = 0; var silent = 0; var totals = 0; var totalsRight = 0; var sure = 0
        for ((name, variants) in all) {
            val truth = variants.getValue("truth")
            val read = SlipReading.read(readings(variants))
            val lines = read.read.lines
            var caseRight = 0; var caseSilent = 0
            for (t in truth.filter { it[0] == "item" }) {
                items++
                val amount = t[2].toLong()
                val deduction = Regex("세$|보험|연금|회|조합|관리비|공제").containsMatchIn(t[1])
                if (lines.any { it.amount == amount && (it.role == SlipRole.DEDUCTION) == deduction && it.role !in TOTALS }) { right++; caseRight++ }
                else println("  $name 못 읽음 ${t[1]} ${t[2]}")
            }
            for (t in truth.filter { it[0] == "total" }) {
                totals++
                val want = t[2].toLong()
                val got = when (t[1]) { "지급합계" -> read.read.gross; "공제합계" -> lines.firstOrNull { it.role == SlipRole.DEDUCTION_TOTAL }?.amount; else -> read.read.net }
                if (got == want) totalsRight++ else println("  $name ${t[1]} want $want got $got")
            }
            val amounts = truth.map { it[2].toLong() }.toSet()
            lines.forEachIndexed { i, l -> if (l.amount != null && l.amount !in amounts && i !in read.unsure) { silent++; caseSilent++; println("  $name 조용히 틀림 ${l.name} ${l.amount}") } }
            if (read.sure) sure++
            println("READ-38 slip ${name.padEnd(18)} ${caseRight}/${truth.count { it[0] == "item" }} 조용히 틀림 $caseSilent sure=${read.sure} " +
                lines.joinToString(" ") { "${it.name}=${it.amount}" })
        }
        println("READ-38 slip total: items $right/$items, totals $totalsRight/$totals, silent $silent, sure $sure/${all.size}")
        assertEquals("틀린 금액이 노란 표시 없이", 0, silent)
        assertTrue("화면 사진 명세서 금액 $right/$items", right * 100 >= items * 95)
        assertTrue("화면 사진 명세서 합계 $totalsRight/$totals", totalsRight * 100 >= totals * 95)
    }

    @Test fun aRulesPageOffAScreenSetsEachPremium() {
        val all = cases().filterKeys { it.startsWith("r") }
        if (all.isEmpty()) return
        var want = 0; var got = 0; var wrong = 0
        for ((name, variants) in all) {
            val texts = readings(variants).map { OcrRows.join(it) }
            // As PayslipScanner keeps the text of a page with no amounts: the reading most rules read from.
            val text = texts.maxBy { RuleText.read(it).size }
            val premiums = RuleText.read(text).map { it.change }.filterIsInstance<RuleChange.Premium>()
            val truth = variants.getValue("truth").filter { it[0] == "rule" }
            for (t in truth) {
                want++
                if (premiums.any { it.target.name == t[1] && it.premium == t[2].toDouble() && it.over8h == t[3].toBoolean() }) got++
                else println("  $name 못 읽음 $t")
            }
            premiums.filter { p -> truth.none { it[1] == p.target.name && it[2].toDouble() == p.premium && it[3].toBoolean() == p.over8h } }
                .forEach { wrong++; println("  $name 틀린 규칙 $it") }
            println("READ-38 rules $name ${premiums.size} premiums")
        }
        println("READ-38 rules total: $got/$want, wrong $wrong")
        assertEquals("틀린 가산 규칙", 0, wrong)
        assertTrue("계산 방법 표 $got/$want", got * 100 >= want * 95)
    }

    private val TOTALS = setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET)

    @Suppress("unused") private fun SlipLine.show() = "$name=$amount"
}
