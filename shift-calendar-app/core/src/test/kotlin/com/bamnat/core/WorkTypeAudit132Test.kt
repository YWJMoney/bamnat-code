package com.bamnat.core

import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatement
import com.bamnat.core.report.ClientStatementDoc
import com.bamnat.core.report.FieldDaysProof
import com.bamnat.core.report.YearShiftReport
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.Callout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 1.32 조사 대조 (계획/1.32-조사-대조표.md 2장): the work-type items that were missing. Amounts are made up. */
class WorkTypeAudit132Test {
    private val weekdays = WorkPattern.Weekly.fromSteps(LocalDate.of(2026, 1, 1), listOf(1L, 1L, 1L, 1L, 1L, null, null))

    @Test fun aCalloutIsExtraWorkOfItsOwnKind() {
        val kinds = ExtraKinds()
        val k = Callout.kind(kinds)
        assertEquals(Callout.NAME, k.name)
        assertTrue(k.id >= com.bamnat.core.model.ExtraKind.FIRST_CUSTOM_ID)
        // The second time, the same kind.
        val kept = kinds.put(k)
        assertEquals(k.id, Callout.kind(kept).id)
        val e = Callout.entry(k, LocalTime.of(22, 0), LocalTime.of(1, 0))
        assertEquals(180, e.paidMinutes)
        val day = com.bamnat.core.model.DayEntry(LocalDate.of(2026, 10, 10), extras = listOf(e))
        assertEquals(1 to 180, Callout.month(listOf(day), kept, YearMonth.of(2026, 10)))
        assertTrue(Callout.offered(com.bamnat.core.work.WorkStyle.SHIFT) && !Callout.offered(com.bamnat.core.work.WorkStyle.FIELD))
    }

    @Test fun aYearOfShiftsFitsOnePage() {
        val resolver = ScheduleResolver(Fixtures.all, weekdays, emptyList())
        val months = YearShiftReport.months(2026, LocalDate.of(2026, 3, 15), resolver::resolve) { 2_000_000L }
        assertEquals(3, months.size)
        assertTrue(months[1].workDays in 19..20)
        // 3/1 삼일절 is a Sunday; 3/2 대체공휴일 a weekday the pattern works.
        assertEquals(1, months[2].holidayDays)
        val doc = YearShiftReport.doc(2026, "", months, LocalDate.of(2026, 3, 15)).text()
        assertTrue(doc.contains("6,000,000원") && doc.contains("2026년 교대 근무 리포트"))
    }

    @Test fun statementsAndDayProofsAreOnPaper() {
        val jobs = listOf(
            Job(id = 1, date = LocalDate.of(2026, 10, 5), title = "철근", client = "대한건설", place = "판교", amount = 200_000, units = 1.0),
            Job(id = 2, date = LocalDate.of(2026, 10, 6), endDate = LocalDate.of(2026, 10, 7), title = "철근", client = "대한건설", place = "판교",
                amount = 300_000, units = 1.5, withholding = true, depositPaid = true, deposit = 100_000),
            Job(id = 3, date = LocalDate.of(2026, 10, 6), title = "야간", client = "다른", amount = 100_000),
        )
        val s = JobStatement.of("대한건설", YearMonth.of(2026, 10), jobs)
        val text = ClientStatementDoc.doc(s, "홍반장", LocalDate.of(2026, 10, 31)).text()
        assertTrue(text, text.contains("대한건설") && text.contains("2건") && text.contains("남은 돈"))
        val days = FieldDaysProof.days(jobs, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        // 10/6 had two jobs: one day.
        assertEquals(3, days.size)
        assertEquals(3, FieldDaysProof.lastMonthDays(days, LocalDate.of(2026, 10, 31)))
        val proof = FieldDaysProof.doc("", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), days, LocalDate.of(2026, 10, 31)).text()
        assertTrue(proof.contains("판교") && proof.contains("3일"))
    }
}
