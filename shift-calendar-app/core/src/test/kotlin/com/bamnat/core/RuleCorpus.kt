package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.BreakPay
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.WeeklyHoliday
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleDayKind
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ 순번 51·47(규칙 정답 시험): 회사 규칙 문서 34개. 취업규칙 조문(줄이 꺾인 채), 글머리 목록, 표를 읽은 줄, 동료가 보낸
 * 말투를 섞었어요. 문서마다 손으로 만든 정답 규칙([Doc.truth])이 있고, 그 정답으로 계산한 11·12월 급여가 정답 금액이에요.
 * [Doc.unread]는 앱이 읽지 못해 '직접 넣기' 칸으로 보여야 하는 문장 수예요.
 *
 * 근무: 월~수 주간(08~17, 휴게 12시 1시간), 목·금 야간(22~07, 휴게 2시 1시간), 토·일 쉼. 11/3 잔업 17~20, 11/15(일) 특근
 * 08~19, 12/6(일) 특근 09~13(일요일이 주휴일이라 휴일 근무). 12/25는 금요일 야간(공휴일 근무). 기본급 2,508,000원, 월 209시간(통상시급 12,000원), 입사 2022-03-01.
 */
object RuleCorpus {
    data class Doc(val id: String, val style: String, val text: String, val truth: List<RuleChange>, val unread: Int = 0, val hand: Long? = null)

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    val day = ShiftType(1, "주간", "주", 0xFF1E88E5, t(8), t(17), 60, t(12))
    val night = ShiftType(2, "야간", "야", 0xFF5E35B1, t(22), t(7), 60, t(2))
    val off = ShiftType(9, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    val shiftTypes = listOf(day, night, off)
    val kinds = emptyList<ExtraKind>()

    val resolver = ScheduleResolver(
        shiftTypes,
        WorkPattern.Rotation(LocalDate.of(2026, 10, 26), listOf(1L, 1L, 1L, 2L, 2L, 9L, 9L)),
        listOf(
            DayEntry(LocalDate.of(2026, 11, 3), extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, t(17), t(20)))),
            DayEntry(LocalDate.of(2026, 11, 15), extras = listOf(ExtraEntry(ExtraKind.SPECIAL_ID, t(8), t(19)))),
            DayEntry(LocalDate.of(2026, 12, 6), extras = listOf(ExtraEntry(ExtraKind.SPECIAL_ID, t(9), t(13)))),
        ),
    )
    val months = listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12))
    val from: YearMonth = YearMonth.of(2026, 11)

    val base = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_508_000))),
        hireDate = LocalDate.of(2022, 3, 1),
        rules = CompanyRules(weeklyHoliday = WeeklyHoliday.Fixed(DayOfWeek.SUNDAY)),
    )

    private fun prem(t: PremiumTarget, p: Double, over8h: Boolean = false) = RuleChange.Premium(t, p, over8h)
    private fun custom(name: String, pay: RulePay, whenever: RuleWhen = RuleWhen(), deduct: Boolean = false, min: Int = 0, max: Int = 0,
                       cap: Long = 0, times: Int = 0, from: YearMonth? = null, until: YearMonth? = null) =
        RuleChange.Custom(CustomRule("t-$name", name, whenever, pay, deduct = deduct, minTenureMonths = min, maxTenureMonths = max,
            capPerMonth = cap, maxTimes = times, from = from, until = until))
    private fun allowance(name: String, amount: Long, cadence: Cadence = Cadence.MONTHLY, shifts: Set<Long> = emptySet(), ordinary: Boolean? = null) =
        RuleChange.Allowance(name, amount, cadence, shifts, ordinary)
    private val even = setOf(2, 4, 6, 8, 10, 12)

    val DOCS: List<Doc> = listOf(
        Doc("D01", "목록", "연장 160%\n야간 70% 가산\n휴일 8시간 이내 150%, 초과 250%",
            listOf(prem(PremiumTarget.OVERTIME, 0.6), prem(PremiumTarget.NIGHT, 0.7), prem(PremiumTarget.HOLIDAY, 0.5), prem(PremiumTarget.HOLIDAY, 1.5, true))),
        Doc("D02", "조문", "제20조(연장·야간근로) ① 연장근로에 대하여는 통상임금의\n100분의 70을 가산하여 지급한다.\n" +
            "② 야간근로(오후 10시부터 다음 날 오전 6시까지)에 대하여는\n통상임금의 100분의 60을 가산하여 지급한다.",
            listOf(prem(PremiumTarget.OVERTIME, 0.7), RuleChange.NightWindow(t(22), t(6)), prem(PremiumTarget.NIGHT, 0.6))),
        Doc("D03", "목록", "야간(23~07) 60%", listOf(RuleChange.NightWindow(t(23), t(7)), prem(PremiumTarget.NIGHT, 0.6))),
        Doc("D04", "목록", "연장·야간·휴일근로는 통상시급의 1.6배",
            listOf(prem(PremiumTarget.OVERTIME, 0.6), prem(PremiumTarget.NIGHT, 0.6), prem(PremiumTarget.HOLIDAY, 0.6))),
        Doc("D05", "목록", "식대 월 200,000원", listOf(allowance("식대", 200_000)), hand = 400_000),
        Doc("D06", "목록", "근속수당: 근속 3년 이상 월 50,000원", listOf(custom("근속수당", RulePay.Monthly(50_000), min = 36)), hand = 100_000),
        Doc("D07", "목록", "근속수당 1년 이상 30,000원, 3년 이상 50,000원, 5년 이상 100,000원",
            listOf(custom("a", RulePay.Monthly(30_000), min = 12, max = 36), custom("b", RulePay.Monthly(50_000), min = 36, max = 60),
                custom("c", RulePay.Monthly(100_000), min = 60)), hand = 100_000),
        Doc("D08", "목록", "교대수당 근무일마다 10,000원", listOf(allowance("교대수당", 10_000, Cadence.PER_WORK_DAY))),
        Doc("D09", "조문", "상여금은 연 400%(통상임금 기준)로 하며 짝수달에 나누어 지급한다.",
            listOf(RuleChange.Bonus("상여금", 400.0, null, even, basis = BonusBasis.ORDINARY))),
        Doc("D10", "조문", "제15조(소정근로시간) 월 소정근로시간은 226시간으로 한다.", listOf(RuleChange.MonthlyHours(226))),
        Doc("D11", "조문", "주휴수당은 1주 소정근로일을 개근한 자에게 1일분을 유급으로 지급한다.\n직책수당 월 150,000원",
            listOf(allowance("직책수당", 150_000))),
        Doc("D12", "말투", "기숙사비는 매월 50,000원 공제해요", listOf(custom("기숙사비", RulePay.Monthly(50_000), deduct = true)), hand = -100_000),
        Doc("D13", "목록", "교대수당 근무일마다 10,000원, 월 150,000원 한도",
            listOf(custom("교대수당", RulePay.PerTime(10_000), RuleWhen(dayKind = RuleDayKind.WORK_DAY), cap = 150_000))),
        Doc("D14", "목록", "교통보조 근무일마다 5,000원 (월 최대 10회)",
            listOf(custom("교통보조", RulePay.PerTime(5_000), RuleWhen(dayKind = RuleDayKind.WORK_DAY), times = 10)), hand = 100_000),
        Doc("D15", "목록", "자격수당 월 30,000원 (2026년 12월부터)", listOf(custom("자격수당", RulePay.Monthly(30_000), from = YearMonth.of(2026, 12)))),
        Doc("D16", "목록", "근속수당: 근속 3년 이상 통상임금의 5%", listOf(custom("근속수당", RulePay.OfOrdinaryMonthly(0.05), min = 36))),
        Doc("D17", "목록", "근속 3년 이상 기본급의 3%", listOf(custom("근속수당", RulePay.OfBaseMonthly(0.03), min = 36)), hand = 150_480),
        Doc("D18", "목록", "휴일근로: 8시간까지 1.5배, 8시간 넘으면 2.5배",
            listOf(prem(PremiumTarget.HOLIDAY, 0.5), prem(PremiumTarget.HOLIDAY, 1.5, true))),
        Doc("D19", "조문", "휴게시간도 근로시간으로 본다.", listOf(RuleChange.Breaks(BreakPay.AS_WORK))),
        Doc("D20", "말투", "야간수당 시간당 3,000원 더 줘요",
            listOf(custom("야간수당", RulePay.PerHour(3_000), RuleWhen(from = t(22), to = t(6))))),
        Doc("D21", "표", "수당명  지급기준  금액\n직무수당  매월  100,000\n식대  매월  150,000\n교대수당  근무 1회  8,000",
            listOf(allowance("직무수당", 100_000), allowance("식대", 150_000), allowance("교대수당", 8_000, Cadence.PER_WORK_DAY))),
        Doc("D22", "목록", "직급수당 기본급의 5%", listOf(custom("직급수당", RulePay.OfBaseMonthly(0.05))), hand = 250_800),
        Doc("D23", "목록", "일요일 근무 1회 30,000원",
            listOf(custom("일요일", RulePay.PerTime(30_000), RuleWhen(daysOfWeek = setOf(DayOfWeek.SUNDAY)))), hand = 60_000),
        Doc("D24", "조문", "1일 10시간을 초과하여 근무한 경우 초과 시간당 4,000원을 지급한다.",
            listOf(custom("10시간", RulePay.PerHour(4_000), RuleWhen(overMinutes = 600)))),
        Doc("D25", "목록", "식대 월 100,000원\n성과급은 연 1회 경영성과에 따라 0~200% 지급\n학자금은 자녀 1인당 연 200만원 한도 내 실비 지급",
            listOf(allowance("식대", 100_000)), unread = 2),
        Doc("D26", "목록", "기본급 2,508,000원\n연장 170%",
            listOf(RuleChange.Base(WageType.MONTHLY, 2_508_000), prem(PremiumTarget.OVERTIME, 0.7))),
        Doc("D27", "붙음", "연장근로는통상임금의170%를지급하고야간근로는60%를가산한다",
            listOf(prem(PremiumTarget.OVERTIME, 0.7), prem(PremiumTarget.NIGHT, 0.6))),
        Doc("D28", "목록", "연말상여 12월 기본급의 100%", listOf(RuleChange.Bonus("연말상여", 100.0, null, setOf(12)))),
        Doc("D29", "조문", "상여금은 3, 6, 9, 12월에 각 기본급의 100%를 지급한다.",
            listOf(RuleChange.Bonus("상여금", 400.0, null, setOf(3, 6, 9, 12)))),
        Doc("D30", "목록", "근속수당 1년 이상 3년 미만 20,000원, 3년 이상 5년 미만 40,000원",
            listOf(custom("a", RulePay.Monthly(20_000), min = 12, max = 36), custom("b", RulePay.Monthly(40_000), min = 36, max = 60)), hand = 80_000),
        Doc("D31", "목록", "야간 근무 1회 25,000원", listOf(allowance("야간", 25_000, Cadence.PER_SHIFT_TYPE, setOf(2L)))),
        Doc("D32", "목록", "야간근무 1회 20,000원, 월 100,000원 한도",
            listOf(custom("야간근무", RulePay.PerTime(20_000), RuleWhen(shiftTypeIds = setOf(2L)), cap = 100_000)), hand = 200_000),
        Doc("D33", "조문", "제22조(휴게) 휴게시간은 근로시간에 포함하지 아니한다.\n제23조(연장근로수당) 연장근로에 대하여는 통상임금의 100분의 80을\n가산하여 지급한다.",
            listOf(RuleChange.Breaks(BreakPay.UNPAID), prem(PremiumTarget.OVERTIME, 0.8))),
        Doc("D34", "말투", "교대수당은 한 번에 10,000원이고 휴일은 8시간 넘으면 2.5배예요",
            listOf(allowance("교대수당", 10_000, Cadence.PER_WORK_DAY), prem(PremiumTarget.HOLIDAY, 1.5, true))),
    )

    /**
     * What a phone's text recognition does to a printed rules page: spaces lost between words, the dot of 1.5배, a comma in an
     * amount read as a dot, 원 as 윈, 0 as O, 지급 as 지금, a comma between clauses lost. [level] 0 is the page as printed.
     */
    fun noisy(text: String, level: Int, r: Random): String {
        if (level == 0) return text
        val p = if (level == 1) 0.5 else 1.0
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val prev = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            when {
                ch == ' ' && r.nextDouble() < 0.35 * p -> Unit
                ch == '.' && prev?.isDigit() == true && next == '5' && r.nextDouble() < 0.5 * p -> Unit
                ch == ',' && prev?.isDigit() == true && next?.isDigit() == true && r.nextDouble() < 0.25 * p -> out.append('.')
                ch == ',' && prev == '%' && r.nextDouble() < 0.4 * p -> Unit
                ch == '원' && r.nextDouble() < 0.1 * p -> out.append('윈')
                ch == '0' && prev?.isDigit() == true && r.nextDouble() < 0.06 * p -> out.append('O')
                ch == '급' && prev == '지' && r.nextDouble() < 0.3 * p -> out.append('금')
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }
}
