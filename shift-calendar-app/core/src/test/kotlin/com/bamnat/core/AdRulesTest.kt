package com.bamnat.core

import com.bamnat.core.tier.AdRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** G 1.32 [WTYP-01] The ad promises; 1.36 838 the rewarded ad is the only one. */
class AdRulesTest {
    private val now = Instant.parse("2026-10-28T09:00:00Z")
    private val installed = now.minusSeconds(10 * 86_400L)

    /** 1.36 838: no banner at all; the promise says so in the user's words. */
    @Test fun noBannerAndThePromise() {
        assertTrue(AdRules.MUTED)
        assertTrue(AdRules.PROMISE.contains("고를 때만"))
        assertTrue(AdRules.PROMISE.contains("그냥 열려요"))
        assertTrue(AdRules.PROMISE.contains("소리 없이"))
    }

    @Test fun theDayPassIsOncePerDay() {
        val today = java.time.LocalDate.of(2026, 10, 28)
        assertTrue(AdRules.rewardedOffer(true, false, true, lastDayPass = today.minusDays(1), today = today))
        assertFalse(AdRules.rewardedOffer(true, false, true, lastDayPass = today, today = today))
    }
}
