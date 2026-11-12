package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CalendarStorage
import com.bamnat.app.data.db.DayEntryEntity
import com.bamnat.app.ui.diag.DATA_CHECK_TITLE
import com.bamnat.app.ui.diag.DATA_SELF_CHECK_ROW
import com.bamnat.app.ui.diag.DataCheckScreen
import com.bamnat.app.ui.diag.DiagnosticsViewModel
import com.bamnat.app.ui.guide.JournalScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVL-70][RVL-71] 갈래 L 9차의 화면 (디자인 품질 조건): 설정 › 데이터에 '데이터 점검' 줄(지난번 스스로 살펴본 것을 한 줄로, 검토 U12-09·T12-07),
 * 열면 바로 점검하는 데이터 점검 화면(한 줄 요약, 점검 줄, 스스로 살펴보기 스위치, 서버 연결, 문의), 스스로 고친 것이 '밤낮이 한 일'에 한 줄(검토 I12b-01).
 * 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlDataCheckScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val stray get() = app.getDatabasePath(CalendarStorage.fileName("stray1"))

    @After fun tidy() {
        stray.delete()
        app.getSharedPreferences("data_self_check", 0).edit().clear().commit()
        c.settingsRepository.update { it.copy(dataSelfCheck = true) }
    }

    /** A day set to a kind of work that is gone, put back by the weekly check. */
    private fun selfChecked() = runBlocking {
        c.scheduleRepository.ensureDefaults()
        c.database.dayEntryDao().upsert(DayEntryEntity(
            epochDay = 20_400, overrideMode = DayEntryEntity.MODE_SHIFT, overrideShiftTypeId = 987_654,
            customStartMinute = null, customEndMinute = null, customBreakMinutes = null, alarmSkipped = false,
        ))
        c.dataSelfCheck.runNow()
    }

    /** Unfolded, the settings page is the right part of the screen: it scrolls there. */
    private fun side(wide: Boolean) = if (wide) 0.75f else 0.5f

    // ---- RVL-70: the row in 설정 › 데이터, with what the weekly check did ----
    private fun row(wide: Boolean = false): Screen.() -> Unit = {
        selfChecked()
        settingsPage("데이터", wide)
        reveal(DATA_CHECK_TITLE, exact = true, across = side(wide))
        assertTrue(appears("지운 근무를 가리키던 날 1개를 근무표대로 돌려놓았어요"))
    }

    @Test fun rowFoldLight() = shoot("rvl70_row_fold_light_100", FOLDED, row())
    @Test fun rowFoldDarkHuge() = shoot("rvl70_row_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, row())
    @Test fun rowOpenLight() = shoot("rvl70_row_open_light_100", UNFOLDED, row(wide = true))

    // ---- RVL-70: the screen, checked as it opens ----
    private fun screen(strayFile: Boolean = false, off: Boolean = false, lines: Boolean = false): Screen.() -> Unit = {
        selfChecked()
        if (strayFile) stray.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
        if (off) c.settingsRepository.update { it.copy(dataSelfCheck = false) }
        val vm = DiagnosticsViewModel(c)
        content { DataCheckScreen(vm, onBack = {}) }
        settle()
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (vm.dataCheck.value == null || vm.checking.value) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for the data check" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
        assertTrue(appears(if (strayFile) "기록은 온전해요. 알아 두실 것이 1개 있어요." else "모두 온전해요. 4가지를 살펴봤어요."))
        assertTrue(shows(DATA_SELF_CHECK_ROW, exact = true))
        assertTrue("what is fine folds", gone("✓ 내 달력 데이터베이스: 이상 없음"))
        assertTrue(appears(if (off) "꺼져 있어요." else "오늘 살펴봤어요"))
        if (lines) {
            tapText(if (strayFile) "이상 없는 4가지 보기" else "이상 없는 4가지 보기", exact = true)
            assertTrue(appears("✓ 내 달력 데이터베이스: 이상 없음"))
            assertTrue(appears("접기", exact = true))
        }
    }

    @Test fun screenFoldLight() = shoot("rvl70_screen_fold_light_100", FOLDED, screen())
    @Test fun screenFoldDarkHuge() = shoot("rvl70_screen_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, screen())
    @Test fun screenOpenLight() = shoot("rvl70_screen_open_light_100", UNFOLDED, screen())
    @Test fun screenNoteFoldLight() = shoot("rvl70_screen_note_fold_light_100", FOLDED, screen(strayFile = true))
    @Test fun screenLinesFoldDarkHuge() = shoot("rvl70_screen_lines_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, screen(lines = true))
    @Test fun screenOffFoldLight() = shoot("rvl71_off_fold_light_100", FOLDED, screen(off = true))

    // ---- RVL-71: what it put right, one line in 밤낮이 한 일 ----
    private fun journal(): Screen.() -> Unit = {
        selfChecked()
        content { JournalScreen(onBack = {}) }
        settle()
        assertTrue(appears("데이터 점검 · 지운 근무를 가리키던 날 1개 근무표대로"))
    }

    @Test fun journalFoldLight() = shoot("rvl71_journal_fold_light_100", FOLDED, journal())
    @Test fun journalFoldDarkHuge() = shoot("rvl71_journal_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, journal())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
    }
}
