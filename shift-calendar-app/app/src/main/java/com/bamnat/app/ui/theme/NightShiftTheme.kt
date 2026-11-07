package com.bamnat.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.ThemeMode
import com.bamnat.core.model.NightShift
import com.bamnat.core.time.Today
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * [SHFT-05] 야간 근무 중 어둡게: dark while the calendar on the calendar tab is in a night shift (one whose
 * hours reach into 22:00~06:00), the phone's theme otherwise. Checked once a minute, on the minute.
 */
@Composable
fun rememberThemeMode(mode: ThemeMode, container: AppContainer): ThemeMode {
    if (mode != ThemeMode.NIGHT_SHIFT) return mode
    val snapshot by container.calendars.activeSnapshot.collectAsStateWithLifecycle(initialValue = null as ScheduleSnapshot?)
    val now by produceState(Today.dateTime()) {
        while (true) {
            delay(60_000L - Today.millis() % 60_000L)
            value = Today.dateTime()
        }
    }
    val night = remember(snapshot, now) { snapshot?.let { NightShift.isOn(now, it.resolver::resolve) } ?: false }
    return if (night) ThemeMode.DARK else ThemeMode.SYSTEM
}

/**
 * [STU-37] 1.36 근무에 따라 바뀌는 모양: which look the day's work calls for now (야간 근무 중, 쉬는 날, 평소), checked once a minute on the
 * calendar on the calendar tab; always 평소 when no such look is set, so nothing is read for nothing.
 */
@Composable
fun rememberShiftLookMode(looks: com.bamnat.core.recipe.ShiftLooks, container: AppContainer): com.bamnat.core.recipe.ShiftLooks.Mode {
    if (looks.isDefault) return com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL
    val snapshot by container.calendars.activeSnapshot.collectAsStateWithLifecycle(initialValue = null as ScheduleSnapshot?)
    val now by produceState(Today.dateTime()) {
        while (true) {
            delay(60_000L - Today.millis() % 60_000L)
            value = Today.dateTime()
        }
    }
    return remember(snapshot, now) {
        snapshot?.let { com.bamnat.core.schedule.ShiftLookMode.at(now, it.resolver::resolve) } ?: com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL
    }
}

/**
 * 1.38 [DSG-07][SLEEP-18] 밤 색: dark and warm, little blue, for the sleep screen at night and the night screen (FLOW-13).
 * Text keeps 4.5:1 on the surface (ContrastTest); the reds are soft so nothing glares in a dark room.
 */
object NightPalette {
    val scheme: androidx.compose.material3.ColorScheme = androidx.compose.material3.darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFFFB38A),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF3A1600),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF4A2A1C),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDBC9),
        secondary = androidx.compose.ui.graphics.Color(0xFFE0B9A4),
        onSecondary = androidx.compose.ui.graphics.Color(0xFF2E1A10),
        background = androidx.compose.ui.graphics.Color(0xFF120C0A),
        onBackground = androidx.compose.ui.graphics.Color(0xFFEAD9D0),
        surface = androidx.compose.ui.graphics.Color(0xFF120C0A),
        onSurface = androidx.compose.ui.graphics.Color(0xFFEAD9D0),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A201B),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC9B4A9),
        surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1C1411),
        surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF261B17),
        surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF30241F),
        surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF17100D),
        outline = androidx.compose.ui.graphics.Color(0xFF8F7A70),
        outlineVariant = androidx.compose.ui.graphics.Color(0xFF4A3B34),
    )

    /** Whether [now] is in the night hours the sleep screen goes dark for (1.39: [com.bamnat.core.design.NightRule.screen]). */
    fun isNight(now: LocalDateTime, sleeping: Boolean): Boolean =
        com.bamnat.core.design.NightRule.screen(com.bamnat.core.design.NightLook.SLEEP_TIME, now, sleeping, systemDark = false)
}

/** The time now, again on every minute, for the night rules. */
@Composable
private fun rememberMinute(): LocalDateTime {
    val now by produceState(Today.dateTime()) {
        while (true) {
            delay(60_000L - Today.millis() % 60_000L)
            value = Today.dateTime()
        }
    }
    return now
}

/**
 * 1.38 [DSG-07] 1.39 [FLOW-14] Whether the sleep screen and the night screen should be in [NightPalette] now
 * ([com.bamnat.core.design.NightRule.screen]): from 22:00 to 06:00 and while a sleep is open, unless 밤 색 is 끔.
 */
@Composable
fun rememberNightMode(look: String, sleeping: Boolean): Boolean {
    val l = com.bamnat.core.design.NightLook.of(look)
    if (l == com.bamnat.core.design.NightLook.OFF) return false
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val now = rememberMinute()
    return com.bamnat.core.design.NightRule.screen(l, now, sleeping, dark)
}

/**
 * 1.39 [FLOW-14][DSG-07] 시간에 맞춘 자동 밤 모드 for the whole app ([com.bamnat.core.design.NightRule.app]): while a sleep is open,
 * inside the sleep planned around the calendar's work (the same plan as 오늘의 흐름) and at 01~05 (a night shift's dawn, or bed).
 * Read once a minute; nothing is read at all while 밤 색 is 끔.
 */
@Composable
fun rememberAppNight(settings: com.bamnat.app.data.AppSettings, container: AppContainer): Boolean {
    val look = com.bamnat.core.design.NightLook.of(settings.nightLook)
    if (look == com.bamnat.core.design.NightLook.OFF) return false
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    if (look == com.bamnat.core.design.NightLook.PHONE) return dark
    val now = rememberMinute()
    val snapshot by container.calendars.activeSnapshot.collectAsStateWithLifecycle(initialValue = null as ScheduleSnapshot?)
    val log by androidx.compose.runtime.remember(container) { container.sleepRepository.recent(days = 2) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sleeping = log.any { it.end == null }
    val today = now.toLocalDate()
    val planned = remember(snapshot, today, settings.sleepTargetMinutes, settings.commuteMinutes, settings.alarmsEnabled) {
        snapshot?.let { snap ->
            runCatching {
                com.bamnat.core.sleep.SleepSchedule.of(snap.resolver, today.minusDays(1), today, settings.alarmsEnabled,
                    settings.sleepTargetMinutes, settings.commuteMinutes).sleeps.map { it.from..it.to }
            }.getOrNull()
        }.orEmpty()
    }
    return com.bamnat.core.design.NightRule.app(look, now, sleeping, planned, dark)
}

/** The sleep screen's content in [NightPalette] when [night]; the app's own colours otherwise. */
@Composable
fun NightSurface(night: Boolean, content: @Composable () -> Unit) {
    NightBars(night, androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f)
    if (!night) return content()
    androidx.compose.material3.MaterialTheme(colorScheme = NightPalette.scheme, typography = androidx.compose.material3.MaterialTheme.typography,
        shapes = androidx.compose.material3.MaterialTheme.shapes, content = content)
}

/**
 * 1.39 (PC 5c4ad849: 밤 색일 때 상태 표시줄 글자가 어두운 바탕에서 안 보여요): while a screen is drawn in [NightPalette] the status and
 * navigation bar icons are light; leaving it (or the night ending) gives them back to the app's own ground ([appLight]), not to
 * what they were when it opened (the app may have turned to 밤 색 meanwhile).
 */
@Composable
private fun NightBars(night: Boolean, appLight: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    val restore = androidx.compose.runtime.rememberUpdatedState(appLight)
    fun set(light: Boolean) {
        var c = view.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    // Every frame while night, so a screen skin drawn around it does not put dark icons back.
    if (night) androidx.compose.runtime.SideEffect { set(false) }
    androidx.compose.runtime.DisposableEffect(night) {
        onDispose { if (night) set(restore.value) }
    }
}
