package com.bamnat.core.memo

import com.bamnat.core.money.FixedCost
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PaymentParser
import com.bamnat.core.money.QuickLine
import com.bamnat.core.money.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 2.0.0 [RVIB-08] (검토 I07ac-04, 대기열 LINK-01·04) 한 줄에 일정·돈·반복: "매달 25일 월세 50만 원", "매주 금요일 레슨비 3만원", "매년 3월 2일
 * 자동차 보험 62만 원", "25일마다 관리비 12만", "매달 말일 적금 30만" read as a memo that comes back on that day and a 고정 지출 (or 고정
 * 수입: "매달 10일 월급 250만") of that amount in 가계부, with one tap. Only a line with both a repeat and an amount with its unit
 * (만·천·원 or a comma) is offered; a plain number is not money.
 */
object MoneyRepeatLine {
    data class Found(
        val name: String,
        val amount: Long,
        val kind: MoneyKind,
        val repeat: Repeat,
        /** The day of the month (31 for 말일), or the day of the week 1 (월)..7 (일). */
        val day: Int,
        /** For [Repeat.YEAR], the month. */
        val month: Int = 1,
        /** The words that said it, to show in the offer. */
        val phrase: String,
    ) {
        /** Stored when it is answered ([MemoAsked]), so the same words are not offered again. */
        val key: String get() = "m:$phrase"

        /** "매달 25일", "매달 말일", "매주 금요일", "매년 3월 2일". */
        val said: String get() = when (repeat) {
            Repeat.MONTH -> if (day >= 31) "매달 말일" else "매달 ${day}일"
            Repeat.WEEK -> "매주 ${"월화수목금토일"[day - 1]}요일"
            Repeat.YEAR -> "매년 ${month}월 ${day}일"
        }

        /** The 고정 지출 (or 수입) it makes, its category guessed from the name ("월세" 주거, "보험" 보험). */
        fun cost(): FixedCost = FixedCost(
            name = name, amount = amount, day = day, kind = kind, repeat = repeat, monthOfYear = month,
            category = when (kind) {
                MoneyKind.INCOME -> "월급".takeIf { name.contains("월급") || name.contains("급여") } ?: "부수입"
                MoneyKind.TRANSFER -> "저축"
                MoneyKind.EXPENSE -> PaymentParser.guessCategory(name).takeIf { it != "기타" } ?: "생활"
            },
        )

        /** The first day it falls on from [today] on. */
        fun firstFrom(today: LocalDate): LocalDate = when (repeat) {
            Repeat.MONTH -> generateSequence(today.withDayOfMonth(1)) { it.plusMonths(1) }
                .map { it.withDayOfMonth(minOf(day, it.lengthOfMonth())) }.first { !it.isBefore(today) }
            Repeat.WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)))
            Repeat.YEAR -> generateSequence(today.year) { it + 1 }.map { y ->
                LocalDate.of(y, month, 1).let { it.withDayOfMonth(minOf(day, it.lengthOfMonth())) }
            }.first { !it.isBefore(today) }
        }

        /** The memo's own repeat ([MemoRepeat]) from [first]. */
        fun memoRepeat(first: LocalDate): String = when (repeat) {
            // 말일 stays 31 (the month's last day), not the first month's 30.
            Repeat.MONTH -> "M$day"
            Repeat.WEEK -> MemoRepeat.code(MemoRepeat.Kind.WEEK, first)
            Repeat.YEAR -> "Y%02d%02d".format(month, day)
        }
    }

    private const val DAYS = "월화수목금토일"
    private val MONTHLY = Regex("(?:매달|매월|달마다|다달이)\\s*(\\d{1,2}\\s*일|말일)|(\\d{1,2})\\s*일\\s*마다")
    private val WEEKLY = Regex("(?:매주|주마다)\\s*([$DAYS])\\s*요일")
    private val YEARLY = Regex("(?:매년|해마다)\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    private val SAVING = Regex("적금|저축|청약")
    private val UNIT = Regex("\\d\\s*(?:만|천|원)|\\d{1,3}(?:,\\d{3})+")

    fun find(text: String): Found? {
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || !UNIT.containsMatchIn(t)) continue
            val (m, repeat) = MONTHLY.find(t)?.let { it to Repeat.MONTH } ?: WEEKLY.find(t)?.let { it to Repeat.WEEK }
                ?: YEARLY.find(t)?.let { it to Repeat.YEAR } ?: continue
            val g = m.groupValues
            val (day, month) = when (repeat) {
                Repeat.MONTH -> (if (g[1] == "말일") 31 else (g[1].filter(Char::isDigit).ifEmpty { g[2] }).toInt()) to 1
                Repeat.WEEK -> DAYS.indexOf(g[1]) + 1 to 1
                Repeat.YEAR -> g[2].toInt() to g[1].toInt()
            }
            if (repeat != Repeat.WEEK && day !in 1..31 || repeat == Repeat.YEAR && month !in 1..12) continue
            val rest = (t.substring(0, m.range.first) + " " + t.substring(m.range.last + 1)).trim()
            val draft = QuickLine.parse(rest, emptyList()) ?: continue
            val amount = draft.amount?.takeIf { it > 0 } ?: continue
            val name = draft.place.trim().trimEnd('은', '는', '을', '를').ifEmpty { if (draft.kind == MoneyKind.INCOME) "고정 수입" else "고정 지출" }
            // 적금·저축 go from one's own account to another: a 이체, not money spent.
            val kind = if (draft.kind == MoneyKind.EXPENSE && SAVING.containsMatchIn(name)) MoneyKind.TRANSFER else draft.kind
            return Found(name.take(30), amount, kind, repeat, day, month, t)
        }
        return null
    }
}

/**
 * 2.0.0 [RVIB-08] The answers kept on a memo ([Memo.reminderAsked]), one a line: a time's ("w:…", [MemoTime.Found.key]) and a money
 * line's ("m:…", [MoneyRepeatLine.Found.key]), the newest of each kind, so answering one does not bring the other back.
 */
object MemoAsked {
    fun with(asked: String?, key: String): String =
        (asked?.lines().orEmpty().filter { it.isNotBlank() && it.startsWith("m:") != key.startsWith("m:") } + key).joinToString("\n")

    fun has(asked: String?, key: String): Boolean = asked?.lines()?.contains(key) == true
}
