package com.bamnat.app.tier

import com.bamnat.core.tier.FeatureLease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * [MON-13] 1.36 (사용자 결정) and 839 묶음 7일권: a bundle's first use opens the whole bundle for a week, once; an ad (or an ad that
 * did not come) opens it for 7 more days; always-on 프로 features have no bundle and no week.
 */
class FeatureTrialTest {
    private val day = 86_400_000L
    private val locked = Gate(live = true, tier = Tier.FREE)

    @Test fun neverTriedCanStartItsWeek() {
        assertFalse(locked.allows(Feature.TAX_FORECAST))
        assertTrue(locked.canTry(Feature.TAX_FORECAST))
        assertTrue(locked.canTry(Feature.PAY_REPORT))
        assertTrue(locked.canTry(Feature.SURE_ALARM))
        assertFalse("always-on 프로 has no week", locked.canTry(Feature.DEVICE_CALENDAR))
    }

    @Test fun theFirstUseOpensItsWholeBundleForSevenDays() {
        val t = FeatureTrials(null)
        assertTrue(t.start(Feature.PAYSLIP_MATCH, 0))
        assertFalse(t.start(Feature.TAX_FORECAST, 3 * day))
        val day6 = locked.copy(leases = t.all.value, now = 6 * day)
        assertTrue(day6.allows(Feature.PAYSLIP_MATCH))
        assertTrue("the same bundle", day6.allows(Feature.TAX_FORECAST))
        assertEquals(7 * day, day6.trialUntil(Feature.PAYSLIP_MATCH))
        assertFalse("another bundle", day6.allows(Feature.MONEY_STATS))
        val day8 = locked.copy(leases = t.all.value, now = 8 * day)
        assertFalse(day8.allows(Feature.PAYSLIP_MATCH))
        assertFalse(day8.canTry(Feature.PAYSLIP_MATCH))
        assertNull(day8.trialUntil(Feature.PAYSLIP_MATCH))
    }

    @Test fun anAdOrAMissingAdOpensSevenDaysAndIsKept() {
        val t = FeatureTrials(null)
        val now = Instant.ofEpochMilli(10 * day)
        t.grant(FeatureLease.Bundle.ALARM, now, LocalDate.of(1970, 1, 11), FeatureLease.Why.NO_AD, "no_fill")
        val g = locked.copy(leases = t.all.value, now = 16 * day)
        assertTrue(g.allows(Feature.SURE_ALARM))
        assertFalse(locked.copy(leases = t.all.value, now = 17 * day + 1).allows(Feature.SURE_ALARM))
    }

    @Test fun beforeTheLaunchNothingIsTried() {
        assertFalse(Gate().canTry(Feature.TAX_FORECAST)) // before the launch everything is open anyway
    }
}
