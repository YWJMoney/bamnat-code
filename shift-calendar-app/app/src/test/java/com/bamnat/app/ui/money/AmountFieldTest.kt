package com.bamnat.app.ui.money

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** [LEDG-01] Typed digits show with separators and the cursor lands where the user typed. */
class AmountFieldTest {
    @Test
    fun separatorsAndCursorMapping() {
        val t = ThousandsTransformation.filter(AnnotatedString("1234567"))
        assertEquals("1,234,567", t.text.text)
        val m = t.offsetMapping
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(1, m.originalToTransformed(1))
        assertEquals(3, m.originalToTransformed(2))
        assertEquals(9, m.originalToTransformed(7))
        for (i in 0..7) assertEquals(i, m.transformedToOriginal(m.originalToTransformed(i)))
        assertEquals("", ThousandsTransformation.filter(AnnotatedString("")).text.text)
        assertEquals("999", ThousandsTransformation.filter(AnnotatedString("999")).text.text)
        assertEquals(1_234L, "1234".toAmount())
        assertEquals(0L, "".toAmount())
    }

    /** [SHOP-03] 센 현금 0원 is an answer; elsewhere a lone 0 empties the field as before. */
    @Test
    fun zeroIsKeptOnlyWhereItIsAnAnswer() {
        assertEquals("", amountInput("0"))
        assertEquals("0", amountInput("0", allowZero = true))
        assertEquals("0", amountInput("000", allowZero = true))
        assertEquals("", amountInput("", allowZero = true))
        assertEquals("1200", amountInput("01,200", allowZero = true))
    }

    /** 1.38 PC 8차: 시급 1,200,010,320원 is told at once; real wages are not. */
    @Test
    fun oddWagesAreTold() {
        val min = 10_320L
        assertEquals(null, com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.HOUR, 12_000, min))
        assertEquals(null, com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.HOUR, 103_200, min))
        assert(com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.HOUR, 1_200_010_320, min)!!.startsWith("시급 1,200,010,320원이 맞나요"))
        assertEquals(null, com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.DAY, 150_000, min))
        assertEquals(null, com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.MONTH, 3_000_000, min))
        assert(com.bamnat.app.ui.work.oddWage(com.bamnat.core.boss.WageForm.MONTH, 300_000_000, min) != null)
    }
}
