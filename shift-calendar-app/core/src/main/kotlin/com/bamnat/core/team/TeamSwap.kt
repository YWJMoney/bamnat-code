package com.bamnat.core.team

import com.bamnat.core.schedule.RestRules
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.38 [P3-X-02] 바꾸기 요청 (사용자 2026-11-06 "밤낮 없이는 안 될 정도로"): a member asks a coworker from their own link to trade a day,
 * the coworker says yes, the leader approves once. Who may be asked is worked out on the leader's phone with the board's own
 * checks ([TeamCheck], last month carried), so a member only ever sees trades that break nothing; the server just holds the list.
 */
data class SwapRound(
    val id: String,
    val token: String?,
    val month: YearMonth,
    /** Member ids in the server's order. */
    val people: List<String>,
    val links: List<String> = emptyList(),
    val asks: List<TeamSwap.Ask> = emptyList(),
    val openedAt: Long = 0,
    val syncedAt: Long? = null,
)

object TeamSwap {
    enum class State(val word: String) { ASKED("상대 답 기다림"), YES("상대가 좋대요"), NO("상대가 어렵대요"), CANCEL("거둠"), DONE("승인"), DENIED("리더가 거절") }

    /** One trade asked: [from] and [to] are member ids, [date] the day; [at] when it was asked (server time). */
    data class Ask(val from: String, val to: String, val date: LocalDate, val state: State, val at: Long = 0)

    /**
     * For each member, the days from [from] on and who they may trade each with: someone whose day differs (one of the two working),
     * neither cell locked, and no new 근무 규칙 or 주 52시간 sign for either of the two. Only their own two rows are checked, so a
     * ward of 30 is a moment's work.
     */
    fun options(team: Team, board: TeamBoard, rules: RestRules = RestRules(), prev: TeamBoard? = null, from: LocalDate = board.month.atDay(1)):
        Map<String, Map<LocalDate, List<String>>> {
        val off = team.shifts.filter { it.isOff }.map { it.id }.toSet()
        fun works(id: Long?) = id != null && id !in off
        val people = board.members.filter { it.active }
        val days = board.days.filter { !it.isBefore(from) }
        val out = LinkedHashMap<String, MutableMap<LocalDate, List<String>>>()
        for (i in people.indices) for (j in i + 1 until people.size) {
            val a = people[i]
            val b = people[j]
            val pair = board.copy(members = listOf(a, b), cells = board.cells.filterKeys { it.first == a.id || it.first == b.id })
            val pairPrev = prev?.let { p -> p.copy(members = listOf(a, b).filter { m -> p.members.any { it.id == m.id } }, cells = p.cells.filterKeys { it.first == a.id || it.first == b.id }) }
            val before = TeamCheck.check(team, pair, rules, pairPrev).filter { it.memberId != null && bad(it) }.map { it.kind to it.text }.toSet()
            for (d in days) {
                val ca = pair.cell(a.id, d)
                val cb = pair.cell(b.id, d)
                if (ca?.shiftId == cb?.shiftId || ca?.locked == true || cb?.locked == true) continue
                if (!works(ca?.shiftId) && !works(cb?.shiftId)) continue
                if (ca == null || cb == null) continue
                val after = TeamCheck.check(team, pair.swap(a.id, b.id, d), rules, pairPrev).filter { it.memberId != null && bad(it) }
                if (after.any { (it.kind to it.text) !in before }) continue
                out.getOrPut(a.id) { LinkedHashMap() }.merge(d, listOf(b.id), { x, y -> x + y })
                out.getOrPut(b.id) { LinkedHashMap() }.merge(d, listOf(a.id), { x, y -> x + y })
            }
        }
        return out
    }

    private fun bad(s: TeamCheck.Sign) = s.kind == TeamCheck.Kind.RULE || s.kind == TeamCheck.Kind.WEEK_HOURS

    /** The board once the leader approves: the two cells traded (a locked one is left alone, as [TeamBoard.swap] does). */
    fun apply(board: TeamBoard, ask: Ask): TeamBoard = board.swap(ask.from, ask.to, ask.date)
}
