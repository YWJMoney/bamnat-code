package com.bamnat.core

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.ItemShares
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ShareGuess
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [ITEM-01][ITEM-05][ITEM-06] 1.30 묶음 BC: who sets an allowance's amount (회사 공통·사람마다·다른 금액의 %), what the
 * month pays with it, the name's guess, and that a company amount or a percent is never asked as 금액 모름.
 */
class PayItemShareTest {
    private fun wage(v: Long) = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), v)))
    private val month = YearMonth.of(2026, 10)
    private val office = ShiftType(10, "사무", "사", 0xFF000000, LocalTime.of(9, 0), LocalTime.of(18, 0), 60, LocalTime.of(12, 0))
    private val off = ShiftType(99, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val resolver = ScheduleResolver(listOf(office, off),
        WorkPattern.Rotation(LocalDate.of(2026, 9, 28), List(5) { office.id } + listOf(off.id, off.id)), emptyList())

    /** 식대 회사 공통 200,000원 (typed by no one), 위험예지수당 기본급의 10%, 자격수당 사람마다 50,000원. */
    private val meals = PayItem("m", "식대", ValueTimeline(), Cadence.MONTHLY, share = ItemShare.FIXED, fixedAmount = 200_000)
    private val risk = PayItem("r", "위험예지수당", ValueTimeline(), Cadence.MONTHLY, share = ItemShare.PERCENT, percent = 0.1)
    private val license = PayItem("l", "자격수당", wage(50_000), Cadence.MONTHLY, ordinary = true)
    private fun profile(vararg items: PayItem) =
        PayProfile(occupation = com.bamnat.core.pay.Occupation.CUSTOM, wageType = WageType.MONTHLY, base = wage(2_000_000), items = items.toList())

    @Test fun `a company amount and a percent are what the month pays`() {
        val p = ItemShares.resolve(profile(meals, risk, license), month)
        val by = p.items.associate { it.id to it.amount.on(month).value }
        assertEquals(200_000L, by["m"])
        assertEquals(200_000L, by["r"])
        assertEquals(50_000L, by["l"])
        assertEquals("기본급의 10% · 매달", ItemShares.describe(risk))
        assertEquals("회사 공통 · 매달 · 200,000원", ItemShares.describe(meals))
        assertEquals(ItemShare.PERSONAL.label, ItemShares.describe(license))
    }

    @Test fun `a percent of 통상임금 counts the allowances in it but never another percent`() {
        val ordinaryRisk = risk.copy(percentOf = BonusBasis.ORDINARY)
        val other = PayItem("o", "기본급2", ValueTimeline(), Cadence.MONTHLY, share = ItemShare.PERCENT, percent = 1.0, ordinary = true)
        val p = ItemShares.resolve(profile(meals.copy(ordinary = true), ordinaryRisk, license, other), month)
        val by = p.items.associate { it.id to it.amount.on(month).value }
        // 2,000,000 + 식대 200,000 + 자격 50,000 = 2,250,000; 기본급2 (a percent) is not in it.
        assertEquals(225_000L, by["r"])
        assertEquals(2_000_000L, by["o"])
        assertEquals(2_250_000L, ItemShares.basisOf(profile(meals.copy(ordinary = true), ordinaryRisk, license, other), ordinaryRisk, month))
    }

    @Test fun `통상임금 and the month's lines see the resolved amounts`() {
        val p = profile(meals.copy(ordinary = true), risk.copy(ordinary = true), license)
        val ordinary = PayCalculator.ordinaryWage(p, month)
        assertEquals(450_000L, ordinary.allowances)
        val result = PayCalculator().calculate(p, month, resolver::resolve, checks = false)
        val lines = result.lines.filter { it.kind == LineKind.ALLOWANCE }.associateBy { it.label }
        assertEquals(200_000L, lines.getValue("식대").amount)
        assertTrue(lines.getValue("식대").formula, lines.getValue("식대").formula.contains("회사 공통"))
        assertEquals(200_000L, lines.getValue("위험예지수당").amount)
        assertTrue(lines.getValue("위험예지수당").formula, lines.getValue("위험예지수당").formula.contains("기본급 2,000,000원 × 10%"))
        assertEquals(50_000L, lines.getValue("자격수당").amount)
    }

    @Test fun `a profile without the new kinds is worked out exactly as before`() {
        val old = profile(license, PayItem("n", "나이트 수당", wage(30_000), Cadence.PER_NIGHT_SHIFT))
        assertSame(old, ItemShares.resolve(old, month))
        // A percent that is not monthly, or a company amount the user typed over, stays the user's.
        val bonusPercent = risk.copy(cadence = Cadence.BONUS)
        assertFalse(bonusPercent.isPercent)
        val typed = meals.copy(amount = wage(180_000))
        assertFalse(typed.isCompanyFixed)
        val p = ItemShares.resolve(profile(typed), month)
        assertEquals(180_000L, p.items.single().amount.on(month).value)
    }

    @Test fun `a company amount or a percent is never asked as 금액 모름`() {
        val unknown = PayItem("u", "자격수당", ValueTimeline(), Cadence.MONTHLY)
        val p = profile(meals, risk, unknown)
        assertNull(Unknowns.describe(Unknowns.item("m"), p, null))
        assertNull(Unknowns.describe(Unknowns.item("r"), p, null))
        assertNotNull(Unknowns.describe(Unknowns.item("u"), p, null))
    }

    @Test fun `the name says who usually sets the amount`() {
        assertEquals(ItemShare.FIXED, ShareGuess.of("식대"))
        assertEquals(ItemShare.FIXED, ShareGuess.of("교대 수당"))
        assertEquals(ItemShare.PERCENT, ShareGuess.of("위험예지수당"))
        assertEquals(ItemShare.PERCENT, ShareGuess.of("기본급2"))
        assertEquals(ItemShare.PERSONAL, ShareGuess.of("자격수당"))
        assertEquals(ItemShare.PERSONAL, ShareGuess.of("고정 연장수당"))
        assertEquals(ItemShare.PERSONAL, ShareGuess.of("가족수당"))
        assertNull(ShareGuess.of("기타"))
        assertNull(ShareGuess.of(" "))
        assertEquals(BonusBasis.ORDINARY, ShareGuess.basisOf("위험예지수당"))
        assertEquals(BonusBasis.BASE, ShareGuess.basisOf("기본급2"))
    }

    @Test fun `percents read short`() {
        assertEquals("10%", ItemShares.pct(0.1))
        assertEquals("2.5%", ItemShares.pct(0.025))
        assertEquals("100%", ItemShares.pct(1.0))
    }
}
