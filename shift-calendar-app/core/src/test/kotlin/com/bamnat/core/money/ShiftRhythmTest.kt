package com.bamnat.core.money

import com.bamnat.core.tax.TaxPlus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-08] 근무 리듬 예산. */
class ShiftRhythmTest {
    private val from = LocalDate.of(2026, 11, 4)
    private val until = from.plusDays(9)
    private val label: (LocalDate) -> String? = { d -> if ((d.dayOfMonth % 2) == 0) "야간" else null }
    private val rows = listOf(TaxPlus.ShiftSpend("야간", 10, 400_000), TaxPlus.ShiftSpend(TaxPlus.OFF, 10, 200_000))

    @Test fun moneyIsSharedByHowEachKindOfDaySpends() {
        val p = ShiftRhythm.plan(300_000, from, until, label, rows)!!
        assertEquals("야간", p.todayLabel)
        assertEquals(40_000L, p.today)
        assertEquals(listOf("야간" to 40_000L, TaxPlus.OFF to 20_000L), p.shares.map { it.label to it.perDay })
        assertEquals("야간 4만 · 쉬는 날 2만", p.words { "${it / 10_000}만" })
    }

    @Test fun nothingWhenThereIsNothingToShareDifferently() {
        assertNull(ShiftRhythm.plan(300_000, from, until, { "야간" }, rows))
        assertNull(ShiftRhythm.plan(300_000, from, until, label, rows.take(1)))
        assertNull(ShiftRhythm.plan(0, from, until, label, rows))
        // PC 5차: 이틀치 지출로는 배우지 않아요.
        assertNull(ShiftRhythm.plan(300_000, from, until, label, rows, spendDays = 2))
        // PC 확인 37446021: 새로 깔아 쓴 돈이 없으면 (날마다 0원) 아무 줄도 없어요.
        assertNull(ShiftRhythm.plan(300_000, from, until, label, listOf(TaxPlus.ShiftSpend("야간", 10, 0), TaxPlus.ShiftSpend(TaxPlus.OFF, 10, 0))))
    }
}
