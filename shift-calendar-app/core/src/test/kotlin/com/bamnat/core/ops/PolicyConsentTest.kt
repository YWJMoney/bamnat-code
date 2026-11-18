package com.bamnat.core.ops

import org.junit.Assert.assertEquals
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
        assertFalse(PolicyConsent.holdsLaunch(tester, agreed = false, today = PolicyConsent.LAUNCH_HELD_UNTIL))
    }

    @Test fun aYesOrALaterInstallIsNotHeld() {
        assertFalse(PolicyConsent.holdsLaunch(tester, agreed = true, today = nov11))
        assertFalse(PolicyConsent.asks(later, agreed = false, today = later))
        assertTrue("unknown install day is asked", PolicyConsent.asks(null, agreed = false, today = nov11))
        assertFalse("nothing before 3.1 is posted", PolicyConsent.asks(tester, agreed = false, today = LocalDate.of(2026, 10, 14)))
    }

    /** 1.39.1 [POLC-01]: a phone installed before 3.12 (11-09) is asked 3.12, and one yes covers 3.11 too. */
    @Test fun aPhoneFromBefore312IsAskedAndOneYesCoversAll() {
        // 3.12's dates come from the server (관리자 앱 › 기능 관리 › 일정, saved 11-08); the app's constants say the same days.
        val cal = com.bamnat.core.flags.PolicyCalendar.SEED.merged(listOf(com.bamnat.core.flags.PolicyVersion(PolicyConsent.PEOPLE_VERSION,
            PolicyConsent.PEOPLE_POSTED, PolicyConsent.PEOPLE_EFFECTIVE, PolicyConsent.SHARE_PEOPLE_HELD_UNTIL)))
        val nov8 = LocalDate.of(2026, 11, 8)
        val nov12 = LocalDate.of(2026, 11, 12)
        assertEquals("3.12", PolicyConsent.ASK_VERSION)
        assertEquals(true, cal.holds("3.12", nov8, emptySet(), nov12))
        assertEquals(true, cal.holds("3.11", LocalDate.of(2026, 11, 1), setOf("3.9"), nov12))
        assertEquals(false, cal.holds("3.12", nov8, setOf("3.12"), nov12))
        assertEquals(false, cal.holds("3.11", LocalDate.of(2026, 11, 1), setOf("3.12"), nov12))
        assertEquals("a new phone is not asked", false, cal.holds("3.12", LocalDate.of(2026, 11, 10), emptySet(), nov12))
        assertEquals("without a yes it starts on 12-09", false, cal.holds("3.12", nov8, emptySet(), LocalDate.of(2026, 12, 9)))
        assertEquals(PolicyConsent.ASK_VERSION, PolicyConsent.ASKED_VERSIONS.first())
    }
}
