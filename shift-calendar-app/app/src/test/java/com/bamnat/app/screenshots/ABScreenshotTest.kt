package com.bamnat.app.screenshots

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.AlarmButtons
import com.bamnat.app.alarm.AlarmsOff
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.alarm.RingingScreen
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ALRM-07][ALRM-08][ALRM-09] 묶음 AB's screenshots, numbers 960~969: the ringing screen with 오늘 남은 알람 모두 끄기
 * and what the buttons do, on a small phone, a common one, on its side, at 200% and dark (960~964), a mission's line
 * (965), the calendar's 오늘 알람 끔 · 다시 켜기 (966, 969 at 200%), the settings row (967) and 알람 점검's new lines (968).
 * Each checks the screen as drawn: no text cut.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ABScreenshotTest : ScreenshotBase() {
    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    @After fun quiet() {
        AlarmsOff.turnOn(app)
        Notifications.createChannels(app)
    }

    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report("ab_$name", cuts)
        assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    private fun Screen.ringing(name: String) {
        content {
            RingingScreen(
                title = "주간 근무", subtitle = "07:00 근무 시작 · 60분 전 알람", snoozeMinutes = 5,
                onSnooze = {}, onDismiss = {}, onAllOff = {}, hint = AlarmButtons.hint(AlarmButtons.Action.DISMISS, mission = false),
            )
        }
        settle()
        assertTrue(appears("오늘 남은 알람 모두 끄기"))
        assertTrue(appears("소리·전원 버튼을 눌러도 꺼져요"))
        noCuts(name)
    }

    @Test fun ringing320() = shoot("960_ab_ringing_320", SMALL_PHONE) { ringing("ringing_320") }
    @Test fun ringing393() = shoot("961_ab_ringing_393", PHONE_393) { ringing("ringing_393") }
    @Test fun ringingSideways() = shoot("962_ab_ringing_landscape", PHONE_LANDSCAPE) { ringing("ringing_landscape") }
    @Test fun ringing200() = shoot("963_ab_ringing_200", PHONE_393, 2f) { ringing("ringing_200") }
    @Test fun ringingDark() = shoot("964_ab_ringing_dark", PHONE_393_NIGHT) { ringing("ringing_dark") }

    @Test fun missionLine() = shoot("965_ab_mission_note", PHONE_393) {
        content {
            com.bamnat.app.alarm.MissionScreen(AlarmMission.MATH, Today.millis(), 5, onSnooze = {}, onDone = {},
                note = com.bamnat.app.alarm.missionNote(AlarmButtons.Action.DISMISS, quiet = false, allOffWaits = true))
        }
        settle()
        assertTrue(appears("소리·전원 버튼은 소리만 30초 줄여요"))
        assertTrue(appears("미션을 마치면 오늘 남은 알람이 모두 꺼져요"))
        noCuts("mission_note")
    }

    private fun Screen.offToday(name: String) {
        // The calendar follows it as it changes.
        AlarmsOff.turnOff(app, null)
        settle()
        assertTrue(appears("오늘 알람 끔 · 밤 12시까지"))
        assertTrue(appears("다시 켜기", exact = true))
        noCuts(name)
    }

    @Test fun calendarOffToday() = shoot("966_ab_calendar_off_today", PHONE_393) { offToday("calendar_off") }
    @Test fun calendarOffToday200() = shoot("969_ab_calendar_off_today_320_200", SMALL_PHONE, 2f) { offToday("calendar_off_200") }

    @Test fun settingsRow() = shoot("967_ab_settings_buttons", PHONE_393) {
        settingsPage("알람")
        reveal("버튼으로 알람 끄기", exact = true)
        assertTrue(appears("아무것도 안 함"))
    }

    @Test fun checkLines() = shoot("968_ab_alarm_check_lines", PHONE_393) {
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(Notifications.ALARM_CHANNEL_ID, "근무 알람", NotificationManager.IMPORTANCE_LOW))
        val vm = com.bamnat.app.ui.diag.DiagnosticsViewModel(app.container)
        content { com.bamnat.app.ui.diag.DiagnosticsScreen(vm, onBack = {}) }
        settle()
        reveal("잠금 화면에 알람 보이기", exact = true)
        assertTrue(appears("조용히로 바뀌어 알람 화면이 안 떠요"))
    }
}
