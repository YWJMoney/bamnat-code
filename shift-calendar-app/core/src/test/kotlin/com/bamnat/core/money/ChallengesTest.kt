package com.bamnat.core.money

import com.bamnat.core.money.Challenges.Stamp.KEPT
import com.bamnat.core.money.Challenges.Stamp.LATER
import com.bamnat.core.money.Challenges.Stamp.MISSED
import com.bamnat.core.money.Challenges.Stamp.NOT_COUNTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.38 [LEDH-31] 혼자 하는 챌린지. Amounts are made up. */
class ChallengesTest {
    private val start = LocalDate.of(2026, 11, 2)
    private fun e(d: LocalDate, cat: String, fixed: Boolean = false) = MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = 9_000, category = cat,
        source = if (fixed) MoneySource.FIXED else MoneySource.MANUAL)

    @Test fun noSpendDaysStampAndRepeatingCostsDoNotBreakThem() {
        val run = Challenges.Run(Challenges.Kind.NO_SPEND, start)
        val entries = listOf(e(start, "식비"), e(start.plusDays(1), "통신", fixed = true))
        val s = Challenges.status(run, entries, emptyList(), start.plusDays(3))
        assertEquals(listOf(MISSED, KEPT, KEPT, LATER, LATER, LATER, LATER), s.stamps)
        assertEquals(2, s.streak)
        assertEquals("2일째 이어 가는 중 · 3일 중 2일", Challenges.words(s))
    }

    /** 1.38 PC 1차: the first day says which day it is and when the stamp comes, not '0일 중 0일'. */
    @Test fun theFirstDaySaysWhenTheStampComes() {
        val s = Challenges.status(Challenges.Run(Challenges.Kind.LESS_DELIVERY, start), emptyList(), emptyList(), start)
        assertEquals("오늘 1일째 · 밤 12시에 도장 · 11/8까지", Challenges.words(s))
        val env = Challenges.status(Challenges.Run(Challenges.Kind.ENVELOPE_30, start), emptyList(), emptyList(), start)
        assertEquals("오늘 1일째 · 봉투에 넣으면 도장 · 12/1까지", Challenges.words(env))
    }

    @Test fun nightLunchboxCountsOnlyNights() {
        val run = Challenges.Run(Challenges.Kind.NIGHT_LUNCHBOX, start)
        val nights = setOf(start, start.plusDays(2))
        val s = Challenges.status(run, listOf(e(start.plusDays(2), "배달"), e(start.plusDays(1), "배달")), emptyList(), start.plusDays(3)) { it in nights }
        assertEquals(listOf(KEPT, NOT_COUNTED, MISSED, NOT_COUNTED), s.stamps.take(4))
        assertEquals(1, s.kept)
        assertEquals(2, s.counted)
    }

    @Test fun envelopeDaysAreTheDaysSomethingWentIn() {
        val run = Challenges.Run(Challenges.Kind.ENVELOPE_30, start)
        val moves = listOf(EnvelopeMove(envelopeId = 1, date = start, amount = 5_000), EnvelopeMove(envelopeId = 1, date = start.plusDays(1), amount = -5_000))
        val s = Challenges.status(run, emptyList(), moves, start.plusDays(1))
        assertEquals(listOf(KEPT, LATER), s.stamps.take(2))
    }

    @Test fun aFinishedRunSaysHowManyDaysWereKept() {
        val run = Challenges.Run(Challenges.Kind.LESS_DELIVERY, start)
        val s = Challenges.status(run, emptyList(), emptyList(), start.plusDays(10))
        assertTrue(s.done)
        assertEquals("끝났어요. 7일 중 7일 지켰어요. 다 지켰어요!", Challenges.words(s))
        assertEquals(run, Challenges.decode(Challenges.encode(run)))
    }
}
