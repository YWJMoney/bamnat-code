package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
import com.bamnat.core.pay.PayPeriod
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
 * 2.0.0 갈래 J 열한 번째. [RVJ-79] 총괄 HQ-125: 눌리는 한 줄([com.bamnat.app.ui.components.ToneLine]에 onClick)은 가는 곳 낱말을
 * 알림함 줄·안내 카드처럼 파란 글자로 끝에 보여 줘요('… · 입사일 넣기', '… · 계약서 쓰기'). 사장님 신고·지급 카드, 급여 대장의 직원 줄,
 * 팀·가게 관리의 가게 카드, 명단 가져오기, 급여의 '○월 마감' 줄을 밝은 100%·어두운 200% 접은 폭으로 찍어요. The shots hold no
 * assertion so the same test draws the 'before'; the tests that name what they look for check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjToneActionScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    // ── 사장님 › 급여 대장: 다가올 신고·지급 (급여 주는 날·입사일), 직원 줄 (계약서·0원·4대보험) ──

    private fun seedShop() = runBlocking {
        val store = app.container.phaseThree
        store.saveWorkplace(Workplace("p1", "식당", fivePlus = false))
        store.saveStaff(StaffMember("s1", "삼점삼", base = 10_320L, weekly = DayOfWeek.entries.take(5).associateWith { nine },
            insurance = Insurance(tax = StaffTax.BIZ)), "p1", 0)
        store.saveStaff(StaffMember("s2", "김알바", base = 10_320L, hiredOn = LocalDate.of(2026, 3, 2),
            weekly = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).associateWith { nine }), "p1", 1)
        store.saveStaff(StaffMember("s3", "새직원", form = WageForm.HOUR, base = 10_320L, hiredOn = LocalDate.of(2026, 9, 1)), "p1", 2)
    }

    private fun shopLedger(): Screen.() -> Unit = {
        seedShop()
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
    }

    private fun filings(): Screen.() -> Unit = {
        shopLedger().invoke(this)
        var found = false
        repeat(4) { if (!found) { found = scrolledTo("다가올 신고·지급"); if (!found) settle() } }
        reveal("다가올 신고·지급", exact = false)
    }

    @Test fun theFilingsLinesSayWhereTheyGo() = shoot("rvj_270_tone_action_filings_folded", FOLDED, filings())
    @Test fun theFilingsLinesDarkBig() = shoot("rvj_271_tone_action_filings_folded_dark_200", DARK, 2f, filings())

    @Test fun theFilingsLinesNameThePlace() = shoot("rvj_272_tone_action_filings_said_folded", FOLDED) {
        filings().invoke(this)
        assertTrue(appears("급여 주는 날 고르기"))
        assertTrue(scrolledTo("입사일 넣기"))
        // The word is the line's: a tap anywhere on it still goes (the whole line is the button, 48dp).
        tapText("입사일 없는 삼점삼님 빼고예요", exact = false)
        settle()
        assertTrue("누르면 그 직원 정보", appears("하는 일 (예: 홀, 주방)"))
    }

    private fun staffRows(): Screen.() -> Unit = {
        shopLedger().invoke(this)
        reveal("계약서 없음", exact = false)
    }

    @Test fun theStaffLinesSayWhereTheyGo() = shoot("rvj_273_tone_action_staff_folded", FOLDED, staffRows())
    @Test fun theStaffLinesDarkBig() = shoot("rvj_274_tone_action_staff_folded_dark_200", DARK, 2f, staffRows())

    @Test fun theStaffLinesNameThePlace() = shoot("rvj_275_tone_action_staff_said_folded", FOLDED) {
        shopLedger().invoke(this)
        assertTrue(scrolledTo("4대보험을 고르지 않았어요"))
        assertTrue(shows("고르기", exact = false))
        assertTrue(scrolledTo("계약서 쓰기"))
        assertTrue(scrolledTo("요일 고르기"))
    }

    // ── 사장님 › 팀·가게 관리: 가게 카드의 5인 이상 ──

    private fun shopCard(): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "우리 가게", payday = 10))
            store.saveStaff(StaffMember("s1", "김알바", base = 10_320L, weekly = mapOf(DayOfWeek.MONDAY to nine)), "p1")
        }
        content { com.bamnat.app.ui.work.ManageScreen("main", onBack = {}, showAll = true) }
        repeat(2) { settle() }
        reveal("인건비", exact = false)
    }

    @Test fun theShopCardLine() = shoot("rvj_276_tone_action_shop_folded", FOLDED) {
        shopCard().invoke(this)
        assertTrue(scrolledTo("5인 이상인지 아직 안 골랐어요"))
    }

    @Test fun theShopCardLineDarkBig() = shoot("rvj_277_tone_action_shop_folded_dark_200", DARK, 2f, shopCard())

    // ── 사장님 › 급여 대장: 명단 가져오기 위 '읽는 칸' ──

    @Test fun theReadColumnsLine() = shoot("rvj_278_tone_action_reads_folded_dark_200", DARK, 2f) {
        shopLedger().invoke(this)
        assertTrue(scrolledTo("예시 파일 받기"))
        reveal("읽는 칸", exact = false)
    }

    // ── 일근(월급) › 급여: 마감을 '나중에' 한 뒤의 줄 ──

    private fun closeLater(): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = { it.copy(period = PayPeriod(payDay = 10, payNextMonth = true)) }).invoke(this)
        if (scrolledTo("이대로 마감할까요?")) reveal("이대로 마감할까요?")
        tapText("나중에", exact = true)
        repeat(2) { settle() }
        reveal("9월 마감은 도구", exact = false)
    }

    @Test fun theCloseLaterLine() = shoot("rvj_279_tone_action_close_later_folded", FOLDED) {
        closeLater().invoke(this)
        assertTrue(shows("9월 마감은 도구 › 9월 마감에서 할 수 있어요", exact = false))
    }

    @Test fun theCloseLaterLineDarkBig() = shoot("rvj_280_tone_action_close_later_folded_dark_200", DARK, 2f, closeLater())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
