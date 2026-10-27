package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyDraft
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.LeadLine
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.PayWay
import com.bamnat.app.company.commit
import com.bamnat.app.company.pendingWays
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyPayScreen
import com.bamnat.app.ui.company.CompanyPayViewModel
import com.bamnat.app.ui.company.PayFindSearch
import com.bamnat.app.ui.company.PayFindSheet
import com.bamnat.app.ui.company.PayWayCard
import com.bamnat.app.ui.pay.BaseNeededCard
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.app.ui.setup.SetupPaySheet
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * [PAYG-01]~[PAYG-13] 1.30 묶음 BA (사용자 2026-10-27), numbers 1300~1319: a company's pay way goes in only with its 기본급, all at
 * once ("이대로 계산"), and closing before that changes nothing; one "기본급만 넣으면 끝나요" everywhere the 기본급 is missing; 자세히
 * 고치기 where it can be seen; the setup's "급여 계산도 들어 있어요. 쓸까요?"; and the pay's first question opening on the company
 * the user already works for.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BAScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    @After fun forget() {
        PayFindSearch.clear()
        val ways = c.companyOf(PersonalCalendar.MAIN).pendingWays
        ways.cancel()
        ways.forgetDropped()
        ways.hoursAsked()
    }

    /** The made-up server: (주)예시정밀's 대표 주기 has a pay way (맞아요 4명). */
    private inner class Leads(private val base: FakeCompanyApi) : CompanyApi by base {
        private val lead = LeadLine("s1", "생산팀", true, listOf("주간", "주간", null, null, "야간", "야간", null, null), null, setOf("휴무"),
            pay = true, ok = 4, bad = 1)

        override suspend fun search(query: String): List<CompanyHit> =
            base.search(query).mapIndexed { i, h -> if (i == 0) h.copy(users = 7, lead = lead) else h }
    }

    private fun leads(): Leads = Leads(company).also { l ->
        companyOn()
        c.companyApi = l
    }

    /** A pay with no 기본급 of its own yet. */
    private fun bare() = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline())

    private fun way(source: PendingWay.Source) = PendingWay("c1", "(주)예시정밀", "생산팀", "s1", company.file, source)

    private fun Screen.box(body: @androidx.compose.runtime.Composable () -> Unit) {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) { body() }
        }
        settle()
    }

    // ---- [PAYG-01][PAYG-02] 이 급여 방식 쓰기: 기본급 칸은 비어서, 비우면 못 넘어가고 아무것도 안 바뀜 ----
    @Test fun usingTheWayWaitsForTheBase() = shoot("1300_ba_use_base_empty", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        box { PayFindSheet(onDismiss = {}) }
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        tapText("(주)예시정밀", exact = true)
        tapText("이 급여 방식 쓰기", exact = true)
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        // Empty, and 이대로 계산 does nothing while it is.
        tapText("이대로 계산", exact = true)
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        assertNull(c.payWays.way.value)
        assertEquals(bare().rules, c.payStore.profile.value?.rules)
        assertNull(c.companyStore.draft.value)
    }

    @Test fun closedBeforeTheBaseChangesNothing() = shoot("1301_ba_skip_nothing_in", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        c.companyOf(PersonalCalendar.MAIN).pendingWays.offer(way(PendingWay.Source.APPLY))
        c.payStore.profile.value.let { p -> box { PayWayCard(p, update = {}) } }
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        tapText("급여 방식은 넣지 않을게요", exact = true)
        assertTrue(shows("‘(주)예시정밀’ 급여 방식은 넣지 않았어요. 급여는 그대로예요.", exact = true))
        assertTrue(shows("다시 넣기", exact = true))
        assertNull(c.payWays.way.value)
        assertEquals(bare().rules, c.payStore.profile.value?.rules)
    }

    // ---- [PAYG-03] 'n명이 쓰고 있어요'로 근무주기와 급여를 같이: 근무주기는 넣고, 급여는 기본급 시트 하나로 ----
    @Test fun theCycleWithItsPayAsksTheBaseOnce() = shoot("1302_ba_cycle_with_pay", PHONE_393) {
        leads()
        runBlocking { c.scheduleRepository.ensureDefaults() }
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        val vm = CompanyDetailViewModel(c, "c1")
        settle()
        val page = runBlocking { c.companyApi.company("c1") }
        var done = false
        vm.apply(page, page.setups.first { it.id == "s1" }, null, withPay = true, done = { done = true }, failed = { error(it) })
        waitFor(200)
        assertTrue(done)
        assertNull("the pay waits for its 기본급", c.payWays.way.value)
        c.payStore.profile.value.let { p -> box { PayWayCard(p, update = {}) } }
        assertTrue(shows("‘(주)예시정밀 · 생산팀’ 근무주기를 넣었어요", exact = true))
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        typeInto("월 기본급", "2600000")
        tapText("이대로 계산", exact = true)
        waitFor(200)
        assertEquals("s1", c.payWays.way.value?.setup)
        assertEquals(2_600_000L, BaseAsk.known(c.payStore.profile.value, Today.month()))
    }

    // ---- [PAYG-07] 첫 설정에서 광장 회사를 고르면 "급여 계산도 들어 있어요. 쓸까요?" ----
    private fun setupWithPay() {
        leads()
        runBlocking { c.scheduleRepository.ensureDefaults() }
        runBlocking { c.payStore.save(null, keepUndo = false) }
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 1, true, Today.instant()))
        c.companyStore.pendingPay = company.file
    }

    @Test fun theFirstSetupAsksToUseThePay() = shoot("1303_ba_setup_pay_use", PHONE_393) {
        setupWithPay()
        var now = false
        content { SetupPaySheet(companyPay = true, onNow = { now = true }, onLater = {}) }
        settle()
        assertTrue(shows("달력을 채웠어요. ‘(주)예시정밀 · 생산팀’ 급여 계산도 들어 있어요. 쓸까요?", exact = true))
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        typeInto("월 기본급", "2700000")
        tapText("이대로 계산", exact = true)
        waitFor(200)
        assertTrue(now)
        assertEquals(2_700_000L, BaseAsk.known(c.payStore.profile.value, Today.month()))
        assertEquals("s1", c.payWays.way.value?.setup)
        assertNull(c.companyStore.pendingPay)
    }

    @Test fun theFirstSetupSheetAtNight() = shoot("1304_ba_setup_pay_night", PHONE_393_NIGHT) {
        setupWithPay()
        content { SetupPaySheet(companyPay = true, onNow = {}, onLater = {}) }
        settle()
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        reveal("나중에", exact = true)
        assertTrue(shows("급여 방식은 넣지 않을게요", exact = true))
    }

    @Test fun notThisCompanysPayLeavesNothingToAsk() = shoot("1305_ba_setup_pay_skip", PHONE_393) {
        setupWithPay()
        var later = false
        content { SetupPaySheet(companyPay = true, onNow = {}, onLater = { later = true }) }
        settle()
        tapText("급여 방식은 넣지 않을게요", exact = true)
        assertTrue(later)
        assertNull(c.companyStore.pendingPay)
        assertNull(c.payStore.profile.value)
        assertNull(c.payWays.way.value)
    }

    // ---- [PAYG-04] 기본급 없는 급여 화면: 0원 대신 '기본급만 넣으면 끝나요' 카드 하나 ----
    @Test fun aPayWithNoBaseAsksItInsteadOfZero() = shoot("1306_ba_result_no_base", PHONE_393) {
        payScreen(Tier.PRO, withProfile = true, adjust = { it.copy(base = ValueTimeline()) })()
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        assertFalse(shows("항목별 내역", exact = true))
        assertFalse(shows("0원", exact = true))
    }

    @Test fun theBaseInShowsTheMonth() = shoot("1318_ba_result_base_in", PHONE_393) {
        payScreen(Tier.PRO, withProfile = true, adjust = { it.copy(base = ValueTimeline()) })()
        typeInto("월 기본급", "2500000")
        tapText("예상 급여 보기", exact = true)
        waitFor(200)
        assertFalse(shows("기본급만 넣으면 끝나요", exact = true))
        reveal("항목별 내역", exact = true)
    }

    @Test fun theBaseCardLargeText() = shoot("1307_ba_base_card_large", SMALL_PHONE, 2f) {
        box { BaseNeededCard(bare(), Today.month(), onSave = { _, _ -> }, companyHours = 180, companyName = "(주)예시정밀") }
        assertTrue(shows("기본급만 넣으면 끝나요", exact = true))
        reveal("회사 규칙 180시간", exact = true)
    }

    // ---- [PAYG-05] 자세히 고치기: 급여 화면 위와 회사 규칙 카드에서 ----
    private fun withPay() = payScreen(Tier.PRO, withProfile = true) { vm -> PayScreen(vm, onBack = {}, companyFind = true) }

    @Test fun theDetailedEditFromTheTop() = shoot("1308_ba_detailed_menu", PHONE_393) {
        leads()
        withPay()()
        tapText("급여 설정 고치기", exact = true)
        // The menu is a popup of its own, outside the window the screen reads: found through the compose rule.
        compose.onNodeWithText("처음부터 설정 (회사 급여 방식 찾기)").assertExists()
        compose.onNodeWithText("회사 규칙 (가산율·수당 기준)").assertExists()
        compose.onNodeWithText("자세히 고치기 (하나씩 묻기)").performClick()
        settle()
        assertTrue(shows("급여 계산 설정", exact = true))
        assertTrue(shows("이전", exact = true) || shows("다음", exact = true) || shows("없으면 직접 만들어요", exact = true))
    }

    @Test fun theDetailedEditOpens() = shoot("1309_ba_detailed_opens", PHONE_393) {
        leads()
        withPay()()
        tapText("자세히 고치기", scrolls = 40)
        assertTrue(shows("이전", exact = true) || shows("다음", exact = true) || shows("없으면 직접 만들어요", exact = true))
        // Opened on the questions, never jumping to a company's way.
        assertFalse(shows("근무주기에 넣은 회사라 먼저 찾아 두었어요.", exact = true))
    }

    @Test fun theEasySetupLinksTheDetailedOne() = shoot("1310_ba_quick_detailed_link", PHONE_393) {
        leads()
        withPay()()
        tapText("처음부터 설정", scrolls = 40)
        assertTrue(shows("지금 설정을 하나씩 고치려면 자세히 고치기", exact = true))
    }

    @Test fun aPayUsingAWayOpensOnIt() = shoot("1311_ba_using_step", PHONE_393) {
        leads()
        c.payWays.use(PayWay("c1", "(주)예시정밀", "생산팀", "s1", null, null))
        withPay()()
        tapText("처음부터 설정", scrolls = 40)
        assertTrue(shows("‘(주)예시정밀 · 생산팀’ 급여 방식을 쓰고 있어요", exact = true))
        assertTrue(shows("자세히 고치기", exact = true))
        assertTrue(shows("다른 회사 급여 방식 찾기", exact = true))
    }

    // ---- [PAYG-06] 근무주기에 넣은 회사면 바로, 광장에 없으면 직접 만들기 ----
    private fun firstPay() = payScreen(Tier.PRO, withProfile = false) { vm -> PayScreen(vm, onBack = {}, companyFind = true) }

    @Test fun theCompanyKnownOpensOnItsWay() = shoot("1312_ba_start_confirm", PHONE_393) {
        leads()
        c.companyStore.setDraft(CompanyDraft("(주)예시정밀", ""))
        firstPay()()
        waitFor(400)
        assertTrue(shows("근무주기에 넣은 회사라 먼저 찾아 두었어요.", exact = true))
        assertTrue(shows("이 급여 방식 쓰기", exact = true))
    }

    @Test fun aCompanyNotOnThePlazaIsMadeByHand() = shoot("1313_ba_start_by_hand", PHONE_393) {
        leads()
        c.companyStore.setDraft(CompanyDraft("가나다라정밀", ""))
        firstPay()()
        waitFor(400)
        assertTrue(shows("‘가나다라정밀’ 직접 만들기", exact = true))
    }

    // ---- [PAYG-08] 회사 통상시급 기준 시간이 먼저, 다르면 의견으로 ----
    @Test fun otherHoursAreOfferedAsAnOpinion() = shoot("1314_ba_hours_opinion", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        val scope = c.companyOf(PersonalCalendar.MAIN)
        val w = way(PendingWay.Source.FIND)
        assertEquals(180, w.hours)
        scope.pendingWays.offer(w)
        assertTrue(runBlocking { scope.commit(c, w, 2_800_000, 209, Today.month()) })
        assertEquals(w to 209, scope.pendingWays.hoursAsk.value)
        c.payStore.profile.value.let { p -> box { PayWayCard(p, update = {}) } }
        assertTrue(shownDialogs().isNotEmpty())
    }

    @Test fun theBaseFieldShowsTheCompanysHours() = shoot("1315_ba_company_hours", PHONE_393_NIGHT) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        c.companyOf(PersonalCalendar.MAIN).pendingWays.offer(way(PendingWay.Source.APPLY))
        c.payStore.profile.value.let { p -> box { PayWayCard(p, update = {}) } }
        assertTrue(shows("통상시급 기준 시간: 회사 규칙 180시간", exact = true))
        tapText("209시간", exact = true)
        assertTrue(shows("회사 규칙은 180시간이에요. 209시간으로 계산하고, 넣은 뒤 같은 회사 분들께 의견으로 보낼지 여쭤볼게요.", exact = true))
    }

    // ---- [PAYG-11] 금액 화면: 회사 공통 항목, 사람마다 다른 수당만, 형태·시작 달은 접어서 ----
    @Test fun theAmountsScreenAsksOnlyWhatIsMine() = shoot("1316_ba_amounts_mine", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(null, keepUndo = false) }
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 1, true, Today.instant()))
        c.companyStore.pendingPay = company.file
        val vm = CompanyPayViewModel(c)
        content { CompanyPayScreen(vm, onBack = {}, onByHand = {}) }
        settle()
        tapText("네, 이 설정으로 할게요", exact = true)
        assertFalse(shows("대략이에요", exact = true))
        reveal("시급·일당이거나 받기 시작한 달이 다르면", exact = true)
        assertNull("in use only once saved", c.payWays.way.value)
    }

    @Test fun theAmountsScreenLargeText() = shoot("1317_ba_amounts_large", SMALL_PHONE, 2f) {
        leads()
        runBlocking { c.payStore.save(null, keepUndo = false) }
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 1, true, Today.instant()))
        c.companyStore.pendingPay = company.file
        val vm = CompanyPayViewModel(c)
        content { CompanyPayScreen(vm, onBack = {}, onByHand = {}) }
        settle()
        assertNotNull(c.companyStore.pendingPay)
    }
}
