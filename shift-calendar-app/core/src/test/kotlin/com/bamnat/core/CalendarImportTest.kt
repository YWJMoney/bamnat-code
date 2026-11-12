package com.bamnat.core

import com.bamnat.core.imports.CalendarImport
import com.bamnat.core.imports.CalendarImport.As
import com.bamnat.core.imports.CalendarImport.Skip
import com.bamnat.core.imports.DeviceCalendar
import com.bamnat.core.imports.DeviceEvent
import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** [IMPORT-01] Samsung Calendar events read as shifts, days off or memos. */
class CalendarImportTest {
    private val day = ShiftType(11, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0))
    private val night = ShiftType(12, "야간", "야", 0xFF5E35B1, LocalTime.of(19, 0), LocalTime.of(7, 0))
    private val off = ShiftType(13, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val types = listOf(day, night, off)

    private var id = 1L
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)
    private fun ev(title: String, from: LocalDateTime, to: LocalDateTime, allDay: Boolean = false, location: String = "", cal: Long = 1) =
        DeviceEvent(id++, cal, title, from, to, allDay, location)
    private fun allDay(title: String, d: Int, days: Int = 1) = ev(title, at(d, 0), at(d + days, 0), allDay = true)

    @Test
    fun titlesThatNameAShiftBecomeThatShift() {
        val p = CalendarImport.propose(listOf(allDay("야간", 3), allDay("[주간 근무]", 4), allDay("N", 5), allDay("나이트", 6),
            allDay("비번", 7), allDay("데이", 8), allDay("휴", 9)), types)
        assertEquals(listOf(As.SHIFT, As.SHIFT, As.SHIFT, As.SHIFT, As.OFF, As.SHIFT, As.OFF), p.map { it.kind })
        assertEquals(listOf(12L, 11L, 12L, 12L, 13L, 11L, 13L), p.map { it.shiftTypeId })
        assertEquals("이름이 같아요", p[0].reason)
        assertEquals("‘나이트’를 야간으로 읽었어요", p[3].reason)
    }

    @Test
    fun hoursThatMatchAShiftBecomeThatShiftWhenTheTitleIsShort() {
        val p = CalendarImport.propose(listOf(
            ev("A조", at(3, 18, 50), at(4, 7, 10)),
            ev("출근", at(4, 7), at(4, 19)),
            ev("병원 정기 검진 예약", at(5, 7), at(5, 19)),
            ev("치과", at(6, 15), at(6, 16), location = "가나다치과"),
        ), types)
        assertEquals(listOf(As.SHIFT, As.SHIFT, As.MEMO, As.MEMO), p.map { it.kind })
        assertEquals("시간이 야간과 같아요", p[0].reason)
        assertEquals("치과\n15:00–16:00 · 가나다치과", CalendarImport.memoText(p[3].event))
    }

    @Test
    fun oneShiftADayAndTripsStayMemos() {
        val p = CalendarImport.propose(listOf(allDay("주간", 3), ev("야간", at(3, 19), at(4, 7)), allDay("제주 여행", 10, days = 3)), types)
        assertEquals(listOf(As.SHIFT, As.MEMO, As.MEMO), p.map { it.kind })
        assertEquals("같은 날 근무가 이미 있어요", p[1].reason)
        assertEquals(LocalDate.of(2026, 11, 12), p[2].event.lastDate)
        // 2.0.0 [RVL-30] A trip over several days is one memo, and says why.
        assertEquals("여러 날 일정이라 메모로", p[2].reason)
        assertEquals("제주 여행\n~11/12", CalendarImport.memoText(p[2].event))
    }

    @Test
    fun whatWasBroughtInOrIsAlreadyThereIsLeftOut() {
        val first = allDay("야간", 3)
        val same = allDay("주간", 4)
        val holiday = DeviceEvent(99, 7, "개천절", at(5, 0), at(6, 0), allDay = true)
        val p = CalendarImport.propose(listOf(first, same, holiday, allDay("휴무", 6)), types, imported = setOf(first.key),
            current = { if (it.dayOfMonth == 4) 11L else if (it.dayOfMonth == 6) 13L else null }, generatedCalendars = setOf(7L))
        assertEquals(listOf(Skip.IMPORTED, Skip.SAME, Skip.GENERATED, Skip.SAME), p.map { it.skip })
        assertEquals(CalendarImport.Count(0, 0, 0), CalendarImport.count(p))
        val fresh = CalendarImport.count(CalendarImport.propose(listOf(allDay("야간", 3), allDay("휴무", 4), allDay("회식", 5)), types))
        assertEquals(CalendarImport.Count(1, 1, 1), fresh)
        assertEquals(3, fresh.total)
    }

    @Test
    fun calendarsKnowWhereTheyComeFrom() {
        assertTrue(DeviceCalendar(1, "내 캘린더", "My calendar", "LOCAL").samsung)
        assertTrue(DeviceCalendar(2, "Samsung account", "a@b.c", "com.osp.app.signin").samsung)
        assertEquals("삼성 캘린더", DeviceCalendar(2, "x", "a@b.c", "com.osp.app.signin").source)
        assertEquals("Google 캘린더", DeviceCalendar(3, "a@gmail.com", "a@gmail.com", "com.google").source)
        assertTrue(DeviceCalendar(4, "대한민국의 공휴일", "a@gmail.com", "com.google").generated)
        assertFalse(DeviceCalendar(5, "근무", "a@gmail.com", "com.google").generated)
        assertNull(CalendarImport.propose(listOf(allDay("회식", 3)), types).single().shiftTypeId)
        assertEquals("야간", CalendarImport.normalize(" 야간 근무 "))
    }

    /** 2.0.0 [RVL-30] (검토 SCAN-26·T07b-08·U07b-13) 연차·반차·여름휴가 go in as leave, a leave or days off over several days day by day. */
    @Test
    fun leaveGoesInAsLeaveDayByDay() {
        val p = CalendarImport.propose(listOf(allDay("연차", 2), allDay("오전 반차", 3), allDay("병가", 4), allDay("여름휴가", 16, days = 3),
            allDay("휴무", 20, days = 2), allDay("휴가신청 마감", 24)), types)
        assertEquals(listOf(As.LEAVE, As.LEAVE, As.LEAVE, As.LEAVE, As.LEAVE, As.LEAVE, As.OFF, As.OFF, As.MEMO), p.map { it.kind })
        assertEquals(Leave(LeaveKind.ANNUAL), p[0].leave)
        assertEquals(Leave(LeaveKind.ANNUAL, 2, fromStart = true), p[1].leave)
        assertEquals(Leave(LeaveKind.PAID), p[2].leave)
        assertEquals(listOf(16, 17, 18), p.subList(3, 6).map { it.date.dayOfMonth })
        assertEquals(listOf(20, 21), p.subList(6, 8).map { it.date.dayOfMonth })
        assertEquals(13L, p[6].shiftTypeId)
        // Each day is remembered on its own, so it is never brought in twice, and the first day keeps the event's own key.
        assertEquals(3, p.subList(3, 6).map { it.key }.toSet().size)
        assertEquals(p[3].event.key, p[3].key)
        val again = CalendarImport.propose(listOf(p[3].event), types, imported = setOf(p[3].key, p[4].key))
        assertEquals(listOf(Skip.IMPORTED, Skip.IMPORTED, null), again.map { it.skip })
        // A leave already on a day with work stays a memo there, as before.
        val clash = CalendarImport.propose(listOf(allDay("주간", 5), allDay("연차", 5)), types)
        assertEquals(listOf(As.SHIFT, As.MEMO), clash.map { it.kind })
        // Too long to be days off: one memo.
        assertEquals(listOf(As.MEMO), CalendarImport.propose(listOf(allDay("휴가", 1).copy(end = LocalDateTime.of(2027, 1, 30, 0, 0))), types).map { it.kind })
    }
}
