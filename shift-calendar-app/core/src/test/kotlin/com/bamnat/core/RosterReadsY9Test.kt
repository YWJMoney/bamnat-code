package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Y9 (1.36 PC 재검토): roster1.jpg as ML Kit read it on the emulator, all five readings (`adb logcat -s BamnatOcr`).
 * One reading broke every person's line into nameless pieces and was taken as the anchor, so four people came out as
 * eight lines. Now: four people, each once, and no cell put in that the photo does not say.
 */
class RosterReadsY9Test {
    private val types = listOf(
        DutyType(1, "주간", "주", false, LocalTime.of(7, 0)),
        DutyType(2, "야간", "야", false, LocalTime.of(19, 0), night = true),
        DutyType(3, "휴무", "휴", true),
    )
    private val codes = DutyCodes(types)
    private val letter = mapOf(1L to 'D', 2L to 'N', 3L to 'O')

    /** What the photo says, D/N/O by day. */
    private val truth = mapOf(
        "김간호" to "DDNNOODDNNOODDNNOODDNNOODDNNOOD",
        "홍길동" to "NNOODDNNOODDNNOODDNNOODDNNOODDD",
        "이수진" to "OODDNNOODDNNOODDNNOODDNNOODDDNN",
        "박민수" to "DDNNOODDNNOODDNNOODDNNOODDDNNOO",
    )

    private fun reads(): List<List<RosterWord>> {
        val reads = mutableListOf<MutableList<RosterWord>>()
        javaClass.getResource("/roster-reads/roster1-pc.tsv")!!.readText().lines().forEach { l ->
            if (l.startsWith("# read")) reads += mutableListOf<RosterWord>()
            val f = l.split('\t')
            if (f[0] == "word") reads.last() += RosterWord(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
        }
        return reads
    }

    @Test fun fourPeopleComeOutAsFourLinesWithNothingWrong() {
        val merged = RosterVote.merge(reads().map { DutyRoster.analyse(it, codes) })
        assertEquals(truth.keys, merged.rows.map { it.label }.toSet())
        assertEquals(4, merged.rows.size)
        for (row in merged.rows) {
            val want = truth.getValue(row.label)
            val wrong = row.cells.filter { (d, c) -> c is DutyCell.Shift && letter[c.shiftTypeId] != want[d - 1] }
            assertTrue("${row.label}: $wrong", wrong.isEmpty())
            assertTrue("${row.label} kept only ${row.cells.size} days", row.cells.size >= 25)
        }
    }

    @Test fun aNamelessLineSayingWhatANamedOneSaysIsFoldedIntoIt() {
        val merged = RosterVote.merge(reads().map { DutyRoster.analyse(it, codes) })
        assertEquals(31, merged.rows.single { it.label == "박민수" }.cells.size)
    }
}
