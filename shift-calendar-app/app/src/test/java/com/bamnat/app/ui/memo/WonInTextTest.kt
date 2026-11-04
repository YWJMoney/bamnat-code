package com.bamnat.app.ui.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PC 확인 5c21f3bb: 메모 → 가계부 offers the first '…원' of a memo with no 금액 칸. */
class WonInTextTest {
    @Test fun `plain, with commas, with a space`() {
        assertEquals(4500L, wonInText("편의점 4500원"))
        assertEquals(12000L, wonInText("점심 12,000 원 나눠 냄"))
    }

    @Test fun `만원 and 1·5만원`() {
        assertEquals(30000L, wonInText("회비 3만원"))
        assertEquals(15000L, wonInText("택시 1.5만 원"))
    }

    @Test fun `the first amount, and none without 원`() {
        assertEquals(4500L, wonInText("커피 4500원, 빵 3000원"))
        assertNull(wonInText("오늘 3시에 2번 출구"))
        assertNull(wonInText(""))
    }
}
