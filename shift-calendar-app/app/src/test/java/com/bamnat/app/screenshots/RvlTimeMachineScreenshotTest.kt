package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.TimeMachine
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.app.ui.settings.TIME_MACHINE_ROW
import com.bamnat.app.ui.settings.TimeMachineDialog
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [RVL-76] (검토 I12b-02) 갈래 L 11차의 화면 (디자인 품질 조건): 설정 › 데이터 › 날짜별 되돌리기 — 근무·가계부의 7일 안 모습을 날마다
 * 시각으로 보여 주고, 하나를 누르면 무엇이 바뀌는지 말한 뒤 한 번 물어요. 밝은·어두운 × 글자 100%·200% × 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlTimeMachineScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun stop() = scope.cancel()

    /** A machine over the main calendar with three moments: last night, and two this morning before the days were cleared. */
    private fun moments(): TimeMachine = runBlocking {
        var now = 0L
        val dir = File(app.cacheDir, "time_machine_shot").apply { deleteRecursively() }
        val machine = TimeMachine(dir, scope, clock = { now }, settleMillis = 60 * 60_000L, firstDelayMillis = 60 * 60_000L)
        machine.watch(PersonalCalendar.MAIN, c.database)
        val repo = c.scheduleRepository
        val type = repo.shiftTypes.first().first { !it.isOff }.id
        val today = Today.date()
        val base = Today.millis()
        now = base - 12 * 60 * 60_000L // 어제 22:30
        machine.keep(PersonalCalendar.MAIN, TimeMachine.Part.DAYS)
        (2L until 6L).forEach { repo.setOverride(today.plusDays(it), ShiftOverride.Shift(type)) }
        now = base - 110 * 60_000L // 오늘 08:40
        machine.keep(PersonalCalendar.MAIN, TimeMachine.Part.DAYS)
        repo.setOverride(today.plusDays(8), ShiftOverride.Shift(type))
        now = base - 35 * 60_000L // 오늘 09:55
        machine.keep(PersonalCalendar.MAIN, TimeMachine.Part.DAYS)
        // The mistake: the days put in this morning cleared.
        (2L until 9L).forEach { repo.resetDay(today.plusDays(it)) }
        now = base - 5 * 60_000L
        machine.keep(PersonalCalendar.MAIN, TimeMachine.Part.DAYS)
        assertEquals(3, machine.points(PersonalCalendar.MAIN, TimeMachine.Part.DAYS).size)
        machine
    }

    private fun list(): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        val machine = moments()
        val calendars = listOf(
            PersonalCalendar(PersonalCalendar.MAIN, PersonalCalendar.MAIN_NAME, PersonalCalendar.COLORS[0]),
            PersonalCalendar("side", "주말 알바", PersonalCalendar.COLORS[1], style = WorkStyle.HOURLY),
        )
        content { TimeMachineDialog(machine, calendars, PersonalCalendar.MAIN, onDone = { _, _ -> }, onDismiss = {}) }
        settle()
        assertTrue(appears("오늘"))
        assertTrue(appears("어제"))
    }

    @Test fun listFoldLight() = shoot("rvl76_list_fold_light_100", FOLDED, list())
    @Test fun listFoldDarkHuge() = shoot("rvl76_list_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, list())
    @Test fun listOpenLight() = shoot("rvl76_list_open_light_100", UNFOLDED, list())
    @Test fun listOpenDark() = shoot("rvl76_list_open_dark_100", UNFOLDED_NIGHT, list())

    /** The moment of 08:40 tapped: what going back would change, asked once. */
    private fun ask(): Screen.() -> Unit = {
        list().invoke(this)
        tapText("08:40 모습", prefix = true)
        settle()
        assertTrue(appears("되돌릴까요?"))
        assertTrue(appears("그때대로 돌아가요"))
    }

    @Test fun askFoldLight() = shoot("rvl76_ask_fold_light_100", FOLDED, ask())
    @Test fun askFoldDarkHuge() = shoot("rvl76_ask_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, ask())

    /** 가계부 with no moment yet: the empty state says what will be kept. */
    private fun empty(): Screen.() -> Unit = {
        list().invoke(this)
        tapText("가계부", exact = true)
        settle()
        assertTrue(appears("아직 돌아갈 때가 없어요"))
    }

    @Test fun emptyFoldLight() = shoot("rvl76_empty_fold_light_100", FOLDED, empty())
    @Test fun emptyFoldDarkHuge() = shoot("rvl76_empty_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, empty())

    /** 설정 › 데이터: the row under 최근 지운 기록, and a tap on it opens the list (no moment yet on a new phone). */
    private fun row(wide: Boolean = false, open: Boolean = false): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        settingsPage(SettingsPage.DATA.titleFor(WorkStyle.SHIFT), wide)
        reveal(TIME_MACHINE_ROW, exact = true, across = if (wide) 0.75f else 0.5f)
        assertTrue(appears(TIME_MACHINE_ROW, exact = true))
        assertTrue(appears("최근 지운 기록", exact = true))
        if (open) {
            tapText(TIME_MACHINE_ROW, exact = true, across = if (wide) 0.75f else 0.5f)
            settle()
            assertTrue(appears("실수로 바꾼 근무나 가계부를"))
        }
    }

    @Test fun rowFoldLight() = shoot("rvl76_row_fold_light_100", FOLDED, row())
    @Test fun rowFoldDarkHuge() = shoot("rvl76_row_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, row())
    @Test fun rowOpenLight() = shoot("rvl76_row_open_light_100", UNFOLDED, row(wide = true))
    @Test fun rowTappedFoldLight() = shoot("rvl76_row_tapped_fold_light_100", FOLDED, row(open = true))

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
