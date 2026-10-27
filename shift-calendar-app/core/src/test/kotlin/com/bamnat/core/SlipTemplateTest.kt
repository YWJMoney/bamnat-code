package com.bamnat.core

import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipTemplate
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [PHOTO-24] 1.30 AV: the payslip the user confirmed last month as the company's template ([SlipTemplate]). This month's photo,
 * worse (names not read, heads lost, taken from the side), reads the lines the template knows, in the template's order, and
 * never silently: a line found by the template is yellow.
 */
class SlipTemplateTest {
    private fun key(s: String) = PayslipReader.ocrFix(s.filter { it.isLetter() }).take(2)

    private fun hit(l: SlipLine, item: PlantSample.Item): Boolean {
        if (l.amount != item.amount || l.role !in item.role) return false
        if (item in PlantSample.TOTALS) return true
        val name = SlipLexicon.of(l.name)?.takeIf { it.distance > 0 }?.name ?: l.name
        return key(name) == key(item.name)
    }

    /** The lines as the user confirmed them: the right ones, by the names the payslip prints. */
    private val confirmed = PayslipRead(PlantSample.ALL.map { SlipLine(it.name, it.role.first(), it.amount, PlantSample.HOURS[it.name]) })

    private fun template(): SlipTemplate = SlipTemplate.learn(confirmed, PlantSample.payslip(PlantSample.Noise.CLEAN, Random(5)))!!

    @Test fun learnedWithoutAmounts() {
        val t = template()
        assertTrue("${t.lines.size}", t.lines.size >= PlantSample.ALL.size - 1)
        val text = t.encode()
        PlantSample.ALL.forEach { assertTrue(it.name, !text.contains("%,d".format(it.amount)) && !text.contains(it.amount.toString())) }
        assertEquals(t, SlipTemplate.decode(text))
        assertNull(SlipTemplate.decode("v0\nx"))
        assertNull(SlipTemplate.decode(null))
        // The same payslip learned again replaces the one kept; another payslip is kept beside it.
        val other = SlipTemplate(listOf(SlipTemplate.Line("봉급", SlipRole.BASE, 1.0, 1.0, 2.0, 2.0, 10.0),
            SlipTemplate.Line("정근수당", SlipRole.ALLOWANCE, 1.0, 20.0, 2.0, 20.0, 10.0), SlipTemplate.Line("기여금", SlipRole.DEDUCTION, 1.0, 40.0, 2.0, 40.0, 10.0)))
        assertEquals(listOf(t, other), SlipTemplate.keep(SlipTemplate.keep(listOf(other), t), t))
        assertEquals(t, SlipTemplate.pick(listOf(other, t), confirmed))
    }

    private class Score { var items = 0; var hit = 0; var silent = 0; var yellow = 0 }

    private fun score(s: Score, res: SlipVote.Result) {
        val lines = res.read.lines
        s.items += PlantSample.ALL.size
        s.hit += PlantSample.ALL.count { item -> lines.any { hit(it, item) } }
        lines.forEachIndexed { i, l -> if (l.amount != null && i !in res.unsure && PlantSample.ALL.none { hit(l, it) }) { s.silent++; println("  SILENT ${l.role} ${l.name} ${l.amount}") } }
        s.yellow += res.unsure.size
    }

    /** Names lost (30% of them not read at all), the heads lost too, from the side: the template brings the lines back. */
    @Test fun lostNamesComeBack() {
        val t = template()
        val without = Score()
        val with = Score()
        for (seed in 0 until 20) {
            val n = PlantSample.Noise(digit = 0.04, comma = 0.1, mark = 0.6, jitter = 5, lose = 0.3, headLost = seed % 2 == 0,
                perspective = if (seed % 3 == 0) 0.08 else 0.0, scale = if (seed % 4 == 1) 0.7 else 1.0)
            val reads = (0 until 3).map { w -> PlantSample.payslip(n, Random(seed * 17 + w), seed % 5 == 4) }
            score(without, SlipReading.read(reads))
            score(with, SlipReading.read(reads, listOf(t)))
        }
        fun pct(s: Score) = "%.1f%%".format(100.0 * s.hit / s.items)
        println("명세서 틀 없이 ${without.hit}/${without.items} (${pct(without)}), 노란 줄 ${without.yellow}, 조용히 틀림 ${without.silent}")
        println("명세서 틀로   ${with.hit}/${with.items} (${pct(with)}), 노란 줄 ${with.yellow}, 조용히 틀림 ${with.silent}")
        assertEquals(0, with.silent)
        assertTrue("${with.hit} > ${without.hit}", with.hit > without.hit)
        assertTrue(pct(with), with.hit >= with.items * 0.97)
    }

    /** The lines come in the order the user confirmed, whatever reading led. */
    @Test fun inTheTemplatesOrder() {
        val t = template()
        val order = t.lines.map { SlipTemplate.key(it.name) }
        for (seed in 0 until 6) {
            val n = PlantSample.Noise(digit = 0.05, comma = 0.1, roman = 0.5, mark = 0.7, jitter = 6, split = 0.2)
            val res = SlipReading.read((0 until 3).map { w -> PlantSample.payslip(n, Random(100 + seed * 7 + w)) }, listOf(t))
            val got = res.read.lines.map { SlipTemplate.key(it.name) }.filter { it in order }
            assertEquals("seed $seed", got, order.filter { it in got })
        }
    }

    /** Another company's payslip is not read with this one's template. */
    @Test fun anotherPayslipIsNotPicked() {
        val t = template()
        val other = PayslipRead(listOf(SlipLine("봉급", SlipRole.BASE, 2_000_000), SlipLine("정근수당", SlipRole.ALLOWANCE, 100_000),
            SlipLine("기여금", SlipRole.DEDUCTION, 200_000), SlipLine("소득세", SlipRole.DEDUCTION, 50_000)))
        assertNull(SlipTemplate.pick(listOf(t), other))
        assertNotNull(SlipTemplate.pick(listOf(t), confirmed))
    }
}
