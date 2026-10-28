package com.bamnat.core

import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CellFrom
import com.bamnat.core.pay.CellKind
import com.bamnat.core.pay.CellPresets
import com.bamnat.core.pay.CellWay
import com.bamnat.core.pay.CommonTerms
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HourlyFacts
import com.bamnat.core.pay.HourlySource
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleCell
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleFinding
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleSheet
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.SlipHourly
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * [RULE-ED-01][RULE-ED-03][RULE-ED-07][HOURLY-04] 1.30 AW: the one list of the company's rules (사진·글·직접·광장), its 칸 to and from
 * the pay setup, a 사람마다 칸 asked of the person, the 통상시급 card's facts, and the chips of '칸 더하기'.
 */
class RuleSheetTest {
    private val month = YearMonth.of(2026, 11)
    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), v)))
    private fun profile(base: Long = 2_090_000, vararg items: PayItem) =
        PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY, base = wage(base), items = items.toList())

    @Test fun readPutsTheHoursAndHourlyApartFromTheCells() {
        val found = listOf(
            RuleFinding("휴일연장수당 해당시간 × 2", RuleChange.Premium(PremiumTarget.HOLIDAY, 1.0, over8h = true), 0.85),
            RuleFinding("통상임금 산정 기준시간 180시간", RuleChange.MonthlyHours(180, HourlySource.RULES), 0.8),
            RuleFinding("명세서로 보면…", RuleChange.OrdinaryHourly(21_618.04, roundUp = true), 0.9),
            RuleFinding("야간 몇 %?", RuleChange.Premium(PremiumTarget.NIGHT, 0.5), 0.4),
        )
        val sheet = RuleSheet.read(found, unread = listOf("휴가보상 해당일수 × 1.5"))
        assertEquals(2, sheet.cells.size)
        assertEquals(180, sheet.common.hours)
        assertEquals(HourlySource.RULES, sheet.common.hoursSource)
        assertEquals(21_618.04, sheet.common.hourly!!, 1e-9)
        assertEquals(2, sheet.common.offers.size)
        // A rule read badly starts off, as 1.27 had it.
        assertFalse(sheet.cells.single { it.change is RuleChange.Premium && (it.change as RuleChange.Premium).target == PremiumTarget.NIGHT }.on)
        assertEquals(1, sheet.unread.size)
        // What goes in: the common values first, then the 칸 that are on.
        val changes = sheet.toChanges()
        assertEquals(3, changes.size)
        val p = sheet.apply(profile(), month)
        assertEquals(180, p.monthlyHours)
        assertEquals(HourlySource.RULES, p.hoursSource)
        assertEquals(1.0, p.rules.holidayPremiumOver8h!!, 1e-9)
        assertEquals(HourlySource.SLIP, p.ordinarySource)
    }

    @Test fun cellsSayItInTheUsersWords() {
        val over = RuleCell.of(RuleChange.Premium(PremiumTarget.HOLIDAY, 1.0, over8h = true))
        assertEquals("휴일 8시간 넘게 (휴일연장)", over.name)
        assertEquals("2배 (시급의 100% 더)", over.how)
        assertEquals(CellKind.PREMIUM, over.kind)
        val night = RuleCell.of(RuleChange.Premium(PremiumTarget.NIGHT, 0.5))
        assertEquals("시급의 50% 더", night.how)
        val ot = RuleCell.of(RuleChange.Premium(PremiumTarget.OVERTIME, 0.5))
        assertEquals("1.5배 (시급의 50% 더)", ot.how)
        val tenure = RuleCell.of(RuleChange.Custom(CustomRule("r1", "근속수당", RuleWhen(), RulePay.Monthly(50_000), minTenureMonths = 36)))
        assertEquals("근속수당 · 매달 50,000원 · 근속 3년 이상", tenure.summary)
        val deduct = RuleCell.of(RuleChange.Custom(CustomRule("r2", "기숙사비", RuleWhen(), RulePay.Monthly(50_000), deduct = true)))
        assertEquals(CellKind.DEDUCT, deduct.kind)
        assertEquals("매달 50,000원 빼요", deduct.how)
    }

    @Test fun aPersonalCellIsAskedOfThePersonAndCompanyOnesAreNot() {
        val own = PayItem("q", "자격수당", ValueTimeline(), Cadence.MONTHLY, ordinary = true, share = ItemShare.PERSONAL)
        val cell = RuleCell.of(RuleChange.Item(own))
        assertTrue(cell.askedOfEach)
        assertEquals("사람마다 달라요 · 각자 넣어요", cell.how)
        val p = RuleSheet(cells = listOf(cell)).apply(profile(), month)
        assertTrue(Unknowns.item("q") in p.estimatedFields)
        // The pay screen then asks for it ("이것만 넣으면 정확해져요").
        assertTrue(RuleOnlyNeeds.of(p, month, emptyList()).any { it.itemId == "q" })

        val fixed = PayItem("f", "식대", ValueTimeline(), Cadence.MONTHLY, share = ItemShare.FIXED, fixedAmount = 200_000)
        val fixedCell = RuleCell.of(RuleChange.Item(fixed))
        assertFalse(fixedCell.askedOfEach)
        assertEquals("회사 공통 · 매달 200,000원", fixedCell.how)
        val q = RuleSheet(cells = listOf(fixedCell)).apply(profile(), month)
        assertFalse(Unknowns.item("f") in q.estimatedFields)
    }

    @Test fun aPercentCellPaysItsShareOfTheBase() {
        // 1.29 turned a % 칸 made on a photo's result into an allowance of 0원; it now goes in whole.
        val pct = PayItem("b2", "기본급2", ValueTimeline(), Cadence.MONTHLY, ordinary = true, share = ItemShare.PERCENT, percent = 0.1, percentOf = BonusBasis.BASE)
        val cell = RuleCell.of(RuleChange.Item(pct))
        assertEquals("매달 기본급의 10%", cell.how)
        val p = RuleSheet(cells = listOf(cell)).apply(profile(2_000_000), month)
        assertEquals(2_200_000, PayCalculator.ordinaryWage(p, month).total)
        assertTrue(p.items.single().isPercent)
    }

    @Test fun anItemOfTheSameNameIsChangedNotAddedTwice() {
        val before = profile(2_000_000, PayItem("old", "자격수당", wage(30_000), Cadence.MONTHLY))
        val now = PayItem("new", "자격 수당", wage(57_500), Cadence.MONTHLY, ordinary = true)
        val p = RuleText.apply(before, listOf(RuleChange.Item(now)), month)
        assertEquals(1, p.items.size)
        assertEquals("old", p.items.single().id)
        assertEquals(57_500, p.items.single().amount.on(month).value)
    }

    @Test fun theSetupsOwnCellsAreChangedWhereTheyAre() {
        val rule1 = CustomRule("r1", "토요일 근무", RuleWhen(daysOfWeek = setOf(java.time.DayOfWeek.SATURDAY)), RulePay.PerTime(20_000))
        val rule2 = CustomRule("r2", "기숙사비", RuleWhen(), RulePay.Monthly(50_000), deduct = true)
        val bonus = PayItem("bonus", "상여", wage(0), Cadence.BONUS, months = setOf(3))
        val item = PayItem("i1", "자격수당", wage(57_500), Cadence.MONTHLY)
        var p = profile(2_000_000, item, bonus).copy(customRules = listOf(rule1, rule2))
        val sheet = RuleSheet.of(p)
        // 상여 is not a 칸 here (the pay screen has its own).
        assertEquals(listOf("i1", "r1", "r2"), sheet.cells.map { it.id })
        val changed = sheet.cells[1].copy(change = RuleChange.Custom(rule1.copy(pay = RulePay.PerTime(30_000))))
        p = RuleSheet.put(p, changed, month)
        assertEquals(listOf("r1", "r2"), p.customRules.map { it.id })
        assertEquals(RulePay.PerTime(30_000), p.customRules[0].pay)
        p = RuleSheet.setOn(p, sheet.cells[2], false)
        assertFalse(p.customRules[1].on)
        p = RuleSheet.remove(p, sheet.cells[0])
        assertEquals(listOf("bonus"), p.items.map { it.id })
        // The sheet's own list: added, put back where it was, ticked off, taken out.
        val s = RuleSheet().add(RuleCell.of(RuleChange.Premium(PremiumTarget.NIGHT, 0.5)))
        val id = s.cells.single().id
        assertFalse(s.toggle(id).cells.single().on)
        assertEquals(0.6, (s.put(s.cells.single().copy(change = RuleChange.Premium(PremiumTarget.NIGHT, 0.6))).cells.single().change as RuleChange.Premium).premium, 1e-9)
        assertTrue(s.remove(id).cells.isEmpty())
    }

    @Test fun theUsersOwnHoursAndHourlyAreTyped() {
        var terms = CommonTerms().withHours(180)
        assertEquals(HourlySource.TYPED, terms.hoursSource)
        terms = terms.withHours(180, HourlySource.PLAZA)
        assertEquals(HourlySource.PLAZA, (terms.toChanges().single() as RuleChange.MonthlyHours).source)
        terms = terms.withHourly(13_000.0)
        val p = RuleSheet(common = terms).apply(profile(), month)
        assertEquals(13_000.0, p.ordinaryHourly!!, 1e-9)
        assertEquals(HourlySource.TYPED, p.ordinarySource)
        // 0 takes the set 통상시급 off: worked out again.
        val back = RuleSheet(common = CommonTerms().withHourly(0.0)).apply(p, month)
        assertNull(back.ordinaryHourly)
        assertNull(back.ordinarySource)
        // A finding taken, then dropped.
        val f = RuleFinding("명세서", RuleChange.MonthlyHours(180, HourlySource.SLIP), 0.8)
        val taken = CommonTerms(offers = listOf(f)).take(f)
        assertTrue(taken.taken(f))
        assertNull(taken.drop(f).hours)
    }

    @Test fun theHourlyCardsFacts() {
        val p = profile(2_000_000, PayItem("q", "자격수당", wage(90_000), Cadence.MONTHLY, ordinary = true)).copy(monthlyHours = 209)
        val v = HourlyFacts.of(p, month)!!
        assertEquals(2_090_000, v.ordinary)
        assertEquals(10_000, v.hourly)
        assertEquals(105, v.percentOfBase)
        assertFalse(v.set)
        assertEquals("통상임금 2,090,000원 ÷ 209시간", v.formula)
        // "통상임금이 기본급의 몇 %인가요?" 150% of 2,000,000 on 180시간.
        assertEquals(16_666.67, HourlyFacts.fromPercent(2_000_000, 150.0, 180), 1e-9)
        assertEquals(0.0, HourlyFacts.fromPercent(0, 150.0, 180), 1e-9)
        // Set from a payslip.
        val set = HourlyFacts.of(p.copy(ordinaryHourly = 21_618.04, ordinarySource = HourlySource.SLIP), month)!!
        assertTrue(set.set)
        assertEquals(21_618, set.hourly)
        assertEquals(HourlySource.SLIP, set.source)
        // 시급제 is its own hour; no 기본급 yet says nothing.
        assertNull(HourlyFacts.of(p.copy(wageType = WageType.HOURLY), month))
        assertNull(HourlyFacts.of(profile(0), month))
    }

    @Test fun thePlantsPhotosComeTo180HoursOnTheSheet() {
        val slip = SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN, Random(1)))).read
        val rules = RuleText.read(OcrRows.join(PlantSample.rules(PlantSample.Noise.CLEAN, Random(1))))
        val sheet = RuleSheet.read(rules + SlipHourly.findings(slip, rules))
        assertEquals(180, sheet.common.hours)
        assertEquals(HourlySource.SLIP, sheet.common.hoursSource)
        assertTrue("${sheet.common.hoursWhy}", sheet.common.hoursWhy.contains("180시간"))
        assertNotNull(sheet.common.hourly)
        val p = sheet.apply(profile(2_397_500), YearMonth.of(2026, 9))
        assertEquals(180, p.monthlyHours)
        assertEquals(21_618.04, p.ordinaryHourly!!, 1e-9)
        // The rule rows are 칸 in the user's words.
        assertTrue(sheet.cells.any { it.name == "휴일 8시간 넘게 (휴일연장)" && it.how.startsWith("2배") })
    }

    @Test fun presetsStartTheCellAndAreFoundInASentence() {
        assertEquals(CellWay.MONTHLY, CellPresets.named("자격수당")!!.way)
        assertTrue(CellPresets.named("자격수당")!!.ordinary)
        val over = CellPresets.named("휴일연장")!!
        assertTrue(over.isPremium)
        assertEquals(RuleChange.Premium(PremiumTarget.HOLIDAY, 1.0, over8h = true), over.premium())
        assertEquals(ItemShare.PERCENT, CellPresets.named("기본급2")!!.share)
        assertEquals(BonusBasis.ORDINARY, CellPresets.named("위험예지")!!.basis)
        assertEquals("근속수당", CellPresets.inSentence("근속수당은 근속 3년 이상 월 5만원 지급")!!.name)
        assertEquals("심야 시간 수당", CellPresets.inSentence("심야수당 시간당 2,000원")!!.name)
        assertNull(CellPresets.inSentence("휴가보상 해당일수 × 1.5"))
        assertEquals("휴가보상금", CellPresets.nameIn("휴가보상금 해당일수 × 1.5"))
        assertTrue(CellPresets.search("수당").size >= 5)
        assertEquals(listOf("식대", "식대 공제"), CellPresets.search("식대").map { it.name }.filter { it.startsWith("식대") })
        assertEquals(CellPresets.ALL.size, CellPresets.search("").size)
        // A sentence-only cell is said where it came from.
        assertEquals(CellFrom.PHOTO, RuleSheet.read(listOf(RuleFinding("야간 50%", RuleChange.Premium(PremiumTarget.NIGHT, 0.5), 0.9))).cells.single().from)
    }
}
