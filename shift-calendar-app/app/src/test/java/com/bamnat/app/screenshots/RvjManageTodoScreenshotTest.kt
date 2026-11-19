package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffShift
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 갈래 J 16차 [RVJ-95] 검토 U05c-01·I05ca-03 (관리 화면 할 일에 신고 기한·서명 안 한 계약서·대타 답이 없어 '할 일'이 반쪽이었어요)
 * and [RVJ-96] U05c-05 (신고 기한을 알림으로 알려 주지 않았어요). 오늘은 사진 시계의 2026-10-14(수): 가게 하나, 지급일 10일, 이주방 10/5
 * 입사(4대보험 신고 10/15·11/16), 박홀에게 10/9에 보낸 근로계약서가 서명 전, 10/16 김알바 대타에 이주방이 '돼요'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjManageTodoScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val week = DayOfWeek.entries.take(5).associateWith { nine }

    private fun seedShop() = runBlocking {
        val store = app.container.phaseThree
        val sent = LocalDate.of(2026, 10, 9).atTime(14, 0).atZone(Today.zone()).toInstant().toEpochMilli()
        store.saveWorkplace(Workplace("p1", "우리 가게", fivePlus = true, payday = 10,
            calls = listOf(TeamCall("C1", null, TeamCover.Gap("s1", LocalDate.of(2026, 10, 16), 0), listOf("s2", "s3"), answers = mapOf("s2" to true, "s3" to false)))))
        store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, hiredOn = LocalDate.of(2025, 3, 3), weekly = week, insurance = Insurance.ALL), "p1", 0)
        store.saveStaff(StaffMember("s2", "이주방", base = 11_000L, hiredOn = LocalDate.of(2026, 10, 5), weekly = week, insurance = Insurance.ALL), "p1", 1)
        store.saveStaff(StaffMember("s3", "박홀", base = 10_500L, hiredOn = LocalDate.of(2026, 9, 1), weekly = week, insurance = Insurance.ALL,
            contract = StaffContract.Sent("근로계약서", emptyList(), sent, linkId = "G1", expiresAt = sent + 14 * 86_400_000L)), "p1", 2)
    }

    private fun manage(): Screen.() -> Unit = style(WorkStyle.BUSINESS) {
        seedShop()
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
        // The 신고 lines are worked out off the main thread from five months of the ledger.
        repeat(4) { if (!shows("대타", exact = false)) settle() }
        repeat(4) { if (!shows("취득 신고", exact = false)) settle() }
    }

    /** The 할 일 on top: the 대타 yes first (one press settles it), then the 신고 dates with how many days, the contract waiting. */
    private fun todos(): Screen.() -> Unit = {
        manage().invoke(this)
        assertTrue(appears("우리 가게 10/16 김알바님 대타: 이주방님이 들어갈 수 있어요"))
        assertTrue(shownWords().joinToString(" | "), scrolledTo("건강보험 자격 취득 신고 · 이주방"))
        assertTrue(scrolledTo("박홀님 근로계약서 서명을 5일째 기다려요"))
        assertTrue("급여 지급일은 닫기·지급 줄이 말해요", !shows("9월분 급여 지급", exact = false))
        reveal("할 일", exact = false)
    }

    @Test fun theToDos() = shoot("rvj_340_manage_todo_folded", FOLDED, todos())
    @Test fun theToDosDarkBig() = shoot("rvj_341_manage_todo_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, todos())
    @Test fun theToDosUnfolded() = shoot("rvj_342_manage_todo_unfolded", UNFOLDED, todos())

    /** A 신고 line opens the 급여 대장 on its 다가올 신고·지급 (the card in view), which says the 3일 전 알림 with its switch. */
    private fun dueOpensFilings(): Screen.() -> Unit = {
        manage().invoke(this)
        tapText("건강보험 자격 취득 신고 · 이주방", exact = false)
        repeat(6) { if (!shows("기한 3일 전 아침 9시에", exact = false)) settle() }
        assertTrue(appears("다가올 신고·지급", exact = false))
        assertTrue(shownWords().joinToString(" | "), scrolledTo("기한 3일 전 아침 9시에 알림으로도 알려 드려요"))
    }

    @Test fun aDueOpensTheFilings() = shoot("rvj_343_manage_due_filings_folded", FOLDED, dueOpensFilings())
    @Test fun aDueOpensTheFilingsDarkBig() = shoot("rvj_344_manage_due_filings_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, dueOpensFilings())

    /** The contract line opens that person's 근로계약서 straight away. */
    @Test fun theContractOpensItsSheet() = shoot("rvj_345_manage_contract_folded", FOLDED) {
        manage().invoke(this)
        tapText("박홀님 근로계약서 서명을", exact = false)
        repeat(4) { if (!shows("10월 9일에 보냈어요", exact = false)) settle() }
        assertTrue(appears("에 보냈어요", exact = false))
    }
}
