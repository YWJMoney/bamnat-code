package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K [RVK-58] (PC-192): '돈 적기' opened at 00:45 in last night's 야간 (20:00–08:00) starts on that night's day, by the
 * 근무 기준 하루 (1.39 FLOW-30, core DayBoundary, DayBoundaryDialTest.aMomentInLastNightsShiftIsThatNights). It was right but said
 * nothing, and on an unfolded phone the date row was under the keyboard: the sheet's top line now says the day and why. After the
 * night (08:30) it is today, with no line. On a day off, before 하루 시작 04:00 it is yesterday's, said too. Today is 2026-10-14.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkPc192ScreenshotTest : ScreenshotBase() {
    private fun entry(hour: Int, minute: Int, yesterday: String, wide: Boolean = false, check: Screen.() -> Unit): Screen.() -> Unit =
        style(WorkStyle.SHIFT) {
            // The base class puts its own hour back for every test, so the hour is set here.
            Today.fix(java.time.ZonedDateTime.of(2026, 10, 14, hour, minute, 0, 0, java.time.ZoneId.of("Asia/Seoul")))
            val repo = app.container.scheduleRepository
            runBlocking {
                val types = repo.shiftTypes.first().associateBy { it.name }
                repo.setOverride(today.minusDays(1), ShiftOverride.Shift(types.getValue(yesterday).id))
                repo.setOverride(today, ShiftOverride.Shift(types.getValue("휴무").id))
            }
            ledger()
            settle()
            tab(1, wide)
            tapText("돈 적기", exact = true, scrolls = 0)
            settle()
            check()
        }

    private val night: Screen.() -> Unit = { assertTrue(shows("10월 13일 (화)에 적어요", exact = true) && shows("야간 근무 시간이에요", exact = true)) }

    @Test fun nightFolded() = shoot("pc192_01_entry_in_night_0045_folded", FOLDED, entry(0, 45, "야간", check = night))
    @Test fun nightFoldedDarkBig() = shoot("pc192_02_entry_in_night_0045_folded_dark_200", DARK, 2f, entry(0, 45, "야간", check = night))
    @Test fun nightUnfolded() = shoot("pc192_03_entry_in_night_0630_unfolded", UNFOLDED, entry(6, 30, "야간", wide = true, check = night))
    @Test fun afterNight() = shoot("pc192_04_entry_after_night_0830_folded", FOLDED, entry(8, 30, "야간") {
        assertFalse("밤 근무가 끝나면 오늘이라 줄이 없어요", shows("에 적어요", exact = false))
    })
    @Test fun smallHoursDayOff() = shoot("pc192_05_entry_0200_day_off_folded", FOLDED, entry(2, 0, "휴무") {
        assertTrue(shows("10월 13일 (화)에 적어요", exact = true) && shows("하루 시작 04:00 전이에요", exact = true))
    })

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
