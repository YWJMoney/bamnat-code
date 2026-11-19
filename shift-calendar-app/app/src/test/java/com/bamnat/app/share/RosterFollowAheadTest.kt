package com.bamnat.app.share

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.RingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVJ-99] 검토 PAYU-15: a followed 리더 link is looked at once 1시간 반 before the next work alarm, so a change made the night
 * before is in before the old alarm rings.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterFollowAheadTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L
    private fun ring(at: Long, snooze: Boolean = false, label: String? = null, nap: Boolean = false) =
        RingRequest("주간", LocalDate.of(2026, 12, 2), LocalTime.of(7, 0), 90, null, at, isSnooze = snooze, label = label, nap = nap)

    @Test fun anHourAndAHalfBeforeTheFirstWorkAlarmStillAhead() {
        val now = 100 * hour
        assertEquals("다음 근무 알람 1시간 반 전", now + 8 * hour - RosterFollowAhead.LEAD_MS,
            RosterFollowAhead.at(listOf(ring(now + 8 * hour), ring(now + 32 * hour)), now))
        assertEquals("1시간 뒤 알람은 이미 늦어 그다음 알람 앞", now + 32 * hour - RosterFollowAhead.LEAD_MS,
            RosterFollowAhead.at(listOf(ring(now + hour), ring(now + 32 * hour)), now))
        assertNull("다시 울림·낮잠·메모 일정은 빼요",
            RosterFollowAhead.at(listOf(ring(now + 8 * hour, snooze = true), ring(now + 9 * hour, nap = true), ring(now + 10 * hour, label = "치과")), now))
        assertNull(RosterFollowAhead.at(emptyList(), now))
    }

    @Test fun noLookWithoutAFollowedLink() {
        RosterFollowAhead.reschedule(context)
        val ours = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.filter {
            it.operation?.let { op -> shadowOf(op).savedIntent.component?.className } == RosterFollowAheadReceiver::class.java.name
        }
        assertEquals("따라가는 링크가 없으면 미리 보기도 없어요", 0, ours.size)
    }
}
