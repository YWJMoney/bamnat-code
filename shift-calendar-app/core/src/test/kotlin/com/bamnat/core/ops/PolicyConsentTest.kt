package com.bamnat.core.ops

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [PRIV-03] 결정 D84: testers are asked once; without a yes the launch switch waits until the date 3.0 promised. */
class PolicyConsentTest {
    private val tester = LocalDate.of(2026, 10, 21)
    private val later = LocalDate.of(2026, 11, 12)
    private val nov11 = LocalDate.of(2026, 11, 11)

    @Test fun aTesterIsAskedAndTheSwitchWaitsUntilTheOldDate() {
        assertTrue(PolicyConsent.asks(tester, agreed = false, today = nov11))
        assertTrue(PolicyConsent.holdsLaunch(tester, agreed = false, today = LocalDate.of(2026, 11, 23)))
        assertFalse(PolicyConsent.holdsLaunch(tester, agreed = false, today = PolicyConsent.HELD_UNTIL))
    }

    @Test fun aYesOrALaterInstallIsNotHeld() {
        assertFalse(PolicyConsent.holdsLaunch(tester, agreed = true, today = nov11))
        assertFalse(PolicyConsent.asks(later, agreed = false, today = later))
        assertTrue("unknown install day is asked", PolicyConsent.asks(null, agreed = false, today = nov11))
        assertFalse("nothing before 3.1 is posted", PolicyConsent.asks(tester, agreed = false, today = LocalDate.of(2026, 10, 14)))
    }
}
