package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.39 [TASK-32][TASK-38] One line typed or shared, read into a title, a day and a time. */
class MemoLineTest {
    private val now = LocalDateTime.of(2026, 11, 7, 19, 17)

    @Test fun theDayAndTimeLeaveTheTitle() {
        val l = MemoTime.line("내일 저녁 6시 치과", now)
        assertEquals("치과", l.title)
        assertEquals(LocalDate.of(2026, 11, 8), l.date)
        assertEquals("저녁 6시 is 18:00, not 06:00", LocalDateTime.of(2026, 11, 8, 18, 0), l.at)
        assertEquals("내일 저녁 6시", l.read)
    }

    @Test fun aTimeWithNoDayIsTheNextSuchTime() {
        assertEquals(LocalDateTime.of(2026, 11, 7, 20, 0), MemoTime.line("8시 약 먹기", now).at)
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), MemoTime.line("아침 9시 택배", now).at)
    }

    @Test fun aDayWithNoTimeIsThatDayAndNothingRings() {
        val l = MemoTime.line("11월 12일 서류 내기", now)
        assertEquals("서류 내기", l.title)
        assertEquals(LocalDate.of(2026, 11, 12), l.date)
        assertNull(l.at)
    }

    @Test fun aSharedMessageKeepsItsOtherLinesAndAPlainLineStaysAsItIs() {
        val l = MemoTime.line("모레 3시 회의\n3층 회의실, 자료 챙기기", now)
        assertEquals("회의", l.title)
        assertEquals("3층 회의실, 자료 챙기기", l.rest)
        val plain = MemoTime.line("우유 사기", now)
        assertEquals("우유 사기", plain.title)
        assertNull(plain.date)
        assertNull(plain.at)
        // Only the words of a day and time: nothing else would be left, so the line stays.
        assertEquals("내일 3시", MemoTime.line("내일 3시", now).title)
    }
}
