package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
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
 * 2.0.0 갈래 J, 통합 2차 PC 확인(J-444 ★4)에서 나온 급여 대장 › 다가올 신고·지급: [RVJ-38] PC-100 입사일 없는 직원이 금액에서 말없이
 * 빠지던 것, [RVJ-39] PC-101 '○월 지급분'이 어느 달에 일한 몫인지와 모두 보기의 길이. 가게 하나에 3.3% 직원(입사일 없음), 일용 직원,
 * 월급 직원, 급여 주는 날은 아직 안 고름. The shots hold no assertion so the same test draws the 'before'; [theCardSaysWhoIsLeftOut] checks.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjFilingsScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    private fun seedShop() = runBlocking {
        val store = app.container.phaseThree
        store.saveWorkplace(Workplace("p1", "식당", fivePlus = false))
        store.saveStaff(StaffMember("s1", "삼점삼", base = 10_320L, weekly = DayOfWeek.entries.take(5).associateWith { nine },
            insurance = Insurance(tax = StaffTax.BIZ)), "p1", 0)
        store.saveStaff(StaffMember("s2", "일용", form = WageForm.DAY, base = 200_000L, hiredOn = LocalDate.of(2026, 8, 3),
            weekly = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).associateWith { nine }, insurance = Insurance(tax = StaffTax.DAILY)), "p1", 1)
        store.saveStaff(StaffMember("s3", "월급", form = WageForm.MONTH, base = 2_500_000L, hiredOn = LocalDate.of(2026, 1, 5),
            weekly = DayOfWeek.entries.take(5).associateWith { nine }, insurance = Insurance.ALL), "p1", 2)
    }

    private fun card(all: Boolean = false): Screen.() -> Unit = {
        seedShop()
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), null) {} }
        repeat(2) { settle() }
        // The card reads its months off the main thread; on the class's first, cold test that can take longer than two settles.
        var found = false
        repeat(4) { if (!found) { found = scrolledTo("다가올 신고·지급"); if (!found) settle() } }
        reveal("다가올 신고·지급", exact = false)
        if (all) {
            tapText("모두 보기", exact = false)
            settle()
            reveal("접기", exact = true)
        }
    }

    @Test fun theCard() = shoot("rvj_93_filings_folded", FOLDED, card())
    @Test fun theCardDarkBig() = shoot("rvj_94_filings_folded_dark_200", DARK, 2f, card())
    @Test fun theCardWide() = shoot("rvj_95_filings_unfolded", UNFOLDED, card())
    @Test fun allOfIt() = shoot("rvj_96_filings_all_folded", FOLDED, card(all = true))

    /** [RVJ-38] Right by the amount: who is left out and what it comes to with them; [RVJ-39] the pay day line and the month worked. */
    @Test fun theCardSaysWhoIsLeftOut() = shoot("rvj_97_filings_missing_folded", FOLDED) {
        card().invoke(this)
        assertTrue(appears("급여 주는 날을 안 골라 일한 달의 다음 달 1일에 준 것으로 셌어요"))
        assertTrue(scrolledTo("입사일 없는 삼점삼님 빼고예요"))
        assertTrue(scrolledTo("9월 지급분 · 8월 일한 몫"))
        assertTrue("아래 한 줄은 줄마다 말하니 없어요", !shows("님은 지난달 신고에서 뺐어요"))
        tapText("입사일 없는 삼점삼님 빼고예요", exact = false)
        settle()
        assertTrue("누르면 그 직원 정보", appears("하는 일 (예: 홀, 주방)"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
