package com.bamnat.core

import com.bamnat.core.plaza.StarterTopics
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
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

    /** 2.0.0 [RVN-24] 검토 PC-211·조건 13: every kind's plaza gets a topic in its own words (유형 낱말 검사: 0 leaks), in every week of the cycle. */
    @Test fun everyKindsTopicHasOnlyItsOwnWords() {
        val monday = LocalDate.of(2026, 11, 9)
        for (style in WorkStyle.entries) for (w in 0L until StarterTopics.ALL.size.toLong()) {
            val t = StarterTopics.of(monday.plusWeeks(w), style)
            assertTrue("$style: $t ${TypeWords.leaks(style, t)}", TypeWords.leaks(style, t).isEmpty())
        }
        // 현장 · 가게 · 투자 say the same topic in their own words; 교대 and 알바 keep it.
        val week = (0L until StarterTopics.ALL.size.toLong()).map { monday.plusWeeks(it) }.first { StarterTopics.of(it) == "출근길·퇴근길에 듣는 것" }
        assertEquals("출근길·퇴근길에 듣는 것", StarterTopics.of(week, WorkStyle.SHIFT))
        assertEquals("출근길·퇴근길에 듣는 것", StarterTopics.of(week, WorkStyle.HOURLY))
        assertEquals("현장 오가는 길에 듣는 것", StarterTopics.of(week, WorkStyle.FIELD))
        assertEquals("가게 오가는 길에 듣는 것", StarterTopics.of(week, WorkStyle.BUSINESS))
        assertEquals("오가는 길에 듣는 것", StarterTopics.of(week, WorkStyle.INVEST))
    }
}
