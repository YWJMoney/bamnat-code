package com.bamnat.core.money

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 (DB 36, 갈래 K) [RVK-46]: what the ledger's new columns mean.
 * 검토 MONEY-07 반복 이체 '어디로', I06aa-05 '이 달만' 예산, MONEY-29·T06a-01 일감 받은 기록, T06a-05·I06ab-03 줄 돈.
 */
class MoneyDb36ModelTest {
    private val feb = YearMonth.of(2027, 2)

    @Test
    fun aRepeatingTransferLandsWhereItWasSetToGo() {
        val saving = FixedCost(id = 1, name = "적금", amount = 300_000, day = 25, kind = MoneyKind.TRANSFER, category = "저축", accountId = 1, toAccountId = 2)
        assertEquals(2L, UpcomingPayments.entry(saving, LocalDate.of(2026, 11, 25)).toAccountId)
        // Spending and income never move money into an account, nor does a 이체 into the account it leaves.
        assertNull(UpcomingPayments.entry(saving.copy(kind = MoneyKind.EXPENSE), LocalDate.of(2026, 11, 25)).toAccountId)
        assertNull(UpcomingPayments.entry(saving.copy(toAccountId = 1), LocalDate.of(2026, 11, 25)).toAccountId)
        // Written by itself on its day, it goes there too.
        val written = Recurring.due(listOf(saving.copy(auto = true, start = LocalDate.of(2026, 11, 1))), emptyList(), LocalDate.of(2026, 11, 25))
        assertEquals(listOf(2L), written.map { it.toAccountId })
    }

    @Test
    fun aMonthsOwnBudgetStandsInForEveryMonthsOne() {
        val budgets = listOf(
            MoneyBudget("", 1_500_000), MoneyBudget("식비", 400_000), MoneyBudget("카페", 100_000),
            MoneyBudget("식비", 700_000, feb), MoneyBudget("카페", 0, feb), MoneyBudget("여행", 500_000, feb),
        )
        val inFeb = Budgets.forMonth(budgets, feb).associate { it.category to it.amount }
        assertEquals(mapOf("" to 1_500_000L, "식비" to 700_000L, "카페" to 0L, "여행" to 500_000L), inFeb)
        assertEquals(mapOf("" to 1_500_000L, "식비" to 400_000L, "카페" to 100_000L), Budgets.forMonth(budgets, feb.plusMonths(1)).associate { it.category to it.amount })
        assertEquals("매달 것만", 3, Budgets.everyMonth(budgets).size)

        val lunch = MoneyEntry(id = 1, date = LocalDate.of(2027, 2, 3), kind = MoneyKind.EXPENSE, amount = 450_000, category = "식비")
        val status = Budgets.status(budgets, listOf(lunch), feb, LocalDate.of(2027, 2, 10))
        assertEquals(700_000L, status.categories.single { it.category == "식비" }.budget)
        assertTrue("2월엔 카페 예산이 없음(0)", status.categories.none { it.category == "카페" })
        // In March the same 45만 is over the every-month 40만, and the new line says so.
        val march = lunch.copy(id = 0, date = LocalDate.of(2027, 3, 3))
        assertEquals(listOf("식비"), Budgets.crossedBy(march, budgets, emptyList(), LocalDate.of(2027, 3, 3)).map { it.category }.filter { it.isNotEmpty() })
        assertTrue(Budgets.crossedBy(lunch.copy(id = 0), budgets, emptyList(), LocalDate.of(2027, 2, 3)).none { it.category == "식비" })
    }

    @Test
    fun aPayCycleUsesTheMonthHoldingMostOfItsDays() {
        assertEquals(YearMonth.of(2026, 11), Budgets.monthOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)))
        assertEquals(YearMonth.of(2026, 11), Budgets.monthOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
        assertEquals(YearMonth.of(2026, 10), Budgets.monthOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 11, 9)))
        val budgets = listOf(MoneyBudget("식비", 400_000), MoneyBudget("식비", 600_000, YearMonth.of(2026, 11)))
        val s = Budgets.statusIn(budgets, emptyList(), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 1))
        assertEquals(600_000L, s.categories.single().budget)
    }

    @Test
    fun whatIsCarriedIsLastMonthsOwnBudget() {
        // January had 70만 for 식비 and spent 50만: 20만 comes over to February's every-month 40만.
        val budgets = listOf(MoneyBudget("식비", 400_000), MoneyBudget("식비", 700_000, YearMonth.of(2027, 1)))
        val jan = MoneyEntry(id = 1, date = LocalDate.of(2027, 1, 10), kind = MoneyKind.EXPENSE, amount = 500_000, category = "식비")
        assertEquals(mapOf("식비" to 200_000L), Budgets.carry(budgets, listOf(jan), feb.atDay(1), feb.atEndOfMonth(), setOf("식비")))
    }

    @Test
    fun aJobsPaymentsCountWithItsDepositAndBalance() {
        val job = Job(id = 1, date = LocalDate.of(2026, 11, 2), title = "욕실", amount = 3_000_000, deposit = 500_000, depositPaid = true)
        assertEquals(500_000L, job.received)
        val paid = job.copy(payments = listOf(
            JobPayment(1, 1, LocalDate.of(2026, 11, 9), 1_000_000, "중도금"),
            JobPayment(2, 1, LocalDate.of(2026, 11, 20), 700_000),
        ))
        assertEquals(2_200_000L, paid.received)
        assertEquals(800_000L, paid.unpaid)
        assertEquals(LocalDate.of(2026, 11, 20), paid.lastPaid)
        // Never more than the job pays.
        val over = paid.copy(balancePaid = true)
        assertEquals(3_000_000L, over.received)
        assertEquals(0L, over.unpaid)
        assertTrue(over.fullyPaid)
    }

    @Test
    fun moneyTheUserOwesIsNeverMoneyComingIn() {
        val today = LocalDate.of(2026, 11, 20)
        val supply = Receivable(id = 1, who = "가나다 자재상", title = "타일", amount = 450_000, date = LocalDate.of(2026, 11, 2),
            kind = ReceivableKind.SUPPLY, toPay = true, payments = listOf(ReceivablePayment(1, 1, LocalDate.of(2026, 11, 10), 150_000)))
        val sale = Receivable(id = 2, who = "손님", amount = 50_000, date = LocalDate.of(2026, 11, 3), kind = ReceivableKind.SALE)
        assertEquals(listOf(2L), Receivables.owed(listOf(supply, sale), emptyList(), today).map { (it as Owed.Written).r.id })
        val summary = Receivables.summary(listOf(supply, sale), emptyList(), today)
        assertEquals(50_000L, summary.total)
        assertEquals("갚은 돈은 이번 달 받은 돈이 아님", 0L, summary.receivedThisMonth)
        assertEquals(listOf(1L), Receivables.toPay(listOf(supply, sale), today).map { it.id })

        // Paying it is spending, under what it was for in this kind of work.
        val line = Receivables.incomeEntry(supply, 300_000, today, WorkStyle.FIELD)!!
        assertEquals(MoneyKind.EXPENSE, line.kind)
        assertEquals("자재비", line.category)
        assertEquals("재료비", Receivables.payCategory(ReceivableKind.SUPPLY, WorkStyle.BUSINESS))
        assertEquals("인건비", Receivables.payCategory(ReceivableKind.WAGE, WorkStyle.BUSINESS))
        assertNull("빌린 돈을 갚는 건 지출이 아님", Receivables.incomeEntry(supply.copy(kind = ReceivableKind.BORROWED, countAsIncome = false), 1, today))

        assertEquals(listOf(ReceivableKind.SALE, ReceivableKind.WORK, ReceivableKind.LOAN, ReceivableKind.OTHER), ReceivableKind.of(toPay = false))
        assertEquals(listOf(ReceivableKind.SUPPLY, ReceivableKind.WAGE, ReceivableKind.BORROWED, ReceivableKind.OTHER), ReceivableKind.of(toPay = true))
    }
}
