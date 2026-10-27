package com.bamnat.core

import com.bamnat.core.plaza.CompanyMatch
import com.bamnat.core.plaza.PublicCycles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SRCH-12] 1.29 AS: the public rows' search reads each row's words from an index made once per file; before, every letter
 * typed worked out every row's six words (and its two spellings for the near match) again, on the screen's own thread.
 * The same answers either way; the time per letter is printed (JVM, this machine: a phone is a few times slower).
 */
class PublicCyclesIndexTest {
    private val typed = listOf("지에스칼텍스", "포스코 광양", "ㅍㅅㅋ", "4조2교대", "칼택스", "에쓰오일", "현대제철 당진", "병원", "s-oil", "한국")

    @Test fun theIndexGivesTheSameRowsAsWorkingEveryRowOut() {
        for (q in typed) {
            val indexed = PublicCycles.search(q).map { it.id }
            // A list that is not the file's own is worked out row by row, as every search was before 1.29.
            val worked = PublicCycles.search(q, rows = PublicCycles.all.toList()).map { it.id }
            assertEquals(q, worked, indexed)
        }
        assertTrue(PublicCycles.search("지에스칼텍스").any { it.company.contains("GS") })
        assertEquals(CompanyMatch.fit("GS칼텍스", "지에스칼텍스"), CompanyMatch.fit(CompanyMatch.Name("GS칼텍스"), CompanyMatch.Query("지에스칼텍스")))
    }

    @Test fun aLetterTypedNoLongerWorksOutEveryRow() {
        PublicCycles.warm()
        assertTrue(PublicCycles.searchIfReady("포스코").isNotEmpty())
        val letters = typed.flatMap { q -> (1..q.length).map { q.take(it) } }
        fun time(block: () -> Unit): Double {
            repeat(3) { block() }
            val t = System.nanoTime()
            repeat(5) { block() }
            return (System.nanoTime() - t) / 5.0 / 1_000_000
        }
        val copy = PublicCycles.all.toList()
        val before = time { letters.forEach { PublicCycles.search(it, rows = copy) } }
        val after = time { letters.forEach { PublicCycles.search(it) } }
        println("[SRCH-12] public rows, ${letters.size} letters typed: worked out per letter %.1f ms, from the index %.1f ms (JVM)".format(before, after))
        assertTrue("index $after ms vs $before ms", after < before)
    }
}
