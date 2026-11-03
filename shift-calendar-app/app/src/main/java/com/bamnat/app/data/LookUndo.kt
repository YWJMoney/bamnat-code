package com.bamnat.app.data

import android.content.Context

/**
 * PC 재검토 N3 1.36 (입힌 뒤 하루 동안 '어제 모양으로'): the look from just before the studio put a look on, kept for a day so it can go
 * back from the calendar even after the app was closed. Only the look settings ([com.bamnat.app.recipe.RecipePieces.lookSnapshot]),
 * never records.
 */
object LookUndo {
    private const val PREFS = "look_undo"
    const val KEEP_MS = 24L * 60 * 60 * 1000

    fun keep(context: Context, before: Map<String, String>, at: Long = com.bamnat.core.time.Today.millis()) {
        val json = org.json.JSONObject(before as Map<*, *>).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("before", json).putLong("at", at).apply()
    }

    /** The look kept and when, while it is less than a day old. */
    fun read(context: Context, now: Long = com.bamnat.core.time.Today.millis()): Pair<Map<String, String>, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L || now - at !in 0 until KEEP_MS) return null
        val o = runCatching { org.json.JSONObject(p.getString("before", null) ?: return null) }.getOrNull() ?: return null
        return o.keys().asSequence().associateWith { o.optString(it) } to at
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
