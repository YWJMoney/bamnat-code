package com.bamnat.core.formula

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.Envelope
import com.bamnat.core.money.EnvelopeMove
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.PayDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-19] 돈 수식 이름: 오늘쓸돈·월급까지·예상잔액·봉투모은돈·봉투진행·지출근무시간. */
class MoneyFactsTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val book = MoneyBook(accounts = listOf(MoneyAccount(id = 1, name = "월급 통장", type = AccountType.BANK, opening = 500_000)))
    private val pay = listOf(PayDay(LocalDate.of(2026, 11, 25), 2_300_000, "11월 급여"))
    private val trip = Envelope(id = 1, name = "여행", target = 1_200_000)

    private fun facts(m: MoneyFacts?) = DayFactsBook(
        today = today, style = "교대", shifts = { null },
        money = { if (it == today) DayFactsBook.DayCash(expense = 65_000) else null },
        moneyFacts = { m },
    )

    private fun read(b: DayFactsBook, name: String, d: LocalDate = today) = Formula.eval(name, b.scope(d))?.number

    @Test fun formulasReadTheForecastAndEnvelopes() {
        val m = MoneyFacts.of(book, today, "교대", pay, hourly = 10_000, envelopes = listOf(trip), moves = listOf(EnvelopeMove(1, 1, today, 300_000)))
        assertEquals(21, m.daysToPay)
        assertEquals(500_000L / 21, m.perDay)
        assertEquals(2_800_000L, m.balance[LocalDate.of(2026, 11, 25)])
        assertEquals(300_000L, m.envelopeSaved)
        assertEquals(25, m.envelopeRate)
        val b = facts(m)
        assertEquals(21.0, read(b, "월급까지"))
        assertEquals(21.0, read(b, "입금까지"))
        assertEquals(25.0, read(b, "봉투진행"))
        assertEquals(6.5, read(b, "지출근무시간"))
        assertEquals(2_800_000.0, read(b, "예상잔액", LocalDate.of(2026, 11, 25)))
    }

    @Test fun withoutBalanceOrPayTheyAreEmpty() {
        val m = MoneyFacts.of(MoneyBook(), today, "현장", emptyList(), hourly = null)
        assertNull(m.perDay)
        assertNull(m.envelopeRate)
        assertEquals(0L, m.envelopeSaved)
        assertEquals(Value.None, facts(null).scope(today).get("오늘쓸돈"))
    }
}
