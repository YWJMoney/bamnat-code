package com.bamnat.core

import com.bamnat.core.tier.FeatureLease
import com.bamnat.core.tier.FeatureLease.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 1.36 839 묶음 7일권: first week quietly, one ad = 7 days, two ads a day, a missing ad opens it anyway, three no's hide it. */
class FeatureLeaseTest {
    private val now = Instant.parse("2027-03-01T03:00:00Z")
    private val today = LocalDate.of(2027, 3, 1)
    private fun days(n: Long) = Duration.ofDays(n)

    @Test fun eachBundleHasItsFeaturesAndAFeatureOneBundle() {
        assertEquals(Bundle.PAY, Bundle.of("PAYSLIP_MATCH"))
        assertEquals(Bundle.ALARM, Bundle.of("SURE_ALARM"))
        assertEquals(Bundle.MONEY, Bundle.of("MONEY_STATS"))
        assertEquals(Bundle.PRINT, Bundle.of("ROSTER_PDF"))
        // Always-on 프로 features are in no bundle (an ad never opens them).
        for (f in listOf("DEVICE_CALENDAR", "ICS_FEED", "AUTO_BACKUP", "MORE_LINKS", "MORE_CALENDARS", "THEME_WARDROBE")) assertNull(f, Bundle.of(f))
        val all = Bundle.entries.flatMap { it.features }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun theFirstUseOpensSevenDaysOnce() {
        val s = FeatureLease.firstUse(FeatureLease.State(), "TAX_FORECAST", now)!!
        assertTrue(s.open(Bundle.PAY, now))
        assertTrue(s.openFeature("PAYSLIP_MATCH", now.plus(days(6))))
        assertFalse(s.open(Bundle.PAY, now.plus(days(7))))
        assertEquals(7L, s.daysLeft(Bundle.PAY, now))
        assertFalse(s.headsUp(Bundle.PAY, now))
        assertTrue(s.headsUp(Bundle.PAY, now.plus(days(5))))
        // Once only, and not for another bundle.
        assertNull(FeatureLease.firstUse(s, "PAY_REPORT", now.plus(days(8))))
        assertFalse(s.open(Bundle.MONEY, now))
        assertEquals(FeatureLease.Why.FIRST, s.grants.single().why)
    }

    @Test fun anAdOpensSevenDaysTwoADayAndNoAdButtonWhileOpen() {
        var s = FeatureLease.State()
        assertTrue(FeatureLease.canWatch(s, Bundle.ALARM, now, today))
        s = FeatureLease.granted(s, Bundle.ALARM, now, today)
        assertTrue(s.open(Bundle.ALARM, now.plus(days(6))))
        assertFalse("open bundles show no ad", FeatureLease.canWatch(s, Bundle.ALARM, now, today))
        s = FeatureLease.granted(s, Bundle.MONEY, now, today, FeatureLease.Why.NO_AD, "no_fill")
        assertTrue("a missing ad opens it all the same", s.open(Bundle.MONEY, now))
        assertTrue(FeatureLease.capped(s, today))
        assertFalse(FeatureLease.canWatch(s, Bundle.PRINT, now, today))
        assertTrue("a new day, new ads", FeatureLease.canWatch(s, Bundle.PRINT, now.plus(days(1)), today.plusDays(1)))
        assertEquals(14L, FeatureLease.daysGiven(s, now.minus(days(1)), now.plus(days(1))))
    }

    @Test fun threeNoesHideTheAdForSevenDaysThenThirty() {
        var s = FeatureLease.State()
        repeat(2) { s = FeatureLease.declined(s, Bundle.PAY, now) }
        assertTrue(FeatureLease.canWatch(s, Bundle.PAY, now, today))
        s = FeatureLease.declined(s, Bundle.PAY, now)
        assertFalse(FeatureLease.canWatch(s, Bundle.PAY, now.plus(days(6)), today.plusDays(6)))
        assertTrue(FeatureLease.canWatch(s, Bundle.PAY, now.plus(days(7)), today.plusDays(7)))
        // Three more no's after the first rest: 30 days.
        val later = now.plus(days(8))
        repeat(3) { s = FeatureLease.declined(s, Bundle.PAY, later) }
        assertFalse(FeatureLease.canWatch(s, Bundle.PAY, later.plus(days(29)), today.plusDays(37)))
        assertTrue(FeatureLease.canWatch(s, Bundle.PAY, later.plus(days(30)), today.plusDays(38)))
        // Watching resets the count.
        s = FeatureLease.declined(FeatureLease.State(), Bundle.PAY, now)
        s = FeatureLease.granted(s, Bundle.PAY, now, today)
        assertNull(s.declines[Bundle.PAY])
    }

    @Test fun themeAdsShareTheTwoADayWithTheBundles() {
        // One theme ad and one bundle ad: today's two are used, so neither a bundle nor a theme may ask again.
        var s = FeatureLease.themeAd(FeatureLease.State(), today)
        assertFalse(FeatureLease.capped(s, today))
        assertTrue(s.until.isEmpty())
        s = FeatureLease.granted(s, Bundle.PRINT, now, today)
        assertTrue(FeatureLease.capped(s, today))
        assertFalse(FeatureLease.canWatch(s, Bundle.PAY, now, today))
        // Two theme ads alone fill the day too, and the next day starts again.
        val t = FeatureLease.themeAd(FeatureLease.themeAd(FeatureLease.State(), today), today)
        assertTrue(FeatureLease.capped(t, today))
        assertFalse(FeatureLease.capped(t, today.plusDays(1)))
        assertEquals(1, FeatureLease.themeAd(t, today.plusDays(1)).adsToday(today.plusDays(1)))
    }

    @Test fun theSecondAdInAMonthIsCounted() {
        var s = FeatureLease.State()
        s = FeatureLease.granted(s, Bundle.PAY, now, today)
        assertEquals(1, FeatureLease.adsIn30Days(s, Bundle.PAY, now))
        s = FeatureLease.granted(s, Bundle.PAY, now.plus(days(8)), today.plusDays(8))
        assertEquals(2, FeatureLease.adsIn30Days(s, Bundle.PAY, now.plus(days(8))))
        assertNotNull(s.until[Bundle.PAY])
    }
}
