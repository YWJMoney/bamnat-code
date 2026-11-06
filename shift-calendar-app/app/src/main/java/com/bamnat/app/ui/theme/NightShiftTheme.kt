package com.bamnat.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.ThemeMode
import com.bamnat.core.model.NightShift
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import com.bamnat.core.time.Today

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

    /** Whether [now] is in the night hours the sleep screen goes dark for. */
    fun isNight(now: LocalDateTime, sleeping: Boolean): Boolean = sleeping || now.hour >= 22 || now.hour < 6
}

/**
 * 1.38 [DSG-07] Whether the sleep screen should be in [NightPalette] now: on unless the user turned 밤 색 off, from 22:00 to
 * 06:00 and while a sleep is open. Checked once a minute, on the minute.
 */
@Composable
fun rememberNightMode(enabled: Boolean, sleeping: Boolean): Boolean {
    if (!enabled) return false
    val now by produceState(Today.dateTime()) {
        while (true) {
            delay(60_000L - Today.millis() % 60_000L)
            value = Today.dateTime()
        }
    }
    return NightPalette.isNight(now, sleeping)
}

/** The sleep screen's content in [NightPalette] when [night]; the app's own colours otherwise. */
@Composable
fun NightSurface(night: Boolean, content: @Composable () -> Unit) {
    if (!night) return content()
    androidx.compose.material3.MaterialTheme(colorScheme = NightPalette.scheme, typography = androidx.compose.material3.MaterialTheme.typography,
        shapes = androidx.compose.material3.MaterialTheme.shapes, content = content)
}
