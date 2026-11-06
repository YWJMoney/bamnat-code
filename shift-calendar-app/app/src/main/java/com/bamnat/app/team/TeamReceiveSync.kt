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

    /** Who opened their link since it last changed; a member without a link is left out. */
    suspend fun seen(app: AppContainer, members: List<TeamMember>): Map<String, Instant?> = members.mapNotNull { m ->
        val id = m.linkId ?: return@mapNotNull null
        val token = m.linkToken ?: return@mapNotNull null
        runCatching { app.shareApi.seen(id, token) }.fold({ m.id to it }, { null })
    }.toMap()

    fun url(m: TeamMember): String? = m.linkId?.let { com.bamnat.app.BuildConfig.SHARE_BASE_URL.trimEnd('/') + "/s/" + it }

    // ---- 1.38 [SLEEP-15] 피곤해요 합계 받기: counts per day from members who press 피곤해요 in their own app, never who ----

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
