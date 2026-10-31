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
