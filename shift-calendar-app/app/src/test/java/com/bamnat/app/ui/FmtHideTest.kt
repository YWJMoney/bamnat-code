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

    /** 1.38 PC 7차: 12,000과 12,500이 모두 '1만'이던 것 — amounts that differ read differently, cut down and never up. */
    @Test fun shortAmountsKeepTheirDecimals() {
        assertEquals(listOf("1.2만", "1.25만", "1만", "1.05만", "12.5만", "99.9만", "125만", "8.5천", "8천", "500", "-1.2만", "1.2억"),
            listOf(12_000L, 12_500, 10_050, 10_500, 125_000, 999_999, 1_250_000, 8_550, 8_000, 500, -12_000, 120_000_000).map(Fmt::wonShort))
        Fmt.masked = true
        assertEquals("•••", Fmt.wonShort(12_500))
    }
}
