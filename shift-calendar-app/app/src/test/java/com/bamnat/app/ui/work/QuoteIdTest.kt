package com.bamnat.app.ui.work

import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 [RVR-19] (검토 TEST-20 ⑥) A new 견적 never takes an id one already has, even after the phone's clock went back. */
class QuoteIdTest {
    @Test fun theTimeItWasMadeWhenNothingIsNewer() {
        assertEquals(1_000L, newQuoteId(emptyList(), now = 1_000L))
        assertEquals(9_000L, newQuoteId(listOf(5_000L, 7_000L), now = 9_000L))
    }

    @Test fun oneMoreThanTheNewestAfterTheClockWentBack() {
        // The same millisecond again, and an earlier one: neither writes over the 견적 made then.
        assertEquals(7_001L, newQuoteId(listOf(5_000L, 7_000L), now = 7_000L))
        assertEquals(7_001L, newQuoteId(listOf(5_000L, 7_000L), now = 6_000L))
    }
}
