package com.bamnat.core.money

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Test
import org.junit.Assert.assertEquals

class JobHoursTest {
    private fun job(d: String, s: String?, e: String?, end: String? = null, status: JobStatus = JobStatus.CONFIRMED) =
        Job(date = LocalDate.parse(d), endDate = end?.let(LocalDate::parse), title = "일",
            start = s?.let(LocalTime::parse), end = e?.let(LocalTime::parse), status = status)

    @Test fun todaysJobCountsItsHours() {
        val weeks = JobHours.weeks(listOf(job("2026-11-05", "08:00", "16:00")), YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(8 * 60, weeks.first { !LocalDate.parse("2026-11-05").isBefore(it.from) && !LocalDate.parse("2026-11-05").isAfter(it.to) }.minutes)
        assertEquals(8 * 60, weeks.sumOf { it.minutes })
    }

    @Test fun overnightSeveralDaysAndCancelled() {
        val jobs = listOf(
            job("2026-11-10", "22:00", "06:00"),
            job("2026-11-11", "09:00", "12:00", end = "2026-11-13"),
            job("2026-11-12", "09:00", "18:00", status = JobStatus.CANCELLED),
            job("2026-11-14", null, null),
        )
        assertEquals(8 * 60 + 3 * 3 * 60, JobHours.weeks(jobs, YearMonth.of(2026, 11), DayOfWeek.MONDAY).sumOf { it.minutes })
    }

    /** 2.0.0 [RVK-43] (검토 T06b-06) The same start and end (a time picked twice) is no length, not a 24-hour day. */
    @Test fun sameStartAndEndIsNoLength() {
        assertEquals(0, JobHours.minutes(job("2026-11-05", "09:00", "09:00")))
        assertEquals(0, JobHours.weeks(listOf(job("2026-11-05", "09:00", "09:00")), YearMonth.of(2026, 11), DayOfWeek.MONDAY).sumOf { it.minutes })
        assertEquals(8 * 60, JobHours.minutes(job("2026-11-05", "22:00", "06:00")))
    }
}
