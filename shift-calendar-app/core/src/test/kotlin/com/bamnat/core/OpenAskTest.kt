package com.bamnat.core

import com.bamnat.core.guide.OpenAsk
import com.bamnat.core.guide.OpenAsk.Want
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2.0.0 [RVP-22][RVP-37] 검토 UIX-43·UIX-62: the windows that come up when the app opens, as a table (the review's 13b order).
 */
class OpenAskTest {
    private fun want(vararg a: OpenAsk) = a.map { Want(it) }

    @Test fun theDayOfAnUpdateShowsOneWindowNotFour() {
        // 체험 마지막 날 + 입은 테마 기간 끝 + 그 테마 새 판 + 새 소식: only the trial's consent (the law first).
        val all = want(OpenAsk.THEME_UPDATE, OpenAsk.WHATS_NEW, OpenAsk.THEME_EXPIRY, OpenAsk.TRIAL_CONSENT)
        assertEquals(OpenAsk.TRIAL_CONSENT, OpenAsk.pick(all, null, emptySet(), busy = false))
        // Answered: the rest wait for the next visit.
        assertNull(OpenAsk.pick(all - Want(OpenAsk.TRIAL_CONSENT), null, setOf(OpenAsk.TRIAL_CONSENT), busy = false))
        // Next visit: what the update brought.
        assertEquals(OpenAsk.WHATS_NEW, OpenAsk.pick(all - Want(OpenAsk.TRIAL_CONSENT), null, emptySet(), busy = false))
    }

    @Test fun newConsentNeverLiesOverWhatsNew() {
        // 1.36 W4 '새로 생긴 기능이 있어요' came over the update notice; now it goes first and the notice waits.
        assertEquals(OpenAsk.NEW_CONSENT, OpenAsk.pick(want(OpenAsk.WHATS_NEW, OpenAsk.NEW_CONSENT), null, emptySet(), false))
        // Already on screen, the update notice keeps its turn when the consent comes after it.
        assertEquals(OpenAsk.WHATS_NEW, OpenAsk.pick(want(OpenAsk.WHATS_NEW, OpenAsk.NEW_CONSENT), OpenAsk.WHATS_NEW, setOf(OpenAsk.WHATS_NEW), false))
    }

    @Test fun nothingComesOverWritingOrASheet() {
        // '새 버전을 받았어요' over the 돈 적기 sheet (U13b-08): it waits until the sheet is closed.
        assertNull(OpenAsk.pick(want(OpenAsk.UPDATE_READY), null, emptySet(), busy = true))
        assertEquals(OpenAsk.UPDATE_READY, OpenAsk.pick(want(OpenAsk.UPDATE_READY), null, emptySet(), busy = false))
        // A window already up stays up (its own dialog counts as busy).
        assertEquals(OpenAsk.UPDATE_READY, OpenAsk.pick(want(OpenAsk.UPDATE_READY), OpenAsk.UPDATE_READY, setOf(OpenAsk.UPDATE_READY), busy = true))
    }

    @Test fun whatTheUserAskedForComesAfterAnotherButNotOverIt() {
        val asked = listOf(Want(OpenAsk.WHATS_NEW, asked = true))
        assertEquals(OpenAsk.WHATS_NEW, OpenAsk.pick(asked, null, setOf(OpenAsk.TRIAL_CONSENT), false))
        assertEquals(OpenAsk.TRIAL_CONSENT, OpenAsk.pick(asked + Want(OpenAsk.TRIAL_CONSENT), OpenAsk.TRIAL_CONSENT, setOf(OpenAsk.TRIAL_CONSENT), false))
        assertNull(OpenAsk.pick(asked, null, emptySet(), busy = true))
    }

    @Test fun theOrderIsTheLawThenTheUpdateThenTheRest() {
        assertEquals(
            listOf(OpenAsk.NEW_CONSENT, OpenAsk.TRIAL_CONSENT, OpenAsk.WHATS_NEW, OpenAsk.THEME_EXPIRY, OpenAsk.UPDATE_READY,
                OpenAsk.THEME_UPDATE, OpenAsk.TEXT_SIZE, OpenAsk.LAUNCH_THANKS, OpenAsk.REVIEW),
            OpenAsk.entries.toList(),
        )
        // Each one alone shows.
        OpenAsk.entries.forEach { assertEquals(it, OpenAsk.pick(want(it), null, emptySet(), false)) }
        assertNull(OpenAsk.pick(emptyList(), null, emptySet(), false))
    }
}
