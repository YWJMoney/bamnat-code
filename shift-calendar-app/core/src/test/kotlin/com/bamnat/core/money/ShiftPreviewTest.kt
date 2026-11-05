package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.38 [LEDH-24] 근무 전 한 줄 예고. Amounts are made up. */
class ShiftPreviewTest {
    private val today = LocalDate.of(2026, 11, 5)
    // Every third day is 야간, tomorrow too.
    private val labelOf: (LocalDate) -> String? = { d -> if (d.toEpochDay() % 3 == today.plusDays(1).toEpochDay() % 3) "야간" else null }
    private fun e(d: LocalDate, amount: Long, cat: String) = MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = amount, category = cat)
    private val past = (1L..60L).map { today.minusDays(it) }
    private val nightSpend = past.filter { labelOf(it) == "야간" }.map { e(it, 18_000, "배달") } + past.filter { labelOf(it) == null }.take(5).map { e(it, 9_000, "배달") }

    @Test fun aNightThatSpendsOnDeliveryIsToldTheEveningBefore() {
        val (at, tip) = ShiftPreview.next(today.atTime(9, 0), 20, null, nightSpend, labelOf)!!
        assertEquals(today.atTime(20, 0), at)
        assertEquals("배달", tip.category)
        assertEquals(today.plusDays(1), tip.day)
        val (title, text) = ShiftPreview.words(tip) { "${it}원" }
        assertEquals("내일 야간이에요", title)
        assert(text.startsWith("지난 야간 날엔 배달에 평균"))
    }

    @Test fun onceAWeekAtMost() {
        assertNull(ShiftPreview.next(today.atTime(9, 0), 20, today.minusDays(2), nightSpend, labelOf)?.takeIf { it.first.toLocalDate() < today.plusDays(5) })
    }

    @Test fun nothingStandsOutNothingIsSaid() {
        val even = past.map { e(it, 10_000, "식비") }
        assertNull(ShiftPreview.next(today.atTime(9, 0), 20, null, even, labelOf))
    }

    @Test fun pastTheHourItWaitsForTheNextNight() {
        val r = ShiftPreview.next(LocalDateTime.of(2026, 11, 5, 21, 0), 20, null, nightSpend, labelOf)!!
        assertEquals(today.plusDays(4), r.second.day)
    }
}
