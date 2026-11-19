package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ReadFixBook
import com.bamnat.app.imports.RosterPhotoTest
import com.bamnat.app.imports.RosterScanner
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.PhotoQuality
import com.bamnat.core.imports.RosterForm
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.work.WorkStyle
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-80][RVL-81] 갈래 L 14차의 화면 (디자인 품질 조건): 찍기 전 한 줄 요령, 찍은 직후 '조금 흐려요' 카드(다시 찍기 · X 이대로 쓰기),
 * 이름이 한 글자 다르게 읽힌 다음 달 근무표가 묻지 않고 내 줄로 열린 모습. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlRosterFormScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    @After fun noFake() {
        RosterScanner.fake = null
        RosterScanner.fakeImage = null
        RosterScanner.fakeQuality = null
    }

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private fun picture(words: List<RosterWord>): Bitmap {
        val bmp = Bitmap.createBitmap(words.maxOf { it.right } + 60, words.maxOf { it.bottom } + 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
        words.forEach { canvas.drawText(it.text, it.left.toFloat(), it.bottom - 8f, paint) }
        return bmp
    }

    private fun opened(then: Screen.(DutyRosterViewModel) -> Unit): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        then(vm)
        settle()
    }

    // ---- RVL-81: the tip before the first photo ----
    private fun tip(): Screen.() -> Unit = opened { vm ->
        waitFor("the tip known") { vm.tip.value }
        assertTrue(appears(com.bamnat.app.ui.imports.SHOT_TIP, exact = true))
    }

    @Test fun tipFoldLight() = shoot("rvl81_tip_fold_light_100", FOLDED, tip())
    @Test fun tipFoldDarkHuge() = shoot("rvl81_tip_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, tip())
    @Test fun tipOpenLight() = shoot("rvl81_tip_open_light_100", UNFOLDED, tip())

    // ---- RVL-81: right after a blurred shot ----
    private fun check(): Screen.() -> Unit = opened { vm -> blurred(vm) }

    private fun Screen.blurred(vm: DutyRosterViewModel) {
        val words = RosterPhotoTest.roster(2.0)
        RosterScanner.fake = { words }
        RosterScanner.fakeImage = { picture(words) }
        RosterScanner.fakeQuality = PhotoQuality.Report(40, listOf(PhotoQuality.Issue.BLURRY))
        vm.readPhoto(Uri.parse("content://test/roster.jpg"), taken = true)
        waitFor("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        vm.pick(1)
        settle()
        assertTrue(appears("조금 흐려요. 다시 찍으면 더 잘 읽어요"))
        assertTrue(appears("다시 찍기", exact = true))
    }

    @Test fun checkFoldLight() = shoot("rvl81_check_fold_light_100", FOLDED, check())
    @Test fun checkFoldDarkHuge() = shoot("rvl81_check_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, check())
    @Test fun checkOpenLight() = shoot("rvl81_check_open_light_100", UNFOLDED, check())

    /** The card's X (이대로 쓰기): the card goes, the month read stays. */
    @Test fun keptFoldLight() = shoot("rvl81_kept_fold_light_100", FOLDED, opened { vm ->
        blurred(vm)
        tapText("이대로 쓰기", exact = true)
        waitFor("the card gone") { vm.ui.value.check == null }
        assertTrue(appears("‘이수진’ 줄 · "))
    })

    // ---- RVL-80: next month's sheet, a letter of the name misread ----
    private fun found(): Screen.() -> Unit = opened { vm ->
        c.dutyCodes.rememberMine(PersonalCalendar.MAIN, "이수진")
        ReadFixBook.get(app).learnRosterForm(PersonalCalendar.MAIN, RosterForm(1, 4))
        val words = RosterPhotoTest.roster(-1.0).map { if (it.text == "이수진") it.copy(text = "이수신") else it }
        RosterScanner.fake = { words }
        RosterScanner.fakeImage = { picture(words) }
        vm.readPhoto(Uri.parse("content://test/dec.jpg"))
        waitFor("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        assertTrue(vm.ui.value.chosen)
        assertTrue(appears("‘이수신’ 줄 · "))
    }

    @Test fun foundFoldLight() = shoot("rvl80_found_fold_light_100", FOLDED, found())
    @Test fun foundFoldDarkHuge() = shoot("rvl80_found_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, found())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
