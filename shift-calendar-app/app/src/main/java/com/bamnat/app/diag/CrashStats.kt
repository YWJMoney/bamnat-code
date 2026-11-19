package com.bamnat.app.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.bamnat.core.stats.CrashMarks
import com.bamnat.core.time.Today
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVL-90] (검토 I12c-03, 처리방침 3.14 문장표 6 '앱이 멈춘 곳 횟수', 깃발 `stats.crash`) 앱이 닫히거나 응답이 없었던 곳의
 * 하루 횟수를 사용 통계 꾸러미에 실어요 (`crash: [{day, kind, sig, at, n}]`, [CrashMarks]). A stop is counted only while
 * [allowed] (the user's yes to 사용 통계 and the `stats.crash` row, which turns on by itself on 3.14's 30-day day by the
 * server's clock): before it nothing is even kept, and an ANR Android recorded before it is never read back. Turning
 * 사용 통계 off forgets what waits.
 */
class CrashStats(
    context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val allowedNow: () -> Boolean = { allowed() },
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    private fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay()

    private fun marks(today: Long): List<CrashMarks.Mark> =
        CrashMarks.decode(prefs.getString(K_MARKS, null), today - CrashMarks.KEEP_DAYS + 1)

    /**
     * One stop of [kind] at [at] at [atMillis]. [now] writes at once ([CrashLog]'s handler: the process ends right after),
     * else in the background.
     */
    fun record(kind: String, at: List<String>, atMillis: Long = Today.millis(), now: Boolean = false) = synchronized(lock) {
        if (at.isEmpty() || !runCatching(allowedNow).getOrDefault(false)) return@synchronized
        val day = dayOf(atMillis)
        val next = CrashMarks.add(marks(dayOf(Today.millis())), day, kind, at)
        val e = prefs.edit().putString(K_MARKS, CrashMarks.encode(next))
        if (now) e.commit() else e.apply()
    }

    /**
     * At start (Android 11+): the 응답 없음 Android recorded since the last look, each with the main thread's lines of ours.
     * While not [allowed] the look only moves on, so turning it on never counts what came before.
     */
    fun readExits(context: Context, nowMillis: Long = Today.millis()) {
        if (Build.VERSION.SDK_INT < 30) return
        val seen = prefs.getLong(K_SEEN, -1L)
        if (seen < 0 || !runCatching(allowedNow).getOrDefault(false)) {
            prefs.edit().putLong(K_SEEN, nowMillis).apply()
            return
        }
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val anrs = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 0) }.getOrNull().orEmpty()
            .filter { it.reason == ApplicationExitInfo.REASON_ANR && it.timestamp > seen && it.timestamp <= nowMillis }
        for (e in anrs.sortedBy { it.timestamp }) {
            val at = runCatching { e.traceInputStream?.bufferedReader()?.useLines { CrashMarks.atTrace(it) } }.getOrNull().orEmpty()
            record(CrashMarks.ANR, at, e.timestamp)
        }
        prefs.edit().putLong(K_SEEN, maxOf(seen, nowMillis)).apply()
    }

    /** The `crash` part of the usage bundle: finished days only, or null when not allowed or none waits. */
    fun payload(): JSONArray? = synchronized(lock) {
        if (!runCatching(allowedNow).getOrDefault(false)) return@synchronized null
        val today = dayOf(Today.millis())
        val due = CrashMarks.due(marks(today), today)
        if (due.isEmpty()) return@synchronized null
        JSONArray(due.map { m ->
            JSONObject().put("day", LocalDate.ofEpochDay(m.day).toString()).put("kind", m.kind).put("sig", m.sig)
                .put("at", JSONArray(m.at)).put("n", m.count)
        })
    }

    /** After the bundle went: the days it carried are gone, today's stay. */
    fun sent() = synchronized(lock) {
        val today = dayOf(Today.millis())
        prefs.edit().putString(K_MARKS, CrashMarks.encode(marks(today).filter { it.day >= today })).apply()
    }

    /** Turning 사용 통계 off forgets what was waiting. */
    fun clear() = synchronized(lock) { prefs.edit().remove(K_MARKS).apply() }

    companion object {
        /** Set by [com.bamnat.app.AppContainer] to the user's yes and `flags.on(FlagCatalog.STATS_CRASH)`; off until then. */
        @Volatile var allowed: () -> Boolean = { false }
        /** Set by [com.bamnat.app.AppContainer]; [CrashLog]'s handler counts a stop through it. */
        @Volatile var current: CrashStats? = null

        private const val FILE = "crash_stats"
        private const val K_MARKS = "marks"
        private const val K_SEEN = "anr_seen"
    }
}
