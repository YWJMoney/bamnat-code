package com.bamnat.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.39 [STAT2-02] 진입·머묾·회복: which LIFE_ counts a day's first open adds, and nothing else leaves the phone. */
class LifeMarksTest {
    private val install = 20_000L

    @Test fun theInstallDayIsEntryAndTheFirstThirtyDaysAreCountedOnTheirOwnDays() {
        assertEquals(listOf(UsageEvent.LIFE_NEW), LifeMarks.of(install, null, install))
        assertEquals(listOf(UsageEvent.LIFE_D1), LifeMarks.of(install, install, install + 1))
        assertEquals(emptyList<UsageEvent>(), LifeMarks.of(install, install + 1, install + 2))
        assertEquals(listOf(UsageEvent.LIFE_D7), LifeMarks.of(install, install + 6, install + 7))
        assertEquals(listOf(UsageEvent.LIFE_D30), LifeMarks.of(install, install + 29, install + 30))
    }

    @Test fun comingBackAfterAWeekOrAMonthIsRecovery() {
        assertEquals(listOf(UsageEvent.LIFE_BACK7), LifeMarks.of(install, install + 40, install + 47))
        assertEquals(listOf(UsageEvent.LIFE_BACK30), LifeMarks.of(install, install + 40, install + 70))
        // Back on the 30th day after a month away is both.
        assertEquals(listOf(UsageEvent.LIFE_D30, UsageEvent.LIFE_BACK30), LifeMarks.of(install, install, install + 30))
        assertEquals(emptyList<UsageEvent>(), LifeMarks.of(install, install + 44, install + 50))
    }

    @Test fun aSecondOpenTheSameDayOrAClockGoneBackAddsNothing() {
        assertEquals(emptyList<UsageEvent>(), LifeMarks.of(install, install, install))
        assertEquals(emptyList<UsageEvent>(), LifeMarks.of(install, install + 5, install + 3))
        assertEquals(emptyList<UsageEvent>(), LifeMarks.of(null, null, install))
    }

    @Test fun theyAreUsageEventsTheServerKnows() {
        for (e in listOf(UsageEvent.LIFE_NEW, UsageEvent.LIFE_D1, UsageEvent.LIFE_D7, UsageEvent.LIFE_D30, UsageEvent.LIFE_BACK7, UsageEvent.LIFE_BACK30)) {
            assertEquals(e, UsageEvent.of(e.key))
        }
    }
}
