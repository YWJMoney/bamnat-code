package com.bamnat.core

import com.bamnat.core.boss.Workplace
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamCheck.asType
import com.bamnat.core.team.TeamCover
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamSwap
import com.bamnat.core.team.TeamTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-29][RVJ-30][RVJ-31] 팀 근무표가 세는 시간 (검토 T05-04·PAYU-03·PAYU-08·T05-01): 휴게를 뺀 시간으로 주 52시간을 보고, 달이
 * 걸친 주는 두 달의 칸을 함께 읽고, 5인 미만 사업장에는 주 52시간을 말하지 않아요.
 */
class TeamHoursTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val day = TeamShift(1, "D", "주간", 0, false, t(9), t(18))
    private val off = TeamShift(9, "O", "휴무", 0, true)
    private fun team(vararg shifts: TeamShift, need: Map<Long, Int> = emptyMap()) = Team("t", "우리 가게", shifts = shifts.toList() + off, need = need)
    private fun people(n: Int) = (0 until n).map { TeamMember("m$it", "t", "직원$it", sort = it) }
    private fun weekSigns(team: Team, b: TeamBoard, rules: RestRules = RestRules(), prev: TeamBoard? = null, next: TeamBoard? = null) =
        TeamCheck.check(team, b, rules, prev, next).filter { it.kind == TeamCheck.Kind.WEEK_HOURS }

    // ---- [RVJ-29] 휴게 ----

    /** 검토 PAYU-03이 적은 시험: 09~18 × 6일은 휴게 1시간씩을 빼면 48시간이라 빨강이 없어요(전에는 54시간으로 셌어요). */
    @Test
    fun `nine to six on six days is 48 hours and is not told`() {
        var b = TeamBoard(nov, people(1))
        (2..7).forEach { b = b.paint("m0", nov.atDay(it), 1) } // 월 2일 ~ 토 7일
        assertEquals(8 * 60, TeamCheck.minutesOf(day))
        assertEquals(emptyList<TeamCheck.Sign>(), weekSigns(team(day), b))
        assertEquals(48 * 60, TeamCheck.fairness(team(day), b).single().minutes)
    }

    @Test
    fun `a seventh day goes past 52 and the line says the break is off`() {
        var b = TeamBoard(nov, people(1))
        (2..8).forEach { b = b.paint("m0", nov.atDay(it), 1) }
        val sign = weekSigns(team(day), b).single()
        assertEquals("직원0: 11/2 주 휴게 빼고 56시간, 52시간을 넘어요(근로기준법 제53조)", sign.text)
        assertEquals(nov.atDay(8), sign.date)
    }

    /** 법이 정한 휴게: 시계로 4시간부터 30분, 8시간 30분부터 1시간(근로기준법 제54조, LegalBreak). 팀이 정한 값이 있으면 그 값이에요. */
    @Test
    fun `the break is the law's by the length of the day unless the team set its own`() {
        fun brk(from: LocalTime, to: LocalTime, own: Int? = null) = TeamCheck.breakOf(TeamShift(1, "D", "주간", 0, false, from, to, own))
        assertEquals(0, brk(t(9), t(12, 59)))
        assertEquals(30, brk(t(9), t(13)))
        assertEquals(30, brk(t(7), t(15)))
        assertEquals(60, brk(t(9), t(17, 30)))
        assertEquals("밤을 넘는 근무도 길이로", 60, brk(t(22), t(7)))
        assertEquals(0, brk(t(9), t(18), own = 0))
        assertEquals(120, brk(t(9), t(21), own = 120))
        assertEquals("근무보다 긴 휴게는 근무 길이까지만", 60, brk(t(9), t(10), own = 300))
        assertEquals(0, TeamCheck.breakOf(off))
        assertEquals(0, TeamCheck.minutesOf(TeamShift(2, "?", "시간 없음", 0, false)))
    }

    @Test
    fun `with no break set by the team the whole day counts and the line does not speak of one`() {
        val whole = day.copy(breakMinutes = 0)
        var b = TeamBoard(nov, people(1))
        (2..7).forEach { b = b.paint("m0", nov.atDay(it), 1) }
        assertEquals("직원0: 11/2 주 54시간, 52시간을 넘어요(근로기준법 제53조)", weekSigns(team(whole), b).single().text)
    }

    /** 30분 휴게가 흔해서 반 시간이 자주 나와요: '52시간, 52시간을 넘어요'처럼 읽히지 않게 분까지 말해요. */
    @Test
    fun `half hours are said`() {
        val d = TeamShift(1, "D", "데이", 0, false, t(7), t(15))
        var b = TeamBoard(nov, people(1))
        (2..8).forEach { b = b.paint("m0", nov.atDay(it), 1) }
        assertEquals("직원0: 11/2 주 휴게 빼고 52시간 30분, 52시간을 넘어요(근로기준법 제53조)", weekSigns(team(d), b).single().text)
    }

    @Test
    fun `auto fill counts worked hours so six days of nine to six are filled`() {
        // 한 사람, 주간 1명 필요: 휴게를 안 빼면 주 5일(45시간)에서 멈추고 엿새째가 '부족'으로 남았어요.
        val plan = TeamAuto.fill(team(day, need = mapOf(1L to 1)), TeamBoard(nov, people(1)))
        val week = (2..8).map { plan.board.cell("m0", nov.atDay(it))?.shiftId }
        assertEquals("엿새 일하고 하루 쉼(연속 6일 규칙)", 6, week.count { it == 1L })
        assertEquals(0, plan.overWeeks)
        // 평균으로 필요한 시간도 일한 시간으로: 8시간 × 7일 ÷ 1명.
        assertEquals(56.0, TeamAuto.weekHoursNeeded(team(day, need = mapOf(1L to 1)), TeamBoard(nov, people(1))), 0.01)
    }

    @Test
    fun `a kind of day goes to a member's calendar with its break`() {
        assertEquals(60, day.asType().breakMinutes)
        assertEquals(8 * 60, day.asType().workMinutes)
        assertEquals(0, day.copy(breakMinutes = 0).asType().breakMinutes)
    }

    @Test
    fun `the team takes the leader's own break and the law's when the calendar has none`() {
        val mine = ShiftType(1, "주간", "D", 0, t(9), t(18), breakMinutes = 90)
        assertEquals(90, TeamTimes.of(mine).breakMinutes)
        assertNull("달력의 0분은 '안 정함'이라 법으로 정한 만큼", TeamTimes.of(mine.copy(breakMinutes = 0)).breakMinutes)
        assertNull(TeamTimes.of(ShiftType(9, "휴무", "O", 0, null, null, breakMinutes = 30, isOff = true)).breakMinutes)
        val set = TeamTimes.setHours(team(day), mapOf(1L to TeamTimes.Hours(t(8), t(20), 9999), 9L to TeamTimes.Hours(t(1), t(2), 10)))
        assertEquals(TeamShift(1, "D", "주간", 0, false, t(8), t(20), TeamTimes.MAX_BREAK), set.shifts.first())
        assertEquals("쉬는 날은 그대로", off, set.shifts.last())
    }

    /** 검토 PC-18: 리더가 자기 달력의 데이를 09~18(휴게 60분)로 바꿨는데 팀은 07~15 그대로였어요. 다른 것을 찾아 알려요. */
    @Test
    fun `kinds whose times left the leader's calendar are found`() {
        val teamDay = TeamShift(1, "D", "데이", 0, false, t(7), t(15))
        val same = TeamShift(2, "E", "이브닝", 0, false, t(15), t(23), breakMinutes = 45)
        val mine = listOf(
            ShiftType(1, "데이", "D", 0, t(9), t(18), breakMinutes = 60),
            ShiftType(2, "이브닝", "E", 0, t(15), t(23)), // 휴게는 팀만 정함: 다른 것이 아니에요
            ShiftType(9, "휴무", "O", 0, null, null, isOff = true),
        )
        assertEquals(mapOf(1L to TeamTimes.Hours(t(9), t(18), 60)), TeamTimes.apart(team(teamDay, same), mine))
        assertEquals(emptyMap<Long, TeamTimes.Hours>(), TeamTimes.apart(team(teamDay.copy(start = t(9), end = t(18), breakMinutes = 60), same), mine))
        // 달력 시간으로 맞추면 시간은 달력 것, 달력이 정하지 않은 휴게는 팀 것 그대로예요.
        val early = same.copy(start = t(14), end = t(22))
        val took = TeamTimes.takeMine(mapOf(2L to TeamTimes.hours(early)), TeamTimes.apart(team(early), mine))
        assertEquals(mapOf(2L to TeamTimes.Hours(t(15), t(23), 45)), took)
        // 달력에 휴게만 새로 정해도 달라요.
        assertEquals(setOf(2L), TeamTimes.apart(team(teamDay.copy(start = t(9), end = t(18), breakMinutes = 60), same),
            mine.map { if (it.id == 2L) it.copy(breakMinutes = 30) else it }).keys)
        // 번호만 같고 이름도 글자도 다른 근무(파일에서 읽은 팀의 1번 'A조')는 내 달력의 데이가 아니에요.
        assertEquals(emptyMap<Long, TeamTimes.Hours>(), TeamTimes.apart(team(TeamShift(1, "A", "A조", 0, false, t(6), t(14))), mine))
    }

    /** 시트는 이제 아무 때나 열려요. 한 근무의 휴게만 고친 저장이 다른 근무 시간까지 내 달력으로 옮기면 안 돼요. */
    @Test
    fun `only the kinds moved in this save go to the leader's calendar`() {
        val dayKind = TeamShift(1, "D", "주간", 0, false, t(7), t(15))
        val nightKind = TeamShift(3, "N", "야간", 0, false, t(23), t(7))
        val before = team(dayKind, nightKind)
        val mine = listOf(ShiftType(1, "주간", "주", 0, t(9), t(18), breakMinutes = 60), ShiftType(3, "야간", "야", 0, t(22), t(7), breakMinutes = 60))
        // 휴게만 고침: 옮길 것이 없어요.
        val onlyBreak = team(dayKind.copy(breakMinutes = 45), nightKind)
        assertEquals(emptySet<Long>(), TeamTimes.moved(before, onlyBreak))
        assertEquals(emptyList<TeamTimes.MineChange>(), TeamTimes.mine(onlyBreak, mine, mine, only = TeamTimes.moved(before, onlyBreak)))
        // 야간만 옮김: 야간만 가요(주간 07~15는 전부터 팀이 따로 정한 시간).
        val nightMoved = team(dayKind, nightKind.copy(start = t(21)))
        assertEquals(setOf(3L), TeamTimes.moved(before, nightMoved))
        assertEquals(listOf(3L), TeamTimes.mine(nightMoved, mine, mine, only = TeamTimes.moved(before, nightMoved)).map { it.type.id })
        // 1.39 그대로(only 없이)는 둘 다.
        assertEquals(listOf(1L, 3L), TeamTimes.mine(nightMoved, mine, mine).map { it.type.id })
    }

    // ---- [RVJ-30] 달이 걸친 주 ----

    /** 11월 30일(월)~12월 6일(일)은 한 주예요. 11월 표에 하루, 12월 표에 엿새를 그리면 56시간인데 전에는 표마다 따로 세어 놓쳤어요. */
    @Test
    fun `a week over two months is read whole on both boards`() {
        val team = team(day)
        val last = TeamBoard(nov, people(1)).paint("m0", nov.atDay(30), 1)
        var first = TeamBoard(dec, people(1))
        (1..6).forEach { first = first.paint("m0", dec.atDay(it), 1) }
        assertEquals("12월 표만 보면 48시간", emptyList<TeamCheck.Sign>(), weekSigns(team, first))
        val onDec = weekSigns(team, first, prev = last).single()
        assertEquals("직원0: 11/30 주 휴게 빼고 56시간, 52시간을 넘어요(근로기준법 제53조)", onDec.text)
        assertEquals(dec.atDay(6), onDec.date)
        val onNov = weekSigns(team, last, next = first).single()
        assertEquals("11월 표에서는 그 달 마지막 날에", nov.atDay(30), onNov.date)
        assertEquals(onDec.text, onNov.text)
    }

    @Test
    fun `auto fill and cover keep the hours already drawn across the month's edge`() {
        // 12시간 일하는 근무(08~21, 휴게 1시간) 한 자리, 한 사람. 11월 30일(월)에 이미 12시간 일했어요.
        val long = TeamShift(1, "D", "종일", 0, false, t(8), t(21))
        val team = team(long, need = mapOf(1L to 1))
        fun firstWeek(p: TeamAuto.Plan) = (1..6).count { p.board.cell("m0", dec.atDay(it))?.shiftId == 1L }
        assertEquals("12월 표만 보면 그 주에 네 번(48시간)", 4, firstWeek(TeamAuto.fill(team, TeamBoard(dec, people(1)))))
        val worked = TeamBoard(nov, people(1)).paint("m0", nov.atDay(30), 1)
        val plan = TeamAuto.fill(team, TeamBoard(dec, people(1)), prev = worked)
        assertEquals("11월 30일까지 세면 세 번(12 + 36 = 48시간)", 3, firstWeek(plan))
        assertEquals(0, plan.overWeeks)
        assertEquals(emptyList<TeamCheck.Sign>(), weekSigns(team, plan.board, prev = worked))

        // 결원 메우기: 직원1이 12월 5일(토)에 못 나와요. 직원0은 11월 30일과 12월 1~3일에 일해서, 5일까지 나오면 60시간이에요.
        val last = TeamBoard(nov, people(2)).paint("m0", nov.atDay(30), 1)
        var b = TeamBoard(dec, people(2))
        (1..3).forEach { b = b.paint("m0", dec.atDay(it), 1) }
        b = b.paint("m1", dec.atDay(5), 1)
        val gap = TeamCover.Gap("m1", dec.atDay(5), 1)
        assertTrue("12월 표만 보면 48시간이라 된다고 했어요", TeamCover.candidates(team, b, gap).single().ok)
        val c = TeamCover.candidates(team, b, gap, prev = last).single()
        assertFalse(c.ok)
        assertEquals(listOf("11/30 주 휴게 빼고 60시간, 52시간을 넘어요(근로기준법 제53조)"), c.why)
    }

    @Test
    fun `a trade in the last week is checked with next month's first days`() {
        val long = TeamShift(1, "D", "종일", 0, false, t(8), t(21))
        val team = team(long)
        // 11월 30일(월): 직원0 근무, 직원1 휴무. 12월 1~4일은 직원1이 일해요. 바꾸면 직원1이 그 주 60시간.
        val b = TeamBoard(nov, people(2)).paint("m0", nov.atDay(30), 1).paint("m1", nov.atDay(30), 9)
        var next = TeamBoard(dec, people(2))
        (1..4).forEach { next = next.paint("m1", dec.atDay(it), 1) }
        assertTrue(TeamSwap.options(team, b).getValue("m0").containsKey(nov.atDay(30)))
        assertFalse("다음 달 첫 주까지 읽으면 바꿀 수 없는 날", TeamSwap.options(team, b, next = next)["m0"].orEmpty().containsKey(nov.atDay(30)))
        assertTrue(TeamCheck.swapCheck(team, b, "m0", "m1", nov.atDay(30), next = next).any { it.kind == TeamCheck.Kind.WEEK_HOURS && it.memberId == "m1" })
    }

    // ---- [RVJ-31] 5인 미만 ----

    /** 5인 미만 사업장에는 제50조·제53조가 적용되지 않아요(근로기준법 제11조 제2항, 시행령 제7조 별표 1). 대장처럼 말하지 않아요. */
    @Test
    fun `a place under five people is not told about 52 hours, the other rules stay`() {
        val small = RestRules(weekLimit = false)
        var b = TeamBoard(nov, people(1))
        (2..8).forEach { b = b.paint("m0", nov.atDay(it), 1) }
        assertEquals(1, weekSigns(team(day), b).size)
        assertEquals(emptyList<TeamCheck.Sign>(), weekSigns(team(day), b, small))
        assertTrue("연속 근무 알림은 그대로(주휴일 제55조 제1항은 적용돼요)", TeamCheck.check(team(day), b, small).any { it.kind == TeamCheck.Kind.RULE })

        // 자동 채우기도 52시간으로 막지 않아요: 13시간 일하는 근무(08~21, 팀이 휴게 없음으로 정함)를 한 사람이 엿새(78시간).
        val long = TeamShift(1, "D", "종일", 0, false, t(8), t(21), breakMinutes = 0)
        val t = team(long, need = mapOf(1L to 1))
        fun days(rules: RestRules) = TeamAuto.fill(t, TeamBoard(nov, people(1)), rules = rules).let { p -> (2..8).count { p.board.cell("m0", nov.atDay(it))?.shiftId == 1L } }
        assertEquals("5인 이상이면 13시간 × 4일 = 52시간까지", 4, days(RestRules()))
        assertEquals(6, days(small))

        // 결원 메우기의 까닭에도 52시간이 없어요.
        var c = TeamBoard(nov, people(2))
        (2..5).forEach { c = c.paint("m0", nov.atDay(it), 1) }
        c = c.paint("m1", nov.atDay(6), 1)
        val gap = TeamCover.Gap("m1", nov.atDay(6), 1)
        assertTrue(TeamCover.candidates(t, c, gap).single().why.single().contains("52시간"))
        assertTrue(TeamCover.candidates(t, c, gap, small).single().ok)
    }

    /** 어디서 5인 미만을 아는지: 팀에 묶인 사업장(없으면 하나뿐인 사업장)의 답, 답이 없으면 리더 자신의 급여 설정. 모르면 5인 이상으로 봐요. */
    @Test
    fun `the team is under five when its place or the leader's own pay settings say so`() {
        fun place(id: String, five: Boolean?, team: String? = null) = Workplace(id, "가게", fivePlus = five, teamId = team)
        assertFalse("아무것도 모르면 5인 이상", Workplace.teamUnderFive("t", emptyList(), null))
        assertTrue(Workplace.teamUnderFive("t", listOf(place("a", false)), null))
        assertFalse("대장이 5명 이상이라고 했으면 내 급여 설정보다 먼저", Workplace.teamUnderFive("t", listOf(place("a", true)), false))
        assertTrue("대장이 아직 답하지 않았으면 내 급여 설정", Workplace.teamUnderFive("t", listOf(place("a", null)), false))
        assertTrue("사업장이 없으면 내 급여 설정", Workplace.teamUnderFive("t", emptyList(), false))
        assertFalse(Workplace.teamUnderFive("t", emptyList(), true))
        // 가게가 둘이면 팀에 묶인 가게만 봐요. 묶인 곳이 없으면 어느 가게인지 모르니 내 급여 설정으로.
        assertTrue(Workplace.teamUnderFive("t", listOf(place("a", true), place("b", false, team = "t")), null))
        assertFalse(Workplace.teamUnderFive("t", listOf(place("a", false), place("b", false)), null))
    }
}
