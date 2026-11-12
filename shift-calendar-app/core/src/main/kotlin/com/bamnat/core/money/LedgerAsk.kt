package com.bamnat.core.money

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 1.37 [LEDH-33] 가계부에 묻기: questions as chips, answered on the phone from the book itself (no server, no AI), in one sentence.
 * Only spending lines count, repeating ones included, transfers never.
 */
object LedgerAsk {
    enum class Question(val label: String) {
        TOP_PLACE("어디에 제일 많이 썼어?"),
        VS_LAST("지난달보다 더 썼어?"),
        THIS_WEEK("이번 주 얼마 썼어?"),
        BIGGEST("이번 달 가장 큰 지출은?"),
        WEEKEND("주말에 더 써?"),
        NO_SPEND("돈 안 쓴 날은 며칠?"),
    }

    fun answer(q: Question, entries: List<MoneyEntry>, today: LocalDate): String {
        val month = YearMonth.from(today)
        val spend = entries.filter { it.kind == MoneyKind.EXPENSE && it.amount > 0 && !it.date.isAfter(today) }
        val now = spend.filter { YearMonth.from(it.date) == month }
        return when (q) {
            Question.TOP_PLACE -> now.groupBy { it.merchant.ifBlank { it.category }.trim() }.mapValues { (_, l) -> l.sumOf { it.amount } }
                .maxByOrNull { it.value }?.let { (k, v) -> "이번 달은 ‘$k’에 ${won(v)} 썼어요 (${now.count { it.merchant.ifBlank { it.category }.trim() == k }}번)." }
                ?: "이번 달에 적은 지출이 아직 없어요."
            Question.VS_LAST -> {
                val upTo = today.dayOfMonth
                val last = month.minusMonths(1)
                val before = spend.filter { YearMonth.from(it.date) == last && it.date.dayOfMonth <= upTo }.sumOf { it.amount }
                val a = now.sumOf { it.amount }
                when {
                    before == 0L -> "지난달 같은 날까지 적은 지출이 없어 견줄 수 없어요. 이번 달은 ${won(a)}."
                    a > before -> "지난달 같은 날(${upTo}일)까지보다 ${won(a - before)} 더 썼어요 (${won(a)})."
                    a < before -> "지난달 같은 날(${upTo}일)까지보다 ${won(before - a)} 덜 썼어요 (${won(a)})."
                    else -> "지난달 같은 날까지와 똑같이 ${won(a)} 썼어요."
                }
            }
            Question.THIS_WEEK -> {
                val from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = spend.filter { !it.date.isBefore(from) }
                "이번 주(월요일부터)는 ${won(week.sumOf { it.amount })}, ${week.size}건이에요."
            }
            Question.BIGGEST -> now.maxByOrNull { it.amount }?.let { "${it.date.monthValue}월 ${it.date.dayOfMonth}일 ‘${it.merchant.ifBlank { it.category }}’ ${won(it.amount)}이에요." }
                ?: "이번 달에 적은 지출이 아직 없어요."
            Question.WEEKEND -> {
                val from = today.minusDays(55)
                val recent = spend.filter { !it.date.isBefore(from) }
                if (recent.isEmpty()) "최근 8주에 적은 지출이 없어요." else {
                    fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                    val days = (0L..55L).map { today.minusDays(it) }
                    val we = days.count(::weekend).coerceAtLeast(1)
                    val wd = (days.size - we).coerceAtLeast(1)
                    val a = recent.filter { weekend(it.date) }.sumOf { it.amount } / we
                    val b = recent.filter { !weekend(it.date) }.sumOf { it.amount } / wd
                    "최근 8주 하루 평균: 주말 ${won(a)}, 평일 ${won(b)}." + if (a > b) " 주말에 더 써요." else if (a < b) " 평일에 더 써요." else ""
                }
            }
            Question.NO_SPEND -> {
                val spentDays = now.map { it.date }.toSet()
                val days = today.dayOfMonth
                "이번 달 ${days}일 중 ${days - spentDays.size}일은 적은 지출이 없어요."
            }
        }
    }

    private fun won(v: Long) = "%,d원".format(java.util.Locale.ROOT, v)
}
