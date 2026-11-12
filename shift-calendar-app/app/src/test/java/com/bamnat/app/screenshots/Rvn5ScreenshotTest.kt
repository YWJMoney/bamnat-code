package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 N 5차 [RVN-19] (회사 이름 도우미, 판 카드 단추 하나, 오늘 근무 먼저, 격주 순서): the screens it touches, light·dark ×
 * 글자 100%·200% × 접은·편 폭, for the PR's before/after sheet. Only taps both the old and new screens have (a tap the new one
 * needs first is made only when it shows), so the same test draws the 'before' on the commit before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn5ScreenshotTest : ScreenshotBase() {
    /** 회사 찾기 with a name one letter off a company the phone knows (얘시정밀 for (주)예시정밀) and a 사업장 typed after it. */
    private fun notFound(): Screen.() -> Unit = {
        val c = companyOn()
        runBlocking { c.companySearch.search("예시") }
        val vm = com.bamnat.app.ui.company.CompanyFindViewModel(c).apply { query.value = "얘시정밀 당진공장" }
        content { com.bamnat.app.ui.company.CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        waitFor(600)
    }

    /** 내 설정 나누기 under a name one letter off. */
    private fun report(): Screen.() -> Unit = {
        val c = companyOn()
        runBlocking { c.companySearch.search("예시") }
        c.companyStore.setDraft(com.bamnat.app.company.CompanyDraft("얘시정밀"))
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        waitFor(600)
        reveal("부서·조 (선택)", exact = true)
    }

    /** '오늘은 어떤 근무예요?' on a 28-day order. */
    private fun phases28(): Screen.() -> Unit = {
        val c = companyOn()
        company.file = com.bamnat.core.plaza.SetupEdit.of(company.file)!!.rotation(LONG).text
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = true, onBack = {}, onApplied = { _, _ -> }, onByHand = {}, openPhasesFor = "s1") }
        settle()
    }

    /** A company's 판 card as it first shows (U10a-08). */
    private fun card(): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(companyOn(), "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        reveal("다니는 회사라면 이 규칙이 맞는지 알려 주세요", exact = true)
    }

    /** 다른 점 말하기 on a weekday company (요일 순서), where 격주 has its button now. */
    private fun weekly(): Screen.() -> Unit = {
        val c = companyOn()
        company.file = JSONObject(company.file).apply {
            remove("rotation")
            put("weekly", JSONArray(listOf("주간", "주간", "주간", "주간", "주간", JSONObject.NULL, JSONObject.NULL)))
        }.toString()
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        if (shows("이 판 더 보기", exact = true)) tapText("이 판 더 보기", exact = true)
        tapText("우리는 달라요 · 다른 점 말하기", exact = true)
        reveal("요일을 누르면 다음 근무로 바뀌어요", exact = true)
    }

    @Test fun notFoundLight() = shoot("rvn_54_not_found_folded", FOLDED, notFound())
    @Test fun notFoundDarkBig() = shoot("rvn_55_not_found_folded_dark_200", DARK, 2f, notFound())
    @Test fun notFoundWide() = shoot("rvn_56_not_found_unfolded", UNFOLDED, notFound())
    @Test fun reportLight() = shoot("rvn_57_report_name_folded", FOLDED, report())
    @Test fun reportDarkBig() = shoot("rvn_58_report_name_folded_dark_200", DARK, 2f, report())
    @Test fun reportWide() = shoot("rvn_59_report_name_unfolded_dark", DARK_WIDE, report())
    @Test fun phasesLight() = shoot("rvn_60_phases_28_folded", FOLDED, phases28())
    @Test fun phasesDarkBig() = shoot("rvn_61_phases_28_folded_dark_200", DARK, 2f, phases28())
    @Test fun phasesWide() = shoot("rvn_62_phases_28_unfolded", UNFOLDED, phases28())
    @Test fun cardLight() = shoot("rvn_63_card_folded", FOLDED, card())
    @Test fun cardDarkBig() = shoot("rvn_64_card_folded_dark_200", DARK, 2f, card())
    @Test fun cardWide() = shoot("rvn_65_card_unfolded_dark", DARK_WIDE, card())
    @Test fun weeklyLight() = shoot("rvn_66_weekly_editor_folded", FOLDED, weekly())
    @Test fun weeklyDarkBig() = shoot("rvn_67_weekly_editor_folded_dark_200", DARK, 2f, weekly())
    @Test fun weeklyWide() = shoot("rvn_68_weekly_editor_unfolded", UNFOLDED, weekly())

    internal companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DARK_WIDE = "w707dp-h823dp-port-night-420dpi"

        /** 28 days: a week of 주간, two off, a week of 야간, three off, then 주간·야간 by twos with days off. */
        val LONG: List<String?> = List(7) { "주간" } + listOf(null, null) + List(7) { "야간" } + listOf(null, null, null) +
            listOf("주간", "주간", "야간", "야간", null, "주간", "야간", null, null)
    }
}
