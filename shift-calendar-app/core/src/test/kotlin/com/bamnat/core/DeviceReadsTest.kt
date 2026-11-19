package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 2.0.0 [RVL-68] (검토 SCAN-34) Every reading the emulator's ML Kit made of a made-up roster, as `tools/ocr_capture.py` saves it in
 * `roster-reads/` with the photo's drawn truth: the phone's own readings (joined cells like 'NOFFOFF', its own tilt), not RapidOCR's
 * on a PC. Each person's line must be found, and a cell on a wrong day stays under 1 in 100. A new capture is a new case here.
 */
class DeviceReadsTest {
    private val types = listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)),
        DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true),
        DutyType(4, "오프", "O", true),
    )
    private val codes = DutyCodes(types)

    private class Capture(val name: String, val truth: Map<String, List<String>>, val reads: List<List<RosterWord>>)

    private fun captures(): List<Capture> {
        val dir = java.io.File(javaClass.classLoader.getResource("roster-reads")!!.toURI())
        return dir.listFiles { f -> f.name.endsWith(".tsv") }!!.sortedBy { it.name }.mapNotNull { f ->
            val truth = LinkedHashMap<String, List<String>>()
            val reads = mutableListOf<MutableList<RosterWord>>()
            f.readLines(Charsets.UTF_8).forEach { l ->
                val c = l.split('\t')
                when {
                    l.startsWith("# read") -> reads += mutableListOf<RosterWord>()
                    c[0] == "truth" -> truth[c[1]] = c[2].split(',')
                    c[0] == "word" && reads.isNotEmpty() -> reads.last() += RosterWord(c[6], c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt(), angle = c[5].toFloat())
                }
            }
            Capture(f.name, truth, reads).takeIf { truth.isNotEmpty() && reads.isNotEmpty() }
        }
    }

    private fun expected(code: String): DutyCell? = codes.read(code)?.first

    @Test fun everyPersonIsFoundAndCellsLandOnTheirDays() {
        val all = captures()
        assertTrue("no capture with a truth in roster-reads/", all.isNotEmpty())
        var read = 0
        var wrong = 0
        val lost = mutableListOf<String>()
        for (cap in all) {
            val rows = RosterVote.merge(cap.reads.map { DutyRoster.analyse(it, codes) }).rows
            val line = StringBuilder("RVL-68 ${cap.name.padEnd(28)}")
            for ((label, want) in cap.truth) {
                val best: RosterRow? = rows.firstOrNull { it.label == label }
                    ?: rows.maxByOrNull { r -> want.withIndex().count { (i, c) -> r.cells[i + 1] == expected(c) } }
                val right = best?.let { r -> want.withIndex().count { (i, c) -> r.cells[i + 1] == expected(c) } } ?: 0
                val bad = best?.cells?.count { (d, c) -> c !is DutyCell.Unknown && want.getOrNull(d - 1)?.let(::expected) != c } ?: 0
                read += best?.cells?.count { it.value !is DutyCell.Unknown } ?: 0
                wrong += bad
                if (best == null || right < 7 || right <= bad * 3) lost += "${cap.name} $label $right/${want.size} x$bad"
                line.append(" $right/${want.size}").append(if (bad > 0) "(x$bad)" else "")
            }
            println(line)
        }
        assertTrue("lines not found:\n" + lost.joinToString("\n"), lost.isEmpty())
        assertTrue("cells on a wrong day: $wrong of $read", wrong * 100 <= read)
    }
}
