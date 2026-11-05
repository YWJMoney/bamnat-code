package com.bamnat.app.data

import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.PayrollLineEntity
import com.bamnat.app.data.db.PayrollMonthEntity
import com.bamnat.app.data.db.StaffEntity
import com.bamnat.app.data.db.StaffWorkEntity
import com.bamnat.app.data.db.TeamCellEntity
import com.bamnat.app.data.db.TeamEntity
import com.bamnat.app.data.db.TeamMemberEntity
import com.bamnat.app.data.db.TeamMonthEntity
import com.bamnat.app.data.db.WorkplaceEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1.38 [P3-01] Phase 3 in the backup (설계 1-3 백업 칸): the 팀 근무표 goes in every backup ("leaderTeams", 팀원 링크 토큰 included as
 * any 공유 링크's, D29); 사업장·직원·급여 ("workplaces") only in one sealed with a password (순번 138 완료 조건 "직원 정보는 암호를 걸어야
 * 들어가요"). A backup without a key leaves the phone's own rows as they are, so an older or unsealed backup never wipes the 대장.
 */
object PhaseThreeBackup {
    const val TEAMS = "leaderTeams"
    const val WORKPLACES = "workplaces"

    suspend fun putTeams(db: AppDatabase, into: JSONObject) {
        val d = db.teamDao()
        val teams = d.teams()
        if (teams.isEmpty()) return
        into.put(TEAMS, JSONObject()
            .put("teams", JSONArray().apply { teams.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("leader", it.leaderName)
                .put("shifts", it.shifts).put("need", it.need).put("created", it.createdAt).put("archived", it.archived)) } })
            .put("members", JSONArray().apply { d.members().forEach { put(JSONObject().put("id", it.id).put("team", it.teamId).put("name", it.name)
                .put("role", it.role).put("skill", it.skill).putOpt("color", it.colorArgb).put("sort", it.sort).putOpt("linkId", it.linkId)
                .putOpt("linkToken", it.linkToken).put("active", it.active)) } })
            .put("cells", JSONArray().apply { d.allCells().sortedWith(compareBy({ it.memberId }, { it.epochDay })).forEach {
                put(JSONArray().put(it.teamId).put(it.memberId).put(it.epochDay).put(it.shiftId).put(it.source).put(it.locked)) } })
            .put("months", JSONArray().apply { d.months().forEach { put(JSONObject().put("team", it.teamId).put("month", it.month).put("state", it.state)
                .put("version", it.version).putOpt("confirmedAt", it.confirmedAt).put("confirmed", it.confirmed)) } })
            .put("wishes", JSONArray().apply { d.wishes().sortedWith(compareBy({ it.memberId }, { it.epochDay })).forEach {
                put(JSONArray().put(it.teamId).put(it.memberId).put(it.epochDay).put(it.kind).put(it.receivedAt).put(it.via)) } }))
    }

    suspend fun putWorkplaces(db: AppDatabase, into: JSONObject) {
        val d = db.payrollDao()
        val places = d.workplaces()
        if (places.isEmpty()) return
        into.put(WORKPLACES, JSONObject()
            .put("places", JSONArray().apply { places.forEach { put(JSONObject().put("id", it.id).put("name", it.name).putOpt("fivePlus", it.fivePlus)
                .putOpt("payday", it.payday).put("breaks", it.legalBreaks).put("weekStart", it.weekStart).putOpt("team", it.teamId).put("created", it.createdAt)) } })
            .put("staff", JSONArray().apply { d.staff().forEach { put(JSONObject().put("id", it.id).put("place", it.workplaceId).put("name", it.name)
                .put("form", it.form).put("base", it.base).put("allowances", it.allowances).putOpt("hired", it.hiredOn).putOpt("left", it.leftOn)
                .putOpt("birth", it.birth).put("job", it.job).put("insurance", it.insurance).put("weeklyHoliday", it.weeklyHolidayPay)
                .put("weekly", it.weekly).putOpt("member", it.memberId).put("sort", it.sort)) } })
            .put("work", JSONArray().apply { d.work().sortedWith(compareBy({ it.staffId }, { it.epochDay })).forEach {
                put(JSONArray().put(it.staffId).put(it.epochDay).put(it.kind).put(it.startMinute ?: JSONObject.NULL).put(it.endMinute ?: JSONObject.NULL)) } })
            .put("months", JSONArray().apply { d.months().forEach { put(JSONObject().put("place", it.workplaceId).put("month", it.month).put("state", it.state)
                .put("rates", it.rateVersion).putOpt("closedAt", it.closedAt)) } })
            .put("lines", JSONArray().apply { d.lines().forEach { put(JSONObject().put("place", it.workplaceId).put("month", it.month).put("staff", it.staffId)
                .put("gross", it.gross).put("items", it.items).put("method", it.method).put("deduct", it.deduct).putOpt("net", it.net)
                .putOpt("paidAt", it.paidAt).putOpt("slipSentAt", it.slipSentAt)) } }))
    }

    /** The rows a backup's [TEAMS] holds, read before anything on the phone changes; null when it has none. */
    class Teams(val teams: List<TeamEntity>, val members: List<TeamMemberEntity>, val cells: List<TeamCellEntity>, val months: List<TeamMonthEntity>,
                val wishes: List<com.bamnat.app.data.db.TeamWishEntity> = emptyList())

    fun readTeams(root: JSONObject): Teams? {
        val o = root.optJSONObject(TEAMS) ?: return null
        return Teams(
            o.optJSONArray("teams").objs().map { TeamEntity(it.getString("id"), it.optString("name"), it.optString("leader"), it.optString("shifts", "[]"),
                it.optString("need"), it.optLong("created"), it.optBoolean("archived")) },
            o.optJSONArray("members").objs().map { TeamMemberEntity(it.getString("id"), it.getString("team"), it.optString("name"), it.optString("role"),
                it.optString("skill", "REG"), it.longOrNull("color"), it.optInt("sort"), it.strOrNull("linkId"), it.strOrNull("linkToken"), it.optBoolean("active", true)) },
            o.optJSONArray("cells").arrays().map { TeamCellEntity(it.getString(0), it.getString(1), it.getLong(2), it.getLong(3), it.optString(4, "MANUAL"), it.optBoolean(5)) },
            o.optJSONArray("months").objs().map { TeamMonthEntity(it.getString("team"), it.getString("month"), it.optString("state", "DRAFT"), it.optInt("version"),
                it.longOrNull("confirmedAt"), it.optString("confirmed")) },
            o.optJSONArray("wishes").arrays().map { com.bamnat.app.data.db.TeamWishEntity(it.getString(0), it.getString(1), it.getLong(2), it.optString(3, "OFF"),
                it.optLong(4), it.optString(5, "LEADER")) },
        )
    }

    class Places(val places: List<WorkplaceEntity>, val staff: List<StaffEntity>, val work: List<StaffWorkEntity>, val months: List<PayrollMonthEntity>, val lines: List<PayrollLineEntity>)

    fun readWorkplaces(root: JSONObject): Places? {
        val o = root.optJSONObject(WORKPLACES) ?: return null
        return Places(
            o.optJSONArray("places").objs().map { WorkplaceEntity(it.getString("id"), it.optString("name"), if (it.has("fivePlus") && !it.isNull("fivePlus")) it.getBoolean("fivePlus") else null,
                it.intOrNull("payday"), it.optBoolean("breaks", true), it.optInt("weekStart", 1), it.strOrNull("team"), it.optLong("created")) },
            o.optJSONArray("staff").objs().map { StaffEntity(it.getString("id"), it.getString("place"), it.optString("name"), it.optString("form", "HOUR"),
                it.optLong("base"), it.optString("allowances", "[]"), it.longOrNull("hired"), it.longOrNull("left"), it.longOrNull("birth"), it.optString("job"),
                it.optString("insurance", "{}"), it.optBoolean("weeklyHoliday", true), it.optString("weekly", "{}"), it.strOrNull("member"), it.optInt("sort")) },
            o.optJSONArray("work").arrays().map { StaffWorkEntity(it.getString(0), it.getLong(1), it.getString(2),
                if (it.isNull(3)) null else it.getInt(3), if (it.isNull(4)) null else it.getInt(4)) },
            o.optJSONArray("months").objs().map { PayrollMonthEntity(it.getString("place"), it.getString("month"), it.optString("state", "OPEN"),
                it.optString("rates"), it.longOrNull("closedAt")) },
            o.optJSONArray("lines").objs().map { PayrollLineEntity(it.getString("place"), it.getString("month"), it.getString("staff"), it.optLong("gross"),
                it.optString("items", "[]"), it.optString("method", "[]"), it.optString("deduct"), it.longOrNull("net"), it.longOrNull("paidAt"), it.longOrNull("slipSentAt")) },
        )
    }

    suspend fun write(db: AppDatabase, teams: Teams?, places: Places?) {
        teams?.let { db.teamDao().replaceAll(it.teams, it.members, it.cells, it.months, it.wishes) }
        places?.let { db.payrollDao().replaceAll(it.places, it.staff, it.work, it.months, it.lines) }
    }

    private fun JSONArray?.objs(): List<JSONObject> = this?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
    private fun JSONArray?.arrays(): List<JSONArray> = this?.let { a -> (0 until a.length()).mapNotNull { a.optJSONArray(it) } }.orEmpty()
    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
    private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null
    private fun JSONObject.strOrNull(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null
}
