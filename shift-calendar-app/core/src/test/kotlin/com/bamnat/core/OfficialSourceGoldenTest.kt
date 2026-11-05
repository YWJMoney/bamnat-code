package com.bamnat.core

import com.bamnat.core.leave.LeaveAccrual
import com.bamnat.core.leave.LeavePolicy
import com.bamnat.core.leave.LotKind
import com.bamnat.core.pay.RateTable
import com.bamnat.core.pay.StandardHours
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.WithholdingTable
import com.bamnat.core.work.QuitCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [P3-ACC] 1.38 정확도 시험 (사용자 2026-11-05 "인터넷으로 가져올 수 있는 데이터·예시로 직접 시험, 정확도 100%").
 * Every expected number here is read from an official text or a figure the 정부 published, not from this app:
 *
 * - 소득세법 시행령 [별표 2] 근로소득 간이세액표 <개정 2026. 2. 27.> (국가법령정보센터 별표 PDF). The whole table
 *   (646 rows × 11 columns) is also checked cell by cell against that PDF by `tools/taxtable_check.py`.
 * - 최저임금 고시: 2026년 10,320원 (월 2,156,880원), 2027년 10,700원 (월 2,236,300원, 고용노동부 고시 2026-08-05).
 * - 국민연금 기준소득월액 하한·상한 보건복지부고시 제2026-31호 (2026-07-01부터 41만~659만 원), 보험료율 2026 9.5%,
 *   2027 10% (국민연금법 부칙 법률 제20903호).
 * - 건강보험료율 7.19% (2026, 2027 동결), 장기요양보험료율 2026 0.9448%, 2027 0.9994% (2026-10-30 장기요양위원회,
 *   건강보험료 대비 13.90%), 고용보험 실업급여 1.8%. 10원 미만은 버림 (공단 고지 방식).
 * - 근로기준법 시행령 제6조 제2항 (월 통상임금 산정 기준시간), 제18조 제3항 (주 15시간 미만 주휴 없음), 제55조,
 *   제60조 (연차), 대법원 2021다227100 (1년 계약 만료 퇴직은 15일이 생기지 않음), 근로자퇴직급여 보장법 제4·8조.
 */
class OfficialSourceGoldenTest {
    private val apr26 = LocalDate.of(2026, 4, 1)
    private val aug26 = LocalDate.of(2026, 8, 1)
    private val feb27 = LocalDate.of(2027, 2, 1)

    // ---- 근로소득 간이세액표 (별표 2, 2026-02-27 개정) ----

    @Test fun taxTableRowsFromTheOfficialText() {
        val t = WithholdingTable.on(apr26)!!
        // "3,000 3,020  74,350 56,850 31,940 26,690 21,440 17,100 13,730 10,350 6,980 3,600 -"
        val row = listOf(74_350L, 56_850, 31_940, 26_690, 21_440, 17_100, 13_730, 10_350, 6_980, 3_600, 0)
        row.forEachIndexed { i, v -> assertEquals("가족 ${i + 1}명", v, t.tax(3_010_000, i + 1)) }
        // "2,000 2,010  19,520 14,750 6,600 3,220 -"
        assertEquals(19_520L, t.tax(2_000_000, 1))
        assertEquals(3_220L, t.tax(2_009_999, 4))
        assertEquals(0L, t.tax(2_000_000, 5))
        // "3,960 3,980  190,620 162,640 104,700 ..." and the first row: below 770천원 there is no tax.
        assertEquals(190_620L, t.tax(3_970_000, 1))
        assertEquals(0L, t.tax(769_999, 1))
        // "10,000천원  1,507,400 1,431,570 1,200,840 ... 960,840"
        assertEquals(1_507_400L, t.tax(10_000_000, 1))
        assertEquals(960_840L, t.tax(10_000_000, 11))
    }

    @Test fun taxTableAboveTenMillionUsesTheTextsFormulas() {
        val t = WithholdingTable.on(apr26)!!
        // 10,000천원 초과 14,000천원 이하: 해당 세액 + 25,000원 + 초과액 × 98% × 35%.
        assertEquals(1_507_400L + 25_000 + 686_000, t.tax(12_000_000, 1))
        // 14,000천원 초과 28,000천원 이하: + 1,397,000원 + 초과액 × 98% × 38%.
        assertEquals(1_507_400L + 1_397_000 + 372_400, t.tax(15_000_000, 1))
        // 30,000천원 초과 45,000천원 이하: + 7,394,600원 + 초과액 × 40% (98% 없음).
        assertEquals(1_507_400L + 7_394_600 + 400_000, t.tax(31_000_000, 1))
        // 87,000천원 초과: + 31,034,600원 + 초과액 × 45%.
        assertEquals(1_431_570L + 31_034_600 + 450_000, t.tax(88_000_000, 2))
    }

    @Test fun taxTableChildrenAndLargeFamiliesFollowNotes3And4() {
        val t = WithholdingTable.on(apr26)!!
        // 제3호: 8~20세 자녀 1명 20,830원, 2명 45,830원, 3명부터 1명마다 33,330원 더; 음수면 0원.
        assertEquals(31_940L - 20_830, t.tax(3_010_000, 3, children = 1))
        assertEquals(0L, t.tax(3_010_000, 4, children = 2)) // 26,690 − 45,830 < 0 → 0원
        assertEquals(1_140_840L - 45_830 - 33_330, t.tax(10_000_000, 5, children = 3))
        // 제4호: 11명 초과 = 11명 세액 − (10명 세액 − 11명 세액) × 초과 인원.
        assertEquals(960_840L - (990_840 - 960_840) * 2, t.tax(10_000_000, 13))
    }

    @Test fun takeHomeUsesTheTableAndATenthForLocalTax() {
        val d = TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, dependents = 1, on = apr26).deductions.toMap()
        assertEquals(74_350L, d[TakeHome.TAX_LABEL])
        assertEquals(7_430L, d[TakeHome.LOCAL_TAX_LABEL])
        // 식대 20만 원 비과세 (소득세법 시행령 제17조의2): the table and the 보험 bases use 2,800,000원.
        val m = TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, dependents = 1, on = apr26, nonTaxable = 200_000).deductions.toMap()
        assertEquals(WithholdingTable.on(apr26)!!.tax(2_800_000, 1), m[TakeHome.TAX_LABEL])
        assertEquals(133_000L, m["국민연금"])
        assertEquals(100_660L, m["건강보험"])
    }

    // ---- 4대보험 근로자 몫 ----

    @Test fun insurance2026OnThreeMillion() {
        val d = TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = apr26).deductions.toMap()
        assertEquals(142_500L, d["국민연금"]) // 3,000,000 × 4.75%
        assertEquals(107_850L, d["건강보험"]) // 3,000,000 × 3.595%
        assertEquals(14_170L, d["장기요양"]) // 107,850 × (0.9448 ÷ 7.19 = 13.14%) = 14,171 → 14,170
        assertEquals(27_000L, d["고용보험"]) // 3,000,000 × 0.9%
    }

    @Test fun insurance2026OnTheMinimumWageMonth() {
        // 2,156,880원 (10,320원 × 209시간).
        val d = TakeHome.estimate(2_156_880, TaxMode.INSURANCE_4, on = apr26).deductions.toMap()
        assertEquals(102_410L, d["국민연금"]) // 기준소득월액 2,156,000 (천원 미만 버림) × 4.75% = 102,410
        assertEquals(77_530L, d["건강보험"]) // 77,539.8 → 77,530
        assertEquals(10_180L, d["장기요양"]) // 77,530 × 13.14% = 10,187.4 → 10,180
        assertEquals(19_410L, d["고용보험"]) // 19,411.9 → 19,410
    }

    @Test fun pensionLimitsFromNotice2026_31() {
        // 상한 659만 원 × 4.75% = 313,025 → 313,020 (보도 '본인 부담 산술상 31만3025원').
        assertEquals(313_020L, TakeHome.estimate(9_000_000, TaxMode.INSURANCE_4, on = aug26).deductions.toMap()["국민연금"])
        // 하한 41만 원 × 4.75% = 19,475 → 19,470.
        assertEquals(19_470L, TakeHome.estimate(300_000, TaxMode.INSURANCE_4, on = aug26).deductions.toMap()["국민연금"])
        // 2026년 6월까지는 옛 상한 637만 원: 6,370,000 × 4.75% = 302,575 → 302,570.
        assertEquals(302_570L, TakeHome.estimate(9_000_000, TaxMode.INSURANCE_4, on = LocalDate.of(2026, 6, 1)).deductions.toMap()["국민연금"])
    }

    /**
     * 4대사회보험 정보연계센터 모의계산 (2026, 150명 미만), 코디네이터가 2026-11-05에 옮겨 적은 값과 한 줄씩.
     * 상한·하한은 국민연금에만 있고(659만·41만), 건강·장기요양·고용은 보수 그대로.
     */
    @Test fun socialInsuranceCalculator2026AtTheLimits() {
        fun four(pay: Long) = TakeHome.estimate(pay, TaxMode.INSURANCE_4, on = aug26).deductions.toMap()
            .let { listOf(it["국민연금"], it["건강보험"], it["장기요양"], it["고용보험"]) }
        assertEquals(listOf(313_020L, 323_550L, 42_510L, 81_000L), four(9_000_000))
        assertEquals(760_080L, four(9_000_000).sumOf { it!! })
        assertEquals(listOf(19_470L, 10_780L, 1_410L, 2_700L), four(300_000))
        assertEquals(34_360L, four(300_000).sumOf { it!! })
        assertEquals(291_520L, four(3_000_000).sumOf { it!! })
        assertEquals(209_530L, TakeHome.estimate(2_156_880, TaxMode.INSURANCE_4, on = aug26).deductions.toMap()
            .filterKeys { it in setOf("국민연금", "건강보험", "장기요양", "고용보험") }.values.sum())
    }

    @Test fun employerShare2026OnThreeMillion() {
        // 사업주: 국민 142,500 + 건강 107,850 + 장기 14,170 + 고용 34,500 (0.9% + 고용안정·직능 0.25%) = 299,020.
        assertEquals(299_020L, com.bamnat.core.work.LaborCost.employerInsurance(3_000_000, aug26))
        val all = com.bamnat.core.boss.Insurance(pension = true, health = true, employment = true)
        assertEquals(299_020L, com.bamnat.core.boss.PayrollLedger.employerInsurance(3_000_000, all, aug26))
        // 하나씩 뺐을 때도 그 몫만 정확히 빠져요.
        assertEquals(299_020L - 34_500, com.bamnat.core.boss.PayrollLedger.employerInsurance(3_000_000, all.copy(employment = false), aug26))
        assertEquals(299_020L - 107_850 - 14_170, com.bamnat.core.boss.PayrollLedger.employerInsurance(3_000_000, all.copy(health = false), aug26))
        assertEquals(299_020L - 142_500, com.bamnat.core.boss.PayrollLedger.employerInsurance(3_000_000, all.copy(pension = false), aug26))
        // 900만: 국민연금만 상한(659만)에서 멈춰요. 313,020 + 323,550 + 42,510 + 103,500 = 782,580.
        assertEquals(782_580L, com.bamnat.core.work.LaborCost.employerInsurance(9_000_000, aug26))
    }

    /**
     * 2027: the app ships 장기요양 as 'not yet fixed' and the 2026-10-30 rate (0.9994%) comes in the signed
     * `remote/v1/rates.json` (서명 열쇠 대기, release/1.38-대기/2027-장기요양-요율.patch). This lays that file over the
     * built-in years exactly as the phone does and checks the official amounts.
     */
    @Test fun insurance2027() {
        val file = com.bamnat.core.remote.RemoteFiles.parse(
            """{"format":1,"kind":"rates","version":2026110501,"issued":"2026-11-05","minApp":33,"data":{"rates":[{"from":"2027-01-01",
            "version":"2027.1","source":"2027년 최저임금 고시, 2026-10-30 장기요양위원회","minimumHourlyWage":10700,"pensionRate":0.1,
            "healthRate":0.0719,"longTermCareRate":0.009994,"unemploymentRate":0.018}],"pensionBases":[]}}""".toByteArray(),
        )
        try {
            RateTable.use(file)
            val d = TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = feb27).deductions.toMap()
            assertEquals(150_000L, d["국민연금"]) // 10% ÷ 2
            assertEquals(107_850L, d["건강보험"]) // 7.19% 동결
            assertEquals(14_990L, d["장기요양"]) // 107,850 × 13.90% = 14,991 → 14,990
            assertEquals(27_000L, d["고용보험"])
            val r = RateTable.on(feb27)
            assertEquals(0.1390, r.longTermCareRate!! / r.healthRate!!, 0.00005)
            // 사장님 인건비 200만 원: 100,000 + 71,900 + 9,990 + 23,000 (고용 0.9% + 고용안정 0.25%) = 204,890원.
            assertEquals(204_890L, com.bamnat.core.work.LaborCost.employerInsurance(2_000_000, LocalDate.of(2027, 5, 1)))
        } finally {
            RateTable.use(null)
        }
    }

    // ---- 최저임금·주휴·기준시간 ----

    @Test fun minimumWageMonthsAsPublished() {
        assertEquals(209, StandardHours.monthly(40 * 60))
        assertEquals(2_156_880L, RateTable.on(apr26).minimumHourlyWage * StandardHours.monthly(40 * 60))
        assertEquals(2_236_300L, RateTable.on(feb27).minimumHourlyWage * StandardHours.monthly(40 * 60))
        assertEquals(10_030, RateTable.on(LocalDate.of(2025, 6, 1)).minimumHourlyWage)
    }

    @Test fun weeklyHolidayHoursFollowTheLaw() {
        assertEquals(0, StandardHours.weeklyHolidayMinutes(14 * 60 + 59)) // 주 15시간 미만: 없음
        assertEquals(3 * 60, StandardHours.weeklyHolidayMinutes(15 * 60)) // 15 ÷ 40 × 8 = 3시간
        assertEquals(4 * 60, StandardHours.weeklyHolidayMinutes(20 * 60))
        assertEquals(8 * 60, StandardHours.weeklyHolidayMinutes(40 * 60))
        assertEquals(8 * 60, StandardHours.weeklyHolidayMinutes(52 * 60)) // 40시간을 넘는 연장은 주휴에 안 들어감
        // 시행령 제6조 제2항: (20 + 4) × 365 ÷ 7 ÷ 12 = 104.3 → 104시간.
        assertEquals(104, StandardHours.monthly(20 * 60))
    }

    // ---- 연차 (제60조, 대법원 2021다227100) ----

    @Test fun annualLeaveDays() {
        val hire = LocalDate.of(2026, 1, 1)
        assertEquals(11, LeaveAccrual.entitlement(hire, LocalDate.of(2026, 12, 31)))
        assertEquals(15, LeaveAccrual.entitlement(hire, LocalDate.of(2027, 1, 1)))
        assertEquals(16, LeaveAccrual.entitlement(hire, LocalDate.of(2029, 1, 1))) // 3년: 15 + 1
        assertEquals(25, LeaveAccrual.entitlement(hire, LocalDate.of(2047, 1, 1))) // 21년: 25일 상한
        assertEquals(25, LeaveAccrual.entitlement(hire, LocalDate.of(2060, 1, 1)))
    }

    @Test fun aOneYearContractEndingOnItsLastDayMakesNo15Days() {
        val hire = LocalDate.of(2026, 1, 1)
        val lots = LeaveAccrual.lots(hire, LeavePolicy(), LocalDate.of(2026, 12, 31))
        assertEquals(11, lots.count { it.kind == LotKind.MONTHLY })
        assertTrue(lots.none { it.kind == LotKind.ANNUAL })
        assertEquals(11, QuitCheck.leaveDays(hire, LocalDate.of(2026, 12, 31), 40.0))
        // 하루 더 다니면 (366일째 근로관계) 15일이 생겨요.
        assertEquals(15.0, LeaveAccrual.lots(hire, LeavePolicy(), LocalDate.of(2027, 1, 1)).single { it.kind == LotKind.ANNUAL }.days, 0.0)
        assertEquals(15, QuitCheck.leaveDays(hire, LocalDate.of(2027, 1, 1), 40.0))
    }

    // ---- 퇴직금 (근로자퇴직급여 보장법 제4·8조) ----

    @Test fun severanceNeedsAYearAnd15Hours() {
        val hire = LocalDate.of(2025, 3, 1)
        assertFalse(QuitCheck.severance(hire, LocalDate.of(2026, 2, 27), 40.0, 10_320).eligible)
        assertTrue(QuitCheck.severance(hire, LocalDate.of(2026, 2, 28), 40.0, 10_320).eligible)
        assertFalse(QuitCheck.severance(hire, LocalDate.of(2026, 6, 30), 14.5, 10_320).eligible)
        // 퇴직 전 3개월 (4/1~6/30, 91일) 임금 6,470,640원 ÷ 91 = 71,105.9 → 71,106원; × 30 × 365일 ÷ 365 = 2,133,180원.
        val s = QuitCheck.severance(LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30), 40.0, 10_320, threeMonthPay = 6_470_640)
        assertEquals(365.0 / 365, s.years, 1e-9)
        assertEquals(71_106L, s.averageDaily)
        assertEquals(2_133_180L, s.amount)
    }

    // ---- 소수 계산 (Double) 때문에 1원·10원이 빠지지 않게 ----

    @Test fun roundAmountsStayRound() {
        // 3,000,000 × 0.9% was 26,999.99… and cut to 26,990원.
        assertEquals(27_000L, com.bamnat.core.pay.WonCut.cut10(3_000_000, 0.009))
        assertEquals(27_000L, TakeHome.estimate(3_000_000, TaxMode.INSURANCE_4, on = apr26).deductions.toMap()["고용보험"])
        // "1.13만원" was 11,299원.
        assertEquals(11_300L, com.bamnat.core.pay.RuleImport.parseAmount("수당 1.13만원"))
        // 장기요양 multiplies before it divides: 107,850 × 1.0% ÷ 7.19% = 15,000원 exactly.
        assertEquals(15_000L, com.bamnat.core.pay.WonCut.cut10(107_850, com.bamnat.core.pay.WonCut.Ratio.of(0.010, 0.0719)))
    }
}
