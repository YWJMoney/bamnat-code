package com.bamnat.app.data

import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.PayObservationEntity
import com.bamnat.app.data.db.TaxSettingEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * [TAX-07] A calendar's 월급 관측 and tax answers (version 21) in the backup, under one "tax" key of that calendar's
 * part. A backup without the key (before 1.25) leaves the phone's as they are; `{}` empties them.
 */
object TaxBackup {
    class Rows(val observations: List<PayObservationEntity>, val settings: List<TaxSettingEntity>)

    suspend fun put(source: AppDatabase): JSONObject {
        val dao = source.taxDao()
        return JSONObject()
            .put("observations", JSONArray().apply {
                dao.observations().forEach { o ->
                    put(JSONObject().put("id", o.id).put("month", o.payMonth).put("net", o.net).putOpt("gross", o.gross).put("day", o.epochDay)
                        .put("source", o.source).put("kind", o.kind).putOpt("entry", o.entryId).putOpt("hours", o.hours).putOpt("days", o.days)
                        .put("note", o.note).put("created", o.createdAt))
                }
            })
            .put("settings", JSONArray().apply { dao.settings().forEach { s -> put(JSONObject().put("key", s.key).put("value", s.value)) } })
    }

    fun read(o: JSONObject): Rows = Rows(
        observations = o.objects("observations").map { r ->
            PayObservationEntity(
                id = r.getLong("id"), payMonth = r.getInt("month"), net = r.optLong("net"), gross = r.optLongOrNull("gross"),
                epochDay = r.getLong("day"), source = r.optString("source", "TYPED"), kind = r.optString("kind", "SALARY"),
                entryId = r.optLongOrNull("entry"), hours = if (r.has("hours") && !r.isNull("hours")) r.getDouble("hours") else null,
                days = if (r.has("days") && !r.isNull("days")) r.getInt("days") else null, note = r.optString("note"), createdAt = r.optLong("created"),
            )
        },
        settings = o.objects("settings").map { s -> TaxSettingEntity(s.getString("key"), s.optString("value")) },
    )

    suspend fun write(target: AppDatabase, rows: Rows) {
        val dao = target.taxDao()
        dao.clearObservations(); dao.insertObservations(rows.observations)
        dao.clearSettings(); dao.restoreSettings(rows.settings)
    }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()

    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
}
