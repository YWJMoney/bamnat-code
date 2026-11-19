package com.bamnat.core

import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Payslip
import com.bamnat.core.pay.SlipGlance.Part
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WayPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** [PAYP-18] 1.40 공유급여 4-1: one payslip picks the company's way that fits it best, nothing of the ways shown. */
class WayPickTest {
    private val oct = YearMonth.of(2026, 10)
    private val won = { v: Long -> "%,d원".format(v) }

    private fun month(base: Long, night: Long, extra: Long = 0) = MonthPay(
        oct,
        listOf(PayLine(LineKind.BASE, "기본급", base, "x"), PayLine(LineKind.NIGHT, "야간", night, "x"), PayLine(LineKind.ALLOWANCE, "교대", extra, "x")),
        ordinaryHourly = 0, ratesVersion = "", minutes = emptyList(), warnings = emptyList(),
    )

    private val slip = WayPick.Slip(oct, gross = 3_003_200, base = 2_500_000, parts = mapOf(Part.NIGHT to 303_200))

    @Test fun theClosestVersionComesFirstAndSaysHowClose() {
        val tried = listOf(
            WayPick.tried("a", month(2_500_000, 250_000, 200_000), slip),
            WayPick.tried("b", month(2_500_000, 300_000, 200_000), slip),
            WayPick.tried("c", month(2_500_000, 400_000, 200_000), slip),
        )
        val ranked = WayPick.rank(tried)
        assertEquals(listOf("b", "a", "c"), ranked.map { it.id })
        assertTrue(ranked.first().glance.matched)
        assertEquals("이 방식이 10월 명세서와 3,200원 차이로 가장 맞아요", WayPick.line(ranked.first(), 3, slip, won))
    }

    @Test fun aMatchStaysAboveACloserTotalThatMissesAPart() {
        // "x" comes to the payslip's total exactly, but its 야간 is far off (other lines make up for it): no fit.
        val x = WayPick.tried("x", MonthPay(oct, listOf(PayLine(LineKind.BASE, "기본급", 2_500_000, "x"), PayLine(LineKind.NIGHT, "야간", 103_200, "x"),
            PayLine(LineKind.ALLOWANCE, "교대", 400_000, "x")), 0, "", emptyList(), emptyList()), slip)
        val y = WayPick.tried("y", month(2_500_000, 302_000, 199_000), slip)
        assertFalse(x.glance.matched)
        assertEquals(listOf("y", "x"), WayPick.rank(listOf(x, y)).map { it.id })
    }

    @Test fun noneCloseIsNotCalledAFit() {
        val t = WayPick.tried("a", month(1_800_000, 100_000), slip)
        assertTrue(WayPick.far(t, slip))
        assertEquals("10월 명세서와 1,103,200원 차이가 나서 맞는 방식이 없어요. 기본급과 내 수당이 명세서와 같은지 먼저 봐 주세요",
            WayPick.line(t, 3, slip, won))
    }

    @Test fun oneVersionSaysHowCloseItIs() {
        val t = WayPick.tried("a", month(2_500_000, 303_200, 200_000), slip)
        assertEquals("이 방식이 10월 명세서와 차이 없이 맞아요", WayPick.line(t, 1, slip, won))
        val off = WayPick.tried("a", month(2_500_000, 380_000, 200_000), slip)
        assertEquals("10월 명세서와 76,800원 차이가 나요", WayPick.line(off, 1, slip, won))
    }

    @Test fun thePayslipFillsTheBaseAndThePersonalAllowancesByName() {
        val p = PayProfile(
            Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(oct.atDay(1), 2_000_000))),
            items = listOf(
                PayItem("i1", "자격 수당", ValueTimeline(), Cadence.MONTHLY),
                PayItem("i2", "식대", ValueTimeline(listOf(DatedValue(oct.atDay(1), 200_000))), Cadence.MONTHLY),
                PayItem("i3", "직책수당", ValueTimeline(), Cadence.MONTHLY),
            ),
            estimatedFields = setOf(Unknowns.item("i1"), Unknowns.item("i3")),
        )
        val s = WayPick.Slip.of(oct, Payslip(gross = 3_000_000, base = 2_508_000, nightPay = 90_000, allowances = mapOf("자격수당" to 50_000, "식대" to 150_000)))
        assertEquals(mapOf(Part.NIGHT to 90_000L), s.parts)
        val f = WayPick.filled(p, s)
        assertEquals(2_508_000L, f.base.on(oct).value)
        assertEquals(50_000L, f.items.first { it.id == "i1" }.amount.on(oct).value)
        // An amount the user (or the way) already has stays; one the payslip does not name stays 금액 모름.
        assertEquals(200_000L, f.items.first { it.id == "i2" }.amount.on(oct).value)
        assertTrue(f.items.first { it.id == "i3" }.amount.entries.isEmpty())
        assertEquals(setOf(Unknowns.item("i3")), f.estimatedFields.filter { it.startsWith(Unknowns.item("")) }.toSet())
    }
}
