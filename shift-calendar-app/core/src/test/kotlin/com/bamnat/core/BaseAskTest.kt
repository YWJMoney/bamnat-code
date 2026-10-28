package com.bamnat.core

import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * [PAYG-02][PAYG-04][PAYG-08] 1.30 묶음 BA (사용자 2026-10-27 "기본급 칸(비우면 다음으로 못 감) — 입력을 안 해도 잘 넘어가고 있음",
 * "통상시급 기준 시간(LG화학 180시간)은 광장 값을 따르고 다르면 고칠 수 있고, 고치면 의견으로"): a company's way goes in only on a
 * base the user gave, the setup's own base is offered as a chip and never typed in for them, and the company's hours stay
 * unless the user says other ones, which are then the ones to send back as an opinion.
 */
class BaseAskTest {
    private val month = YearMonth.of(2026, 10)

    private fun monthly(base: Long, estimated: Set<String> = emptySet(), hours: Int = 209) = PayProfile(
        Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2025, 11, 1), base))),
        monthlyHours = hours, estimatedFields = estimated,
    )

    @Test fun theFieldNeverStartsFilledButTheOwnBaseIsAChip() {
        // Nothing of the user's own: no chip, and nothing to put the way in on.
        assertNull(BaseAsk.keepChip(null, month))
        assertNull(BaseAsk.keepChip(monthly(0), month))
        // Only guessed (대략·추정): not offered as "그대로 쓰기" either.
        assertNull(BaseAsk.keepChip(monthly(2_090_000, setOf(Unknowns.BASE)), month))
        assertEquals(2_090_000L, BaseAsk.keepChip(monthly(2_090_000), month))
        assertFalse(BaseAsk.ready(null))
        assertFalse(BaseAsk.ready(0))
        assertFalse(BaseAsk.ready(-5))
        assertTrue(BaseAsk.ready(1))
    }

    @Test fun companyHoursKept() {
        // The company's way brought 180시간; the 기본급 alone keeps them (hours null).
        val company = monthly(0, hours = 180)
        val kept = BaseAsk.apply(company, 2_400_000, month, hours = null)
        assertEquals(180, kept.monthlyHours)
        assertEquals(2_400_000, kept.base.on(month).value)
        assertTrue(BaseAsk.showsEstimate(kept, month))
        // The user's own hours replace them and count as said.
        val own = BaseAsk.apply(company, 2_400_000, month, hours = 209)
        assertEquals(209, own.monthlyHours)
        assertFalse(RuleOnlyNeeds.HOURS_UNSET in own.estimatedFields)
    }

    @Test fun theCompanyHoursChipComesFirstAndOnce() {
        assertEquals(listOf(180, 209, 226, 243), BaseAsk.hoursChoices(180))
        assertEquals(listOf(209, 226, 243), BaseAsk.hoursChoices(209))
        assertEquals(listOf(209, 226, 243), BaseAsk.hoursChoices(null))
        assertEquals("hours no workplace counts are not offered", listOf(209, 226, 243), BaseAsk.hoursChoices(40))
    }

    @Test fun onlyHoursThatDifferFromTheCompanysAreSentBack() {
        assertEquals(209, BaseAsk.hoursToPropose(209, 180))
        assertNull("the company's own", BaseAsk.hoursToPropose(180, 180))
        assertNull("not said", BaseAsk.hoursToPropose(null, 180))
        assertNull("the company said none", BaseAsk.hoursToPropose(209, null))
        assertNull("a typing slip", BaseAsk.hoursToPropose(2090, 180))
    }

    @Test fun noBaseShowsNoEstimate() {
        // [PAYG-04] 0원 is never a month's pay: the screen asks the base instead.
        assertFalse(BaseAsk.showsEstimate(monthly(0), month))
        assertTrue(BaseAsk.needed(monthly(0), month))
        assertTrue(BaseAsk.showsEstimate(monthly(1), month))
    }

    /** [FIN130-04] The 통상시급 card says truly whether the hours are the 광장's or the user's own. */
    @Test fun hoursSayWhereTheyCameFrom() {
        val company = monthly(0, hours = 180).copy(hoursSource = com.bamnat.core.pay.HourlySource.PLAZA)
        // The company's own hours picked: still the 광장's.
        assertEquals(com.bamnat.core.pay.HourlySource.PLAZA, BaseAsk.apply(company, 2_800_000, month, 180).hoursSource)
        assertEquals(com.bamnat.core.pay.HourlySource.PLAZA, BaseAsk.apply(company, 2_800_000, month, null).hoursSource)
        // Other hours: the user's.
        val typed = BaseAsk.apply(company, 2_800_000, month, 209)
        assertEquals(209, typed.monthlyHours)
        assertEquals(com.bamnat.core.pay.HourlySource.TYPED, typed.hoursSource)
        // A source said before stays when the same hours are picked again.
        val slip = monthly(2_000_000).copy(hoursSource = com.bamnat.core.pay.HourlySource.SLIP)
        assertEquals(com.bamnat.core.pay.HourlySource.SLIP, BaseAsk.apply(slip, 2_800_000, month, 209).hoursSource)
    }
}
