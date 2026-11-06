package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [READ-41] 1.38 PC 12차: the sample payslips s02·s03 (made-up amounts, tools/slip_screen_bench.py) as ML Kit read them on the
 * emulator, every reading (`adb logcat -s BamnatOcr`). On the phone 3 and 2 lines went missing that the bench reads.
 */
class SlipDeviceReplayTest {
    private fun reads(name: String): List<List<OcrLine>> {
        val reads = mutableListOf<MutableList<OcrLine>>()
        javaClass.getResource("/slip-screen-device/$name.tsv")!!.readText().lines().forEach { l ->
            if (l.startsWith("# slip read")) reads += mutableListOf<OcrLine>()
            val f = l.split('\t')
            if (f[0] == "word") reads.last() += OcrLine(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
        }
        return reads
    }

    private fun truth(name: String) = javaClass.getResource("/slip-screen/$name.truth.tsv")!!.readText().lines()
        .map { it.split('\t') }.filter { it[0] == "item" }

    private fun check(device: String, bench: String) {
        val r = SlipReading.read(reads(device))
        val lines = r.read.lines
        println("$device: " + lines.joinToString(" | ") { "${it.name}=${it.amount}${if (lines.indexOf(it) in r.unsure) "?" else ""}" })
        // Each item once: two lines of 21,000 need two lines read with 21,000.
        val pool = lines.filter { it.role !in setOf(SlipRole.GROSS, SlipRole.TOTAL, SlipRole.DEDUCTION_TOTAL, SlipRole.NET) }.mapNotNull { it.amount }.toMutableList()
        val missing = truth(bench).filter { t -> !pool.remove(t[2].toLong()) }
        // An amount the payslip has once: the line with it carries that line's name.
        val once = truth(bench).groupBy { it[2] }.filterValues { it.size == 1 }.values.map { it.single() }
        val misnamed = once.filter { t -> lines.filter { it.amount == t[2].toLong() }.let { ls -> ls.isNotEmpty() && ls.none { SlipNameKey(it.name).take(3) == SlipNameKey(t[1]).take(3) } } }
        println("$device missing ${missing.map { it[1] }} misnamed ${misnamed.map { it[1] + "→" + lines.first { l -> l.amount == it[2].toLong() }.name }}")
        assertEquals("$device 못 읽은 줄", emptyList<String>(), missing.map { it[1] })
        assertEquals("$device 이름이 다른 줄", emptyList<String>(), misnamed.map { it[1] })
    }

    private fun SlipNameKey(s: String) = s.filter { it.isLetterOrDigit() }.replace("1", "I").uppercase()

    @Test fun s02() = check("s02", "s02-monitor-dim")
    @Test fun s03() = check("s03", "s03-side-angle")
}
