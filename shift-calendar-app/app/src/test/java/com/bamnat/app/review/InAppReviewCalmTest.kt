package com.bamnat.app.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 [SPREAD-06] The review sheet waits after an ad, a payment window or an app-stop; the moment stays due. */
class InAppReviewCalmTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test fun anAdOrPaymentHushesItForTenMinutes() {
        assertTrue(InAppReview.calm(now, crashAt = 0L, until = 0L))
        assertFalse(InAppReview.calm(now, crashAt = 0L, until = now + 1))
        assertTrue(InAppReview.calm(now + InAppReview.HUSH_MS, crashAt = 0L, until = now + InAppReview.HUSH_MS))
    }

    @Test fun anAppStopKeepsItOffForThreeDays() {
        assertFalse(InAppReview.calm(now, crashAt = now - day, until = 0L))
        assertFalse(InAppReview.calm(now, crashAt = now - 3 * day, until = 0L))
        assertTrue(InAppReview.calm(now, crashAt = now - 3 * day - 1, until = 0L))
    }
}
