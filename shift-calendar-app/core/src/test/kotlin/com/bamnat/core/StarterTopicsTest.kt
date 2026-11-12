package com.bamnat.core

import com.bamnat.core.plaza.StarterTopics
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVN-20] 검토 U10a-02·I10ab-03: a quiet plaza's topic stays the week (Monday to Sunday) and moves on the next. */
class StarterTopicsTest {
    @Test fun oneTopicAWeekFromMonday() {
        val monday = LocalDate.of(2026, 11, 9)
        val week = (0L..6L).map { StarterTopics.of(monday.plusDays(it)) }.toSet()
        assertEquals(1, week.size)
        assertNotEquals(StarterTopics.of(monday), StarterTopics.of(monday.plusDays(7)))
        assertNotEquals(StarterTopics.of(monday), StarterTopics.of(monday.minusDays(1)))
        // Every one comes in turn, before 2024 too.
        assertEquals(StarterTopics.ALL.toSet(), (0L until StarterTopics.ALL.size.toLong()).map { StarterTopics.of(monday.plusWeeks(it)) }.toSet())
        assertTrue(StarterTopics.of(LocalDate.of(2020, 3, 4)) in StarterTopics.ALL)
    }

    @Test fun noWordOfAKindOfWorkItDoesNotHave() {
        val words = listOf("근무", "야간", "주간", "교대", "연차", "급여", "월급", "매출", "손님", "수익")
        StarterTopics.ALL.forEach { t -> words.forEach { w -> assertTrue("$t: $w", w !in t) } }
    }
}
