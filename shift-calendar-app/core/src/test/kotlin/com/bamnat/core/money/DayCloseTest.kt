package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.37 [LEDH-09] 퇴근 시각 하루 마감. */
class DayCloseTest {
    private val d = LocalDate.of(2026, 11, 4)
    private fun t(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m)

    // 4일 주간 07~19, 5일 야간 22~(6일)07, 6일 쉬는 날.
    private val work: (LocalDate) -> DayClose.Work? = { x ->
        when (x) {
            d -> DayClose.Work(t(d, 7), t(d, 19))
            d.plusDays(1) -> DayClose.Work(t(x, 22), t(x.plusDays(1), 7))
            else -> null
        }
    }

    @Test fun afterTheShiftNotAtNine() {
        assertEquals(t(d, 19, 30), DayClose.at(d, work))
        // The night day: nothing ends on it and the night starts at 22:00, so 21:00 stays.
        assertEquals(t(d.plusDays(1), 21), DayClose.at(d.plusDays(1), work))
        // After the night: the morning it ends.
        assertEquals(t(d.plusDays(2), 7, 30), DayClose.at(d.plusDays(2), work))
        assertEquals(t(d.plusDays(3), 21), DayClose.at(d.plusDays(3), work))
    }

    @Test fun noEveningNoticeWhileAtWork() {
        val evening: (LocalDate) -> DayClose.Work? = { x -> if (x == d) DayClose.Work(t(d, 18), t(d.plusDays(1), 6)) else null }
        assertNull(DayClose.at(d, evening))
        assertEquals(t(d.plusDays(1), 6, 30), DayClose.next(LocalDateTime.of(d, java.time.LocalTime.NOON), evening))
    }

    @Test fun wordsSayWhatIsWaiting() {
        val won: (Long) -> String = { "${it}원" }
        assertEquals("오늘 정리할 게 3건 있어요", DayClose.words(3, 0, won).first)
        assertEquals("오늘 쓴 돈 8000원", DayClose.words(0, 8000, won).first)
    }
}
