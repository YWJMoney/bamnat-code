package com.bamnat.core

import com.bamnat.core.imports.CalendarImport
import com.bamnat.core.imports.Ics
import com.bamnat.core.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 2.0.0 [RVL-73] (검토 I07bb-05) A calendar file (.ics) from another calendar or roster app, read as the phone's events are. */
class IcsTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val from = LocalDate.of(2026, 11, 1)
    private val to = LocalDate.of(2026, 12, 31)

    private fun ics(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//예시//KO\r\n" +
        events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    @Test fun `times, dates, UTC and a zone of their own`() {
        val text = ics(
            "UID:a1\r\nSUMMARY:야간\r\nDTSTART;TZID=Asia/Seoul:20261103T220000\r\nDTEND;TZID=Asia/Seoul:20261104T070000",
            "UID:a2\r\nSUMMARY:연차\r\nDTSTART;VALUE=DATE:20261110\r\nDTEND;VALUE=DATE:20261111",
            "UID:a3\r\nSUMMARY:회의\\, 본사\r\nDTSTART:20261112T000000Z\r\nDURATION:PT1H30M\r\nLOCATION:2층\r\nDESCRIPTION:첫 줄\\n둘째 줄",
        )
        val e = Ics.read(text, seoul, from, to)
        assertTrue(Ics.looksLikeIcs(text))
        assertEquals(3, e.size)
        assertEquals(LocalDateTime.of(2026, 11, 3, 22, 0), e[0].begin)
        assertEquals(LocalDateTime.of(2026, 11, 4, 7, 0), e[0].end)
        assertTrue(e[1].allDay)
        assertEquals(LocalDate.of(2026, 11, 10), e[1].lastDate)
        // 00:00 UTC is 09:00 in Seoul; escaped text reads as written.
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), e[2].begin)
        assertEquals(LocalDateTime.of(2026, 11, 12, 10, 30), e[2].end)
        assertEquals("회의, 본사", e[2].title)
        assertEquals("첫 줄\n둘째 줄", e[2].description)
        assertEquals(Ics.FILE_CALENDAR, e[2].calendarId)
    }

    @Test fun `a folded line, an alarm inside and a cancelled event`() {
        val text = ics(
            "UID:b1\r\nSUMMARY:아주 긴 \r\n 제목\r\nDTSTART:20261105T090000\r\nDTEND:20261105T180000\r\nBEGIN:VALARM\r\nTRIGGER:-PT10M\r\nSUMMARY:알림\r\nEND:VALARM",
            "UID:b2\r\nSUMMARY:취소됨\r\nSTATUS:CANCELLED\r\nDTSTART:20261106T090000",
        )
        val e = Ics.read(text, seoul, from, to).single()
        assertEquals("아주 긴 제목", e.title)
        assertEquals(LocalTime.of(18, 0), e.end.toLocalTime())
    }

    @Test fun `repeating events open within the range, without the days taken out`() {
        val weekly = ics("UID:c1\r\nSUMMARY:주간\r\nDTSTART:20261102T090000\r\nDTEND:20261102T180000\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261115T235959\r\nEXDATE:20261104T090000")
        val days = Ics.read(weekly, seoul, from, to).map { it.date.dayOfMonth }
        assertEquals(listOf(2, 6, 9, 11, 13), days)
        val daily = ics("UID:c2\r\nSUMMARY:D\r\nDTSTART;VALUE=DATE:20261128\r\nRRULE:FREQ=DAILY;INTERVAL=2;COUNT=4")
        assertEquals(listOf(28, 30, 2, 4), Ics.read(daily, seoul, from, to).map { it.date.dayOfMonth })
        // The same file read again gives the same keys: nothing comes in twice.
        assertEquals(Ics.read(weekly, seoul, from, to).map { it.key }, Ics.read(weekly, seoul, from, to).map { it.key })
        // A long-repeating event stops at the range's end.
        val forever = ics("UID:c3\r\nSUMMARY:N\r\nDTSTART:20200101T220000\r\nRRULE:FREQ=DAILY")
        assertEquals(61, Ics.read(forever, seoul, from, to).size)
    }

    @Test fun `the events are proposed as the phone's are`() {
        val types = listOf(
            ShiftType(1, "주간", "주", 0xFF000000, LocalTime.of(9, 0), LocalTime.of(18, 0)),
            ShiftType(2, "야간", "야", 0xFF000000, LocalTime.of(22, 0), LocalTime.of(7, 0)),
            ShiftType(3, "휴무", "휴", 0xFF000000, null, null, isOff = true),
        )
        val text = ics(
            "UID:d1\r\nSUMMARY:야간\r\nDTSTART:20261103T220000\r\nDTEND:20261104T070000",
            "UID:d2\r\nSUMMARY:연차\r\nDTSTART;VALUE=DATE:20261110",
            "UID:d3\r\nSUMMARY:치과\r\nDTSTART:20261112T150000\r\nDTEND:20261112T160000",
        )
        val p = CalendarImport.propose(Ics.read(text, seoul, from, to), types)
        assertEquals(CalendarImport.As.SHIFT, p.first { it.date.dayOfMonth == 3 }.kind)
        assertEquals(CalendarImport.As.LEAVE, p.first { it.date.dayOfMonth == 10 }.kind)
        assertEquals(CalendarImport.As.MEMO, p.first { it.date.dayOfMonth == 12 }.kind)
        assertFalse(Ics.looksLikeIcs("이름,1,2,3"))
    }
}
