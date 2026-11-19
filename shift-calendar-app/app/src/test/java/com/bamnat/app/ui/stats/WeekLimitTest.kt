package com.bamnat.app.ui.stats

import com.bamnat.core.hours.HourLimits
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVJ-23] 검토 PC-51·U04-07·T05b-07·U05b-11: '이번 주 근무'와 올해 막대의 한 주 한도. 직장인은 급여 설정 전에도 법 한도
 * 52시간이고, 정한 적 없는 경고 기본값 64시간을 '내가 정한' 값으로 보여 주지 않아요.
 */
class WeekLimitTest {
    private val default = HourLimits().weeklyLimitHours
    private val pay = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 3_000_000L))))

    @Test fun anEmployeeWithNoPaySetupSeesTheLegalWeek() {
        assertEquals(52, weekLimitFor(employee = true, profile = null, own = default))
        assertTrue(weekLimitNote(52, legal = 52).startsWith("근로기준법 한 주 한도 52시간"))
    }

    @Test fun aWarningTheUserSetIsKeptUntilThePaySetupSaysTheLaw() {
        assertEquals("직접 바꾼 경고값", 60, weekLimitFor(employee = true, profile = null, own = 60))
        assertEquals("급여 설정이 있으면 법 한도", 52, weekLimitFor(employee = true, profile = pay, own = 60))
        assertEquals("5인 미만은 법 한도가 없어 내 경고값", 60, weekLimitFor(employee = true, profile = pay.copy(fiveOrMoreEmployees = false), own = 60))
    }

    @Test fun workWithNoEmployerKeepsItsOwnWarning() {
        assertEquals(default, weekLimitFor(employee = false, profile = null, own = default))
        assertEquals(48, weekLimitFor(employee = false, profile = pay, own = 48))
    }

    @Test fun theDefaultWarningIsNotCalledMine() {
        assertTrue(weekLimitNote(default, legal = null).contains("(기본값)"))
        assertTrue(weekLimitNote(60, legal = null).startsWith("내가 정한"))
    }
}
