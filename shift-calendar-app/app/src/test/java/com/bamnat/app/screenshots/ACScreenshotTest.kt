package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PublicUse
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * [SPAT-01]~[SPAT-06] 1.26 묶음 AC: the researched company cycles (공개 자료 기반) searched by name, put on the calendar with
 * the order the user chose and today's place in it, then the pay with the 업종's usual allowances, then the plaza as
 * '공개 자료 확인·고침' (or '처음 제공' for a company nobody has), from the tap to what is stored. 1000~1019.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ACScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val gsEps get() = PublicCycles.all.first { it.company == "GS EPS" }
    private val gsCaltex get() = PublicCycles.all.first { it.company == "GS칼텍스" }

    private fun pattern(): WorkPattern.Rotation = runBlocking {
        PatternTimeline.current(c.scheduleRepository.patterns.first(), Today.date()) as WorkPattern.Rotation
    }

    private fun nameOn(p: WorkPattern, day: java.time.LocalDate): String? = runBlocking {
        val types = c.scheduleRepository.shiftTypes.first().associateBy { it.id }
        p.shiftTypeIdOn(day)?.let { types[it]?.name }
    }

    /** The first setup up to the company step, the name typed and the search open. */
    private fun Screen.setupSearch(name: String) {
        // Without the company server unless a test turns it on: the public rows are searched all the same.
        if (c.companyApi !is FakeCompanyApi) c.companyApi = com.bamnat.app.company.CompanyClient(baseUrl = "")
        tapText("순서가 돌아가요")
        typeInto("회사 이름 (선택)", name)
        tapText("‘$name’", prefix = true)
        waitFor(400)
    }

    /** Today is the second night of 주주휴휴야야휴휴, then the rest of the first setup as it stands. */
    private fun Screen.finishSetup() {
        tapText("네, 시간이 맞아요")
        tapText("다음", exact = true)
        tapText("야간", exact = true); tapText("둘째 날")
        tapText("회사 근무표와 같아요"); tapText("다음", exact = true)
        tapText("저장하고 달력 보기")
    }

    /** The row on the calendar as its sheet puts it, today being day [today] of [codes]. */
    private fun applied(cycle: com.bamnat.core.plaza.PublicCycle, codes: String, today: Int = 0) =
        runBlocking { com.bamnat.app.company.PublicCycleFiles.apply(c, cycle, codes, today) }

    private fun usePublic(cycleId: String, company: String, form: String, codes: String, picked: Boolean) =
        c.companyStore.setPublicUse(PublicUse(cycleId, company, form, codes, picked, Today.millis()))

    /** The calendar's cards are stacked; the public cycle's is opened from its line. */
    private fun Screen.openWayCard(title: String) {
        if (!shows(title, exact = true)) tapText("근무주기 다음 단계")
        reveal(title, exact = true)
    }

    private fun withPay() = runBlocking { c.payStore.save(PayViewModel(c).sampleProfile().copy(rules = companyRules), keepUndo = false) }

    // ---- 413·414 [SPAT-01][SPAT-02] 첫 설정의 회사 찾기: the public rows, without the company server ----
    @Test fun setupSearchShowsPublicRows() = shoot("1000_ac_setup_search_public", PHONE_393, fresh(null) {
        setupSearch("GS EPS")
        assertTrue(appears(PublicCycles.BADGE, exact = false))
        assertTrue(appears("4조 2교대 · 주주휴휴야야휴휴", exact = false))
        // D79: never like a coworkers' setup.
        assertTrue(gone("채택", exact = false) && gone("대표 판", exact = false))
    })

    // ---- 415 [SPAT-03] the row's order, then the rest of the first setup, then the calendar ----
    @Test fun setupPicksAPublicRowAndSavesItsCycle() = shoot("1001_ac_setup_public_saved", PHONE_393, fresh(null) {
        setupSearch("GS EPS")
        tapText("4조 2교대 · 주주휴휴야야휴휴")
        assertTrue(appears("이 순서가 맞나요?", exact = true))
        tapText("이 순서로 설정 이어가기")
        // On to the times: the row's shifts are in the draft.
        assertTrue(appears("근무 시간이 맞나요?", exact = true))
        finishSetup()
        val p = pattern()
        assertEquals(8, p.steps.size)
        val today = Today.date()
        assertEquals("야간", nameOn(p, today))
        assertEquals("휴무", nameOn(p, today.plusDays(1)))
        assertEquals("주간", nameOn(p, today.plusDays(3)))
        val u = c.companyStore.publicUse.value!!
        assertEquals(gsEps.id, u.cycleId)
        assertEquals("DDOONNOO", u.codes)
        assertFalse(u.picked)
        // One invitation: the row's own, not the hand-made one.
        assertNull(c.companyStore.draft.value)
        assertFalse(c.companyStore.handMade.value)
    })

    @Test fun publicSheetInSetup() = shoot("1002_ac_setup_public_sheet", PHONE_393, fresh(null) {
        setupSearch("GS EPS")
        tapText("4조 2교대 · 주주휴휴야야휴휴")
    })

    // ---- 414·415 사용자 2026-10-25 14:05: nobody has the company → 처음으로 회사 정보를 제공해 주세요! ----
    @Test fun setupFirstProviderGivesTheCycle() = shoot("1003_ac_setup_first_provider", PHONE_393, fresh(null) {
        setupSearch("한별정밀")
        assertTrue(appears("처음으로 회사 정보를 제공해 주세요!", exact = true))
        // Without the company server: no plaza or reward words, only the cycle put in.
        assertTrue(gone("보상", exact = false))
        assertTrue(appears("흔한 순서부터 보여 드려요", exact = false))
        tapText("4조 2교대", exact = true)
        tapText("‘한별정밀’ 근무주기 넣기")
        assertTrue(appears("공개 자료에는 순서가 없어요", exact = false))
        tapText("2222 · 주2·휴2·야2·휴2")
        tapText("이 순서로 설정 이어가기")
        finishSetup()
        val u = c.companyStore.publicUse.value!!
        assertEquals("", u.cycleId)
        assertEquals("한별정밀", u.company)
        assertEquals("4조2교대", u.formKey)
        assertEquals("DDOONNOO", u.codes)
        assertTrue(u.picked)
        assertEquals(8, pattern().steps.size)
    })

    @Test fun firstProviderCard() = shoot("1004_ac_first_provider_card", PHONE_393, fresh(null) {
        companyOn()
        setupSearch("한별정밀")
        assertTrue(appears("근무주기만 올리면 보상은 없어요", exact = false))
    })

    // ---- 415 [SPAT-03] 근무 주기 › 회사 이름으로 근무주기 찾기: a row with no public order, today picked, the calendar written ----
    @Test fun patternScreenFindsAndAppliesAFormOnlyRow() = shoot("1005_ac_pattern_apply_form_only", PHONE_393) {
        c.companyApi = com.bamnat.app.company.CompanyClient(baseUrl = "")
        settingsPage("내 근무")
        tapText("근무 주기", exact = true)
        tapText("회사 이름으로 근무주기 찾기")
        typeInto("회사 이름이나 초성 (예: 예시정밀, ㅇㅅㅈㅁ)", "GS칼텍스")
        waitFor(400)
        tapText("공개 순서 없음 · 순서는 직접 골라요")
        assertTrue(appears("공개 자료에는 순서가 없어요", exact = false))
        tapText("주야비휴")
        tapText("2번째 날 야간", exact = true)
        tapText("달력에 넣기")
        assertTrue(appears("달력에 넣었어요", exact = false))
        assertTrue(appears("급여도 넣기", exact = true) || shows("광장에 올리기", exact = true))
        val p = pattern()
        assertEquals(4, p.steps.size)
        assertEquals("야간", nameOn(p, Today.date()))
        assertEquals("비번", nameOn(p, Today.date().plusDays(1)))
        val u = c.companyStore.publicUse.value!!
        assertEquals(gsCaltex.id, u.cycleId)
        assertEquals("DNBO", u.codes)
        assertTrue(u.picked)
    }

    // ---- 416 [SPAT-04] the calendar's card → 간편 급여 with the form picked and the 업종's allowances 확인 필요 ----
    @Test fun payNudgeOpensThePaySetupFromTheCycle() = shoot("1006_ac_pay_from_cycle", PHONE_393) {
        runBlocking { c.payStore.save(null, keepUndo = false) }
        usePublic(gsEps.id, gsEps.company, gsEps.formKey, gsEps.codes, false)
        settle()
        openWayCard("급여도 넣어 볼까요?")
        tapText("급여 넣기", exact = true)
        assertTrue(appears("‘GS EPS’ 근무주기에 맞춰", exact = false))
        tapText("다음", exact = true)
        typeInto("월 기본급", "3000000")
        tapText("다음", exact = true)
        if (!shows("이대로 계산", exact = true)) tapText("다음", exact = true)
        tapText("이대로 계산", exact = true)
        // The save runs off the main thread: a slow CI machine may not have it yet after the tap.
        repeat(100) { if (c.payStore.profile.value == null) { Thread.sleep(20); settle() } }
        val p = c.payStore.profile.value!!
        assertEquals(com.bamnat.core.pay.Occupation.MANUFACTURING, p.occupation)
        val usual = PublicCycles.all.first { it.id == gsEps.id }.let { com.bamnat.core.plaza.PublicPay.usualItems(it) }
        assertTrue(usual.isNotEmpty())
        usual.forEach { name ->
            val item = p.items.first { it.name == name }
            assertTrue(name, Unknowns.item(item.id) in p.estimatedFields)
        }
    }

    // ---- 417 [SPAT-05] pay set → the reward first → the report as '공개 자료 확인·고침' → the company's plaza page ----
    @Test fun shareInviteSendsTheCheckedCycleAndOpensThePlaza() = shoot("1007_ac_share_sent_plaza", PHONE_393) {
        companyOn()
        withPay()
        applied(gsCaltex, "DDOONNOO")
        settle()
        openWayCard("‘GS칼텍스’ 근무주기를 광장에 올려 주실래요?")
        assertTrue(appears("급여 정보까지 넣어 광장에 올리면 보상을 드려요", exact = false))
        tapText("올리기", exact = true)
        assertTrue(appears("공개 자료 확인·고침 · GS칼텍스", exact = true))
        tapText("보내기", exact = true)
        val (note, setup) = company.reports.last()
        assertTrue(note, note.startsWith("공개 자료 확인·고침: 4조 2교대 · 순서 주주휴휴야야휴휴 (공개 순서 없음, 직접 골랐어요)"))
        // No amount goes: the base is not in it and every item's value is left out.
        val base = c.payStore.profile.value!!.base.on(Today.month()).value
        assertFalse(setup.toString().contains(base.toString()))
        val items = setup.optJSONObject("pay")?.optJSONArray("items")
        for (i in 0 until (items?.length() ?: 0)) assertTrue(items!!.getJSONObject(i).optLong("amount", 0) == 0L)
        assertEquals(8, setup.getJSONObject("rotation").getJSONArray("steps").length())
        assertEquals("r9", c.companyStore.publicUse.value!!.sentId)
        tapText("‘GS칼텍스’ 광장 보기")
        assertTrue(appears("(주)예시정밀", exact = false))
    }

    // ---- 417 사용자 2026-10-25 14:05: no pay → only the cycle goes, no reward, and it still goes up ----
    @Test fun firstProviderWithoutPaySendsTheCycleOnly() = shoot("1008_ac_cycle_only_report", PHONE_393) {
        companyOn()
        runBlocking { c.payStore.save(null, keepUndo = false) }
        applied(PublicCycles.provided("한별정밀", "4조2교대"), "DDOONNOO")
        c.companyStore.updatePublicUse { it.copy(payClosed = true) }
        settle()
        openWayCard("‘한별정밀’ 근무주기를 광장에 올려 주실래요?")
        assertTrue(appears("급여 정보는 아직 없어서 근무주기만 가요", exact = true))
        tapText("올리기", exact = true)
        assertTrue(appears("처음 제공 · 한별정밀", exact = true))
        assertTrue(appears("급여 설정이 아직 없어서 근무주기만 올라가요. 보상은 없어요", exact = true))
        tapText("보내기", exact = true)
        val (note, setup) = company.reports.last()
        assertTrue(note, note.startsWith("처음 제공: 4조 2교대 · 순서 주주휴휴야야휴휴"))
        assertFalse(setup.has("pay"))
        assertEquals(8, setup.getJSONObject("rotation").getJSONArray("steps").length())
        assertNotNull(c.companyStore.publicUse.value!!.sentId)
    }

    // ---- 414 [SPAT-02] 회사 찾기 with the server: coworkers' setups first, the public rows under them ----
    @Test fun companyFindMergesServerAndPublic() = shoot("1009_ac_find_merged", PHONE_393) {
        val vm = com.bamnat.app.ui.company.CompanyFindViewModel(companyOn()).apply { query.value = "예시" }
        content { com.bamnat.app.ui.company.CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        waitFor(400)
        assertTrue(appears("(주)예시정밀", exact = false))
        vm.query.value = "포스코"
        waitFor(400)
        assertTrue(appears(PublicCycles.BADGE, exact = false))
        assertTrue(gone("처음으로 회사 정보를 제공해 주세요!", exact = true))
    }

    // ---- sizes: 320×568, 200% text, dark ----
    @Test fun searchSmall() = shoot("1010_ac_setup_search_320", SMALL_PHONE, fresh(null) { setupSearch("GS EPS") })
    @Test fun sheetLargeText() = shoot("1011_ac_sheet_200", PHONE_393, 2f) {
        content {
            com.bamnat.app.ui.company.PublicApplySheet(gsCaltex, onDismiss = {}, onPay = {}, onShare = {})
        }
        settle()
        tapText("주야비휴")
    }
    @Test fun sheetDark() = shoot("1012_ac_sheet_dark", PHONE_393_NIGHT) {
        content { com.bamnat.app.ui.company.PublicApplySheet(gsEps, onDismiss = {}, onPay = {}, onShare = {}) }
        settle()
        tapText("6번째 날 야간", exact = true)
    }
    @Test fun firstProviderDark() = shoot("1013_ac_first_provider_dark", PHONE_393_NIGHT, fresh(null) { companyOn(); setupSearch("한별정밀") })
    @Test fun firstProviderLargeText() = shoot("1014_ac_first_provider_200", PHONE_393, 2f, fresh(null) { companyOn(); setupSearch("한별정밀") })
    @Test fun reportHeadSmall() = shoot("1015_ac_report_head_320", SMALL_PHONE) {
        companyOn()
        withPay()
        usePublic(gsCaltex.id, gsCaltex.company, gsCaltex.formKey, "DDOONNOO", true)
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        settle()
    }
    @Test fun shareCardDark() = shoot("1016_ac_share_card_dark", PHONE_393_NIGHT) {
        companyOn()
        withPay()
        usePublic(gsEps.id, gsEps.company, gsEps.formKey, gsEps.codes, false)
        settle()
        openWayCard("‘GS EPS’ 근무주기를 광장에 올려 주실래요?")
    }
    @Test fun payNoteSmall() = shoot("1017_ac_pay_note_320", SMALL_PHONE) {
        runBlocking { c.payStore.save(null, keepUndo = false) }
        usePublic(gsEps.id, gsEps.company, gsEps.formKey, gsEps.codes, false)
        settle()
        openWayCard("급여도 넣어 볼까요?")
        tapText("급여 넣기", exact = true)
    }

    // ---- 414 [SPAT-02] 회사 규칙 광장's search: the public rows under the plaza's own ----
    @Test fun boardSearchShowsPublicRows() = shoot("1018_ac_board_public", PHONE_393) {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn()).apply { query.value = "포스코" }
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}, onRates = {}) }
        waitFor(400)
        reveal(PublicCycles.BADGE)
        assertTrue(appears("포스코", exact = true))
    }

    // ---- 418 [SPAT-06] 도움말 finds the answer and opens 근무 주기 ----
    @Test fun helpAnswersAndOpensThePattern() = shoot("1019_ac_help_answer", PHONE_393) {
        inquiriesOn()
        tab(4, wide = false)
        tapText("도움말과 문의", exact = true)
        typeInto("기능과 도움말 찾기", "회사 근무주기")
        tapText("우리 회사 근무주기를 이름으로 찾을 수 있나요?", exact = true)
        reveal("근무 주기 열기", exact = true)
        assertTrue(appears("처음으로 회사 정보를 제공해 주세요!", exact = false))
        tapText("근무 주기 열기", exact = true)
        assertTrue(appears("회사 이름으로 근무주기 찾기", exact = true))
    }

    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
    }
}
