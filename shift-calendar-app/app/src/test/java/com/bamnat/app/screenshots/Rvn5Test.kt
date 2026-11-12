package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PublicUse
import com.bamnat.app.ui.company.Phase
import com.bamnat.app.ui.company.Phases
import com.bamnat.app.ui.company.left
import com.bamnat.app.ui.company.nextAsk
import com.bamnat.core.model.ShiftType
import com.bamnat.core.plaza.CompanyNames
import com.bamnat.core.plaza.PublicCycles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-19] 검토 COMP-20·U10a-11·I10ab-01·T10a-10 회사 이름 도우미, U10a-08 판 카드, U10a-09 '오늘은 어떤 근무예요?', and the
 * 사업장 of a first provider carried to the plaza. On the screens as a person uses them, with the phone's own company list.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn5Test : ScreenshotBase() {
    private fun Screen.find(q: String, opened: MutableList<String> = mutableListOf()) {
        val c = companyOn()
        runBlocking { c.companySearch.search("예시") }
        val vm = com.bamnat.app.ui.company.CompanyFindViewModel(c).apply { query.value = q }
        content { com.bamnat.app.ui.company.CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = { opened += it }, onByHand = {}, focus = false) }
        waitFor(600)
    }

    @Test fun aNameNotFoundOffersTheOnesItMayMeanFirstAndTheSiteGoesApart() = shoot("rvn_t_name_like", FOLDED) {
        val opened = mutableListOf<String>()
        find("얘시정밀 당진공장", opened)
        assertTrue(appears("혹시 이 회사인가요?", exact = true))
        assertTrue(appears("(주)예시정밀", exact = true))
        assertTrue(appears("한두 글자만 달라요", exact = false))
        // The 사업장 typed after the name is in its own box; the name's box keeps what was typed, to fix or keep.
        assertTrue(appears("당진공장", exact = true))
        assertTrue(appears("얘시정밀", exact = true))
        assertTrue(appears("‘얘시정밀’ 근무주기 넣기", exact = true))
        // The company it meant opens instead of a new one.
        tapText("(주)예시정밀", exact = true)
        assertEquals(listOf("c1"), opened)
    }

    @Test fun aNameStillBeingTypedCannotStartANewCompany() = shoot("rvn_t_name_unfinished", FOLDED) {
        find("한별정ㅁ")
        assertTrue(appears(CompanyNames.words(CompanyNames.Problem.UNFINISHED), exact = true))
        assertTrue(appears("근무주기 넣기", exact = true))
        assertTrue(gone("‘한별정ㅁ’ 근무주기 넣기", exact = true))
    }

    @Test fun theReportOffersTheKnownNameAndTakesIt() = shoot("rvn_t_report_name", FOLDED) {
        val c = companyOn()
        runBlocking { c.companySearch.search("예시") }
        c.companyStore.setDraft(com.bamnat.app.company.CompanyDraft("얘시정밀 당진공장"))
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        waitFor(600)
        assertTrue(appears("혹시 이 회사인가요?", exact = true))
        tapText("이 이름으로", exact = false)
        // The known spelling, and the 사업장 that was in the name below it.
        assertTrue(appears("(주)예시정밀", exact = true))
        assertTrue(gone("혹시 이 회사인가요?", exact = true))
    }

    @Test fun theReportOfTheCompanyInUseIsNotSecondGuessed() = shoot("rvn_t_report_used", FOLDED) {
        val c = companyOn()
        runBlocking { c.companySearch.search("예시") }
        c.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("c1", "(주)예시정밀", "", "s1", 1, true, com.bamnat.core.time.Today.instant()))
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        waitFor(600)
        assertTrue(gone("혹시 이 회사인가요?", exact = true))
    }

    @Test fun theCardShowsOneBigButtonAndTheRestBehindMore() = shoot("rvn_t_card_more", FOLDED) {
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(companyOn(), "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        reveal("이 판 더 보기", exact = true)
        assertTrue(gone("바뀐 기록", exact = true))
        assertTrue(gone("우리는 달라요 · 다른 점 말하기", exact = true))
        assertTrue(appears("맞아요", exact = true))
        tapText("이 판 더 보기", exact = true)
        assertTrue(appears("바뀐 기록", exact = true))
        assertTrue(appears("이 판 접기", exact = true))
    }

    @Test fun aLongOrderAsksTodayFirstAndKeepsTheButtonInSight() = shoot("rvn_t_phases_28", FOLDED) {
        val c = companyOn()
        company.file = com.bamnat.core.plaza.SetupEdit.of(company.file)!!.rotation(Rvn5ScreenshotTest.LONG).text
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = true, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, openPhasesFor = "s1") }
        settle()
        assertTrue(appears("오늘 근무를 누르면 맞는 줄만 남아요.", exact = false))
        // The button is on screen before anything is scrolled.
        assertTrue(shows("이 순서로 쓸게요", exact = true))
        tapText("야간", exact = true)
        assertTrue(appears("오늘 야간", exact = false))
        assertTrue(appears("다시 고르기", exact = true))
    }

    @Test fun theRowsNarrowDayByDay() {
        val d = ShiftType(id = 1, name = "주간", label = "주", colorArgb = 0xFF5C6BC0, start = null, end = null)
        val n = ShiftType(id = 2, name = "야간", label = "야", colorArgb = 0xFF283593, start = null, end = null)
        // 주주야야휴휴: six rows; today 주간 leaves two (주주야야휴휴, 주야야휴휴주), tomorrow tells them apart.
        val order = listOf(d, d, n, n, null, null)
        val rows = order.indices.map { s -> Phase(s, (0 until 7).map { order[(s + it) % order.size] }) }
        val p = Phases(rows, order.size, current = null)
        assertEquals(0, p.nextAsk(emptyMap()))
        val today = mapOf(0 to "주간")
        assertEquals(listOf(0, 1), p.left(today).map { it.step })
        assertEquals(1, p.nextAsk(today))
        val both = today + (1 to "야간")
        assertEquals(listOf(1), p.left(both).map { it.step })
        assertNull(p.nextAsk(both))
        // A day every row has alike is not asked.
        val same = Phases(listOf(Phase(0, listOf(d, d, n)), Phase(1, listOf(d, n, n))), 3, current = null)
        assertEquals(1, same.nextAsk(emptyMap()))
    }

    @Test fun aFirstProvidersSiteIsKeptWithItsCycle() {
        val cycle = PublicCycles.provided("한별정밀", "4조2교대", " 당진공장 ")
        assertEquals("당진공장", cycle.site)
        val use = PublicUse(cycle.id, cycle.company, cycle.formKey, "", false, 1L, site = cycle.site)
        val back = PublicUse.read(use.json())!!
        assertEquals("당진공장", back.site)
        assertEquals("당진공장", back.cycle!!.site)
        // An old one without it reads as before.
        assertEquals("", PublicUse.read(use.copy(site = "").json())!!.site)
    }

    @Test fun theWeekBecomesTwoWeeksInTheProposalEditor() = shoot("rvn_t_weekly_two", FOLDED) {
        val c = companyOn()
        company.file = org.json.JSONObject(company.file).apply {
            remove("rotation")
            put("weekly", org.json.JSONArray(listOf("주간", "주간", "주간", "주간", "주간", org.json.JSONObject.NULL, org.json.JSONObject.NULL)))
        }.toString()
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        tapText("이 판 더 보기", exact = true)
        tapText("우리는 달라요 · 다른 점 말하기", exact = true)
        tapText("격주로 달라요 · 2주 순서로 바꾸기", exact = true)
        assertTrue(appears("2주 토 쉼", exact = true))
        tapText("2주 토 쉼", exact = true)
        assertTrue(appears("바꾼 곳", exact = false))
    }
}
