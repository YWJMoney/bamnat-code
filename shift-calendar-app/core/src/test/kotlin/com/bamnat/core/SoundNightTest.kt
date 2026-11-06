package com.bamnat.core

import com.bamnat.core.sleep.ClipPicker
import com.bamnat.core.sleep.SleepOnset
import com.bamnat.core.sleep.SoundDetector
import com.bamnat.core.sleep.SoundEvent
import com.bamnat.core.sleep.SoundFrame
import com.bamnat.core.sleep.SoundFrames
import com.bamnat.core.sleep.SoundKind
import com.bamnat.core.sleep.SoundSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** 1.38 [SLEEP-06][SLEEP-07] 소리 사건: 방 소리를 따라가고, 튀는 소리만 이름 붙여요. */
class SoundNightTest {
    private val quiet = SoundFrame(-60f, 0.5f, 0.1f)
    private fun run(vararg parts: Pair<SoundFrame, Long>): List<SoundEvent> {
        val out = mutableListOf<SoundEvent>()
        val d = SoundDetector { out += it }
        for ((f, ms) in parts) repeat((ms / SoundFrames.FRAME_MILLIS).toInt()) { d.push(f) }
        d.finish()
        return out
    }

    @Test fun aQuietRoomHasNoEvents() {
        assertTrue(run(quiet to 60_000).isEmpty())
    }

    @Test fun snoresAreLowSmoothBreaths() {
        val snore = SoundFrame(-40f, 0.8f, 0.03f)
        val e = run(quiet to 10_000, snore to 1_200, quiet to 3_000, snore to 1_200, quiet to 3_000)
        assertEquals(listOf(SoundKind.SNORE, SoundKind.SNORE), e.map { it.kind })
        assertTrue(e[0].millis in 1_100..1_400)
        assertTrue(e[0].startMillis in 9_960L..10_020L)
    }

    @Test fun aCoughIsShortLoudAndBroad() {
        val e = run(quiet to 10_000, SoundFrame(-30f, 0.3f, 0.15f) to 450, quiet to 2_000)
        assertEquals(listOf(SoundKind.COUGH), e.map { it.kind })
    }

    @Test fun softRustleIsMoving() {
        val e = run(quiet to 10_000, SoundFrame(-46f, 0.2f, 0.3f) to 1_500, quiet to 2_000)
        assertEquals(listOf(SoundKind.MOVE), e.map { it.kind })
    }

    @Test fun aSteadyFanBecomesTheRoom() {
        // Our own sleep sound or a fan: loud at first, then just the room.
        val fan = SoundFrame(-35f, 0.7f, 0.05f)
        val e = run(quiet to 5_000, fan to 15 * 60_000L)
        assertTrue("only the start of the fan stands out: $e", e.size <= 2)
    }

    @Test fun framesFromSamples() {
        val n = 480
        val low = ShortArray(n) { (8000 * sin(2 * PI * 100 * it / 16_000)).toInt().toShort() }
        val high = ShortArray(n) { (8000 * sin(2 * PI * 4000 * it / 16_000)).toInt().toShort() }
        val fl = SoundFrames.of(low); val fh = SoundFrames.of(high)
        assertTrue(fl.low > fh.low)
        assertTrue(fh.zcr > fl.zcr)
        assertTrue("${fl.db}", fl.db in -16f..-14f)
        assertEquals(-96f, SoundFrames.of(ShortArray(n)).db)
    }

    @Test fun clipsSpreadOverTheHoursAndTheMorningGetsThreeKinds() {
        val h = 3_600_000L
        val ev = (0 until 10).map { SoundEvent(it * 1000L, it * 1000L + 800, SoundKind.SNORE, 20f + it) } +
            SoundEvent(2 * h, 2 * h + 500, SoundKind.COUGH, 15f) + SoundEvent(3 * h, 3 * h + 3000, SoundKind.TALK, 12f) +
            SoundEvent(3 * h + 9000, 3 * h + 9900, SoundKind.MOVE, 30f)
        val kept = ClipPicker.pick(ev)
        assertEquals(3, kept.count { it.startMillis < h })
        assertTrue(kept.none { it.kind == SoundKind.MOVE })
        assertEquals(setOf(SoundKind.SNORE, SoundKind.COUGH, SoundKind.TALK), ClipPicker.listen(ev).map { it.kind }.toSet())
    }

    @Test fun summaryCountsSnoringInMinutes() {
        val ev = listOf(
            SoundEvent(0, 1_000, SoundKind.SNORE, 10f), SoundEvent(60_000, 61_000, SoundKind.SNORE, 10f),
            SoundEvent(600_000, 601_000, SoundKind.SNORE, 10f), SoundEvent(700_000, 700_400, SoundKind.COUGH, 20f),
        )
        val s = SoundSummary.of(ev)
        assertEquals(2L, s.snoreMinutes)
        assertEquals("코골이 2분 · 기침 1번", s.line())
        assertEquals("조용한 밤이었어요", SoundSummary.of(emptyList()).line())
    }

    @Test fun asleepAfterFifteenStillMinutes() {
        val moved = List(10) { true } + List(5) { false } + listOf(true) + List(20) { false }
        assertEquals(16, SleepOnset.minuteAsleep(moved))
        assertNull(SleepOnset.minuteAsleep(List(30) { it % 10 == 0 }))
    }
}
