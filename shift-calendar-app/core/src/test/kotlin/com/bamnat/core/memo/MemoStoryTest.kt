package com.bamnat.core.memo

import com.bamnat.core.design.DayStory
import com.bamnat.core.design.StoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.38 [FLOW-08] The memos of a day with a time, as lines of the day's story. */
class MemoStoryTest {
    private val day = LocalDate.of(2026, 11, 6)

    @Test fun aScheduleOrAWrittenTimePutsTheMemoOnTheLine() {
        val memos = listOf(
            Memo(id = 1, date = day, title = "치과", reminder = MemoReminder(day.atTime(15, 0), ring = true, notify = false)),
            Memo(id = 2, date = day, title = "점심 약속", body = "12시 반 김밥"),
            Memo(id = 3, date = day, title = "시간 없는 메모"),
            Memo(id = 4, date = day, title = "버린 메모", body = "9시", deletedAtMillis = 1),
        )
        val items = MemoStory.items(memos, day)
        assertEquals(listOf("치과", "점심 약속"), items.map { it.title })
        assertEquals(day.atTime(15, 0), items[0].start)
        assertEquals(day.atTime(12, 30), items[1].start)
        assertTrue(items.all { it.kind == StoryKind.Memo })
        assertEquals(1L, MemoStory.memoIdOf(items[0]))
        // In time order on the line.
        assertEquals(listOf("점심 약속", "치과"), DayStory.arrange(day, items).map { it.item.title })
    }

    @Test fun aRepeatingMemoIsOnEachOfItsDaysAtItsTime() {
        val start = day.minusWeeks(2)
        val weekly = Memo(id = 5, date = start, title = "분리수거 8시", repeat = "W" + day.dayOfWeek.value,
            reminder = MemoReminder(start.atTime(20, 0), ring = false, notify = true))
        assertEquals(day.atTime(20, 0), MemoStory.items(listOf(weekly), day).single().start)
        val written = weekly.copy(reminder = null, title = "분리수거", body = "저녁 8시")
        assertEquals(day.atTime(20, 0), MemoStory.items(listOf(written), day).single().start)
    }

    @Test fun aTimeOnAnotherDayIsNotOnThisDay() {
        val tomorrow = Memo(id = 6, date = day, title = "내일 오후 3시 회의")
        assertTrue(MemoStory.items(listOf(tomorrow), day).isEmpty())
        assertNull(MemoStory.memoIdOf(com.bamnat.core.design.StoryItem(LocalDateTime.of(day, LocalTime.NOON), kind = StoryKind.Shift, title = "주간")))
    }
}
