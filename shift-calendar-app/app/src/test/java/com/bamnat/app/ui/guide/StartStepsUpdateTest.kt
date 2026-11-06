package com.bamnat.app.ui.guide

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 PC 4a5c3828: the first week's 시작 단계 is for a new phone, not for a calendar that came by an update. */
class StartStepsUpdateTest {
    private val day = LocalDate.of(2026, 11, 6)

    @Test fun aFreshInstallSeesTheSteps() = assertFalse(StartStepsState.cameByUpdate(day, null))

    @Test fun aCalendarSetUpBeforeTheUpdateDoesNot() {
        assertTrue(StartStepsState.cameByUpdate(day.minusDays(3), day))
        // Set up before 1.36 kept the day: an update is enough.
        assertTrue(StartStepsState.cameByUpdate(null, day))
    }

    @Test fun aSetupOnOrAfterTheUpdateDayIsNew() {
        assertFalse(StartStepsState.cameByUpdate(day, day))
        assertFalse(StartStepsState.cameByUpdate(day.plusDays(1), day))
    }
}
