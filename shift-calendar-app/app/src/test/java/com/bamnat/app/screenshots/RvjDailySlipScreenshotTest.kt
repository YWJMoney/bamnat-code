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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J [RVJ-72] (총괄 PC-177): 사장님 일용 직원(일급 20만 원, 월·수·금)의 임금명세서. 세금 줄이 간이세액표의 '근로소득세'가 아니라
 * '일용근로 소득세'이고, 그 아래 한 줄('일한 날마다 15만 원을 빼고 셈해요.')과 ⓘ에 셈법(소득세법 제47조 제2항·제129조 제1항 제4호·제59조 제3항·제86조,
 * 주휴는 그 주의 날에: 국세청 서면-2022-소득관리-4916)이 있어요. Light·dark × 글자 100%·200%.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjDailySlipScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))

    private fun slip(): Screen.() -> Unit = {
        runBlocking {
            val store = app.container.phaseThree
            store.saveWorkplace(Workplace("p1", "식당", fivePlus = false, payday = 10))
            store.saveStaff(StaffMember("s2", "이일용", form = WageForm.DAY, base = 200_000L, hiredOn = LocalDate.of(2026, 8, 3),
                weekly = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).associateWith { nine }, insurance = Insurance(tax = StaffTax.DAILY)), "p1", 0)
        }
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), YearMonth.of(2026, 11)) {} }
        repeat(2) { settle() }
        tapText("명세서", exact = true)
        settle()
        assertTrue(appears("일용근로 소득세", exact = true))
        assertFalse("간이세액표 이름이 아니에요", shows("근로소득세 (간이세액표)", exact = true))
        assertTrue(shows("일한 날마다 15만 원을 빼고 셈해요.", exact = false))
    }

    @Test fun slipLight() = shoot("rvj_216_daily_slip_folded", FOLDED, slip())
    @Test fun slipDarkBig() = shoot("rvj_217_daily_slip_folded_dark_200", RvjTeamCheckScreenshotTest.DARK, 2f, slip())
}
