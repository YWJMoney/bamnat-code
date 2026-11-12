package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 K 9차 [RVK-44]: 총괄이 PC 3차·사진 검토에서 넘긴 줄. 플랫폼 수입 '쓴 돈'의 뜻을 아래 한 줄로, 큰 글자의 '입금 확인' 줄 (HQ-75),
 * 기능 찾기 › 일용직 세금 › 바로 가기가 그 카드로, 그릴 것이 없을 때 한 줄 (PC-134), 공수만 적은 현장의 세금 카드 (PC-136). Light·dark ×
 * 글자 100%·200% × 접은·편 폭. The code before draws the same scenes (the PR's 'before'). Today is 2026-10-14. Amounts and names are made up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkNineScreenshotTest : ScreenshotBase() {
    /** [RVK-44] (HQ-75) 배달 (쿠팡이츠) ten days this month, a 유류비 by hand; the week of 10/9 not paid in yet. */
    private fun platform(wide: Boolean = false, at: String = "남은 돈"): Screen.() -> Unit = style(WorkStyle.FIELD) {
        app.container.calendars.updateField(PersonalCalendar.MAIN) { it.copy(mode = com.bamnat.core.money.FieldMode.PLATFORM) }
        runBlocking {
            val money = app.container.moneyRepository
            money.allJobs().forEach { money.delete(it) }
            (1..10).forEach { k ->
                money.save(PlatformWork.job(today.minusDays(k.toLong()), "쿠팡이츠", 18 + k % 5, 96_000L + k * 3_000, 300 + k * 10, 0))
            }
            money.save(MoneyEntry(date = today.minusDays(3), kind = MoneyKind.EXPENSE, amount = 62_000, category = "유류비", merchant = "SK에너지 역삼"))
        }
        settle()
        tab(1, wide)
        reveal("플랫폼 수입", exact = false)
        reveal(at, exact = false)
    }

    /** [PC-134] A 건별 현장 calendar (no 공수 ever): 기능 찾기 › 일용직 세금 › 바로 가기. */
    private fun dailyTax(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        runBlocking {
            val money = app.container.moneyRepository
            money.allJobs().forEach { money.delete(it) }
            money.save(Job(date = today.minusDays(4), title = "욕실 리모델링", client = "이OO 고객", amount = 2_400_000))
        }
        settle()
        FeatureLinks.open(FeatureRoute(Place.STATS, "dailyTax"))
        settle()
    }

    /** [PC-136] A 현장 phone of 공수 days alone (형틀 18만 원, 3.3% 없음): 장부 › 통계 › 세금. */
    private fun dayLabour(): Screen.() -> Unit = style(WorkStyle.FIELD) {
        runBlocking {
            val money = app.container.moneyRepository
            money.allJobs().forEach { money.delete(it) }
            // The seed's two 하루 마감 days off: this phone keeps 일당 only.
            listOf(today, today.minusDays(1)).forEach { money.save(com.bamnat.core.money.DailySales(it, sales = 0, costs = 0)) }
            (1..12).forEach { k -> money.save(Job(date = today.minusDays(k.toLong() * 2), title = "형틀", client = "OO건설", amount = 180_000, units = 1.0)) }
        }
        settle()
        ledgerPage(null)()
        settle()
        reveal("세금·실수령", exact = true)
        reveal("일용근로소득", exact = false)
    }

    @Test fun platformLight() = shoot("rvk9_01_platform_folded", FOLDED, platform {
        assertTrue("쓴 돈의 뜻은 아래 한 줄", appears("유류비·정비·보험", exact = true))
    })
    @Test fun platformDarkBig() = shoot("rvk9_02_platform_folded_dark_200", DARK, 2f, platform(at = "일한 몫") {
        assertTrue("큰 글자에서도 입금 확인", appears("입금 확인", exact = true))
    })
    @Test fun platformCostsDarkBig() = shoot("rvk9_08_platform_costs_folded_dark_200", DARK, 2f, platform {
        assertTrue("200%에서 쓴 돈의 뜻이 따로 한 줄", appears("유류비·정비·보험", exact = true))
    })
    @Test fun platformWide() = shoot("rvk9_03_platform_unfolded", UNFOLDED, platform(wide = true))
    @Test fun dailyTaxLight() = shoot("rvk9_04_daily_tax_none_folded", FOLDED, dailyTax {
        assertTrue("바로 가기가 일용직 세금 카드로", appears("일용직 세금", exact = false))
        assertTrue("그릴 것이 없을 때 한 줄", appears("일당(공수)으로 적은 날이 있으면", exact = false))
    })
    @Test fun dailyTaxDarkBig() = shoot("rvk9_05_daily_tax_none_folded_dark_200", DARK, 2f, dailyTax())
    @Test fun dayLabourLight() = shoot("rvk9_06_day_labour_tax_folded", FOLDED, dayLabour {
        assertTrue("일용근로는 사업자 세금이 아님", appears("일용근로소득이라 부가가치세가 없고", exact = false))
        assertFalse("부가세 금액이 나오지 않음", shows("부가가치세 ", exact = false) && shows("낼 세금 예상", exact = false))
    })
    @Test fun dayLabourDarkBig() = shoot("rvk9_07_day_labour_tax_folded_dark_200", DARK, 2f, dayLabour())

    private fun platform(wide: Boolean = false, at: String = "남은 돈", then: Screen.() -> Unit): Screen.() -> Unit = { platform(wide, at)(); then() }
    private fun dailyTax(then: Screen.() -> Unit): Screen.() -> Unit = { dailyTax()(); then() }
    private fun dayLabour(then: Screen.() -> Unit): Screen.() -> Unit = { dayLabour()(); then() }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
