package com.bamnat.core

import com.bamnat.core.imports.MonthFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ROST-08] "이 순서 반복": the order typed from the 1st carried to the month's end. */
class MonthFillTest {
    @Test fun repeatsTheOrderTyped() {
        val typed = "DDEENNOO".mapIndexed { i, c -> i + 1 to c }.toMap()
        val rest = MonthFill.repeat(typed, 9, 31)!!
        assertEquals((9..31).toList(), rest.keys.sorted())
        assertEquals("DDEENNOODDEENNOODDEENNO", (9..31).joinToString("") { rest.getValue(it).toString() })
    }

    @Test fun startsWhereTheTypingStarted() {
        // Typed from the 5th: the order counts from there, and a day typed later stays.
        val typed = mapOf(5 to 'D', 6 to 'N', 7 to 'O', 12 to 'X')
        val rest = MonthFill.repeat(typed, 8, 14)!!
        assertEquals(mapOf(8 to 'D', 9 to 'N', 10 to 'O', 11 to 'D', 13 to 'O', 14 to 'D'), rest)
    }

    @Test fun nothingToRepeat() {
        assertNull(MonthFill.repeat(mapOf(1 to 'D'), 2, 30))
        assertNull(MonthFill.repeat(emptyMap<Int, Char>(), 1, 30))
        assertNull(MonthFill.repeat(mapOf(1 to 'D', 2 to 'E'), 31, 30))
    }

    // ---- 2.0.0 [RVL-39] (검토 U07b-16) 지난달과 같게: 요일에 맞춰 · 내 순서 이어서 ----

    private fun run(text: String): List<Char?> = text.map { if (it == '.') null else it }

    @Test fun findsTheOrderOfARotation() {
        // DDEENNOO over two months (61 days): an order of 8, not 16 or 24, though those agree too.
        val days = run("DDEENNOO".repeat(8).take(61))
        assertEquals(8, MonthFill.period(days))
        // A week (weekdays on, weekend off) is an order of 7.
        assertEquals(7, MonthFill.period(run("WWWWWOO".repeat(9))))
    }

    @Test fun anOrderWithAFewChangedDaysIsStillFound() {
        val days = "DDEENNOO".repeat(8).take(61).toCharArray()
        // Three days traded (E→O, D→N, E→D).
        days[10] = 'O'; days[33] = 'N'; days[50] = 'D'
        assertEquals(8, MonthFill.period(days.map { it }))
    }

    @Test fun noOrderInARosterThatDoesNotRepeat() {
        assertNull(MonthFill.period(run("DENODDNOEEDONNDOEDNNOEODDENOEDDNOOEDNDOE")))
        // One kind of day only, or too little seen to call it an order.
        assertNull(MonthFill.period(run("D".repeat(40))))
        assertNull(MonthFill.period(run("DDEENNO")))
        // Unknown days do not count as agreeing.
        assertNull(MonthFill.period(run(".".repeat(60))))
    }

    @Test fun carriesTheOrderOnFromWhereItEnded() {
        // 30 days of DDEENNOO end on ..DDEENN (day 30 is N): the next month starts O O D D.
        val days = run("DDEENNOO".repeat(4).take(30))
        assertEquals("OODDEENNOO", MonthFill.carry(days, 8, 10).joinToString("") { it.toString() })
    }

    @Test fun carryTakesWhatMostDaysAtThatPlaceWere() {
        // Places 0..3 of an order of 4; place 1 was once changed to O. Unknown days stay unknown.
        val days = run("DEN.DON.DEN.")
        assertEquals(listOf('D', 'E', 'N', null), MonthFill.carry(days, 4, 4))
    }
}
