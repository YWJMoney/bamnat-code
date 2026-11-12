package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-72] (검토 T07b-01·T07b-02·I07bb-02) 갈래 L 10차의 화면 (디자인 품질 조건): 알바 시간표를 붙여 넣으면 '어느 줄이 내 것인가요?'에 그 사람의
 * 일한 날과 시간이, 고른 뒤 달력에 시간이 보이고, 한 칸을 고치는 창은 시간·공수부터. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlTimeSheetScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private val sheet = "2026년 11월 알바 시간표\n이름 1 2 3 4 5 6 7 8 9 10\n" +
        "김알바 09:00-15:00 - 9-3 - 18:00-23:00 휴무 - 10-16 - 9-3\n" +
        "박알바 - 10-16 - 10-16 - - 6h - 18-22 -"

    private fun pasted(style: WorkStyle, text: String, then: Screen.(DutyRosterViewModel) -> Unit): Screen.() -> Unit = style(style) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readText(text)
        waitFor("the sheet read") { vm.ui.value.rows != null }
        then(vm)
        settle()
    }

    /** Two people's lines, each with the days they work and the times. */
    private fun whose(): Screen.() -> Unit = pasted(WorkStyle.HOURLY, sheet) { _ ->
        assertTrue(appears("어느 줄이 내 것인가요?"))
        assertTrue(appears("1일 9–15"))
    }

    @Test fun whoseFoldLight() = shoot("rvl72_whose_fold_light_100", FOLDED, whose())
    @Test fun whoseFoldDarkHuge() = shoot("rvl72_whose_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, whose())
    @Test fun whoseOpenLight() = shoot("rvl72_whose_open_light_100", UNFOLDED, whose())

    /** 김알바's line picked: the month with the days worked at their times, the days between not marked as missed. */
    private fun picked(): Screen.() -> Unit = pasted(WorkStyle.HOURLY, sheet) { vm ->
        vm.pick(vm.ui.value.rows!!.indexOfFirst { it.label == "김알바" })
        settle()
        assertTrue(appears("‘김알바’ 줄을 읽었어요"))
        assertTrue(vm.ui.value.missing.isEmpty() && vm.ui.value.doubtful.isEmpty())
    }

    @Test fun pickedFoldLight() = shoot("rvl72_picked_fold_light_100", FOLDED, picked())
    @Test fun pickedFoldDarkHuge() = shoot("rvl72_picked_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, picked())
    @Test fun pickedOpenLight() = shoot("rvl72_picked_open_light_100", UNFOLDED, picked())

    /** One day of the 시간표 fixed: its times first, 쉬는 날, the kinds after; no 연차 keys on an 알바's sheet. */
    private fun fixTimes(): Screen.() -> Unit = style(WorkStyle.HOURLY) {
        val types = runBlocking { c.scheduleRepository.shiftTypes.first() }
        content {
            com.bamnat.app.ui.imports.CellDialog(nov.atDay(3), types, current = DutyCell.Hours(9 * 60, 360), timed = true, onDismiss = {}) { }
        }
        settle()
        assertTrue(appears("9–15 고치기"))
        assertTrue(!shows("연차", exact = true))
    }

    @Test fun fixTimesFoldLight() = shoot("rvl72_fix_times_fold_light_100", FOLDED, fixTimes())
    @Test fun fixTimesFoldDarkHuge() = shoot("rvl72_fix_times_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, fixTimes())

    /** A 현장 공수표: the day's 공수 to pick. */
    private fun fixUnits(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        val types = runBlocking { c.scheduleRepository.shiftTypes.first() }
        content {
            com.bamnat.app.ui.imports.CellDialog(nov.atDay(3), types, current = DutyCell.Units(0.5), timed = true, onDismiss = {}) { }
        }
        settle()
        assertTrue(appears("1.5공"))
    }

    @Test fun fixUnitsFoldLight() = shoot("rvl72_fix_units_fold_light_100", FOLDED, fixUnits())
    @Test fun fixUnitsOpenDark() = shoot("rvl72_fix_units_open_dark_100", UNFOLDED_NIGHT, fixUnits())

    /** A 공수표 pasted on a 현장 calendar: the line's days with their 공수. */
    private fun units(): Screen.() -> Unit = pasted(WorkStyle.FIELD, "11월 공수\n성명 1 2 3 4 5 6 7 8\n홍길동 1 1 0.5 - 1.5 1 - 1") { _ ->
        assertTrue(appears("‘홍길동’ 줄을 읽었어요"))
    }

    @Test fun unitsFoldLight() = shoot("rvl72_units_fold_light_100", FOLDED, units())
    @Test fun unitsFoldDarkHuge() = shoot("rvl72_units_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, units())

    /** 2.0.0 [RVL-72] 내 근무 of an 알바·현장 calendar has its own row to 한 달 근무 넣기 (only 교대 had one). */
    private fun settingsRow(s: WorkStyle, row: String, wide: Boolean = false): Screen.() -> Unit = style(s) {
        settingsPage(com.bamnat.app.ui.settings.SettingsPage.WORK.titleFor(s), wide)
        reveal(row, exact = true, across = if (wide) 0.75f else 0.5f)
        assertTrue(appears(row, exact = true))
    }

    @Test fun hourlyRowFoldLight() = shoot("rvl72_row_hourly_fold_light_100", FOLDED,
        settingsRow(WorkStyle.HOURLY, com.bamnat.app.ui.settings.HOURLY_ROSTER_ROW))
    @Test fun fieldRowFoldDarkHuge() = shoot("rvl72_row_field_fold_dark_200", FOLDED_NIGHT, fontScale = 2f,
        settingsRow(WorkStyle.FIELD, com.bamnat.app.ui.settings.FIELD_ROSTER_ROW))
    @Test fun hourlyRowOpenLight() = shoot("rvl72_row_hourly_open_light_100", UNFOLDED,
        settingsRow(WorkStyle.HOURLY, com.bamnat.app.ui.settings.HOURLY_ROSTER_ROW, wide = true))

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
