package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ExtraBasis
import com.bamnat.core.pay.ExtraRule
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.schedule.ScheduleResolver
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PAYS-14] 명세서 표본 5종의 달(2026년 10월, 10/9 한글날)과 회사의 실제 규칙(truth), 간편 설정이 막 끝난 처음 상태(start).
 * 명세서 글은 `src/test/resources/payslips/`의 고정 파일(사진을 기기 안에서 읽은 글과 같은 모양: 줄 순서·칸 나눔이 제각각).
 */
internal object SlipCases {
    val oct: YearMonth = YearMonth.of(2026, 10)
    private val since = LocalDate.of(2026, 1, 1)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(since, v)))
    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    class Case(val name: String, val file: String, val resolver: ScheduleResolver, val truth: PayProfile, val start: PayProfile)

    // A. 병원 간호 3교대 (듀티표): D 07~15, E 15~23, N 23~07(휴게 각 1시간), 6일 주기 D D E E N N O O.
    private val D = ShiftType(1, "데이", "D", 0xFF1E88E5, t(7), t(15), 60, t(11))
    private val E = ShiftType(2, "이브닝", "E", 0xFF43A047, t(15), t(23), 60, t(19))
    private val N = ShiftType(3, "나이트", "N", 0xFF5E35B1, t(23), t(7), 60, t(3))
    private val O = ShiftType(9, "오프", "O", 0xFF9E9E9E, null, null, isOff = true)
    private val nurseCal = ScheduleResolver(listOf(D, E, N, O), WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf(1L, 1L, 2L, 2L, 3L, 3L, 9L, 9L)), emptyList())
    val hospital = Case(
        "병원 간호 3교대", "hospital.txt", nurseCal,
        truth = PayProfile(Occupation.NURSE, WageType.MONTHLY, wage(2_600_000),
            items = listOf(
                PayItem("night", "나이트수당", wage(35_000), Cadence.PER_NIGHT_SHIFT),
                PayItem("license", "면허수당", wage(50_000), Cadence.MONTHLY, ordinary = true),
            )),
        start = WorkForms.apply(WorkForm.NURSE, 2_500_000, LocalDate.of(2026, 10, 1)),
    )

    // B. 제조 4조 2교대: 주간 08~20, 야간 20~08(휴게 1시간), 8일 주기. 10/17 쉬는 날 대근 주간, 회사는 대근 1회 150,000원.
    private val DAY12 = ShiftType(11, "주간", "주", 0xFF1E88E5, t(8), t(20), 60, t(12))
    private val NIGHT12 = ShiftType(12, "야간", "야", 0xFF5E35B1, t(20), t(8), 60, t(0))
    private val OFF = ShiftType(19, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val factoryCal = ScheduleResolver(listOf(DAY12, NIGHT12, OFF),
        WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf(11L, 11L, 12L, 12L, 19L, 19L, 19L, 19L)),
        listOf(DayEntry(d(17), extras = listOf(ExtraEntry(ExtraKind.COVER_ID, coveredShiftId = DAY12.id)))))
    val factory = Case(
        "제조 4조 2교대", "factory.txt", factoryCal,
        truth = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, wage(2_300_000),
            items = listOf(PayItem("shift", "교대수당", wage(100_000), Cadence.MONTHLY, ordinary = true)),
            rules = CompanyRules(extra = mapOf(ExtraKind.COVER_ID to ExtraRule(basis = ExtraBasis.FIXED_PER_TIME, amount = 150_000)))),
        start = WorkForms.apply(WorkForm.FOUR_TEAM, 2_300_000, LocalDate.of(2026, 10, 1)),
    )

    // C. 공기업 3조 2교대: 주간 09~18(휴게 1시간), 야간 18~09(휴게 2시간 00~02), 6일 주기. 빨간날은 교대제라 평일처럼.
    private val PD = ShiftType(21, "주간", "주", 0xFF1E88E5, t(9), t(18), 60, t(12))
    private val PN = ShiftType(22, "야간", "야", 0xFF5E35B1, t(18), t(9), 120, t(0))
    private val PO = ShiftType(29, "비번", "비", 0xFF9E9E9E, null, null, isOff = true)
    private val publicCal = ScheduleResolver(listOf(PD, PN, PO), WorkPattern.Rotation(LocalDate.of(2026, 9, 3), listOf(21L, 22L, 29L, 29L, 21L, 29L)), emptyList())
    val publicCorp = Case(
        "공기업 3조 2교대", "public.txt", publicCal,
        truth = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, wage(3_000_000),
            items = listOf(PayItem("shift", "교대근무수당", wage(150_000), Cadence.MONTHLY, ordinary = true)),
            rules = CompanyRules().withHolidayWork(HolidayWork.SHIFT_NORMAL)),
        start = WorkForms.apply(WorkForm.THREE_SHIFT, 3_000_000, LocalDate.of(2026, 10, 1)),
    )

    // D. 편의점 시급(5명 미만): 월~금 18~24, 휴게 없음. 시급 10,320원(2026 최저임금), 주휴수당.
    private val CS = ShiftType(31, "저녁", "저", 0xFFFF7043, t(18), t(0), 0)
    private val CO = ShiftType(39, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val storeCal = ScheduleResolver(listOf(CS, CO), WorkPattern.Rotation(LocalDate.of(2026, 9, 28), List(5) { 31L } + listOf(39L, 39L)), emptyList())
    val store = Case(
        "편의점 시급", "store.txt", storeCal,
        truth = PayProfile(Occupation.PART_TIME, WageType.HOURLY, wage(10_320), fiveOrMoreEmployees = false),
        start = WorkForms.apply(WorkForm.PART_TIME, 10_030, LocalDate.of(2026, 10, 1)).copy(fiveOrMoreEmployees = false),
    )

    // E. 물류 야간 고정: 21~06(휴게 1시간 01~02), 월~금. 회사 야간 가산 60%, 빨간날 근무는 특근 1회 80,000원.
    private val LN = ShiftType(41, "야간", "야", 0xFF5E35B1, t(21), t(6), 60, t(1))
    private val LO = ShiftType(49, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val logisticsCal = ScheduleResolver(listOf(LN, LO), WorkPattern.Rotation(LocalDate.of(2026, 9, 28), List(5) { 41L } + listOf(49L, 49L)), emptyList())
    val logistics = Case(
        "물류 야간", "logistics.txt", logisticsCal,
        truth = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, wage(2_400_000),
            rules = CompanyRules(nightPremium = 0.6).withHolidayWork(HolidayWork.AS_EXTRA).withHolidayShiftPay(LN.id, 80_000)),
        start = WorkForms.apply(WorkForm.OFFICE, 2_400_000, LocalDate.of(2026, 10, 1)),
    )

    val all = listOf(hospital, factory, publicCorp, store, logistics)
}
