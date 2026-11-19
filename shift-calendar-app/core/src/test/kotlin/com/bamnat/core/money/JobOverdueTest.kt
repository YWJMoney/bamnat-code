package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.0.0 [RVH-82] 총괄 (K 10차와 같은 빨강, 조건 6): the calendar's 받을 돈 is 주의 only once its promised day has passed. Before it, or
 * on a 가계약, or once paid, it is news; the late total of the money screen counts the same jobs.
 */
class JobOverdueTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test fun moneyIsLateOnlyAfterItsPromisedDay() {
        val done = Job(1, today.minusDays(3), title = "도배", amount = 300_000)
        assertTrue("마지막 날이 지났고 못 받음", done.overdue(today))
        assertFalse("오늘이 마지막 날", done.copy(date = today).overdue(today))
        assertFalse("약속일이 아직", done.copy(due = today.plusDays(7)).overdue(today))
        assertTrue("약속일이 어제", done.copy(due = today.minusDays(1)).overdue(today))
        assertFalse("다 받음", done.copy(depositPaid = true, deposit = 100_000, balancePaid = true).overdue(today))
        assertFalse("가계약", done.copy(status = JobStatus.TENTATIVE).overdue(today))
        assertFalse("취소", done.copy(status = JobStatus.CANCELLED).overdue(today))
        assertFalse("여러 날 일감의 마지막 날 전", done.copy(endDate = today.plusDays(1)).overdue(today))
    }

    @Test fun theLateTotalCountsTheSameJobs() {
        val jobs = listOf(
            Job(1, today.minusDays(3), title = "a", amount = 300_000),
            Job(2, today.minusDays(3), title = "b", amount = 200_000, due = today.plusDays(3)),
        )
        assertEquals(300_000L, MoneyAhead.late(MoneyBook(jobs = jobs), today))
    }
}
