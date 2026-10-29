package com.bamnat.core

import com.bamnat.core.money.DailySales
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyCalculator
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.money.Withholding
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.32 [WTYP-13] 조사 대조 W36·C12 (근무유형 조사 10장 건설 "1억 넘는 금액 시험"): a 1억 넘는 일감, 한 달 매출, 3.3%, 수수료 and
 * a year of them add up to the won, with nothing cut to Int on the way.
 */
class BigAmounts132Test {
    private val day = LocalDate.of(2026, 11, 2)
    private val month = YearMonth.from(day)

    @Test fun aJobOverAHundredMillionIsHeldBackAndOwedToTheWon() {
        val j = Job(date = day, title = "골조 공사", client = "A건설", amount = 150_000_000, withholding = true)
        // 3% 소득세 4,500,000 + 지방세 450,000.
        assertEquals(4_950_000L, Withholding.of(150_000_000))
        assertEquals(4_950_000L, j.withheld)
        assertEquals(145_050_000L, j.unpaid)
        val m = MoneyCalculator.month(month, emptyList(), listOf(j, j.copy(id = 2, amount = 2_350_000_000)), emptyList())
        // What is earned is after 3.3%: 2.35억 holds back 77,550,000.
        assertEquals(77_550_000L, Withholding.of(2_350_000_000))
        assertEquals(2_500_000_000L - 4_950_000L - 77_550_000L, m.jobEarned)
        assertEquals(4_950_000L + 77_550_000L, m.jobWithheld)
    }

    @Test fun aMonthOfBigSalesAndSpendingAddsUp() {
        val sales = (1..30).map { DailySales(month.atDay(it), sales = 120_000_000, costs = 30_000_000) }
        val spent = listOf(MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = 9_999_999_999, category = "기타"))
        val m = MoneyCalculator.month(month, spent, emptyList(), sales)
        assertEquals(3_600_000_000L, m.sales)
        assertEquals(2_700_000_000L, m.salesNet)
        assertEquals(9_999_999_999L, m.expenses)
        assertEquals(2_700_000_000L - 9_999_999_999L, m.balance)
    }

    @Test fun dayRatesAndCutsStayExactWhenLarge() {
        // 공수 셋째 자리 × 큰 일당.
        assertEquals(1_031_250L, DayRate.amount(750_000, 1.375))
        assertEquals(DayRate.amount(250_000_000, 2.125), 531_250_000L)
        val cut = PlatformWork.Cut(feePermille = 200, perCall = 1_000)
        assertEquals(30_000_000L + 2_000_000L, cut.of(150_000_000, 2_000))
    }
}
