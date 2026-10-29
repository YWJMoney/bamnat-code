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

    /** 1.32 [WTYP-12] 당직(야간) 뒤 바로 이어진 장주간과 잔업은 한 번에 센 연속 근무예요. */
    @Test fun aNightRunningIntoTheDayIsOneStretch() {
        val pattern = WorkPattern.Weekly.fromSteps(LocalDate.of(2026, 10, 5), listOf(3L, 5L, null, null, null, null, null))
        val extra = com.bamnat.core.model.ExtraEntry(com.bamnat.core.model.ExtraKind.OVERTIME_ID, LocalTime.of(19, 0), LocalTime.of(23, 0))
        val entries = listOf(com.bamnat.core.model.DayEntry(LocalDate.of(2026, 10, 6), extras = listOf(extra)))
        val resolver = ScheduleResolver(Fixtures.all, pattern, entries)
        val s = com.bamnat.core.hours.Stretches.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), resolver::resolve)
        assertEquals(1, s.size)
        // 10/5 22:00 → 10/6 23:00: 25시간.
        assertEquals(25 * 60L, s[0].minutes)
        assertEquals(1, com.bamnat.core.hours.Stretches.over(s, 24).size)
        assertTrue(com.bamnat.core.hours.Stretches.over(s, 0).isEmpty() && com.bamnat.core.hours.Stretches.over(s, 28).isEmpty())
        assertEquals("10/5 22:00부터 25시간 연속 · 24시간 넘음", com.bamnat.core.hours.Stretches.line(s[0], 24))
        assertTrue(com.bamnat.core.hours.Stretches.Preset.RESIDENT.matches(80, 72, 24))
    }

    /** 1.32 [WTYP-17] 대타(대근)는 소정근로가 아니라 주휴 계산에 넣지 않아요: 월요일 8시간 + 화요일 대타 8시간 = 주휴 없음. */
    @Test fun aCoverDoesNotMakeTheWeekEarnHolidayPay() {
        val monday = LocalDate.of(2026, 11, 2)
        val hourly = com.bamnat.core.pay.PayProfile(com.bamnat.core.pay.Occupation.CUSTOM, com.bamnat.core.pay.WageType.HOURLY,
            com.bamnat.core.pay.ValueTimeline(listOf(com.bamnat.core.pay.DatedValue(LocalDate.of(2026, 1, 1), 10_320))))
        val cover = com.bamnat.core.model.ExtraEntry(com.bamnat.core.model.ExtraKind.COVER_ID, coveredShiftId = 1)
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(monday, listOf(1L, null, null, null, null, null, null)),
            listOf(com.bamnat.core.model.DayEntry(monday.plusDays(1), extras = listOf(cover))))
        val pay = com.bamnat.core.pay.PayCalculator().calculate(hourly, YearMonth.of(2026, 11), r::resolve)
        val week = pay.holidayWeeks.first { it.from == monday }
        assertEquals(8 * 60, week.workedMinutes)
        assertTrue(!week.earned)
        val c = com.bamnat.core.work.HourlyLines.covers(YearMonth.of(2026, 11), r::resolve)
        assertEquals(1, c.count)
        assertEquals(8 * 60, c.minutes)
        assertTrue(com.bamnat.core.work.HourlyLines.coverLine(c)!!.contains("주휴 계산에 넣지 않았어요"))
        // 휴게 포함 시급: 8시간 일하고 1시간 쉬면 10,320 × 8/9.
        val stay = com.bamnat.core.work.HourlyLines.stay(YearMonth.of(2026, 11), 10_320, r::resolve)
        assertEquals(9_173, stay.copy(paidMinutes = 480, breakMinutes = 60).rate)
        assertTrue(stay.breakMinutes > 0)
    }

    @Test fun aCallSourceTakesItsOwnFeeAndInsurance() {
        val mon = LocalDate.of(2026, 10, 26)
        val cut = com.bamnat.core.money.PlatformWork.Cut(feePermille = 200, perCall = 1_000)
        assertEquals(20_000L + 5_000L, cut.of(100_000, 5))
        assertEquals("20%", com.bamnat.core.money.PlatformWork.Cut.percent(200))
        assertEquals("12.5%", com.bamnat.core.money.PlatformWork.Cut.percent(125))
        assertEquals(125, com.bamnat.core.money.PlatformWork.Cut.parsePercent("12.5"))
        assertEquals(null, com.bamnat.core.money.PlatformWork.Cut.parsePercent("120"))
        val rules = com.bamnat.core.money.FieldRules(mode = com.bamnat.core.money.FieldMode.PLATFORM, platformCosts = mapOf("로지" to cut))
        val j = com.bamnat.core.money.PlatformWork.job(mon, "로지", 5, 100_000, 300, 0)
        assertTrue(com.bamnat.core.money.PlatformWork.isPlatform(j))
        val m = com.bamnat.core.money.PlatformWork.month(YearMonth.from(mon), listOf(j), rules = rules)
        assertEquals(25_000L, m.fees)
        assertEquals(75_000L, m.net)
        // Without a cut set nothing comes off, as before.
        assertEquals(100_000L, com.bamnat.core.money.PlatformWork.month(YearMonth.from(mon), listOf(j)).net)
        // The deposit that comes is the amount after 3.3% and the cut.
        val p = com.bamnat.core.money.PlatformWork.payouts(listOf(j), rules).single()
        assertEquals(96_700L - 25_000L, p.expected)
        val dep = com.bamnat.core.money.MoneyEntry(date = p.payDate, kind = com.bamnat.core.money.MoneyKind.INCOME, amount = 71_700,
            category = "급여", merchant = "로지 정산")
        assertEquals(dep, com.bamnat.core.money.PlatformWork.depositFor(p, listOf(dep)))
    }

    @Test fun plusOneCountsOnTodaysJobForTheUsualPlatform() {
        val today = LocalDate.of(2026, 10, 29)
        val old = com.bamnat.core.money.PlatformWork.job(today.minusDays(3), "배민", 20, 90_000, 0, 0).copy(id = 1)
        val other = com.bamnat.core.money.PlatformWork.job(today.minusDays(2), "쿠팡이츠", 10, 40_000, 0, 0).copy(id = 2)
        val old2 = old.copy(id = 3, date = today.minusDays(1))
        val jobs = listOf(old, other, old2)
        assertEquals("배민", com.bamnat.core.money.PlatformWork.usual(today, jobs))
        val first = com.bamnat.core.money.PlatformWork.plusOne(today, "배민", jobs)
        assertEquals(1, com.bamnat.core.money.PlatformWork.count(first))
        assertEquals(0L, first.amount)
        val again = com.bamnat.core.money.PlatformWork.plusOne(today, "배민", jobs + first.copy(id = 4, amount = 5_000))
        assertEquals(2, com.bamnat.core.money.PlatformWork.count(again))
        assertEquals(5_000L, again.amount)
        // Today's platform wins over the most used one.
        assertEquals("쿠팡이츠", com.bamnat.core.money.PlatformWork.usual(today, jobs + other.copy(id = 5, date = today)))
        val week = com.bamnat.core.work.WidgetFigures.platformDay(jobs + again.copy(id = 4), LocalDate.of(2026, 10, 26), today)
        assertEquals(2, week.today)
        assertEquals(20 + 10 + 20 + 2, week.week)
        assertEquals(null, com.bamnat.core.money.PlatformWork.usual(today, emptyList()))
    }

    @Test fun theEndTimeGivesTheUnits() {
        val rules = com.bamnat.core.money.DayRate.SAMPLE_RULES
        assertEquals(1.0, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(16, 30), rules)!!, 0.0)
        assertEquals(1.0, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(17, 0), rules)!!, 0.0)
        assertEquals(1.5, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(17, 1), rules)!!, 0.0)
        assertEquals(2.0, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(20, 30), rules)!!, 0.0)
        // Past the last rule, the last rule's 공수.
        assertEquals(2.0, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(23, 0), rules)!!, 0.0)
        assertEquals(null, com.bamnat.core.money.DayRate.byEnd(LocalTime.of(17, 0), emptyList()))
        assertEquals("17:00까지 1공 · 19:00까지 1.5공 · 21:00까지 2공", com.bamnat.core.money.DayRate.rulesLine(rules))
        // Own chips: distinct, at most five, in 0..5; none means the first ones.
        assertEquals(listOf(1.0, 0.5, 1.5), com.bamnat.core.money.FieldRules().chips)
        assertEquals(listOf(1.0, 1.25, 2.0, 0.5, 3.0), com.bamnat.core.money.DayRate.cleanChips(listOf(1.0, 1.25, 1.0, 2.0, 0.0, 6.0, 0.5, 3.0, 4.0)))
        assertEquals(listOf(1.25), com.bamnat.core.money.FieldRules(unitChips = listOf(1.25)).chips)
    }

    @Test fun aTrackFillsTheCapAndItsKindsOnce() {
        val t = com.bamnat.core.work.PublicTracks.Track.TEACHER
        assertEquals(57, t.capHours)
        val kinds = com.bamnat.core.work.PublicTracks.withKinds(ExtraKinds(), t)
        assertEquals(listOf("학부모 상담", "생활지도", "평가 출제"), kinds.kinds.filter { !it.isBuiltIn }.map { it.name })
        // Twice is still once each.
        val again = com.bamnat.core.work.PublicTracks.withKinds(kinds, t)
        assertEquals(kinds.kinds.size, again.kinds.size)
        assertTrue(com.bamnat.core.work.PublicTracks.missing(again, t).isEmpty())
        // 군인: no cap is assumed.
        assertEquals(null, com.bamnat.core.work.PublicTracks.Track.MILITARY.capHours)
        assertTrue(com.bamnat.core.work.DayWork.PUBLIC_RULE_NOTE.contains("2026년 10월 1일"))
    }

    @Test fun theCompanysRecordIsSetBesideMine() {
        val r = ScheduleResolver(Fixtures.all, weekdays, emptyList())
        val thu = LocalDate.of(2026, 10, 15)
        // The company stamped 퇴근 one minute early; my record says 18:00.
        val row = com.bamnat.core.imports.AttendanceRow(thu, LocalTime.of(9, 0), LocalTime.of(17, 59))
        val d = com.bamnat.core.imports.AttendanceCompare.diff(row, r.resolve(thu))!!
        assertTrue(d.leftEarlier)
        assertEquals(1, d.shortMinutes)
        assertTrue(d.lines.single().startsWith("퇴근 회사 17:59 · 내 18:00"))
        // The same times: nothing to say.
        assertEquals(null, com.bamnat.core.imports.AttendanceCompare.diff(row.copy(end = LocalTime.of(18, 0)), r.resolve(thu)))
        // 결근 at the company on a day I worked.
        val absent = com.bamnat.core.imports.AttendanceCompare.diff(com.bamnat.core.imports.AttendanceRow(thu, absent = true), r.resolve(thu))!!
        assertTrue(absent.lines.single().startsWith("회사엔 결근"))
        val res = com.bamnat.core.imports.AttendanceCompare.compare(listOf(row, row.copy(date = thu.plusDays(1), end = LocalTime.of(18, 0))), r::resolve)
        assertEquals(2, res.compared)
        assertEquals("2일 비교 · 1일 달라요 · 회사 기록이 1분 적어요", res.summary)
    }
}
