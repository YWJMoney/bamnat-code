package com.bamnat.app.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.37 PC 4차: 금액 가리기 also hides the amounts in sentences made outside Fmt. */
class FmtHideTest {
    @After fun off() { Fmt.masked = false }

    @Test fun hidesAmountsOnlyWhenMasked() {
        val s = "야간 다음 날은 쉬는 날보다 하루 1,545원 더 써요"
        assertEquals(s, Fmt.hide(s))
        Fmt.masked = true
        assertEquals("야간 다음 날은 쉬는 날보다 하루 •••••원 더 써요", Fmt.hide(s))
        assertEquals("•••••원 · 근무 3번", Fmt.hide("2만원 · 근무 3번"))
        assertEquals("11월 5일 근무 3번", Fmt.hide("11월 5일 근무 3번"))
    }
}
