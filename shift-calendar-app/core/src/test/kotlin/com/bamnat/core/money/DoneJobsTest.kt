package com.bamnat.core.money

import com.bamnat.core.work.WorkStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 1.37 PC 6차: 번 돈·받을 돈 count only jobs done or past, the same everywhere. */
class DoneJobsTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val nov = YearMonth.of(2026, 11)
    private val jobs = listOf(
        Job(1, LocalDate.of(2026, 11, 2), title = "a", client = "가", amount = 300_000, status = JobStatus.DONE),
        Job(2, LocalDate.of(2026, 11, 3), title = "b", client = "가", amount = 200_000),
        Job(3, LocalDate.of(2026, 11, 10), title = "c", client = "나", amount = 400_000, deposit = 100_000, depositPaid = true),
        Job(4, LocalDate.of(2026, 11, 12), title = "d", client = "나", amount = 100_000, status = JobStatus.TENTATIVE),
    )

    @Test fun onlyDoneJobsAreEarnedAndOwed() {
        val m = MoneyCalculator.month(nov, emptyList(), jobs, emptyList(), today = today)
        assertEquals(500_000, m.jobEarned)
        assertEquals(500_000, m.jobOwed)
        assertEquals(500_000, m.jobAhead)
        assertEquals(100_000, m.jobReceived) // the 선금 that came in still counts as money in
        assertEquals(4, m.jobCount)
        assertEquals(listOf(1L, 2L), MoneyCalculator.unpaid(jobs, today).map { it.id })
        assertEquals(mapOf("가" to 500_000L, "나" to 0L), WorkStats.clients(nov, jobs, today).associate { it.client to it.earned })
        // Without a day, as before: every job of the month.
        assertEquals(1_000_000, MoneyCalculator.month(nov, emptyList(), jobs, emptyList()).jobEarned)
    }
}
