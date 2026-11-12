package com.bamnat.core.money

import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.37 [LEDH-16] 지난달 돈 이야기: once at the start of a month, last month's work and money as one story of a few
 * cards — the hours worked and what they earned (pay expected against what came in), what was spent and kept, the
 * envelopes, one thing that went well and one to try next. Work comes from the pay engine (the app passes it in);
 * everything else from the ledger. Nothing leaves the phone.
 */
data class MonthStory(
    val month: YearMonth,
    val workedMinutes: Int?,
    val workDays: Int?,
    val nightDays: Int?,
    /** What the pay engine expected for the month's work (take-home), and what came in as income. */
    val payExpected: Long?,
    val earned: Long,
    val spent: Long,
    val kept: Long,
    /** Money put into envelopes during the month (less what was taken out). */
    val saved: Long,
    val wentWell: String?,
    val tryNext: String?,
) {
    data class Card(val title: String, val big: String, val lines: List<String>)

    /** The story as cards, the first one alone for free (결정 5: 전체 5장은 7일권). */
    fun cards(): List<Card> {
        val m = "${month.monthValue}월"
        val out = mutableListOf<Card>()
        out += Card("$m 한눈에", "${won(kept, signed = true)} 남겼어요", listOfNotNull(
            "들어온 돈 ${won(earned)} · 나간 돈 ${won(spent)}",
            workedMinutes?.takeIf { it > 0 }?.let { "일한 시간 ${it / 60}시간" + (workDays?.let { d -> " (${d}일)" } ?: "") },
        ))
        if (workedMinutes != null && workedMinutes > 0) out += Card("$m 일한 것", "${workedMinutes / 60}시간", listOfNotNull(
            workDays?.let { "근무한 날 ${it}일" },
            nightDays?.takeIf { it > 0 }?.let { "야간 ${it}번" },
            payExpected?.let { "급여 예상(실수령) ${won(it)}" },
            payExpected?.takeIf { it > 0 && earned > 0 }?.let { "들어온 돈과 ${won(earned - it, signed = true)} 차이" },
        ))
        out += Card("$m 쓴 돈", won(spent), listOfNotNull(
            if (earned > 0) "들어온 돈의 ${(spent * 100 / earned).coerceAtLeast(0)}%를 썼어요" else null,
            workedMinutes?.takeIf { it > 0 && earned > 0 }?.let { mins -> "쓴 돈은 근무 약 ${(spent * mins / earned / 60)}시간어치예요" },
        ))
        out += Card("$m 모은 돈", won(saved), listOf(if (saved > 0) "봉투에 넣은 돈이에요" else "다음 달엔 월급이 들어올 때 봉투에 나눠 보세요"))
        out += Card("다음 달 한 가지", tryNext ?: "이대로 좋아요", listOfNotNull(wentWell?.let { "잘한 것: $it" }))
        return out
    }

    companion object {
        /** Shown in the first [DAYS] days of a month. */
        const val DAYS = 7

        fun due(today: LocalDate): Boolean = today.dayOfMonth <= DAYS

        /** "식비는", "배달은": the topic particle after a Korean word (a word not ending in Hangul takes 는). */
        private fun eun(word: String): String {
            val last = word.lastOrNull() ?: return word
            val final = if (last in '가'..'힣') (last - '가') % 28 else 0
            return word + if (final != 0) "은" else "는"
        }

        private fun won(v: Long, signed: Boolean = false): String {
            val s = "%,d원".format(java.util.Locale.ROOT, kotlin.math.abs(v))
            return when { !signed -> if (v < 0) "-$s" else s; v > 0 -> "+$s"; v < 0 -> "-$s"; else -> s }
        }

        /**
         * The story of [month] from the ledger, with [before] (the month before it) to find what went well and what to
         * try; work figures are null when the calendar has no pay engine.
         */
        fun of(
            month: YearMonth, now: MonthMoney, before: MonthMoney?, moves: List<EnvelopeMove>,
            workedMinutes: Int? = null, workDays: Int? = null, nightDays: Int? = null, payExpected: Long? = null,
        ): MonthStory {
            val earned = now.income
            val spent = now.spent
            val saved = moves.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
            val prev = before?.expenseByCategory?.associate { it.category to it.amount }.orEmpty()
            val changes = now.expenseByCategory.map { it.category to (it.amount - (prev[it.category] ?: 0)) } +
                prev.keys.filter { c -> now.expenseByCategory.none { it.category == c } }.map { it to -(prev[it] ?: 0) }
            // Only changes worth a sentence: 1만 원 and more.
            val down = changes.filter { it.second <= -10_000 }.minByOrNull { it.second }
            val up = changes.filter { it.second >= 10_000 }.maxByOrNull { it.second }
            return MonthStory(
                month, workedMinutes, workDays, nightDays, payExpected, earned, spent, earned - spent, saved,
                wentWell = down?.let { (c, d) -> "${eun(c)} 지난달보다 ${won(-d)} 덜 썼어요" },
                tryNext = up?.let { (c, d) -> "${eun(c)} 지난달보다 ${won(d)} 더 썼어요. 이번 달엔 예산을 걸어 볼까요?" },
            )
        }
    }
}
