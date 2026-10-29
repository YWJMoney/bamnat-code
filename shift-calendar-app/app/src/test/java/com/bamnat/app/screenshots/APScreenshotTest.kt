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
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.LeadLine
import com.bamnat.app.company.Opinion
import com.bamnat.app.company.PayFind
import com.bamnat.app.company.PayWay
import com.bamnat.app.company.VoteResult
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.company.BaseAskSheet
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyPayScreen
import com.bamnat.app.ui.company.CompanyPayViewModel
import com.bamnat.app.ui.company.PayFindLine
import com.bamnat.app.ui.company.PayFindSearch
import com.bamnat.app.ui.company.PayFindSheet
import com.bamnat.app.ui.company.PayWayCard
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
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

/**
 * [PAYF-01]~[PAYF-07] 1.29 묶음 AP (사용자 2026-10-27), numbers 1230~1249: the pay setup's first question is the box to find
 * the company's pay way (the phone's rows at once, the server's laid over them, never a false "없어요" while it is slow),
 * using it brings the calculation and asks only the 기본급, the cycle's "급여 계산 방식도 같이 넣을까요?", and 맞아요·달라요 on
 * the pay way in use.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class APScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    @After fun forget() {
        PayFindSearch.clear()
        com.bamnat.app.ui.company.payGiveUpMs = com.bamnat.app.ui.company.PAY_GIVE_UP_MS
    }

    /** The made-up server: (주)예시정밀's 대표 주기 has a pay way (맞아요 4명), 예시정밀 2공장's none. */
    private inner class Leads(private val base: FakeCompanyApi) : CompanyApi by base {
        var slow = false
        val votes = mutableListOf<Pair<Boolean, String?>>()
        private val lead = LeadLine("s1", "생산팀", true, listOf("주간", "주간", null, null, "야간", "야간", null, null), null, setOf("휴무"),
            pay = true, ok = 4, bad = 1)

        override suspend fun search(query: String): List<CompanyHit> {
            if (slow) delay(60_000)
            return base.search(query).mapIndexed { i, h ->
                if (i == 0) h.copy(users = 7, lead = lead) else h.copy(users = 2, lead = lead.copy(setup = "s2", dept = "포장팀", pay = false, ok = 1, bad = 0))
            }
        }

        override suspend fun company(id: String): CompanyPage {
            val p = base.company(id)
            return p.copy(setups = p.setups.map { s -> if (s.id == "s1") s.copy(lead = true, opinion = Opinion(4, 1)) else s })
        }

        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult {
            votes += ok to why
            return base.vote(device, company, setup, ok, verified, why)
        }
    }

    private fun leads(): Leads = Leads(company).also { l ->
        companyOn()
        c.companyApi = l
    }

    /** A pay with no 기본급 of its own yet, as a company's rules bring it. */
    private fun bare() = PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline())

    private fun Screen.box(body: @androidx.compose.runtime.Composable () -> Unit) {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) { body() }
        }
        settle()
    }

    /** The pay screen the first time (no pay yet) on the main calendar: the company's pay way is asked first. */
    private fun firstPay() = payScreen(Tier.PRO, withProfile = false) { vm -> PayScreen(vm, onBack = {}, companyFind = true) }

    // ---- [PAYF-01] 급여 설정 첫 질문은 '우리 회사 급여 방식 찾기' 칸 ----
    @Test fun thePaySetupStartsWithTheSearchBox() {
        leads()
        shoot("1230_ap_pay_first_search", PHONE_393, firstPay())
    }

    @Test fun theSearchBoxAsTheFirstQuestionChecks() = shoot("1231_ap_pay_first_search_small", SMALL_PHONE, 2f) {
        leads()
        firstPay()()
        assertTrue(appears("우리 회사 급여 방식 찾기", exact = true))
        assertTrue(appears("회사 이름", exact = true))
        assertTrue(appears("직접 만들게요", exact = true))
    }

    @Test fun typingListsThePayWays() = shoot("1232_ap_pay_find_results", PHONE_393) {
        leads()
        firstPay()()
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        assertTrue(appears("(주)예시정밀", exact = true))
        assertTrue(appears("급여 방식 있음 · 맞아요 4명", exact = true))
        assertTrue(appears("급여 방식은 아직 없어요", exact = true))
        // 결정 D79: no value of the pay before it is used.
        assertTrue(gone("상여", exact = false))
    }

    // ---- [PAYF-02] 느린 서버: 휴대폰 목록은 바로, "없어요"는 서버가 답한 뒤에만 ----
    @Test fun aSlowServerNeverSaysNone() = shoot("1233_ap_pay_find_slow", PHONE_393_NIGHT) {
        leads().slow = true
        // The answer still coming after the slow line (the screenshot's settling moves the clock by seconds at a time).
        com.bamnat.app.ui.company.payGiveUpMs = 10 * 60_000L
        val row = PublicCycles.all.first { it.company.length >= 3 }
        box { PayFindSheet(onDismiss = {}) }
        typeInto("회사 이름", row.company)
        compose.mainClock.advanceTimeBy(3_000)
        waitFor(100)
        assertTrue(appears(row.company, exact = false))
        assertTrue(appears("서버가 조금 느려요. 찾는 대로 바로 보여 드릴게요", exact = true))
        assertTrue(gone("급여 방식은 아직 없어요. 직접", exact = false))
    }

    // ---- [PAYF-01][PAYF-04] 이 급여 방식 쓰기: 다시 묻지 않고 금액만 ----
    @Test fun usingThePayWayAsksOnlyTheAmounts() = shoot("1234_ap_company_pay_amounts", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(null, keepUndo = false) }
        // [PAYG-01] 1.30 BA: chosen only; the way is in use once its amounts are saved.
        val ready = runBlocking { PayFind(c).use("c1", "s1") } as PayFind.Used.Ready
        assertNull(c.payWays.way.value)
        assertNull(c.payStore.profile.value)
        // As the first setup leaves a coworkers' setup with its pay for the amounts screen.
        c.companyStore.pendingPay = ready.way.text
        val vm = CompanyPayViewModel(c)
        content { CompanyPayScreen(vm, onBack = {}, onByHand = {}) }
        settle()
        assertTrue(gone("이 회사 급여 설정을 쓸까요?", exact = true))
        assertTrue(appears("거의 다 됐어요! 내 금액만 넣어요", exact = true))
        // [PAYG-08] 1.30 BA: the company's 통상시급 hours shown first, open.
        reveal("회사 규칙 180시간", exact = true)
    }

    @Test fun usingItOnAPayAsksTheBase() = shoot("1235_ap_pay_find_base", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        var saved: PayProfile? = null
        box { PayFindSheet(onDismiss = {}, onSaved = { saved = it }) }
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        tapText("(주)예시정밀", exact = true)
        tapText("이 급여 방식 쓰기", exact = true)
        // [PAYG-01] 1.30 BA: nothing in until 이대로 계산 with the 기본급.
        assertTrue(appears("기본급만 넣으면 끝나요", exact = true))
        assertNull(c.payWays.way.value)
        typeInto("월 기본급", "2500000")
        tapText("이대로 계산", exact = true)
        assertEquals("(주)예시정밀", c.payWays.way.value?.company)
        assertEquals(2_500_000L, BaseAsk.known(saved, Today.month()))
        assertFalse(BaseAsk.needed(saved, Today.month()))
    }

    // ---- [PAYF-04] 쓰고 있는 급여 방식에 맞아요·달라요, 달라요는 고쳐서 제안하기로 ----
    @Test fun thePayWayInUseHasOkAndDiffers() = shoot("1236_ap_pay_way_card", PHONE_393) {
        val l = leads()
        c.payWays.use(PayWay("c1", "(주)예시정밀", "생산팀", "s1", null, null))
        box { PayWayCard(bare(), update = {}) }
        assertTrue(appears("‘(주)예시정밀 · 생산팀’ 급여 방식을 쓰고 있어요", exact = true))
        // [PAYG-04] 1.30 BA: the 기본급 is asked by the pay screen's one card, not by a line here.
        assertTrue(gone("기본급을 넣어야 예상 급여가 맞아요", exact = true))
        assertTrue(appears("맞았어요", exact = true))
        assertTrue(appears("달라요", exact = true))
        assertTrue(appears("고쳐서 제안하기", exact = true))
        tapText("달라요", exact = true)
        tapText("달라요 보내기", exact = true)
        assertEquals(false to "order", l.votes.last())
        assertEquals(false, c.payWays.way.value?.vote)
        // 달라요 goes straight on to the company's values, to send what ours is.
        assertTrue(appears("다른 점 말하기", exact = true))
    }

    @Test fun thePayWayCardAsItShows() = shoot("1241_ap_pay_way_card_only", SMALL_PHONE, 2f) {
        leads()
        c.payWays.use(PayWay("c1", "(주)예시정밀", "생산팀", "s1", null, null))
        box { PayWayCard(bare(), update = {}) }
        reveal("토론 보기", exact = true)
    }

    // ---- [PAYF-03] 'n명이 쓰고 있어요'로 근무주기를 넣을 때 급여도 넣을까요 ----
    @Test fun theCycleAsksAboutThePayOnce() = shoot("1237_ap_with_pay_ask", PHONE_393) {
        leads()
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, openPhasesFor = "s1") }
        settle()
        assertTrue(appears("급여 계산 방식도 같이 넣을까요?", exact = true))
        assertTrue(appears("네, 같이 넣을게요", exact = true))
        assertTrue(appears("근무주기만 넣을게요", exact = true))
    }

    @Test fun theCycleAloneLeavesThePay() = shoot("1238_ap_with_pay_answers", PHONE_393) {
        leads()
        runBlocking { c.scheduleRepository.ensureDefaults() }
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        val vm = CompanyDetailViewModel(c, "c1")
        settle()
        val page = runBlocking { c.companyApi.company("c1") }
        val s1 = page.setups.first { it.id == "s1" }
        var done = false
        vm.apply(page, s1, null, withPay = false, done = { done = true }, failed = { error(it) })
        waitFor(200)
        assertTrue(done)
        assertNull(c.payWays.way.value)
        assertTrue(c.payStore.profile.value?.rules == bare().rules)
        // With the pay: [PAYG-03] 1.30 BA the cycle is in, the pay waits for its 기본급 in one sheet.
        done = false
        vm.apply(page, s1, null, withPay = true, done = { done = true }, failed = { error(it) })
        waitFor(200)
        assertTrue(done)
        assertNull(c.payWays.way.value)
        assertTrue(c.payStore.profile.value?.rules == bare().rules)
        val profile = c.payStore.profile.value
        box { PayWayCard(profile, update = {}) }
        assertTrue(appears("‘(주)예시정밀 · 생산팀’ 근무주기를 넣었어요", exact = true))
        assertTrue(appears("기본급만 넣으면 끝나요", exact = true))
    }

    // ---- [PAYF-06] 기본급은 꼭, 통상시급 기준 시간은 선택 ----
    @Test fun theBaseWithTheHoursBehindTheOrdinaryWage() = shoot("1239_ap_base_ask_hours", PHONE_393) {
        var saved: Pair<Long, Int?>? = null
        content { BaseAskSheet(bare(), Today.month(), onDismiss = {}) { b, h -> saved = b to h } }
        settle()
        assertTrue(appears("기본급을 확인해 주세요", exact = true))
        typeInto("월 기본급", "2090000")
        tapText("통상시급 기준 시간도 알아요 (선택)", exact = true)
        typeInto("통상시급을 알면 (선택)", "10000")
        assertTrue(appears("월 209시간으로 계산해요", exact = true))
        tapText("이대로 계산", exact = true)
        assertEquals(2_090_000L to 209, saved)
    }

    // ---- [PAYF-01] 손으로 만든 급여에도 검색 칸 한 번 ----
    @Test fun aPayMadeByHandStillFindsTheCompany() = shoot("1240_ap_pay_find_line", PHONE_393) {
        leads()
        runBlocking { c.payStore.save(bare(), keepUndo = false) }
        box { PayFindLine() }
        assertTrue(appears("우리 회사 급여 방식이 있나요?", exact = true))
        tapText("회사 이름으로 찾기", exact = true)
        assertTrue(appears("우리 회사 급여 방식 찾기", exact = true))
        assertNotNull(c.payStore.profile.value)
    }

    // ---- [PAYF-01] 설정 고치기가 어느 쪽으로 열리든 검색 칸이 먼저 ----
    private fun withPay() = payScreen(Tier.PRO, withProfile = true) { vm -> PayScreen(vm, onBack = {}, companyFind = true) }

    @Test fun editingTheSetupStartsWithTheSearchBox() = shoot("1242_ap_edit_setup_search", PHONE_393) {
        leads()
        withPay()()
        tapText("처음부터 설정", scrolls = 40)
        assertTrue(appears("우리 회사 급여 방식 찾기", exact = true))
        assertTrue(appears("회사 이름", exact = true))
        assertTrue(appears("직접 만들게요", exact = true))
    }

    private fun Screen.wizard(onDone: (PayProfile) -> Unit = {}) {
        val p = runBlocking { com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile() }
        runBlocking { c.payStore.save(p, keepUndo = false) }
        content { com.bamnat.app.ui.pay.PayWizard(initial = p, onCancel = {}, onDone = onDone, companyFind = true) }
        settle()
    }

    @Test fun theQuestionSetupHasTheSameBox() = shoot("1243_ap_wizard_search", PHONE_393) {
        leads()
        wizard()
        assertTrue(appears("우리 회사 급여 방식 찾기", exact = true))
        assertTrue(appears("없으면 직접 만들어요", exact = true))
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        assertTrue(appears("급여 방식 있음 · 맞아요 4명", exact = true))
    }

    @Test fun theQuestionSetupUsesTheWayAndAsksTheBase() = shoot("1244_ap_wizard_use", PHONE_393_NIGHT) {
        leads()
        var done: PayProfile? = null
        wizard { done = it }
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        tapText("(주)예시정밀", exact = true)
        assertTrue(appears("‘(주)예시정밀’ 급여 방식을 쓸까요?", exact = true))
        // The wizard's own 다음 waits while the box's steps are on; 이전 goes back to the box.
        assertTrue(gone("다음", exact = true))
        tapText("이 급여 방식 쓰기", exact = true)
        assertTrue(appears("기본급만 넣으면 끝나요", exact = true))
        typeInto("월 기본급", "2700000")
        tapText("이대로 계산", exact = true)
        assertEquals(2_700_000L, BaseAsk.known(done, Today.month()))
        assertEquals("s1", c.payWays.way.value?.setup)
    }

    @Test fun theQuestionSetupBoxLarge() = shoot("1245_ap_wizard_search_large", SMALL_PHONE, 2f) {
        leads()
        wizard()
        assertTrue(appears("우리 회사 급여 방식 찾기", exact = true))
    }
}
