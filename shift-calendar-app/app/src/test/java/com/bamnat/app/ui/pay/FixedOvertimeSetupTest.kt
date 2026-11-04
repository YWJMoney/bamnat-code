package com.bamnat.app.ui.pay

import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayExamples
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.36 PC 급여 검토 E2·W2: 포괄임금 from the first setup's last page. */
class FixedOvertimeSetupTest {
    private val since = LocalDate.of(2026, 1, 1)
    private val salary = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(since, 3_000_000))))

    @Test fun offLeavesNoFixedOvertime() {
        val on = withFixedOvertime(salary, since, 20, 400_000, false)
        assertEquals(1, on.items.count { it.id == FIXED_OT_ID })
        assertTrue(withFixedOvertime(on, since, null, null, false).items.none { it.id == FIXED_OT_ID })
    }

    @Test fun hoursAndTheContractsAmountGoIn() {
        val p = withFixedOvertime(salary, since, 20, 400_000, false)
        val item = p.items.single { it.id == FIXED_OT_ID }
        assertEquals(20 * 60, item.fixedOvertimeMinutes)
        assertEquals(400_000L, item.amount.on(YearMonth.of(2026, 10)).value)
        assertEquals(3_000_000L, p.base.on(YearMonth.of(2026, 10)).value)
    }

    @Test fun aSalaryInOneSumIsSplit() {
        val p = withFixedOvertime(salary, since, 20, null, true)
        val ot = p.items.single { it.id == PayExamples.INCLUSIVE_ID }
        val base = p.base.on(YearMonth.of(2026, 10)).value
        // 기본급 = 3,000,000 × 209 ÷ (209 + 30) and the rest is 고정 연장수당.
        assertEquals(Math.round(3_000_000.0 * 209 / 239), base)
        assertEquals(3_000_000L, base + ot.amount.on(YearMonth.of(2026, 10)).value)
        assertEquals(1, p.base.entries.size)
    }

    @Test fun hourlyPayHasNoFixedOvertime() {
        val hourly = salary.copy(wageType = WageType.HOURLY)
        assertTrue(withFixedOvertime(hourly, since, 20, null, false).items.isEmpty())
    }
}
