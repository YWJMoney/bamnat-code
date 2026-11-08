package com.bamnat.core

import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 계산 단위·요일별 시급 (경쟁 앱 조사 P3-B-12, 사용자 2026-11-06 '넷 다 1.38에'). */
class PayrollUnitTest {
    private val nov = YearMonth.of(2026, 11)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun theUnitOnlyEverCountsUp() {
        assertEquals(t(13, 10), PayrollLedger.roundUp(t(9), t(13, 7), 10))
        assertEquals(t(13, 30), PayrollLedger.roundUp(t(9), t(13, 7), 30))
        assertEquals(t(14), PayrollLedger.roundUp(t(9), t(13, 7), 60))
        assertEquals("딱 맞으면 그대로", t(13), PayrollLedger.roundUp(t(9), t(13), 30))
        assertEquals("1분 단위는 그대로", t(13, 7), PayrollLedger.roundUp(t(9), t(13, 7), 1))
        assertEquals("자정 넘김", t(2, 0), PayrollLedger.roundUp(t(22), t(1, 41), 30))
    }

    @Test fun aLateDayIsPaidToTheUnitAbove() {
        val place = Workplace("w", "가게", fivePlus = false, legalBreaks = false, minuteUnit = 30)
        val s = StaffMember("s", "알바", base = 10_320, weekly = mapOf(DayOfWeek.MONDAY to StaffShift(t(9), t(13))), weeklyHolidayPay = false)
        // 11/2 월요일 13:07 퇴근 → 13:30까지, 4시간 30분.
        val late = StaffWork("s", LocalDate.of(2026, 11, 2), WorkKind.NORMAL, t(9), t(13, 7))
        val line = PayrollLedger.line(place, s, nov, listOf(late))
        assertEquals(4 * 4 * 60 + 270, line.workMinutes)
        val plain = PayrollLedger.line(place.copy(minuteUnit = 1), s, nov, listOf(late))
        assertTrue("단위가 있으면 더 받거나 같아요", line.pay!!.total > plain.pay!!.total)
    }

    @Test fun weekdayRatesPayEachDayItsOwnAndPremiumsAtTheWeeksAverage() {
        // 월 5시간 10,320원, 토 5시간 12,000원 (휴게 30분씩): 통상시급 = (10,320×270 + 12,000×270) ÷ 540 = 11,160원.
        val place = Workplace("w", "가게", fivePlus = false)
        val s = StaffMember("s", "주말알바", base = 10_320, dayRates = mapOf(DayOfWeek.SATURDAY to 12_000L),
            weekly = mapOf(DayOfWeek.MONDAY to StaffShift(t(9), t(14)), DayOfWeek.SATURDAY to StaffShift(t(9), t(14))))
        assertEquals(11_160L, PayrollLedger.ordinaryHourly(place, s))
        val line = PayrollLedger.line(place, s, nov, emptyList())
        // 11월 월요일 5번 × 4.5시간 × 10,320 = 232,200 + 토요일 4번 × 4.5시간 × 12,000 = 216,000 (주 9시간이라 주휴 없음).
        assertEquals(448_200L, line.pay!!.total)
        assertTrue(line.pay!!.lines.any { it.label == "요일별 시급 차액" && it.amount == -3_780L })
        // 최저임금 검사는 가장 낮은 요일로.
        val low = PayrollLedger.line(place, s.copy(base = 9_000), nov, emptyList())
        assertTrue(low.warnings.any { it.key == "min" })
    }
}
