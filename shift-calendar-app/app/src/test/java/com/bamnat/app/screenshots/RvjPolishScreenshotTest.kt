package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.time.Today
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J 세 번째 PR (디자인 품질 조건 2·6·13): 급여 리포트 카드의 단추, 연차 소멸 예정 줄과 사장님 급여 대장의 주의 색, 일근의
 * '이번 주 근무' 한도. Light·dark × 글자 100%·200% × 접은·편 폭, written against what both the old and new screens show so the same
 * test draws the 'before' (the sheet and the asks that are new are after-only).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjPolishScreenshotTest : ScreenshotBase() {
    // ---- [RVJ-22] 검토 HQ-11: 급여 › 도구 › 급여 리포트 ----
    private fun report(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true).invoke(this)
        tapText("도구", exact = true, scrolls = 0)
        settle()
        reveal("급여 리포트", exact = true)
    }

    @Test fun reportLight() = shoot("rvj_30_report_folded", FOLDED, report())
    @Test fun reportDarkBig() = shoot("rvj_31_report_folded_dark_200", DARK, 2f, report())
    @Test fun reportWide() = shoot("rvj_32_report_unfolded", UNFOLDED, report())

    /** The card keeps the month's PDF one press away and nothing else; the other seven are a list behind '다른 리포트'. */
    private fun reportSheet(): Screen.() -> Unit = {
        report().invoke(this)
        assertTrue("이 달 PDF는 카드에 그대로", scrolledTo("월 PDF", scrolls = 0))
        listOf("월 CSV", "년 PDF", "상담용 묶음", "근무 기록 증빙").forEach { assertTrue("'$it'는 카드에 줄지어 있지 않아요", gone(it)) }
        tapText(com.bamnat.app.ui.pay.OTHER_REPORTS, exact = true)
        settle()
        listOf("월 CSV", "년 PDF", "년 CSV", "근무 기록 증빙", "최근 3개월 근무 기록", "상담용 묶음").forEach { assertTrue("'$it'는 목록에", scrolledTo(it)) }
    }

    @Test fun reportSheetLight() = shoot("rvj_33_report_sheet_folded", FOLDED, reportSheet())
    @Test fun reportSheetDarkBig() = shoot("rvj_34_report_sheet_folded_dark_200", DARK, 2f, reportSheet())

    // ---- [RVJ-19] 검토 HQ-12: 급여 › 결과 › 연차 카드의 소멸 예정 줄 ----
    private fun lapse(): Screen.() -> Unit = {
        leaveSeed()
        content { com.bamnat.app.ui.pay.PayScreen(com.bamnat.app.ui.pay.PayViewModel(app.container), onBack = {}) }
        settle()
        reveal("소멸 예정", exact = false)
    }

    @Test fun lapseLight() = shoot("rvj_35_leave_lapse_folded", FOLDED, lapse())
    @Test fun lapseDarkBig() = shoot("rvj_36_leave_lapse_folded_dark_200", DARK, 2f, lapse())

    // ---- [RVJ-19] 사장님 급여 대장: 알려 주는 줄은 주의 색 ----
    private val roster = StaffRoster(listOf(
        Staff("s1", "김알바", 10_320, shifts = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }),
        Staff("s2", "이주말", 10_320, shifts = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).associateWith { StaffShift(LocalTime.of(10, 0), LocalTime.of(19, 0)) }),
    ))

    private fun payroll(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.PayrollScreen(roster, YearMonth.from(Today.date())) {} }
        settle()
    }

    private fun staffInsurance(): Screen.() -> Unit = {
        payroll().invoke(this)
        tapText("직원 정보", exact = true)
        settle()
        reveal("4대보험 가입 대상이에요", exact = false)
    }

    @Test fun ledgerLight() = shoot("rvj_37_ledger_folded", FOLDED, payroll())
    @Test fun ledgerDarkBig() = shoot("rvj_38_ledger_folded_dark_200", DARK, 2f, payroll())
    @Test fun ledgerWide() = shoot("rvj_39_ledger_unfolded", UNFOLDED, payroll())
    @Test fun staffInsuranceLight() = shoot("rvj_40_staff_insurance_folded", FOLDED, staffInsurance())
    @Test fun staffInsuranceDarkBig() = shoot("rvj_41_staff_insurance_folded_dark_200", DARK, 2f, staffInsurance())

    // ---- [RVJ-24] 검토 BOSS-53: 계약서는 아래 시트, '종이로 받았어요'는 받은 날을 묻고, 잘못 누른 표시는 되돌려요 ----
    private fun contract(step: Int): Screen.() -> Unit = {
        payroll().invoke(this)
        tapText("계약서", exact = true)
        settle()
        if (step >= 1) {
            tapText(com.bamnat.app.ui.work.PAPER, exact = true)
            settle()
            assertTrue("받은 날을 먼저 물어요", scrolledTo("종이 계약서를 받았나요?", exact = true, scrolls = 0))
            assertTrue("빈 것이 있으면 알려요", scrolledTo("종이에는 다 적혀 있는지 봐 주세요", scrolls = 0))
        }
        if (step >= 2) {
            tapText("받았어요", exact = true)
            settle()
            assertTrue(scrolledTo("종이 계약서로 받았어요 · "))
        }
        if (step >= 3) {
            tapText(com.bamnat.app.ui.work.PAPER_UNDO, exact = true)
            settle()
            assertTrue("표시가 지워져요", gone("종이 계약서로 받았어요 · "))
            assertTrue("다시 초안으로", scrolledTo(com.bamnat.app.ui.work.PAPER, exact = true))
        }
    }

    @Test fun contractDraftLight() = shoot("rvj_44_contract_draft_folded", FOLDED, contract(0))
    @Test fun contractDraftDarkBig() = shoot("rvj_45_contract_draft_folded_dark_200", DARK, 2f, contract(0))
    @Test fun contractDraftWide() = shoot("rvj_46_contract_draft_unfolded", UNFOLDED, contract(0))
    @Test fun paperAskLight() = shoot("rvj_47_paper_ask_folded", FOLDED, contract(1))
    @Test fun paperAskDarkBig() = shoot("rvj_48_paper_ask_folded_dark_200", DARK, 2f, contract(1))
    @Test fun paperMarked() = shoot("rvj_49_paper_marked_folded", FOLDED, contract(2))
    @Test fun paperMarkedDarkBig() = shoot("rvj_50_paper_marked_folded_dark_200", DARK, 2f, contract(2))
    @Test fun paperTakenBack() = shoot("rvj_51_paper_undone_folded", FOLDED, contract(3))

    // ---- [RVJ-23] 검토 PC-51: 일근, 급여 설정 전 '이번 주 근무' ----
    private fun dayWeek(): Screen.() -> Unit = {
        val c = app.container
        runBlocking { c.payStore.save(null, keepUndo = false) }
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY) }
        settle()
        tab(3, wide = false)
        reveal("이번 주 근무", exact = false)
    }

    @Test fun dayWeekLight() = shoot("rvj_42_day_week_folded", FOLDED, dayWeek())
    @Test fun dayWeekDarkBig() = shoot("rvj_43_day_week_folded_dark_200", DARK, 2f, dayWeek())

    // ---- [RVJ-27] 검토 PAYU-57·T05b-13: 팀 근무표 › 파일·사진에서 가져오기 ----
    private val teamShifts = listOf(
        com.bamnat.core.team.TeamShift(1, "D", "주간", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
        com.bamnat.core.team.TeamShift(2, "E", "저녁", 0xFFE39B3B, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
        com.bamnat.core.team.TeamShift(3, "N", "야간", 0xFF5E4AA8, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
        com.bamnat.core.team.TeamShift(4, "O", "휴무", 0xFF9AA3AD, true),
    )
    private val teamMembers = listOf("김민서", "이준호", "박하은", "서연", "정도윤").mapIndexed { i, n -> com.bamnat.core.team.TeamMember("m$i", "t1", n, sort = i) }

    /** A photo's read: 김민서 came out as 김민셔, and 최다인 is not on the team yet. */
    private fun teamGrid(): com.bamnat.core.imports.RosterGrid {
        val month = YearMonth.from(Today.date()).plusMonths(1)
        val codes = listOf("D", "D", "E", "휴", "N", "N", "OFF")
        val rows = listOf("김민셔 책임", "이준호", "박하은", "최다인", "서연").mapIndexed { i, n ->
            com.bamnat.core.imports.GridRow(n.substringBefore(" "), n.substringAfter(" ", "").ifBlank { null },
                (1..28).associate { d -> month.atDay(d) to codes[(d + i) % codes.size] }, if (i == 0) setOf(month.atDay(3)) else emptySet())
        }
        return com.bamnat.core.imports.RosterGrid(month, (1..28).map(month::atDay), rows, codes.distinct())
    }

    private fun teamImport(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.team.TeamImportDialog(teamGrid(), true, teamMembers, teamShifts, onDismiss = {}, onApply = {}) }
        settle()
    }

    @Test fun teamImportLight() = shoot("rvj_52_team_import_folded", FOLDED, teamImport())
    @Test fun teamImportDarkBig() = shoot("rvj_53_team_import_folded_dark_200", DARK, 2f, teamImport())
    @Test fun teamImportWide() = shoot("rvj_54_team_import_unfolded", UNFOLDED, teamImport())

    /** After only: the name a letter off is that member with a line to check, the new member's name can be fixed, a line opens to its cells. */
    private fun teamImportChecked(): Screen.() -> Unit = {
        teamImport().invoke(this)
        assertTrue("한 글자 다른 이름은 그 팀원으로 맞춰 둬요", scrolledTo("비슷한 이름이라 김민서님으로 맞춰 뒀어요", scrolls = 0))
        assertTrue("새 팀원은 이름을 고칠 칸이 있어요", scrolledTo("새 팀원 이름", exact = true))
        assertTrue(scrolledTo("새 팀원 1명을 더해요: 최다인"))
        tapText("김민셔 (책임)", exact = true)
        settle()
        assertTrue("줄을 누르면 읽은 칸이 보여요", scrolledTo("1 D · 2 E · 3 휴?"))
    }

    @Test fun teamImportCells() = shoot("rvj_55_team_import_cells_folded", FOLDED, teamImportChecked())

    @Test fun teamImportUndoAsk() = shoot("rvj_56_team_import_undo_ask_folded", FOLDED) {
        content { com.bamnat.app.ui.team.ImportUndoAsk(listOf(com.bamnat.core.team.TeamMember("n1", "t1", "최다인"), com.bamnat.core.team.TeamMember("n2", "t1", "한지우")), onRemove = {}, onKeep = {}) }
        settle()
    }

    // ---- [RVJ-25] 검토 PAYU-11·PAYC-21: 급여 화면 맨 위, 설정 파일을 못 읽었거나 저장이 안 됐을 때의 한 줄 (새 줄이라 뒤만) ----
    private fun payTrouble(): Screen.() -> Unit = {
        content {
            androidx.compose.foundation.layout.Column {
                com.bamnat.app.pay.PayStore.Trouble.entries.forEach { t -> com.bamnat.app.ui.pay.PayTroubleBand(t, onHistory = {}, onSeen = {}, onRetry = {}) }
            }
        }
        settle()
        assertTrue(scrolledTo(com.bamnat.app.ui.pay.TROUBLE_RESTORED, exact = true, scrolls = 0))
        assertTrue(scrolledTo(com.bamnat.app.ui.pay.TROUBLE_LOST, exact = false, scrolls = 0))
        assertTrue(scrolledTo(com.bamnat.app.ui.pay.TROUBLE_NOT_SAVED, exact = true, scrolls = 0))
        assertTrue("저장될 때까지 남는 줄에는 '다시 저장'", scrolledTo("다시 저장", exact = true, scrolls = 0))
        assertTrue("읽지 못한 줄에는 그 자리로 가는 단추", scrolledTo(com.bamnat.app.ui.pay.TROUBLE_IMPORT, exact = true, scrolls = 0))
    }

    @Test fun payTroubleLight() = shoot("rvj_57_pay_trouble_folded", FOLDED, payTrouble())
    @Test fun payTroubleDarkBig() = shoot("rvj_58_pay_trouble_folded_dark_200", DARK, 2f, payTrouble())

    /**
     * #460 사진 검토: '저장하지 못했어요'는 X가 없어요. 저장이 안 되는 동안은 남고, 저장되면 바로 사라져요. '읽지 못했어요'의
     * '파일 불러오기'는 설정 › 데이터의 그 줄로 가요.
     */
    @Test fun payTroubleGoesWhenSaved() = shoot("rvj_59_pay_trouble_saved_folded", FOLDED) {
        val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        val dir = java.io.File(context.filesDir, "pay-trouble-shot").apply { deleteRecursively() }
        fun pay(won: Long) = com.bamnat.core.pay.PayProfile(com.bamnat.core.pay.Occupation.CUSTOM, com.bamnat.core.pay.WageType.MONTHLY,
            com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(java.time.LocalDate.of(2026, 1, 1), won))))
        val store = com.bamnat.app.pay.PayStore(context, dir)
        runBlocking {
            store.save(pay(3_000_000L))
            // The folder is gone and a file stands where it was: nothing can be written into it.
            dir.deleteRecursively(); dir.writeText("x")
            store.save(pay(3_300_000L))
        }
        com.bamnat.app.ui.settings.SettingsLinks.target.value?.let(com.bamnat.app.ui.settings.SettingsLinks::consume)
        content {
            val trouble by store.trouble.collectAsState()
            androidx.compose.foundation.layout.Column {
                com.bamnat.app.ui.pay.PayTroubleBand(trouble, onHistory = null, onSeen = store::troubleSeen, onRetry = { runBlocking { store.retrySave() } })
                com.bamnat.app.ui.pay.PayTroubleBand(com.bamnat.app.pay.PayStore.Trouble.LOST, onHistory = null, onSeen = {}, onRetry = {})
            }
        }
        settle()
        assertTrue(shows(com.bamnat.app.ui.pay.TROUBLE_NOT_SAVED, exact = true))
        tapText("다시 저장", exact = true, scrolls = 0)
        settle()
        assertTrue("저장이 안 되는 동안은 남아요", shows(com.bamnat.app.ui.pay.TROUBLE_NOT_SAVED, exact = true))
        dir.delete()
        tapText("다시 저장", exact = true, scrolls = 0)
        assertTrue("저장되면 바로 사라져요", gone(com.bamnat.app.ui.pay.TROUBLE_NOT_SAVED, exact = true))
        assertTrue("다시 저장 단추도 함께", gone("다시 저장", exact = true))
        tapText(com.bamnat.app.ui.pay.TROUBLE_IMPORT, exact = true, scrolls = 0)
        val there = com.bamnat.app.ui.settings.SettingsLink.Page(com.bamnat.app.ui.settings.SettingsPage.DATA, "파일 불러오기")
        org.junit.Assert.assertEquals("설정 › 데이터 › 파일 불러오기 줄로", there, com.bamnat.app.ui.settings.SettingsLinks.target.value)
        com.bamnat.app.ui.settings.SettingsLinks.consume(there)
        dir.deleteRecursively()
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
