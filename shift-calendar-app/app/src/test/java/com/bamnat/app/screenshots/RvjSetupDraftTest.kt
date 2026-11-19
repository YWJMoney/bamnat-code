package com.bamnat.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.components.DraftStore
import com.bamnat.app.ui.pay.PayQuickSetup
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.app.ui.pay.PayWizard
import com.bamnat.app.ui.pay.QuickDraft
import com.bamnat.app.ui.pay.WizardDraft
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.bamnat.app.ui.pay.CompanyRulesEditor
import com.bamnat.app.ui.pay.RulesDraft
import com.bamnat.app.ui.work.StaffDraft
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 갈래 J 아홉 번째 [RVJ-75] (검토 PAYU-04, 조건 10 '적던 것은 사라지지 않아요'): 급여 마법사(자세히 고치기)와 빠른 설정의 넣던 답을
 * 이 휴대폰에 7일 임시 저장해, 다른 앱에 다녀오거나 화면이 다시 만들어지거나 앱이 닫혀도 '넣던 급여 설정을 이어서 해요 · 처음부터'로
 * 돌아와요. 빠른 설정도 바꾼 것이 있으면 취소·← 전에 한 번 물어요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjSetupDraftTest : ScreenshotBase() {
    private val resume = "넣던 급여 설정을 이어서 해요"

    @Before fun clean() {
        DraftStore.clear(app, WizardDraft.KEY)
        DraftStore.clear(app, QuickDraft.KEY)
        DraftStore.clear(app, StaffDraft.KEY)
        DraftStore.clear(app, RulesDraft.KEY)
    }

    @After fun after() {
        DraftStore.clear(app, WizardDraft.KEY)
        DraftStore.clear(app, QuickDraft.KEY)
        DraftStore.clear(app, StaffDraft.KEY)
        DraftStore.clear(app, RulesDraft.KEY)
    }

    @Test fun theEasyAnswersComeBackWhole() {
        val vm = PayViewModel(app.container)
        val initial = vm.sampleProfile()
        val a = QuickDraft.Answers(
            form = WorkForm.OFFICE, fiveOrMore = true, formTapped = true, amount = 3_100_000, hours = 209,
            holiday = HolidayWork.SUBSTITUTE, tax = TaxMode.INSURANCE_4, fixedOt = 20, fixedOtPay = 300_000, lump = false,
            fromSlip = null, step = "amount", foundCycle = null,
        )
        val raw = QuickDraft.encode(initial, a)
        val back = QuickDraft.decode(raw, initial)!!
        assertEquals(WorkForm.OFFICE, back.form)
        assertEquals(true, back.fiveOrMore)
        assertTrue(back.formTapped)
        assertEquals(3_100_000L, back.amount)
        assertEquals(209, back.hours)
        assertEquals(HolidayWork.SUBSTITUTE, back.holiday)
        assertEquals(TaxMode.INSURANCE_4, back.tax)
        assertEquals(20, back.fixedOt)
        assertEquals(300_000L, back.fixedOtPay)
        assertEquals("amount", back.step)
        // Kept for another setup (the pay changed since), or not a draft at all: nothing comes back.
        assertNull(QuickDraft.decode(raw, null))
        assertNull(QuickDraft.decode(raw, initial.copy(fiveOrMoreEmployees = !initial.fiveOrMoreEmployees)))
        assertNull(QuickDraft.decode("다른 글", initial))
        assertNull(QuickDraft.decode("", initial))
    }

    // ── 급여 마법사 ──

    private var vm: PayViewModel? = null
    private var cancelled = 0

    @Composable private fun Wizard(m: PayViewModel, at: String?) {
        val profile = m.profile.collectAsState().value
        val snap = m.snapshot.collectAsState().value
        if (profile != null && snap != null) PayWizard(profile, onCancel = { cancelled++ }, onDone = {}, snapshot = snap,
            preview = { m.preview(it, snap, Today.month()) }, startAt = at)
    }

    /** 자세히 고치기 opened on the amount, 기본급 3,100,000원 put in. */
    private fun typedWizard(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Wizard(m, "amount") }.invoke(this)
        typeInto("기본급", "3100000")
        repeat(2) { settle() }
    }

    /** The wizard goes the way a screen made again or Android closing the app takes it, and opens again (no step given). */
    private fun wizardGoneAndBack(): Screen.() -> Unit = {
        content {}
        settle()
        val m = vm!!
        content { Wizard(m, null) }
        repeat(2) { settle() }
    }

    @Test fun theWizardKeepsWhatWasPut() = shoot("rvj_230_wizard_kept_folded", FOLDED) {
        typedWizard().invoke(this)
        wizardGoneAndBack().invoke(this)
        assertTrue(DraftStore.read(app, WizardDraft.KEY).isNotEmpty())
        assertTrue(appears(resume))
        assertTrue(shows("3100000") || shows("3,100,000"))
    }

    @Test fun theWizardKeptDarkBig() = shoot("rvj_231_wizard_kept_folded_dark_200", DARK, 2f) {
        typedWizard().invoke(this)
        wizardGoneAndBack().invoke(this)
        assertTrue(appears(resume))
    }

    @Test fun theWizardStartsOver() = shoot("rvj_232_wizard_fresh_folded", FOLDED) {
        typedWizard().invoke(this)
        wizardGoneAndBack().invoke(this)
        tapText("처음부터", exact = true)
        repeat(2) { settle() }
        assertFalse(shows(resume))
        assertEquals("", DraftStore.read(app, WizardDraft.KEY))
    }

    @Test fun nothingChangedKeepsNothing() = shoot("rvj_233_wizard_quiet_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Wizard(m, "amount") }.invoke(this)
        wizardGoneAndBack().invoke(this)
        assertEquals("", DraftStore.read(app, WizardDraft.KEY))
        assertFalse(shows(resume))
    }

    // ── 빠른 설정 ──

    @Composable private fun Quick(m: PayViewModel) {
        val profile = m.profile.collectAsState().value
        val snap = m.snapshot.collectAsState().value
        if (profile != null && snap != null) PayQuickSetup(
            initial = profile, snapshot = snap,
            preview = { m.preview(it, snap, Today.month()) },
            slipRules = { base, read, rules -> m.slipRules(base, snap, read, rules) },
            onDone = { _, _ -> }, onDetailed = {}, onCancel = { cancelled++ },
        )
    }

    /** 처음부터 설정 over a pay that is there: 다음, then 기본급 3,100,000원. */
    private fun typedQuick(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Quick(m) }.invoke(this)
        tapText("다음", exact = true, scrolls = 0)
        repeat(2) { settle() }
        typeInto("월 기본급", "3100000")
        repeat(2) { settle() }
    }

    private fun quickGoneAndBack(): Screen.() -> Unit = {
        content {}
        settle()
        val m = vm!!
        content { Quick(m) }
        repeat(2) { settle() }
    }

    @Test fun theEasySetupKeepsWhatWasPut() = shoot("rvj_234_quick_kept_folded", FOLDED) {
        typedQuick().invoke(this)
        quickGoneAndBack().invoke(this)
        assertTrue(DraftStore.read(app, QuickDraft.KEY).contains("a\t3100000"))
        assertTrue(appears(resume))
        assertTrue(shows("3100000") || shows("3,100,000"))
    }

    @Test fun theEasySetupKeptDarkBig() = shoot("rvj_235_quick_kept_folded_dark_200", DARK, 2f) {
        typedQuick().invoke(this)
        quickGoneAndBack().invoke(this)
        assertTrue(appears(resume))
    }

    @Test fun theEasySetupStartsOver() {
        shoot("rvj_236_quick_fresh_folded", FOLDED) {
            typedQuick().invoke(this)
            quickGoneAndBack().invoke(this)
            tapText("처음부터", exact = true)
            repeat(2) { settle() }
            assertFalse(shows(resume))
            assertFalse(shows("3,100,000"))
            assertEquals("", DraftStore.read(app, QuickDraft.KEY))
        }
    }

    @Test fun cancellingTheEasySetupAsks() = shoot("rvj_237_quick_cancel_asks_folded", FOLDED) {
        typedQuick().invoke(this)
        tapText("이전", exact = true, scrolls = 0)
        repeat(2) { settle() }
        tapText("취소", exact = true, scrolls = 0)
        assertTrue(appears("적은 내용을 버릴까요?"))
        assertEquals(0, cancelled)
    }

    @Test fun cancellingTheEasySetupThrowsItAway() {
        shoot("rvj_238_quick_cancel_dropped_folded", FOLDED) {
            typedQuick().invoke(this)
            tapText("이전", exact = true, scrolls = 0)
            repeat(2) { settle() }
            tapText("취소", exact = true, scrolls = 0)
            assertTrue(appears("적은 내용을 버릴까요?"))
            tapText("버리기", exact = true, scrolls = 0)
            repeat(2) { settle() }
            assertEquals(1, cancelled)
            assertEquals("", DraftStore.read(app, QuickDraft.KEY))
        }
    }

    @Test fun cancellingUnchangedClosesAtOnce() {
        shoot("rvj_239_quick_cancel_quiet_folded", FOLDED) {
            payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Quick(m) }.invoke(this)
            tapText("취소", exact = true, scrolls = 0)
            repeat(2) { settle() }
            assertFalse(shows("적은 내용을 버릴까요?"))
            assertEquals(1, cancelled)
        }
    }

    // ── 직원 정보 창 (사장님) ──

    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    @Test fun theStaffDraftKeepsNoKeys() {
        val start = StaffMember("s1", "김알바", base = 10_320L, job = "홀", clockId = "c1", clockToken = "열쇠값")
        val raw = StaffDraft.encode("p1", start, start.copy(job = "주방"))
        assertFalse(raw.contains("열쇠값"))
        assertFalse(raw.contains("c1"))
        val back = StaffDraft.decode(raw)!!
        assertEquals("p1", back.place)
        assertEquals("주방", back.now.job)
        assertEquals("s1", back.start.id)
        assertNull(back.now.clockToken)
        assertNull(StaffDraft.decode(""))
        assertNull(StaffDraft.decode("다른 글"))
    }

    private fun staffLedger(): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "식당", fivePlus = true, payday = 31, payNextMonth = false))
            store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, hiredOn = LocalDate.of(2026, 3, 2), job = "홀",
                weekly = DayOfWeek.entries.take(5).associateWith { nine }, insurance = Insurance.ALL), "p1", 0)
        }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
    }

    /** The 대장 goes the way Android closing the app takes it (the window with it), and opens again. */
    private fun ledgerAgain(): Screen.() -> Unit = {
        content {}
        settle()
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(3) { settle() }
    }

    private val staffResume = "넣던 직원 정보를 이어서 적어요"

    private fun typedNewPerson(): Screen.() -> Unit = {
        staffLedger().invoke(this)
        tapText("직원 더하기", exact = true)
        settle()
        typeInto("이름", "정새봄")
        repeat(2) { settle() }
    }

    @Test fun aNewPersonComesBack() = shoot("rvj_240_staff_kept_folded", FOLDED) {
        typedNewPerson().invoke(this)
        ledgerAgain().invoke(this)
        assertTrue(appears(staffResume))
        assertTrue(shows("정새봄"))
        val kept = StaffDraft.decode(DraftStore.read(app, StaffDraft.KEY))
        assertEquals("정새봄", kept?.now?.name)
    }

    @Test fun aNewPersonComesBackDarkBig() = shoot("rvj_241_staff_kept_folded_dark_200", DARK, 2f) {
        typedNewPerson().invoke(this)
        ledgerAgain().invoke(this)
        assertTrue(appears(staffResume))
    }

    @Test fun aChangedPersonComesBack() = shoot("rvj_242_staff_changed_kept_folded", FOLDED) {
        staffLedger().invoke(this)
        tapText("직원 정보", exact = true)
        settle()
        typeInto("하는 일 (예: 홀, 주방)", "주방")
        repeat(2) { settle() }
        ledgerAgain().invoke(this)
        assertTrue(appears(staffResume))
        assertTrue(shows("주방", exact = true))
        tapText("처음부터", exact = true)
        repeat(2) { settle() }
        assertFalse(shows(staffResume))
        assertTrue(shows("홀", exact = true))
        assertEquals("", DraftStore.read(app, StaffDraft.KEY))
    }

    @Test fun aNewPersonUntypedKeepsNothing() {
        shoot("rvj_243_staff_quiet_folded", FOLDED) {
            staffLedger().invoke(this)
            tapText("직원 더하기", exact = true)
            repeat(2) { settle() }
            ledgerAgain().invoke(this)
            // The 4대보험 the law picks by itself for a new person is not the boss's typing: nothing kept, nothing opens.
            assertEquals("", DraftStore.read(app, StaffDraft.KEY))
            assertFalse(shows(staffResume))
        }
    }

    // ── 회사 규칙 편집기 ──

    @Composable private fun Rules(m: PayViewModel) {
        val profile = m.profile.collectAsState().value
        val snap = m.snapshot.collectAsState().value
        if (profile != null && snap != null) CompanyRulesEditor(m, profile, snap, Today.month(), onClose = { cancelled++ }, onOpenKinds = {})
    }

    private fun rulesGoneAndBack(): Screen.() -> Unit = {
        content {}
        settle()
        val m = vm!!
        content { Rules(m) }
        repeat(2) { settle() }
    }

    @Test fun theRulesComeBack() = shoot("rvj_244_rules_kept_folded", FOLDED) {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Rules(m) }.invoke(this)
        tapText("5인 이상 사업장", exact = true)
        repeat(2) { settle() }
        rulesGoneAndBack().invoke(this)
        assertTrue(appears("고치던 회사 규칙을 이어서 고쳐요"))
        assertTrue(shows("5인 미만 사업장은"))
        tapText("처음부터", exact = true)
        repeat(2) { settle() }
        assertFalse(shows("고치던 회사 규칙을 이어서 고쳐요"))
        assertEquals("", DraftStore.read(app, RulesDraft.KEY))
    }

    @Test fun theRulesComeBackDarkBig() = shoot("rvj_245_rules_kept_folded_dark_200", DARK, 2f) {
        payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Rules(m) }.invoke(this)
        tapText("5인 이상 사업장", exact = true)
        repeat(2) { settle() }
        rulesGoneAndBack().invoke(this)
        assertTrue(appears("고치던 회사 규칙을 이어서 고쳐요"))
    }

    @Test fun savedRulesKeepNothing() {
        shoot("rvj_246_rules_saved_folded", FOLDED) {
            payScreen(Tier.PRO, withProfile = true) { m -> vm = m; Rules(m) }.invoke(this)
            tapText("5인 이상 사업장", exact = true)
            repeat(2) { settle() }
            reveal("저장", exact = true)
            tapText("저장", exact = true, scrolls = 0)
            repeat(2) { settle() }
            assertEquals("", DraftStore.read(app, RulesDraft.KEY))
            assertEquals(1, cancelled)
        }
    }

    companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
