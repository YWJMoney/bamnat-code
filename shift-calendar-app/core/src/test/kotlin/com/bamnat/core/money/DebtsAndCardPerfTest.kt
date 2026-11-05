package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.38 [LEDH-25] 빚 갚기, [LEDH-27] 카드 실적 게이지. Amounts are made up. */
class DebtsAndCardPerfTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test fun noInterestLoanEndsWhenTheMonthsAddUp() {
        val p = Debts.plan(1_000_000, 0.0, 100_000, YearMonth.of(2026, 11))
        assertEquals(10, p.months)
        assertEquals(0L, p.interest)
        assertEquals(YearMonth.of(2027, 8), p.payoff)
    }

    @Test fun payingMoreEndsSoonerAndCostsLessInterest() {
        val first = YearMonth.of(2026, 11)
        val base = Debts.plan(10_000_000, 5.0, 300_000, first)
        assertEquals(36, base.months)
        assertTrue(base.interest in 700_000L..820_000L)
        val c = Debts.compare(10_000_000, 5.0, 300_000, 100_000, first)!!
        assertEquals(36 - Debts.plan(10_000_000, 5.0, 400_000, first).months!!, c.sooner)
        assertTrue(c.sooner > 0 && c.interestSaved > 0)
    }

    @Test fun aPaymentBelowTheInterestNeverEnds() {
        assertNull(Debts.plan(10_000_000, 12.0, 100_000, YearMonth.of(2026, 11)).months)
        assertNull(Debts.compare(10_000_000, 12.0, 50_000, 10_000, YearMonth.of(2026, 11)))
    }

    @Test fun splitTakesThisMonthsInterestFirst() {
        assertEquals(41_667L to 258_333L, Debts.split(10_000_000, 5.0, 300_000))
        assertEquals(0L to 300_000L, Debts.split(10_000_000, 0.0, 300_000))
    }

    @Test fun aLoanIsOwedInNetWorthAndPaysDownByTransfer() {
        val bank = MoneyAccount(id = 1, name = "월급 통장", type = AccountType.BANK, opening = 2_000_000)
        val loan = MoneyAccount(id = 2, name = "학자금", type = AccountType.LOAN, opening = -5_000_000, rate = 1.7, monthlyPay = 200_000, payDay = 25)
        val pay = MoneyEntry(date = today.minusDays(1), kind = MoneyKind.TRANSFER, amount = 200_000, category = "", accountId = 1, toAccountId = 2)
        val p = NetWorth.on(listOf(bank, loan), listOf(pay), today)
        assertEquals(1_800_000L, p.have)
        assertEquals(4_800_000L, p.owe)
        assertEquals(4_800_000L, Debts.left(loan, MoneyAccounts.balances(listOf(bank, loan), listOf(pay))))
        assertTrue(!MoneyAhead.spendable(loan))
    }

    @Test fun theForecastTakesTheLoanPaymentOnItsDay() {
        val bank = MoneyAccount(id = 1, name = "월급 통장", type = AccountType.BANK, opening = 2_000_000)
        val loan = MoneyAccount(id = 2, name = "학자금", type = AccountType.LOAN, opening = -500_000, monthlyPay = 200_000, payDay = 25)
        val book = MoneyBook(entries = emptyList(), accounts = listOf(bank, loan))
        val (flows, _) = MoneyAhead.flows(book, today, today.plusDays(90), emptyList())
        val loanFlows = flows.filter { it.kind == FlowKind.LOAN }
        assertEquals(listOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 25)), loanFlows.map { it.date })
        assertEquals(listOf(-200_000L, -200_000L, -100_000L), loanFlows.map { it.amount })
    }

    @Test fun cardGaugeCountsThisMonthLeavesSkippedCategoriesOut() {
        val card = MoneyAccount(id = 3, name = "신한 Deep", type = AccountType.CREDIT, payDay = 14, perfTiers = listOf(300_000, 500_000), perfSkip = listOf("세금"))
        fun e(d: LocalDate, amount: Long, cat: String) = MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = amount, category = cat, accountId = 3)
        val entries = listOf(
            e(today, 320_000, "식비"), e(today, 100_000, "세금"), e(today.minusDays(1), -20_000, "식비"),
            e(LocalDate.of(2026, 10, 10), 510_000, "쇼핑"),
        )
        val g = CardPerf.gauge(card, listOf(card), entries, today)!!
        assertEquals(300_000L, g.counted)
        assertEquals(100_000L, g.skipped)
        assertEquals(300_000L, g.reached)
        assertEquals(500_000L, g.next)
        assertEquals(200_000L, g.toNext)
        assertEquals(500_000L, g.lastReached)
    }

    @Test fun tiersReadManAndPlainNumbers() {
        assertEquals(listOf(300_000L, 500_000L, 1_000_000L), CardPerf.parseTiers("50만, 30만 100만원"))
        assertEquals(listOf(400_000L), CardPerf.parseTiers("400000"))
        assertTrue(CardPerf.parseTiers("없음").isEmpty())
    }
}
