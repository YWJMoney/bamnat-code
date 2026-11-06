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
}
