package com.bamnat.app.screenshots

import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.recipe.LiveTryOn
import com.bamnat.app.ui.recipe.TryOnScreen
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TRY-05] 1.39 실제 화면으로 입어 보기: 입어 보기의 새 단추, and the real app's tabs with a theme tried on (and 지금 모습) — the 가계부,
 * 근무·급여 and 달력 with the sample records, folded and open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LiveTryOnScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val neon: Recipe get() = BuiltInThemes.of(WorkStyle.SHIFT).first { it.title.contains("네온") }

    @After fun letGo() = LiveTryOn.drop(c)

    private fun Screen.tryOn(tab: String) {
        runBlocking { LiveTryOn.start(c, neon, own = false, LookParts.of(neon).map { it.id }.toSet(), tab, LiveTryOn.From.Detail("ABCD2345")) }
        settle()
        settle()
    }

    @Test fun button() = shoot("1420_try_on_live_button_folded", FOLDED) {
        content { TryOnScreen(neon, WorkStyle.SHIFT, onDismiss = {}, onKeep = { true }, live = { _, _ -> true }) { Text("이 테마 입기") } }
    }
    @Test fun moneyMine() = shoot("1421_try_on_live_money_before_folded", FOLDED) { tab(1, wide = false) }
    @Test fun moneyTried() = shoot("1422_try_on_live_money_folded", FOLDED) { tryOn("money") }
    @Test fun moneyNow() = shoot("1423_try_on_live_money_now_folded", FOLDED) { tryOn("money"); LiveTryOn.show(c, false); settle() }
    @Test fun statsTried() = shoot("1424_try_on_live_stats_folded", FOLDED) { tryOn("stats") }
    @Test fun calendarTriedOpen() = shoot("1425_try_on_live_calendar_unfolded", UNFOLDED) { tryOn("calendar") }
    @Test fun memosTried() = shoot("1426_try_on_live_memos_folded", FOLDED) { tryOn("memos") }
}
