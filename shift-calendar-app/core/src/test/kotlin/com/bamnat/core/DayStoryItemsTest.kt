package com.bamnat.core

import com.bamnat.core.design.DayStory
import com.bamnat.core.design.DayStoryItems
import com.bamnat.core.design.StoryItem
import com.bamnat.core.design.StoryKind
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.SleepEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.38 [FLOW-08] 내 하루 이야기: shifts, sleep, money and memos of one day on one line, the night shift unbroken. */
class DayStoryItemsTest {
    // 11-11 야간 22:00–07:00, 11-12 휴무, 11-13 주간.
    private val start = LocalDate.of(2026, 11, 11)
    private val resolver = ScheduleResolver(Fixtures.all,
        WorkPattern.Rotation(start, listOf(Fixtures.night.id, Fixtures.off.id, Fixtures.day.id)), emptyList())
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun theNightBeforeTheSleepAfterAndTheDaysMoneyMakeOneLine() {
        val items = DayStoryItems.of(day, resolver,
            sleeps = listOf(SleepEntry(start = at(12, 8, 30), end = at(12, 14, 40))),
            money = listOf(
                MoneyEntry(id = 1, date = day, kind = MoneyKind.EXPENSE, amount = 6500, category = "식비", merchant = "편의점", time = LocalTime.of(7, 20)),
                MoneyEntry(id = 2, date = day, kind = MoneyKind.EXPENSE, amount = 3000, category = "교통"), // no time: stays in the money card
            ),
            memos = listOf(StoryItem(at(12, 15), null, StoryKind.Memo, "도시락", id = "memo:9@$day")),
        )
        val lines = DayStory.arrange(day, items)
        assertEquals(listOf("야간", "편의점 6,500원", "잠 6시간 10분", "도시락"), lines.map { it.item.title })
        assertTrue(lines[0].fromYesterday)
        assertEquals(at(12, 7), lines[0].to)
        assertTrue(DayStoryItems.worthShowing(lines))
    }

    @Test fun aDayOfOnlyAShiftKeepsToTheCards() {
        val lines = DayStory.arrange(day.plusDays(1), DayStoryItems.of(day.plusDays(1), resolver))
        assertEquals(listOf("주간"), lines.map { it.item.title })
        assertFalse(DayStoryItems.worthShowing(lines))
    }

    @Test fun aPlannedSleepShowsOnlyWhereNoSleepWasWritten() {
        val plan = com.bamnat.core.sleep.SleepPlan.Tip(com.bamnat.core.sleep.SleepPlan.Kind.SLEEP_AFTER_NIGHT, at(12, 8), at(12, 14), "퇴근 뒤 6시간",
            com.bamnat.core.sleep.SleepPlan.Basis.GUIDANCE)
        val planned = DayStoryItems.of(day, resolver, plans = listOf(plan))
        assertEquals(1, planned.count { it.kind == StoryKind.SleepPlan })
        val kept = DayStoryItems.of(day, resolver, plans = listOf(plan), sleeps = listOf(SleepEntry(start = at(12, 8, 10), end = at(12, 13))))
        assertEquals(0, kept.count { it.kind == StoryKind.SleepPlan })
    }
}
