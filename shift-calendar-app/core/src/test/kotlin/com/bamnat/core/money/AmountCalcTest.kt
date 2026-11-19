package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVK-15] (검토 U06a-01) 금액 칸 계산. */
class AmountCalcTest {
    @Test fun countsLikeACalculator() {
        assertEquals(12_000L, AmountCalc.eval("36000÷3"))
        assertEquals(10_900L, AmountCalc.eval("4500+3200×2"))
        assertEquals(3_333L, AmountCalc.eval("10000÷3"))
        assertEquals(6_667L, AmountCalc.eval("20000÷3"))
        assertEquals(8_000L, AmountCalc.eval("12000−4000"))
        assertEquals(45_000L, AmountCalc.eval("15000×3"))
        assertEquals(36_000L, AmountCalc.eval("36000÷"))
        assertEquals(5_000L, AmountCalc.eval("5000"))
    }

    @Test fun keyboardSignsMeanTheSame() {
        assertEquals("36000÷3", AmountCalc.clean("36000/3"))
        assertEquals("4500×2", AmountCalc.clean("4500*2"))
        assertEquals("4500×2", AmountCalc.clean("4500x2"))
        assertEquals("9000−1000", AmountCalc.clean("9000-1000"))
        assertEquals(12_000L, AmountCalc.eval("36000/3"))
    }

    @Test fun keepsOnlyWhatMakesASum() {
        // No operator first (it is dropped), a second operator in a row takes the first one's place, no leading zeros, no letters.
        assertEquals("3", AmountCalc.clean("÷3"))
        assertEquals("36000÷", AmountCalc.clean("36000+÷"))
        assertEquals("5+3", AmountCalc.clean("05+03"))
        assertEquals("100+20", AmountCalc.clean("1,00 원+2a0"))
        assertTrue(AmountCalc.clean("1".repeat(80)).length <= AmountCalc.MAX_LENGTH)
        assertTrue(AmountCalc.hasOp("36000÷3"))
        assertTrue(AmountCalc.hasOp("36000÷"))
        assertFalse(AmountCalc.hasOp("36000"))
    }

    @Test fun nothingToSaveIsNull() {
        assertNull(AmountCalc.eval(""))
        assertNull(AmountCalc.eval("5000÷0"))
        assertNull(AmountCalc.eval("3000−5000"))
        assertNull(AmountCalc.eval("3000−3000"))
        assertNull(AmountCalc.eval("99999999999×10"))
    }
}
