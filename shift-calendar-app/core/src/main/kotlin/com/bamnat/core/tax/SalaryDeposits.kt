package com.bamnat.core.tax

import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * [TAX-01] Is this income line the month's pay? A bank's deposit text rarely says "급여", so the payment reader put
 * most pay deposits under 부수입, and the money tab then added them on top of the pay estimate (사용자 U9-다). A line is
 * pay when it is filed as 급여, when it came from a sender the user confirmed as the employer before, or when it lands
 * within [DAYS] of the pay day for about the take-home the calendar expects ([NEAR]) and is not small change.
 */
object SalaryDeposits {
    const val DAYS = 5L
    const val NEAR = 0.20
    const val MIN = 300_000L

    enum class Why(val words: String) {
        FILED("급여로 분류된 입금이에요"),
        PAYER("전에 월급으로 확인한 곳에서 들어왔어요"),
        LOOKS("지급일 무렵, 예상 실수령과 비슷한 금액이에요"),
    }

    data class Seen(val entry: MoneyEntry, val payMonth: YearMonth, val why: Why)

    /**
     * [entry] as pay, or null. [payMonths] lists the pay months with their pay day and expected 실수령 (either may be
     * unknown); [payers] are the senders (가맹점 칸) the user has confirmed as pay before.
     */
    fun seen(entry: MoneyEntry, payMonths: List<Expected>, payers: Set<String>): Seen? {
        if (entry.kind != MoneyKind.INCOME || entry.amount <= 0) return null
        val month = closest(entry.date, payMonths) ?: Expected(YearMonth.from(entry.date), null, null)
        if (entry.shareOf(MoneyCategories.SALARY) > 0) return Seen(entry, month.payMonth, Why.FILED)
        val payer = key(entry.merchant)
        if (payer.isNotEmpty() && payers.any { key(it) == payer }) return Seen(entry, month.payMonth, Why.PAYER)
        val net = month.net ?: return null
        val day = month.payDay ?: return null
        val near = abs(entry.amount - net).toDouble() / net <= NEAR
        val onTime = abs(entry.date.toEpochDay() - day.toEpochDay()) <= DAYS
        return if (entry.amount >= MIN && near && onTime) Seen(entry, month.payMonth, Why.LOOKS) else null
    }

    /** One pay month's pay day and expected take-home, as far as known. */
    data class Expected(val payMonth: YearMonth, val payDay: LocalDate?, val net: Long?)

    private fun closest(date: LocalDate, months: List<Expected>): Expected? =
        months.filter { it.payDay != null }.minByOrNull { abs(it.payDay!!.toEpochDay() - date.toEpochDay()) }
            ?.takeIf { abs(it.payDay!!.toEpochDay() - date.toEpochDay()) <= 20 }

    /** A sender's name without spaces, brackets and (주) marks, so "(주)밤낮병원" and "밤낮병원" are one. */
    fun key(name: String): String = name.replace(Regex("\\(주\\)|㈜|주식회사|\\s|[()\\[\\]]"), "").lowercase()
}
