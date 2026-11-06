package com.bamnat.core.team

import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.TeamCheck.asType
import java.time.LocalDate
import java.time.YearMonth

/** 1.38 [P3-04][TEAM-19] 희망 휴무 (순번 491, 설계 1-3 `team_wish`): what a member asked for one day. */
enum class WishKind(val label: String) { OFF("쉬고 싶어요"), WORK("일하고 싶어요"), AVOID_NIGHT("나이트 빼 주세요") }

data class TeamWish(val memberId: String, val date: LocalDate, val kind: WishKind = WishKind.OFF)

/**
 * [TEAM-19] 희망 휴무 without a server (설계 L2 "링크·QR·파일" of which the 파일·글 way needs no 처리방침 3.3): a member's app writes
 * a line the member sends by messenger, and the leader pastes what came. "[밤낮 희망] 11월 3 7 15 쉬고 / 20 나이트 빼고".
 * A leader's own note works too: "민지 3, 7, 15" — a name of the team then the days.
 */
object TeamWishText {
    const val TAG = "[밤낮 희망]"

    fun write(month: YearMonth, wishes: List<TeamWish>, name: String = ""): String = buildString {
        append(TAG); if (name.isNotBlank()) append(' ').append(name)
        append(' ').append(month.monthValue).append("월")
        WishKind.entries.forEach { k ->
            val days = wishes.filter { it.kind == k && YearMonth.from(it.date) == month }.map { it.date.dayOfMonth }.sorted()
            if (days.isNotEmpty()) append(' ').append(days.joinToString(" ")).append(' ').append(WORD.getValue(k)).append(" /")
        }
    }.trimEnd(' ', '/')

    private val WORD = mapOf(WishKind.OFF to "쉬고", WishKind.WORK to "일하고", WishKind.AVOID_NIGHT to "나이트 빼고")

    /**
     * Each line of [text] for the member whose name it holds (the longest name found wins, so "민지2" is not "민지"); a line
     * without a team name goes to [fallback] when given. Days outside [month] or past its end are left out.
     */
    fun read(text: String, month: YearMonth, members: List<TeamMember>, fallback: String? = null): List<TeamWish> {
        val out = LinkedHashSet<TeamWish>()
        for (raw in text.lines()) {
            val line = raw.replace(TAG, " ").trim()
            if (line.isEmpty()) continue
            val who = members.filter { it.name.isNotBlank() && line.contains(it.name) }.maxByOrNull { it.name.length }?.id ?: fallback ?: continue
            val body = members.firstOrNull { it.id == who }?.name?.let { line.replace(it, " ") } ?: line
            // Numbers wait until a kind word says what they are ("3 7 쉬고 20 나이트 빼고"); numbers left at the end are 쉬는 날.
            val pending = ArrayList<Int>()
            fun flush(k: WishKind) { pending.filter { it in 1..month.lengthOfMonth() }.forEach { out += TeamWish(who, month.atDay(it), k) }; pending.clear() }
            Regex("""(\d{1,2})\s*월|(\d{1,2})|나이트\s*빼|야간\s*빼|일하고|근무하고|쉬고|휴무|오프|(?i:off)""").findAll(body).forEach { m ->
                when {
                    m.groupValues[1].isNotEmpty() -> Unit
                    m.groupValues[2].isNotEmpty() -> pending += m.groupValues[2].toInt()
                    m.value.startsWith("나이트") || m.value.startsWith("야간") -> flush(WishKind.AVOID_NIGHT)
                    m.value.startsWith("일하") || m.value.startsWith("근무") -> flush(WishKind.WORK)
                    else -> flush(WishKind.OFF)
                }
            }
            flush(WishKind.OFF)
        }
        return out.toList()
    }
}

/**
 * [TEAM-20] L3 자동 채우기 (순번 495·496, 설계 2-1 [135]): only the empty cells, never a locked or painted one, so the leader's
 * hand always wins. Each day each kind of [Team.need] is filled by whoever can take it without breaking the rules ([DutyCheck]
 * over the member's whole month with the team's [RestRules]: 연속 근무, 나이트 뒤 쉼, 11시간, 월 나이트), 주 52시간 (근로기준법
 * 제53조) or a 희망 휴무, and of those the one with the fewest hours so far, then the fewest of that kind (공정성: 쉬는 날이 고르게).
 * What is left becomes the team's off day. A day that still lacks people is told ([Plan.short]) rather than forced: the rules
 * and the 52 hours come before the head count.
 * 1.38 PC 2차 (4a5c3828): the check used to look three days either side only, so a 24일 연속·주 84시간 row went in as "부족 없음".
 * [plans] tries a few orders and keeps the safest, then the fullest, for "여러 안 중 고르기". Pure Kotlin, 30명×31일 in about a second.
 */
object TeamAuto {
    data class Plan(
        val board: TeamBoard, val filled: Int, val short: Map<LocalDate, Map<Long, Int>>, val spread: Int, val wishesKept: Int, val wishesMissed: Int,
        /** 주 52시간을 넘는 주 (사람·주 하나에 1). */
        val overWeeks: Int = 0,
        /** The other rule signs ([TeamCheck.Kind.RULE]): 연속 근무, 휴식, 나이트 뒤 쉼. */
        val ruleSigns: Int = 0,
        /** The longest run of work days anyone has. */
        val maxRun: Int = 0,
        /** Hours of the month, most against least. */
        val hourSpread: Int = 0,
    ) {
        val danger: Int get() = overWeeks + ruleSigns
        val shortPeople: Int get() = short.values.sumOf { it.values.sum() }
    }

    private fun weekOf(d: LocalDate): LocalDate = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    fun fill(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), seed: Int = 0): Plan {
        val types = team.shifts.associateBy { it.id }
        val asTypes = types.mapValues { it.value.asType() }
        val off = team.shifts.firstOrNull { it.isOff }
        val work = team.shifts.filter { !it.isOff }
        val active = board.members.filter { it.active }.let { l -> if (seed == 0) l else l.shuffled(java.util.Random(seed.toLong())) }
        val wish = wishes.associateBy { it.memberId to it.date }
        var b = board
        val startEmpty = board.empty
        // Wishes for a day off land first, where the cell is still empty.
        if (off != null) wishes.filter { it.kind == WishKind.OFF }.forEach { w -> if (b.cell(w.memberId, w.date) == null) b = b.paint(w.memberId, w.date, off.id, CellSource.AUTO) }
        val count = HashMap<Pair<String, Long>, Int>()
        val minutes = HashMap<String, Int>()
        val weekends = HashMap<String, Int>()
        val week = HashMap<Pair<String, LocalDate>, Int>()
        fun note(m: String, d: LocalDate, id: Long) {
            count.merge(m to id, 1, Int::plus)
            if (types[id]?.isOff == false && d.dayOfWeek.value >= 6) weekends.merge(m, 1, Int::plus)
            types[id]?.let { val min = TeamCheck.minutesOf(it); minutes.merge(m, min, Int::plus); week.merge(m to weekOf(d), min, Int::plus) }
        }
        b.cells.values.forEach { note(it.memberId, it.date, it.shiftId) }
        fun row(trial: TeamBoard, m: String) = board.days.map { it to trial.cell(m, it)?.shiftId?.let(asTypes::get) }
        // The member's signs now, kept while their row does not change, so each try is checked against what it adds.
        // The text too, so a run already too long that grows ("7일 연속" → "8일 연속") counts as new.
        val before = HashMap<String, Set<String>>()
        fun signs(trial: TeamBoard, m: String) = DutyCheck.check(row(trial, m), rules).map { "${it.kind} ${it.date} ${it.text}" }.toSet()
        fun breaks(m: TeamMember, d: LocalDate, id: Long, trial: TeamBoard): Boolean {
            val w = wish[m.id to d]
            val t = types.getValue(id)
            if (w?.kind == WishKind.OFF) return true
            if (w?.kind == WishKind.AVOID_NIGHT && DutyCheck.isNight(t.asType())) return true
            if ((week[m.id to weekOf(d)] ?: 0) + TeamCheck.minutesOf(t) > TeamCheck.WEEK_LIMIT_MINUTES) return true
            val had = before.getOrPut(m.id) { signs(trial, m.id) }
            return (signs(trial.paint(m.id, d, id, CellSource.AUTO), m.id) - had).isNotEmpty()
        }
        // 1.38 PC 6db1056b (나이트 15번 넷·0번 둘, 네 안이 같음): one score for who takes a kind — the hours so far, each of that kind
        // already taken as a half shift more (so nights and evenings go round), weekends worked the same, and per 안 [seed] a small
        // different nudge so each 안 is really another way. (Nights first each day was tried: it spread nights but left 38 short, not 16.)
        val jitter = java.util.Random(seed * 7919L + 1)
        val nudge = active.associate { it.id to if (seed == 0) 0.0 else jitter.nextDouble() * 6.0 }
        fun score(m: TeamMember, d: LocalDate, s: TeamShift): Double {
            val hours = (minutes[m.id] ?: 0) / 60.0
            val kind = (count[m.id to s.id] ?: 0) * (TeamCheck.minutesOf(s) / 60.0) * (if (DutyCheck.isNight(s.asType())) 1.5 else 0.5)
            val weekend = if (d.dayOfWeek.value >= 6) (weekends[m.id] ?: 0) * 6.0 else 0.0
            return hours + kind + weekend + (nudge[m.id] ?: 0.0)
        }
        val order = work
        for (d in board.days) {
            for (s in order) {
                val need = team.need[s.id] ?: continue
                var have = active.count { b.cell(it.id, d)?.shiftId == s.id }
                while (have < need) {
                    val pick = active.filter { b.cell(it.id, d) == null && !breaks(it, d, s.id, b) }
                        .sortedWith(compareBy<TeamMember>({ if (wish[it.id to d]?.kind == WishKind.WORK) 0 else 1 }, { score(it, d, s) }))
                        .firstOrNull() ?: break
                    b = b.paint(pick.id, d, s.id, CellSource.AUTO); note(pick.id, d, s.id); before.remove(pick.id); have++
                }
            }
            if (off != null) active.forEach { m -> if (b.cell(m.id, d) == null) { b = b.paint(m.id, d, off.id, CellSource.AUTO); before.remove(m.id) } }
        }
        return measure(team, board, b, startEmpty, wishes, rules)
    }

    /** How a filled board stands: short days, wishes, and the dangers the 안 line shows before the leader picks it. */
    fun measure(team: Team, start: TeamBoard, b: TeamBoard, startEmpty: Int, wishes: List<TeamWish>, rules: RestRules): Plan {
        val types = team.shifts.associateBy { it.id }
        val active = b.members.filter { it.active }
        val nights = active.map { m -> b.days.count { d -> b.cell(m.id, d)?.shiftId?.let(types::get)?.let { DutyCheck.isNight(it.asType()) } == true } }
        val kept = wishes.count { w -> val c = b.cell(w.memberId, w.date)?.shiftId?.let(types::get)
            when (w.kind) { WishKind.OFF -> c?.isOff == true; WishKind.WORK -> c != null && !c.isOff; WishKind.AVOID_NIGHT -> c != null && !DutyCheck.isNight(c.asType()) } }
        val signs = TeamCheck.check(team, b, rules)
        val runs = active.map { m ->
            var best = 0; var cur = 0
            b.days.forEach { d -> if (b.cell(m.id, d)?.shiftId?.let(types::get)?.isOff == false) { cur++; best = maxOf(best, cur) } else cur = 0 }
            best
        }
        val hours = active.map { m -> b.days.sumOf { d -> b.cell(m.id, d)?.shiftId?.let(types::get)?.let(TeamCheck::minutesOf) ?: 0 } / 60 }
        return Plan(b, startEmpty - b.empty, b.short(team.need), (nights.maxOrNull() ?: 0) - (nights.minOrNull() ?: 0), kept, wishes.size - kept,
            overWeeks = signs.count { it.kind == TeamCheck.Kind.WEEK_HOURS }, ruleSigns = signs.count { it.kind == TeamCheck.Kind.RULE },
            maxRun = runs.maxOrNull() ?: 0, hourSpread = (hours.maxOrNull() ?: 0) - (hours.minOrNull() ?: 0))
    }

    /**
     * [TEAM-20] A few orders, best first: no danger (52시간·연속·휴식) first, then the fewest short, wishes kept, the evenest hours
     * and nights. A plan only adds what the board's own signs did not have, so a leader's painted danger counts in every 안 alike.
     */
    fun plans(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), tries: Int = 8, keep: Int = 4): List<Plan> =
        (0 until tries).map { fill(team, board, wishes, rules, it) }
            .sortedWith(compareBy({ it.danger }, { it.shortPeople }, { -it.wishesKept }, { it.hourSpread + it.spread * 4 }))
            // 1.38 PC 6db1056b: 안 that read the same on their line are one choice, not four.
            .distinctBy { listOf(it.danger, it.shortPeople, it.short.size, it.wishesKept, it.hourSpread, it.spread, it.maxRun) }
            .take(keep)

    /**
     * The hours the month asks of each person on average: [Team.need] × each kind's hours over the active members, a week.
     * Above 52 the need cannot be met inside the law with these people; close to it, short days are likely.
     */
    fun weekHoursNeeded(team: Team, board: TeamBoard): Double {
        val people = board.members.count { it.active }
        if (people == 0) return 0.0
        val perDay = team.shifts.filter { !it.isOff }.sumOf { (team.need[it.id] ?: 0) * TeamCheck.minutesOf(it) }
        return perDay * 7.0 / people / 60.0
    }
}
