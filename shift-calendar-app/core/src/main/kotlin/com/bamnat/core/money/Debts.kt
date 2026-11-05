package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 1.38 [LEDH-25] 빚 갚기 (조사 C3): 대출·빌린 돈 계좌의 남은 원금, 완납 예정, 남은 이자, 그리고 "매달 10만 원 더 갚으면 7달 당김,
 * 이자 38만 원 덜". 계산은 원리금을 매달 같은 돈으로 갚는 단순한 셈(월 이자 = 남은 원금 × 연 이자율 ÷ 12)이라 은행의 실제
 * 상환표(원금균등·거치·변동 금리)와 조금 다를 수 있어요. 화면도 그렇게 말해요.
 */
object Debts {
    /** At most this many months are worked out; past it the loan is "not paid off at this pace". */
    const val MAX_MONTHS = 600
    /** The extra amounts offered to compare, per month. */
    val EXTRAS = listOf(50_000L, 100_000L, 300_000L)

    data class Plan(
        /** Months until the last payment; null when [monthly] never catches up with the interest. */
        val months: Int?,
        val interest: Long,
        val payoff: YearMonth?,
    )

    data class Compare(val extra: Long, val sooner: Int, val interestSaved: Long)

    /** What is still owed on a loan account (its balance below zero), never negative. */
    fun left(loan: MoneyAccount, balances: Map<Long, Long>): Long = (-(balances[loan.id] ?: loan.opening)).coerceAtLeast(0)

    /**
     * Paying [monthly] on [principal] at [ratePct] a year, starting with the payment in [first]: how many months,
     * how much interest in all, and the month of the last payment.
     */
    fun plan(principal: Long, ratePct: Double, monthly: Long, first: YearMonth): Plan {
        if (principal <= 0) return Plan(0, 0, null)
        if (monthly <= 0) return Plan(null, 0, null)
        val r = ratePct.coerceAtLeast(0.0) / 100.0 / 12.0
        var left = principal.toDouble()
        var interest = 0.0
        var n = 0
        while (left > 0.5 && n < MAX_MONTHS) {
            val i = left * r
            if (monthly <= i) return Plan(null, 0, null)
            interest += i
            left = left + i - monthly
            n++
        }
        if (left > 0.5) return Plan(null, 0, null)
        return Plan(n, interest.roundToLong(), first.plusMonths((n - 1).toLong()))
    }

    /** The month of the next payment on [loan] from [today]: this month if its day is still ahead, else next month. */
    fun firstMonth(loan: MoneyAccount, today: LocalDate): YearMonth {
        val ym = YearMonth.from(today)
        val day = (loan.payDay ?: return ym.plusMonths(1)).coerceAtMost(ym.lengthOfMonth())
        return if (today.dayOfMonth <= day) ym else ym.plusMonths(1)
    }

    /** Paying [extra] more a month: how many months sooner and how much interest less. Null when either plan never ends. */
    fun compare(principal: Long, ratePct: Double, monthly: Long, extra: Long, first: YearMonth): Compare? {
        val base = plan(principal, ratePct, monthly, first)
        val more = plan(principal, ratePct, monthly + extra, first)
        val b = base.months ?: return null
        val m = more.months ?: return null
        return Compare(extra, b - m, (base.interest - more.interest).coerceAtLeast(0))
    }

    /**
     * One payment split into its interest (this month's, on what is owed now) and the rest, which lowers what is owed.
     * The interest is spending (분류 '이자·금융'); the principal is a transfer to the loan.
     */
    fun split(owed: Long, ratePct: Double, pay: Long): Pair<Long, Long> {
        val interest = (owed * ratePct.coerceAtLeast(0.0) / 100.0 / 12.0).roundToLong().coerceIn(0, pay)
        return interest to (pay - interest)
    }

    /** The transfer category of the principal part; the interest part is spending under 이자·금융 (사장님 장부는 대출이자) named "<대출> 이자". */
    const val PRINCIPAL = "대출 상환"

    /** This month's payment on [loan] as lines: the interest as spending, the rest as a transfer that lowers what is owed. */
    fun lines(loan: MoneyAccount, owed: Long, pay: Long, day: LocalDate, interestCategory: String = MoneyCategories.INTEREST): List<MoneyEntry> {
        val (interest, principal) = split(owed, loan.rate, pay.coerceAtMost(owed + split(owed, loan.rate, pay).first))
        return listOfNotNull(
            interest.takeIf { it > 0 }?.let { MoneyEntry(date = day, kind = MoneyKind.EXPENSE, amount = it, category = interestCategory,
                merchant = "${loan.name} 이자", accountId = loan.payFromId) },
            principal.takeIf { it > 0 }?.let { MoneyEntry(date = day, kind = MoneyKind.TRANSFER, amount = it, category = PRINCIPAL,
                merchant = loan.name, accountId = loan.payFromId, toAccountId = loan.id) },
        )
    }
}
