package com.bamnat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * [VIS-06] 1.25 묶음 Y (U11): one colour family for each kind of settings group, so groups of the same
 * kind look alike and the others look apart: the circle behind the icon ([container]), the icon on it
 * ([content], 4.5:1 on [container]) and the band at the row's left ([band], 3:1 on the page). ContrastTest
 * checks both themes.
 */
@Immutable
data class SettingsTone(val name: String, val container: Color, val content: Color, val band: Color)

/** [VIS-06] The four kinds the seven groups fall into. */
enum class SettingsKind { WORK, ALERT, SCREEN, DATA }

@Immutable
data class SettingsTones(val work: SettingsTone, val alert: SettingsTone, val screen: SettingsTone, val data: SettingsTone) {
    fun of(kind: SettingsKind): SettingsTone = when (kind) {
        SettingsKind.WORK -> work
        SettingsKind.ALERT -> alert
        SettingsKind.SCREEN -> screen
        SettingsKind.DATA -> data
    }
    val all: List<SettingsTone> get() = listOf(work, alert, screen, data)
}

internal val LightSettingsTones = SettingsTones(
    work = SettingsTone("일", Color(0xFFD6E4FF), Color(0xFF1A4FB5), Color(0xFF2F5BEA)),
    alert = SettingsTone("알림", Color(0xFFFFE2CC), Color(0xFF8A3B00), Color(0xFFC25700)),
    screen = SettingsTone("화면", Color(0xFFEADDFF), Color(0xFF5B2DB8), Color(0xFF7048E8)),
    data = SettingsTone("데이터", Color(0xFFD2F0E0), Color(0xFF0B6B45), Color(0xFF13804F)),
)

internal val DarkSettingsTones = SettingsTones(
    work = SettingsTone("일", Color(0xFF203A6E), Color(0xFFC4D6FF), Color(0xFF8AB0FF)),
    alert = SettingsTone("알림", Color(0xFF5A2A00), Color(0xFFFFD2B0), Color(0xFFFFA05C)),
    screen = SettingsTone("화면", Color(0xFF3E2873), Color(0xFFE0D2FF), Color(0xFFB197FC)),
    data = SettingsTone("데이터", Color(0xFF0F4430), Color(0xFFB4EDD0), Color(0xFF5FD3A0)),
)

/** [VIS-06] The tones of the theme on screen (the phone's dynamic colours included, by how dark the page is). */
@Composable
fun settingsTones(): SettingsTones =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkSettingsTones else LightSettingsTones
