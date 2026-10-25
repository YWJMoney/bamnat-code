package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.ui.company.CompanyDetailScreen
import com.bamnat.app.ui.company.CompanyDetailViewModel
import com.bamnat.app.ui.company.CompanyFindScreen
import com.bamnat.app.ui.company.CompanyFindViewModel
import com.bamnat.app.ui.company.CompanyPayScreen
import com.bamnat.app.ui.company.CompanyPayViewModel
import com.bamnat.app.ui.company.ShareInviteNotice
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.ShareAsk
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
 * [PLAZA-01]~[PLAZA-06] 1.25 묶음 X's screenshots, numbers 870~889, each one a run from the first tap to what is saved or
 * sent (결정 D76): 다른 점 말하기 on a company's own values, the 대표 판 compared and an earlier one put back, the
 * company's pay asked about in the first setup, 초성 찾기, the invitation to share a setup made by hand, and 처음 설정
 * 다시 받기 — on a fold, a small phone, at 200%, on its side and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class XScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val DIFF = "우리는 달라요 · 다른 점 말하기"
    }

    private fun Screen.detail(setup: Boolean = false, prepare: () -> Unit = {}): CompanyDetailViewModel {
        companyOn()
        prepare()
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = setup, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        return vm
    }

    /** The proposal this phone sent last: its lines' fields and the file it carried. */
    private fun lastProposal(): Pair<List<String>, JSONObject> {
        val (_, lines, setup) = company.posts.last()
        assertNotNull(setup)
        return lines.map { it.field } to setup!!
    }

    private fun JSONObject.shift(name: String): JSONObject = getJSONArray("shiftTypes").let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("name") == name }
    }

    // ---- 386 [PLAZA-02] 다른 점 말하기 ----
    @Test fun diffEditorOpensOnTheCompanysValues() = shoot("870_x_diff_editor", FOLDED) {
        detail()
        tapText(DIFF, exact = true)
        assertTrue(shows("이 회사 값으로 채워 두었어요", exact = false))
        assertTrue(shows("아직 바꾼 곳이 없어요", exact = true))
        // Nothing to send before something is changed, and it says why.
        assertTrue(shows("우리와 다른 값을 하나 이상 바꾸면 보낼 수 있어요", exact = true))
        assertTrue(company.posts.isEmpty())
    }
    @Test fun aChangedBreakIsSentAsAProposal() = shoot("871_x_diff_sent", FOLDED) {
        detail()
        tapText(DIFF, exact = true)
        tapText("주간 휴게 10분 늘리기", exact = true)
        assertTrue(shows("바꾼 곳 1가지", exact = true))
        tapText("제안 보내기", exact = true)
        val (fields, setup) = lastProposal()
        assertEquals(listOf("shift"), fields)
        assertEquals(70, setup.shift("주간").getInt("break"))
        // The rest of the file is the company's, untouched.
        assertEquals(JSONObject(company.file).shift("야간").toString(), setup.shift("야간").toString())
        assertEquals("다른 점 1가지: 주간", company.posts.last().first)
        // The talk opens on it, so the user sees it went up.
        assertTrue(shows("토론", exact = true))
    }
    @Test fun diffEditorHugeText() = shoot("872_x_diff_editor_huge_text", PHONE, fontScale = 2f) {
        detail()
        tapText(DIFF, exact = true)
        // A tall button at twice the size: bring it up before the tap so its middle is on screen.
        reveal(DIFF, exact = true)
        tapText(DIFF, exact = true)
        // Down to the break stepper and use it: the editor keeps the room to scroll at twice the size.
        repeat(6) { if (!shows("주간 휴게 10분 늘리기", exact = true)) scrollUp(250, across = 0.5f) }
        tapText("주간 휴게 10분 늘리기", exact = true, scrolls = 0)
        // The count keeps its own line at twice the size.
        assertTrue(shows("바꾼 곳 1가지", exact = true))
    }
    @Test fun anOrderChangeIsAnOrderLineDark() = shoot("873_x_diff_order_dark", FOLDED_NIGHT) {
        detail()
        tapText(DIFF, exact = true)
        // The first day of 주간 주간 야간 야간 휴무 becomes the next shift in the file's own order.
        tapText("1일 주간", exact = true)
        assertTrue(shows("바꾼 곳 1가지", exact = true))
        reveal("바꾼 곳 비교해 보기", exact = true)
        tapText("바꾼 곳 비교해 보기", exact = true)
        tapText("제안 보내기", exact = true)
        val (fields, setup) = lastProposal()
        assertEquals(listOf("order"), fields)
        val names = setup.getJSONArray("shiftTypes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { it.getString("name") }
        assertEquals(com.bamnat.app.ui.company.next(names, "주간"), setup.getJSONObject("rotation").getJSONArray("steps").getString(0))
    }
    @Test fun mineFillsTheEditorLandscape() = shoot("874_x_diff_mine_landscape", PHONE_LANDSCAPE) {
        // The company's 주간 starts at 07:00; this phone's own starts at 08:00.
        detail { company.file = SetupEdit.of(company.file)!!.shiftTime("주간", "07:00", "20:00", 60).text }
        tapText(DIFF, exact = true)
        assertTrue(shows("아직 바꾼 곳이 없어요", exact = true))
        // 내 설정으로 채우기 reads this phone's own (amounts left out) as the starting point.
        tapText("내 설정으로 채우기", exact = true)
        assertFalse(shows("아직 바꾼 곳이 없어요", exact = true))
        assertTrue(shows("바꾼 곳 ", exact = false))
    }

    // ---- 389 [PLAZA-05] 대표 판 ----
    @Test fun anotherVersionIsComparedWithTheLead() = shoot("875_x_compare_lead", FOLDED) {
        company.twins = true
        companyOn()
        company.rivalFile = SetupEdit.of(company.file)!!.shiftTime("주간", "08:00", "19:00", 60).text
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        assertTrue(shows("대표 판 v2", exact = true))
        tapText("대표 판과 비교", exact = true)
        assertTrue(shows("다른 점 1가지: 주간", exact = true))
        assertTrue(shows("이 판: 08:00~19:00", exact = false))
    }
    @Test fun anEarlierVersionIsPutBack() = shoot("876_x_history_back", FOLDED) {
        company.bodies = true
        companyOn()
        company.rivalFile = SetupEdit.of(company.file)!!.shiftTime("주간", "08:00", "19:00", 60).text
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        tapText("바뀐 기록", exact = true)
        // The line starts with the day it came in.
        reveal("같은 부서의 다른 판을 더 많은 분이 써서 대표 판이 됐어요", exact = false)
        tapText("지금 판과 비교 · 되돌리기", exact = true)
        assertTrue(shows("v1과 지금 비교", exact = true))
        tapText("이 판으로 되돌리기", exact = true)
        // Through the rotation sheet as any rule set, then this phone uses v1.
        if (shows("이 순서로 쓸게요", exact = true)) tapText("이 순서로 쓸게요", exact = true)
        settle()
        assertEquals("s0", c.companyStore.applied.value?.setup)
        assertEquals(1, c.companyStore.applied.value?.version)
    }
    @Test fun aNewLeadIsSaidOnceAndCanBeKept() = shoot("877_x_new_lead", FOLDED) {
        val c = companyOn()
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s-old", 1, true, Today.instant()))
        val vm = CompanyDetailViewModel(c, "c1")
        content { CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        assertTrue(shows("쓰시던 판 대신 새 대표 판이 생겼어요", exact = true))
        tapText("비교하고 바꾸기", exact = true)
        assertTrue(shows("내 설정과 새 대표 판", exact = true))
        reveal("이 판으로 바꾸기", exact = true)
        back()
        tapText("그대로 둘게요", exact = true)
        assertFalse(shows("쓰시던 판 대신 새 대표 판이 생겼어요", exact = true))
        assertEquals("s-old", c.companyStore.applied.value?.setup)
    }

    // ---- 387 [PLAZA-03] 회사 급여 설정을 쓸까요? ----
    private fun Screen.companyPay(): CompanyPayViewModel {
        val c = companyOn()
        c.companyStore.setApplied(AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        c.companyStore.pendingPay = company.file
        val vm = CompanyPayViewModel(c)
        content { CompanyPayScreen(vm, onBack = {}, onByHand = {}) }
        settle()
        return vm
    }
    @Test fun theCompanysPayIsAskedFirst() = shoot("878_x_pay_ask", FOLDED) {
        companyPay()
        assertTrue(shows("이 회사 급여 설정을 쓸까요?", exact = true))
        assertFalse("amounts wait for the answer", shows("거의 다 됐어요! 내 금액만 넣어요", exact = true))
        tapText("네, 이 설정으로 할게요", exact = true)
        assertTrue(shows("거의 다 됐어요! 내 금액만 넣어요", exact = true))
        assertTrue(company.posts.isEmpty())
    }
    @Test fun ourPayDiffersAndIsUsedAndProposed() = shoot("879_x_pay_differs", FOLDED) {
        val vm = companyPay()
        tapText("우리 급여는 조금 달라요", exact = true)
        reveal("연장 가산 10% 늘리기", exact = true)
        tapText("연장 가산 10% 늘리기", exact = true)
        tapText("제안 보내기", exact = true)
        settle()
        // This phone's pay is made from what the user said, and the same went to the company's talk.
        val ot = { t: String -> JSONObject(t).getJSONObject("pay").getJSONObject("rules").opt("ot")?.takeIf { it != JSONObject.NULL } }
        assertTrue(ot(c.companyStore.pendingPay!!) != ot(company.file))
        assertNotNull(ot(c.companyStore.pendingPay!!))
        assertTrue(vm.edited)
        assertEquals(listOf("overtime"), lastProposal().first)
        assertTrue(shows("말씀하신 다른 점을 넣은 방식으로 계산해요", exact = false))
    }
    @Test fun payAskHugeText() = shoot("880_x_pay_ask_huge_text", PHONE, fontScale = 2f) {
        companyPay()
        reveal("처음부터 직접 만들게요", exact = true)
    }

    // ---- 387 [PLAZA-03] 초성 찾기 ----
    @Test fun choseongFindsCompanies() = shoot("881_x_choseong", FOLDED) {
        val vm = CompanyFindViewModel(companyOn()).apply { query.value = "ㅇㅅㅈㅁ" }
        content { CompanyFindScreen(vm, setup = true, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        waitFor(400)
        assertTrue(shows("초성으로 찾았어요. 쓰는 설정이 있는 회사만 보여요.", exact = true))
        assertTrue(shows("(주)예시정밀", exact = true))
        assertTrue(shows("쓰는 설정 1개", exact = false))
        // [NAV-02] Back clears the search first and the screen stays (its 뒤로 is not called); the search again for the picture.
        back()
        assertEquals("", vm.query.value)
        vm.query.value = "ㅇㅅㅈㅁ"
        waitFor(400)
        assertTrue(shows("(주)예시정밀", exact = true))
    }

    // ---- 388 [PLAZA-04] 광장에 올리기 권유 ----
    private fun Screen.invite() {
        val c = companyOn()
        c.companyStore.setHandMade(true)
        content { ShareInviteNotice() }
        settle()
    }
    @Test fun aHandMadeSetupIsInvitedAndWaitsLonger() = shoot("882_x_invite", FOLDED) {
        invite()
        assertTrue(shows(ShareAsk.Moment.SETUP.title, exact = true))
        val before = Today.millis()
        tapText("다음에", exact = true)
        val due = c.companyStore.inviteDue.value
        assertTrue(due >= ShareAsk.nextDue(1, before))
        assertFalse(shows(ShareAsk.Moment.SETUP.title, exact = true))
    }
    @Test fun whatWouldGoIsShownFirst() = shoot("883_x_invite_preview", FOLDED) {
        invite()
        tapText("보낼 내용 보기", exact = true)
        assertTrue(shows("· 근무 시각:", exact = false))
        assertTrue(shows("금액·이름·연락처는 가지 않아요.", exact = true))
    }
    @Test fun stopSeeingItForGood() = shoot("884_x_invite_never", FOLDED) {
        invite()
        tapText("그만 볼게요", exact = true)
        assertTrue(c.companyStore.inviteNever.value)
        assertFalse(shows(ShareAsk.Moment.SETUP.title, exact = true))
    }
    @Test fun inviteSmallPhone() = shoot("885_x_invite_small", SMALL_PHONE) {
        invite()
        reveal("나눠 볼게요", exact = true)
    }

    // ---- 390 [PLAZA-06] 처음 설정 다시 받기 ----
    @Test fun setupAgainLeavesEverythingWhenGivenUp() = shoot("886_x_setup_again_cancel", FOLDED) {
        val before = runBlocking { c.scheduleRepository.patterns.first() }
        settingsPage("내 근무")
        tapText("처음 설정 다시 받기", exact = true)
        assertTrue(shows("지금 달력은 그대로예요", exact = false))
        assertNotNull(c.againSetupDrafts.draft.value)
        tapText("다시 받기 그만두기", exact = true)
        settle()
        assertNull(c.againSetupDrafts.draft.value)
        assertEquals(before, runBlocking { c.scheduleRepository.patterns.first() })
        assertTrue(shows("일하는 방식", exact = true))
    }
    @Test fun setupAgainSavesLikeTheFirstSetup() = shoot("887_x_setup_again_saved", FOLDED) {
        settingsPage("내 근무")
        tapText("처음 설정 다시 받기", exact = true)
        // The choice reads "현장 · 프리랜서" with its line under it.
        tapText("일감과 받을 돈을 달력에 적어요", exact = false)
        settle()
        assertEquals(WorkStyle.FIELD, c.settingsRepository.current.workStyle)
        assertNull(c.againSetupDrafts.draft.value)
        assertFalse(shows("처음 설정 다시 받기", exact = true) && shows("지금 달력은 그대로예요", exact = false))
    }
    @Test fun helpOffersTheSetupAgain() {
        inquiriesOn()
        shoot("888_x_help_setup_again", FOLDED) {
            askFromScreen(InquiryStart(help = true))
            reveal("처음 설정을 놓치셨나요? 처음 설정 다시 받기", exact = true)
            tapText("처음 설정을 놓치셨나요? 처음 설정 다시 받기", exact = true)
            settle()
            assertTrue(shows("지금 달력은 그대로예요", exact = false))
        }
    }

    // ---- 388 [PLAZA-04] pay set up by hand ----
    @Test fun payMadeByHandIsOfferedToThePlaza() = shoot("889_x_pay_by_hand_invite", FOLDED) {
        c.companyApi = company
        payScreen(com.bamnat.app.tier.Tier.PRO, withProfile = true) { vm ->
            // The pay setup just finished by hand, with no coworkers' setup in use.
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.profile.value?.let { vm.saveSetup(it) } }
            PayScreen(vm, onBack = {}, onShareWay = {})
        }.invoke(this)
        settle()
        reveal(ShareAsk.Moment.PAY.title, exact = true)
        tapText("다음에", exact = true)
        assertFalse(shows(ShareAsk.Moment.PAY.title, exact = true))
    }
}
