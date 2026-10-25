package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ExtraBasis
import com.bamnat.core.pay.ExtraRule
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [HOLI-01][HOLI-02][HOLI-03] 빨간날 근무와 휴가의 골든 사례 (research/1.25-V-조사.md 2·3장, research/pay-golden-cases.md 1.25 V).
 * 2026-10-09(금) 한글날 하루만 근무가 있는 달. 월급 2,508,000원 ÷ 209시간 = 통상시급 12,000원, 시급제는 시급 12,000원.
 * 모닝 07~15, 이브닝 15~23, 나이트 23~07(휴게 없음). 금액은 손으로 셈.
 */
class HolidayWorkGoldenTest {
    private val oct = YearMonth.of(2026, 10)
    private val hangul = LocalDate.of(2026, 10, 9)
    private fun t(h: Int) = LocalTime.of(h, 0)
    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), v)))
    private val M = ShiftType(1, "모닝", "M", 0xFF1E88E5, t(7), t(15), 0)
    private val E = ShiftType(2, "이브닝", "E", 0xFF43A047, t(15), t(23), 0)
    private val N = ShiftType(3, "나이트", "N", 0xFF5E35B1, t(23), t(7), 0)
    private val O = ShiftType(9, "휴무", "O", 0xFF9E9E9E, null, null, isOff = true)
    private val monthly = PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY, base = wage(2_508_000))
    private val hourly = PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.HOURLY, base = wage(12_000))

    private fun on(shift: ShiftType, leave: Leave? = null) =
        ScheduleResolver(listOf(M, E, N, O), WorkPattern.Rotation(LocalDate.of(2026, 10, 1), listOf(O.id)),
            listOf(DayEntry(hangul, ShiftOverride.Shift(shift.id), leave = leave)))
    private val none = ScheduleResolver(listOf(M, E, N, O), WorkPattern.Rotation(LocalDate.of(2026, 10, 1), listOf(O.id)), emptyList())
    private fun pay(p: PayProfile, r: ScheduleResolver): MonthPay = PayCalculator().calculate(p, oct, r::resolve)
    /** What the 한글날 shift adds to the month. */
    private fun added(p: PayProfile, r: ScheduleResolver) = pay(p, r).total - pay(p, none).total
    private fun PayProfile.mode(m: HolidayWork, f: (CompanyRules) -> CompanyRules = { it }) = copy(rules = f(rules.withHolidayWork(m)))

    @Test
    fun `G1 휴일 가산 모닝 8시간 150%`() {
        // 8 × 12,000 × 150% = 144,000.
        assertEquals(144_000, added(monthly, on(M)))
        assertEquals(HolidayWork.PREMIUM, monthly.rules.holidayWork)
    }

    @Test
    fun `G2 휴일 가산 이브닝은 22~23시 야간 가산이 겹침`() {
        // 144,000 + 1 × 12,000 × 50% = 150,000.
        assertEquals(150_000, added(monthly, on(E)))
    }

    @Test
    fun `G3 법대로 나이트는 0~24시 안의 1시간만 휴일`() {
        // 휴일 1 × 12,000 × 150% = 18,000, 야간 7 × 6,000 = 42,000.
        assertEquals(60_000, added(monthly, on(N)))
    }

    @Test
    fun `G4 밤샘을 시작한 날로 보면 나이트 8시간이 모두 휴일`() {
        // 8 × 18,000 = 144,000 + 야간 42,000 = 186,000.
        assertEquals(186_000, added(monthly.copy(rules = monthly.rules.copy(holidayByShiftStart = true)), on(N)))
    }

    @Test
    fun `G5 특근처럼 근무 종류별 1회 금액, 나이트 야간 수당은 따로`() {
        val p = monthly.mode(HolidayWork.AS_EXTRA) { it.withHolidayShiftPay(N.id, 100_000).withHolidayShiftPay(M.id, 70_000) }
        assertEquals(HolidayWork.AS_EXTRA, p.rules.holidayWork)
        assertEquals(ExtraKind.SPECIAL_ID, p.rules.publicHolidayAs)
        assertTrue(p.rules.holidayByShiftStart)
        // 나이트: 1회 100,000 + 야간 7 × 6,000 = 142,000. 모닝: 70,000. 이브닝은 금액이 없어 법정 휴일 가산 150,000.
        assertEquals(142_000, added(p, on(N)))
        assertEquals(70_000, added(p, on(M)))
        assertEquals(150_000, added(p, on(E)))
        assertEquals(100_000, p.rules.holidayShiftPay(N.id))
        assertTrue(p.rules.holidayWorkText(mapOf(N.id to "나이트", M.id to "모닝")).contains("나이트 100,000원"))
        // 0 takes the amount away again.
        assertEquals(0, p.rules.withHolidayShiftPay(N.id, 0).holidayShiftPay(N.id))
    }

    @Test
    fun `G6 대근처럼 통상시급 200%`() {
        val p = monthly.mode(HolidayWork.AS_EXTRA) {
            it.withHolidayWork(HolidayWork.AS_EXTRA, ExtraKind.COVER_ID)
                .copy(extra = mapOf(ExtraKind.COVER_ID to ExtraRule(basis = ExtraBasis.HOURLY_RATE, multiplier = 2.0)))
        }
        // 8 × 12,000 × 200% = 192,000.
        assertEquals(192_000, added(p, on(M)))
    }

    @Test
    fun `G7 대체휴일은 가산 없이 야간만`() {
        val p = monthly.mode(HolidayWork.SUBSTITUTE)
        assertEquals(HolidayWork.SUBSTITUTE, p.rules.holidayWork)
        assertEquals(42_000, added(p, on(N)))
        assertEquals(0, added(p, on(M)))
    }

    @Test
    fun `G8 교대제라 상관없음은 평일과 같음, 대체휴일과 구분해 기억`() {
        val p = monthly.mode(HolidayWork.SHIFT_NORMAL)
        assertEquals(HolidayWork.SHIFT_NORMAL, p.rules.holidayWork)
        assertEquals(0, added(p, on(M)))
        assertEquals(HolidayWork.PREMIUM, p.rules.withHolidayWork(HolidayWork.PREMIUM).holidayWork)
    }

    @Test
    fun `G9 월급제 빨간날 나이트에 연차는 근무일로 인정, 가산 0`() {
        val m = pay(monthly, on(N, Leave()))
        assertEquals(2_508_000, m.total)
        val part = m.day(hangul)!!.parts.single { it.label == "연차" }
        assertTrue(part.formula, part.formula.contains("근무일로 인정"))
    }

    @Test
    fun `G10 월급제 빨간날 나이트 반차는 일한 4시간만 야간·휴일`() {
        // 23~03: 휴일 1 × 18,000, 야간 4 × 6,000 = 24,000.
        assertEquals(42_000, added(monthly, on(N, Leave(quarters = 2))))
    }

    @Test
    fun `G11 시급제 빨간날 연차는 유급휴일 하루만, 두 번 주지 않음`() {
        // 1.24까지 유급휴일 96,000 + 연차 96,000 = 192,000.
        val m = pay(hourly, on(N, Leave()))
        assertEquals(96_000, m.total)
        assertEquals(0, m.day(hangul)!!.parts.single { it.label == "연차" }.amount)
        assertTrue(m.lines.none { it.label == "유급 휴가" })
    }

    @Test
    fun `G12 시급제 빨간날 반차는 일한 몫과 유급휴일 하루`() {
        // 기본 3 × 12,000 = 36,000, 휴일 1 × 18,000, 야간 4 × 6,000 = 24,000, 유급휴일 8 × 12,000 = 96,000 → 174,000.
        assertEquals(174_000, pay(hourly, on(N, Leave(quarters = 2))).total)
    }

    @Test
    fun `G13 나이트 1회 수당은 휴가 날엔 없음`() {
        val p = monthly.copy(items = listOf(PayItem("n", "나이트 수당", wage(30_000), Cadence.PER_SHIFT_TYPE, shiftTypeIds = setOf(N.id))))
        assertEquals(0, added(p, on(N, Leave())))
        assertEquals(60_000 + 30_000, added(p, on(N)))
    }

    @Test
    fun `G14 무급 휴가를 빨간날에 넣어도 월급에서 빼지 않음`() {
        val unpaid = Leave(kind = com.bamnat.core.model.LeaveKind.entries.first { !it.paid })
        assertEquals(2_508_000, pay(monthly, on(M, unpaid)).total)
    }
}
