package com.bamnat.app.r132

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.AlarmService
import com.bamnat.app.alarm.LeaveTimeNotice
import com.bamnat.app.alarm.RingRequest
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.WorkTypePrefs
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.FieldMode
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 1.32 [WTYP-13] 조사 대조 W10·W19·C12: 회귀 시험 묶음. A phone moved by backup still rings (the alarm planned again, an old
 * phone's sound file giving way to this phone's), an alarm and the 퇴근 notice follow a changed 근무 시간, and every setting
 * this round added comes back on a new phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WorkTypeRegression132Test {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 11, 2)

    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(t: LocalDateTime) = Today.fix(t.atZone(zone))
    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * The alarms follow the calendar's shared snapshot, which takes a moment after a write: the app's own follower plans
     * again when it comes. Waits up to 5 seconds for [want], planning again meanwhile as the app does.
     */
    private suspend fun planned(want: Long): Long? {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            c.rescheduleAlarms()
            val got = c.alarmScheduler.upcoming.value?.triggerAtMillis
            if (got == want) return got
            Thread.sleep(50)
        }
        return c.alarmScheduler.upcoming.value?.triggerAtMillis
    }

    @After fun tearDown() = runBlocking {
        c.settingsRepository.update { AppSettings() }
        Today.reset()
    }

    /** W10: after a restore on a new phone the alarm is planned again, and a sound the new phone lacks is never silence. */
    @Test fun aPhoneMovedByBackupStillRings() = runBlocking {
        at(day.atTime(5, 0))
        val repo = c.scheduleRepository
        val old = "content://media/external/audio/media/99999"
        val id = repo.saveShiftType(ShiftType(0, "이전 주간", "이", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0), alarmEnabled = true,
            alarms = listOf(AlarmSpec(60)), alarmSoundUri = old))
        repo.setOverride(day, ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true) }
        assertEquals(millis(day.atTime(6, 0)), planned(millis(day.atTime(6, 0))))
        val file = c.backupManager.export()

        // The new phone: its alarms off and nothing planned.
        c.settingsRepository.update { it.copy(alarmsEnabled = false) }
        c.rescheduleAlarms()
        assertEquals(null, c.alarmScheduler.upcoming.value)

        c.backupManager.restore(file)
        planned(millis(day.atTime(6, 0)))
        val next = c.alarmScheduler.upcoming.value
        assertNotNull("복원 뒤 알람이 다시 잡혀요", next)
        assertEquals(millis(day.atTime(6, 0)), next!!.triggerAtMillis)
        val ring = RingRequest.from(next.plan, next.triggerAtMillis)
        assertEquals(old, ring.soundUri)
        // The old phone's file first, then this phone's alarm sound and the system's: something always plays.
        val phone = Uri.parse("content://settings/system/alarm_alert_new_phone")
        val tried = AlarmService.soundCandidates(ring.soundUri, phone)
        assertEquals(Uri.parse(old), tried.first())
        assertEquals(phone, tried[1])
        assertTrue(Settings.System.DEFAULT_ALARM_ALERT_URI in tried)
        assertTrue(AlarmService.soundCandidates(null, null).isNotEmpty())
    }

    /** W19: 근무 시간을 고치면 출근 전 알람과 퇴근 시각 알림이 새 시간을 따라가요. */
    @Test fun noticesFollowAChangedShift() = runBlocking {
        at(day.atTime(5, 0))
        val repo = c.scheduleRepository
        val type = ShiftType(0, "고칠 주간", "고", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0), alarmEnabled = true, alarms = listOf(AlarmSpec(30)))
        val id = repo.saveShiftType(type)
        repo.setOverride(day, ShiftOverride.Shift(id))
        c.settingsRepository.update { it.copy(alarmsEnabled = true) }
        assertEquals(millis(day.atTime(6, 30)), planned(millis(day.atTime(6, 30))))
        assertEquals(day.atTime(16, 0), LeaveTimeNotice.next(repo.snapshot.first().resolver, day.atTime(5, 0)))

        // 2시 출근으로 바꾸면 30분 전 알람은 1시 30분, 퇴근 알림은 새 끝 시각.
        repo.saveShiftType(type.copy(id = id, start = LocalTime.of(14, 0), end = LocalTime.of(23, 0)))
        assertEquals(millis(day.atTime(13, 30)), planned(millis(day.atTime(13, 30))))
        assertEquals(day.atTime(23, 0), LeaveTimeNotice.next(repo.snapshot.first().resolver, day.atTime(5, 0)))
    }

    /** C12: 이번 판에 생긴 설정이 새 휴대폰에서 그대로: 희망 오프, 선택근무·소득 종류, 연속 근무 한도, 퇴근 알림, 콜 수수료·공수 버튼·규칙. */
    @Test fun thisRoundsSettingsComeBackOnANewPhone() = runBlocking {
        at(day.atTime(9, 0))
        c.wishOff.toggle(PersonalCalendar.MAIN, day.plusDays(10))
        c.wishOff.setDeadline(PersonalCalendar.MAIN, 20)
        WorkTypePrefs.get(app).setSelective(PersonalCalendar.MAIN, 3, 2)
        WorkTypePrefs.get(app).setIncomeType(PersonalCalendar.MAIN, "BUSINESS")
        c.settingsRepository.update { it.copy(stretchLimitHours = 24, leaveTimeNotice = true) }
        val rules = FieldRules(mode = FieldMode.DAY_RATE, dayRate = 250_000, platformCosts = mapOf("로지" to PlatformWork.Cut(200, 1_000)),
            unitChips = listOf(1.0, 1.25), unitRules = DayRate.SAMPLE_RULES)
        c.calendars.updateField(PersonalCalendar.MAIN) { rules }
        val file = c.backupManager.export()

        // A new phone.
        prefs("wish_off").edit().clear().commit()
        c.wishOff.reload()
        prefs("work_type_prefs").edit().clear().commit()
        WorkTypePrefs.get(app).reload()
        c.settingsRepository.update { AppSettings() }
        c.calendars.updateField(PersonalCalendar.MAIN) { FieldRules() }

        c.backupManager.restore(file)
        val wish = c.wishOff.all.value[PersonalCalendar.MAIN]
        assertTrue(wish?.has(day.plusDays(10)) == true)
        assertEquals(20, wish?.deadlineDay)
        val choices = WorkTypePrefs.get(app).of(PersonalCalendar.MAIN)
        assertEquals(3, choices.selectiveMonths)
        assertEquals("BUSINESS", choices.incomeType)
        assertEquals(24, c.settingsRepository.current.stretchLimitHours)
        assertEquals(true, c.settingsRepository.current.leaveTimeNotice)
        val back = c.calendars.get(PersonalCalendar.MAIN)?.field
        assertEquals(rules.platformCosts, back?.platformCosts)
        assertEquals(rules.unitChips, back?.unitChips)
        assertEquals(rules.unitRules, back?.unitRules)
        c.calendars.updateField(PersonalCalendar.MAIN) { FieldRules() }
    }
}
