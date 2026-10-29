package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.33 [SHR-20][J133-77]: the share sheet's 누구에게 (동료·가족·친구), the card a coworker's link with the company setup shows,
 * and settings in a shop's and a part-timer's words.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class J133ScreenshotTest : ScreenshotBase() {
    @Test fun foldedShareCoworker() = shoot("j133_01_folded_share_coworker", FOLDED) {
        val c = receivedOn()
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = shares, onOpenReceived = {}) }
        tapText("같", prefix = true)
        reveal("보여 줄 것")
    }
    @Test fun foldedShareFamily() = shoot("j133_02_folded_share_family", FOLDED) {
        val c = receivedOn()
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = shares, onOpenReceived = {}) }
        tapText("가족", prefix = true)
        reveal("보여 줄 것")
    }
    @Test fun foldedCoworkerSetupCard() = shoot("j133_03_folded_coworker_setup", FOLDED) {
        val file = coworkerFile()
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.bamnat.app.ui.calendar.CoworkerSetupCard("민지", file, onBefore = { true }, onDone = {})
                com.bamnat.app.ui.calendar.FriendStart()
            }
        }
    }
    @Test fun foldedSettingsShop() = shoot("j133_04_folded_settings_shop", FOLDED, style(WorkStyle.BUSINESS) { tab(4, wide = false) })
    @Test fun foldedSettingsHourly() = shoot("j133_05_folded_settings_hourly", FOLDED, style(WorkStyle.HOURLY) { tab(4, wide = false) })
    @Test fun foldedHelpField() = shoot("j133_06_folded_help_field", FOLDED, style(WorkStyle.FIELD) {
        tab(4, wide = false)
        tapText("도움말과 문의", exact = true)
    })
}
