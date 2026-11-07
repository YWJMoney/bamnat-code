package com.bamnat.core

import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.SlipGlance
import com.bamnat.core.pay.SlipGlance.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 1.39 발굴 3-2: a shared way checked against a payslip by amounts alone. */
class SlipGlanceTest {
    private val month = MonthPay(
        YearMonth.of(2026, 10),
        listOf(
            PayLine(LineKind.BASE, "기본급", 2_500_000, "x"),
            PayLine(LineKind.OVERTIME, "연장", 300_000, "x"),
            PayLine(LineKind.NIGHT, "야간", 150_000, "x"),
            PayLine(LineKind.ALLOWANCE, "식대", 200_000, "x"),
        ),
        ordinaryHourly = 0, ratesVersion = "", minutes = emptyList(), warnings = emptyList(),
    )
    private val won = { v: Long -> "%,d원".format(v) }

    @Test fun aTotalWithinRoundingMatches() {
        val r = SlipGlance.of(month, 3_149_200)
        assertTrue(r.matched)
        assertEquals(800L, r.diff)
        assertEquals("지급 총액이 명세서와 맞아요 (차이 800원)", SlipGlance.line(r, won))
    }

    @Test fun thePartsApartArePickedTheFurthestFirst() {
        val r = SlipGlance.of(month, 3_050_000, mapOf(Part.OVERTIME to 290_000, Part.NIGHT to 75_000, Part.ALLOWANCE to 200_000))
        assertFalse(r.matched)
        assertEquals(listOf(Part.NIGHT, Part.OVERTIME), r.differs)
        assertEquals(listOf("night", "overtime"), r.fixFields)
        assertEquals("명세서보다 100,000원 많아요 · 야간·연장에서 차이가 커요", SlipGlance.line(r, won))
    }

    @Test fun aTotalApartWithNoPartsAsksForTheBaseFirst() {
        val r = SlipGlance.of(month, 3_400_000)
        assertFalse(r.matched)
        assertEquals("명세서보다 250,000원 적어요. 기본급과 내 수당 금액이 명세서와 같은지 먼저 봐 주세요", SlipGlance.line(r, won))
    }

    @Test fun theSameTotalWithAPartApartIsNoMatch() {
        val r = SlipGlance.of(month, 3_150_000, mapOf(Part.HOLIDAY to 50_000))
        assertFalse(r.matched)
        assertEquals(listOf(Part.HOLIDAY), r.differs)
    }
}
