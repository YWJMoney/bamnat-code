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
 * with the team's [RestRules]) or a 희망 휴무, and of those the one with the fewest of that kind so far, then the fewest hours
 * (공정성). What is left becomes the team's off day. A day that still lacks people is told ([Plan.short]) rather than forced.
 * [plans] tries a few orders and keeps the fairest, for "여러 안 중 고르기". Pure Kotlin, 30명×31일 well under a second.
 */
object TeamAuto {
    data class Plan(val board: TeamBoard, val filled: Int, val short: Map<LocalDate, Map<Long, Int>>, val spread: Int, val wishesKept: Int, val wishesMissed: Int)

    fun fill(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), seed: Int = 0): Plan {
        val types = team.shifts.associateBy { it.id }
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
        fun note(m: String, id: Long) {
            count.merge(m to id, 1, Int::plus)
            types[id]?.let { minutes.merge(m, TeamCheck.minutesOf(it), Int::plus) }
        }
        board.cells.values.forEach { note(it.memberId, it.shiftId) }
        fun breaks(m: TeamMember, d: LocalDate, id: Long, trial: TeamBoard): Boolean {
            val w = wish[m.id to d]
            val t = types.getValue(id)
            if (w?.kind == WishKind.OFF) return true
            if (w?.kind == WishKind.AVOID_NIGHT && DutyCheck.isNight(t.asType())) return true
            val days = (-3..3).map { d.plusDays(it.toLong()) }.filter { YearMonth.from(it) == board.month }
            val before = DutyCheck.check(days.map { it to trial.cell(m.id, it)?.shiftId?.let(types::get)?.asType() }, rules).map { it.kind to it.date }.toSet()
            val after = trial.paint(m.id, d, id, CellSource.AUTO)
            return DutyCheck.check(days.map { it to after.cell(m.id, it)?.shiftId?.let(types::get)?.asType() }, rules).any { (it.kind to it.date) !in before }
        }
        for (d in board.days) {
            for (s in work) {
                val need = team.need[s.id] ?: continue
                var have = active.count { b.cell(it.id, d)?.shiftId == s.id }
                while (have < need) {
                    val pick = active.filter { b.cell(it.id, d) == null && !breaks(it, d, s.id, b) }
                        .sortedWith(compareBy<TeamMember>({ if (wish[it.id to d]?.kind == WishKind.WORK) 0 else 1 }, { count[it.id to s.id] ?: 0 }, { minutes[it.id] ?: 0 }))
                        .firstOrNull() ?: break
                    b = b.paint(pick.id, d, s.id, CellSource.AUTO); note(pick.id, s.id); have++
                }
            }
            if (off != null) active.forEach { m -> if (b.cell(m.id, d) == null) b = b.paint(m.id, d, off.id, CellSource.AUTO) }
        }
        val nights = active.map { m -> board.days.count { d -> b.cell(m.id, d)?.shiftId?.let(types::get)?.let { DutyCheck.isNight(it.asType()) } == true } }
        val kept = wishes.count { w -> val c = b.cell(w.memberId, w.date)?.shiftId?.let(types::get)
            when (w.kind) { WishKind.OFF -> c?.isOff == true; WishKind.WORK -> c != null && !c.isOff; WishKind.AVOID_NIGHT -> c != null && !DutyCheck.isNight(c.asType()) } }
        return Plan(b, startEmpty - b.empty, b.short(team.need), (nights.maxOrNull() ?: 0) - (nights.minOrNull() ?: 0), kept, wishes.size - kept)
    }

    /** [TEAM-20] A few orders, best first: fewest short days, then wishes kept, then the evenest nights. */
    fun plans(team: Team, board: TeamBoard, wishes: List<TeamWish> = emptyList(), rules: RestRules = RestRules(), tries: Int = 4): List<Plan> =
        (0 until tries).map { fill(team, board, wishes, rules, it) }
            .distinctBy { it.board.cells }
            .sortedWith(compareBy({ it.short.values.sumOf { m -> m.values.sum() } }, { -it.wishesKept }, { it.spread }))
}
