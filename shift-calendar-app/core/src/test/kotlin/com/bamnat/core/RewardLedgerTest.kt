package com.bamnat.core

import com.bamnat.core.tier.FeatureLease
import com.bamnat.core.tier.FeatureLease.Bundle
import com.bamnat.core.tier.ProOfferTimes
import com.bamnat.core.tier.RewardLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 1.36 840: the reward ledger, the yearly discount said honestly, and the only times an offer card shows. */
class RewardLedgerTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2027, 3, 15)
    private val now = today.atTime(12, 0).atZone(zone).toInstant()
    private val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
    private val nextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant()
    private val yearly = 29_990_000_000L
    private val monthly = 4_990_000_000L

    @Test fun theLedgerAddsTheMonthsDaysAndPricesThemAtTheYearlyPlan() {
        var s = FeatureLease.firstUse(FeatureLease.State(), "PAYSLIP_MATCH", monthStart.plus(Duration.ofDays(1)))!!
        s = FeatureLease.granted(s, Bundle.MONEY, now, today)
        val m = RewardLedger.month(s, monthStart, nextMonth, listOf(RewardLedger.Part("테마", 30)), yearly, 68_400)
        assertEquals("급여 7일 · 돈 통계 7일 · 테마 30일", m.partsLine)
        assertEquals(44L, m.days)
        assertEquals(1, m.ads)
        assertEquals(3_600L, m.wonValue) // 44 × 29,990 ÷ 365 ≈ 3,615 → 3,600
        assertEquals(68_400L, m.found)
        assertNull("no price, no 원 said", RewardLedger.month(s, monthStart, nextMonth, emptyList(), null, 0).wonValue)
        // 1.36 PC P22: a bundle opened two ways says both.
        val both = FeatureLease.granted(s, Bundle.PAY, monthStart.plus(Duration.ofDays(9)), today.withDayOfMonth(10))
        assertEquals("급여 14일(처음 7일 + 광고 7일) · 돈 통계 7일", RewardLedger.month(both, monthStart, nextMonth, emptyList(), yearly, 0).partsLine)
        assertEquals(82L, RewardLedger.perDay(yearly))
        assertEquals(2_499L, RewardLedger.perMonth(yearly))
    }

    @Test fun theDiscountIsAgainstTheMonthlyPlanTimesTwelveOnly() {
        assertEquals(49, RewardLedger.savingsPercent(monthly, yearly)) // 1 − 29,990 ÷ 59,880 = 49.9%
        assertEquals(50, RewardLedger.savingsPercent(5_000_000_000L, 30_000_000_000L))
        assertNull(RewardLedger.savingsPercent(null, yearly))
        assertNull("not cheaper, nothing said", RewardLedger.savingsPercent(1_000_000_000L, 12_000_000_000L))
    }

    private fun due(s: FeatureLease.State, used: Set<LocalDate> = setOf(today), lastBefore: LocalDate? = today.minusDays(1),
                    shown: Set<String> = emptySet(), snoozed: LocalDate? = null, lastShown: LocalDate? = null) =
        ProOfferTimes.due(s, now, today, zone, used, lastBefore, shown, snoozed, lastShown)

    @Test fun theSecondAdForOneBundleInAMonthOffersOnce() {
        var s = FeatureLease.granted(FeatureLease.State(), Bundle.PAY, now.minus(Duration.ofDays(8)), today.minusDays(8))
        assertNull(due(s))
        s = FeatureLease.granted(s, Bundle.PAY, now.minus(Duration.ofHours(1)), today)
        val d = due(s)!!
        assertEquals(ProOfferTimes.Time.SECOND_AD, d.time)
        assertEquals(Bundle.PAY, d.bundle)
        assertNull("once", due(s, shown = setOf(d.key)))
        assertNull("snoozed for 7 days", due(s, snoozed = today.plusDays(1)))
        assertNull("not within 7 days of the last card", due(s, lastShown = today.minusDays(3)))
    }

    @Test fun twoBundlesByAdsInAMonthAndFiveDaysOfSeven() {
        var s = FeatureLease.granted(FeatureLease.State(), Bundle.PAY, monthStart.plus(Duration.ofDays(2)), today.withDayOfMonth(3))
        s = FeatureLease.granted(s, Bundle.ALARM, monthStart.plus(Duration.ofDays(4)), today.withDayOfMonth(5))
        val d = due(s)!!
        assertEquals(ProOfferTimes.Time.TWO_BUNDLES, d.time)
        assertEquals(2, d.ads)
        assertEquals(14L, d.days)
        val week = (0L..4L).map { today.minusDays(it) }.toSet()
        assertEquals(ProOfferTimes.Time.HEAVY_USE, due(FeatureLease.State(), used = week)!!.time)
        assertNull(due(FeatureLease.State(), used = (0L..3L).map { today.minusDays(it) }.toSet()))
    }

    @Test fun backAfterAMonthOpensTheMostUsedBundleNotAnOffer() {
        var s = FeatureLease.granted(FeatureLease.State(), Bundle.ALARM, now.minus(Duration.ofDays(60)), today.minusDays(60))
        s = FeatureLease.granted(s, Bundle.ALARM, now.minus(Duration.ofDays(50)), today.minusDays(50))
        val d = due(s, lastBefore = today.minusDays(31), snoozed = today.plusDays(3))!!
        assertEquals(ProOfferTimes.Time.BACK, d.time)
        assertEquals(Bundle.ALARM, d.bundle)
        val back = FeatureLease.back(s, Bundle.ALARM, now)
        assertTrue(back.open(Bundle.ALARM, now.plus(Duration.ofDays(6))))
        assertEquals("not an ad", s.adsToday(today), back.adsToday(today))
        assertNull(due(back, lastBefore = today.minusDays(31)))
    }
}
