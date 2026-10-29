package com.bamnat.core

import com.bamnat.core.tier.AdRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** G 1.32 [WTYP-01] The ad promises: quiet places, quiet days, not right after opening, not while typing, always closable. */
class AdRulesTest {
    private val now = Instant.parse("2026-10-28T09:00:00Z")
    private val installed = now.minusSeconds(10 * 86_400L)

    @Test fun onlyUnderPayAndMoneyAndNeverForPro() {
        AdRules.Place.entries.forEach { p ->
            val shown = AdRules.banner(p, live = true, pro = false, configured = true, installedAt = installed, now = now)
            assertTrue(p.name, shown == (p == AdRules.Place.MONEY || p == AdRules.Place.STATS))
        }
        assertFalse(AdRules.banner(AdRules.Place.MONEY, true, pro = true, configured = true, installedAt = installed, now = now))
        assertFalse(AdRules.banner(AdRules.Place.MONEY, live = false, pro = false, configured = true, installedAt = installed, now = now))
        assertFalse(AdRules.banner(AdRules.Place.MONEY, true, false, true, installedAt = now.minusSeconds(86_400), now = now))
    }

    @Test fun notRightAfterOpeningNorWhileTypingNorOnceClosed() {
        assertFalse(AdRules.bannerNow(true, openedAt = now.minusSeconds(5), now = now, typing = false, closed = false))
        assertTrue(AdRules.bannerNow(true, openedAt = now.minusSeconds(AdRules.LAUNCH_QUIET_SECONDS), now = now, typing = false, closed = false))
        assertFalse(AdRules.bannerNow(true, openedAt = null, now = now, typing = true, closed = false))
        assertFalse(AdRules.bannerNow(true, openedAt = null, now = now, typing = false, closed = true))
        assertFalse(AdRules.bannerNow(false, openedAt = null, now = now, typing = false, closed = false))
        assertTrue(AdRules.MUTED)
        assertTrue(AdRules.PROMISE.contains("소리 없이"))
    }

    @Test fun theDayPassIsOncePerDay() {
        val today = java.time.LocalDate.of(2026, 10, 28)
        assertTrue(AdRules.rewardedOffer(true, false, true, lastDayPass = today.minusDays(1), today = today))
        assertFalse(AdRules.rewardedOffer(true, false, true, lastDayPass = today, today = today))
    }
}
