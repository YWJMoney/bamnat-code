package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [HOLI-01][PAYS-12][PAYS-14][PAYS-15][PAYX-48] 묶음 V's screenshots, numbers 830~849, each also an end-to-end check (D76):
 * a tap on the screen, then the value saved and the month it comes to. 빨간날에 일하면 in 회사 규칙, the easy pay setup
 * (근무 형태 → 금액 → 빨간날 → 이렇게 계산해요), 명세서로 회사 방식 맞추기 and the 광장 ask after it, 월급날 비교.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val saved get() = c.payStore.profile.value

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val FORM_TITLE = "어떤 근무로 급여를 받나요?"
        /** A September payslip with an allowance the sample setup does not have. */
        val SLIP = """
            2026년 9월 급여명세서
            기본급 2,850,000
            야간근로수당 412,000
            교대수당 150,000
            식대 200,000
            지급합계 3,612,000
        """.trimIndent()
    }

    // ---- 374 [HOLI-01][HOLI-03] 빨간날에 일하면, one choice in 회사 규칙 ----
    private fun holidayCards(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        rulesEditor { companyRules }.invoke(this)
        reveal("빨간날(공휴일)에 일하면", exact = true)
        then()
    }

    @Test fun holidayCardsFolded() = shoot("830_v_holiday_cards", FOLDED, holidayCards {
        HolidayWork.entries.forEach { assertTrue("${it.label} card", shows(it.label, exact = true)) }
    })
    @Test fun holidayAsExtraSaves() = shoot("831_v_holiday_as_extra", FOLDED, holidayCards {
        tapText(HolidayWork.AS_EXTRA.label, exact = true)
        reveal("빨간날 근무 1회 금액", exact = true)
        assertTrue(shows("빨간날 야간"))
        tapText("저장", exact = true)
        assertEquals(HolidayWork.AS_EXTRA, saved!!.rules.holidayWork)
        // A night shift on a 빨간날 is one 특근 by the day it starts, its night premium kept.
        assertTrue(saved!!.rules.holidayByShiftStart)
        assertEquals(ExtraKind.SPECIAL_ID, saved!!.rules.publicHolidayAs)
    })
    @Test fun holidayShiftNormalSaves() = shoot("832_v_holiday_shift_normal", FOLDED_NIGHT, holidayCards {
        tapText(HolidayWork.SHIFT_NORMAL.label, exact = true)
        tapText("저장", exact = true)
        assertEquals(HolidayWork.SHIFT_NORMAL, saved!!.rules.holidayWork)
        assertFalse(saved!!.rules.publicHolidaysPaid)
    })
    @Test fun holidayCardsUnfolded() = shoot("833_v_holiday_cards_unfolded", UNFOLDED, holidayCards())

    // ---- 375 [PAYS-12] 급여 간편 설정 ----
    private fun quickSetup(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = false).invoke(this)
        then()
    }

    @Test fun quickSetupFirstQuestion() = shoot("834_v_quick_form", FOLDED, quickSetup {
        assertTrue(shows(FORM_TITLE))
        // The calendar's 주간 08~20 and 야간 20~08 point to 2교대, offered first.
        assertTrue(shows("2교대 · 달력을 보면 이것 같아요"))
        assertFalse("the old setup's first question is behind 자세히 고치기", shows("월급·시급·일당 중에"))
    })

    /** The whole easy setup by taps: 2교대, 기본급, 빨간날은 특근처럼, then 이대로 계산 saves it and the month is worked out. */
    @Test fun quickSetupEndToEnd() = shoot("835_v_quick_confirm", FOLDED, quickSetup {
        tapText("2교대 · 달력을 보면 이것 같아요", exact = true)
        waitFor(400)
        typeInto("월 기본급", "2600000")
        tapText("다음", exact = true)
        assertTrue(shows("빨간날(공휴일)에 일하면 어떻게 받나요?"))
        tapText(HolidayWork.AS_EXTRA.label, exact = true)
        tapText("다음", exact = true)
        assertTrue(shows("이렇게 계산해요", exact = true))
        assertTrue(shows("월 기본급 2,600,000원"))
        assertTrue(shows("빨간날 근무는 특근"))
        tapText("이대로 계산", exact = true)
        waitFor(400)
        assertNotNull(saved)
        val p = saved!!
        assertEquals(2_600_000L, p.base.on(Today.month()).value)
        assertEquals(WageType.MONTHLY, p.wageType)
        assertEquals(Occupation.MANUFACTURING, p.occupation)
        assertEquals(HolidayWork.AS_EXTRA, p.rules.holidayWork)
        // Back on the result: this month is worked out from the setup just made.
        assertTrue(shows("급여 계산", exact = true))
        assertTrue(c.payStore.profile.value != null)
    })

    /** D70: setting up again over a setup keeps what it had (the 1.9 특근 단가, 수당) and changes only the amount. */
    @Test fun quickSetupKeepsWhatItHad() = shoot("836_v_quick_again", FOLDED, {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        val before = saved!!
        tapText("처음부터 설정", scrolls = 40)
        assertTrue(shows(FORM_TITLE))
        // The sample is a nurse setup: its form is picked already.
        assertTrue(shows("간호 듀티"))
        tapText("다음", exact = true)
        typeInto("월 기본급", "3100000")
        tapText("다음", exact = true)
        tapText("다음", exact = true)
        tapText("이대로 계산", exact = true)
        waitFor(400)
        val after = saved!!
        assertEquals(3_100_000L, after.base.on(Today.month()).value)
        assertEquals(before.items.map { it.name }, after.items.map { it.name })
        assertEquals(before.rules.extra[ExtraKind.SPECIAL_ID], after.rules.extra[ExtraKind.SPECIAL_ID])
    })

    @Test fun quickSetupDetailedOpensTheQuestions() = shoot("837_v_quick_detailed", SMALL_PHONE, quickSetup {
        tapText("3교대", exact = true)
        waitFor(400)
        typeInto("월 기본급", "2400000")
        tapText("다음", exact = true)
        tapText("다음", exact = true)
        tapText("자세히 고치기 (수당·계산 방식 하나씩)", exact = true)
        // The question-by-question setup, started from the form and amount picked.
        assertTrue(shows("계산 시작") || shows("다음", exact = true))
        assertFalse(shows("이렇게 계산해요", exact = true))
        back()
        assertTrue("back returns to the easy setup", shows(FORM_TITLE) || shows("이렇게 계산해요", exact = true))
    })

    // ---- 376 [PAYS-14][PAYS-15] 명세서 + 회사 계산 방법 → 회사 규칙, then 광장 ----
    private var shared = false
    private fun slipRules(then: Screen.() -> Unit = {}): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { vm -> PayScreen(vm, onBack = {}, onShareWay = { shared = true }) }.invoke(this)
        tapText("사진으로 회사 방식 맞추기", scrolls = 40)
        then()
    }
    private fun Screen.typeSlip() {
        tapText("글로 붙여 넣기", exact = true)
        typeInto("명세서 글", SLIP)
        tapText("읽기", exact = true)
    }

    @Test fun slipRulesTwoPhotos() = shoot("838_v_slip_two_photos", FOLDED, slipRules {
        assertTrue(shows("1. 급여 명세서"))
        assertTrue(shows("2. 회사 계산 방법 (있으면)"))
        assertTrue(shows("사진은 휴대폰 안에서만 읽고 어디에도 보내지 않아요."))
    })
    @Test fun slipRulesRead() = shoot("839_v_slip_read", FOLDED, slipRules {
        typeSlip()
        assertTrue(shows("2026년 9월 명세서"))
        reveal("맞춰 보기", exact = true)
    })

    /** The payslip read, worked out against September's calendar, applied: the 교대수당 is now in the setup. */
    @Test fun slipRulesAppliedEndToEnd() = shoot("840_v_slip_changes", FOLDED, slipRules {
        val before = saved!!
        typeSlip()
        tapText("맞춰 보기", exact = true)
        waitFor(800)
        assertTrue(shows("이렇게 바꿀게요", exact = true))
        assertTrue(shows("2026년 9월 명세서 3,612,000원"))
        tapText("이대로 적용", exact = true)
        waitFor(400)
        val after = saved!!
        assertTrue("교대수당 added", after.items.any { it.name == "교대수당" })
        assertEquals(2_850_000L, after.base.on(java.time.YearMonth.of(2026, 9)).value)
        assertTrue(before != after)
        // [PAYS-15] Then the 광장 ask, with how it is adopted and what it brings.
        assertTrue(shows("같은 회사 사람들도 바로 쓰게 해 주실래요?"))
    })
    @Test fun slipRulesPlazaAsk() = shoot("841_v_plaza_ask", FOLDED_NIGHT, slipRules {
        typeSlip()
        tapText("맞춰 보기", exact = true)
        waitFor(800)
        tapText("이대로 적용", exact = true)
        waitFor(400)
        assertTrue(shows("이렇게 채택돼요"))
        tapText("광장에 올리기", exact = true)
        assertTrue("광장 report opened", shared)
    })

    /** The easy setup's own way in: a payslip fills the form, the amount and the company's ways on the first setup. */
    @Test fun quickSetupFromSlip() = shoot("842_v_quick_from_slip", FOLDED, quickSetup {
        tapText("모르겠으면 명세서 사진으로 맞추기")
        typeSlip()
        tapText("맞춰 보기", exact = true)
        waitFor(800)
        tapText("이대로 적용", exact = true)
        waitFor(400)
        assertTrue(shows("명세서에서 찾은 회사 방식을 넣었어요"))
        tapText("이대로 계산", exact = true)
        waitFor(400)
        val p = saved!!
        assertEquals(2_850_000L, p.base.on(java.time.YearMonth.of(2026, 9)).value)
        assertTrue(p.items.any { it.name == "교대수당" })
    })

    // ---- 377 [PAYX-48] 월급날 비교: '사용하러 가기' opens the card, and the amount is typed right there ----
    private fun withPay() = runBlocking { c.payStore.save(PayViewModel(c).sampleProfile().copy(rules = companyRules), keepUndo = false) }
    private fun Screen.openPayGap() {
        com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRegistry.get("pay.gap")!!)
        settle()
        waitFor(400)
    }

    /** From 기능 찾기 or a tip: the stats tab, the card in view and the amount asked; saving puts a 급여 line in 돈. */
    @Test fun payGapEndToEnd() = shoot("843_v_pay_gap_add", FOLDED, {
        withPay()
        settle()
        openPayGap()
        assertTrue("the amount is asked at once", shows("들어온 급여 적기", exact = true))
        typeInto("들어온 급여", "3200000")
        tapText("저장", exact = true)
        waitFor(400)
        val lines = runBlocking { c.moneyRepository.book.first().entries }
        assertTrue(lines.any { it.kind == MoneyKind.INCOME && it.amount == 3_200_000L && it.category == MoneyCategories.SALARY })
        reveal("10월 월급날 비교", exact = true)
        assertTrue(shows("들어온 돈 3,200,000원"))
    })
    @Test fun payGapBeforeTheMoney() = shoot("844_v_pay_gap_waiting", FOLDED_NIGHT, {
        withPay()
        settle()
        tab(3, wide = false)
        reveal("10월 월급날 비교", exact = true)
        assertTrue(shows("들어온 급여 적기", exact = true))
    })
    @Test fun payGapBeforeSetup() = shoot("845_v_pay_gap_no_setup", FOLDED, {
        runBlocking { c.payStore.save(null, keepUndo = false) }
        settle()
        openPayGap()
        reveal("10월 월급날 비교", exact = true)
        assertTrue(shows("급여 계산 맞추기", exact = true))
    })

    // ---- 378 at other sizes: 큰 글씨, 어두운 화면, 작은 폰 ----
    @Test fun quickConfirmHugeText() = shoot("846_v_quick_confirm_huge_text", PHONE, fontScale = 2f, quickSetup {
        tapText("2교대 · 달력을 보면 이것 같아요", exact = true)
        waitFor(400)
        typeInto("월 기본급", "2600000")
        tapText("다음", exact = true)
        tapText("다음", exact = true)
        assertTrue(shows("이렇게 계산해요", exact = true))
    })
    @Test fun quickHolidayAmountsDark() = shoot("847_v_quick_holiday_dark", FOLDED_NIGHT, quickSetup {
        tapText("2교대 · 달력을 보면 이것 같아요", exact = true)
        waitFor(400)
        typeInto("월 기본급", "2600000")
        tapText("다음", exact = true)
        tapText(HolidayWork.AS_EXTRA.label, exact = true)
        reveal("빨간날 근무 1회 금액", exact = true)
        assertTrue(shows("빨간날 주간"))
    })
    @Test fun slipRulesSmallPhone() = shoot("848_v_slip_small", SMALL_PHONE, slipRules {
        typeSlip()
        tapText("맞춰 보기", exact = true)
        waitFor(800)
        assertTrue(shows("이렇게 바꿀게요", exact = true))
    })
    @Test fun payGapDialogDark() = shoot("849_v_pay_gap_dialog_dark", FOLDED_NIGHT, {
        withPay()
        settle()
        openPayGap()
        assertTrue(shows("통장에 실제로 들어온 금액이에요(세금·4대보험 뺀 금액)."))
    })
}
