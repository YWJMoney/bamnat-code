package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.37 묶음 H [LEDH-16] 지난달 돈 이야기. */
class MonthStoryTest {
    private val oct = YearMonth.of(2026, 10)
    private fun d(m: YearMonth, day: Int) = m.atDay(day)

    @Test fun storyTellsWorkMoneyAndOneThingEach() {
        val entries = listOf(
            MoneyEntry(1, d(oct, 25), MoneyKind.INCOME, 2_300_000, MoneyCategories.SALARY),
            MoneyEntry(2, d(oct, 3), MoneyKind.EXPENSE, 300_000, "식비"),
            MoneyEntry(3, d(oct, 5), MoneyKind.EXPENSE, 200_000, "배달"),
            MoneyEntry(4, d(oct.minusMonths(1), 5), MoneyKind.EXPENSE, 450_000, "식비"),
            MoneyEntry(5, d(oct.minusMonths(1), 6), MoneyKind.EXPENSE, 50_000, "배달"),
        )
        val now = MoneyCalculator.month(oct, entries, emptyList(), emptyList(), null)
        val before = MoneyCalculator.month(oct.minusMonths(1), entries, emptyList(), emptyList(), null)
        val moves = listOf(EnvelopeMove(1, 1, d(oct, 26), 230_000), EnvelopeMove(2, 1, d(oct.plusMonths(1), 1), 99))
        val s = MonthStory.of(oct, now, before, moves, workedMinutes = 160 * 60, workDays = 20, nightDays = 6, payExpected = 2_250_000)
        assertEquals(2_300_000L, s.earned)
        assertEquals(500_000L, s.spent)
        assertEquals(1_800_000L, s.kept)
        assertEquals(230_000L, s.saved)
        assertEquals("식비는 지난달보다 150,000원 덜 썼어요", s.wentWell)
        assertEquals("배달은 지난달보다 150,000원 더 썼어요. 이번 달엔 예산을 걸어 볼까요?", s.tryNext)
        val cards = s.cards()
        assertEquals(5, cards.size)
        assertEquals("+1,800,000원 남겼어요", cards[0].big)
        assertTrue(cards[1].lines.contains("야간 6번"))
        assertTrue(cards[1].lines.contains("들어온 돈과 +50,000원 차이"))
    }

    @Test fun withoutWorkTheWorkCardIsLeftOut() {
        val now = MoneyCalculator.month(oct, emptyList(), emptyList(), emptyList(), null)
        val s = MonthStory.of(oct, now, null, emptyList())
        assertEquals(4, s.cards().size)
        assertTrue(MonthStory.due(LocalDate.of(2026, 11, 7)))
        assertFalse(MonthStory.due(LocalDate.of(2026, 11, 8)))
    }
}
