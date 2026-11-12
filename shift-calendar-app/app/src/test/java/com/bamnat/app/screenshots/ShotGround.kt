package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.theme.BamnatTheme

/**
 * 2.0.0 [QA-12] (검토 HQ-25, 갈래 K #457 k06에서 찾음) The app draws every screen on its Scaffold or Surface, so a word outside a card
 * is onBackground on the background. A screenshot's [ScreenshotBase.Screen.content] put the screen under BamnatTheme alone: no
 * ground, and a word outside a Surface took Compose's default black, which on a dark shot read as missing (a title the app shows
 * fine) or hid one that really is missing. Every shot now stands on the theme's background with onBackground for its words, as in
 * the app.
 *
 * The box fills the window and hands its exact size on (propagateMinConstraints), as the window gives the content without it, so
 * nothing moves: only the colours under and of the words change.
 */
@Composable
internal fun ShotGround(body: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { body() }
    }
}

/** [QA-12] A shot's own theme (light, dark or 밤 색 set by the test), on its own ground: BamnatTheme + [ShotGround]. */
@Composable
internal fun ShotTheme(themeMode: ThemeMode, night: Boolean = false, body: @Composable () -> Unit) {
    BamnatTheme(themeMode = themeMode, night = night) { ShotGround(body) }
}
