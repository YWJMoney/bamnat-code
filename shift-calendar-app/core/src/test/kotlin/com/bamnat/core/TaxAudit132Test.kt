package com.bamnat.core

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.LedgerSmarts
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.YearEndPreview
import com.bamnat.core.tax.TaxPlus
import com.bamnat.core.tax.TaxSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.32 조사 대조 (계획/1.32-조사-대조표.md): [TAXP-12]..[TAXP-15] and [LEDX-18]'s settled note. Amounts are made up. */
class TaxAudit132Test {
    @Test fun childrenRaiseTheCardCapFrom2026() {
        assertEquals(500_000L, YearEndPreview.childCardCap(60_000_000, 2026, 1))
        assertEquals(1_000_000L, YearEndPreview.childCardCap(60_000_000, 2026, 3))
        assertEquals(500_000L, YearEndPreview.childCardCap(80_000_000, 2026, 3))
        assertEquals(250_000L, YearEndPreview.childCardCap(80_000_000, 2026, 1))
        assertEquals(0L, YearEndPreview.childCardCap(60_000_000, 2025, 2))
        val lots = YearEndPreview.Extras(credit = 40_000_000)
        assertEquals(3_000_000L, YearEndPreview.cardDeduction(40_000_000, lots))
        assertEquals(4_000_000L, YearEndPreview.cardDeduction(40_000_000, lots, 2026, 2))
        // 18세 미만 asked apart wins over the 자녀 수 of 급여 설정.
        assertEquals(1, YearEndPreview.cardChildren(lots.copy(under18 = 1), 3))
        assertEquals(3, YearEndPreview.cardChildren(lots, 3))
        val r = YearEndPreview.estimate(2026, 55_000_000, 2_000_000, 2_200_000, 3_000_000, 11, 2, 2, lots)
        assertTrue(r.tips.any { it.text.contains("자녀 2명") })
    }

    @Test fun theRestOfTheYearIsSaidAsSteps() {
        // Under the 25% 문턱: how far, and what to use before and after it.
        val few = YearEndPreview.estimate(2026, 33_000_000, 1_000_000, 1_100_000, 1_000_000, 10, 1, 0,
            YearEndPreview.Extras(credit = 3_000_000, medical = 300_000))
        assertTrue(few.tips.any { it.text.contains("문턱") && it.text.contains("남았어요") })
        assertTrue(few.tips.any { it.text.contains("의료비") && it.text.contains("3%") })
        assertTrue(few.tips.any { it.text.contains("홈택스") })
        // The tips with won come first.
        assertTrue(few.tips.first().worth >= few.tips.last().worth)
    }

    @Test fun hometaxNumbersThenTheLedgerFromOctober() {
        val card = MoneyAccount(id = 1, name = "신한", type = AccountType.CREDIT, last4 = "1234", payDay = 14)
        val today = LocalDate.of(2026, 11, 15)
        // Oct 1 ~ Nov 15 is 46 days: run on to the 92 days of Oct~Dec, twice.
        val lines = listOf(
            MoneyEntry(date = LocalDate.of(2026, 10, 20), kind = MoneyKind.EXPENSE, amount = 1_000_000, category = "쇼핑", cardLast4 = "1234"),
            MoneyEntry(date = LocalDate.of(2026, 3, 20), kind = MoneyKind.EXPENSE, amount = 9_000_000, category = "쇼핑", cardLast4 = "1234"),
        )
        val typed = YearEndPreview.Extras(htCredit = 10_000_000, htDebit = 2_000_000)
        val x = YearEndPreview.extrasFrom(lines, listOf(card), 2026, today, typed)
        assertEquals(12_000_000L, x.credit)
        assertEquals(2_000_000L, x.debit)
        assertTrue(x.hasHometax)
        // A whole-year amount typed wins over both.
        assertEquals(20_000_000L, YearEndPreview.extrasFrom(lines, listOf(card), 2026, today, typed.copy(credit = 20_000_000)).credit)
        // Kept in the tax settings and read back.
        val s = TaxSettings(yearEnd = typed.copy(under18 = 2), yearEndYear = 2026)
        assertEquals(s, TaxSettings.decode(s.encode()))
        assertEquals(-1, TaxSettings.decode(TaxSettings().encode()).yearEnd.under18)
    }

    @Test fun depositsAreCheckedAgainstTheSetFee() {
        val d = LocalDate.of(2026, 10, 5)
        val sales = listOf(DailySales(d, 100_000, card = 100_000))
        fun dep(won: Long) = MoneyEntry(date = d.plusDays(2), kind = MoneyKind.INCOME, amount = won, category = "판매", merchant = "신한카드 가맹점대금")
        val fair = TaxPlus.cardDeposits(sales, listOf(dep(98_500)), d.plusDays(3), feeBp = 150).single()
        assertEquals(98_500L, fair.expected)
        assertEquals(0L, fair.overFee)
        val short = TaxPlus.cardDeposits(sales, listOf(dep(96_000)), d.plusDays(3), feeBp = 150).single()
        assertEquals(2_500L, short.overFee)
        assertTrue(short.overFee >= TaxPlus.OVER_FEE_NOTE)
        // No fee set: nothing to say beyond the 수수료 itself.
        assertEquals(0L, TaxPlus.cardDeposits(sales, listOf(dep(96_000)), d.plusDays(3)).single().overFee)
    }

    @Test fun cashReceiptsAreKeptWithTheDay() {
        val day = DailySales(LocalDate.of(2026, 10, 5), 200_000, cash = 120_000, cashReceipt = 80_000)
        assertEquals(40_000L, day.cash - day.cashReceipt)
    }

    @Test fun aLineFixedToTheBillStopsAskingToBeFixed() {
        val note = "신한 1234 · " + LedgerSmarts.abroad("USD", 12.3)!!.note
        val settled = LedgerSmarts.settledAbroad(note)
        assertEquals("신한 1234 · USD 12.30 · 청구서 금액", settled)
        assertFalse(settled.contains(LedgerSmarts.ABROAD_MARK))
        assertEquals(null, LedgerSmarts.abroadOf(MoneyEntry(date = LocalDate.MIN, kind = MoneyKind.EXPENSE, amount = 1, category = "", note = settled)))
    }
}
