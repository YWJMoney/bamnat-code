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
import com.bamnat.app.ui.RecentFill
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [READ-04][READ-05][READ-08][READ-10][READ-11] 묶음 U's screenshots, numbers 810~822 (1.25 순번 369~372): "어느 줄(조)이 내
 * 것인가요?" on every size the plan asks for, 사진 위에서 누르기 and 무엇을 읽었는지 보기, a photo that read no row of dates,
 * the first setup's question and its unknown code, the payslip's two photos, 방금 넣은 것 되돌리기 in 도구, and the
 * keypad's overwrite line. The roster photo is drawn from the words ML Kit would give ([RosterScanner.fake]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
    }

    @After fun noFake() {
        RosterScanner.fake = null
        RosterScanner.fakeImage = null
    }

    /** The words drawn as the photo they came from: white paper, black print. */
    private fun picture(words: List<RosterWord>): Bitmap {
        val w = words.maxOf { it.right } + 60
        val h = words.maxOf { it.bottom } + 60
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
        words.forEach { canvas.drawText(it.text, it.left.toFloat(), it.bottom - 8f, paint) }
        return bmp
    }

    private fun fakePhoto(words: List<RosterWord>) {
        RosterScanner.fake = { words }
        RosterScanner.fakeImage = { picture(words) }
    }

    /** 한 달 근무 넣기 after a photo of four lines was read: the question first. */
    private fun asked(days: Boolean = true, then: Screen.(DutyRosterViewModel) -> Unit = {}): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        fakePhoto(RosterPhotoTest.roster(2.5, days))
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        settle()
        then(vm)
        settle()
    }

    // ---- 369: the question, every size ----
    @Test fun whichLine() = shoot("810_u_which_line_phone", PHONE_393, asked { reveal("어느 줄(조)이 내 것인가요?") })
    @Test fun whichLineSmall() = shoot("811_u_which_line_small_phone", SMALL_PHONE, asked { reveal("어느 줄(조)이 내 것인가요?") })
    @Test fun whichLineLandscape() = shoot("812_u_which_line_landscape", PHONE_LANDSCAPE, asked { reveal("어느 줄(조)이 내 것인가요?") })
    @Test fun whichLineHuge() = shoot("813_u_which_line_huge_text", PHONE, fontScale = 2f, asked { reveal("어느 줄(조)이 내 것인가요?") })
    @Test fun whichLineDark() = shoot("814_u_which_line_dark", FOLDED_NIGHT, asked { reveal("어느 줄(조)이 내 것인가요?") })
    @Test fun picked() = shoot("815_u_line_picked", PHONE_393, asked { vm ->
        vm.pick(1)
        settle()
        assertTrue(vm.ui.value.chosen)
    })

    // ---- 369: the ways out ----
    @Test fun noDays() = shoot("816_u_no_row_of_dates", PHONE_393, asked(days = false) { reveal("사진 위에서 누르기") })
    @Test fun tapDialog() = shoot("817_u_tap_on_photo", PHONE_393, asked(days = false) {
        tapText("사진 위에서 누르기")
        assertTrue(shows("날짜 줄(1 2 3 …)을 눌러 주세요"))
    })
    @Test fun diagnosis() = shoot("818_u_what_was_read", PHONE_393, asked {
        tapText("무엇을 읽었는지 보기")
        assertTrue(shows("근무 줄 4개"))
    })

    // ---- 371: the first setup's 듀티표가 있어요 ----
    private fun setup(pick: Boolean): Screen.() -> Unit = {
        // The ward's D/E/N/O, and 교육 the setup's kinds do not cover.
        fakePhoto(RosterPhotoTest.roster(-2.0) { p, d -> if (p == 2 && d == 15) "교육" else listOf("D", "D", "E", "E", "N", "N", "O", "O")[(d + p * 3) % 8] })
        val id = c.calendars.begin("병동")
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c, calendarId = id)
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
        settle()
        vm.readRosterPhoto(app, Uri.parse("content://test/roster.jpg"))
        settle()
        if (pick) {
            vm.useRosterRow(2)
            settle()
        }
    }
    @Test fun setupWhichLine() = shoot("819_u_setup_which_line", FOLDED) {
        setup(pick = false).invoke(this)
        reveal("어느 줄(조)이 내 것인가요?")
    }
    @Test fun setupUnknownCode() = shoot("820_u_setup_unknown_code", FOLDED) {
        setup(pick = true).invoke(this)
        reveal("‘교육’(1일)는 무엇인가요?")
    }

    // ---- payslip and company rules, two photos ----
    @Test fun payslipTwoPhotos() = shoot("821_u_payslip_two_photos", PHONE_393) {
        content {
            com.bamnat.app.ui.pay.PayslipFillDialog("명세서 사진으로 채우기", onDismiss = {},
                rules = com.bamnat.app.ui.pay.RuleTarget(emptyList(), emptyList(), true) { _, _ -> }, onRead = {})
        }
        settle()
        assertTrue(shows("2. 회사 계산 방법", exact = false))
    }

    // ---- 372: taking a month back, and what saving overwrites ----
    @Test fun undoInTools() = shoot("822_u_undo_recent_fill", PHONE_393, style(WorkStyle.SHIFT) {
        RecentFill.record("11월에 30일을 넣었어요", c.calendars.active.value, c.appScope) {}
        tapText("도구", exact = true)
        assertTrue(shows("방금 넣은 것 되돌리기"))
    })
    @Test fun keypadOverwrite() = shoot("823_u_keypad_overwrite", FOLDED, style(WorkStyle.SHIFT) {
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        val t = snap.shiftTypes.associateBy { it.name }
        // The 3rd was changed by hand before: typing the month over it says so.
        val changed = snap.entries.firstOrNull { it.override != ShiftOverride.None }?.date
        val month = changed?.let { YearMonth.from(it) } ?: YearMonth.of(2026, 10)
        val initial = (1..6).associate { d -> month.atDay(d) to ShiftOverride.Shift(t.getValue("주간").id) as ShiftOverride } +
            (changed?.let { mapOf(it to ShiftOverride.Shift(t.getValue("휴무").id) as ShiftOverride) } ?: emptyMap())
        content { com.bamnat.app.ui.calendar.DutyTypingSheet(snap, month, onDismiss = {}, onSave = {}, initial = initial) }
        settle()
    })
}
