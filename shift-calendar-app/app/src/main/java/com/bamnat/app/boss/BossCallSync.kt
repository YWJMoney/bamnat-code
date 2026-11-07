package com.bamnat.app.boss

import com.bamnat.app.AppContainer
import com.bamnat.app.team.TeamCallSync
import com.bamnat.app.ui.Fmt
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffShift

/**
 * 1.38 [P3-X-01] 사장님 대타 요청: the 리더's 결원 메우기 link for a shop. "11/7(토) 10:00~18:00 대신 일할 수 있나요?" to a few staff at
 * once, their 예/어려워요, and the 대근 row once the boss picks one. [TeamCall.gap] holds the staff id and the day (shift 0).
 */
object BossCallSync {
    fun title(gap: TeamCover.Gap, shift: StaffShift): String =
        "${gap.date.monthValue}/${gap.date.dayOfMonth}(${Fmt.dayOfWeekShort(gap.date.dayOfWeek)}) ${Fmt.time(shift.start)}~${Fmt.time(shift.end)} 대신 일할 수 있나요?"

    suspend fun open(c: AppContainer, place: Workplace, gap: TeamCover.Gap, shift: StaffShift, people: List<StaffMember>): Pair<Workplace, TeamCall> {
        val made = c.shareApi.callCreate(place.name, title(gap, shift), "먼저 '일할 수 있어요'를 누른 사람 중에서 사장님이 정해 알려 드려요.",
            people.map { it.name.ifBlank { "직원" } }, TeamCallSync.hours(gap))
        val call = TeamCall(made.id, made.token, gap, people.map { it.id }, made.urls, createdAt = Today.millis(), expiresAt = made.expiresAt.toEpochMilli())
        val next = c.phaseThree.updateWorkplace(place.id) { p -> p.copy(calls = TeamCallSync.tidy(p.calls.filterNot { it.gap == gap && it.taken == null }) + call) }
            ?: place.copy(calls = place.calls + call)
        return next to call
    }

    suspend fun pull(c: AppContainer, placeId: String, id: String) {
        val call = c.phaseThree.workplaceNow(placeId)?.calls?.firstOrNull { it.id == id } ?: return
        val token = call.token ?: return
        val got = c.shareApi.callRead(id, token)
        val answers = got.answers.mapNotNull { (p, yes) -> call.people.getOrNull(p)?.let { it to yes } }.toMap()
        val seen = got.seen.mapNotNull(call.people::getOrNull).toSet()
        c.phaseThree.updateWorkplace(placeId) { p -> p.copy(calls = p.calls.map { if (it.id == id) it.copy(answers = answers, seen = seen) else it }) }
    }

    suspend fun take(c: AppContainer, placeId: String, id: String, staffId: String) {
        val call = c.phaseThree.workplaceNow(placeId)?.calls?.firstOrNull { it.id == id } ?: return
        call.token?.let { t -> runCatching { c.shareApi.callTake(id, t, call.people.indexOf(staffId).takeIf { it >= 0 }) } }
        c.phaseThree.updateWorkplace(placeId) { p -> p.copy(calls = p.calls.map { if (it.id == id) it.copy(taken = staffId) else it }) }
    }

    suspend fun end(c: AppContainer, placeId: String, id: String) {
        val call = c.phaseThree.workplaceNow(placeId)?.calls?.firstOrNull { it.id == id } ?: return
        call.token?.let { t -> runCatching { c.shareApi.callDelete(id, t) } }
        c.phaseThree.updateWorkplace(placeId) { p -> p.copy(calls = p.calls.filterNot { it.id == id }) }
    }

    fun message(gap: TeamCover.Gap, shift: StaffShift, call: TeamCall, staff: List<StaffMember>): String = buildString {
        append(title(gap, shift)).append('\n').append("내 이름 옆 링크에서 '들어갈 수 있어요' 또는 '어려워요'를 눌러 주세요.\n")
        val names = staff.associateBy { it.id }
        call.people.forEachIndexed { i, id -> call.urls.getOrNull(i)?.let { append("\n${names[id]?.name?.ifBlank { "직원" } ?: "?"}: $it") } }
    }
}
