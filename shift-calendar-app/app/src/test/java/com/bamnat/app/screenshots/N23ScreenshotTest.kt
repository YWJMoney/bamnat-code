package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.NetTimings
import com.bamnat.app.ui.diag.SpeedCheck
import com.bamnat.app.ui.diag.SpeedCheckCard
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-23] (PC-187) 자가 점검 › 조회 속도's 연결 방식 line, the one line of the change that shows: the phone's own engine, and
 * a phone before Android 14 told why. Light and dark, text 100% and 200%, the Fold folded and open. Shots 2300~2307; HQ-126 an
 * Android 12·13 phone waiting for the system update, folded at 200% (2308, 2309).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class N23ScreenshotTest : ScreenshotBase() {
    private fun card(line: String): Screen.() -> Unit = {
        NetTimings.clear()
        listOf(120L, 180L, 240L, 2_900L).forEach { NetTimings.record("GET", "/api/board", it, NetTimings.Result.OK) }
        NetTimings.record("GET", "/api/terms", 6_000, NetTimings.Result.OFFLINE)
        val s = NetTimings.summary()
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) { SpeedCheckCard(s, line) }
        }
        settle()
        assertTrue(appears(line, exact = true))
        NetTimings.clear()
    }

    private val fast = SpeedCheck.way(fast = true, available = true)
    private val before14 = SpeedCheck.way(fast = false, available = false, sdk = 30)
    /** HQ-126 An Android 12·13 phone still waiting for the system update that brings the engine. */
    private val beforeUpdate = SpeedCheck.way(fast = false, available = false, sdk = 32)

    @Test fun foldedLight() = shoot("2300_n23_way_folded", FOLDED, card(fast))
    @Test fun foldedLightLarge() = shoot("2301_n23_way_folded_200", FOLDED, 2f, card(fast))
    @Test fun foldedDark() = shoot("2302_n23_way_folded_night", FOLDED.replace("-port-", "-port-night-"), card(fast))
    @Test fun foldedDarkLarge() = shoot("2303_n23_way_folded_night_200", FOLDED.replace("-port-", "-port-night-"), 2f, card(before14))
    @Test fun openLight() = shoot("2304_n23_way_open", UNFOLDED, card(fast))
    @Test fun openLightLarge() = shoot("2305_n23_way_open_200", UNFOLDED, 2f, card(before14))
    @Test fun openDark() = shoot("2306_n23_way_open_night", UNFOLDED.replace("-port-", "-port-night-"), card(fast))
    @Test fun openDarkLarge() = shoot("2307_n23_way_open_night_200", UNFOLDED.replace("-port-", "-port-night-"), 2f, card(fast))
    @Test fun twelveFoldedLarge() = shoot("2308_n23_way_12_folded_200", FOLDED, 2f, card(beforeUpdate))
    @Test fun twelveFoldedDarkLarge() = shoot("2309_n23_way_12_folded_night_200", FOLDED.replace("-port-", "-port-night-"), 2f, card(beforeUpdate))
}
