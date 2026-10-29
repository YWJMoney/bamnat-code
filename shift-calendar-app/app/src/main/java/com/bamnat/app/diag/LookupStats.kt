package com.bamnat.app.diag

import android.content.Context
import org.json.JSONObject

/**
 * [STAT-08] 1.31 N4 (상세리뷰 N4, 사용자 2026-10-28 "스위치는 기간이 되면 자동으로 켜지는 현 방식"): how long lookups took on this
 * phone, for 사용 통계 once 처리방침 3.5 is in force (the `stats.lookup` row turns on by itself that day) and only with the user's
 * yes to 사용 통계 and to its added items given on 1.31 or later. [com.bamnat.app.company.NetTimings] hands every timed lookup
 * here; while [allowed] is false nothing is kept, so nothing from before can go later.
 *
 * What goes is, per kind, the middle value of what waits, rounded up to a [StartupStats.BUCKETS] edge:
 * `lookups: {wait: {find, company, plaza}, call: {search, company, plaza, talk, write}}`. Never what was looked up, which
 * company, an address, a phone id or a time of day.
 */
class LookupStats(
    context: Context,
    private val allowed: () -> Boolean = { false },
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    /** What waits to be sent: (kind, step, ms), at most [KEEP], the newest kept. */
    fun pending(): List<Triple<String, String, Long>> = synchronized(lock) { decode(prefs.getString(K_PENDING, null)) }

    /** One timed lookup ([kind] "wait" or "call", [step] its name). Written in the background, only while [allowed]. */
    fun record(kind: String, step: String, ms: Long) {
        if (STEPS[kind]?.contains(step) != true || !allowed()) return
        synchronized(lock) {
            prefs.edit().putString(K_PENDING, encode((decode(prefs.getString(K_PENDING, null)) + Triple(kind, step, ms)).takeLast(KEEP))).apply()
        }
    }

    /** The `lookups` part of the usage bundle, or null when not allowed or nothing waits. */
    fun payload(): JSONObject? = if (!allowed()) null else summary(pending())

    /** After the bundle went: not sent again. */
    fun sent() = synchronized(lock) { prefs.edit().remove(K_PENDING).apply() }

    /** Turning 사용 통계 off forgets what was waiting. */
    fun clear() = synchronized(lock) { prefs.edit().remove(K_PENDING).apply() }

    companion object {
        /** Set by [com.bamnat.app.AppContainer] to `flags.on(FlagCatalog.STATS_LOOKUP)`; off until then. */
        @Volatile var policyAllows: () -> Boolean = { false }

        private const val FILE = "lookup_stats"
        private const val K_PENDING = "pending"
        /** A few days of use; older ones fall off. */
        const val KEEP = 200

        /** The kinds and their steps; the company server takes the same (`LOOKUP_STEPS`). */
        val STEPS = mapOf(
            "wait" to listOf("find", "company", "plaza"),
            "call" to listOf("search", "company", "plaza", "talk", "write"),
        )

        private fun median(values: List<Long>): Long = values.sorted()[(values.size - 1) / 2]

        fun summary(list: List<Triple<String, String, Long>>): JSONObject? {
            if (list.isEmpty()) return null
            val o = JSONObject()
            for ((kind, steps) in STEPS) {
                val part = JSONObject()
                for (step in steps) {
                    val ms = list.filter { it.first == kind && it.second == step }.map { it.third }
                    if (ms.isNotEmpty()) part.put(step, StartupStats.bucket(median(ms)))
                }
                if (part.length() > 0) o.put(kind, part)
            }
            return o.takeIf { it.length() > 0 }
        }

        internal fun encode(list: List<Triple<String, String, Long>>): String = list.joinToString(";") { "${it.first},${it.second},${it.third}" }

        internal fun decode(text: String?): List<Triple<String, String, Long>> = text.orEmpty().split(';').mapNotNull { row ->
            val p = row.split(',')
            if (p.size != 3 || STEPS[p[0]]?.contains(p[1]) != true) return@mapNotNull null
            Triple(p[0], p[1], p[2].toLongOrNull()?.coerceIn(0, 600_000) ?: return@mapNotNull null)
        }
    }
}
