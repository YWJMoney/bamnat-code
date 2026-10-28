package com.bamnat.core.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** [GUIDE-20] 1.31 P2: the first week's one card, its rows and when it goes. */
class StartStepsTest {
    private val day0 = LocalDate.of(2026, 11, 1)

    @Test fun theFirstWeekShowsWhatIsLeftAndThenGoes() {
        val rows = StartSteps.rows(day0, day0.plusDays(2), false, alarmsOn = true, hasShifts = false, companyLinked = false, companyApplies = true)!!
        assertEquals(listOf(StartSteps.Step.ALARM, StartSteps.Step.SHIFTS, StartSteps.Step.COMPANY), rows.map { it.step })
        assertEquals("시작 3단계 중 1개 했어요", StartSteps.title(rows))
        assertNull("after 7 days", StartSteps.rows(day0, day0.plusDays(7), false, false, false, false, true))
        assertNull("said 그만 보기", StartSteps.rows(day0, day0, true, false, false, false, true))
        assertNull("all done", StartSteps.rows(day0, day0, false, true, true, true, true))
        assertNull("install day unknown", StartSteps.rows(null, day0, false, false, false, false, true))
    }

    @Test fun workWithoutCompaniesHasTwoSteps() {
        val rows = StartSteps.rows(day0, day0, false, alarmsOn = false, hasShifts = true, companyLinked = false, companyApplies = false)!!
        assertEquals("시작 2단계 중 1개 했어요", StartSteps.title(rows))
        assertNull(StartSteps.rows(day0, day0, false, alarmsOn = true, hasShifts = true, companyLinked = false, companyApplies = false))
    }
}
