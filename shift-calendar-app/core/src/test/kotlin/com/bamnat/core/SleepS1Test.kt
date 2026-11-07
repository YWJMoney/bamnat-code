package com.bamnat.core

import com.bamnat.core.sleep.Noise
import com.bamnat.core.sleep.NoiseMix
import com.bamnat.core.sleep.NoiseSynth
import com.bamnat.core.sleep.NoiseTimer
import com.bamnat.core.sleep.SleepAuto
import com.bamnat.core.sleep.SleepAuto.Segment
import com.bamnat.core.sleep.SleepCheck
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepMood
import com.bamnat.core.sleep.SleepMorning
import com.bamnat.core.sleep.SleepTag
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.sqrt

/** 1.38 [SLEEP-01][SLEEP-03][SLEEP-04] 잠드는 소리, 아침 한 장, 자동으로 잡은 잠. */
class SleepS1Test {
    private fun t(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    // ---- SLEEP-04 ----

    private fun rms(x: FloatArray) = sqrt(x.sumOf { (it * it).toDouble() } / x.size)
    /** How much of the sound is in quick changes: high for hiss, low for rumble. */
    private fun hiss(x: FloatArray): Double {
        var d = 0.0
        for (i in 1 until x.size) d += ((x[i] - x[i - 1]) * (x[i] - x[i - 1])).toDouble()
        return sqrt(d / (x.size - 1)) / rms(x)
    }
    private fun play(noise: Noise, seconds: Int = 4): FloatArray {
        val s = NoiseSynth(seed = 7)
        val out = FloatArray(NoiseSynth.SAMPLE_RATE * seconds)
        s.render(NoiseMix(mapOf(noise to 1f)), out)
        return out
    }

    @Test fun everySoundIsAudibleAndNeverClips() {
        for (n in Noise.entries) {
            val x = play(n)
            assertTrue("$n is heard", rms(x) > 0.01)
            assertTrue("$n stays in range", x.all { it in -1f..1f })
            assertTrue("$n rarely touches the limit", x.count { kotlin.math.abs(it) >= 0.999f } < x.size / 1000)
        }
    }

    @Test fun pinkIsSofterUpTopThanWhiteAndBrownSofterStill() {
        val white = hiss(play(Noise.WHITE))
        val pink = hiss(play(Noise.PINK))
        val brown = hiss(play(Noise.BROWN))
        assertTrue("white $white > pink $pink", white > pink * 1.5)
        assertTrue("pink $pink > brown $brown", pink > brown * 1.5)
    }

    @Test fun sameSeedSameSound() {
        val a = FloatArray(2048); val b = FloatArray(2048)
        NoiseSynth(seed = 3).render(NoiseMix.DEFAULT, a)
        NoiseSynth(seed = 3).render(NoiseMix.DEFAULT, b)
        assertArrayEquals(a, b, 0f)
    }

    @Test fun silentMixIsSilent() {
        val x = FloatArray(1000)
        NoiseSynth().render(NoiseMix(emptyMap()), x)
        assertTrue(x.all { it == 0f })
    }

    @Test fun mixRoundTripsThroughSettings() {
        val m = NoiseMix(mapOf(Noise.RAIN to 0.6f, Noise.FAN to 0.25f))
        assertEquals("rain:0.60,fan:0.25", m.encode())
        assertEquals(m.levels, NoiseMix.decode(m.encode()).levels)
        assertEquals(NoiseMix.DEFAULT, NoiseMix.decode(null))
        assertEquals(NoiseMix.DEFAULT, NoiseMix.decode("nonsense"))
        assertEquals(1f, NoiseMix.decode("white:3").levels[Noise.WHITE])
    }

    @Test fun timerFadesOutAndStops() {
        val total = 30 * 60_000L
        assertEquals(1f, NoiseTimer.gain(0, total))
        assertEquals(1f, NoiseTimer.gain(total - NoiseTimer.FADE_MILLIS, total))
        val mid = NoiseTimer.gain(total - NoiseTimer.FADE_MILLIS / 2, total)
        assertTrue(mid in 0.2f..0.3f)
        assertEquals(0f, NoiseTimer.gain(total, total))
        assertEquals(1f, NoiseTimer.gain(10 * 3_600_000L, 0))
        var last = 1f
        for (e in 0..total step 5_000) { val g = NoiseTimer.gain(e, total); assertTrue(g <= last); last = g }
        assertEquals("계속", NoiseTimer.label(0)); assertEquals("1시간", NoiseTimer.label(60)); assertEquals("15분", NoiseTimer.label(15))
    }

    // ---- SLEEP-03 ----

    @Test fun asksAboutTheLatestSleepOnceAndNotLate() {
        val night = SleepEntry(1, t(5, 8, 10), t(5, 15, 0))
        val nap = SleepEntry(2, t(5, 17, 0), t(5, 17, 40))
        val now = t(5, 18)
        // A 40-minute nap is not asked about; the main sleep is.
        assertEquals(night, SleepMorning.pending(listOf(nap, night), emptyList(), now))
        val answered = SleepCheck(start = night.start, end = night.end!!, mood = SleepMood.FRESH, tags = setOf(SleepTag.LIGHT))
        assertNull(SleepMorning.pending(listOf(nap, night), listOf(answered), now))
        // Moved by 20 minutes since: still the same sleep.
        assertNull(SleepMorning.pending(listOf(night.copy(end = t(5, 15, 20))), listOf(answered), now))
        // Too long ago.
        assertNull(SleepMorning.pending(listOf(night), emptyList(), t(6, 6)))
        // Still going on.
        assertNull(SleepMorning.pending(listOf(SleepEntry(3, t(5, 8), null)), emptyList(), now))
    }

    @Test fun tagsAndSummary() {
        val tags = setOf(SleepTag.NOISE, SleepTag.CAFFEINE)
        assertEquals("caffeine,noise", SleepTag.encode(tags))
        assertEquals(tags, SleepTag.decode("noise, caffeine,unknown"))
        assertEquals("개운 · 늦은 카페인, 시끄러웠음",
            SleepMorning.summary(SleepCheck(start = t(1, 0), end = t(1, 7), mood = SleepMood.FRESH, tags = tags)))
        assertEquals("답했어요", SleepMorning.summary(SleepCheck(start = t(1, 0), end = t(1, 7), mood = null)))
        assertEquals(SleepMood.GREAT, SleepMood.of(5)); assertNull(SleepMood.of(9))
    }

    // ---- SLEEP-01 ----

    @Test fun joinsShortWakingsAndTrimsToTheAlarm() {
        val segs = listOf(Segment(t(5, 8, 20), t(5, 11, 30)), Segment(t(5, 11, 50), t(5, 15, 10)))
        val alarm = t(5, 14, 40)
        val c = SleepAuto.candidates(segs, emptyList(), listOf(alarm), emptySet(), t(5, 18))
        assertEquals(listOf(Segment(t(5, 8, 20), alarm)), c)
    }

    @Test fun keepsApartWhatIsFarApartAndDropsShortOnes() {
        val segs = listOf(Segment(t(5, 8, 0), t(5, 12, 0)), Segment(t(5, 13, 0), t(5, 13, 25)), Segment(t(5, 16, 0), t(5, 17, 0)))
        val c = SleepAuto.candidates(segs, emptyList(), emptyList(), emptySet(), t(5, 20))
        assertEquals(listOf(Segment(t(5, 8, 0), t(5, 12, 0)), Segment(t(5, 16, 0), t(5, 17, 0))), c)
    }

    @Test fun leavesOutWhatIsLoggedDeclinedStillGoingOrOld() {
        val s = Segment(t(5, 0, 0), t(5, 7, 0))
        val now = t(5, 9)
        assertEquals(listOf(s), SleepAuto.candidates(listOf(s), emptyList(), emptyList(), emptySet(), now))
        // The user tapped, or the watch measured it.
        assertTrue(SleepAuto.candidates(listOf(s), listOf(SleepEntry(1, t(4, 23, 50), t(5, 6, 50))), emptyList(), emptySet(), now).isEmpty())
        // A sleep left open by the taps covers it too.
        assertTrue(SleepAuto.candidates(listOf(s), listOf(SleepEntry(1, t(4, 23, 50), null)), emptyList(), emptySet(), now).isEmpty())
        assertTrue(SleepAuto.candidates(listOf(s), emptyList(), emptyList(), setOf(s.key), now).isEmpty())
        assertTrue(SleepAuto.candidates(listOf(s), emptyList(), emptyList(), emptySet(), t(5, 7, 5)).isEmpty())
        assertTrue(SleepAuto.candidates(listOf(s), emptyList(), emptyList(), emptySet(), t(6, 14)).isEmpty())
    }

    @Test fun anAlarmFarFromTheEndDoesNotCutTheSleep() {
        val s = Segment(t(5, 0, 0), t(5, 7, 0))
        assertEquals(listOf(s), SleepAuto.candidates(listOf(s), emptyList(), listOf(t(5, 3, 0), t(5, 9, 0)), emptySet(), t(5, 12)))
    }

    @Test fun trustAndLine() {
        assertTrue(!SleepAuto.trusted(1)); assertTrue(SleepAuto.trusted(2))
        assertEquals("07:40–14:10 · 6시간 30분", SleepAuto.line(Segment(t(5, 7, 40), t(5, 14, 10)), t(5, 16)))
        assertEquals("어제 23:00–06:00 · 7시간", SleepAuto.line(Segment(t(4, 23, 0), t(5, 6, 0)), t(6, 9)))
    }
}
