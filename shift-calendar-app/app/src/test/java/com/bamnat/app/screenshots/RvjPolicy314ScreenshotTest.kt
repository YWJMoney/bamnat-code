package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.SharedSetup
import com.bamnat.app.company.WayPicker
import com.bamnat.app.ui.company.PickResult
import com.bamnat.app.ui.pay.HourlyPayCard
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.core.pay.HolidayPayMode
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayPresets
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.WayPick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 갈래 J, 처리방침 3.14의 두 줄 (사용자 11-19 '16줄 모두'). [RVJ-81] 결정 5 (13번): 급여 설정의 생년(선택, 휴대폰 안)으로 만 60세 국민연금과
 * 65세 이후 새로 일한 곳의 고용보험이 저절로 빠지고, 줄마다 '그래도 내요'. [RVJ-82] 결정 3 (12번): '지난달 명세서로 맞추기'의 판 카드에 '명세서로
 * 맞춰 본 분 n명'. Both wait for the 3.14 day by the server's clock. Light·dark × 글자 100%·200% × 접은·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjPolicy314ScreenshotTest : ScreenshotBase() {
    private val month = YearMonth.from(today)

    /** A 시급 파트타임 born in 1958, first worked here in 2025 (at 67): 국민연금 and 고용보험 both come off by the age. */
    private fun age(on: Boolean): Screen.() -> Unit = {
        if (on) policy314()
        twoOnTwoOff()
        val preset = PayPresets.of(Occupation.PART_TIME)
        val p = PayPresets.profile(preset, preset.wageType, preset.baseHint, LocalDate.of(2025, 3, 1), preset.items.filter { it.checked },
            fiveOrMore = true, holidayMode = HolidayPayMode.FULL, estimated = emptySet())
            .copy(taxMode = TaxMode.INSURANCE_4, taxChosen = true, birthYear = 1958)
        val s = runBlocking { app.container.scheduleRepository.snapshot.first() }
        val pay = PayCalculator().calculate(p, month, s.resolver::resolve, checks = false)
        val vm = PayViewModel(app.container)
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                HourlyPayCard(vm, p, pay.total, unlocked = true, snapshot = s, month = month, holidayWeeks = pay.holidayWeeks)
            }
        }
        settle()
        if (on) reveal("생년 (선택)", exact = true)
    }

    private fun ageOn(): Screen.() -> Unit = {
        age(on = true).invoke(this)
        assertTrue(shows("만 60세가 지나 국민연금을 떼지 않아요", exact = false))
        assertTrue(shows("65세 이후 새로 일한 곳이라 고용보험을 떼지 않아요", exact = false))
        assertTrue(shows("그래도 내요", exact = true))
    }

    @Test fun birthYearLight() = shoot("rvj_303_pay_birth_year_folded", FOLDED, ageOn())
    @Test fun birthYearDarkBig() = shoot("rvj_304_pay_birth_year_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, ageOn())
    @Test fun birthYearUnfolded() = shoot("rvj_305_pay_birth_year_unfolded", UNFOLDED, ageOn())

    /** Before 3.14 the row is as it was: no 생년 field, nothing taken off by the age. */
    @Test fun birthYearBefore314() = shoot("rvj_306_pay_birth_year_before_314_folded", FOLDED) {
        age(on = false).invoke(this)
        assertFalse(shows("생년 (선택)", exact = true))
        assertFalse(shows("그래도 내요", exact = true))
    }

    // ---- [RVJ-82] 판 카드의 명세서 수 ----
    /** Three versions of 생산팀 tried against last month's payslip; the server's payslip checks beside 맞아요. */
    private fun slips(on: Boolean = true): Screen.() -> Unit = {
        if (on) policy314()
        twoOnTwoOff()
        val preset = PayPresets.of(Occupation.MANUFACTURING)
        val p = PayPresets.profile(preset, preset.wageType, 2_400_000, LocalDate.of(2023, 3, 1), preset.items.filter { it.checked },
            fiveOrMore = true, holidayMode = HolidayPayMode.FULL, estimated = emptySet())
        val last = month.minusMonths(1)
        val s = runBlocking { app.container.scheduleRepository.snapshot.first() }
        val pay = PayCalculator().calculate(p, last, s.resolver::resolve, checks = false)
        val slip = WayPick.Slip(last, pay.total + 12_000, 2_400_000)
        val setups = listOf(
            SharedSetup("s2", true, 9, 8, 0, 3, "생산팀", 2, "{}", emptyList(), checked = 5),
            SharedSetup("s1", false, 3, 2, 1, 3, "생산팀", 1, "{}", emptyList(), checked = 0),
            SharedSetup("s3", false, 4, 3, 0, 3, "생산2팀", null, "{}", emptyList(), checked = 1),
        )
        val slipsOn = app.container.flags.on(com.bamnat.core.flags.FlagCatalog.SLIP_COUNT)
        val options = setups.mapIndexed { i, st ->
            val tried = WayPick.tried(st.id, PayCalculator().calculate(p, last, s.resolver::resolve, checks = false), slip.copy(gross = slip.gross + i * 35_000L))
            WayPicker.Option(PendingWay("c1", "(주)예시정밀", st.dept, st.id, "{}", PendingWay.Source.FIND), p, tried, WayPicker.label(st, setups, slips = slipsOn))
        }
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickResult(WayPicker.Result.Picked(options, 0), slip, chosen = "s2", onChoose = {}, onUse = {})
            }
        }
        settle()
        assertEquals(on, shows("명세서로 맞춰 본 분 5명", exact = false))
    }

    @Test fun slipCountLight() = shoot("rvj_307_way_pick_slip_count_folded", FOLDED, slips())
    @Test fun slipCountDarkBig() = shoot("rvj_308_way_pick_slip_count_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, slips())
    @Test fun slipCountUnfolded() = shoot("rvj_309_way_pick_slip_count_unfolded", UNFOLDED, slips())

    /** Before 3.14 the card says 맞아요 only, as it did. */
    @Test fun slipCountBefore314() = shoot("rvj_310_way_pick_slip_count_before_314_folded", FOLDED, slips(on = false))
}
