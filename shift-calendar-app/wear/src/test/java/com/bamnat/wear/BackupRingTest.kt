package com.bamnat.wear

import com.bamnat.core.time.Today
import com.bamnat.core.work.WatchPayload
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 [RVI-90] (검토 I03b-01) 워치 예비 알람: the wrist buzzes only when the phone's ring never came and the phone is gone. */
class BackupRingTest {
    private val alarm = LocalDateTime.of(2026, 11, 13, 6, 0)
    private val at = alarm.atZone(Today.zone()).toInstant().toEpochMilli()
    private fun payload(ring: Boolean = true, next: LocalDateTime? = alarm) =
        WatchPayload(emptyList(), alarm.minusHours(8), nextAlarm = next, alarmLabel = "주간 근무", ringOnWatch = ring)

    @Test fun ringsOnlyWhenThePhoneIsGoneAndNeverRang() {
        // The phone's battery ran out in the night: no ring came, and nothing answers.
        assertEquals(BackupRing.Why.RING, BackupRing.decide(at, payload(), phoneRangAt = 0L, phoneThere = false))
        // The phone rang (it told the watch), even early in its 깨우는 구간.
        assertEquals(BackupRing.Why.PHONE_RANG, BackupRing.decide(at, payload(), phoneRangAt = at - 20 * 60_000L, phoneThere = false))
        // Yesterday's ring does not count.
        assertEquals(BackupRing.Why.RING, BackupRing.decide(at, payload(), phoneRangAt = at - 24 * 3600_000L, phoneThere = false))
        // The phone is there: it rang by itself or the alarm was turned off.
        assertEquals(BackupRing.Why.PHONE_THERE, BackupRing.decide(at, payload(), phoneRangAt = 0L, phoneThere = true))
    }

    @Test fun nothingWithoutTheWatchAlarmOrWhenTheAlarmMoved() {
        assertEquals(BackupRing.Why.NO_ALARM, BackupRing.decide(at, payload(ring = false), 0L, false))
        assertEquals(BackupRing.Why.NO_ALARM, BackupRing.decide(at, payload(next = null), 0L, false))
        assertEquals(BackupRing.Why.NO_ALARM, BackupRing.decide(at, null, 0L, false))
        // A newer copy says the next alarm is another one (이번만 끄기 on the phone): this backup is stale.
        assertEquals(BackupRing.Why.MOVED, BackupRing.decide(at, payload(next = alarm.plusDays(1)), 0L, false))
    }
}
