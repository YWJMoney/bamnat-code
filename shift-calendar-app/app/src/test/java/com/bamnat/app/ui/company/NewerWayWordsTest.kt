package com.bamnat.app.ui.company

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** 2.0.0 [RVN-24] 검토 I10ac-04: a newer version of the way in use is said in what it would change in my month. */
class NewerWayWordsTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun theLineSaysTheMonthsDifference() {
        assertEquals("고친 판이 나왔어요 · 바꾸면 이번 달 +12,300원", PayWayWords.newer(nov to 12_300L, nov))
        assertEquals("고친 판이 나왔어요 · 바꾸면 10월 −4,000원", PayWayWords.newer(YearMonth.of(2026, 10) to -4_000L, nov))
        assertEquals("고친 판이 나왔어요 · 바꿔도 이번 달은 같아요", PayWayWords.newer(nov to 0L, nov))
    }

    @Test fun withoutAWorkedOutMonthItIsTheOldLine() {
        assertEquals(PayWayWords.NEWER, PayWayWords.newer(null, nov))
    }
}
