package com.bamnat.core.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [SETUP-22] A setup put off counts as done once its calendar has an order or the leader's team. */
class SetupLaterDoneTest {
    @Test fun aPatternOrATeamFinishesASetupPutOff() {
        assertTrue(SetupLaterDone.done(later = true, styleChosen = true, hasPattern = true, hasTeam = false))
        assertTrue(SetupLaterDone.done(later = true, styleChosen = true, hasPattern = false, hasTeam = true))
        assertFalse("nothing made yet", SetupLaterDone.done(later = true, styleChosen = true, hasPattern = false, hasTeam = false))
        assertFalse("no kind of work chosen yet", SetupLaterDone.done(later = true, styleChosen = false, hasPattern = true, hasTeam = true))
        assertFalse("a setup going on now is not ended", SetupLaterDone.done(later = false, styleChosen = true, hasPattern = true, hasTeam = true))
    }
}
