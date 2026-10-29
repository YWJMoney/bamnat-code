package com.bamnat.app.data

import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.AppliedRecipeEntity
import com.bamnat.app.data.db.RecipeDraftEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * [RCP-17][RCP-25] 1.34 나눔 판 in the backup, under one "recipes" key: the recipes being made and the record of those put on. A
 * restored record can no longer be taken back (its snapshot was of the other phone's settings), so it comes back as replaced.
 * A backup without the key (before 1.34) leaves the phone's as they are.
 */
object RecipeBackup {
    class Rows(val applied: List<AppliedRecipeEntity>, val drafts: List<RecipeDraftEntity>)

    suspend fun put(source: AppDatabase): JSONObject {
        val dao = source.recipeDao()
        return JSONObject()
            .put("applied", JSONArray().apply {
                dao.applied().forEach { e ->
                    put(JSONObject().put("id", e.id).put("code", e.code).put("version", e.version).put("hash", e.hash).put("title", e.title)
                        .put("calendar", e.calendarId).put("body", e.body).put("inputs", e.inputs).put("pieces", e.pieces)
                        .put("at", e.appliedAt).putOpt("undone", e.undoneAt).putOpt("replaced", e.replacedAt).put("kept", e.keptSent))
                }
            })
            .put("drafts", JSONArray().apply {
                dao.drafts().forEach { d ->
                    put(JSONObject().put("id", d.id).put("body", d.body).put("step", d.step).putOpt("code", d.code).put("scope", d.scope).put("at", d.updatedAt))
                }
            })
    }

    fun read(o: JSONObject, restoredAt: Long): Rows = Rows(
        applied = o.objects("applied").map { r ->
            AppliedRecipeEntity(
                id = r.getLong("id"), code = r.optString("code"), version = r.optInt("version"), hash = r.optString("hash"),
                title = r.optString("title"), calendarId = r.optString("calendar", PersonalCalendar.MAIN), body = r.optString("body"),
                inputs = r.optString("inputs", "{}"), pieces = r.optString("pieces"), before = "{}", appliedAt = r.optLong("at"),
                undoneAt = r.optLongOrNull("undone"), replacedAt = r.optLongOrNull("replaced") ?: restoredAt.takeIf { r.optLongOrNull("undone") == null },
                keptSent = r.optBoolean("kept"),
            )
        },
        drafts = o.objects("drafts").map { d ->
            RecipeDraftEntity(id = d.getLong("id"), body = d.optString("body"), step = d.optInt("step", 1),
                code = if (d.has("code") && !d.isNull("code")) d.getString("code") else null, scope = d.optString("scope", "public"), updatedAt = d.optLong("at"))
        },
    )

    suspend fun write(target: AppDatabase, rows: Rows) {
        val dao = target.recipeDao()
        dao.clearApplied(); rows.applied.forEach { dao.upsertApplied(it) }
        dao.clearDrafts(); rows.drafts.forEach { dao.upsertDraft(it) }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()

    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
}
