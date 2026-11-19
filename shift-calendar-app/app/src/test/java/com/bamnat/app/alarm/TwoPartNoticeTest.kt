package com.bamnat.app.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.Fmt
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-21] 결정 19 (설계 4-2절 1·2·7번): the lock screen's 오늘 근무 says both parts on one line, the widget's day line both
 * parts, and 둘째 구간 알림 comes 10 minutes before the later part, quiet, unless the shift rings an alarm for it.
 */
@RunWith(AndroidJUnit4::class)
class TwoPartNoticeTest {
    private val day = LocalDate.of(2026, 11, 18)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun type(start: LocalTime, end: LocalTime, gapStart: LocalTime? = null, gap: Int = 0, gapAlarm: Boolean = false) =
        ShiftType(1, "주간", "주", 0xFF1565C0, start, end, gapStart = gapStart, gapMinutes = gap, gapAlarm = gapAlarm)
    private fun resolver(type: ShiftType) = ScheduleResolver(listOf(type), listOf(WorkPattern.Rotation(day, listOf(1L))), emptyList())

    private val shop = type(t(11), t(21), t(15), 120)

    @Test fun theLockScreenSaysBothParts() {
        assertEquals("오늘 주간 11:00~15:00, 17:00~21:00", TodayShiftNotice.lines(resolver(shop), day).first)
        // One part, as before.
        assertEquals("오늘 주간 09:00~18:00", TodayShiftNotice.lines(resolver(type(t(9), t(18))), day).first)
    }

    @Test fun theWidgetLineSaysBothParts() {
        assertEquals("${Fmt.time(t(11))}–${Fmt.time(t(15))}, ${Fmt.time(t(17))}–${Fmt.time(t(21))}", Fmt.dayHours(resolver(shop).resolve(day)))
        assertEquals(Fmt.timeRangeShort(t(9), t(18)), Fmt.dayHours(resolver(type(t(9), t(18))).resolve(day)))
    }

    @Test fun theSecondPartIsToldTenMinutesBefore() {
        val r = resolver(shop)
        val due = GapNotice.next(r, day.atTime(12, 0))!!
        assertEquals(day.atTime(16, 50), due.at)
        assertEquals(day.atTime(17, 0), due.partStart)
        assertEquals("17:00 다시 시작" to "브레이크 타임 끝나고 주간 17:00–21:00", GapNotice.words(due, "브레이크 타임"))
        assertEquals(due, GapNotice.dueAt(r, day.atTime(16, 50)))
        assertNull("after the part started", GapNotice.dueAt(r, day.atTime(17, 5)))
        // After today's, the next day's.
        assertEquals(day.plusDays(1).atTime(16, 50), GapNotice.next(r, day.atTime(17, 0))!!.at)
    }

    @Test fun anAlarmForTheSecondPartTakesItsPlace() {
        val loud = resolver(shop.copy(gapAlarm = true))
        assertFalse(GapNotice.tells(loud.resolve(day)))
        assertNull(GapNotice.next(loud, day.atTime(12, 0)))
        assertTrue(GapNotice.tells(resolver(shop).resolve(day)))
    }

    @Test fun aDayOfOnePartHasNone() {
        assertNull(GapNotice.next(resolver(type(t(9), t(18))), day.atTime(8, 0)))
    }
}
