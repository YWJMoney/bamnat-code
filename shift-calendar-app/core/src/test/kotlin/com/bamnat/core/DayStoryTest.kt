package com.bamnat.core

import com.bamnat.core.design.DayStory
import com.bamnat.core.design.StoryItem
import com.bamnat.core.design.StoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.38 [DSG-05] A day's story: a night shift across midnight stays one line, cut to the day, in time order. */
class DayStoryTest {
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun aNightShiftAcrossMidnightIsOneLineEachDay() {
        val night = StoryItem(at(12, 19), at(13, 7), StoryKind.Shift, "야간")
        val today = DayStory.arrange(day, listOf(night))
        assertEquals(1, today.size)
        assertEquals(at(12, 19), today[0].from)
        assertEquals(at(13, 0), today[0].to)
        assertTrue(today[0].intoTomorrow)
        assertFalse(today[0].fromYesterday)
        val tomorrow = DayStory.arrange(day.plusDays(1), listOf(night))
        assertTrue(tomorrow.single().fromYesterday)
        assertEquals(at(13, 7), tomorrow.single().to)
    }

    @Test fun thingsComeInTimeOrderAndOtherDaysStayOut() {
        val items = listOf(
            StoryItem(at(12, 18, 30), null, StoryKind.Alarm, "알람"),
            StoryItem(at(12, 8), at(12, 14), StoryKind.Sleep, "잠"),
            StoryItem(at(12, 12, 10), null, StoryKind.Money, "점심 9,000원"),
            StoryItem(at(11, 9), null, StoryKind.Memo, "어제 메모"),
            StoryItem(at(13, 0), null, StoryKind.Memo, "내일 0시 메모"),
        )
        val lines = DayStory.arrange(day, items)
        assertEquals(listOf("잠", "점심 9,000원", "알람"), lines.map { it.item.title })
        assertEquals(1, DayStory.nowAfter(lines, at(12, 13)))
        assertEquals(-1, DayStory.nowAfter(lines, at(12, 7)))
    }

    @Test fun twoThingsAtTheSameMinuteKeepTheirOrder() {
        val lines = DayStory.arrange(day, listOf(
            StoryItem(at(12, 7), at(12, 15), StoryKind.Shift, "주간"),
            StoryItem(at(12, 7), null, StoryKind.Alarm, "출근 알람"),
        ))
        assertEquals(listOf("주간", "출근 알람"), lines.map { it.item.title })
    }

    @Test fun lastNightsShiftIsStillGoingAfterMidnight() {
        val lines = DayStory.arrange(day, listOf(
            StoryItem(at(11, 18), at(12, 6), StoryKind.Shift, "야간"),
            StoryItem(at(12, 9), null, StoryKind.Memo, "도시락"),
        ))
        assertTrue(DayStory.ongoing(lines[0], at(12, 0, 11)))
        assertFalse(DayStory.ongoing(lines[0], at(12, 6)))
        assertFalse(DayStory.ongoing(lines[1], at(12, 9)))
    }
}
