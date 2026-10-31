package com.bamnat.app.tier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MON-13] 1.36 (사용자 결정): 금액 비교하기·세후 예상 open for a week from their first use, then ask for 프로. */
class FeatureTrialTest {
    private val day = 86_400_000L
    private val locked = Gate(live = true, tier = Tier.FREE)

    @Test fun neverTriedCanStartItsWeek() {
        assertFalse(locked.allows(Feature.TAX_FORECAST))
        assertTrue(locked.canTry(Feature.TAX_FORECAST))
        assertFalse(locked.canTry(Feature.PAY_REPORT))
    }

    @Test fun openForSevenDaysFromTheFirstUse() {
        val t = FeatureTrials(null)
        assertTrue(t.start(Feature.PAYSLIP_MATCH, 0))
        assertFalse(t.start(Feature.PAYSLIP_MATCH, 3 * day))
        val day6 = locked.copy(trials = t.all.value, now = 6 * day)
        assertTrue(day6.allows(Feature.PAYSLIP_MATCH))
        assertEquals(7 * day, day6.trialUntil(Feature.PAYSLIP_MATCH))
        assertFalse(day6.allows(Feature.TAX_FORECAST))
        val day8 = locked.copy(trials = t.all.value, now = 8 * day)
        assertFalse(day8.allows(Feature.PAYSLIP_MATCH))
        assertFalse(day8.canTry(Feature.PAYSLIP_MATCH))
        assertNull(day8.trialUntil(Feature.PAYSLIP_MATCH))
    }

    @Test fun onlyTheTwoFeaturesHaveATrial() {
        assertFalse(FeatureTrials(null).start(Feature.PAY_REPORT, 0))
        assertFalse(Gate().canTry(Feature.TAX_FORECAST)) // before the launch everything is open anyway
    }
}
