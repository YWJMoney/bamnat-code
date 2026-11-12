package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.BusinessTax.Profile
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** 갈래 K 9차 [RVK-44]: 총괄이 PC 3차에서 넘긴 줄(PC-134·PC-136). */
class RvkNineTest {
    private val today = LocalDate.of(2026, 11, 12)

    /** PC-136: a phone of 공수 days alone (no 3.3%) showed '사업자 세금 예상 · 부가가치세 84,004원'. 일용근로 has no 부가세 (부가가치세법 제12조 제3항). */
    @Test fun dayLabourCarriesNoBusinessTax() {
        val days = (1..20).map { Job(date = today.minusDays(it.toLong()), title = "형틀", amount = 180_000, units = 1.0) }
        val y = BusinessTax.year(2026, today, Profile(), days, emptyList(), emptyList())
        assertEquals(0, y.vat)
        assertEquals(0, y.sales)
        assertEquals(3_600_000, y.dayWages)
        assertTrue(y.dayLabourOnly)
        assertEquals(0, y.total)
    }

    /** PC-136: beside 건별 work, only the 건별 is 매출; a 공수 day paid with 3.3% is 인적용역 (부가세 면세) and still counts for 종합소득세. */
    @Test fun onlyBusinessWorkIsSales() {
        val jobs = listOf(
            Job(date = today.minusDays(3), title = "형틀", amount = 200_000, units = 1.0),
            Job(date = today.minusDays(4), title = "형틀", amount = 200_000, units = 1.0, withholding = true),
            Job(date = today.minusDays(5), title = "욕실 리모델링", amount = 3_000_000),
        )
        val y = BusinessTax.year(2026, LocalDate.of(2026, 12, 31), Profile(), jobs, emptyList(), emptyList())
        assertEquals(3_200_000, y.sales)
        assertEquals(200_000, y.dayWages)
        assertFalse(y.dayLabourOnly)
        // 일반과세: 부가세 is the 건별 job's alone (3,000,000 × 10/110), the 3.3% 공수 day being 면세.
        val shopOnly = BusinessTax.year(2026, LocalDate.of(2026, 12, 31), Profile(), listOf(jobs[2]), emptyList(), emptyList())
        assertEquals(shopOnly.vat, y.vat)
        assertTrue(BusinessTax.isDayLabour(jobs[0]))
        assertFalse(BusinessTax.isDayLabour(jobs[1]))
        assertFalse(BusinessTax.isDayLabour(jobs[2]))
        // A shop day beside 공수 still carries its 부가세.
        val shop = BusinessTax.year(2026, LocalDate.of(2026, 12, 31), Profile(), listOf(jobs[0]), listOf(DailySales(LocalDate.of(2026, 5, 1), 11_000_000, 0)), emptyList())
        assertEquals(1_000_000, shop.vat)
    }

    /** PC-134: 기능 찾기 › 일용직 세금 › 바로 가기 comes down to the card, not the top of the tab. */
    @Test fun dailyTaxGuideOpensTheCard() {
        assertEquals("dailyTax", FeatureRegistry.get("field.dailyTax")!!.route.focus)
    }
}
