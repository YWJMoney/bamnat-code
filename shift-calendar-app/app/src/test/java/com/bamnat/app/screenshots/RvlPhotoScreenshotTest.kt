package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.imports.RosterPhotoTest
import com.bamnat.app.imports.RosterScanner
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.TAP_PHOTO
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVL-35]~[RVL-40] 갈래 L 4차의 화면 (디자인 품질 조건): 사진 위에서 누르기의 확대(⊕·⊖·두 손가락), 무엇을 읽었는지 보기, '사진과
 * 같은가요?'의 노란 칸 하나씩 확인, 어느 줄을 읽었는지 한 줄, 이미 넣어 둔 연차를 그대로 두는 줄과 달력 위 테두리, 지난달과 같게의 세 방법,
 * 가게 유형의 키(연차 없음). 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlPhotoScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    @After fun noFake() {
        RosterScanner.fake = null
        RosterScanner.fakeImage = null
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

    /** The words drawn as the photo they came from: white paper, black print. */
    private fun picture(words: List<RosterWord>): Bitmap {
        val bmp = Bitmap.createBitmap(words.maxOf { it.right } + 60, words.maxOf { it.bottom } + 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
        words.forEach { canvas.drawText(it.text, it.left.toFloat(), it.bottom - 8f, paint) }
        return bmp
    }

    /** 김민지's line has 'D' on the 3rd and 9th: the ward's letter, matched to 주간 as a guess (yellow). */
    private fun code(p: Int, d: Int): String = if (p == 0 && (d == 3 || d == 9)) "D" else RosterPhotoTest.codeOf(p, d)

    /** A photo of four lines read, and 김민지's picked. */
    private fun read(pick: Boolean = true, then: Screen.(DutyRosterViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        val words = RosterPhotoTest.roster(2.5, code = ::code)
        RosterScanner.fake = { words }
        RosterScanner.fakeImage = { picture(words) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        waitFor("the photo read") { vm.ui.value.rows != null && !vm.ui.value.reading }
        if (pick) {
            vm.pick(vm.ui.value.rows!!.indexOfFirst { it.label == "김민지" })
            settle()
        }
        then(vm)
        settle()
    }

    // ---- RVL-36 / RVL-37: the yellow cells one by one, and whose line went in ----
    private fun yellow(): Screen.() -> Unit = read { vm ->
        assertEquals(setOf(3, 9), vm.ui.value.doubtful)
        assertTrue(appears("‘김민지’ 줄을 읽었어요"))
        assertTrue(appears("다른 줄 고르기", exact = true))
        reveal("노란 2칸 확인하기")
    }

    @Test fun yellowFoldLight() = shoot("rvl36_yellow_fold_light_100", FOLDED, yellow())
    @Test fun yellowFoldDarkHuge() = shoot("rvl36_yellow_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, yellow())
    @Test fun yellowOpenLight() = shoot("rvl36_yellow_open_light_100", UNFOLDED, yellow())

    private fun pointed(): Screen.() -> Unit = read {
        tapText("노란 2칸 확인하기")
        assertTrue(appears("3일 · 주간으로 읽었어요"))
        reveal("맞아요", exact = true)
    }

    @Test fun pointedFoldLight() = shoot("rvl36_pointed_fold_light_100", FOLDED, pointed())
    @Test fun pointedFoldDarkHuge() = shoot("rvl36_pointed_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, pointed())
    @Test fun pointedOpenLight() = shoot("rvl36_pointed_open_light_100", UNFOLDED, pointed())

    /** Both right as read: the second is pointed at once, and the last one answers the card. */
    @Test fun lookedFoldLight() = shoot("rvl36_looked_fold_light_100", FOLDED, read { vm ->
        tapText("노란 2칸 확인하기")
        tapText("맞아요", exact = true)
        assertTrue(appears("9일 · 주간으로 읽었어요"))
        tapText("맞아요", exact = true)
        waitFor("the card answered") { vm.ui.value.same == true }
        assertTrue(vm.ui.value.doubtful.isEmpty())
    })

    /** '다른 줄 고르기' asks again which line is mine. */
    @Test fun otherLineFoldLight() = shoot("rvl37_other_line_fold_light_100", FOLDED, read {
        tapText("다른 줄 고르기", exact = true)
        assertTrue(appears("어느 줄(조)이 내 것인가요?"))
    })

    // ---- RVL-35: the photo made bigger to tap on ----
    private fun tapDialog(zoom: Int = 0): Screen.() -> Unit = read(pick = false) {
        tapText(TAP_PHOTO)
        assertTrue(appears("1. 날짜 줄을 눌러 주세요", exact = true))
        // 2.0.0 [RVL-35] (검토 HQ-80) A wide roster opens as tall as the window, so a line can be tapped at once.
        assertTrue("opens zoomed", appears("%"))
        repeat(zoom) { tapText("사진 크게") }
    }

    @Test fun tapFoldLight() = shoot("rvl35_tap_fold_light_100", FOLDED, tapDialog())
    @Test fun tapFoldDarkHuge() = shoot("rvl35_tap_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, tapDialog())
    @Test fun tapOpenLight() = shoot("rvl35_tap_open_light_100", UNFOLDED, tapDialog())
    @Test fun tapZoomedFoldLight() = shoot("rvl35_tap_zoomed_fold_light_100", FOLDED, tapDialog(zoom = 2))

    private fun diagnosis(): Screen.() -> Unit = read(pick = false) {
        tapText("무엇을 읽었는지 보기")
        assertTrue(appears("근무 줄 4개"))
    }

    @Test fun diagnosisFoldLight() = shoot("rvl35_diagnosis_fold_light_100", FOLDED, diagnosis())
    @Test fun diagnosisFoldDarkHuge() = shoot("rvl35_diagnosis_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, diagnosis())
    @Test fun diagnosisOpenLight() = shoot("rvl35_diagnosis_open_light_100", UNFOLDED, diagnosis())

    // ---- RVL-38: leave already there ----
    private fun typed(style: WorkStyle = WorkStyle.SHIFT, before: () -> Unit = {}, then: Screen.(DutyRosterViewModel) -> Unit): Screen.() -> Unit = style(style) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        before()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        then(vm)
        settle()
    }

    private val repo get() = c.calendars.repository(PersonalCalendar.MAIN)
    private fun id(name: String) = runBlocking { repo.shiftTypes.first() }.first { it.name == name }.id

    private fun overwrite(keep: Boolean = false): Screen.() -> Unit = typed(before = {
        runBlocking { repo.setLeave(nov.atDay(5), Leave()); repo.setLeave(nov.atDay(12), Leave()) }
    }) { vm ->
        vm.tap(1)
        repeat(14) { i -> vm.put(DutyCell.Shift(id(if (i % 4 < 2) "주간" else "야간"))) }
        settle()
        assertTrue(appears("넣어 둔 2일이 바뀌어요"))
        assertTrue(appears("5·12일 (연차·바꾼 근무)"))
        if (keep) {
            tapText("그 날은 그대로 두기", exact = true)
            assertTrue(appears("넣어 둔 2일은 그대로 둬요"))
            assertTrue(appears("11월에 12일 넣기"))
        }
    }

    @Test fun overwriteFoldLight() = shoot("rvl38_overwrite_fold_light_100", FOLDED, overwrite())
    @Test fun overwriteFoldDarkHuge() = shoot("rvl38_overwrite_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, overwrite())
    @Test fun overwriteOpenLight() = shoot("rvl38_overwrite_open_light_100", UNFOLDED, overwrite())
    @Test fun keptFoldLight() = shoot("rvl38_kept_fold_light_100", FOLDED, overwrite(keep = true))

    // ---- RVL-39: 지난달과 같게, three ways ----
    /** 9·10월 as D D N N O O O O (an order of 8): last month carried on, and the other ways to put it in. */
    private fun copied(then: Screen.(DutyRosterViewModel) -> Unit = {}): Screen.() -> Unit = typed(before = {
        val order = listOf("주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무").map(::id)
        val sep = LocalDate.of(2026, 9, 1)
        val days = (0 until 61).map { sep.plusDays(it.toLong()) }
        runBlocking { repo.updateDays(days) { e -> e.copy(override = ShiftOverride.Shift(order[(e.date.toEpochDay() - sep.toEpochDay()).toInt() % 8])) } }
    }) { vm ->
        vm.sameAsLastMonth(runBlocking { repo.snapshot.first() })
        settle()
        assertTrue(appears("지난달까지 이어 온 8일 순서대로 넣었어요"))
        then(vm)
    }

    @Test fun copiedFoldLight() = shoot("rvl39_copied_fold_light_100", FOLDED, copied())
    @Test fun copiedFoldDarkHuge() = shoot("rvl39_copied_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, copied())
    @Test fun copiedOpenLight() = shoot("rvl39_copied_open_light_100", UNFOLDED, copied())
    @Test fun copiedByWeekdayFoldLight() = shoot("rvl39_copied_weekday_fold_light_100", FOLDED, copied {
        tapText("요일대로", exact = true)
        assertTrue(appears("지난달을 요일에 맞춰 넣었어요"))
    })

    // ---- RVL-40: a shop's keys ----
    private fun shopKeys(): Screen.() -> Unit = typed(WorkStyle.BUSINESS) { vm ->
        assertFalse(vm.leaveKeys)
        assertFalse(shows("오전 반차 넣기"))
    }

    @Test fun shopKeysFoldLight() = shoot("rvl40_shop_keys_fold_light_100", FOLDED, shopKeys())
    @Test fun shopKeysFoldDarkHuge() = shoot("rvl40_shop_keys_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shopKeys())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }
}
