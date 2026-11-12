package com.bamnat.core

import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.LedgerCsv
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.LineKind
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.PlanVsActual
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.team.CellSource
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCopy
import com.bamnat.core.team.TeamEditor
import com.bamnat.core.team.TeamImport
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamOverlap
import com.bamnat.core.team.TeamPaste
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** [P3-02][P3-03][P3-04] 1.38 Phase 3 P3-1 판: 리더 팀 근무표, 사장님 급여 대장, 계획 대 실제 잠의 core (all shipped off). */
class Phase3CoreTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val D = 1L
    private val E = 2L
    private val N = 3L
    private val O = 4L

    // ---- M24 L1 리더 팀 근무표 ----

    private fun members(vararg names: String) = names.mapIndexed { i, n -> TeamMember("m$i", "t", n, sort = i) }

    @Test
    fun `pasted names become members, roles apart and twins numbered`() {
        val p = TeamPaste.people("1. 민지 책임\n- 준호\n\n김수아 RN\n민지\n이하나 신규")
        assertEquals(listOf("민지", "준호", "김수아", "민지2", "이하나"), p.map { it.name })
        assertEquals("책임", p[0].role)
        assertEquals(Skill.CHARGE, p[0].skill)
        assertEquals(Skill.REG, p[2].skill)
        assertEquals(Skill.NEW, p[4].skill)
        // 1.38 PC 1차: "지우 (주임)", "하은/선임", "민지★".
        val q = TeamPaste.people("지우 (주임)\n하은/선임\n서연★\n도윤 [신규]")
        assertEquals(listOf("지우", "하은", "서연", "도윤"), q.map { it.name })
        assertEquals(listOf("주임", "선임", "책임", "신규"), q.map { it.role })
        assertEquals(listOf(Skill.CHARGE, Skill.CHARGE, Skill.CHARGE, Skill.NEW), q.map { it.skill })
    }

    @Test
    fun `painting, pasting a run, swapping and locking`() {
        var b = TeamBoard(nov, members("민지", "준호"))
        b = b.paste("m0", nov.atDay(1), listOf(D, D, E, null, N))
        assertEquals(listOf(D, D, E, null, N), b.row("m0").take(5).map { it?.shiftId })
        b = b.paint("m1", nov.atDay(1), N)
        b = b.swap("m0", "m1", nov.atDay(1))
        assertEquals(N, b.cell("m0", nov.atDay(1))!!.shiftId)
        assertEquals(D, b.cell("m1", nov.atDay(1))!!.shiftId)
        b = b.lock("m0", nov.atDay(1), true)
        assertEquals(b, b.paint("m0", nov.atDay(1), O))
        assertEquals(b, b.swap("m0", "m1", nov.atDay(1)))
        // A day of another month is never painted.
        assertEquals(b, b.paint("m0", dec.atDay(1), D))
        assertEquals(2 * 30 - 5, b.empty)
    }

    @Test
    fun `undo and redo walk the steps back and forth`() {
        val ed = TeamEditor(TeamBoard(nov, members("민지")))
        ed.apply { it.paint("m0", nov.atDay(1), D) }
        ed.apply { it.paint("m0", nov.atDay(2), E) }
        assertTrue(ed.canUndo)
        ed.undo()
        assertNull(ed.board.cell("m0", nov.atDay(2)))
        ed.redo()
        assertEquals(E, ed.board.cell("m0", nov.atDay(2))!!.shiftId)
        // A step that changes nothing is not a step.
        val before = ed.board
        ed.apply { it.paint("m0", dec.atDay(1), D) }
        ed.undo()
        assertNull(ed.board.cell("m0", nov.atDay(2)))
        assertFalse(before == ed.board)
    }

    @Test
    fun `next month continues each member's cycle, or copies the weekday`() {
        val cycle = listOf(D, D, N, N, O, O)
        var last = TeamBoard(nov, members("교대", "일근"))
        last = last.paste("m0", nov.atDay(1), List(30) { cycle[it % 6] })
        // 일근: 월~금 D, 주말 O, with one day off in the middle so it is no clean cycle.
        last.days.forEach { d -> last = last.paint("m1", d, if (d.dayOfWeek.value <= 5) D else O) }
        last = last.paint("m1", nov.atDay(11), O)
        val next = TeamCopy.nextMonth(last, TeamBoard(dec, last.members))
        // Day 30 of Nov was step 29 % 6 = 5 (O), so Dec 1 is step 0 again.
        assertEquals(cycle + cycle.take(1), next.row("m0").take(7).map { it?.shiftId })
        assertEquals(CellSource.COPY, next.cell("m0", dec.atDay(1))!!.source)
        // Dec 2 2026 is a Wednesday: the same weekday of last month.
        assertEquals(D, next.cell("m1", dec.atDay(2))!!.shiftId)
        assertEquals(O, next.cell("m1", dec.atDay(5))!!.shiftId)
        assertEquals(listOf(D, N), TeamCopy.cycleOf(List(12) { if (it % 2 == 0) D else N }))
        assertNull(TeamCopy.cycleOf(listOf(D, E, N)))
    }

    @Test
    fun `counts, short days and changes since the confirmed board`() {
        var b = TeamBoard(nov, members("a", "b", "c"))
        b = b.paint("m0", nov.atDay(1), D).paint("m1", nov.atDay(1), D).paint("m2", nov.atDay(1), N)
        assertEquals(mapOf(D to 2, N to 1), b.count(nov.atDay(1)))
        val short = b.short(mapOf(D to 2, N to 2))
        assertEquals(mapOf(N to 1), short[nov.atDay(1)])
        assertEquals(mapOf(D to 2, N to 2), short[nov.atDay(2)])
        val confirmed = b
        b = b.paint("m2", nov.atDay(1), O).paint("m0", nov.atDay(3), E)
        val changes = b.changesFrom(confirmed)
        assertEquals(2, changes.size)
        assertEquals(Triple("m2", nov.atDay(1), N to O), changes[0])
        assertEquals(mapOf(nov.atDay(1) to D, nov.atDay(3) to E), b.daysOf("m0"))
    }

    @Test
    fun `a photographed roster becomes members and cells`() {
        val rows = listOf(
            RosterRow("민지 책임", mapOf(1 to DutyCell.Shift(D), 2 to DutyCell.Off, 3 to DutyCell.Unknown("?"))),
            RosterRow("", mapOf(1 to DutyCell.Shift(N), 31 to DutyCell.Shift(N))),
        )
        val r = TeamImport.fromRows(rows, nov, offId = O)
        assertEquals(listOf("민지", "이름 없음 2"), r.people.map { it.name })
        assertEquals(mapOf(nov.atDay(1) to D, nov.atDay(2) to O), r.cells[0])
        // Day 31 is past November.
        assertEquals(mapOf(nov.atDay(1) to N), r.cells[1])
        assertTrue(nov.atDay(3) in r.unsure[0]!!)
    }

    @Test
    fun `the same name working the same day on two teams is told`() {
        val a = Team("ta", "A팀")
        val b = Team("tb", "B팀")
        val ba = TeamBoard(nov, listOf(TeamMember("x", "ta", "민지"))).paint("x", nov.atDay(1), D).paint("x", nov.atDay(2), O)
        val bb = TeamBoard(nov, listOf(TeamMember("y", "tb", "민 지"))).paint("y", nov.atDay(1), N).paint("y", nov.atDay(2), D)
        val c = TeamOverlap.clashes(mapOf(a to ba, b to bb), offIds = setOf(O))
        assertEquals(1, c.size)
        assertEquals(nov.atDay(1), c[0].date)
        assertEquals(listOf("A팀", "B팀"), c[0].teams)
    }

    // ---- M25 B1 사장님 급여 대장 ----

    private val nine2six = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val weekdays = DayOfWeek.entries.filter { it.value <= 5 }.associateWith { nine2six }
    private val place = Workplace("p", "우리 카페", fivePlus = true)

    @Test
    fun `a full-time hourly member is paid hours and 주휴 by the same engine`() {
        val s = StaffMember("s", "민지", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays)
        val l = PayrollLedger.line(place, s, nov, emptyList())
        // November 2026 has 21 weekdays; 9~18 less the legal hour is 8 hours.
        assertEquals(21, l.workDays)
        assertEquals(21 * 8 * 60, l.workMinutes)
        assertTrue(l.gross > 21 * 8 * 10_320)
        assertTrue("주휴 line", l.pay!!.holidayWeeks.any { it.earned })
        assertTrue(l.warnings.isEmpty())
        assertEquals(0, l.employerInsurance)
    }

    @Test
    fun `an absence takes the week's 주휴 away and says so`() {
        val s = StaffMember("s", "민지", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays)
        val full = PayrollLedger.line(place, s, nov, emptyList())
        val absent = PayrollLedger.line(place, s, nov, listOf(StaffWork("s", LocalDate.of(2026, 11, 11), WorkKind.ABSENT)))
        assertEquals(20, absent.workDays)
        assertTrue(absent.gross < full.gross - 8 * 10_320)
        assertTrue(absent.warnings.any { it.key == "holiday" })
        // A day the shop is closed is not a 결근: no 주휴 warning.
        val off = PayrollLedger.line(place, s, nov, listOf(StaffWork("s", LocalDate.of(2026, 11, 11), WorkKind.OFF)))
        assertTrue(off.warnings.none { it.key == "holiday" })
    }

    @Test
    fun `under five people night work has no premium`() {
        val night = StaffMember("s", "준호", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1),
            weekly = mapOf(DayOfWeek.FRIDAY to StaffShift(LocalTime.of(20, 0), LocalTime.of(2, 0))))
        val five = PayrollLedger.line(place, night, nov, emptyList())
        val small = PayrollLedger.line(place.copy(fivePlus = false), night, nov, emptyList())
        assertTrue(five.amountOf(LineKind.NIGHT) > 0)
        assertEquals(0, small.amountOf(LineKind.NIGHT))
        assertNotNull(PayrollLedger.month(place.copy(fivePlus = false), listOf(night), nov, emptyList()).note)
        assertNull(PayrollLedger.month(place, listOf(night), nov, emptyList()).note)
    }

    @Test
    fun `late, cover and changed hours move the pay`() {
        val s = StaffMember("s", "민지", base = 10_000 + 320, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays)
        val base = PayrollLedger.line(place, s, nov, emptyList())
        val late = PayrollLedger.line(place, s, nov, listOf(StaffWork("s", LocalDate.of(2026, 11, 2), WorkKind.LATE, LocalTime.of(10, 0), LocalTime.of(18, 0))))
        val cover = PayrollLedger.line(place, s, nov, listOf(StaffWork("s", LocalDate.of(2026, 11, 7), WorkKind.COVER, LocalTime.of(9, 0), LocalTime.of(13, 0))))
        assertTrue(late.gross < base.gross)
        assertTrue(cover.gross > base.gross)
        assertEquals(base.workDays + 1, cover.workDays)
    }

    @Test
    fun `under the minimum wage, left before the month, and the employer's insurance`() {
        val low = StaffMember("s", "민지", base = 9_000, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays)
        assertTrue(PayrollLedger.line(place, low, nov, emptyList()).warnings.any { it.key == "min" })
        val gone = low.copy(leftOn = LocalDate.of(2026, 10, 31))
        assertTrue(PayrollLedger.month(place, listOf(gone), nov, emptyList()).lines.isEmpty())
        val insured = StaffMember("s", "민지", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays, insurance = Insurance.ALL)
        val l = PayrollLedger.line(place, insured, nov, emptyList())
        assertTrue(l.employerInsurance > 0)
        val healthOnly = PayrollLedger.employerInsurance(l.gross, Insurance(health = true), nov.atDay(1))
        assertTrue(healthOnly in 1 until l.employerInsurance)
        val monthly = StaffMember("m", "점장", form = WageForm.MONTH, base = 2_500_000, hiredOn = LocalDate.of(2026, 1, 1), weekly = weekdays)
        assertEquals(2_500_000, PayrollLedger.line(place, monthly, nov, emptyList()).amountOf(LineKind.BASE))
    }

    @Test
    fun `SHOP-11 staff move to the ledger once`() {
        val roster = StaffRoster(listOf(Staff("a", "민지", 11_000, holidayPay = false, insured = true, shifts = weekdays)), legalBreaks = false)
        val (p, staff) = PayrollLedger.fromRoster(roster, "p")
        assertFalse(p.legalBreaks)
        assertEquals(11_000, staff[0].base)
        assertFalse(staff[0].weeklyHolidayPay)
        assertEquals(Insurance.ALL, staff[0].insurance)
        assertEquals(weekdays, staff[0].weekly)
    }

    @Test
    fun `the 임금대장 file has the items of 시행령 제27조 and keeps formulas out`() {
        val s = StaffMember("s", "=민지", base = 10_320, hiredOn = LocalDate.of(2026, 1, 1), birth = LocalDate.of(2001, 5, 6), job = "홀", weekly = weekdays)
        val csv = LedgerCsv.write(PayrollLedger.month(place, listOf(s), nov, emptyList()))
        listOf("성명", "생년월일", "고용 연월일", "종사 업무", "임금 계산기초", "근로일수", "근로시간", "연장근로시간", "기본급", "공제액").forEach {
            assertTrue(it, csv.contains(it))
        }
        assertTrue(csv.contains("'=민지"))
        assertTrue(csv.contains("2001-05-06"))
        assertTrue(csv.contains("3년"))
        val row = LedgerCsv.cells(PayrollLedger.line(place, s, nov, emptyList()))
        assertEquals(LedgerCsv.HEADER.size, row.size)
    }

    // ---- M23 계획 대 실제 잠 ----

    @Test
    fun `the sleep after a night is set against what was measured`() {
        val night = Fixtures.night
        val types = Fixtures.all
        val r = ScheduleResolver(types, WorkPattern.Rotation(nov.atDay(1), listOf(night.id, Fixtures.off.id)), emptyList())
        // A night on Nov 1 (22:00~07:00 or so) is slept off on the morning of Nov 2.
        val nights = PlanVsActual.nights(emptyList(), r, nov.atDay(1), nov.atDay(1), alarmsOn = false)
        assertTrue(nights.isNotEmpty())
        val plan = nights.first { it.kind == PlanVsActual.Kind.AFTER_NIGHT }
        assertEquals(PlanVsActual.Verdict.NO_RECORD, plan.verdict)
        val short = SleepEntry(1, plan.planFrom.plusMinutes(30), plan.planFrom.plusMinutes(30 + 240), imported = true)
        val got = PlanVsActual.of(com.bamnat.core.sleep.SleepSchedule.of(r, nov.atDay(1), nov.atDay(1), false), listOf(short), nov.atDay(1), nov.atDay(1))
            .first { it.kind == PlanVsActual.Kind.AFTER_NIGHT }
        assertEquals(PlanVsActual.Verdict.SHORT, got.verdict)
        assertEquals(240L - got.planMinutes, got.diffMinutes)
        assertTrue(got.measured)
        assertTrue(got.line, got.line.contains("적게"))
        // One night is not a habit: no summary line yet.
        assertNull(PlanVsActual.summary(listOf(got)).headline)
        val month = PlanVsActual.nights(
            (0 until 10).map { i -> val d = nov.atDay(2 + i * 2); SleepEntry(i.toLong(), d.atTime(8, 30), d.atTime(13, 0)) },
            r, nov.atDay(1), nov.atDay(20), alarmsOn = false,
        )
        val sum = PlanVsActual.summary(month)
        assertTrue(sum.enough)
        assertNotNull(sum.headline)
        // A record far from any plan is not taken.
        val far = PlanVsActual.nights(listOf(SleepEntry(9, LocalDateTime.of(2026, 11, 1, 13, 0), LocalDateTime.of(2026, 11, 1, 14, 0))),
            r, nov.atDay(1), nov.atDay(1), alarmsOn = false).first { it.kind == PlanVsActual.Kind.AFTER_NIGHT }
        assertEquals(PlanVsActual.Verdict.NO_RECORD, far.verdict)
        // 2.0.0 [RVIB-14] (PC-124) 잠들었어요 then 깼어요 at once is a tap, not a sleep 7시간 short of the plan.
        val tap = SleepEntry(10, plan.planFrom, plan.planFrom.plusMinutes(2))
        val tapped = PlanVsActual.of(com.bamnat.core.sleep.SleepSchedule.of(r, nov.atDay(1), nov.atDay(1), false), listOf(tap), nov.atDay(1), nov.atDay(1))
            .first { it.kind == PlanVsActual.Kind.AFTER_NIGHT }
        assertEquals(PlanVsActual.Verdict.NO_RECORD, tapped.verdict)
    }
}
