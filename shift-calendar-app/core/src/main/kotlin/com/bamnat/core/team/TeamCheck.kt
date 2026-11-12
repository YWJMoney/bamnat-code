package com.bamnat.core.team

import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.LegalBreak
import com.bamnat.core.schedule.RestRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 1.38 [P3-04][TEAM-16] L2 팀 규칙 바로 검사 (순번 490, 설계 2-1 [132]): while the leader paints, each member's row read by the same
 * [DutyCheck] the user's own 한 달 넣기 uses (역순 N→D·E→D, 11시간, 나이트 뒤 쉬는 날, 연속 근무, 월 야간 상한), plus what only a team
 * has: 근무별 최소 인원 ([Team.need]), 책임자 없는 근무, and 주 52시간 (근로기준법 제53조). 알림 only; the board saves as painted.
 */
object TeamCheck {
    enum class Kind { RULE, SHORT, NO_CHARGE, WEEK_HOURS }

    /** One sign: a member's cell ([memberId] set) or a whole day ([memberId] null). */
    data class Sign(val kind: Kind, val date: LocalDate, val memberId: String?, val text: String)

    /** 주 52시간: 40 + 12 (근로기준법 제50·53조). */
    const val WEEK_LIMIT_MINUTES = 52 * 60

    fun TeamShift.asType(): ShiftType = ShiftType(id = id, name = name, label = label, colorArgb = colorArgb, start = start, end = end,
        breakMinutes = breakOf(this), isOff = isOff)

    /** Minutes a kind of day takes on the clock, start to end (over midnight too); 0 for an off day or one without times. */
    fun spanOf(s: TeamShift): Int {
        if (s.isOff || s.start == null || s.end == null) return 0
        val m = (s.end.toSecondOfDay() - s.start.toSecondOfDay()) / 60
        return if (m <= 0) m + 24 * 60 else m
    }

    /**
     * 2.0.0 [RVJ-29] (검토 T05-04·PAYU-03) The 휴게 inside a kind of day: the team's own ([TeamShift.breakMinutes]), else what the
     * law asks for that long a day (근로기준법 제54조, [LegalBreak.minutesFor]), the way the 급여 대장 counts the same shop.
     */
    fun breakOf(s: TeamShift): Int = spanOf(s).let { span -> (s.breakMinutes ?: LegalBreak.minutesFor(span)).coerceIn(0, span) }

    /**
     * Minutes worked in a kind of day: start to end less the 휴게, which is not 근로시간 (제50조 제1항 "휴게시간을 제외하고"). A team
     * on 09~18 six days is 48 hours, not 54 ([RVJ-29]: the 52시간 sign and the auto-fill read this).
     */
    fun minutesOf(s: TeamShift): Int = spanOf(s) - breakOf(s)

    /**
     * [RVJ-29] What a week past the limit is told with: "휴게 빼고 55시간, 52시간을 넘어요" ("휴게 빼고" only when some was taken
     * off). To the minute ("52시간 30분"): a 30분 휴게 makes half hours common, and "52시간, 52시간을 넘어요" would read wrong.
     */
    fun overWords(week: List<TeamShift>): String =
        (if (week.any { breakOf(it) > 0 }) "휴게 빼고 " else "") + "${com.bamnat.core.hours.HoursFormat.korean(week.sumOf(::minutesOf))}, 52시간을 넘어요"

    /**
     * 2.0.0 [RVJ-30] (검토 PAYU-08) The cell of [date] wherever it is drawn: this month's [board], else last month's or next month's.
     * A week that lies over two months is one week to the law, so its hours are read from both.
     */
    fun cellAt(board: TeamBoard, prev: TeamBoard?, next: TeamBoard?, memberId: String, date: LocalDate): TeamCell? =
        board.cell(memberId, date) ?: prev?.cell(memberId, date) ?: next?.cell(memberId, date)

    /** 1.38 병동 신청표 (사용자 2026-11-06): how many days of last month are read with this one, like the paper's "NN off" margin. */
    const val CARRY_DAYS = 7

    /** The last [CARRY_DAYS] of [prev] for [memberId], to read before the month (null when there is no last month). */
    fun carry(prev: TeamBoard?, memberId: String, asTypes: Map<Long, ShiftType>): List<Pair<LocalDate, ShiftType?>> =
        prev?.days?.takeLast(CARRY_DAYS)?.map { d -> d to prev.cell(memberId, d)?.shiftId?.let(asTypes::get) }.orEmpty()

    /** "NN off" of the paper: the labels of the last [n] days of [prev] for [memberId] ("·" for an empty day); "" with no last month. */
    fun tail(team: Team, prev: TeamBoard?, memberId: String, n: Int = 4): String {
        val types = team.shifts.associateBy { it.id }
        return prev?.days?.takeLast(n)?.joinToString("") { d -> prev.cell(memberId, d)?.shiftId?.let(types::get)?.label?.take(2) ?: "·" }.orEmpty()
    }

    /**
     * [DutyCheck] over [carry] then [days], told only for the month: a run that began last month is told on the 1st
     * (11/28 N, 11/29 N, 12/1 D is an N→D the paper's margin was there to catch).
     */
    fun withCarry(carry: List<Pair<LocalDate, ShiftType?>>, days: List<Pair<LocalDate, ShiftType?>>, rules: RestRules): List<DutyCheck.Warning> {
        if (carry.isEmpty()) return DutyCheck.check(days, rules)
        val first = days.firstOrNull()?.first ?: return emptyList()
        return DutyCheck.check(carry + days, rules).mapNotNull { w ->
            when {
                !w.date.isBefore(first) -> w
                (w.kind == DutyCheck.Kind.LONG_RUN || w.kind == DutyCheck.Kind.MANY_NIGHTS) && days.first().second?.isOff == false -> w.copy(date = first)
                else -> null
            }
        }
    }

    fun check(team: Team, board: TeamBoard, rules: RestRules = RestRules(), prev: TeamBoard? = null, next: TeamBoard? = null): List<Sign> {
        val types = team.shifts.associateBy { it.id }
        val asTypes = types.mapValues { it.value.asType() }
        val out = ArrayList<Sign>()
        val active = board.members.filter { it.active }
        for (m in active) {
            val days = board.days.map { d -> d to board.cell(m.id, d)?.shiftId?.let(asTypes::get) }
            withCarry(carry(prev, m.id, asTypes), days, rules).forEach { w -> out += Sign(Kind.RULE, w.date, m.id, "${m.name}: ${w.text}") }
            // 주 52시간, each Monday week with a day in the month. [RVJ-30] The days of that week in last month and next month count
            // too; [RVJ-31] a 5인 미만 사업장 is not told ([RestRules.weekLimit]).
            if (rules.weekLimit) board.days.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct().forEach { mon ->
                val week = (0..6).map { mon.plusDays(it.toLong()) }
                val worked = week.mapNotNull { d -> cellAt(board, prev, next, m.id, d)?.shiftId?.let(types::get) }
                if (worked.sumOf(::minutesOf) > WEEK_LIMIT_MINUTES) out += Sign(Kind.WEEK_HOURS, week.last().coerceAtMost(board.days.last()), m.id,
                    "${m.name}: ${mon.monthValue}/${mon.dayOfMonth} 주 ${overWords(worked)}(근로기준법 제53조)")
            }
        }
        board.short(team.need).forEach { (d, missing) ->
            out += Sign(Kind.SHORT, d, null, missing.entries.joinToString(" ") { (k, n) -> "${types[k]?.label ?: "?"} ${n}명 부족" })
        }
        // 책임자 없는 근무: only once the team has said who is 책임 (a team without any is not asked).
        if (active.any { it.skill == Skill.CHARGE }) for (d in board.days) {
            val working = active.mapNotNull { m -> board.cell(m.id, d)?.shiftId?.let(types::get)?.takeIf { !it.isOff }?.let { it to m } }
            working.groupBy({ it.first }, { it.second }).forEach { (shift, people) ->
                if (people.none { it.skill == Skill.CHARGE }) out += Sign(Kind.NO_CHARGE, d, null, "${shift.label} 책임자 없음")
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.kind }))
    }

    /** One line of the 확인할 것 card: [count] signs of one kind told at once, the dangerous kinds first. */
    data class Line(val kind: Kind, val text: String, val count: Int, val first: LocalDate)

    /** Danger first: 규칙(역순·11시간·연속) and 주 52시간 are the leader's to fix; 부족 next; 책임자 없음 last, as one line. */
    val ORDER = listOf(Kind.RULE, Kind.WEEK_HOURS, Kind.SHORT, Kind.NO_CHARGE)

    /**
     * 1.38 PC 1차 (58 signs, most of them 책임자 없음, hid the N→D): the card's lines. Each RULE and WEEK_HOURS sign keeps its own line;
     * SHORT and NO_CHARGE are told once each, with how many and the first days ("책임자 없는 근무 32번 · 1일 N, 2일 D …").
     * [charges] is how many active 책임자 the team has; with only one, the 책임자 line says one more makes it shorter.
     */
    fun summary(signs: List<Sign>, dayWords: (LocalDate) -> String = { "${it.dayOfMonth}일" }, charges: Int = 0): List<Line> {
        val byKind = signs.groupBy { it.kind }
        return ORDER.flatMap { k ->
            val list = byKind[k].orEmpty().sortedBy { it.date }
            when {
                list.isEmpty() -> emptyList()
                k == Kind.RULE || k == Kind.WEEK_HOURS -> list.map { Line(k, "${dayWords(it.date)} ${it.text}", 1, it.date) }
                else -> {
                    val head = if (k == Kind.SHORT) "인원이 모자란 날 ${list.map { it.date }.distinct().size}일" else "책임자 없는 근무 ${list.size}번"
                    val some = list.take(4).joinToString(", ") { "${dayWords(it.date)} ${it.text.removeSuffix(" 책임자 없음")}" }
                    // 1.38 PC 8차: one 책임자 can't be on every shift, so the line says how it goes away.
                    val tip = if (k == Kind.NO_CHARGE && charges == 1) " · 책임자를 한 명 더 정하면 줄어요" else ""
                    listOf(Line(k, "$head · $some" + (if (list.size > 4) " …" else "") + tip, list.size, list.first().date))
                }
            }
        }
    }

    /** [TEAM-17] 공정성 표 (설계 L2 C): each member's 야간·주말·공휴일 근무와 쉬는 날 in the month. */
    data class Fair(val member: TeamMember, val nights: Int, val weekends: Int, val holidays: Int, val offs: Int, val minutes: Int)

    fun fairness(team: Team, board: TeamBoard, holidays: Set<LocalDate> = emptySet()): List<Fair> {
        val types = team.shifts.associateBy { it.id }
        return board.members.filter { it.active }.map { m ->
            val row = board.days.mapNotNull { d -> board.cell(m.id, d)?.shiftId?.let(types::get)?.let { d to it } }
            val work = row.filter { !it.second.isOff }
            Fair(m,
                nights = work.count { DutyCheck.isNight(it.second.asType()) },
                weekends = work.count { it.first.dayOfWeek == DayOfWeek.SATURDAY || it.first.dayOfWeek == DayOfWeek.SUNDAY },
                holidays = work.count { it.first in holidays },
                offs = row.count { it.second.isOff },
                minutes = work.sumOf { minutesOf(it.second) })
        }
    }

    /**
     * [TEAM-18] 교환 요청 검사 (순번 493, 출시 후 할 일 C2·C7): what trading [date] between [a] and [b] would break, as new signs only
     * (signs already there before the trade are left out), so the leader sees "이 맞바꾸기는 준호 N→D 11시간 미만" before saying yes.
     */
    fun swapCheck(team: Team, board: TeamBoard, a: String, b: String, date: LocalDate, rules: RestRules = RestRules(),
                  prev: TeamBoard? = null, next: TeamBoard? = null): List<Sign> {
        val before = check(team, board, rules, prev, next).map { it.kind to it.text }.toSet()
        return check(team, board.swap(a, b, date), rules, prev, next).filter { (it.kind to it.text) !in before }
    }
}
