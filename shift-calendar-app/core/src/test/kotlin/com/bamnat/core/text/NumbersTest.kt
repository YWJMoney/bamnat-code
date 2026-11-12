package com.bamnat.core.text

import com.bamnat.core.invest.InvestFees
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** 2.0.0 [RVK-33] (검토 MONEY-17·55·70·75·78) Numbers that a German, Vietnamese or Arabic phone wrote or read otherwise. */
class NumbersTest {
    private val before = Locale.getDefault()

    @After fun back() = Locale.setDefault(before)

    @Test fun aPriceFieldKeepsThousandsAsThousands() {
        // MONEY-70: '72,500' pasted or typed with the comma key was 72.5.
        assertEquals("72500", Numbers.cleanDecimal("72,500", commaIsDecimal = false))
        assertEquals("72500", Numbers.cleanDecimal("72,500", commaIsDecimal = true))
        assertEquals("1234.5", Numbers.cleanDecimal("1,234.5", commaIsDecimal = false))
        assertEquals("1234.5", Numbers.cleanDecimal("1.234,5", commaIsDecimal = true))
        assertEquals("1234567", Numbers.cleanDecimal("1.234.567", commaIsDecimal = true))
        // A comma typed on a Korean phone is dropped, so the digits after it keep their place.
        assertEquals("72", Numbers.cleanDecimal("72,", commaIsDecimal = false))
        // On a phone that writes decimals with a comma, the comma key is the point.
        assertEquals("0.", Numbers.cleanDecimal("0,", commaIsDecimal = true))
        assertEquals("0.05", Numbers.cleanDecimal("0,05", commaIsDecimal = true))
        assertEquals("0.5", Numbers.cleanDecimal(".5", commaIsDecimal = false))
        assertEquals("1.25", Numbers.cleanDecimal("1.2.5", commaIsDecimal = false))
        // MONEY-78: 'E' and '−' never reach the field now that captures write plain numbers.
        assertEquals("0.00052", Numbers.cleanDecimal(Numbers.plain(5.2E-4), commaIsDecimal = false))
    }

    @Test fun aRateReadsTheCommaAsThePoint() {
        // MONEY-17: '4,5' (a comma-decimal phone) was 0 or 45.
        assertEquals("4.5", Numbers.cleanRate("4,5"))
        assertEquals("12.75", Numbers.cleanRate("12.75"))
    }

    @Test fun plainNeverWritesAnExponent() {
        assertEquals("0.00052", Numbers.plain(5.2E-4))
        assertEquals("12345678.9", Numbers.plain(12_345_678.9))
        assertEquals("15", Numbers.plain(15.0))
        assertEquals("0", Numbers.plain(-0.0))
        assertEquals("0.12", Numbers.plain(0.1234, 2))
    }

    @Test fun whatTheAppWritesIsTheSameOnEveryPhone() {
        for (tag in listOf("de-DE", "vi-VN", "ar-EG", "fr-FR")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertEquals(tag, "1,234,567원", Numbers.won(1_234_567))
            // MONEY-75: the 기본 수수료 sheet opened with '0,05' and its save was off.
            assertEquals(tag, "0.05", InvestFees.rateText(0.05))
        }
    }

    @Test fun aNoteWrittenOnAnyPhoneReadsBack() {
        assertEquals(12.30, Numbers.readWritten("12.30")!!, 1e-9)
        assertEquals(12.30, Numbers.readWritten("12,30")!!, 1e-9)
        assertEquals(1234.56, Numbers.readWritten("1.234,56")!!, 1e-9)
        assertEquals(1234.56, Numbers.readWritten("1,234.56")!!, 1e-9)
        assertEquals(1234.0, Numbers.readWritten("1,234")!!, 1e-9)
    }
}
