package com.bamnat.app.team

import com.bamnat.app.AppContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.team.TeamMember
import com.bamnat.core.time.Today

/**
 * 1.38 [P3-X-01] 결원 메우기 between the leader's phone and the share server: ask a few people at once "이날 대신 들어갈 수 있나요?",
 * read their 예/어려워요, and close it once the leader has put someone in. The team row keeps the asks ([Team.calls]); an ask past its
 * day or settled more than a day ago is dropped from the row the next time the row is written here.
 */
object TeamCallSync {
    /** How long a link answers: until the end of the day of the gap, at least 3 hours, at most a week (the server's cap). */
    fun hours(gap: TeamCover.Gap, now: Long = Today.millis()): Int {
        val end = gap.date.plusDays(1).atStartOfDay(Today.zone()).toInstant().toEpochMilli()
        return (((end - now) / 3_600_000L).toInt() + 1).coerceIn(3, 168)
    }

    fun title(team: Team, gap: TeamCover.Gap): String {
        val s = team.shifts.firstOrNull { it.id == gap.shiftId }
        return "${gap.date.monthValue}/${gap.date.dayOfMonth}(${Fmt.dayOfWeekShort(gap.date.dayOfWeek)}) ${s?.name ?: "근무"} 대신 들어갈 수 있나요?"
    }

    fun detail(team: Team, gap: TeamCover.Gap): String {
        val s = team.shifts.firstOrNull { it.id == gap.shiftId }
        val time = if (s?.start != null && s.end != null) "${Fmt.timeRange(s.start!!, s.end!!)} · " else ""
        return "${time}먼저 '들어갈 수 있어요'를 누른 사람 중에서 ${team.name} 리더가 정해 알려 드려요."
    }

    suspend fun open(c: AppContainer, team: Team, gap: TeamCover.Gap, people: List<TeamMember>): Pair<Team, TeamCall> {
        val made = c.shareApi.callCreate(team.name, title(team, gap), detail(team, gap), people.map { it.name }, hours(gap))
        val call = TeamCall(made.id, made.token, gap, people.map { it.id }, made.urls, createdAt = Today.millis(), expiresAt = made.expiresAt.toEpochMilli())
        val next = c.phaseThree.updateTeam(team.id) { t -> t.copy(calls = tidy(t.calls.filterNot { it.gap == gap && it.taken == null }) + call) }
            ?: team.copy(calls = team.calls + call)
        return next to call
    }

    /** Reads the answers in; null when the ask is not on this phone or has no key. */
    suspend fun pull(c: AppContainer, team: Team, id: String): Team? {
        val call = team.calls.firstOrNull { it.id == id } ?: return null
        val token = call.token ?: return null
        val got = c.shareApi.callRead(id, token)
        val answers = got.answers.mapNotNull { (p, yes) -> call.people.getOrNull(p)?.let { it to yes } }.toMap()
        val seen = got.seen.mapNotNull(call.people::getOrNull).toSet()
        return c.phaseThree.updateTeam(team.id) { t -> t.copy(calls = t.calls.map { if (it.id == id) it.copy(answers = answers, seen = seen) else it }) }
    }

    /** The leader put [memberId] in: the page tells the others it is filled. The board itself is painted by the caller. */
    suspend fun take(c: AppContainer, team: Team, id: String, memberId: String): Team {
        val call = team.calls.firstOrNull { it.id == id } ?: return team
        val p = call.people.indexOf(memberId).takeIf { it >= 0 }
        call.token?.let { t -> runCatching { c.shareApi.callTake(id, t, p) } }
        return c.phaseThree.updateTeam(team.id) { t -> t.copy(calls = t.calls.map { if (it.id == id) it.copy(taken = memberId) else it }) } ?: team
    }

    /** Takes the ask back: the server forgets it and its answers. */
    suspend fun end(c: AppContainer, team: Team, id: String): Team {
        val call = team.calls.firstOrNull { it.id == id } ?: return team
        call.token?.let { t -> runCatching { c.shareApi.callDelete(id, t) } }
        return c.phaseThree.updateTeam(team.id) { t -> t.copy(calls = t.calls.filterNot { it.id == id }) } ?: team
    }

    /** The message with each person's own link (메신저 한 번에, or one by one). */
    fun message(team: Team, call: TeamCall, members: List<TeamMember>): String = buildString {
        append(title(team, call.gap)).append('\n').append("내 이름 옆 링크에서 '들어갈 수 있어요' 또는 '어려워요'를 눌러 주세요.\n")
        val names = members.associateBy { it.id }
        call.people.forEachIndexed { i, id -> call.urls.getOrNull(i)?.let { append("\n${names[id]?.name ?: "?"}: $it") } }
    }

    /** Asks still worth keeping on the row: not yet past, or settled less than a day ago. */
    fun tidy(calls: List<TeamCall>, now: Long = Today.millis()): List<TeamCall> =
        calls.filter { it.expiresAt == 0L || it.expiresAt + 86_400_000L > now }.takeLast(20)
}
