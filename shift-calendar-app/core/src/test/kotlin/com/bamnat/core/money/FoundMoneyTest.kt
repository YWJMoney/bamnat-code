package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-17] 찾아 준 돈 한 판. */
class FoundMoneyTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun refundsTiedThisYearAndSubscriptionsToLookAt() {
        val pay = MoneyEntry(1, LocalDate.of(2026, 3, 12), MoneyKind.EXPENSE, 35_000, "쇼핑", merchant = "가게")
        val back = MoneyEntry(2, LocalDate.of(2026, 3, 14), MoneyKind.EXPENSE, -12_000, "쇼핑", merchant = "가게",
            note = LedgerSmarts.refundNote(12_000, pay))
        val lastYear = back.copy(id = 3, date = LocalDate.of(2025, 12, 1))
        val untied = MoneyEntry(4, LocalDate.of(2026, 5, 1), MoneyKind.EXPENSE, -5_000, "기타")
        val subs = (8..10).map { m -> MoneyEntry(10L + m, LocalDate.of(2026, m, 20), MoneyKind.EXPENSE, 9_900L + (m - 8) * 1_000, "구독", merchant = "영상구독") }
        val f = FoundMoney.of(listOf(pay, back, lastYear, untied) + subs, today)
        assertEquals(1, f.refunds)
        assertEquals(12_000L, f.refunded)
        assertEquals(listOf("영상구독"), f.staleSubs.map { it.place })
        assertEquals(11_900L * 12, f.staleYearly)
        assertEquals(2_000L, f.raisedSubs.single().raised)
    }

    @Test fun nothingFoundIsEmpty() {
        assertTrue(FoundMoney.of(emptyList(), today).isEmpty)
    }
}
