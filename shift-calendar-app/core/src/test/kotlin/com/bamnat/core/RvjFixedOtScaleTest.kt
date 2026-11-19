package com.bamnat.core

import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayExamples
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayScale
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * 2.0.0 [RVJ-76] 검토 PAYU-49·T05b-02·I05ba-02: 고정 연장 시간 off the whole hour (계약서의 22.5시간) is paid and split as it says.
 * 2.0.0 [RVJ-77] 검토 I05ba-01: '모든 호봉 n% 올리기' raises the whole table, 원 단위에서 반올림, blank rows left blank.
 */
class RvjFixedOtScaleTest {
    private val from = LocalDate.of(2026, 11, 1)
    private val salary = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 12_000L * 209))))

    @Test
    fun `a fixed overtime of twenty two and a half hours is paid for twenty two and a half`() {
        val p = PayExamples.apply(PayExamples.Kind.FIXED_OVERTIME, salary, from, overtimeMinutes = 1_350)
        val item = p.items.single()
        assertEquals(1_350, item.fixedOvertimeMinutes)
        // 통상시급 12,000원 × 1.5 × 22.5시간 = 405,000원
        assertEquals(405_000L, item.amount.on(from).value)
    }

    @Test
    fun `the whole hours still read as before`() {
        val hours = PayExamples.apply(PayExamples.Kind.FIXED_OVERTIME, salary, from, overtimeHours = 20)
        val minutes = PayExamples.apply(PayExamples.Kind.FIXED_OVERTIME, salary, from, overtimeMinutes = 1_200)
        assertEquals(hours, minutes)
        assertEquals(PayExamples.inclusiveSplit(salary, from, 20), PayExamples.inclusiveSplitMinutes(salary, from, 1_200))
    }

    @Test
    fun `a salary in one sum splits on twenty two and a half hours`() {
        val total = 12_000L * 209
        val p = PayExamples.apply(PayExamples.Kind.INCLUSIVE_TOTAL, salary, from, overtimeMinutes = 1_350)
        val base = p.base.on(from).value
        // 기본급 = 총액 × 209 ÷ (209 + 1.5 × 22.5)
        assertEquals((total * 209.0 / (209 + 1.5 * 22.5)).roundToLong(), base)
        val ot = p.items.single { it.id == PayExamples.INCLUSIVE_ID }
        assertEquals(total - base, ot.amount.on(from).value)
        assertEquals(1_350, ot.fixedOvertimeMinutes)
    }

    @Test
    fun `a payslip line with its hours brings the hours along`() {
        // I05ba-02: '고정연장수당 22.5시간 300,000' fills the 직접 칸 the wizard shows for that line.
        val line = PayslipReader.read("기본급 2,500,000\n고정연장수당 22.5시간 300,000\n지급계 2,800,000").lines
            .single { it.name.contains("고정연장") }
        assertEquals(SlipRole.ALLOWANCE, line.role)
        assertEquals(1_350, line.minutes)
        assertEquals(300_000L, line.amount)
    }

    @Test
    fun `every step goes up by the percent and rounds to the won`() {
        val raised = PayScale.raised(listOf(2_400_000L, 2_480_015L, null, 0L, 2_640_000L), 3.5)
        assertEquals(listOf(2_484_000L, 2_566_816L, null, 0L, 2_732_400L), raised)
        // 2,480,015 × 1.035 = 2,566,815.525 → 2,566,816
    }

    @Test
    fun `a raise of nothing changes nothing`() {
        val rows = listOf(2_400_000L, 2_480_000L)
        assertEquals(rows, PayScale.raised(rows, 0.0))
        assertNull(PayScale.raised(listOf<Long?>(null), 5.0).single())
    }

    @Test
    fun `a table kept from last year goes on from the step it is at`() {
        val last = PayScale(mapOf(1 to 2_400_000L, 2 to 2_480_000L, 3 to 2_560_000L), stepMonth = 3, step = 1, since = LocalDate.of(2025, 11, 1))
        // 2026-03-01 is 2호봉, so a table opened again in 2026-11 starts from 2호봉.
        assertEquals(2, last.stepOn(from))
    }
}
