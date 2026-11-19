package com.bamnat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 2.0.0 [RVIB-15] (PC-123, 디자인 품질 12) The night screen, and the sleep page in its night look, are dark on a light phone: the
 * system's three buttons kept the light app's pale scrim, a bright band under the dark screen. While a screen here [Hold]s, the
 * main window's bars are drawn as on a dark app, with no scrim of their own (the screen's ground shows under the buttons, the
 * icons light); leaving it gives them back to the app's own look. The alarm screen, always dark, sets the same in its window.
 */
object DarkSystemBars {
    /** How many screens on now ask for dark system bars ([com.bamnat.app.MainActivity] follows it). */
    var held by mutableIntStateOf(0)
        private set

    /** Dark system bars while this is in the composition and [on]. */
    @Composable
    fun Hold(on: Boolean = true) {
        DisposableEffect(on) {
            if (on) held++
            onDispose { if (on) held-- }
        }
    }
}
