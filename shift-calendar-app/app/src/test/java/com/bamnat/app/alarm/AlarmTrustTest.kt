package com.bamnat.app.alarm

import android.content.Intent
import android.media.AudioManager
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.settings.nextLine
import com.bamnat.app.ui.settings.whenText
import com.bamnat.core.alarm.PlannedAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** [EASE-17][EASE-20][EASE-21] 1.38 알람 믿음: the call hold has an end, the ringing screen knows the shift, the card says the next alarm. */
@RunWith(AndroidJUnit4::class)
class AlarmTrustTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun callHoldEnds() {
        assertTrue(AlarmService.holdsForCall(AudioManager.MODE_IN_CALL, 0))
        assertTrue(AlarmService.holdsForCall(AudioManager.MODE_RINGTONE, 10 * 60_000L))
        assertFalse(AlarmService.holdsForCall(AudioManager.MODE_IN_CALL, AlarmService.CALL_HOLD_MILLIS))
        // 메신저 통화 모드 left on by an app: three minutes, then it rings.
        assertTrue(AlarmService.holdsForCall(AudioManager.MODE_IN_COMMUNICATION, 60_000L))
        assertFalse(AlarmService.holdsForCall(AudioManager.MODE_IN_COMMUNICATION, AlarmService.APP_CALL_HOLD_MILLIS))
        assertFalse(AlarmService.holdsForCall(AudioManager.MODE_NORMAL, 0))
    }

    @Test
    fun ringCarriesShiftColourAndSaysTheDay() {
        val ring = RingRequest("야간", today, LocalTime.of(22, 0), 60, null, 0L, shiftColor = 0xFF5E35B1)
        assertEquals(0xFF5E35B1, RingRequest.decode(ring.encode())!!.shiftColor)
        assertEquals(0xFF5E35B1, RingRequest.fromIntent(ring.putInto(Intent()))!!.shiftColor)
        val shown = ringShift(ring, today)!!
        assertEquals("오늘 근무", shown.day)
        assertEquals("야간", shown.name)
        assertEquals(Color(0xFF5E35B1.toInt()), shown.color)
        assertEquals("내일 근무", ringShift(ring, today.minusDays(1))!!.day)
        assertEquals("어제 시작한 근무", ringShift(ring, today.plusDays(1))!!.day)
        // No block for a memo's schedule, a test or 일어났나요?.
        assertNull(ringShift(ring.copy(label = "회의"), today))
        assertNull(ringShift(ring.copy(recheck = true), today))
        assertNull(ringShift(ring.copy(shiftName = ""), today))
    }

    @Test
    fun cardSaysTheNextAlarm() {
        val plan = PlannedAlarm(today.plusDays(1).atTime(5, 30), today.plusDays(1), LocalTime.of(7, 0), 1, "주간", 90, null)
        assertEquals("내일 05:30 · 주간 근무 알람", nextLine(true, UpcomingAlarm(plan, 0L, true), today))
        assertEquals("알람이 모두 꺼져 있어요", nextLine(false, UpcomingAlarm(plan, 0L, true), today))
        assertEquals("오늘", whenText(today, today))
        assertEquals("11월 8일 (일)", whenText(today.plusDays(3), today))
    }
}
