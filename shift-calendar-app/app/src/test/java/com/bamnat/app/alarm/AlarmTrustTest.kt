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
        // 1.39 [SHOP-13] 8차 PC 식당: 가게의 영업은 '영업 근무 알람'이 아니에요.
        assertEquals("내일 05:30 · 영업 알람", nextLine(true, UpcomingAlarm(plan.copy(shiftName = "영업"), 0L, true), today))
        assertEquals("알람이 모두 꺼져 있어요", nextLine(false, UpcomingAlarm(plan, 0L, true), today))
        assertEquals("오늘", whenText(today, today))
        assertEquals("11월 8일 (일)", whenText(today.plusDays(3), today))
    }

    /** PC 267560dd: the words the build got wrong or left unclear. */
    @Test
    fun pcWordsRead() {
        assertEquals("시작 23시간 50분 뒤", com.bamnat.app.ui.Fmt.startOffset(-(23 * 60 + 50)))
        assertEquals("시작 30분 전", com.bamnat.app.ui.Fmt.startOffset(30))
        assertEquals("로", com.bamnat.app.ui.Fmt.ro("시작 23시간 50분 뒤"))
        val test = RingRequest(AlarmScheduler.TEST_NAME, today, LocalTime.of(14, 8), 0, null, 0L, label = AlarmScheduler.TEST_NAME)
        assertEquals("소리와 화면이 잘 나오는지 보는 알람이에요", AlarmService.describe(test))
        // A nap alarm the day after a 야간 says the shift began yesterday.
        val nap = RingRequest("야간", today, LocalTime.of(19, 0), -(19 * 60), null,
            today.plusDays(1).atTime(14, 0).atZone(com.bamnat.core.time.Today.zone()).toInstant().toEpochMilli())
        assertEquals("어제 19:00에 시작한 근무 · 시작 19시간 뒤 알람", AlarmService.describe(nap))
        assertEquals("오늘 남은 알람 모두 끄기", allOffLabel(false))
        assertTrue(allOffLabel(true).startsWith("한 번 더 누르면"))
    }

    /** [ALRM-29] What a change to the calendar says about alarms. */
    @Test
    fun calendarChangeSaysTheAlarm() {
        fun a(h: Int, m: Int) = PlannedAlarm(today.atTime(h, m), today, LocalTime.of(7, 0), 1, "주간", 60, null)
        fun w(was: List<PlannedAlarm>, now: List<PlannedAlarm>, skipped: Boolean = false, byHand: Boolean = false, wasWork: Boolean = false) =
            com.bamnat.core.alarm.AlarmPlanner.WorkDay(today, was, now, skipped, byHand, wasWork)
        assertEquals(" · 06:00 알람이 울려요", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(emptyList(), listOf(a(6, 0))))))
        assertEquals(" · 꺼 뒀던 06:00 알람을 켰어요", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(emptyList(), listOf(a(6, 0)), skipped = true))))
        assertEquals(" · 이날 알람은 꺼져 있어요", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(emptyList(), listOf(a(6, 0)), skipped = true, byHand = true))))
        // 오전 반차 or another shift: the alarm moved.
        assertEquals(" · 알람 05:30 → 09:30", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(listOf(a(5, 30)), listOf(a(9, 30)), wasWork = true))))
        // Turned off at work by the user: the change says nothing of it.
        assertEquals("", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(listOf(a(5, 30)), listOf(a(9, 30)), skipped = true, wasWork = true))))
        assertEquals(" · 2일 알람이 맞춰졌어요", com.bamnat.app.ui.calendar.workAlarmWords(listOf(w(emptyList(), listOf(a(6, 0))), w(emptyList(), listOf(a(7, 0))))))
    }
}
