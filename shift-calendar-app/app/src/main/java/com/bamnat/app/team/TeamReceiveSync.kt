package com.bamnat.app.team

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamReceive
import org.json.JSONObject
import java.time.Instant

/**
 * 1.38 [P3-L-15] 받기: 봤어요 (who opened their link since the last 확정) and 병동 전체 보기 (one link with the whole table, only when the
 * leader turns it on). The table link lives on this phone only: after a restore the leader makes it again with one press.
 */
object TeamReceiveSync {
    data class Board(val id: String, val token: String, val url: String)

    private fun prefs(c: Context) = c.getSharedPreferences("team_board_links", Context.MODE_PRIVATE)

    fun board(c: Context, teamId: String): Board? = runCatching {
        prefs(c).getString(teamId, null)?.let { JSONObject(it) }?.let { Board(it.getString("id"), it.getString("token"), it.getString("url")) }
    }.getOrNull()

    private fun keep(c: Context, teamId: String, b: Board?) {
        prefs(c).edit().apply { if (b == null) remove(teamId) else putString(teamId, JSONObject().put("id", b.id).put("token", b.token).put("url", b.url).toString()) }.apply()
    }

    /** 병동 전체 보기 on: the table as confirmed goes up (the same link each time). */
    suspend fun putBoard(app: AppContainer, context: Context, team: Team, b: TeamBoard, members: List<TeamMember>): Board {
        val names = members.map { it.name.take(20) }
        val rows = TeamReceive.rows(team, b, members)
        val colors = TeamReceive.colors(team)
        board(context, team.id)?.let { now ->
            val ok = runCatching { app.shareApi.boardUpdate(now.id, now.token, team.name.take(30), b.month, names, rows, colors) }
            if (ok.isSuccess) return now
            if ((ok.exceptionOrNull() as? com.bamnat.app.share.ShareLinkException)?.message != "not_found") throw ok.exceptionOrNull()!!
        }
        val made = app.shareApi.boardCreate(team.name.take(30), b.month, names, rows, colors)
        return Board(made.id, made.token, made.url).also { keep(context, team.id, it) }
    }

    suspend fun dropBoard(app: AppContainer, context: Context, teamId: String) {
        board(context, teamId)?.let { runCatching { app.shareApi.boardDelete(it.id, it.token) } }
        keep(context, teamId, null)
    }

    /** 1.39 [PRIV-06] 모두 지우기 cut the table link on the server: the phone forgets it (병동 전체 보기 then reads off). */
    fun forgetBoard(c: Context, teamId: String) = keep(c, teamId, null)

    /** 1.39 G2: what one member's link says about 봤어요. */
    sealed interface LinkSeen {
        data class At(val at: Instant) : LinkSeen
        data object Not : LinkSeen
        /** The server has no such link (ran out, cut, taken down, or the server lost it) or it is not this phone's to read. */
        data object Gone : LinkSeen
        /** Not reached (no internet); says nothing about the link. */
        data object Unknown : LinkSeen
    }

    /**
     * Who opened their link since it last changed; a member without a link is left out. 1.39 G2 (PC a483ca15): a link the server no
     * longer has used to drop out of the map as if it were never sent, so a team whose links were all gone read "0/0명 · 모두 봤어요".
     */
    suspend fun seen(app: AppContainer, members: List<TeamMember>): Map<String, LinkSeen> = members.mapNotNull { m ->
        val id = m.linkId ?: return@mapNotNull null
        val token = m.linkToken ?: return@mapNotNull null
        m.id to try {
            app.shareApi.seen(id, token)?.let { LinkSeen.At(it) } ?: LinkSeen.Not
        } catch (e: com.bamnat.app.share.ShareLinkException) {
            if (e.message == "not_found" || e.message == "forbidden") LinkSeen.Gone else LinkSeen.Unknown
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            LinkSeen.Unknown
        }
    }.toMap()

    /** The 봤어요 line under the board, and who each button is for. */
    data class SeenLine(val text: String, val notSeen: List<TeamMember>, val gone: List<TeamMember>, val unread: Int)

    fun seenLine(version: Int, members: List<TeamMember>, got: Map<String, LinkSeen>): SeenLine {
        val asked = members.filter { it.id in got }
        val gone = asked.filter { got[it.id] == LinkSeen.Gone }
        val read = asked.filter { got[it.id] is LinkSeen.At || got[it.id] == LinkSeen.Not }
        val notSeen = read.filter { got[it.id] == LinkSeen.Not }
        val unread = asked.count { got[it.id] == LinkSeen.Unknown }
        val parts = ArrayList<String>()
        if (read.isNotEmpty()) {
            parts += "${version}판 링크를 본 사람 ${read.size - notSeen.size}/${read.size}명" + when {
                notSeen.isNotEmpty() -> " · 아직: " + notSeen.joinToString(", ") { it.name }
                gone.isEmpty() && unread == 0 -> " · 모두 봤어요"
                else -> ""
            }
        }
        if (gone.isNotEmpty()) parts += "링크가 없어요: " + gone.joinToString(", ") { it.name } + " · 기간이 지났거나 지워졌어요. 새 링크를 보내 주세요"
        if (unread > 0) parts += if (read.isEmpty() && gone.isEmpty()) "본 사람을 읽지 못했어요. 인터넷이 될 때 '다시 보기'를 눌러 주세요" else "못 읽은 ${unread}명"
        return SeenLine(parts.joinToString("\n"), notSeen, gone, unread)
    }

    fun url(m: TeamMember): String? = m.linkId?.let { com.bamnat.app.BuildConfig.SHARE_BASE_URL.trimEnd('/') + "/s/" + it }

    // ---- 1.38 [P3-L-18] 피곤해요 합계 받기: counts per day from members who press 피곤해요 in their own app, never who ----

    private fun tiredKey(teamId: String) = "tired:$teamId"

    fun tiredBox(c: Context, teamId: String): com.bamnat.app.share.TiredBox? = runCatching {
        prefs(c).getString(tiredKey(teamId), null)?.let { JSONObject(it) }?.let { com.bamnat.app.share.TiredBox(it.getString("id"), it.getString("token")) }
    }.getOrNull()

    /** On: a box for the team, and every member link that exists tied to it (a link made later is tied by [tieTired]). */
    suspend fun tiredOn(app: AppContainer, context: Context, team: Team, members: List<TeamMember>): com.bamnat.app.share.TiredBox {
        val box = tiredBox(context, team.id) ?: app.shareApi.tiredCreate(team.name.take(30)).also {
            prefs(context).edit().putString(tiredKey(team.id), JSONObject().put("id", it.id).put("token", it.token).toString()).apply()
        }
        tieTired(app, box.id, members)
        return box
    }

    suspend fun tieTired(app: AppContainer, box: String?, members: List<TeamMember>) {
        members.forEach { m -> val id = m.linkId; val t = m.linkToken; if (id != null && t != null) runCatching { app.shareApi.tiredTie(id, t, box) } }
    }

    /** 1.39 [PRIV-06] 모두 지우기 cut the box on the server (and every member link with it): the phone forgets it. */
    fun forgetTired(c: Context, teamId: String) = prefs(c).edit().remove(tiredKey(teamId)).apply()

    suspend fun tiredOff(app: AppContainer, context: Context, teamId: String, members: List<TeamMember>) {
        tiredBox(context, teamId)?.let { b -> tieTired(app, null, members); runCatching { app.shareApi.tiredDelete(b.id, b.token) } }
        prefs(context).edit().remove(tiredKey(teamId)).apply()
    }

    /** The last 7 days as one line: "오늘 4명 · 어제 3명 미만 · …"; null while nobody has said so this week. */
    fun tiredLine(got: com.bamnat.app.share.TiredGot, today: java.time.LocalDate): String? {
        if (got.days.isEmpty()) return null
        val name = { d: java.time.LocalDate -> when (d) { today -> "오늘"; today.minusDays(1) -> "어제"; else -> "${d.monthValue}/${d.dayOfMonth}" } }
        val shown = got.days.filter { it.second != null }
        val todayPart = got.days.firstOrNull { it.first == today }?.let { (d, n) -> "${name(d)} ${n?.let { "${it}명" } ?: "${got.min}명 미만"}" }
        return listOfNotNull(todayPart).plus(shown.filter { it.first != today }.map { (d, n) -> "${name(d)} ${n}명" }).joinToString(" · ")
    }
}
