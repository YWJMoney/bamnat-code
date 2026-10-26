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
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.LeadLine
import com.bamnat.app.company.LeadShare
import com.bamnat.app.company.Opinion
import com.bamnat.app.company.VoteResult
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyInlineResults
import com.bamnat.app.ui.company.LeadShareDialog
import com.bamnat.app.ui.company.PayFindSheet
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SPAT-07][SPAT-08][PLAZA-14]~[PLAZA-18] 1.27 묶음 AH (사용자 2026-10-25), numbers 1100~1119: the company name lists what
 * is there as it is typed (the server's 대표 주기 and who uses it, then the public rows, then '여기 없어요, 직접 추가'), a new
 * calendar says '이 설정 쓰기', the one question "이 정보를 다른 사람이 사용해도 될까요?" and what it became, 맞아요·달라요
 * with where it differs, a deleted cycle starts again, and 우리 회사 급여 방식 찾기 — small phone, fold, 200% and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AHScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }

    /** The made-up server with a 대표 주기 on each company found, who uses it, and opinions. */
    private inner class Leads(private val base: FakeCompanyApi) : CompanyApi by base {
        var slow = false
        var doubted = false
        var votes = mutableListOf<Pair<Boolean, String?>>()
        private val lead = LeadLine("s1", "", true, listOf("주간", "주간", null, null, "야간", "야간", null, null), null, setOf("휴무"),
            pay = true, ok = 4, bad = 1)

        override suspend fun search(query: String): List<CompanyHit> {
            if (slow) delay(60_000)
            return base.search(query).mapIndexed { i, h ->
                if (i == 0) h.copy(users = 7, lead = lead.copy(doubted = doubted), sites = 2) else h.copy(users = 2, lead = lead.copy(setup = "s2", dept = "포장팀", pay = false, ok = 1, bad = 0))
            }
        }

        override suspend fun company(id: String): CompanyPage {
            val p = base.company(id)
            return p.copy(setups = p.setups.map { s ->
                if (s.id == "s1") s.copy(lead = !doubted, doubted = doubted,
                    opinion = Opinion(if (doubted) 2 else 4, if (doubted) 4 else 1, mapOf("order" to (if (doubted) 2 else 1), "time" to (if (doubted) 1 else 0), "pay" to (if (doubted) 1 else 0)), mine = "ok"))
                else s.copy(lead = true, opinion = Opinion(1, 0))
            })
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

    private fun Screen.inline(query: String, server: Boolean = true) {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                CompanyInlineResults(query, onServer = if (server) ({ }) else null, onPick = { _, _ -> })
            }
        }
        settle()
    }

    // ---- 436 [SPAT-07] 치는 대로: the server's companies, then the public rows, then 여기 없어요 ----
    @Test fun theFirstSetupListsWhatIsThereAsItIsTyped() = shoot("1100_ah_setup_inline", PHONE_393, fresh(null) {
        leads()
        tapText("순서가 돌아가요")
        typeInto("회사 이름 (선택)", "예시정밀")
        waitFor(400)
        assertTrue(shows("찾은 곳 · 예시정밀", exact = true))
        assertTrue(shows("(주)예시정밀", exact = true))
        assertTrue(shows("7명 쓰고 있어요 · 맞아요 4명 · 급여 방식 있음 · 사업장 2곳", exact = true))
        assertTrue(shows("대표 주기 주주휴휴야야휴휴", exact = false))
        reveal("여기 없어요, 직접 추가", exact = true)
    })

    @Test fun onePressOfTheServerRowOpensItsPage() = shoot("1101_ah_setup_inline_open", PHONE_393, fresh(null) {
        leads()
        tapText("순서가 돌아가요")
        typeInto("회사 이름 (선택)", "예시정밀")
        waitFor(400)
        tapText("(주)예시정밀", exact = true)
        assertTrue(shows("대표 주기 · 찾는 분께 먼저 보여요", exact = true))
        // [SPAT-07] 437: picking in a setup, nothing is in use yet — never '다시 넣기'.
        assertFalse(shows("다시 넣기", exact = true))
    })

    @Test fun theServerSlowThePublicRowsComeAtOnce() = shoot("1102_ah_inline_slow", SMALL_PHONE) {
        leads().slow = true
        // AH-I1: 지에스 is GS, as people say it.
        inline("지에스칼텍스")
        assertTrue(shows("GS칼텍스", exact = false))
        assertTrue(shows("공개 자료", exact = false))
        compose.mainClock.advanceTimeBy(3_000)
        waitFor(100)
        assertTrue(shows("같은 회사 분들 자료는 지금 못 불러왔어요", exact = false))
        assertTrue(shows("여기 없어요, 직접 추가", exact = true))
    }

    @Test fun nothingFoundSaysAddItYourself() = shoot("1103_ah_inline_none", SMALL_PHONE, 2f) {
        leads()
        inline("한별정밀")
        waitFor(400)
        assertTrue(shows("여기 없어요, ‘한별정밀’ 직접 추가", exact = true))
        tapText("여기 없어요, ‘한별정밀’ 직접 추가", exact = true)
        assertTrue(shows("처음으로 회사 정보를 제공해 주세요!", exact = true))
    }

    // ---- 437 [SPAT-07] 새 달력: '이 설정 쓰기', not the main calendar's '다시 넣기' ----
    @Test fun aNewCalendarSaysUseThisNotPutAgain() = shoot("1104_ah_new_calendar_use", FOLDED) {
        leads()
        // The main calendar uses (주)예시정밀 생산팀 already.
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = true, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, onPick = { _, _ -> }) }
        settle()
        assertTrue(shows("이 설정 쓰기", exact = true))
        assertFalse(shows("다시 넣기", exact = true))
        assertFalse(shows("지금 이 설정을 쓰고 있어요", exact = true))
    }

    // ---- 438 [PLAZA-14] 한 번만 묻기와 그 뒤 ----
    private fun Screen.dialog(step: LeadShare.Step) {
        content { LeadShareDialog(step, c.leadShare, onOpen = {}) }
        settle()
    }

    private val pick = LeadShare.Pick("(주)예시정밀", "{}")

    @Test fun theOneQuestion() = shoot("1105_ah_lead_ask", SMALL_PHONE, 2f) {
        dialog(LeadShare.Step.Ask(pick))
        assertTrue(shows("이 정보를 다른 사람이 사용해도 될까요?", exact = true))
        assertTrue(shows("급여 금액, 이름, 연락처, 사진은 보내지 않아요", exact = false))
        assertTrue(shows("같이 쓸게요", exact = true))
        assertTrue(shows("내 달력에만 쓸게요", exact = true))
    }

    @Test fun itBecameTheLead() = shoot("1106_ah_lead_made", PHONE_393) {
        dialog(LeadShare.Step.Made(pick, "(주)예시정밀", "c1"))
        assertTrue(shows("이제 ‘(주)예시정밀’의 대표 주기가 됐어요!", exact = true))
    }

    @Test fun anotherLeadsSoTheSiteIsAsked() = shoot("1107_ah_lead_site", PHONE_393_NIGHT) {
        dialog(LeadShare.Step.NeedSite(pick, "(주)예시정밀", "주주휴휴야야휴휴"))
        assertTrue(shows("어느 사업장에서 일하세요?", exact = true))
        assertTrue(shows("다른 순서(주주휴휴야야휴휴)가 대표예요", exact = false))
        typeInto("사업장 이름 (예: 여수공장)", "여수공장")
        assertTrue(shows("이 사업장으로 나누기", exact = true))
    }

    @Test fun theSiteLeadAndTheSameOrder() = shoot("1108_ah_lead_site_made", SMALL_PHONE) {
        dialog(LeadShare.Step.Made(pick, "(주)예시정밀 · 여수공장", "c1"))
        assertTrue(shows("이제 ‘(주)예시정밀 · 여수공장’의 대표 주기가 됐어요!", exact = true))
        dialog(LeadShare.Step.Agreed(pick, "(주)예시정밀", "c1"))
        assertTrue(shows("새로 올리지 않고 맞아요 한 표를 더했어요", exact = false))
    }

    // ---- 439 [PLAZA-15] 맞아요·달라요, 어디가 ----
    @Test fun opinionsWithWhereItDiffers() = shoot("1109_ah_opinions", FOLDED) {
        val l = leads()
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        reveal("맞아요 4 · 달라요 1 (순서 1)", exact = true)
        assertTrue(shows("맞았어요 ✓", exact = true))
        tapText("달라요", exact = true)
        assertTrue(shows("근무 순서가 달라요", exact = true))
        assertTrue(shows("근무 시간이 달라요", exact = true))
        assertTrue(shows("급여 항목이 달라요", exact = true))
        tapText("근무 시간이 달라요", exact = true)
        tapText("달라요 보내기", exact = true)
        assertEquals(false to "time", l.votes.last())
    }

    @Test fun tooManyDifferentAndItStepsDown() = shoot("1110_ah_doubted", PHONE_393_NIGHT) {
        leads().doubted = true
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        assertTrue(shows("달라요가 많아요 · 다시 확인 중", exact = true))
        reveal("맞아요 2 · 달라요 4 (순서 2 · 시간 1 · 급여 항목 1)", exact = true)
    }

    // ---- 437 [SPAT-08] 주기 삭제는 처음처럼 ----
    @Test fun aDeletedCycleStartsAgain() = shoot("1111_ah_cycle_deleted", PHONE_393) {
        leads()
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        c.companyStore.setMember("c1", "(주)예시정밀")
        settingsPage("내 근무")
        tapText("근무 주기", exact = true)
        tapText("패턴 삭제", exact = true)
        tapText("삭제", exact = true)
        assertTrue(shows("근무주기를 지웠어요", exact = true))
        assertTrue(shows("회사 이름으로 근무주기 찾기", exact = true))
        assertNull(c.companyStore.applied.value)
        assertNull(c.companyStore.member.value)
        assertTrue(runBlocking { c.scheduleRepository.patterns.first() }.isEmpty())
    }

    // ---- 440 [PLAZA-16] 우리 회사 급여 방식 찾기 ----
    private fun Screen.payFind() {
        content { PayFindSheet(onDismiss = {}) }
        settle()
    }

    @Test fun thePayWayIsSaidOnlyAsThere() = shoot("1112_ah_pay_find", SMALL_PHONE) {
        leads()
        payFind()
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        assertTrue(shows("급여 방식 있음 · 맞아요 4명", exact = true))
        assertTrue(shows("급여 방식은 아직 없어요", exact = true))
        // 결정 D79: no value before it is used.
        assertFalse(shows("야간", exact = false))
    }

    @Test fun usingThePayWayJoins() = shoot("1113_ah_pay_find_use", PHONE_393, 2f) {
        leads()
        payFind()
        typeInto("회사 이름", "예시정밀")
        waitFor(400)
        tapText("(주)예시정밀", exact = true)
        assertTrue(shows("‘(주)예시정밀’ 급여 방식을 쓸까요?", exact = true))
        tapText("이 급여 방식 쓰기", exact = true)
        assertTrue(shows("이제 내 금액만 넣으면 돼요", exact = true))
        assertEquals("c1", c.companyStore.member.value?.first)
        assertTrue(c.companyStore.pendingPay != null)
    }

    @Test fun noPayWayAsksTheEasySetup() = shoot("1114_ah_pay_find_none", PHONE_393_NIGHT) {
        leads()
        payFind()
        typeInto("회사 이름", "예시정밀 2")
        waitFor(400)
        tapText("예시정밀 2공장", exact = true)
        assertTrue(shows("아직 ‘예시정밀 2공장’ 급여 방식이 없어요", exact = true))
        tapText("간편 설정으로 만들기", exact = true)
        assertEquals("예시정밀 2공장", c.companyStore.draft.value?.company)
    }

    // ---- [PLAZA-18] AH-I3: 오늘 근무로 자리 찾기 ----
    @Test fun todaysShiftFindsThePlace() = shoot("1115_ah_today_finder", PHONE_393) {
        c.companyApi = com.bamnat.app.company.CompanyClient(baseUrl = "")
        runBlocking { c.scheduleRepository.ensureDefaults() }
        val row = PublicCycles.all.first { it.orderState == com.bamnat.core.plaza.OrderState.EXACT && !it.roster && !it.weekly &&
            com.bamnat.core.plaza.CycleOrders.kinds(it.codes).size > 1 }
        content { com.bamnat.app.ui.company.PublicApplySheet(row, onDismiss = {}, onPay = {}, onShare = {}) }
        settle()
        reveal("모르겠으면 · 오늘 근무가 뭐예요?", exact = true)
    }
}
