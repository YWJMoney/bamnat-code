package com.bamnat.core.formula

import com.bamnat.core.money.Envelope
import com.bamnat.core.money.EnvelopeMove
import com.bamnat.core.money.Envelopes
import com.bamnat.core.money.FlowKind
import com.bamnat.core.money.MoneyAhead
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.money.PayDay
import java.time.LocalDate

/**
 * 1.37 [LEDH-19] 돈 수식 이름 넓히기: what the 가계부's new cards know, for formulas — 오늘쓸돈·월급까지 (앞으로의 돈), 예상잔액 (the
 * forecast, 90 days from today), 봉투모은돈·봉투진행 (모으기 봉투) and the hourly take-home behind 지출근무시간. Worked out once, the
 * first time a formula reads one of them, because a forecast is a whole calculation.
 */
data class MoneyFacts(
    val perDay: Long?,
    val daysToPay: Int?,
    val balance: Map<LocalDate, Long>,
    val envelopeSaved: Long,
    val envelopeRate: Int?,
    val hourly: Long?,
) {
    companion object {
        /** The forecast's length: the 90 days the 앞으로의 돈 card shows with the pass. */
        const val DAYS = MoneyAhead.FULL_DAYS

        /** What counts as the next money in, by the formula word of the work style (현장 일감 입금, 가게 카드 매출). */
        fun nextKinds(styleWord: String): Set<FlowKind> = when (styleWord) {
            "현장" -> setOf(FlowKind.JOB, FlowKind.OWED)
            "자영업" -> setOf(FlowKind.CARD_SALES, FlowKind.OWED, FlowKind.JOB)
            else -> setOf(FlowKind.PAY)
        }

        fun of(
            book: MoneyBook, today: LocalDate, styleWord: String, pay: List<PayDay>, hourly: Long?,
            envelopes: List<Envelope> = emptyList(), moves: List<EnvelopeMove> = emptyList(),
        ): MoneyFacts {
            val f = MoneyAhead.forecast(book, today, DAYS, pay)
            val spent = book.entries.filter { it.date == today && it.kind == MoneyKind.EXPENSE && it.source != MoneySource.FIXED }.sumOf { it.amount }
            val next = f.untilNext(nextKinds(styleWord), spent)
            val open = Envelopes.status(envelopes, moves, today)
            val target = envelopes.filter { !it.closed && it.target > 0 }.sumOf { it.target }
            val saved = open.sumOf { it.saved }
            return MoneyFacts(
                perDay = next?.perDay,
                daysToPay = next?.daysLeft,
                balance = if (f.hasBalance) f.days.associate { it.date to it.balance } else emptyMap(),
                envelopeSaved = saved,
                envelopeRate = if (target > 0) (saved * 100 / target).toInt().coerceIn(0, 100) else null,
                hourly = hourly,
            )
        }
    }
}
