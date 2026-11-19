package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
import com.bamnat.core.imports.StaffRow
import com.bamnat.core.imports.StaffSheets
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
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
 * 2.0.0 갈래 J, 통합 2차 PC 확인(J-444 ★3·★5, Q18~22): [RVJ-40] PC-102 명단 가져오기의 일급·월급·소득세·외국인 칸과 못 읽은 칸,
 * [RVJ-42] PC-104 주 40시간을 넘는 월급 직원에게 한 번 묻기, [RVJ-41] PC-103 계약서 '매월 말일'. The shots draw the 'before' too
 * (no assertion in them); the checks are in the tests that name what they look for.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjImportScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    private fun seedShop(monthly: Boolean) = runBlocking {
        val store = app.container.phaseThree
        store.saveWorkplace(Workplace("p1", "식당", fivePlus = true, payday = 31, payNextMonth = false))
        if (monthly) store.saveStaff(StaffMember("m", "박매니저", form = WageForm.MONTH, base = 2_500_000L, hiredOn = LocalDate.of(2026, 1, 5), job = "매니저",
            weekly = DayOfWeek.entries.take(6).associateWith { nine }, insurance = Insurance.ALL), "p1", 0)
        else store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, hiredOn = LocalDate.of(2026, 3, 2), job = "홀",
            weekly = DayOfWeek.entries.take(5).associateWith { nine }, insurance = Insurance.ALL), "p1", 0)
    }

    private fun ledger(monthly: Boolean): Screen.() -> Unit = {
        seedShop(monthly)
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
    }

    // ---- [RVJ-40] 검토 PC-102 ----
    private fun importing(): Screen.() -> Unit = {
        val sheet = StaffSheets.read(listOf(
            "이름,시급,일급,월급,소득세,외국인,비고", "김알바,10320,,,근로,,", "이일용,,150000,,일용,,", "박매니저,,,2500000,근로,,",
            "최디자인,,협의,,3.3%,,", "응우옌,10320,,,근로,예,수습",
        ).map { it.split(',') }, Today.date())!!
        val lines = StaffImport.lines(sheet.rows, emptyList(), Today.date())
        content { com.bamnat.app.ui.work.StaffImportDialog(lines, false, 10, sheet.unread, onDismiss = {}, onApply = { _, _ -> }) }
        settle()
    }

    @Test fun importPreview() = shoot("rvj_98_import_folded", FOLDED, importing())
    @Test fun importPreviewDarkBig() = shoot("rvj_99_import_folded_dark_200", DARK, 2f, importing())
    @Test fun importPreviewWide() = shoot("rvj_100_import_unfolded", UNFOLDED, importing())

    @Test fun importSaysWhatItCouldNotRead() = shoot("rvj_101_import_missing_folded", FOLDED) {
        importing().invoke(this)
        assertTrue(appears("읽지 않은 칸: 비고"))
        // [RVJ-40] The 요일 칸 every line lacks is said once at the top, not on each of the five lines.
        assertTrue(appears("확인: 모두 근무 요일·시간이 없어요"))
        assertTrue(scrolledTo("일급 150,000원"))
        assertTrue(scrolledTo("월급 2,500,000원"))
        assertTrue(scrolledTo("일급 칸 ‘협의’ 못 읽음"))
        assertTrue(scrolledTo("금액을 적으면 더할 수 있어요: 최디자인"))
        assertTrue("금액이 비면 더할 수 없어요", !enabled("5명 더하기"))
        typeInto("일급 금액", "150000")
        repeat(2) { settle() }
        assertTrue(gone("금액을 적으면 더할 수 있어요"))
        assertTrue(enabled("5명 더하기"))
    }

    @Test fun theColumnsReadAreSaidFirst() = shoot("rvj_102_import_reads_folded", FOLDED) {
        ledger(false).invoke(this)
        assertTrue(scrolledTo("읽는 칸: 이름·시급·일급·월급"))
    }

    // ---- 2.0.0 [RVJ-97] 검토 U05c-02: 읽은 줄을 그 자리에서 고치기, 4대보험은 법대로 다시 ----
    private fun editing(fix: Boolean): Screen.() -> Unit = {
        val nineToSix = LocalTime.of(9, 0) to LocalTime.of(18, 0)
        val rows = listOf(
            StaffRow("김알바", WageForm.HOUR, 9_800, listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).associateWith { LocalTime.of(10, 0) to LocalTime.of(14, 0) },
                job = "홀", doubtful = setOf("base")),
            StaffRow("이주방", WageForm.HOUR, 11_000, DayOfWeek.entries.take(5).associateWith { nineToSix }, job = "주방"),
            StaffRow("박매니저", WageForm.MONTH, 2_500_000, DayOfWeek.entries.take(5).associateWith { nineToSix }, job = "매니저"),
        )
        val lines = StaffImport.lines(rows, emptyList(), Today.date())
        content { com.bamnat.app.ui.work.StaffImportDialog(lines, true, 10, onDismiss = {}, onApply = { _, _ -> }) }
        settle()
        tapText("김알바 고치기", exact = true)
        settle()
        if (fix) fixKim()
    }

    /** Two more days (주 17시간 30분) and the 최저시급 over: the line is in all three 4대보험 now. */
    private fun Screen.fixKim() {
        tapText("화", exact = true)
        settle()
        tapText("목", exact = true)
        settle()
        typeInto("시급 금액", "10500")
        repeat(2) { settle() }
    }

    @Test fun importEdit() = shoot("rvj_350_import_edit_folded", FOLDED, editing(false))
    @Test fun importEditDarkBig() = shoot("rvj_351_import_edit_folded_dark_200", DARK, 2f, editing(false))
    @Test fun importEditWide() = shoot("rvj_352_import_edit_unfolded", UNFOLDED, editing(false))

    @Test fun aFixedLineIsInsuredByTheLawAgain() = shoot("rvj_353_import_edit_fixed_folded", FOLDED) {
        val before = "4대보험 없음 · 주 15시간 미만이라 빠져요"
        editing(false).invoke(this)
        assertTrue("주 10시간 30분은 4대보험이 빠진 까닭을 말해요", appears(before))
        assertTrue(appears("확인: 금액, 2026년 최저시급 10,320원보다 적어요"))
        assertTrue("고치는 칸은 직원 정보와 같아요", appears("근로소득(간이세액표)") && appears("일하는 요일"))
        fixKim()
        assertTrue(scrolledTo("월화수목금 10:00~14:00 · 주 17시간 30분"))
        assertTrue("주 15시간을 넘으면 법대로 다시 채워요", gone(before))
        assertTrue("고친 금액의 확인은 지워요", gone("최저시급"))
        assertTrue(scrolledTo("읽은 대로 되돌리기"))
        assertTrue(enabled("3명 더하기"))
    }

    // ---- [RVJ-42] 검토 PC-104 ----
    private fun overtime(pick: String?): Screen.() -> Unit = {
        ledger(true).invoke(this)
        tapText("직원 정보", exact = true)
        settle()
        reveal("40시간을 넘는 8시간은", exact = false)
        if (pick != null) {
            tapText(pick, exact = true)
            settle()
            reveal(pick, exact = true)
        }
    }

    @Test fun overtimeAsk() = shoot("rvj_103_monthly_ot_ask_folded", FOLDED, overtime(null))
    @Test fun overtimeAskDarkBig() = shoot("rvj_104_monthly_ot_ask_folded_dark_200", DARK, 2f, overtime(null))
    @Test fun overtimeAskWide() = shoot("rvj_105_monthly_ot_ask_unfolded", UNFOLDED, overtime(null))

    @Test fun overtimeInPay() = shoot("rvj_106_monthly_ot_in_pay_folded", FOLDED) {
        overtime("월급에 들어 있어요").invoke(this)
        assertTrue(scrolledTo("월급 = 기본급 "))
    }

    @Test fun overtimeAsksUntilPicked() = shoot("rvj_107_monthly_ot_unpicked_folded", FOLDED) {
        overtime(null).invoke(this)
        assertTrue(scrolledTo("고르기 전에는 ‘더 줘요’로 셈해요"))
    }

    @Test fun ledgerSaysItIsUnasked() = shoot("rvj_108_monthly_ot_ledger_folded", FOLDED) {
        ledger(true).invoke(this)
        reveal("40시간 넘는 몫 정하기", exact = true)
        tapText("40시간 넘는 몫 정하기", exact = true)
        settle()
        assertTrue(appears("40시간을 넘는 주 8시간을 월급 말고 더 주나요?"))
    }

    // ---- [RVJ-41] PC-103 · [RVJ-42] 계약서 ----
    @Test fun contractSaysLastDayAndOvertime() = shoot("rvj_109_contract_ot_folded", FOLDED) {
        ledger(true).invoke(this)
        tapText("계약서", exact = true)
        settle()
        assertTrue(scrolledTo("지급일: 매월 말일"))
        assertTrue(scrolledTo("한 주 소정근로시간 40시간, 그 밖의 평소 근무 한 주 8시간은 연장근로"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
