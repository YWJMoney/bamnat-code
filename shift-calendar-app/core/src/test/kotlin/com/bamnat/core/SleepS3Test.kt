package com.bamnat.core

import com.bamnat.core.sleep.BeforeAfter
import com.bamnat.core.sleep.PlanVsActual
import com.bamnat.core.sleep.RecoveryChallenge
import com.bamnat.core.sleep.ShiftAdapt
import com.bamnat.core.sleep.SleepCauses
import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepMood
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.sleep.SleepTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.38 [SLEEP-10][SLEEP-13][SLEEP-20][SLEEP-22] 적응 계획, 원인 찾기, 회복 도전, 조치 전후. */
class SleepS3Test {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun work(day: Int, h: Int, name: String) =
        SleepSchedule.Work(d(day), d(day).atTime(h, 0), d(day).atTime(h, 0).plusHours(9), name)

    @Test fun movesTheBedtimeTowardsNightsThreeDaysAhead() {
        val w = listOf(work(1, 7, "주간"), work(2, 7, "주간"), work(6, 22, "야간"))
        assertNull("too far yet", ShiftAdapt.stepFor(d(2), w))
        val first = ShiftAdapt.stepFor(d(3), w)!!
        assertEquals(30, first.shiftMinutes)
        assertEquals("야간까지 3일 · 오늘은 평소보다 30분 늦게 자요", first.head)
        assertEquals(90, ShiftAdapt.stepFor(d(5), w)!!.shiftMinutes)
        assertTrue(first.lines.any { it.second == ShiftAdapt.Strength.STRONG })
        // Back to days: earlier.
        val back = listOf(work(1, 22, "야간"), work(4, 7, "주간"))
        assertEquals(-60, ShiftAdapt.stepFor(d(2), back)!!.shiftMinutes)
        // Same rhythm: nothing.
        assertNull(ShiftAdapt.stepFor(d(2), listOf(work(1, 7, "주간"), work(3, 9, "주간"))))
    }

    @Test fun causesNeedFiveTimesAndAClearDifference() {
        fun c(i: Int, mood: SleepMood, vararg t: SleepTag) =
            SleepCheck(start = LocalDateTime.of(2026, 10, 1, 0, 0).plusDays(i.toLong()), end = LocalDateTime.of(2026, 10, 1, 7, 0).plusDays(i.toLong()), mood = mood, tags = t.toSet())
        val checks = (0 until 8).map { c(it, if (it < 6) SleepMood.TIRED else SleepMood.FRESH, SleepTag.CAFFEINE) } +
            (8 until 16).map { c(it, if (it < 9) SleepMood.TIRED else SleepMood.FRESH) }
        val f = SleepCauses.find(checks).single()
        assertEquals("‘늦은 카페인’ 고른 8번 중 6번 피곤했어요", f.line)
        assertTrue(SleepCauses.find(checks.take(4)).isEmpty())
    }

    @Test fun challengeCountsKeptDaysOnly() {
        fun n(day: Int, minutes: Long?) = PlanVsActual.Night(d(day), PlanVsActual.Kind.AFTER_NIGHT, d(day).atTime(9, 0), d(day).atTime(16, 0),
            minutes?.let { listOf(SleepEntry(day.toLong(), d(day).atTime(9, 0), d(day).atTime(9, 0).plusMinutes(it))) } ?: emptyList())
        val p = RecoveryChallenge.progress(d(1), listOf(n(1, 420), n(2, 200), n(3, null), n(4, 400)), d(5))
        assertEquals(listOf(d(1), d(4)), p.met)
        assertEquals(listOf(d(2)), p.short)
        assertEquals("14일 중 2일 채웠어요", p.line)
    }

    @Test fun beforeAndAfterAMarkedDay() {
        val snore = (1..7).associate { d(it) to 40L } + (8..14).associate { d(it) to 15L }
        val mood = (1..7).associate { d(it) to 2 } + (8..14).associate { d(it) to 4 }
        assertEquals("코골이 하룻밤 40분 → 15분 · 아침 기분 2.0 → 4.0", BeforeAfter.compare(d(8), snore, mood)!!.line)
        assertNull(BeforeAfter.compare(d(8), mapOf(d(7) to 30L), emptyMap()))
    }
}
