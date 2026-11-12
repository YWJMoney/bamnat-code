package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.consent.ConsentItem
import com.bamnat.app.data.GuideStore
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.tier.Tier
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [FIT-01][FIT-02][FIT-03][TIER-06][TIER-07][VIS-06] 1.25 묶음 Y's screenshots, numbers 890~909, each an end-to-end
 * check (D76): a tap on the screen, what the screen shows, and what was saved. 890~895 a calendar shows only what
 * its kind of work uses and keeps what the user adds, 896~899 "어떤 일을 하고 계신가요?", 900~903 the 프로 badge and
 * 프로 기능 모아보기, 904~906 the settings groups' colours, 907~909 the help and the search.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class YScreenshotTest : ScreenshotBase() {

    internal companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val LATER = "허용은 나중에 할게요"
    }

    private val added: Set<String> get() = GuideStore.get(app).current.addedTo(PersonalCalendar.MAIN)

    /** The guide's store outlives a test in this JVM: nothing added by an earlier one. */
    @org.junit.Before fun nothingAdded() = GuideStore.get(app).restoreAdded(org.json.JSONArray())

    private fun Screen.tools() = tapText("도구")

    // ---- 391 [FIT-01][FIT-02] ----
    @Test fun investToolsHideShiftTools() = shoot("890_y_invest_tools", FOLDED, style(WorkStyle.INVEST) {
        tools()
        assertTrue("근무 주기 설정 is put away", gone("근무 주기 설정", exact = true))
        // [FIT-05] 1.36: 가족·연인 근무표는 투자 달력도 받아요.
        assertTrue(appears("동료 근무표 받기", exact = true))
        assertTrue("달력 공유, not 근무표", appears("달력 공유", exact = true))
        reveal("이 달력에 안 맞아 숨긴 도구")
    })

    @Test fun investAddsAToolBackAndItStays() = shoot("891_y_invest_tool_added", FOLDED, style(WorkStyle.INVEST) {
        tools()
        tapText("이 달력에 안 맞아 숨긴 도구")
        tapText("더하기", exact = true)
        val got = added.single()
        val id = got.substringAfter(':')
        assertEquals(FitSurface.CALENDAR_TOOLS.name, got.substringBefore(':'))
        assertTrue(FitTable.visible(FitSurface.CALENDAR_TOOLS, id, WorkStyle.INVEST, added))
        // The sheet shows it among the tools now, and after it is closed and opened again.
        val label = mapOf("pattern" to "근무 주기 설정", "typeMonth" to "이번 달 전부 넣기", "receive" to "동료 근무표 받기", "teams" to "모든 조 보기").getValue(id)
        // Closed and opened again (the system back here would not reach the sheet's own window).
        tap(180f, 60f)
        assertTrue(gone("달력 도구", exact = true))
        tools()
        assertTrue(appears(label, exact = true))
        // 더 보기 opens by itself on what was added (its rows may sit below the fold; a swipe would move the sheet).
        assertTrue("더 보기 stays open on what was added", appears("이 달력에 안 맞아 숨긴 도구 2개 · 접기", exact = true))
    })

    @Test fun investCustomizeHasNoShiftParts() = shoot("892_y_invest_customize", FOLDED, style(WorkStyle.INVEST) {
        tools()
        // [CNV-08] 카드 배치 is the studio's 달력 화면 now; what does not fit is under ‘＋ 카드 넣기’.
        tapText("카드 배치", exact = true)
        assertTrue(gone("근무 횟수 줄", exact = true))
        assertTrue(gone("주 근무시간 줄", exact = true))
        assertTrue(gone("휴식 점검", exact = true))
        tapText("＋ 카드 넣기", prefix = true)
        tapText("이 달력에 안 맞아 숨긴 기능")
        reveal("근무 횟수 줄", exact = true)
    })

    @Test fun investAddsAPartToTheCalendarPage() = shoot("893_y_invest_part_added", FOLDED, style(WorkStyle.INVEST) {
        tools()
        tapText("카드 배치", exact = true)
        tapText("＋ 카드 넣기", prefix = true)
        tapText("이 달력에 안 맞아 숨긴 기능")
        tapText("더하기", exact = true)
        assertTrue(added.single().startsWith(FitSurface.CALENDAR_PARTS.name + ":"))
        reveal("빼기", exact = true)
    })

    @Test fun shiftCalendarKeepsEveryTool() = shoot("894_y_shift_tools", SMALL_PHONE, style(WorkStyle.SHIFT) {
        tools()
        reveal("근무 주기 설정", exact = true)
        assertTrue("nothing is put away for shift work", gone("숨긴 도구"))
    })

    @Test fun shopToolsDark() = shoot("895_y_shop_tools_dark", FOLDED_NIGHT, style(WorkStyle.BUSINESS) {
        tools()
        assertTrue(gone("근무 주기 설정", exact = true))
    })

    // ---- 392 [FIT-03] ----
    private fun firstScreen(then: Screen.() -> Unit): Screen.() -> Unit {
        c.consentStore.forget()
        c.consentStore.availableForTest = setOf(ConsentItem.CALENDAR)
        return fresh(null, then)
    }

    private val c get() = app.container

    @Test fun nurseTypedWithATypoStartsAMonthlyRoster() = shoot("896_y_job_nurse", FOLDED, firstScreen {
        assertTrue(appears("어떤 일을 하고 계신가요?"))
        typeInto("하는 일", "간호샤")
        tapText("간호사 · 교대 근무 · 근무표가 매달 나와요", exact = true)
        // [EASE-05] No page between the job and the setup.
        assertTrue(gone(LATER))
        val d = c.setupDrafts.draft.value
        assertEquals(WorkStyle.SHIFT, d?.style)
        assertTrue(d!!.rosterMonthly)
    })

    /** 1.38 통합 6차 2eb28e83: in the small hours the order preview names the day it rings (어제), not 오늘. */
    @Test fun theOrderPreviewNamesTheAskedDay() = shoot("898c_y_order_yesterday", PHONE_393) {
        val d = com.bamnat.core.model.ShiftType(1, "주간", "주", 0xFF1E88E5, java.time.LocalTime.of(7, 0), java.time.LocalTime.of(19, 0))
        val asked = java.time.LocalDate.of(2026, 11, 5)
        content { com.bamnat.app.ui.setup.OrderPreview(listOf(1L, null), mapOf(1L to d), asked, 0, onToday = {}, realToday = asked.plusDays(1)) }
        assertTrue(appears("어제(", exact = false))
        assertTrue(gone("오늘은", exact = true))
        assertTrue(appears("다음 단계에서 어제 근무를 한 번 더 확인해요.", exact = true))
    }

    /** 1.38 PC 4a5c3828: a shop's 같이 보기 is its 영업일, never 가족·연인과 근무. */
    @Test fun aShopsTogetherSheetSaysItsDays() = shoot("898b_y_together_shop", PHONE_393) {
        content { com.bamnat.app.ui.calendar.TogetherSheet(onSend = {}, onTake = null, onDismiss = {}, shop = true) }
        assertTrue(appears("영업일 같이 보기", exact = true))
        assertTrue(gone("가족·연인과 근무 같이 보기", exact = true))
    }

    @Test fun anUnknownJobFallsBackToTheCards() = shoot("897_y_job_unknown", FOLDED, firstScreen {
        typeInto("하는 일", "우주비행사아저씨")
        assertTrue(appears("맞는 일을 찾지 못했어요"))
        reveal("또는 일하는 방식으로 고르기")
        assertTrue(appears("평일 근무", exact = true))
    })

    @Test fun aChipPicksTheJob() = shoot("898_y_job_chip", PHONE_393, firstScreen {
        // [EASE-09] 1.38 PC 994c2328: the chip starts the job at once, with no '이렇게 시작할까요?' card to tap again.
        tapText("편의점 알바", exact = true)
        assertTrue(gone("이렇게 시작할까요?"))
        assertTrue(gone(LATER))
        assertEquals(WorkStyle.HOURLY, c.setupDrafts.draft.value?.style)
    })

    @Test fun jobQuestionHugeText() = shoot("899_y_job_huge_text", PHONE, fontScale = 2f, firstScreen {
        typeInto("하는 일", "택배")
        reveal("택배 기사 · 현장 · 프리랜서")
    })

    // ---- 393 [TIER-06][TIER-07] ----
    @Test fun proCollectionOpensTheFeature() = shoot("900_y_pro_collection", FOLDED) {
        tab(SETTINGS_TAB, wide = false)
        tapText("프로", exact = true)
        reveal("프로 기능 모아보기")
        reveal("매일 자동 백업", exact = true)
        tapText("매일 자동 백업", exact = true)
        // Gone to where it lives: 설정 › 데이터, with the badge on its row.
        assertTrue(gone("프로 기능 모아보기"))
        reveal("매일 자동 백업")
        assertTrue(appears("프로 기능"))
    }

    @Test fun badgesShowBeforeTheLaunch() = shoot("901_y_pro_badge_data", FOLDED) {
        settingsPage("데이터")
        reveal("매일 자동 백업")
        assertTrue("프로 shows although everything is free now", appears("프로 기능"))
        assertTrue(gone("잠겨 있어요"))
    }

    @Test fun lockedBadgeWhenFree() = shoot("902_y_pro_badge_locked", FOLDED) {
        app.container.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
        settingsPage("데이터")
        reveal("매일 자동 백업")
        assertTrue(appears("프로 기능, 잠겨 있어요"))
    }

    @Test fun proCollectionDarkInvest() = shoot("903_y_pro_collection_dark", FOLDED_NIGHT, style(WorkStyle.INVEST) {
        tab(SETTINGS_TAB, wide = false)
        tapText("프로", exact = true)
        // 2.0.0 [RVO-05] 이 유형의 다섯만 보이고 나머지는 '모두 보기'.
        reveal("모두 보기 ·")
        tapText("모두 보기 ·")
        reveal("다른 일에 쓰는 프로 기능")
    })

    // ---- 394 [VIS-06] ----
    @Test fun settingsColours() = shoot("904_y_settings_colours", PHONE_393) {
        tab(SETTINGS_TAB, wide = false)
        reveal("내 근무", exact = true)
    }
    @Test fun settingsColoursDark() = shoot("905_y_settings_colours_dark", FOLDED_NIGHT) {
        tab(SETTINGS_TAB, wide = false)
        reveal("데이터", exact = true)
    }
    @Test fun settingsColoursHugeText() = shoot("906_y_settings_colours_200", PHONE, 2f) { tab(SETTINGS_TAB, wide = false) }

    // ---- 395 ----
    @Test fun searchFindsTheCollection() = shoot("907_y_search_collection", FOLDED) {
        tab(SETTINGS_TAB, wide = false)
        typeInto("설정 찾기", "모아보기")
        assertTrue(appears("프로 기능 모아보기"))
    }
    @Test fun investToolsSmallPhone() = shoot("908_y_invest_tools_320", SMALL_PHONE, style(WorkStyle.INVEST) { tools() })
    @Test fun investToolsLandscape() = shoot("909_y_invest_tools_landscape", PHONE_LANDSCAPE, style(WorkStyle.INVEST) { tools() })
}
