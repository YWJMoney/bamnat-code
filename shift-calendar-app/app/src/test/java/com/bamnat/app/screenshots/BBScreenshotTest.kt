package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.PayWay
import com.bamnat.app.company.SentReport
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.company.CompanyBoardScreen
import com.bamnat.app.ui.company.CompanyBoardViewModel
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyPayScreen
import com.bamnat.app.ui.company.CompanyPayViewModel
import com.bamnat.app.ui.company.LocalCompanyCalendar
import com.bamnat.app.ui.company.PayWayCard
import com.bamnat.app.ui.company.PlazaCalendarChips
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.bamnat.app.company.pendingWays
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * [CALI-01]~[CALI-08] 1.30 묶음 BB (사용자 2026-10-27: "다른 달력으로 회사를 선택하면 전체로 적용이 되고 있다. 달력은 개별적으로
 * 적용이 되게 해야 한다", "내가 보낸 규칙도 해당 달력에 맞춰 보여져야 한다. 온전한 독립 달력이다"), numbers 1320~1326: an added
 * calendar's company is its own: its pay way card, its 이 설정 쓰기, its coworkers' pay, its 광장 (chips above when there are
 * two calendars or more) and the rules it sent; 내 달력 keeps its own throughout. Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BBScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    private fun applied(id: String, name: String, dept: String, setup: String) =
        AppliedSetup(id, name, dept, setup, 2, true, Instant.parse("2026-10-01T00:00:00Z"))

    private fun bare() = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline())

    private fun Screen.on(calendar: String, body: @Composable () -> Unit) {
        content {
            CompositionLocalProvider(LocalCompanyCalendar provides calendar) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) { body() }
            }
        }
        settle()
    }

    /** 내 달력 in 공장A, the added part-time calendar in (주)예시정밀; the second calendar's id. */
    private fun twoCompanies(): String {
        companyOn()
        val b = addedCalendar()
        c.companyStore.setApplied(applied("c-a", "공장A", "", "s-a"))
        c.companyStore.setMember("c-a", "공장A")
        c.companyOf(b).store.setApplied(applied("c1", "(주)예시정밀", "생산팀", "s1"))
        c.companyOf(b).store.setMember("c1", "(주)예시정밀")
        return b
    }

    private fun Screen.board(calendar: String): CompanyBoardViewModel {
        val vm = CompanyBoardViewModel(c, calendar)
        content {
            CompositionLocalProvider(LocalCompanyCalendar provides calendar) {
                CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onRates = {},
                    header = { PlazaCalendarChips(c, calendar, onPick = {}, Modifier.padding(horizontal = 16.dp)) })
            }
        }
        settle()
        return vm
    }

    // ---- [CALI-04] 둘째 달력의 급여: 그 달력의 회사 급여 방식 ----
    @Test fun theSecondCalendarsPayHasItsOwnWay() = shoot("1320_bb_second_pay_way", PHONE_393) {
        val b = twoCompanies()
        c.companyOf(b).payWays.use(PayWay("c1", "(주)예시정밀", "생산팀", "s1"))
        on(b) { PayWayCard(bare(), update = {}) }
        assertTrue(appears("‘(주)예시정밀 · 생산팀’ 급여 방식을 쓰고 있어요", exact = true))
        assertTrue(gone("공장A", exact = false))
        assertNull("내 달력의 급여 방식은 그대로", c.payWays.way.value)
    }

    // ---- [CALI-03] 둘째 달력에서 'n명이 쓰고 있어요'로 이 설정 쓰기: 내 달력의 회사는 그대로 ----
    @Test fun usingASetupInTheSecondCalendarKeepsTheMainOne() = shoot("1321_bb_second_apply_keeps_main", PHONE_393) {
        companyOn()
        val b = addedCalendar()
        c.companyStore.setApplied(applied("c-a", "공장A", "", "s-a"))
        runBlocking { c.calendars.pay(b).save(bare(), keepUndo = false) }
        val vm = CompanyDetailViewModel(c, "c1", b)
        settle()
        val page = runBlocking { c.companyApi.company("c1") }
        var done = false
        vm.apply(page, page.setups.first { it.id == "s1" }, null, withPay = true, done = { done = true }, failed = { error(it) })
        waitFor(300)
        assertTrue(done)
        assertEquals("s1", c.companyOf(b).store.applied.value?.setup)
        // [PAYG-01] 1.30 BA: the second calendar's pay way waits for its 기본급; 내 달력's has nothing waiting.
        assertEquals("s1", c.companyOf(b).pendingWays.pending.value?.setup)
        assertNull(c.companyOf(com.bamnat.app.data.PersonalCalendar.MAIN).pendingWays.pending.value)
        assertEquals("공장A", c.companyStore.applied.value?.company)
        assertNull(c.payWays.way.value)
        assertTrue(c.companyOf(b).payWays.askBase.value)
        assertFalse(c.payWays.askBase.value)
        c.calendars.pay(b).profile.value.let { p -> on(b) { PayWayCard(p, update = {}) } }
        assertTrue(appears("기본급만 넣으면 끝나요", exact = true))
    }

    // ---- [CALI-08] 광장 위 달력 칩: 달력마다 그 회사 ----
    @Test fun thePlazaSaysWhichCalendarsCompany() = shoot("1322_bb_plaza_calendar_chips", PHONE_393) {
        val b = twoCompanies()
        board(b)
        assertTrue(appears("편의점 알바 · (주)예시정밀", exact = true))
        assertTrue(appears("${PersonalCalendar.MAIN_NAME} · 공장A", exact = true))
    }

    // ---- [CALI-08] 내 활동: 이 달력에서 보낸 규칙만, 다른 달력의 것은 한 줄로 ----
    @Test fun myActivityIsTheCalendarsOwn() = shoot("1323_bb_activity_per_calendar", PHONE_393) {
        val b = twoCompanies()
        c.companyOf(b).store.addSent(SentReport("r2", "라마바전자", "생산1팀", false, Instant.parse("2026-10-11T00:00:00Z")))
        val vm = board(b)
        vm.activity.show()
        waitFor(200)
        assertTrue(appears("‘편의점 알바’에서 보낸 규칙 1", exact = true))
        assertTrue(appears("라마바전자", exact = false))
        assertTrue(gone("(주)예시정밀 · 생산팀 —", exact = false))
        reveal("‘${PersonalCalendar.MAIN_NAME}’에서 보낸 규칙 1개는 그 달력의 광장에서 보여요", exact = true)
    }

    // ---- [CALI-05] 둘째 달력에 동료 설정이 급여와 함께: 그 달력의 금액만 묻기 ----
    @Test fun theSecondCalendarsCoworkersPay() = shoot("1324_bb_second_company_pay", PHONE_393_NIGHT) {
        val b = twoCompanies()
        c.companyOf(b).store.pendingPay = company.file
        val vm = CompanyPayViewModel(c, b)
        content { CompanyPayScreen(vm, onBack = {}, onByHand = {}) }
        settle()
        assertEquals("(주)예시정밀", vm.company)
        assertNotNull(vm.text)
        assertNull("내 달력은 묻지 않아요", c.companyStore.pendingPay)
    }

    // ---- [CALI-07] 달력을 지우면 그 회사도: 칩이 사라지고 내 달력은 그대로 ----
    @Test fun aDeletedCalendarsCompanyGoes() = shoot("1325_bb_after_delete", PHONE_393) {
        val b = twoCompanies()
        c.calendars.delete(b)
        settle()
        assertTrue(c.companyOf(b).isMain)
        assertEquals("공장A", c.companyStore.applied.value?.company)
        board(PersonalCalendar.MAIN)
        assertTrue(gone("편의점 알바", exact = false))
        assertTrue(gone("(주)예시정밀 · ", exact = false))
    }

    // ---- [CALI-08] 큰 글씨·작은 휴대폰에서도 칩은 옆으로 밀어 보기 ----
    @Test fun theChipsOnASmallPhone() = shoot("1326_bb_chips_small_big_text", SMALL_PHONE, 2f) {
        val b = twoCompanies()
        board(b)
        assertTrue(appears("편의점 알바 · (주)예시정밀", exact = true))
    }
}
