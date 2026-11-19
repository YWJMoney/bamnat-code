package com.bamnat.core

import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MissingItem
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipGlance.Part
import com.bamnat.core.pay.SlipShape
import com.bamnat.core.pay.WayPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 2.0.0 [RVN-24] 검토 I10ac-01 명세서 한 장으로 회사와 판을 저절로 맞추기: 수당 이름·가산 꼴로 "이 판이 가장 가까워요(항목 n/m)". */
class SlipShapeTest {
    private val oct = YearMonth.of(2026, 10)
    private val won = { v: Long -> "%,d원".format(v) }

    /** A version worked out: 기본급, 야간 at [nightHour] an hour for 40 hours, 연장 for 20 hours, and its allowances. */
    private fun month(nightHour: Long, vararg allowances: Pair<String, Long>, missing: List<String> = emptyList()) = MonthPay(
        oct,
        listOf(
            PayLine(LineKind.BASE, "기본급", 2_500_000, "x"),
            PayLine(LineKind.NIGHT, "야간근로 가산", nightHour * 40, "x", minutes = 40 * 60),
            PayLine(LineKind.OVERTIME, "연장근로", 18_000L * 20, "x", minutes = 20 * 60),
        ) + allowances.map { PayLine(LineKind.ALLOWANCE, it.first, it.second, "x") },
        ordinaryHourly = 12_000, ratesVersion = "", minutes = emptyList(), warnings = emptyList(),
        missing = missing.mapIndexed { i, n -> MissingItem("m$i", n, null, guess = 0) },
    )

    /** The payslip as read from its photo: 야간 6,000 an hour for 40 hours, 연장 for 20, 식대 and 교대수당. */
    private val slip = WayPick.Slip(
        oct, gross = 3_420_000, base = 2_500_000,
        parts = mapOf(Part.NIGHT to 240_000, Part.OVERTIME to 360_000),
        allowances = mapOf("식대" to 200_000, "교대수당" to 120_000),
        minutes = mapOf(Part.NIGHT to 40 * 60, Part.OVERTIME to 20 * 60),
        whole = true,
    )

    @Test fun `the same allowances and 가산 make every item alike`() {
        val fit = SlipShape.of(slip, month(6_000, "식대" to 200_000, "교대 근무 수당" to 120_000))
        assertEquals("항목 4/4", fit.count())
        assertTrue(fit.whole)
        val t = WayPick.tried("a", month(6_000, "식대" to 200_000, "교대 근무 수당" to 120_000), slip)
        assertTrue(WayPick.sure(t, slip))
        assertEquals("이 방식이 10월 명세서와 차이 없이 맞아요 (항목 4/4)", WayPick.line(t, 1, slip, won))
        assertNull(WayPick.differ(t, slip))
    }

    @Test fun `another 배율 for 야간 and an allowance the payslip lacks are what differ`() {
        // 야간 7,200 an hour (60% of 12,000) against the payslip's 6,000 (50%); and 직책수당 the payslip does not pay.
        val fit = SlipShape.of(slip, month(7_200, "식대" to 200_000, "교대수당" to 120_000, "직책수당" to 50_000))
        assertEquals("항목 3/5", fit.count())
        assertEquals(listOf("야간", "직책수당"), fit.differ)
        val t = WayPick.tried("b", month(7_200, "식대" to 200_000, "교대수당" to 120_000, "직책수당" to 50_000), slip)
        assertFalse(WayPick.sure(t, slip))
        assertEquals("명세서와 다른 곳: 야간·직책수당", WayPick.differ(t, slip))
    }

    @Test fun `an allowance left to each person counts as the version's by its name`() {
        val fit = SlipShape.of(slip, month(6_000, "식대" to 200_000, missing = listOf("교대 수당")))
        assertEquals("항목 4/4", fit.count())
    }

    @Test fun `the version with the payslip's shape comes first when no total matches`() {
        // Neither comes to the payslip's total (the calendar had fewer nights); "same" pays as the payslip does, "near" is closer
        // in money only because it pays an allowance the payslip does not have.
        val same = WayPick.tried("same", month(6_000, "식대" to 200_000, "교대수당" to 120_000).let { m ->
            m.copy(lines = m.lines.map { if (it.kind == LineKind.NIGHT) it.copy(amount = 120_000, minutes = 20 * 60) else it })
        }, slip)
        val near = WayPick.tried("near", month(6_000, "식대" to 200_000, "직책수당" to 250_000).let { m ->
            m.copy(lines = m.lines.map { if (it.kind == LineKind.NIGHT) it.copy(amount = 120_000, minutes = 20 * 60) else it })
        }, slip)
        assertFalse(same.glance.matched)
        assertTrue(near.gap < same.gap)
        assertEquals(listOf("same", "near"), WayPick.rank(listOf(near, same)).map { it.id })
    }

    @Test fun `two numbers typed by hand say nothing of what else the payslip pays`() {
        val typed = WayPick.Slip(oct, gross = 3_420_000, base = 2_500_000)
        val t = WayPick.tried("a", month(6_000, "식대" to 200_000), typed)
        assertEquals("", WayPick.items(t, typed))
        assertFalse(WayPick.sure(t, typed))
        assertNull(WayPick.differ(t, typed))
    }

    @Test fun `the company a payslip names is read from its top lines`() {
        assertEquals("밤낮산업", PayslipReader.companyOf("2026년 10월 급여명세서\n회사명 : 밤낮산업    성명 : 홍길동\n기본급 2,500,000"))
        assertEquals("밤낮산업", PayslipReader.companyOf("(주)밤낮산업 2026년 10월 급여명세서\n기본급 2,500,000"))
        assertEquals("한빛정밀", PayslipReader.companyOf("한빛정밀 주식회사\n2026년 10월분 임금명세서"))
        assertEquals("한빛정밀", PayslipReader.companyOf("한빛정밀 주식회사 평택공장\n2026년 10월분 임금명세서"))
        assertEquals("새벽물류", PayslipReader.companyOf("새벽물류 2026년 10월 급여명세서\n성명 홍길동"))
        // A person, a title alone, or only pay lines name no company.
        assertNull(PayslipReader.companyOf("홍길동님 급여명세서\n기본급 2,500,000"))
        assertNull(PayslipReader.companyOf("2026년 10월 급여명세서\n기본급 2,500,000\n식대 200,000"))
        assertEquals("밤낮산업", PayslipReader.read("회사명: 밤낮산업\n기본급 2,500,000\n지급 합계 2,500,000").company)
    }
}
