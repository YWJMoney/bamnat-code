package com.bamnat.core.layout

import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.DaySource
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** [TAB-04][TAB-05] 1.36 달력 판: 타임라인 bars, 근무 띠 주기 and each kind of work's 전문 판. */
class CalendarBoardTest {
    private val d = LocalDate.of(2026, 11, 2)
    private fun day(date: LocalDate, s: String?, e: String?, off: Boolean = false): ResolvedDay {
        val t = ShiftType(1, if (off) "휴" else "근무", "근", 0xFF000000, s?.let(LocalTime::parse), e?.let(LocalTime::parse), isOff = off)
        return ResolvedDay(date, t, DaySource.PATTERN, t.start, t.end, 0, scheduledMinutes = 0, alarmSkipped = false, entry = null)
    }

    @Test fun aDayShiftIsOneBar() {
        assertEquals(listOf(TimeSpan(9 * 60, 18 * 60)), Timeline.spans(d, day(d, "09:00", "18:00"), null))
    }

    @Test fun aNightShiftRunsOnIntoTheNextMorning() {
        val night = day(d.minusDays(1), "22:00", "07:00")
        assertEquals(listOf(TimeSpan(22 * 60, 24 * 60)), Timeline.spans(d.minusDays(1), night, null))
        assertEquals(listOf(TimeSpan(0, 7 * 60, carried = true), TimeSpan(9 * 60, 18 * 60)), Timeline.spans(d, day(d, "09:00", "18:00"), night))
    }

    @Test fun aDayOffDrawsNothing() {
        assertEquals(emptyList<TimeSpan>(), Timeline.spans(d, day(d, null, null, off = true), null))
    }

    @Test fun bandFindsTheRotaCycle() {
        val rota = listOf("주", "주", "야", "야", "비", "휴")
        assertEquals(6, ShiftBand.cycle(rota + rota + rota.take(3)))
        assertNull(ShiftBand.cycle(listOf("주", "야", null, "휴", "주")))
        assertNull(ShiftBand.cycle(listOf("주", "야", "휴", "주", "주", "휴", "야")))
    }

    @Test fun expertBoardsPerKindOfWork() {
        assertEquals(CalendarBoard.BAND, CalendarBoard.expertFor(WorkStyle.SHIFT))
        assertEquals(CalendarBoard.LINE, CalendarBoard.expertFor(WorkStyle.HOURLY))
        assertEquals(CalendarBoard.LIST, CalendarBoard.expertFor(WorkStyle.BUSINESS))
        assertEquals(CalendarBoard.MONTH, CalendarBoard.of("nope"))
    }
}

/** [TAB-07] The three tabs' views in one setting. */
class TabViewsTest {
    @Test fun roundTripsAndDefaultsAreEmpty() {
        val v = TabViews(CalendarBoard.BAND, com.bamnat.core.memo.MemoView("board", "p:상태", "-p:매출"), com.bamnat.core.money.LedgerBoard.BOARD)
        assertEquals(v, TabViews.decode(v.encode()))
        assertEquals("", TabViews().encode())
        assertEquals(TabViews(), TabViews.decode("cal=zzz;memo=bad;money=?"))
        assertEquals(CalendarBoard.LINE, TabViews.decode("money=cal;cal=timeline").cal)
    }
}
