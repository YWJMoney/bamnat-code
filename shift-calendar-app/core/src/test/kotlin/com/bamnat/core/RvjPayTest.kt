package com.bamnat.core

import com.bamnat.core.boss.AccidentRates
import com.bamnat.core.boss.Durunuri
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.InsuranceLine
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayPeriod
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RateTable
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.YearEndPreview
import com.bamnat.core.remote.RemoteFiles
import com.bamnat.core.report.WorkProof
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 2.0.0 갈래 J: 검토 PAYC-01~07 (급여 계산) 재현 시험. */
class RvjPayTest {
    private fun t(h: Int) = LocalTime.of(h, 0)
    private val day = ShiftType(1, "주간", "주", 0xFF000000, t(9), t(18), 60, t(12))
    private val off = ShiftType(9, "휴무", "휴", 0xFF000000, null, null, isOff = true)
    private val monthly = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 12_000L * 209))),
    )

    @After fun reset() = RateTable.use(null)

    // ── [RVJ-01] PAYC-01 ──
    // 2026-12-21(월)~27(일): 월~목 8시간, 25일(금, 성탄절) 근무 8시간, 26일(토, 쉬는 날) 8시간 더 일함 → 그 주 48시간.
    private val christmasWeek = ScheduleResolver(listOf(day, off),
        WorkPattern.Rotation(LocalDate.of(2026, 12, 21), List(5) { day.id } + listOf(off.id, off.id)),
        listOf(DayEntry(LocalDate.of(2026, 12, 26), extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 480)))))

    private fun saturday(p: PayProfile) = PayCalculator().calculate(p, YearMonth.of(2026, 12), christmasWeek::resolve)
        .minutes.single { it.date == LocalDate.of(2026, 12, 26) }

    @Test fun holidayHoursFillTheWeeks40SoSaturdayIsOvertime() {
        // 근로기준법 제2조 제1항 제7호: 1주는 휴일을 포함한 7일. 금요일 휴일 8시간도 40시간에 들어가 토요일 8시간은 모두 연장.
        val s = saturday(monthly)
        assertEquals(480, s.overtime)
        assertEquals(0, s.extraRegular)
        // 회사 규칙 '휴일 근무는 주 40시간에 안 넣음'이면 예전처럼 소정 외(100%)로 남아요.
        val old = saturday(monthly.copy(rules = monthly.rules.copy(holidayOutsideWeek = true)))
        assertEquals(0, old.overtime)
        assertEquals(480, old.extraRegular)
        // 휴일 근무 자체에는 연장 가산이 겹치지 않아요.
        val fri = PayCalculator().calculate(monthly, YearMonth.of(2026, 12), christmasWeek::resolve).minutes.single { it.date == LocalDate.of(2026, 12, 25) }
        assertEquals(480, fri.holidayWithin8h)
        assertEquals(0, fri.overtime)
    }

    // ── [RVJ-02] PAYC-02 ──
    @Test fun aTwelveHourNightStartingOnAHolidayGetsOvertimePastEightHours() {
        // 한글날(10/9) 19:00 → 10/10 07:00, 휴게 1시간(03:00, 평일 쪽): 휴일 5시간 + 평일 6시간 = 11시간.
        val night = ShiftType(2, "야간", "야", 0xFF000000, t(19), t(7), 60, t(3))
        val hangul = LocalDate.of(2026, 10, 9)
        val r = ScheduleResolver(listOf(night, off), WorkPattern.Rotation(hangul, listOf(night.id) + List(30) { off.id }), emptyList())
        val m = PayCalculator().calculate(monthly, YearMonth.of(2026, 10), r::resolve).minutes.single { it.date == hangul }
        assertEquals(300, m.holidayWithin8h)
        // 휴일 5시간이 하루 8시간 칸을 먼저 채워 평일 3시간은 기본, 나머지 3시간은 연장(평일에 같은 근무를 하면 기본 8 + 연장 3).
        assertEquals(180, m.regular)
        assertEquals(180, m.overtime)
    }

    // ── [RVJ-03] PAYC-03 ──
    @Test fun aWageThatChangesInsideThePeriodPaysEachDayAtItsOwnRate() {
        val evening = ShiftType(3, "저녁", "저", 0xFF000000, t(18), t(0), 0)
        val r = ScheduleResolver(listOf(evening, off),
            WorkPattern.Rotation(LocalDate.of(2026, 12, 21), List(5) { evening.id } + listOf(off.id, off.id)), emptyList())
        fun alba(vararg v: Pair<LocalDate, Long>) = PayProfile(
            occupation = Occupation.PART_TIME, wageType = WageType.HOURLY, fiveOrMoreEmployees = false,
            base = ValueTimeline(v.map { DatedValue(it.first, it.second) }),
            period = PayPeriod(endDay = 20, payDay = 25),
        )
        val jan = YearMonth.of(2027, 1) // 12/21~1/20
        val split = PayCalculator().calculate(alba(LocalDate.of(2026, 1, 1) to 10_320L, LocalDate.of(2027, 1, 1) to 10_700L), jan, r::resolve)
        val allNew = PayCalculator().calculate(alba(LocalDate.of(2026, 1, 1) to 10_700L), jan, r::resolve)
        // 12/21~25, 12/28~31: 9일 × 6시간 = 54시간, 그리고 12/21~27 주의 주휴 6시간: 60시간 × 380원 = 22,800원 덜.
        val line = split.lines.single { it.label == "그날 시급 차이" }
        assertEquals(-22_800L, line.amount)
        assertEquals(22_800L, allNew.total - split.total)
        assertTrue(line.formula, line.formula.startsWith("12/21~12/31 10,320원"))
    }

    // ── [RVJ-04] PAYC-04·14 ──
    private fun rates(json: String) = RateTable.use(RemoteFiles.parse(
        """{"format":1,"kind":"rates","version":2027010101,"issued":"2026-12-20","minApp":33,"data":{"rates":[$json]}}""".toByteArray(),
    ))

    @Test fun newYearValuesComeFromTheRatesFile() {
        val feb27 = LocalDate.of(2027, 2, 1)
        // Without a 2027 고시 in the file, 2026's values stand.
        assertEquals(4_591_740L, TakeHome.healthCap(feb27))
        assertEquals(200_000L, TakeHome.freeLimit(YearMonth.of(2027, 2)))
        assertNull(Durunuri.limit(2027))
        val before = AccidentRates.premium(3_000_000, "retail_food", feb27)
        assertEquals(AccidentRates.YEAR, AccidentRates.yearOn(feb27))
        rates("""{"from":"2027-01-01","version":"2027.2","source":"시험","minimumHourlyWage":10700,"pensionRate":0.10,"healthRate":0.0719,
            "longTermCareRate":0.009994,"unemploymentRate":0.018,"healthCapShare":4700000,"mealFreeLimit":250000,"durunuriUnder":2800000,
            "accidentPermille":{"retail_food":9.0}}""")
        assertEquals(4_700_000L, TakeHome.healthCap(feb27))
        assertEquals(4_591_740L, TakeHome.healthCap(LocalDate.of(2026, 12, 1)))
        assertEquals(250_000L, TakeHome.freeLimit(YearMonth.of(2027, 2)))
        assertEquals(200_000L, TakeHome.freeLimit(YearMonth.of(2026, 12)))
        assertEquals(2_800_000L, Durunuri.limit(2027))
        assertEquals(2027, AccidentRates.yearOn(feb27))
        assertTrue(AccidentRates.premium(3_000_000, "retail_food", feb27) != before)
        assertEquals(before, AccidentRates.premium(3_000_000, "retail_food", LocalDate.of(2026, 12, 1)))
    }

    @Test fun aBadOptionalValueRejectsTheFile() {
        val bad = runCatching { rates("""{"from":"2027-01-01","version":"x","source":"x","minimumHourlyWage":10700,"healthCapShare":5}""") }
        assertTrue(bad.isFailure)
    }

    // ── [RVJ-05] PAYC-20 ──
    @Test fun aYearTheAppHasNoRatesForSaysSoAndFollowsThePensionLaw() {
        assertNull(RateTable.staleYear(LocalDate.of(2027, 5, 1)))
        assertEquals(2027, RateTable.staleYear(LocalDate.of(2028, 1, 1)))
        // 국민연금법 제88조: 2028년 10.5% (근로자 5.25%).
        assertEquals(0.105, RateTable.pensionRateOn(LocalDate.of(2028, 3, 1))!!, 1e-9)
        assertEquals(0.13, RateTable.pensionRateOn(LocalDate.of(2034, 3, 1))!!, 1e-9)
        val pension = TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = LocalDate.of(2028, 2, 1)).deductions.toMap()["국민연금"]
        assertEquals(157_500L, pension)
    }

    // ── [RVJ-06] PAYC-05·PAYU-48 ──
    private val weekdays = ScheduleResolver(listOf(day, off),
        WorkPattern.Rotation(LocalDate.of(2026, 1, 5), List(5) { day.id } + listOf(off.id, off.id)), emptyList())

    @Test fun theYearEndPreviewUsesTheCalendarAndTheInsuranceTheWorkerIsOutOf() {
        val upTo = YearMonth.of(2026, 3)
        val all = YearEndPreview.ofYear(monthly, upTo, weekdays::resolve)!!
        assertEquals(3, all.months)
        assertEquals(9, all.projectedMonths)
        // 60세: 국민연금을 떼지 않으니 연금보험료 공제가 없어 결정세액이 더 커요.
        val sixty = YearEndPreview.ofYear(monthly.copy(insuranceOff = setOf(InsuranceLine.PENSION)), upTo, weekdays::resolve)!!
        assertTrue("${sixty.determined} vs ${all.determined}", sixty.determined > all.determined)
        // 7월에 들어온 사람은 여섯 달치만(예전에는 받은 달을 열두 달로 늘렸어요).
        val july = monthly.copy(hireDate = LocalDate.of(2026, 7, 1))
        val half = YearEndPreview.ofYear(july, YearMonth.of(2026, 8), weekdays::resolve)!!
        assertEquals(2, half.months)
        assertEquals(4, half.projectedMonths)
        assertTrue("${half.salary} vs ${all.salary}", half.salary < all.salary * 6 / 10)
        // 닫은 달은 그 달 남긴 금액으로.
        val closed = YearEndPreview.ofYear(monthly, upTo, weekdays::resolve, closed = { if (it.monthValue == 1) 10_000_000L else null })!!
        assertTrue(closed.salary > all.salary + 7_000_000)
    }

    // ── [RVJ-07] PAYC-06 ──
    @Test fun workProofCountsTheExtraHoursThePayCounts() {
        val d1 = LocalDate.of(2026, 11, 2)
        val d2 = LocalDate.of(2026, 11, 3)
        val r = ScheduleResolver(listOf(day, off), WorkPattern.Rotation(d1, List(5) { day.id } + listOf(off.id, off.id)), listOf(
            // 18:00~21:00, 휴게 30분 → 2시간 30분.
            DayEntry(d1, extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, start = t(18), end = t(21), breakMinutes = 30))),
            // 시각 없이 2시간만 적은 옛 기록.
            DayEntry(d2, extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120))),
        ))
        val days = WorkProof.days(d1, d2, r::resolve)
        assertEquals(150, days.single { it.date == d1 }.extraMinutes)
        val second = days.single { it.date == d2 }
        assertEquals(120, second.extraMinutes)
        assertTrue(second.extraUntimed)
        assertNotNull(days)
    }

    // ── [RVJ-47] PC-111 ──
    @Test fun workProofSaysADayOffWorkedAsItWasPutIn() {
        val mon = LocalDate.of(2026, 11, 2)
        val sat = LocalDate.of(2026, 11, 7)
        val r = ScheduleResolver(listOf(day, off), WorkPattern.Rotation(mon, List(5) { day.id } + listOf(off.id, off.id)), listOf(
            // 휴무일 특근 09~15, 휴게 30분.
            DayEntry(sat, extras = listOf(ExtraEntry(ExtraKind.SPECIAL_ID, start = t(9), end = t(15), breakMinutes = 30))),
            // 시각 없이 2시간만 적은 잔업.
            DayEntry(mon, extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120))),
        ))
        val days = WorkProof.days(mon, sat, r::resolve)
        val worked = days.single { it.date == sat }
        assertEquals("휴무일 특근의 시작·끝은 넣은 대로", t(9) to t(15), worked.start to worked.end)
        assertEquals(30, worked.breakMinutes)
        assertEquals(330, worked.extraMinutes)
        // 근무가 있는 날은 근무 시각 그대로.
        assertEquals(t(9) to t(18), days.single { it.date == mon }.let { it.start to it.end })
        val text = WorkProof.doc("제조·생산 교대", mon, sat, days, sat).text()
        assertTrue("직업은 직업 칸에: $text", text.contains("직업") && text.contains("제조·생산 교대"))
        assertTrue("이름 칸에 직업이 들어가지 않아요", !Regex("이름[^\\n]*제조").containsMatchIn(text))
        assertTrue("시각이 없는 줄은 말로", text.contains("2시간 (시각 안 적음)") && !text.contains("시각 없음"))
    }

    /** [RVJ-47] PC-111 같은 틀: the 연간 교대 리포트 put the 직업 under '이름' too; the 현장 일한 날 증빙 has a 이름 칸 to write in. */
    @Test fun theOtherReportsOnTheSameFrameNameTheJob() {
        val mon = LocalDate.of(2026, 11, 2)
        val r = ScheduleResolver(listOf(day, off), WorkPattern.Rotation(mon, List(5) { day.id } + listOf(off.id, off.id)), emptyList())
        val months = com.bamnat.core.report.YearShiftReport.months(2026, LocalDate.of(2026, 11, 30), r::resolve) { null }
        val year = com.bamnat.core.report.YearShiftReport.doc(2026, "제조·생산 교대", months, mon).text()
        assertTrue(year, year.contains("직업: 제조·생산 교대") && !Regex("이름[^\\n]*제조").containsMatchIn(year))
        val field = com.bamnat.core.report.FieldDaysProof.doc("", mon, mon, emptyList(), mon).text()
        assertTrue(field, field.contains("이름: "))
    }
}
