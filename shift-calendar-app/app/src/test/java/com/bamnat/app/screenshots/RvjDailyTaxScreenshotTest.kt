package com.bamnat.app.screenshots

import androidx.compose.runtime.collectAsState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.PayQuickSetup
import com.bamnat.core.money.Job
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 2.0.0 갈래 J 열두 번째. [RVJ-80] 검토 T04-05·T05b-11 (현장 일당): 급여 빠른 설정 마지막 쪽의 세금 칩에 '일용근로'(일당이면 맨 앞에
 * 골라져 시작)와 한 줄 안내, 통계 › 거래처별의 '일용직으로 받으면 …' 줄. 밝은 100%·어두운 200% 접은 폭과 편 폭으로 찍어요. The shots
 * hold no assertion so the same test draws the 'before'; the tests that name what they look for check.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjDailyTaxScreenshotTest : ScreenshotBase() {

    private fun wage(type: WageType, amount: Long) = PayProfile(
        occupation = Occupation.CUSTOM, wageType = type,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), amount))),
    )

    // ── 급여 › 빠른 설정 마지막 쪽: 일당 20만 원, 세금 방식을 아직 고르지 않음 ──

    private fun quickTax(profile: PayProfile): Screen.() -> Unit = {
        payScreen(Tier.PRO, withProfile = true, adjust = { profile }) { m ->
            val p = m.profile.collectAsState().value
            val snap = m.snapshot.collectAsState().value
            if (p != null && snap != null) PayQuickSetup(
                initial = p, snapshot = snap,
                preview = { m.preview(it, snap, Today.month()) },
                slipRules = { base, read, rules -> m.slipRules(base, snap, read, rules) },
                onDone = { _, _ -> }, onDetailed = {}, onCancel = {}, startAt = "confirm",
            )
        }.invoke(this)
        repeat(2) { settle() }
        reveal("급여에서 떼는 돈", exact = false)
    }

    private val daily get() = quickTax(wage(WageType.DAILY, 200_000))

    @Test fun theDailyWayInTheEasySetup() = shoot("rvj_290_daily_quick_tax_folded", FOLDED, daily)
    @Test fun theDailyWayDarkBig() = shoot("rvj_291_daily_quick_tax_folded_dark_200", DARK, 2f, daily)
    @Test fun theDailyWayUnfolded() = shoot("rvj_292_daily_quick_tax_unfolded", UNFOLDED, daily)

    @Test fun aDailyStartsOnTheDailyWay() = shoot("rvj_293_daily_quick_tax_said_folded", FOLDED) {
        daily.invoke(this)
        assertTrue("일당은 일용근로 칩", shows("일용근로", exact = true))
        // One line + ⓘ (조건 4); the 15만 원 rule is behind ⓘ.
        assertTrue(scrolledTo("일당은 대부분 ‘일용근로’로 떼요", exact = true))
        assertFalse(shows("하루 15만 원까지는 소득세가 없고 고용보험만 떼요", exact = false))
        assertTrue(scrolledTo("일용근로 소득세·고용보험 참고값", exact = false))
    }

    @Test fun anHourlyHasNoDailyWay() = shoot("rvj_294_hourly_quick_tax_folded", FOLDED) {
        quickTax(wage(WageType.HOURLY, 10_320)).invoke(this)
        assertTrue(shows("떼지 않음", exact = true))
        assertFalse("알바 칩에는 일용근로가 없어요", shows("일용근로", exact = true))
    }

    // ── 현장 › 통계 › 거래처별: 공수로 적은 일당 4일 ──

    private fun clients(wide: Boolean = false): Screen.() -> Unit = style(WorkStyle.FIELD) {
        runBlocking {
            val money = app.container.moneyRepository
            (6..9).forEach { d ->
                money.save(Job(date = LocalDate.of(2026, 10, d), title = "골조 형틀", client = "OO건설", amount = 200_000, units = 1.0, balancePaid = true))
            }
            money.save(Job(date = LocalDate.of(2026, 10, 2), title = "욕실 타일", client = "김OO 고객", amount = 600_000, balancePaid = true))
        }
        settle()
        content { com.bamnat.app.ui.stats.StatsScreen(com.bamnat.app.ui.stats.StatsViewModel(app.container), wide = wide) }
        repeat(2) { settle() }
        reveal("3.3% 떼고 받으면", exact = false)
    }

    @Test fun theClientsSayTheDailyWay() = shoot("rvj_295_field_clients_daily_folded", FOLDED, clients())
    @Test fun theClientsDarkBig() = shoot("rvj_296_field_clients_daily_folded_dark_200", DARK, 2f, clients())
    @Test fun theClientsUnfolded() = shoot("rvj_297_field_clients_daily_unfolded", UNFOLDED, clients(wide = true))

    @Test fun theDailyLineNamesTheDays() = shoot("rvj_298_field_clients_daily_said_folded", FOLDED) {
        clients().invoke(this)
        // 20만 원 × 4일, 한 번에: 소득세 5,400원 + 지방소득세 540원 → 794,060원.
        assertTrue(scrolledTo("일용직으로 받으면 794,060원 (일당 4일, 한 번에 받을 때) · 따로 신고하지 않아요", exact = false))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
