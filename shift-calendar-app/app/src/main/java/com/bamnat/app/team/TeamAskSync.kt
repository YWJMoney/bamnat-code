package com.bamnat.app.team

import com.bamnat.app.AppContainer
import com.bamnat.core.team.AskRound
import com.bamnat.core.team.AskRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamWish
import com.bamnat.core.time.Today
import java.time.YearMonth

/**
 * 1.38 [P3-L-14] 신청 받기 between the leader's phone and the share server: open a round with a link per member, read what came
 * into the month's 희망 (via LINK, replacing the round's last read), close or end it. The team row keeps the round ([Team.ask]).
 */
object TeamAskSync {
    suspend fun open(c: AppContainer, team: Team, month: YearMonth, rules: AskRules, members: List<TeamMember>): Team {
        team.ask?.takeIf { it.token != null }?.let { old -> runCatching { c.shareApi.askDelete(old.id, old.token!!) } }
        val made = c.shareApi.askCreate(team.name, month, rules, team.shifts.filter { !it.isOff }, members.map { it.name }, leaveLeft(c, team, month, members))
        val round = AskRound(made.id, made.token, month, rules, members.map { it.id }.zip(made.urls).toMap(LinkedHashMap()), openedAt = Today.millis())
        val next = c.phaseThree.updateTeam(team.id) { it.copy(ask = round) } ?: team.copy(ask = round)
        c.phaseThree.putLinkWishes(team.id, month, emptyList())
        return next
    }

    /** Reads the round into the month's 희망; null when there is no round to read (none, or its key is not on this phone). */
    suspend fun pull(c: AppContainer, team: Team): Team? {
        val round = team.ask ?: return null
        val token = round.token ?: return null
        val got = c.shareApi.askRead(round.id, token)
        val ids = round.links.keys.toList()
        val wishes = got.wishes.mapNotNull { w -> ids.getOrNull(w.m)?.let { TeamWish(it, w.date, w.kind, w.shiftId, w.at.toEpochMilli()) } }
        c.phaseThree.putLinkWishes(team.id, round.month, wishes)
        val read = round.copy(
            notes = got.notes.mapNotNull { (m, n) -> ids.getOrNull(m)?.let { it to n } }.filter { it.second.isNotBlank() }.toMap(),
            seen = got.seen.mapNotNull(ids::getOrNull).toSet(), closed = got.closed, syncedAt = Today.millis())
        return c.phaseThree.updateTeam(team.id) { t -> if (t.ask?.id == round.id) t.copy(ask = read) else t }
    }

    suspend fun close(c: AppContainer, team: Team, closed: Boolean): Team {
        val round = team.ask ?: return team
        round.token?.let { c.shareApi.askChange(round.id, it, closed = closed) }
        return c.phaseThree.updateTeam(team.id) { t -> t.ask?.takeIf { it.id == round.id }?.let { a -> t.copy(ask = a.copy(closed = closed)) } ?: t } ?: team
    }

    /** Ends the round: the server forgets every request; what was read stays in the month's 희망 on this phone. */
    suspend fun end(c: AppContainer, team: Team): Team {
        val round = team.ask ?: return team
        round.token?.let { c.shareApi.askDelete(round.id, it) }
        return c.phaseThree.updateTeam(team.id) { t -> if (t.ask?.id == round.id) t.copy(ask = null) else t } ?: team
    }

    /**
     * 1.38 [P3-X-05] 남은 연차 per member on the 1st of [month]: the law's leave from their 입사일 less the 연차·반차 painted on the
     * team's boards of the two years before (a shift named 연차 or 반차). Null for a member without an 입사일.
     */
    suspend fun leaveLeft(c: AppContainer, team: Team, month: YearMonth, members: List<TeamMember>): List<Double?> {
        if (members.none { it.hiredOn != null }) return members.map { null }
        val leaveIds = team.shifts.filter { it.label.contains("연차") || it.name.contains("연차") }.map { it.id }.toSet()
        val halfIds = team.shifts.filter { it.label.contains("반차") || it.name.contains("반차") }.map { it.id }.toSet()
        val boards = (1..24L).map { c.phaseThree.boardNow(team.id, month.minusMonths(it)) }
        return members.map { m ->
            val hire = m.hiredOn ?: return@map null
            val used = boards.flatMap { b -> b.days.mapNotNull { d -> b.cell(m.id, d)?.shiftId?.let { id -> if (id in leaveIds) d to 1.0 else if (id in halfIds) d to 0.5 else null } } }.toMap()
            com.bamnat.core.leave.LeaveLeft.of(hire, used, month.atDay(1)).left
        }
    }

    /** The message that sends every member their link at once (메신저 한 번에). */
    fun message(team: Team, round: AskRound, members: List<TeamMember>): String = buildString {
        append("${team.name} ${round.month.monthValue}월 근무 신청이에요. 내 이름 옆 링크를 눌러 쉬고 싶은 날을 골라 주세요.")
        val caps = listOfNotNull(round.rules.weekdayCap.takeIf { it > 0 }?.let { "평일 하루 ${it}명" }, round.rules.weekendCap.takeIf { it > 0 }?.let { "주말 하루 ${it}명" })
        if (caps.isNotEmpty()) append(" (${caps.joinToString(", ")}까지${if (round.rules.firstCome) ", 먼저 신청한 순" else ""})")
        append('\n')
        members.forEach { m -> round.links[m.id]?.let { append("\n${m.name}: $it") } }
    }
}
