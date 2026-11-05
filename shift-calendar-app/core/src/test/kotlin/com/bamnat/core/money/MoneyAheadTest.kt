package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.37 묶음 H [LEDH-01][LEDH-02][LEDH-05][LEDH-06][LEDH-07] 근무가 보이는 가계부. */
class MoneyAheadTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val bank = MoneyAccount(id = 1, name = "월급 통장", type = AccountType.BANK, opening = 500_000)
    private val card = MoneyAccount(id = 2, name = "신용", type = AccountType.CREDIT, payDay = 14, closeDay = 31, payFromId = 1)
    private val savings = MoneyAccount(id = 3, name = "적금", type = AccountType.OTHER, opening = 3_000_000)

    private fun book(entries: List<MoneyEntry> = emptyList(), fixed: List<FixedCost> = emptyList(), jobs: List<Job> = emptyList()) =
        MoneyBook(entries = entries, fixed = fixed, accounts = listOf(bank, card, savings), jobs = jobs)

    @Test fun startCountsOnlySpendableMoney() {
        assertEquals(500_000L, MoneyAhead.startBalance(listOf(bank, card, savings), emptyList()))
        assertNull(MoneyAhead.startBalance(listOf(card, savings), emptyList()))
    }

    @Test fun payFixedAndCardBillMoveTheBalanceOnTheirDays() {
        // October's card spending is billed on 11/14 from the bank.
        val spent = MoneyEntry(10, LocalDate.of(2026, 10, 20), MoneyKind.EXPENSE, 700_000, "식비", accountId = 2)
        val rent = FixedCost(id = 5, name = "월세", amount = 400_000, day = 10)
        val f = MoneyAhead.forecast(book(listOf(spent), listOf(rent)), today, 30,
            pay = listOf(PayDay(LocalDate.of(2026, 11, 25), 2_300_000, "11월 급여")), everyday = 0)
        assertTrue(f.hasBalance)
        assertEquals(500_000L, f.start)
        assertEquals(100_000L, f.on(LocalDate.of(2026, 11, 10))!!.balance)
        assertEquals(-600_000L, f.on(LocalDate.of(2026, 11, 14))!!.balance)
        assertEquals(1_700_000L, f.on(LocalDate.of(2026, 11, 25))!!.balance)
        assertEquals(LocalDate.of(2026, 11, 14), f.firstBelowZero!!.date)
        // The bill leaves 11 days before pay: more than a week, so it is not "카드값이 월급보다 먼저" yet.
        assertTrue(f.billsBeforePay.isEmpty())
        val close = MoneyAhead.forecast(book(listOf(spent)), today, 30, pay = listOf(PayDay(LocalDate.of(2026, 11, 17), 2_300_000, "급여")), everyday = 0)
        assertEquals(1, close.billsBeforePay.size)
    }

    @Test fun everydaySpendingIsLearnedWithoutRepeatingLines() {
        val lines = (1..30).map { MoneyEntry(it.toLong(), today.minusDays(it.toLong()), MoneyKind.EXPENSE, 20_000, "식비") } +
            MoneyEntry(99, today.minusDays(3), MoneyKind.EXPENSE, 900_000, "주거", source = MoneySource.FIXED)
        val (perDay, guessed) = MoneyAhead.everydayPerDay(lines, today)
        assertEquals(20_000L, perDay)
        assertFalse(guessed)
        val f = MoneyAhead.forecast(book(lines), today, 5, pay = emptyList())
        assertEquals(20_000L, f.everydayPerDay)
        assertEquals(500_000L - 5 * 20_000, f.end!!.balance)
    }

    @Test fun payAlreadyWrittenTodayAndRepeatingPayLineAreNotCountedTwice() {
        val salaryFixed = FixedCost(id = 7, name = "월급", amount = 2_000_000, day = 25, kind = MoneyKind.INCOME, category = MoneyCategories.SALARY)
        val got = MoneyEntry(1, today, MoneyKind.INCOME, 2_100_000, MoneyCategories.SALARY)
        val f = MoneyAhead.forecast(book(listOf(got), listOf(salaryFixed)), today, 30,
            pay = listOf(PayDay(today, 2_200_000, "급여"), PayDay(LocalDate.of(2026, 11, 25), 2_200_000, "급여")), everyday = 0)
        val pays = f.flows.filter { it.kind == FlowKind.PAY }
        assertEquals(listOf(LocalDate.of(2026, 11, 25)), pays.map { it.date })
        assertTrue(f.flows.none { it.kind == FlowKind.FIXED_IN })
        assertTrue(f.fixedPayReplaced)
    }

    @Test fun jobsComeInOnTheirPromisedDayAndLateOnesAreApart() {
        val ahead = Job(id = 1, date = today.minusDays(2), title = "욕실", amount = 1_000_000, due = today.plusDays(5))
        val late = Job(id = 2, date = today.minusDays(20), title = "도배", amount = 300_000, due = today.minusDays(3))
        val f = MoneyAhead.forecast(book(jobs = listOf(ahead, late)), today, 10, pay = emptyList(), everyday = 0)
        assertEquals(listOf(FlowKind.JOB), f.flows.map { it.kind })
        assertEquals(1_500_000L, f.on(today.plusDays(5))!!.balance)
        assertEquals(300_000L, f.late)
    }

    @Test fun untilNextPaySpreadsWhatIsLeftAfterBillsOverTheDays() {
        val rent = FixedCost(id = 5, name = "월세", amount = 200_000, day = 10)
        val f = MoneyAhead.forecast(book(fixed = listOf(rent)), today, 30, pay = listOf(PayDay(LocalDate.of(2026, 11, 14), 2_000_000, "급여")), everyday = 0)
        val u = f.untilNext()!!
        assertEquals(10, u.daysLeft)
        assertEquals(200_000L, u.reserved)
        assertEquals(30_000L, u.perDay)
        assertEquals("월급까지 10일", u.words)
        assertNull(MoneyAhead.forecast(MoneyBook(), today, 30, pay = emptyList()).untilNext())
    }

    @Test fun payCycleRunsFromPayDayToTheDayBeforeTheNext() {
        val days = PayCycle.monthlyPayDays(today, 25, PayCycle::weekdayBefore)
        // 2026-10-25 is a Sunday → Friday 10/23; 11/25 is a Wednesday.
        val r = PayCycle.around(today, days)!!
        assertEquals(LocalDate.of(2026, 10, 23), r.from)
        assertEquals(LocalDate.of(2026, 11, 24), r.to)
        assertEquals("10/23~11/24", r.label)
    }

    @Test fun budgetsOverACycleMatchTheMonthWhenTheCycleIsTheMonth() {
        val lines = listOf(
            MoneyEntry(1, LocalDate.of(2026, 10, 26), MoneyKind.EXPENSE, 100_000, "식비"),
            MoneyEntry(2, LocalDate.of(2026, 11, 2), MoneyKind.EXPENSE, 50_000, "식비"),
        )
        val budgets = listOf(MoneyBudget(MoneyBudget.TOTAL, 600_000))
        val month = Budgets.status(budgets, lines, YearMonth.of(2026, 11), today)
        val same = Budgets.statusIn(budgets, lines, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), today)
        assertEquals(month, same)
        assertFalse(month.isCycle)
        val cycle = Budgets.statusIn(budgets, lines, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24), today)
        assertTrue(cycle.isCycle)
        assertEquals(150_000L, cycle.total!!.spent)
        assertEquals(21, cycle.daysLeft)
        assertEquals(YearMonth.of(2026, 11), cycle.month)
    }

    @Test fun workTimeSaysMoneyAsWork() {
        val hourly = WorkTime.hourly(2_400_000, 160 * 60)!!
        assertEquals(15_000L, hourly)
        assertEquals("근무 6시간", WorkTime.words(90_000, hourly))
        assertEquals("근무 40분", WorkTime.words(10_000, hourly))
        assertEquals("근무 1.5시간", WorkTime.words(22_500, hourly))
        assertEquals("근무 20시간", WorkTime.words(300_000, hourly))
        assertNull(WorkTime.hourly(2_000_000, 300))
        assertEquals("공수 0.3", WorkTime.units(60_000, 200_000))
        assertEquals("하루 매출의 12%", WorkTime.salesShare(60_000, 500_000))
        assertTrue(WorkTime.shows(50_000, WorkTime.DEFAULT_FROM))
        assertFalse(WorkTime.shows(49_999, WorkTime.DEFAULT_FROM))
        assertFalse(WorkTime.shows(1_000_000, 0))
        assertNotNull(WorkTime.words(1, hourly))
    }
}
