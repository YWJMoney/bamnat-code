package com.bamnat.core

import com.bamnat.core.guide.GrowNudges
import com.bamnat.core.guide.GrowState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** AG 1.27 [INVITE-11][INVITE-12] The asks to invite and to tell the developer come seldom and stop when closed. */
class GrowNudgesTest {
    private val installed = LocalDate.of(2026, 10, 1)

    @Test fun theInviteCardWaitsThreeDaysShowsForADayAndComesBackTwoWeeksOn() {
        val s = GrowState()
        assertFalse("not before the first setup", GrowNudges.invite(s, installed, installed.plusDays(5), setupDone = false))
        assertFalse(GrowNudges.invite(s, installed, installed.plusDays(2), setupDone = true))
        val day = installed.plusDays(3)
        assertTrue(GrowNudges.invite(s, installed, day, setupDone = true))
        val shown = s.copy(inviteShownOn = day)
        assertTrue("the whole day it showed", GrowNudges.invite(shown, installed, day, true))
        assertFalse(GrowNudges.invite(shown, installed, day.plusDays(1), true))
        assertFalse(GrowNudges.invite(shown, installed, day.plusDays(13), true))
        assertTrue(GrowNudges.invite(shown, installed, day.plusDays(14), true))
        // Closed: gone at once, two weeks on again.
        val closed = shown.copy(inviteClosedOn = day)
        assertFalse(GrowNudges.invite(closed, installed, day, true))
        assertTrue(GrowNudges.invite(closed, installed, day.plusDays(14), true))
        assertFalse("no install date: not asked", GrowNudges.invite(s, null, day, true))
    }

    @Test fun theSettingsCardComesBackAMonthAfterItWasClosed() {
        val today = LocalDate.of(2026, 10, 26)
        assertTrue(GrowNudges.card(GrowState(), today))
        val closed = GrowState(cardClosedOn = today)
        assertFalse(GrowNudges.card(closed, today.plusDays(29)))
        assertTrue(GrowNudges.card(closed, today.plusDays(30)))
    }

    @Test fun anAppStopAsksOnceWhileItIsRecent() {
        val day = 24L * 60 * 60 * 1000
        val at = 1_800_000_000_000L
        assertFalse("none", GrowNudges.crash(GrowState(), 0, at))
        assertTrue(GrowNudges.crash(GrowState(), at, at + day))
        assertFalse("offered already", GrowNudges.crash(GrowState(crashSeenAt = at), at, at + day))
        assertFalse("a week old", GrowNudges.crash(GrowState(), at, at + 8 * day))
    }

    @Test fun theThirdUndoOfADaySaysTheLineOnceInTwoWeeks() {
        val today = LocalDate.of(2026, 10, 26)
        var s = GrowState()
        s = GrowNudges.undone(s, today)
        s = GrowNudges.undone(s, today)
        assertFalse(GrowNudges.feedbackAfterUndo(s, today))
        s = GrowNudges.undone(s, today)
        assertTrue(GrowNudges.feedbackAfterUndo(s, today))
        s = s.copy(undoToldOn = today)
        assertFalse("told today", GrowNudges.feedbackAfterUndo(GrowNudges.undone(s, today), today))
        // A new day starts the count again.
        var later = s
        repeat(3) { later = GrowNudges.undone(later, today.plusDays(13)) }
        assertFalse(GrowNudges.feedbackAfterUndo(later, today.plusDays(13)))
        repeat(3) { later = GrowNudges.undone(later, today.plusDays(14)) }
        assertTrue(GrowNudges.feedbackAfterUndo(later, today.plusDays(14)))
    }
}
