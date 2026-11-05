package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-33] 가계부에 묻기. Amounts are made up. */
class LedgerAskTest {
    private val today = LocalDate.of(2026, 11, 10) // a Tuesday
    private fun e(d: LocalDate, amount: Long, place: String) = MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = amount, category = "식비", merchant = place)
    private val book = listOf(
        e(LocalDate.of(2026, 11, 2), 12_000, "김밥"), e(LocalDate.of(2026, 11, 9), 30_000, "마트"), e(LocalDate.of(2026, 11, 10), 5_000, "김밥"),
        e(LocalDate.of(2026, 10, 5), 20_000, "마트"),
    )

    @Test fun answersFromTheBook() {
        assertEquals("이번 달은 ‘마트’에 30,000원 썼어요 (1번).", LedgerAsk.answer(LedgerAsk.Question.TOP_PLACE, book, today))
        assertEquals("지난달 같은 날(10일)까지보다 27,000원 더 썼어요 (47,000원).", LedgerAsk.answer(LedgerAsk.Question.VS_LAST, book, today))
        assertEquals("이번 주(월요일부터)는 35,000원, 2건이에요.", LedgerAsk.answer(LedgerAsk.Question.THIS_WEEK, book, today))
        assertEquals("이번 달 10일 중 7일은 적은 지출이 없어요.", LedgerAsk.answer(LedgerAsk.Question.NO_SPEND, book, today))
    }
}
