package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.37 묶음 H [LEDH-11][LEDH-13] 모으기 봉투·세금 봉투. */
class EnvelopesTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val trip = Envelope(id = 1, name = "여행", target = 1_200_000, due = LocalDate.of(2027, 3, 1), sharePercent = 10)
    private val tax = Envelope(id = 2, name = "종소세", kind = EnvelopeKind.TAX, sharePercent = 5)

    private fun move(id: Long, daysAgo: Long, amount: Long) = EnvelopeMove(id, 1, today.minusDays(daysAgo), amount)

    @Test fun paceGivesTheDayTheGoalIsReached() {
        // 100,000 a month for three months.
        val moves = listOf(move(1, 80, 100_000), move(2, 50, 100_000), move(3, 20, 100_000))
        val s = Envelopes.statusOf(trip, moves, today)
        assertEquals(300_000L, s.saved)
        assertEquals(900_000L, s.left)
        assertEquals(100_000L, s.perMonth)
        assertEquals(today.plusDays(270), s.eta)
        // Due 3/1: four months left → 225,000 a month; at this pace it is late.
        assertEquals(225_000L, s.neededPerMonth)
        assertTrue(s.late)
        assertEquals(0.25f, s.fraction, 0.001f)
    }

    @Test fun extraMoneyAndShiftsSayHowMuchSooner() {
        val moves = listOf(move(1, 80, 100_000), move(2, 50, 100_000), move(3, 20, 100_000))
        val s = Envelopes.statusOf(trip, moves, today)
        assertEquals(30, Envelopes.soonerBy(s, 100_000, today))
        assertEquals(5, Envelopes.shiftsFor(900_000, 180_000))
        assertNull(Envelopes.shiftsFor(900_000, null))
    }

    @Test fun splitPutsTaxFirstAndNeverFillsPastTheGoal() {
        val nearlyFull = listOf(EnvelopeMove(1, 1, today, 1_150_000))
        val split = Envelopes.split(2_000_000, listOf(trip, tax), nearlyFull)
        assertEquals(listOf(2L, 1L), split.keys.toList())
        assertEquals(100_000L, split[2])
        assertEquals(50_000L, split[1])
        assertEquals(mapOf(2L to 50_000L), Envelopes.split(1_000_000, listOf(trip, tax), emptyList(), taxOnly = true))
        assertTrue(Envelopes.split(0, listOf(trip), emptyList()).isEmpty())
    }

    @Test fun threeGoalsAreFreeAndTaxDoesNotCount() {
        val three = (1L..3L).map { Envelope(id = it, name = "목표$it") } + tax
        assertFalse(Envelopes.canAdd(three, pro = false))
        assertTrue(Envelopes.canAdd(three, pro = true))
        assertTrue(Envelopes.canAdd(three.drop(1), pro = false))
    }
}
