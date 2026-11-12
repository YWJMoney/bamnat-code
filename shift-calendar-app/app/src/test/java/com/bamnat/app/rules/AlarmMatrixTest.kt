package com.bamnat.app.rules

import android.app.AlarmManager
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.invest.InvestNotices
import com.bamnat.app.memo.MemoScheduleAlarm
import com.bamnat.app.sleep.SmartWake
import com.bamnat.core.invest.AlarmKind
import com.bamnat.core.invest.InvestAlarm
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.LocalTime

/**
 * [REL-14] 326 (검토 C1·C2): every kind of alarm the app sets (the shift alarm, 알람 점검's test alarm, a snooze),
 * on Android 8, 11, 13 and 15, with 알람 및 리마인더 allowed and refused. Allowed (or before Android 12, where
 * there is no such permission): an alarm clock, which rings in Doze. Refused: still set, allowed while idle.
 *
 * 2.0.0 [RVR-18] (검토 TEST-19·T60-01) Not only the shift's: a memo's schedule and an investor's alarm, each ringing and as a notice
 * only, and the smart wake's window, each with the way it is promised ([Way]). The day is fixed (Today.fix), not the real clock.
 * Every other place that sets a system alarm is in [AlarmWaysRulesTest]'s table.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 30, 33, 35])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AlarmMatrixTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val day = LocalDate.of(2026, 10, 14)
    private val shiftDay = ShiftType(1, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0), alarmEnabled = true)
    private val calendars = listOf(com.bamnat.app.alarm.RingingCalendar(null, ScheduleSnapshot(listOf(shiftDay), WorkPattern.Rotation(day.minusDays(3), listOf(1L)), emptyList())))
    private fun millis(date: LocalDate, time: LocalTime) = date.atTime(time).atZone(Today.zone()).toInstant().toEpochMilli()

    @Before fun quiet() {
        Today.fix(day.atTime(1, 0).atZone(Today.zone()))
        // The app's own planning would use the same slots a moment later.
        app.container.appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun allowAgain() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.container.settingsRepository.update { it.copy(smartWakeMinutes = 0) }
        Today.reset()
    }

    /** Sets each kind and returns what the system holds after each, by name. */
    private fun setEveryKind(): Map<String, ShadowAlarmManager.ScheduledAlarm?> {
        val s = AlarmScheduler(app, app.container.telemetry)
        val out = LinkedHashMap<String, ShadowAlarmManager.ScheduledAlarm?>()
        fun added(set: () -> Unit): ShadowAlarmManager.ScheduledAlarm? {
            val before = alarms.scheduledAlarms.toSet()
            set()
            return (alarms.scheduledAlarms - before).firstOrNull()
        }
        val now = Today.dateTime()
        out["shift"] = added { s.reschedule(calendars, true, now) }
        out["test"] = added { s.scheduleTest() }
        val ring = RingRequest("주간", day, LocalTime.of(7, 0), 0, null, Today.millis(), label = "주간")
        out["snooze"] = added { s.scheduleSnooze(ring, 5) }
        out["memo rings"] = added {
            MemoScheduleAlarm.reschedule(app, listOf(Memo(id = 1, title = "면접", reminder = MemoReminder(day.atTime(9, 0), ring = true, notify = false))), now)
        }
        out["memo notice"] = added {
            MemoScheduleAlarm.reschedule(app, listOf(Memo(id = 2, title = "치과", reminder = MemoReminder(day.atTime(10, 0), ring = false, notify = true))), now)
        }
        fun invest(ring: Boolean) = listOf(InvestNotices.Plan("invest", "투자", listOf(InvestAlarm(kind = AlarmKind.TARGET, minute = 600, once = day.plusDays(1), ring = ring)), emptyList()))
        out["invest rings"] = added { InvestNotices.reschedule(app, invest(ring = true), now) }
        out["invest notice"] = added { InvestNotices.reschedule(app, invest(ring = false), now) }
        app.container.settingsRepository.update { it.copy(smartWakeMinutes = 20) }
        out["smart wake"] = added { SmartWake.arm(app, RingRequest("주간", day, LocalTime.of(7, 0), 60, null, millis(day, LocalTime.of(6, 0)))) }
        return out
    }

    /** How a kind is promised to ring: an alarm clock (on time, the icon in the bar), exact while idle, or roughly then. */
    private enum class Way { CLOCK, EXACT, ROUGH }

    private fun ShadowAlarmManager.ScheduledAlarm.way() = when {
        alarmClockInfo != null -> Way.CLOCK
        windowLengthMs == ShadowAlarmManager.WINDOW_EXACT -> Way.EXACT
        else -> Way.ROUGH
    }

    /** The promise with 알람 및 리마인더 allowed: ringing kinds are alarm clocks, notices and the smart wake's window exact. */
    private val promised = linkedMapOf(
        "shift" to Way.CLOCK, "test" to Way.CLOCK, "snooze" to Way.CLOCK,
        "memo rings" to Way.CLOCK, "memo notice" to Way.EXACT,
        "invest rings" to Way.CLOCK, "invest notice" to Way.EXACT,
        "smart wake" to Way.EXACT,
    )

    @Test fun withThePermissionEveryKindRingsTheWayItIsPromised() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val set = setEveryKind()
        assertEquals(promised.keys.toList(), set.keys.toList())
        for ((kind, alarm) in set) {
            assertNotNull("$kind on API ${Build.VERSION.SDK_INT}", alarm)
            assertEquals("$kind on API ${Build.VERSION.SDK_INT}", promised[kind], alarm!!.way())
            assertTrue("$kind rings while the phone dozes", alarm.isAllowWhileIdle || alarm.alarmClockInfo != null)
        }
    }

    @Test fun refusedEveryKindStillRings() {
        assumeTrue("the permission exists from Android 12", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        for ((kind, alarm) in setEveryKind()) {
            assertNotNull("$kind still set on API ${Build.VERSION.SDK_INT}", alarm)
            assertNull("$kind is not an alarm clock without the permission", alarm!!.alarmClockInfo)
            assertEquals("$kind rings roughly then without the permission", Way.ROUGH, alarm.way())
            assertTrue("$kind rings while the phone dozes", alarm.isAllowWhileIdle)
        }
    }
}
