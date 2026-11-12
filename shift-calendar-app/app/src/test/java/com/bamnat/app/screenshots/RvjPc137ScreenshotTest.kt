package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.EditValueDialog
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.app.ui.pay.PhotoThumb
import com.bamnat.app.ui.pay.PinnedPhotos
import com.bamnat.app.ui.pay.RaiseDialog
import com.bamnat.app.ui.pay.ValueTarget
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 2.0.0 갈래 J 여섯 번째, 통합 3차 PC 마지막 판(01:55 KST)에서 J에 온 것: [RVJ-55] PC-138 마감한 달의 금액 바꾸기·인상 규칙 미리 보기,
 * [RVJ-56] PC-137 마감한 달에서 넣은 수당의 위 줄, [RVJ-57] PC-144 처음 만든 급여 설정에 지난달 마감 묻기, [RVJ-58] PC-145 달이 끝나기
 * 전 '이 달 마감하기', [RVJ-60] J-488 ★13 고정 연장근로수당 직원의 근거, [RVJ-61] HQ-81 명세서 사진 ⊕ ⊖.
 * The shots hold no assertion so the same test draws the 'before'; the tests that name what they look for check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjPc137ScreenshotTest : ScreenshotBase() {
    private val shown get() = Today.month()
    private val next get() = Today.month().plusMonths(1)
    private fun won(v: Long) = "%,d원".format(v)

    /** This month closed as 도구 › 이 달 마감하기 keeps it; the closed total. */
    private fun closeNow(): Long = runBlocking {
        val c = app.container
        val month = PayViewModel(c).calculateOff(c.payStore.profile.value!!, c.scheduleRepository.snapshot.first(), Today.month())
        c.payStore.saveClosed(com.bamnat.core.pay.CalcSnapshot.close(month, Today.millis()), Today.month())
        month.total
    }

    // ── [RVJ-56] PC-137: 마감한 달을 보며 수당 추가 ──
    private fun adding(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        closeNow()
        repeat(2) { settle() }
        tapText("내 급여 값", exact = true, scrolls = 0)
        settle()
        tapText("수당 추가", exact = true, scrolls = 14)
        typeInto("한 달 금액", "200000")
        repeat(2) { settle() }
        tapText("추가", exact = true)
        settle()
        tapText("추가", exact = true)
        repeat(3) { settle() }
        reveal("만들었어요", exact = false)
    }

    @Test fun madeOnClosed() = shoot("rvj_145_closed_made_folded", FOLDED, adding())
    @Test fun madeOnClosedDarkBig() = shoot("rvj_146_closed_made_folded_dark_200", DARK, 2f) {
        adding().invoke(this)
        // At 200% the line sits right under the top bar; bring it fully into view.
        scrollDown(160)
        settle()
    }

    @Test fun theMadeLineSaysTheMonthItLandsIn() = shoot("rvj_147_closed_made_said_folded", FOLDED) {
        adding().invoke(this)
        assertTrue(appears("‘매달 수당 1’ 항목을 만들었어요. ${next.monthValue}월 급여부터 들어가요."))
        assertFalse(shows("합계에 바로 들어갔어요"))
    }

    // ── [RVJ-55] PC-138: 마감한 달이 있는 금액 바꾸기 미리 보기 ──
    private var kept = 0L

    /** This month closed, then a 200,000원 수당 put in (the month worked out again comes to more than it was closed at), as on the PC. */
    private fun preview(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { vm ->
            val profile = vm.profile.collectAsState().value
            val snap = vm.snapshot.collectAsState().value
            if (profile != null && snap != null) EditValueDialog(vm, profile, snap, ValueTarget.Base, Today.month(), onDismiss = {})
        }.invoke(this)
        kept = closeNow()
        runBlocking {
            val c = app.container
            val p = c.payStore.profile.value!!
            c.payStore.save(p.copy(items = p.items + PayItem("extra", "매달 수당 1", ValueTimeline(listOf(DatedValue(shown.atDay(1), 200_000))),
                Cadence.MONTHLY)), keepUndo = false)
        }
        repeat(2) { settle() }
        typeInto("새 금액", "4000000")
        repeat(3) { settle() }
    }

    @Test fun previewWithAClosedMonth() = shoot("rvj_148_closed_preview_folded", FOLDED, preview())
    @Test fun previewWithAClosedMonthDarkBig() = shoot("rvj_149_closed_preview_folded_dark_200", DARK, 2f, preview())

    @Test fun theClosedMonthStaysAtItsClosedAmount() = shoot("rvj_150_closed_preview_said_folded", FOLDED) {
        preview().invoke(this)
        assertTrue("마감한 금액 ${won(kept)}", appears("${won(kept)} ·\u00A0마감(그대로)", exact = true))
        assertFalse("다시 계산한 금액이 마감한 달에 나오지 않아요", shows("${won(kept + 200_000)} →"))
        assertTrue("마감한 달은 바뀌는 달에서 빠져요", shows("앞으로 11개월이 바뀌어요"))
    }

    /** [RVJ-55] 매년 인상 규칙: 1월 인상(올해는 이미 지남)이면 첫 줄은 이번 달인데, 마감했으니 '마감(그대로)'와 다음 달 값. */
    @Test fun theRaisePreviewSkipsTheClosedMonth() = shoot("rvj_151_closed_raise_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true) { vm ->
            val profile = vm.profile.collectAsState().value
            if (profile != null) RaiseDialog(vm, profile, ValueTarget.Base, onDismiss = {})
        }.invoke(this)
        closeNow()
        repeat(2) { settle() }
        tapText("1월", exact = true)
        tapText("3%", exact = true)
        repeat(2) { settle() }
        assertTrue(scrolledTo("마감(그대로)", exact = true))
        assertTrue(shows(com.bamnat.app.ui.Fmt.month(next), exact = true))
    }

    // ── [RVJ-58] PC-145: 달이 끝나기 전 '이 달 마감하기' ──
    private fun early(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        tapText("도구", exact = true, scrolls = 0)
        settle()
        tapText("이 달 마감하기", exact = false, scrolls = 14)
        repeat(2) { settle() }
    }

    @Test fun closeEarly() = shoot("rvj_152_close_early_folded", FOLDED, early())
    @Test fun closeEarlyDarkBig() = shoot("rvj_153_close_early_folded_dark_200", DARK, 2f, early())

    @Test fun closingBeforeTheMonthIsOverAsksOnce() = shoot("rvj_154_close_early_said_folded", FOLDED) {
        early().invoke(this)
        assertTrue(appears("아직 ${shown.monthValue}월 근무가 남았어요"))
        tapText("지금 마감", exact = true)
        repeat(2) { settle() }
        assertTrue(scrolledTo("마감한 결과를 보여 주고 있어요"))
    }

    // ── [RVJ-57] PC-144: 처음 만든 급여 설정 ──
    @Test fun aFirstSetupDoesNotAskToCloseLastMonth() = shoot("rvj_155_first_setup_folded", FOLDED) {
        val c = app.container
        c.settingsRepository.update { it.copy(previewTier = null) }
        val vm = PayViewModel(c)
        vm.saveSetup(vm.sampleProfile().copy(rules = companyRules), invite = false)
        repeat(2) { settle() }
        content { com.bamnat.app.ui.pay.PayScreen(vm, onBack = {}) }
        repeat(2) { settle() }
        assertFalse("오늘 만든 설정에 지난달 마감을 묻지 않아요", scrolledTo("이대로 마감할까요?"))
    }

    // ── [RVJ-60] J-488 ★13: 월급에 들어 있는 고정 연장근로수당 (월~토 9~18시, 5인 미만) ──
    /** The class keeps 2026-10-14 (SHOT_TIME); a test that moves the clock gives it back. */
    @After fun clockBack() = Today.fix(SHOT_TIME)

    /** [fourSaturdays]: 2026년 11월(토요일 넷, 연장 32시간 < 고정 몫 약 34.8시간); else 10월(토요일 다섯, 연장 40시간). */
    private fun basis(fourSaturdays: Boolean): Screen.() -> Unit = {
        if (fourSaturdays) Today.fix(ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, SEOUL))
        runBlocking {
            val store = app.container.phaseThree
            val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
            store.saveWorkplace(Workplace("p1", "식당", fivePlus = false, payday = 31, payNextMonth = false))
            store.saveStaff(StaffMember("m", "한월급", form = WageForm.MONTH, base = 2_500_000L, hiredOn = LocalDate.of(2026, 1, 5), job = "매니저",
                weekly = DayOfWeek.entries.take(6).associateWith { nine }, insurance = Insurance.ALL, overtimeInPay = true), "p1", 0)
        }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
        tapText("근거 보기", exact = true)
        repeat(2) { settle() }
        reveal("고정 연장근로수당", exact = true)
    }

    @Test fun basisTwoLines() = shoot("rvj_156_fixed_ot_basis_folded", FOLDED, basis(true))
    @Test fun basisTwoLinesDarkBig() = shoot("rvj_157_fixed_ot_basis_folded_dark_200", DARK, 2f, basis(true))

    @Test fun theBasisReadsAsTheContract() = shoot("rvj_158_fixed_ot_basis_said_folded", FOLDED) {
        basis(true).invoke(this)
        assertTrue(scrolledTo("월급 2,500,000원 − 고정 연장근로수당"))
        assertTrue(scrolledTo("이번 달 연장근로 32시간은 이 안이에요"))
        assertFalse("음수 줄이 없어요", shows("이미 받은 몫"))
        assertFalse("고정 몫 안이면 차액 줄이 없어요", shows("연장근로 차액", exact = true))
        assertFalse(shows("입력한 월 기본급"))
    }

    @Test fun aLongerMonthAddsTheGap() = shoot("rvj_159_fixed_ot_gap_folded", FOLDED) {
        basis(false).invoke(this)
        assertTrue(scrolledTo("연장근로 차액", exact = true))
        assertTrue(scrolledTo("연장근로 40시간 410,240원 − 고정 연장근로수당"))
    }

    // ── [RVJ-61] HQ-81: 명세서 사진 ⊖ ⊕ ──
    private fun photo(): Bitmap = Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.GRAY; textSize = 28f }
        repeat(20) { i -> c.drawText("기본급 2,397,500   국민연금 141,750", 20f, 60f + i * 56, p) }
    }

    private fun pinned(zoomed: Boolean, photos: Int = 1): Screen.() -> Unit = {
        val bitmaps = List(photos) { photo() }
        content {
            Column {
                PinnedPhotos(bitmaps)
                Text("명세서 줄", modifier = Modifier.padding(16.dp))
            }
        }
        settle()
        if (zoomed) {
            tapText("사진 크게", exact = true)
            settle()
        }
        buttonsOffThePhoto()
    }

    /** [RVJ-61] (HQ-102): every button of the photo sits under it (they lay on it and hid lines being read). */
    private fun Screen.buttonsOffThePhoto() {
        val all = nodes()
        fun said(n: androidx.compose.ui.semantics.SemanticsNode) =
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty() +
                n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text }
        val photo = all.first { n -> said(n).any { it.startsWith("읽은 명세서 사진") } }.boundsInWindow
        val names = setOf("사진 작게", "사진 크게", "사진 단추 더 보기", "사진 칸 넓게", "사진 칸 좁게", "접기", "닫기", "명세서", "계산 방법")
        val buttons = all.filter { n -> said(n).any { it in names || it.endsWith("%") } }
        assertTrue("사진 단추가 없어요", buttons.size >= 2)
        buttons.forEach { b -> assertTrue("'${said(b)}'가 사진 위에 떠요", b.boundsInWindow.top >= photo.bottom - 0.5f) }
    }

    @Test fun pinnedPhotoButtons() = shoot("rvj_160_payslip_photo_zoom_folded", FOLDED, pinned(true))
    @Test fun pinnedPhotoButtonsDarkBig() = shoot("rvj_161_payslip_photo_zoom_folded_dark_200", DARK, 2f, pinned(true))
    @Test fun pinnedPhotoButtonsWide() = shoot("rvj_162_payslip_photo_zoom_unfolded", UNFOLDED, pinned(true))

    /** [RVJ-61] (HQ-102): at big letters the row has no room for 사진 칸·접기, so they go into one ⋮ (and the zoom in words is left out). */
    @Test fun bigLettersPutTheRoomButtonsInOne() = shoot("rvj_167_payslip_photo_band_more_folded_dark_200", DARK, 2f) {
        pinned(true).invoke(this)
        assertFalse(shows("접기", exact = true))
        assertFalse(shows("150%", exact = true))
        assertTrue(enabled("사진 작게"))
        tapText("사진 단추 더 보기", exact = true)
        settle()
        assertTrue(inPopup("사진 칸 넓게"))
        assertTrue(inPopup("접기"))
    }

    /** [RVJ-61] (HQ-102): two photos (명세서·계산 방법) at big letters: which photo goes into the ⋮ too, never on the photo. */
    @Test fun twoPhotosAtBigLetters() = shoot("rvj_168_payslip_two_photos_band_folded_dark_200", DARK, 2f) {
        pinned(false, photos = 2).invoke(this)
        tapText("사진 단추 더 보기", exact = true)
        settle()
        assertTrue(inPopup("계산 방법 사진"))
    }

    /** [RVJ-61] (HQ-102): at usual letters everything shows in the one row under the photo. */
    @Test fun twoPhotosInOneRow() = shoot("rvj_169_payslip_two_photos_band_unfolded", UNFOLDED) {
        pinned(true, photos = 2).invoke(this)
        assertTrue(shows("계산 방법", exact = true))
        assertTrue(shows("접기", exact = true))
        assertTrue(shows("150%", exact = true))
    }

    @Test fun thePhotoZoomsWithoutTwoFingers() = shoot("rvj_163_payslip_photo_zoom_said_folded", FOLDED) {
        pinned(false).invoke(this)
        assertTrue(shows("사진 크게", exact = true))
        assertFalse("1×에서는 더 작게 할 수 없어요", enabled("사진 작게"))
        tapText("사진 크게", exact = true)
        settle()
        assertTrue(appears("150%", exact = true))
        assertTrue(enabled("사진 작게"))
        assertTrue("사진 칸 크기 단추는 이름이 달라요", shows("사진 칸 넓게", exact = true))
    }

    /** [RVJ-61] 같은 구조: 명세서 맞추기 결과의 '읽은 사진' 작은 그림을 눌러 크게 연 창. */
    private fun big(zoomed: Boolean): Screen.() -> Unit = {
        val bitmap = photo()
        content { Column(Modifier.padding(16.dp)) { PhotoThumb(bitmap) } }
        settle()
        tapText("읽은 사진", exact = false)
        repeat(2) { settle() }
        if (zoomed) {
            tapText("사진 크게", exact = true)
            settle()
        }
        buttonsOffThePhoto()
    }

    @Test fun bigPhoto() = shoot("rvj_165_payslip_photo_big_folded", FOLDED, big(true))

    @Test fun theBigPhotoZoomsToo() = shoot("rvj_166_payslip_photo_big_said_folded", FOLDED) {
        big(false).invoke(this)
        assertFalse(enabled("사진 작게"))
        tapText("사진 크게", exact = true)
        settle()
        assertTrue(appears("150%", exact = true))
        assertTrue(shows("닫기", exact = true))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
