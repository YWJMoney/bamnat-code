package com.bamnat.app.diag

import android.content.Context
import com.bamnat.app.StartupTrace
import org.json.JSONObject

/**
 * [PERF-15][PERF-16] 1.28 AO: how long the app took to open on this phone. Each opening that reached the calendar's
 * month leaves one [StartupTrace.Sample] here: the last one for 설정 › 진단 (it never leaves the phone), and the ones
 * since the last send for 사용 통계 (only when [allowed]: the user's yes to 사용 통계 and, since 처리방침 3.2 names
 * startup times, the `stats.more` flag). What is sent is the middle value of this phone's openings, in buckets
 * ([bucket]), with no phone id and no time of day: `startup: {container, activity, firstMonth, warm?}` (how many openings is not sent).
 */
class StartupStats(
    context: Context,
    /**
     * Whether startup times are kept for 사용 통계 and sent with it: the user's yes to 사용 통계 and [policyAllows].
     * Openings while this is false are never sent later (only the last one is kept, for 진단).
     */
    private val allowed: () -> Boolean = { false },
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    /** The last opening that reached the month, for the diagnostics screen; null before the first. */
    val last: StartupTrace.Sample? get() = decode(prefs.getString(K_LAST, null)).firstOrNull()

    /** What waits to be sent (at most [KEEP] openings, the newest kept). */
    fun pending(): List<StartupTrace.Sample> = synchronized(lock) { decode(prefs.getString(K_PENDING, null)) }

    /** Called once the calendar's month is drawn after an opening. Written in the background (apply). */
    fun record(sample: StartupTrace.Sample) = synchronized(lock) {
        val edit = prefs.edit().putString(K_LAST, encode(listOf(sample)))
        if (allowed()) edit.putString(K_PENDING, encode((decode(prefs.getString(K_PENDING, null)) + sample).takeLast(KEEP)))
        edit.apply()
    }

    /** The `startup` part of the usage bundle, or null when not allowed or nothing waits. */
    fun payload(): JSONObject? {
        if (!allowed()) return null
        return summary(pending())
    }

    /** After the bundle went: the openings it summed up are not sent again. */
    fun sent() = synchronized(lock) { prefs.edit().remove(K_PENDING).apply() }

    /** Turning 사용 통계 off forgets what was waiting; the last opening stays for the diagnostics screen. */
    fun clear() = synchronized(lock) { prefs.edit().remove(K_PENDING).apply() }

    companion object {
        /**
         * [PERF-16] Startup times are a new item for 사용 통계 (처리방침 3.1 §1-9 lists what it sends, with no times), so they
         * wait for 처리방침 3.2 and its consent: the `stats.more` flag of the flag book. Off until [com.bamnat.app.AppContainer]
         * sets it to `flags.on(FlagCatalog.STATS_MORE)` (1.28 통합).
         */
        @Volatile var policyAllows: () -> Boolean = { false }

        private const val FILE = "startup_stats"
        private const val K_LAST = "last"
        private const val K_PENDING = "pending"
        /** About a week of openings at a few a day; older ones fall off. */
        const val KEEP = 30

        /**
         * Upper edges of the buckets in milliseconds: a time is sent as the first edge at or above it, so the server
         * never learns a phone's exact time (and a slow phone cannot be told from another by it). Over 15 s is 20000.
         * The company server checks the same list (`STARTUP_BUCKETS`).
         */
        val BUCKETS = longArrayOf(100, 200, 300, 400, 500, 700, 1_000, 1_500, 2_000, 3_000, 4_000, 6_000, 10_000, 15_000, 20_000)

        fun bucket(ms: Long): Long = BUCKETS.firstOrNull { ms <= it } ?: BUCKETS.last()

        /** [PERF-15] 진단's line: "마지막 시작: 첫 달까지 1.2초 (새로 열기, 준비 기다림 0.3초)". */
        fun describe(s: StartupTrace.Sample?): String {
            if (s == null) return "마지막 시작: 아직 잰 적 없어요(달력이 열리면 재요)"
            val how = if (s.kind == StartupTrace.Kind.COLD) "새로 열기" else "다시 열기"
            val wait = if (s.containerWait > 0) ", 준비 기다림 ${seconds(s.containerWait)}" else ""
            return "마지막 시작: 첫 달까지 ${seconds(s.firstMonth)} ($how$wait)"
        }

        /** 1234 → "1.2초", 80 → "0.1초" (one decimal, at least 0.1). */
        fun seconds(ms: Long): String {
            val tenths = ((ms + 50) / 100).coerceAtLeast(1)
            return "${tenths / 10}.${tenths % 10}초"
        }

        private fun median(values: List<Long>): Long = values.sorted()[(values.size - 1) / 2]

        /** One kind's openings as `{container, activity, firstMonth}`, each the bucketed middle value. */
        private fun part(list: List<StartupTrace.Sample>, withActivity: Boolean): JSONObject = JSONObject().apply {
            put("container", bucket(median(list.map { it.containerWait })))
            if (withActivity) put("activity", bucket(median(list.map { it.activity })))
            put("firstMonth", bucket(median(list.map { it.firstMonth })))
        }

        /** Cold openings at the top (the 2-1 contract's keys), warm ones under `warm`; null when there are none. */
        fun summary(list: List<StartupTrace.Sample>): JSONObject? {
            val cold = list.filter { it.kind == StartupTrace.Kind.COLD }
            val warm = list.filter { it.kind == StartupTrace.Kind.WARM }
            if (cold.isEmpty() && warm.isEmpty()) return null
            val o = if (cold.isNotEmpty()) part(cold, withActivity = true) else JSONObject()
            if (warm.isNotEmpty()) o.put("warm", part(warm, withActivity = false))
            return o
        }

        internal fun encode(list: List<StartupTrace.Sample>): String =
            list.joinToString(";") { "${it.kind.key},${it.containerWait},${it.activity},${it.firstMonth}" }

        internal fun decode(text: String?): List<StartupTrace.Sample> = text.orEmpty().split(';').mapNotNull { row ->
            val p = row.split(',')
            if (p.size != 4) return@mapNotNull null
            val kind = StartupTrace.Kind.entries.firstOrNull { it.key == p[0] } ?: return@mapNotNull null
            val n = p.drop(1).map { it.toLongOrNull()?.coerceIn(0, 600_000) ?: return@mapNotNull null }
            StartupTrace.Sample(kind, n[0], n[1], n[2])
        }
    }
}
