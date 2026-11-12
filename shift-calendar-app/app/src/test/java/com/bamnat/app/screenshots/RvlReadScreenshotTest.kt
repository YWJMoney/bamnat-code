package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.imports.DocScanner
import com.bamnat.app.imports.RosterScanner
import com.bamnat.app.pay.OcrModel
import com.bamnat.app.ui.imports.CAMERA
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.OTHERS
import com.bamnat.app.ui.imports.SCANNER
import com.bamnat.app.ui.imports.TAP_PHOTO
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-32][RVL-33][RVL-34] 갈래 L 3차의 화면 (디자인 품질 조건): 한 달 근무 넣기의 들어가는 길(스캐너, 스캐너를 못 쓰는 폰은 사진 찍기,
 * 사진 고르기, '다른 방법' 시트), 읽는 동안의 한 줄(준비물 받는 중 · 42%, 5번 중 3번째 · 멈추기), 못 읽었을 때의 까닭·고치는 단추·다음 걸음
 * 셋, 첫 설정 '근무표가 있어요'의 같은 틀. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlReadScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)
    private val photo = Uri.parse("content://test/roster.jpg")

    @After fun noFakes() {
        RosterScanner.fake = null
        RosterScanner.fakeImage = null
        RosterScanner.fakeUnderWay = null
        DocScanner.forceAvailable = null
    }

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    /** A roster-looking picture for the failed reading's '사진에서 내 줄 알려 주기'. */
    private fun picture(): Bitmap = Bitmap.createBitmap(600, 420, Bitmap.Config.ARGB_8888).also { b ->
        val canvas = Canvas(b)
        canvas.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.DKGRAY; strokeWidth = 2f; textSize = 22f }
        for (r in 0..6) canvas.drawLine(10f, 30f + r * 55, 590f, 30f + r * 55, p)
        for (k in 0..10) canvas.drawLine(10f + k * 58, 30f, 10f + k * 58, 360f, p)
    }

    private fun roster(scanner: Boolean, then: Screen.(DutyRosterViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        DocScanner.forceAvailable = scanner
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        then(vm)
        settle()
    }

    // ---- RVL-34: the ways in ----
    private fun ways(scanner: Boolean): Screen.() -> Unit = roster(scanner) {
        assertTrue(appears(if (scanner) SCANNER else CAMERA))
        assertTrue(appears(OTHERS, exact = true))
    }

    @Test fun waysFoldLight() = shoot("rvl34_ways_fold_light_100", FOLDED, ways(true))
    @Test fun waysFoldDarkHuge() = shoot("rvl34_ways_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, ways(true))
    @Test fun waysOpenLight() = shoot("rvl34_ways_open_light_100", UNFOLDED, ways(true))
    @Test fun waysNoScannerFoldLight() = shoot("rvl34_ways_no_scanner_fold_light_100", FOLDED, ways(false))
    @Test fun waysNoScannerFoldDarkHuge() = shoot("rvl34_ways_no_scanner_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, ways(false))

    // ---- RVL-34 (HQ-61 ④): the keypad's tools go to a second line when the width runs out, the rarer three wait in '더 보기' ----
    // (The menu is a popup window the shots do not draw; its button is checked here, what it does in EaseScreenshotTest.)
    private fun keypad(): Screen.() -> Unit = roster(true) { vm ->
        vm.tap(3)
        settle()
        reveal("» 건너뛰기")
        assertTrue(appears("되돌리기"))
        assertTrue(appears(com.bamnat.app.ui.imports.KEYPAD_MORE, exact = true))
    }

    @Test fun keypadFoldLight() = shoot("rvl34_keypad_fold_light_100", FOLDED, keypad())
    @Test fun keypadFoldDarkHuge() = shoot("rvl34_keypad_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, keypad())
    @Test fun keypadOpenLight() = shoot("rvl34_keypad_open_light_100", UNFOLDED, keypad())

    private fun others(scanner: Boolean): Screen.() -> Unit = roster(scanner) {
        tapText(OTHERS, exact = true)
        assertTrue(appears("근무표 넣는 다른 방법"))
    }

    @Test fun othersFoldLight() = shoot("rvl34_others_fold_light_100", FOLDED, others(true))
    @Test fun othersFoldDarkHuge() = shoot("rvl34_others_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, others(true))
    @Test fun othersOpenLight() = shoot("rvl34_others_open_light_100", UNFOLDED, others(true))
    @Test fun othersNoScannerFoldLight() = shoot("rvl34_others_no_scanner_fold_light_100", FOLDED, others(false))

    // ---- RVL-32: a reading under way, with 멈추기 ----
    private fun underWay(preparing: Int?, step: Pair<Int, Int>?): Screen.() -> Unit = roster(true) { vm ->
        RosterScanner.fakeUnderWay = { prep, prog -> if (preparing != null) prep(preparing); if (step != null) prog(step.first, step.second) }
        RosterScanner.fake = { awaitCancellation() }
        vm.readPhoto(photo)
        waitFor("the reading line") { vm.ui.value.reading && (vm.ui.value.preparing != null || vm.ui.value.step != null) }
        assertTrue(appears("멈추기", exact = true))
    }

    @Test fun preparingFoldLight() = shoot("rvl32_preparing_fold_light_100", FOLDED, underWay(42, null))
    @Test fun preparingFoldDarkHuge() = shoot("rvl32_preparing_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, underWay(42, null))
    @Test fun preparingOpenLight() = shoot("rvl32_preparing_open_light_100", UNFOLDED, underWay(42, null))
    @Test fun stepsFoldLight() = shoot("rvl32_steps_fold_light_100", FOLDED, underWay(null, 3 to 5))
    @Test fun stepsFoldDarkHuge() = shoot("rvl32_steps_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, underWay(null, 3 to 5))

    // ---- RVL-32 / RVL-33: a reading that gave nothing ----
    /** No network for the reading model: said at once, with 와이파이 켜기 and the three ways on (no photo to tap). */
    private fun noNetwork(): Screen.() -> Unit = roster(true) { vm ->
        RosterScanner.fake = { throw OcrModel.NotReady(OcrModel.Wait.NETWORK) }
        vm.readPhoto(photo)
        waitFor("the failure") { !vm.ui.value.reading && vm.ui.value.error != null }
        assertTrue(appears("와이파이 켜기"))
    }

    @Test fun noNetworkFoldLight() = shoot("rvl33_no_network_fold_light_100", FOLDED, noNetwork())
    @Test fun noNetworkFoldDarkHuge() = shoot("rvl33_no_network_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, noNetwork())
    @Test fun noNetworkOpenLight() = shoot("rvl33_no_network_open_light_100", UNFOLDED, noNetwork())

    /** A photo that read no table, on a phone without the scanner: '사진에서 내 줄 알려 주기' first, then 다시 찍기, 직접 칠하기. */
    private fun nothing(): Screen.() -> Unit = roster(false) { vm ->
        RosterScanner.fake = { emptyList() }
        RosterScanner.fakeImage = { picture() }
        vm.readPhoto(photo)
        waitFor("the failure") { !vm.ui.value.reading && vm.ui.value.error != null }
        assertTrue(appears(TAP_PHOTO))
    }

    @Test fun nothingFoldLight() = shoot("rvl33_nothing_fold_light_100", FOLDED, nothing())
    @Test fun nothingFoldDarkHuge() = shoot("rvl33_nothing_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, nothing())
    @Test fun nothingOpenLight() = shoot("rvl33_nothing_open_light_100", UNFOLDED, nothing())

    // ---- The first setup's '근무표가 있어요', the same ways, line and failure ----
    private fun setup(then: Screen.(com.bamnat.app.ui.setup.SetupViewModel) -> Unit): Screen.() -> Unit = {
        DocScanner.forceAvailable = false
        val id = c.calendars.begin("병동")
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c, calendarId = id)
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
        settle()
        then(vm)
        settle()
    }

    private fun setupFailed(): Screen.() -> Unit = setup { vm ->
        RosterScanner.fake = { throw OcrModel.NotReady(OcrModel.Wait.STORAGE) }
        vm.readRosterPhoto(app, photo)
        waitFor("the failure") { !vm.rosterReading.value && vm.roster.value?.error != null }
        reveal("저장 공간 보기")
    }

    @Test fun setupFailedFoldLight() = shoot("rvl33_setup_failed_fold_light_100", FOLDED, setupFailed())
    @Test fun setupFailedFoldDarkHuge() = shoot("rvl33_setup_failed_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, setupFailed())

    private fun setupReading(): Screen.() -> Unit = setup { vm ->
        RosterScanner.fakeUnderWay = { prep, _ -> prep(42) }
        RosterScanner.fake = { awaitCancellation() }
        vm.readRosterPhoto(app, photo)
        waitFor("the reading line") { vm.rosterReading.value && vm.rosterPreparing.value != null }
        reveal("멈추기", exact = true)
    }

    @Test fun setupReadingFoldLight() = shoot("rvl32_setup_reading_fold_light_100", FOLDED, setupReading())
    @Test fun setupReadingOpenLight() = shoot("rvl32_setup_reading_open_light_100", UNFOLDED, setupReading())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
