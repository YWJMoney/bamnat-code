package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2.0.0 [RVP-61] (검토 REST-02) Taking a look out does what the editor says ("이 모양을 쓰던 칸은 모양 1이 돼요"): the other days keep
 * the look they had.
 */
class WithoutLookTest {
    private val a = CellLook(bg = "FFFFE0E0")
    private val b = CellLook(bg = "FFE0FFE0")
    private val c = CellLook(bg = "FFE0E0FF")

    @Test fun byTheWeekTheOthersKeepTheirLook() {
        // 수 = look 3 (C), 토·일 = look 2 (B).
        val v = CalendarView(looks = listOf(a, b, c), pattern = CellPattern(CellPattern.WEEK, week = listOf(0, 0, 2, 0, 0, 1, 1)), todayLook = 2)
        val out = v.withoutLook(1)
        assertEquals(listOf(a, c), out.looks)
        assertEquals("수 keeps C, 토·일 go to look 1", listOf(0, 0, 1, 0, 0, 0, 0), out.pattern.week)
        assertEquals("today's C keeps its look", 1, out.todayLook)
        // The days draw as before, but for the ones that had B.
        assertEquals(c, out.lookFor(0, 3, null, today = false))
        assertEquals(c, v.lookFor(0, 3, null, today = false))
    }

    @Test fun byTheKindOfDayAndInTurns() {
        val v = CalendarView(looks = listOf(a, b, c), pattern = CellPattern(CellPattern.SHIFT, shift = mapOf("day" to 0, "night" to 2, "off" to 1)))
        assertEquals(mapOf("day" to 0, "night" to 1, "off" to 0), v.withoutLook(1).pattern.shift)
        val turns = CalendarView(looks = listOf(a, b, c), pattern = CellPattern(CellPattern.CYCLE, n = 3))
        assertEquals("three in turns with two looks left is two", 2, turns.withoutLook(0).pattern.n)
    }

    @Test fun theTodayLookTakenOutAndTheLastTwo() {
        val v = CalendarView(looks = listOf(a, b), pattern = CellPattern(CellPattern.WEEK, week = listOf(0, 0, 0, 0, 0, 1, 1)), todayLook = 1)
        val out = v.withoutLook(1)
        assertEquals(listOf(a), out.looks)
        assertEquals(CellPattern(), out.pattern)
        assertNull(out.todayLook)
        assertEquals("only the app's own look left is no looks", CalendarView(), CalendarView(looks = listOf(CellLook(), b)).withoutLook(1))
    }
}
