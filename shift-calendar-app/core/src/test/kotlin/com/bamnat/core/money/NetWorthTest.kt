package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-26] 순자산. Amounts are made up. */
class NetWorthTest {
    private val bank = MoneyAccount(id = 1, name = "월급통장", type = AccountType.BANK, opening = 1_000_000)
    private val card = MoneyAccount(id = 2, name = "신용", type = AccountType.CREDIT, payFromId = 1)
    private val d = LocalDate.of(2026, 11, 5)

    @Test fun haveLessWhatTheCardStillOwes() {
        val entries = listOf(
            MoneyEntry(date = d.minusDays(40), kind = MoneyKind.EXPENSE, amount = 200_000, category = "식비", accountId = 2),
            MoneyEntry(date = d.minusDays(20), kind = MoneyKind.TRANSFER, amount = 150_000, category = "카드값", accountId = 1, toAccountId = 2),
            MoneyEntry(date = d.minusDays(2), kind = MoneyKind.INCOME, amount = 300_000, category = "급여", accountId = 1),
        )
        val now = NetWorth.on(listOf(bank, card), entries, d)
        assertEquals(1_150_000L, now.have)
        assertEquals(50_000L, now.owe)
        assertEquals(1_100_000L, now.net)
        assertEquals(13, NetWorth.year(listOf(bank, card), entries, d).size)
        assertEquals(emptyList<NetWorth.Point>(), NetWorth.year(listOf(card), entries, d))
    }
}

/** 1.37 [LEDH-23] 내 월급 만들기. */
class SelfSalaryTest {
    @Test fun secondLowestMonthAndMonthsItLasts() {
        val p = SelfSalary.of(listOf(2_100_000, 3_400_000, 1_200_000, 2_800_000, 2_500_000, 3_000_000), List(6) { 2_000_000L }, 5_000_000)!!
        assertEquals(2_100_000L, p.salary)
        assertEquals(5, p.covered)
        assertEquals(2.5, p.lastFor!!, 0.001)
        assertEquals(null, SelfSalary.of(listOf(0, 0, 1_000_000), listOf(0, 0, 500_000), null))
    }

    /** PC 확인 46200ae4: the morning's money is the balance before today's lines; card and no-account spending never left it. */
    @Test fun morningBalanceCountsOnlyWhatLeftTheAccounts() {
        val today = java.time.LocalDate.of(2026, 11, 20)
        val bank = MoneyAccount(id = 1, name = "월급통장", type = AccountType.BANK, opening = 1_000_000)
        val card = MoneyAccount(id = 2, name = "신용", type = AccountType.CREDIT, payFromId = 1)
        val e = listOf(
            MoneyEntry(date = today, kind = MoneyKind.EXPENSE, amount = 10_000, category = "식비", accountId = 2),
            MoneyEntry(date = today, kind = MoneyKind.EXPENSE, amount = 5_000, category = "식비"),
            MoneyEntry(date = today, kind = MoneyKind.EXPENSE, amount = 3_000, category = "카페", accountId = 1),
            MoneyEntry(date = today, kind = MoneyKind.TRANSFER, amount = 50_000, category = "카드값", accountId = 1, toAccountId = 2),
        )
        assertEquals(53_000L, MoneyAhead.spentFromStart(listOf(bank, card), e, today))
        val f = MoneyAhead.forecast(MoneyBook(entries = e, accounts = listOf(bank, card)), today, 30, emptyList())
        assertEquals(947_000L, f.start)
    }
}
