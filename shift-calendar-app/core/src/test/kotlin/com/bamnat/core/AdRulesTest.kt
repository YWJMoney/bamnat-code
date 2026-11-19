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

    /** 2.0.0 [RVM-60] RCP-14: the quiet days are exactly when ads would run but the SDK may not start. */
    @Test fun theQuietDaysAreWhenNoAdCanCome() {
        val fresh = now.minusSeconds(3600)
        assertTrue(AdRules.quiet(true, false, fresh, now))
        assertFalse(AdRules.sdkMayStart(true, false, fresh, now))
        assertFalse("프로는 광고가 없어 '쉬는 날'도 아니에요", AdRules.quiet(true, true, fresh, now))
        assertFalse("광고 스위치가 꺼지면 아니에요", AdRules.quiet(false, false, fresh, now))
        assertFalse(AdRules.quiet(true, false, installed, now))
        assertTrue(AdRules.sdkMayStart(true, false, installed, now))
        assertFalse("깐 때를 모르면 쉬지 않아요", AdRules.quiet(true, false, null, now))
        assertFalse("서버가 0일로 두면 쉬지 않아요", AdRules.quiet(true, false, fresh, now, quietDays = 0))
    }
}
