package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.AddBonusDialog
import com.bamnat.app.ui.pay.ItemKind
import com.bamnat.app.ui.pay.PayQuickSetup
import com.bamnat.app.ui.pay.PayScaleDialog
import com.bamnat.app.ui.pay.PayWizard
import com.bamnat.core.pay.AnnualPay
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayScale
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.time.Today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 J 열 번째.
 * [RVJ-76] 검토 PAYU-49·T05b-02·I05ba-02: 고정 연장 시간을 칩(10·20·30·40·52) 밖으로도 넣어요. 수당 창·급여 마법사의 연봉·빠른 설정의
 * 포괄임금 셋 모두 같은 고르기(칩 + '직접')라 계약서의 12·22.5·45시간이 그대로 들어가요.
 * [RVJ-77] 검토 PAYU-46·T05b-05·I05ba-01: 호봉표를 다시 열면 지난번 표가 그대로 나오고, 해마다 오르는 표는 '모든 호봉 n% 올리기' 한 번과
 * 되돌리기로 바꿔요.
 * [RVJ-78] 총괄 J-518 사진 다듬기: 큰 글자에서 '넣던 … 이어서' 줄, 호봉 이름표 간격, 상여 '− 100% +'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjFixedOtScaleScreenshotTest : ScreenshotBase() {

    // ── [RVJ-76] 수당 창 ──

    /** 수당 추가 › 매달 수당, '고정연장수당' 400,000원, 고정 연장 '직접' 22.5시간. */
    private fun itemDirect(hours: String): Screen.() -> Unit = {
        itemBuilder(ItemKind.MONTHLY).invoke(this)
        typeInto("이름 (선택)", "고정연장수당")
        typeInto("한 달 금액", "400000")
        repeat(2) { settle() }
        tapText("직접", exact = true)
        settle()
        typeInto("고정 연장 시간 (한 달)", hours)
        repeat(2) { settle() }
    }

    @Test fun theAllowanceTakesHalfHours() = shoot("rvj_250_item_ot_direct_folded", FOLDED) {
        itemDirect("22.5").invoke(this)
        assertTrue(shows("22.5"))
        assertFalse(shows("0.5시간 단위로", exact = false))
        // The month's 연장수당 already pays it, so '+0원' says why (it is not added twice).
        assertTrue(shows("연장수당을 미리 주는 거", exact = false))
    }

    @Test fun theAllowanceTakesHalfHoursDarkBig() = shoot("rvj_251_item_ot_direct_folded_dark_200", DARK, 2f) {
        itemDirect("22.5").invoke(this)
        assertTrue(shows("22.5"))
    }

    @Test fun offTheHalfHourIsSaid() = shoot("rvj_252_item_ot_off_grid_folded", FOLDED) {
        itemDirect("22.3").invoke(this)
        assertTrue(appears("0.5시간 단위로 200시간까지 넣어 주세요", exact = false))
    }

    // ── [RVJ-76] 급여 마법사의 연봉 ──

    private val annual: (PayProfile) -> PayProfile = { p -> p.copy(annual = AnnualPay(42_000_000, fixedOvertimeMonthly = 300_000)) }

    private fun wizardAnnual(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = annual) { m ->
            val profile = m.profile.collectAsState().value
            val snap = m.snapshot.collectAsState().value
            if (profile != null && snap != null) PayWizard(profile, onCancel = {}, onDone = {}, snapshot = snap,
                preview = { m.preview(it, snap, Today.month()) }, startAt = "amount")
        }.invoke(this)
        tapText("직접", exact = true)
        settle()
        typeInto("고정 연장 시간 (한 달)", "22.5")
        repeat(2) { settle() }
    }

    @Test fun theAnnualSalaryTakesHalfHours() = shoot("rvj_253_wizard_annual_ot_folded", FOLDED) {
        wizardAnnual().invoke(this)
        assertTrue(shows("22.5"))
        assertTrue(shows("몇 시간분인지 몰라요"))
    }

    @Test fun theAnnualSalaryDarkBig() = shoot("rvj_254_wizard_annual_ot_folded_dark_200", DARK, 2f) {
        wizardAnnual().invoke(this)
        assertTrue(shows("22.5"))
    }

    // ── [RVJ-76] 빠른 설정의 포괄임금 ──

    private fun quickInclusive(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { m ->
            val profile = m.profile.collectAsState().value
            val snap = m.snapshot.collectAsState().value
            if (profile != null && snap != null) PayQuickSetup(
                initial = profile, snapshot = snap,
                preview = { m.preview(it, snap, Today.month()) },
                slipRules = { base, read, rules -> m.slipRules(base, snap, read, rules) },
                onDone = { _, _ -> }, onDetailed = {}, onCancel = {}, startAt = "confirm",
            )
        }.invoke(this)
        tapText("연장수당이 월급에 들어 있어요 (포괄임금)", exact = true)
        settle()
        tapText("직접", exact = true)
        settle()
        typeInto("고정 연장 시간 (한 달)", "22.5")
        repeat(2) { settle() }
    }

    @Test fun theEasySetupTakesHalfHours() = shoot("rvj_255_quick_ot_direct_folded", FOLDED) {
        quickInclusive().invoke(this)
        assertTrue(shows("22.5"))
        assertTrue(shows("계약서에 적힌 한 달 연장 시간"))
    }

    @Test fun theEasySetupDarkBig() = shoot("rvj_256_quick_ot_direct_folded_dark_200", DARK, 2f) {
        quickInclusive().invoke(this)
        assertTrue(shows("22.5"))
    }

    // ── [RVJ-76] 결과 화면의 줄 ──

    @Test fun thePayLineSaysTheHalfHour() = shoot("rvj_257_pay_line_half_hour_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true, adjust = { p ->
            p.copy(items = p.items + PayItem("ot-half", "고정연장수당", ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 405_000))),
                Cadence.MONTHLY, fixedOvertimeMinutes = 1_350))
        }).invoke(this)
        assertTrue(scrolledTo("고정 연장 22.5시간분"))
    }

    // ── [RVJ-77] 호봉표 ──

    private val table = PayScale(
        mapOf(1 to 2_400_000L, 2 to 2_480_000L, 3 to 2_560_000L, 4 to 2_640_000L, 5 to 2_720_000L),
        stepMonth = 3, step = 1, since = LocalDate.of(2025, 11, 1),
    )

    private fun scaleAgain(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = { it.copy(payScale = table) }) { m ->
            val profile = m.profile.collectAsState().value
            if (profile != null) PayScaleDialog(m, profile, onDismiss = {})
        }.invoke(this)
    }

    @Test fun theTableComesBack() = shoot("rvj_258_scale_again_folded", FOLDED) {
        scaleAgain().invoke(this)
        assertTrue(shows("지난번에 넣은 호봉표예요", exact = false))
        assertTrue(shows("2480000") || shows("2,480,000"))
        assertTrue(shows("5호봉", exact = true))
    }

    @Test fun theTableComesBackDarkBig() = shoot("rvj_259_scale_again_folded_dark_200", DARK, 2f) {
        scaleAgain().invoke(this)
        assertTrue(shows("지난번에 넣은 호봉표예요", exact = false))
    }

    private fun raised(): Screen.() -> Unit = {
        scaleAgain().invoke(this)
        tapText("모든 호봉 % 올리기", exact = true)
        settle()
        tapText("3.5%", exact = true)
        settle()
        tapText("모든 호봉 3.5% 올리기", exact = true)
        repeat(2) { settle() }
    }

    @Test fun everyStepGoesUpAtOnce() = shoot("rvj_260_scale_raised_folded", FOLDED) {
        raised().invoke(this)
        assertTrue(appears("모든 호봉을 3.5% 올렸어요"))
        assertTrue(shows("2484000") || shows("2,484,000"))
        assertTrue(shows("되돌리기", exact = true))
    }

    @Test fun everyStepGoesUpUnfolded() = shoot("rvj_261_scale_raised_unfolded", UNFOLDED) {
        raised().invoke(this)
        assertTrue(appears("모든 호봉을 3.5% 올렸어요"))
    }

    @Test fun theRaiseComesBack() = shoot("rvj_262_scale_raise_undone_folded", FOLDED) {
        raised().invoke(this)
        tapText("되돌리기", exact = true)
        repeat(2) { settle() }
        assertFalse(shows("모든 호봉을 3.5% 올렸어요"))
        assertTrue(shows("2400000") || shows("2,400,000"))
        assertFalse(shows("2484000") || shows("2,484,000"))
    }

    @Test fun aRowChangedByHandEndsTheUndo() = shoot("rvj_265_scale_raise_then_edit_folded", FOLDED) {
        raised().invoke(this)
        typeInto("2호봉 기본급", "2600000")
        repeat(2) { settle() }
        // 되돌리기 takes back only what '모두 올리기' changed: a row the user changed after it ends it.
        assertFalse(shows("되돌리기", exact = true))
        assertTrue(shows("모든 호봉 % 올리기", exact = true))
    }

    @Test fun theRaiseDarkBig() = shoot("rvj_263_scale_raised_folded_dark_200", DARK, 2f) {
        raised().invoke(this)
        assertTrue(appears("모든 호봉을 3.5% 올렸어요"))
    }

    // ── [RVJ-78] 상여 '− 100% +' ──

    @Test fun theBonusStepperHasRoom() = shoot("rvj_264_bonus_stepper_folded_dark_200", DARK, 2f) {
        content { AddBonusDialog(onDismiss = {}, onAdd = { _, _, _, _, _, _, _ -> }) }
        settle()
        tapText("한 번에 기본급의 늘리기", exact = true)
        settle()
        assertTrue(shows("110%", exact = true))
        assertEquals(true, shows("한 번에 기본급의 줄이기", exact = true))
    }

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
