package com.bamnat.core.pay

import com.bamnat.core.Fixtures
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.37 [LEDH-04] '이날 일하면?'. */
class DayWhatIfTest {
    private val start = LocalDate.of(2026, 1, 5) // a Monday
    private val weekdays = WorkPattern.Weekly.fromSteps(start, listOf(1L, 1L, 1L, 1L, 1L, null, null))
    private val sat = LocalDate.of(2026, 11, 7)
    private val wed = LocalDate.of(2026, 11, 4)
    private fun resolver(entries: List<DayEntry> = emptyList()) = ScheduleResolver(Fixtures.all, weekdays, entries)
    private fun hourly() = PayProfile(Occupation.entries.first(), WageType.HOURLY, ValueTimeline(listOf(DatedValue(start, 12_000))))

    @Test fun aCoverOnADayOffAddsPayAndLeaveOrOffAreOfferedOnAWorkDay() {
        val r = resolver()
        val offers = DayWhatIf.changes(r.resolve(sat), Fixtures.all)
        assertTrue(offers.first() is DayWhatIf.Change.Cover)
        assertEquals(listOf(DayWhatIf.Change.Leave, DayWhatIf.Change.Off), DayWhatIf.changes(r.resolve(wed), Fixtures.all))
        val cover = DayWhatIf.Change.Cover(Fixtures.day)
        val after = resolver(listOf(DayWhatIf.entry(null, sat, cover)))
        val a = DayWhatIf.answer(hourly(), sat, cover, r::resolve, after::resolve)
        assertTrue("a day of 주간 at 12,000원 adds pay: ${a.gross}", a.gross >= 8 * 12_000)
        assertEquals(null, a.net)
        val taxed = hourly().copy(taxMode = TaxMode.INSURANCE_4, taxChosen = true)
        val n = DayWhatIf.answer(taxed, sat, cover, r::resolve, after::resolve)
        assertNotNull(n.net)
        assertTrue(n.net!! in 1 until n.gross)
        // An hourly worker who takes a day off earns less.
        val off = resolver(listOf(DayWhatIf.entry(null, wed, DayWhatIf.Change.Off)))
        assertTrue(DayWhatIf.answer(hourly(), wed, DayWhatIf.Change.Off, r::resolve, off::resolve).gross < 0)
        assertEquals(12, DayWhatIf.envelopeShare(60_000, 500_000))
    }

    /** 2.0.0 [RVK-43] (검토 T06a-09) A working day's chips are 연차·쉬는 날, so the question is '이날 쉬면?'. */
    @Test fun theQuestionFollowsTheDay() {
        val r = resolver()
        assertEquals("이날 일하면?", DayWhatIf.question(DayWhatIf.changes(r.resolve(sat), Fixtures.all)))
        assertEquals("이날 쉬면?", DayWhatIf.question(DayWhatIf.changes(r.resolve(wed), Fixtures.all)))
    }
}
