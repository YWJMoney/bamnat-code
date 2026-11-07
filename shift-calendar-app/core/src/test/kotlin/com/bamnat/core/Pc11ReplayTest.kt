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
 * [READ-41] 1.38 PC 11차 (56b4d8ee): two sample photos as ML Kit read them on the emulator, every reading
 * (`adb logcat -s BamnatOcr`). 조-근무표-빈칸6.png came out with 1조's 5·18일 blank and a nameless '3번째 줄 · 18일'; the blurry
 * duty-photo.jpg (12 people, three to each rota) came out as 10 lines with names cut short (정, 김, 유, 세).
 */
class Pc11ReplayTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true), DutyType(4, "오프", "O", true),
    ))

    private fun reads(name: String): List<List<RosterWord>> {
        val reads = mutableListOf<MutableList<RosterWord>>()
        javaClass.getResource("/roster-reads/$name")!!.readText().lines().forEach { l ->
            if (l.startsWith("# read")) reads += mutableListOf<RosterWord>()
            val f = l.split('\t')
            if (f[0] == "word") reads.last() += RosterWord(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
        }
        return reads
    }

    private fun letter(c: DutyCell?) = (c as? DutyCell.Shift)?.let { "DENO"[(it.shiftTypeId - 1).toInt()] }

    @Test fun theTeamRosterComesOutAsFourWholeLines() {
        // 주·야·비·휴 in the setup's 데이·나이트·오프: 비 and 휴 are both 오프 here.
        val order = "DNNOOD"
        val start = mapOf("1조" to 0, "2조" to 2, "3조" to 4, "4조" to 0)
        val merged = RosterVote.merge(reads("team11-pc.tsv").map { DutyRoster.analyse(it, codes) })
        assertEquals(listOf("1조", "2조", "3조", "4조"), merged.rows.map { it.label })
        for (r in merged.rows) {
            assertEquals("${r.label} days", (1..30).toSet(), r.cells.keys)
            for (d in 1..30) assertEquals("${r.label} ${d}일", order[(d - 1 + start.getValue(r.label)) % 6], letter(r.cells[d]))
        }
        // The photo's blanks (1조 5·18일) are filled from the repeat and said to be (PC 12차: 말없이 채우지 않게).
        assertTrue(merged.rows.first { it.label == "1조" }.filled.containsAll(setOf(5, 18)))
    }

    @Test fun aDaySplitBetweenReadingsTakesTheRepeatInYellow() {
        // PC 12차: 1조's 3일 read 야 and 비 alike, and the anchor's 비 was kept.
        val merged = RosterVote.merge(reads("team11-pc.tsv").map { DutyRoster.analyse(it, codes) })
        val row = merged.rows.first { it.label == "1조" }
        val split = row.copy(cells = (row.cells + (3 to DutyCell.Shift(4))).toSortedMap(), unsure = row.unsure + 3, guessed = row.guessed - 3)
        val again = com.bamnat.core.imports.RosterPeriod.fill(listOf(split)).single()
        assertEquals('N', letter(again.cells[3]))
        assertTrue(3 in again.guessed)
    }

    @Test fun theBlurryDutyRosterKeepsEveryoneWithNothingQuietlyWrong() {
        val truth = mapOf(
            "김민지" to 0, "이서연" to 2, "박지훈" to 4, "최수빈" to 6, "정하늘" to 0, "강다은" to 2, "윤서준" to 4, "임유진" to 6,
            "한지민" to 0, "오세훈" to 2, "서예린" to 4, "권도윤" to 6,
        )
        val rota = "DDEENNOO"
        val merged = RosterVote.merge(reads("duty11-pc.tsv").map { DutyRoster.analyse(it, codes) })
        println(merged.rows.joinToString { "${it.label}:${it.cells.size}" })
        assertEquals(12, merged.rows.size)
        val named = merged.rows.filter { it.label in truth }
        assertTrue("whole names ${named.size}/12", named.size >= 10)
        for (r in named) for ((d, c) in r.cells) {
            if (d in r.guessed || d in r.unsure || c is DutyCell.Unknown) continue
            assertEquals("${r.label} ${d}일", rota[(d - 1 + truth.getValue(r.label)) % 8], letter(c))
        }
    }
}
