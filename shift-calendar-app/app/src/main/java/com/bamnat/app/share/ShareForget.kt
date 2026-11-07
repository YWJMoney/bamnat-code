package com.bamnat.app.share

import com.bamnat.app.AppContainer
import com.bamnat.app.team.TeamReceiveSync
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * 1.39 [PRIV-06] 이 휴대폰이 보낸 정보 모두 지우기, the share server's part: every link this phone made and still holds the delete key
 * for goes from the server with that key, and the phone forgets the link with it.
 *
 * What it covers (the share-worker's kinds): 달력 공유 링크(share, and this phone's place in a 모임), 팀원 근무 링크(share),
 * 희망 근무 받기(ask), 결원 메우기·대타 요청(call), 근무 바꾸기(swap), 병동 전체 보기(board), 피곤해요 합계(tired),
 * 직원 출퇴근 링크(clock), 근로계약서 서명 링크(sign).
 *
 * What stays: links others made and this phone only opens (받은 달력), and what this phone pressed on someone else's link
 * (희망 근무·피곤해요): those are the maker's and go when they cut their link. The team, its boards, the 희망 already read in,
 * the staff and a signed contract stay on the phone: only the server copy and the address go.
 *
 * A link the server could not be reached for stays, key and all, so pressing again finishes it; [Result.left] counts those.
 * A link already gone on the server (cut, past its date, taken down) counts as cut ([ShareApi]'s deletes say so).
 */
object ShareForget {
    data class Result(val cut: Int, val left: Int) {
        /** "공유 링크 3개", for the words after 모두 지우기; null when there was none. */
        val words: String? get() = if (cut > 0) "공유 링크 ${cut}개" else null
        /** Said after the rest when some could not be cut, so the user knows to press again. */
        val leftWords: String? get() = if (left > 0) "공유 링크 ${left}개는 인터넷이 끊겨 남았어요. 연결한 뒤 다시 누르면 마저 지워요" else null
    }

    /**
     * What 모두 지우기 would cut, said before it asks: "끊기는 링크: 달력 공유 2개 · 팀원 근무 8개", with what stays on the phone for a
     * leader or a boss. Null when this phone made none, so a user with no links reads nothing about teams or staff.
     */
    suspend fun held(c: AppContainer): String? {
        if (!c.shareApi.enabled) return null
        val store = c.phaseThree
        val teams = store.teamList.first()
        val members = teams.sumOf { t -> store.members(t.id).first().count { it.linkId != null } }
        val rounds = teams.count { it.ask != null } + teams.count { it.swap != null } +
            teams.sumOf { it.calls.size } + teams.count { TeamReceiveSync.board(c.appContext, it.id) != null } +
            teams.count { TeamReceiveSync.tiredBox(c.appContext, it.id) != null }
        val places = store.workplaces.first()
        val staff = store.allStaff()
        val clocks = staff.count { it.clockId != null }
        val signs = staff.count { it.contract?.linkId != null }
        val calls = places.sumOf { it.calls.size }
        val parts = listOfNotNull(
            c.shareSync.allLinks().size.takeIf { it > 0 }?.let { "달력 공유 ${it}개" },
            members.takeIf { it > 0 }?.let { "팀원 근무 ${it}개" },
            rounds.takeIf { it > 0 }?.let { "희망·대타·바꾸기 등 팀 링크 ${it}개" },
            calls.takeIf { it > 0 }?.let { "대타 요청 ${it}개" },
            clocks.takeIf { it > 0 }?.let { "출퇴근 ${it}개" },
            signs.takeIf { it > 0 }?.let { "계약서 서명 ${it}개" },
        )
        if (parts.isEmpty()) return null
        val stays = listOfNotNull("팀 근무표와 받아 둔 희망 근무".takeIf { members + rounds > 0 },
            "직원 기록과 서명받은 계약서".takeIf { clocks + signs + calls > 0 })
        return "끊기는 링크: " + parts.joinToString(" · ") +
            (if (stays.isEmpty()) "" else ". ${stays.joinToString("·")}는 휴대폰에 그대로예요")
    }

    suspend fun all(c: AppContainer): Result {
        if (!c.shareApi.enabled) return Result(0, 0)
        var cut = 0
        var left = 0

        /** One delete: true when the server has it no more. A refusal other than offline also leaves it, to try again. */
        suspend fun gone(block: suspend () -> Unit): Boolean = try {
            block()
            cut++
            true
        } catch (e: IOException) {
            left++
            false
        } catch (e: ShareLinkException) {
            left++
            false
        }

        // 모임: this phone's link out of the group first, while its key is still here.
        c.shareGroup.code.value?.let { code ->
            val own = c.shareGroup.ownKey?.let { c.shareLinksOf(it).link.value }
            val out = own == null || runCatching { c.shareApi.groupLeave(code, own) }.isSuccess
            if (out) c.shareGroup.clear()
        }

        // 달력 공유 링크, every calendar's (SHR-13 모든 링크 끊기).
        // Counted by what is still here after: a refusal other than offline (a key the server does not take) leaves the link too.
        val calendarLinks = c.shareSync.allLinks().size
        runCatching { c.shareSync.cutAll() }
        val calendarLeft = c.shareSync.allLinks().size
        cut += (calendarLinks - calendarLeft).coerceAtLeast(0)
        left += calendarLeft

        // 팀 (리더): each member's 근무 링크, and the team's 받기·결원·바꾸기·전체 보기·피곤해요.
        val store = c.phaseThree
        for (team in store.teamList.first()) {
            for (m in store.members(team.id).first()) {
                val id = m.linkId ?: continue
                val token = m.linkToken
                if (token == null || gone { c.shareApi.delete(ShareLinkState(id, "", token, java.time.Instant.EPOCH, m.name)) }) {
                    store.saveMembers(listOf(m.copy(linkId = null, linkToken = null)))
                }
            }
            team.ask?.let { round ->
                val token = round.token
                if (token == null || gone { c.shareApi.askDelete(round.id, token) }) {
                    store.updateTeam(team.id) { t -> if (t.ask?.id == round.id) t.copy(ask = null) else t }
                }
            }
            team.swap?.let { round ->
                val token = round.token
                if (token == null || gone { c.shareApi.swapDelete(round.id, token) }) {
                    store.updateTeam(team.id) { t -> if (t.swap?.id == round.id) t.copy(swap = null) else t }
                }
            }
            val callsOut = team.calls.filter { call -> call.token?.let { t -> gone { c.shareApi.callDelete(call.id, t) } } ?: true }.map { it.id }.toSet()
            if (callsOut.isNotEmpty()) store.updateTeam(team.id) { t -> t.copy(calls = t.calls.filterNot { it.id in callsOut }) }
            TeamReceiveSync.board(c.appContext, team.id)?.let { b ->
                if (gone { c.shareApi.boardDelete(b.id, b.token) }) TeamReceiveSync.forgetBoard(c.appContext, team.id)
            }
            TeamReceiveSync.tiredBox(c.appContext, team.id)?.let { b ->
                if (gone { c.shareApi.tiredDelete(b.id, b.token) }) TeamReceiveSync.forgetTired(c.appContext, team.id)
            }
        }

        // 가게 (사장님): 대타 요청, each staff's 출퇴근 링크 and 계약서 서명 링크.
        for (place in store.workplaces.first()) {
            val callsOut = place.calls.filter { call -> call.token?.let { t -> gone { c.shareApi.callDelete(call.id, t) } } ?: true }.map { it.id }.toSet()
            if (callsOut.isNotEmpty()) store.updateWorkplace(place.id) { p -> p.copy(calls = p.calls.filterNot { it.id in callsOut }) }
        }
        for (s in store.allStaff()) {
            val clock = s.clockId
            if (clock != null) {
                val token = s.clockToken
                if (token == null || gone { c.shareApi.clockDelete(clock, token) }) store.forgetClock(s.id)
            }
            val sent = s.contract ?: continue
            val link = sent.linkId ?: continue
            val token = sent.token
            if (token == null || gone { c.shareApi.signDelete(link, token) }) {
                // The contract and its signature stay on the phone (근로기준법 제42조: 3년 보관); only the page goes.
                store.updateContract(s.id) { cur -> if (cur?.linkId == link) cur.copy(linkId = null, token = null, url = null) else cur }
            }
        }

        return Result(cut, left)
    }
}
