package com.bamnat.app.team

import com.bamnat.app.AppContainer
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.SwapRound
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamSwap
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 1.38 [P3-X-02] 바꾸기 요청 between the leader's phone and the share server. The phone works out who may trade which day
 * ([TeamSwap.options]) and sends that with everyone's row; members ask and answer on their links; the leader approves here and
 * the new rows go up at once, so no one is offered a trade the approved one has made unsafe.
 */
object TeamSwapSync {
    /** Rows of labels and the trades allowed, in [people]'s order, from today on. */
    suspend fun payload(team: Team, board: TeamBoard, people: List<String>, rules: RestRules, prev: TeamBoard?, today: LocalDate = Today.date()):
        Pair<List<List<String>>, Map<Int, Map<Int, List<Int>>>> = withContext(Dispatchers.Default) {
        val labels = team.shifts.associate { it.id to it.label }
        val rows = people.map { id -> board.days.map { d -> board.cell(id, d)?.shiftId?.let(labels::get).orEmpty() } }
        val from = maxOf(today.plusDays(1), board.month.atDay(1))
        val options = TeamSwap.options(team, board.copy(members = board.members.filter { it.id in people }), rules, prev, from)
        val index = people.withIndex().associate { it.value to it.index }
        val ok = options.mapNotNull { (id, days) ->
            index[id]?.let { p -> p to days.mapKeys { it.key.dayOfMonth }.mapValues { (_, qs) -> qs.mapNotNull(index::get) }.filterValues { it.isNotEmpty() } }
        }.toMap()
        rows to ok
    }

    suspend fun open(c: AppContainer, team: Team, board: TeamBoard, members: List<TeamMember>, rules: RestRules, prev: TeamBoard?): Team {
        team.swap?.takeIf { it.token != null }?.let { old -> runCatching { c.shareApi.swapDelete(old.id, old.token!!) } }
        val people = members.map { it.id }
        val (rows, ok) = payload(team, board, people, rules, prev)
        val made = c.shareApi.swapCreate(team.name, board.month, members.map { it.name }, rows, ok)
        val round = SwapRound(made.id, made.token, board.month, people, made.urls, openedAt = Today.millis())
        return c.phaseThree.updateTeam(team.id) { it.copy(swap = round) } ?: team.copy(swap = round)
    }

    private fun state(s: String) = when (s) {
        "asked" -> TeamSwap.State.ASKED; "yes" -> TeamSwap.State.YES; "no" -> TeamSwap.State.NO
        "done" -> TeamSwap.State.DONE; "denied" -> TeamSwap.State.DENIED; else -> TeamSwap.State.CANCEL
    }

    suspend fun pull(c: AppContainer, team: Team): Team? {
        val round = team.swap ?: return null
        val token = round.token ?: return null
        val got = c.shareApi.swapRead(round.id, token)
        val asks = got.asks.mapNotNull { a ->
            val from = round.people.getOrNull(a.p) ?: return@mapNotNull null
            val to = round.people.getOrNull(a.q) ?: return@mapNotNull null
            if (a.d !in 1..round.month.lengthOfMonth()) return@mapNotNull null
            TeamSwap.Ask(from, to, round.month.atDay(a.d), state(a.state), a.at.toEpochMilli())
        }
        return c.phaseThree.updateTeam(team.id) { t -> if (t.swap?.id == round.id) t.copy(swap = round.copy(asks = asks, syncedAt = Today.millis())) else t }
    }

    /**
     * The leader's word on [ask]; [after] is the board once it is applied (or as it is, when denied). The new rows and trades go up
     * with it.
     */
    suspend fun decide(c: AppContainer, team: Team, ask: TeamSwap.Ask, yes: Boolean, after: TeamBoard, rules: RestRules, prev: TeamBoard?): Team {
        val round = team.swap ?: return team
        val token = round.token ?: return team
        val p = round.people.indexOf(ask.from)
        val (rows, ok) = payload(team, after, round.people, rules, prev)
        c.shareApi.swapChange(round.id, token, rows, ok, listOf(Triple(p, ask.date.dayOfMonth, yes)))
        val st = if (yes) TeamSwap.State.DONE else TeamSwap.State.DENIED
        return c.phaseThree.updateTeam(team.id) { t ->
            t.swap?.takeIf { it.id == round.id }?.let { s -> t.copy(swap = s.copy(asks = s.asks.map { if (it == ask) it.copy(state = st) else it })) } ?: t
        } ?: team
    }

    /** The board changed by hand: the members' links get the new rows and trades. */
    suspend fun refresh(c: AppContainer, team: Team, board: TeamBoard, rules: RestRules, prev: TeamBoard?) {
        val round = team.swap ?: return
        val token = round.token ?: return
        val (rows, ok) = payload(team, board, round.people, rules, prev)
        c.shareApi.swapChange(round.id, token, rows, ok)
    }

    suspend fun end(c: AppContainer, team: Team): Team {
        val round = team.swap ?: return team
        round.token?.let { t -> runCatching { c.shareApi.swapDelete(round.id, t) } }
        return c.phaseThree.updateTeam(team.id) { t -> if (t.swap?.id == round.id) t.copy(swap = null) else t } ?: team
    }

    fun message(team: Team, round: SwapRound, members: List<TeamMember>): String = buildString {
        append("${team.name} ${round.month.monthValue}월 근무 바꾸기 링크예요. 내 이름 옆 링크에서 바꾸고 싶은 날과 사람을 고르면, 그분이 좋다고 한 뒤 제가 승인해요.")
        append(" 쉬는 시간·주 52시간 규칙에 맞는 사람만 보여요.\n")
        val names = members.associateBy { it.id }
        round.people.forEachIndexed { i, id -> round.links.getOrNull(i)?.let { append("\n${names[id]?.name ?: "?"}: $it") } }
    }
}
