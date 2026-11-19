package com.bamnat.app.ui.company

import com.bamnat.core.plaza.PlazaTag
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVN-24] 검토 PC-211·조건 13 (유형 낱말 검사에 광장 시작 꾸러미): a plaza's starter kit (the chips, the first words of 오늘 한 줄 and
 * the 운영팀's welcome) says each kind's own day: 현장 '오늘 현장', 알바 '오늘 알바', 가게 '오늘 가게', 투자 none of 근무. No kind gets a word
 * [TypeWords] forbids it.
 */
class PlazaStarterWordsTest {
    private val nows = listOf<PlazaTag.Now?>(null, PlazaTag.Now.DAY, PlazaTag.Now.NIGHT)

    private fun kit(style: WorkStyle?): List<String> = Starter.entries.map { it.label(style) } +
        nows.map { Starter.todayLine(style, it) } + Starter.talkOf(style)

    @Test fun `no kind of work is asked in a word it does not use`() {
        for (style in listOf<WorkStyle?>(null) + WorkStyle.entries) for (line in kit(style)) {
            // A 교대·일근 plaza's own '근무' lines are the app's words; '야간' is only said where there is a night (the tag), as before.
            val leaks = TypeWords.leaks(style, line)
            assertTrue("$style: '$line' $leaks", leaks.isEmpty())
        }
    }

    @Test fun `a field plaza says the field and a part-time plaza says the part-time day`() {
        assertEquals("📅 오늘 현장 한 줄", Starter.TODAY.label(WorkStyle.FIELD))
        assertEquals("오늘 현장은 ", Starter.todayLine(WorkStyle.FIELD, null))
        assertEquals("현장 이야기", Starter.talkOf(WorkStyle.FIELD))
        assertEquals("📅 오늘 알바 한 줄", Starter.TODAY.label(WorkStyle.HOURLY))
        assertEquals("오늘 알바는 ", Starter.todayLine(WorkStyle.HOURLY, null))
        assertEquals("알바 이야기", Starter.talkOf(WorkStyle.HOURLY))
        // The ones that were right stay: 교대·일근 keep 근무, a shop its 가게, an investor its day.
        for (s in listOf(null, WorkStyle.SHIFT, WorkStyle.DAY)) {
            assertEquals("📅 오늘 근무 한 줄", Starter.TODAY.label(s))
            assertEquals("근무 이야기", Starter.talkOf(s))
        }
        assertEquals("🏪 오늘 가게 한 줄", Starter.TODAY.label(WorkStyle.BUSINESS))
        assertEquals("💬 오늘 한 줄", Starter.TODAY.label(WorkStyle.INVEST))
        // A part-timer who is working now still sees it said as a 근무.
        assertEquals("오늘 야간이에요. ", Starter.todayLine(WorkStyle.HOURLY, PlazaTag.Now.NIGHT))
    }

    @Test fun `no kind of work has a topic with a word it does not use`() {
        val monday = java.time.LocalDate.of(2026, 11, 9)
        for (style in listOf<WorkStyle?>(null) + WorkStyle.entries) for (w in 0L until com.bamnat.core.plaza.StarterTopics.ALL.size.toLong()) {
            val t = com.bamnat.core.plaza.StarterTopics.of(monday.plusWeeks(w), style)
            assertTrue("$style: $t", TypeWords.leaks(style, t).isEmpty())
        }
    }
}
