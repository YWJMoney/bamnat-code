package com.bamnat.core

import com.bamnat.core.sleep.SleepAuto
import com.bamnat.core.sleep.SleepEntry
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVI-96] (검토 I03c-01) 누르지 않는 수면 기록: the charger near bedtime, the stillness after, and the alarm turned off. */
class SleepNoTapTest {
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun aNightIsTheChargerToTheAlarmWhenThePhoneWasStillBetween() {
        assertEquals(SleepAuto.Segment(at(12, 23, 40), at(13, 6, 0)), SleepAuto.phoneNight(at(12, 23, 40), at(13, 0, 30), at(13, 6, 0)))
        // Never still (read in bed until the alarm), never on the charger, or still only after the alarm: nothing to ask.
        assertNull(SleepAuto.phoneNight(at(12, 23, 40), null, at(13, 6, 0)))
        assertNull(SleepAuto.phoneNight(null, at(13, 0, 30), at(13, 6, 0)))
        assertNull(SleepAuto.phoneNight(at(12, 23, 40), at(13, 6, 10), at(13, 6, 0)))
        // Too short for a night.
        assertNull(SleepAuto.phoneNight(at(13, 5, 40), at(13, 5, 50), at(13, 6, 0)))
    }

    @Test fun itIsAskedLikeAPieceTheSleepApiSawAndNotOverATappedSleep() {
        val night = SleepAuto.phoneNight(at(12, 23, 40), at(13, 0, 30), at(13, 6, 0))!!
        assertEquals(listOf(night), SleepAuto.candidates(listOf(night), emptyList(), listOf(at(13, 6, 0)), emptySet(), at(13, 6, 30)))
        // Not before it has settled.
        assertEquals(emptyList<SleepAuto.Segment>(), SleepAuto.candidates(listOf(night), emptyList(), emptyList(), emptySet(), at(13, 6, 5)))
        // 잠들었어요 was tapped that night: the tap stands.
        val tapped = SleepEntry(1, at(13, 0, 10), at(13, 6, 0))
        assertEquals(emptyList<SleepAuto.Segment>(), SleepAuto.candidates(listOf(night), listOf(tapped), emptyList(), emptySet(), at(13, 6, 30)))
    }
}
