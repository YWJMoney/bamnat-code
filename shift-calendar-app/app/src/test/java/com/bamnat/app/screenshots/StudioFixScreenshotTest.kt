package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.settings.LabeledSlider
import com.bamnat.app.ui.theme.BamnatTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVP-62] (검토 REST-03·U14-06) 스튜디오's −/＋ held to the end: it stops there and the value is kept when the finger lifts.
 * Before, reaching 16dp turned the button off mid-hold, the repeat ran on until the screen closed and nothing was kept. Number 2715.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StudioFixScreenshotTest : ScreenshotBase() {
    @Test fun aHeldPlusStopsAtTheEndAndKeepsIt() = shoot("2715_studio_held_plus_end", FOLDED) {
        var kept by androidx.compose.runtime.mutableFloatStateOf(-1f)
        content {
            BamnatTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    LabeledSlider("모서리", 13f, 0f..16f, "dp") { kept = it }
                }
            }
        }
        settle()
        holdText("모서리 1씩 늘리기", frames = 150)
        check(appears("모서리 16dp", exact = true)) { "the hold did not reach the end" }
        check(kept == 16f) { "the end was not kept when the finger lifted: $kept" }
        tapText("모서리 1씩 줄이기", exact = true)
        check(appears("모서리 15dp", exact = true)) { "− did not go down" }
        check(kept == 15f) { "− was not kept: $kept" }
        waitFor(1500)
        check(shows("모서리 15dp", exact = true)) { "the old repeat kept pressing ＋" }
    }
}
