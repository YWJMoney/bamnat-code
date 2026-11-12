package com.bamnat.app.diag

import android.content.Context
import com.bamnat.app.BuildConfig
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.serverRequest
import com.bamnat.core.stats.KeepMarks
import com.bamnat.core.stats.LifeMarks
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * [STAT-01] 사용 통계 보내기. A [TelemetrySink], so it only hears events while the user said yes (see
 * [Telemetry]). It keeps names and daily counts ([UsageBook]) and sends the finished days once a day
 * to the company server: no phone id, no times, no details, never amounts or text.
 */
class UsageStats(
    context: Context,
    private val baseUrl: String = BuildConfig.COMPANY_BASE_URL,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val post: (String, JSONObject) -> Unit = { url, body -> serverRequest(url, "POST", "/api/events", body) },
    /** [PERF-16] 1.28 AO: startup times added to the bundle when [StartupStats.allowed]; null sends none. */
    private val startup: StartupStats? = null,
    /** [STAT-08] 1.31 N4: lookup times added when [LookupStats.policyAllows]; null sends none. */
    private val lookups: LookupStats? = null,
    /** 1.39 [SETUP-20] 첫 설정 누른 횟수 added when [SetupTapStats.policyAllows]; null sends none. */
    private val setupTaps: SetupTapStats? = null,
    /** 1.39 [STAT2-02] The install day (epoch day in [zone]) for [LifeMarks]; read from the package, never sent. */
    private val installDay: () -> Long? = { installDayOf(context, zone()) },
    /** 1.39 [OBS2-08] The kind of work chosen (WorkStyle name) for `style`, sent only while [kindAllowed]; null before setup. */
    private val style: () -> String? = { null },
) : TelemetrySink {
    private val prefs = context.applicationContext.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
    private val lock = Any()

    private fun today(): Long = Today.date(zone()).toEpochDay()

    private fun book(): UsageBook = UsageBook.decode(prefs.getString(K_BOOK, null))

    override fun send(name: String, detail: String) {
        val event = UsageEvent.fromLog(name, detail) ?: return
        record(event)
    }

    fun record(event: UsageEvent) = synchronized(lock) {
        // 1.40 [DISC-06] The card's four totals are not even counted before 처리방침 3.13 (깃발 stats.disc).
        if (event in UsageEvent.POLICY_313 && !discAllowed()) return@synchronized
        val day = today()
        var book = book().record(event, day)
        val e = prefs.edit()
        // 1.39 [STAT2-02] 진입·머묾·회복: the day's first open adds its LIFE_ counts once the `stats.life` row is on (처리방침 3.12).
        // The last open day stays here only to tell 'came back after a week'; it is never sent and goes with clear().
        if (event == UsageEvent.OPEN) {
            val last = prefs.getLong(K_LAST_OPEN, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
            if (last == null || last < day) {
                if (lifeAllowed()) for (mark in LifeMarks.of(installDay(), last, day)) book = book.record(mark, day)
                // 1.39 [OBS2-08] 기능과 머묾: the features of the last open day came back within 7·30 days (깃발 stats.keep, 처리방침 3.12).
                if (keepAllowed()) {
                    val seen = book.seen
                    KeepMarks.of(last, day) { d -> seen.filterValues { d in it }.keys }?.let { mark ->
                        val waiting = KeepMarks.decode(prefs.getString(K_KEEP, null), day - KeepMarks.KEEP_DAYS)
                        e.putString(K_KEEP, KeepMarks.encode((waiting + mark).takeLast(KeepMarks.MAX_MARKS)))
                    }
                }
                e.putLong(K_LAST_OPEN, day)
            }
        }
        e.putString(K_BOOK, book.encode()).apply()
    }

    /** What is waiting, for the settings page's "보낼 내용 보기". */
    /** [ALRM-02] The last day a bundle went, for 서버 연결 점검; null before any did. */
    val sentDay: LocalDate? get() = prefs.getLong(K_SENT_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)

    fun preview(flagsOn: List<String>? = null): String? = synchronized(lock) {
        book().bundle(today(), BuildConfig.VERSION_NAME) { LocalDate.ofEpochDay(it).toString() }
            ?.let { withStartup(JSONObject(withFlags(it, flagsOn))).toString() }
    }

    /**
     * [FLAG-16] 1.28 AL: with the `stats.more` row on (처리방침 3.2 and its own yes), the keys of the dark-merge rows on
     * this phone, sorted: the same list on every phone with the same rows, so it tells no phone apart.
     */
    private fun withFlags(body: String, flagsOn: List<String>?): String =
        if (flagsOn == null) body else JSONObject(body).put("flagsOn", org.json.JSONArray(flagsOn.sorted())).toString()

    /** [PERF-16] AO 1.28: the bundle with its `startup` part when there is one to send (gated by [StartupStats.policyAllows]). */
    private fun withStartup(body: JSONObject): JSONObject = body.apply {
        startup?.payload()?.let { put("startup", it) }
        lookups?.payload()?.let { put("lookups", it) } // [STAT-08] 1.31
        setupTaps?.payload()?.let { put("setup", it) } // 1.39 [SETUP-20]
        // 1.39 [OBS2-08] 유형별 흐름과 기능과 머묾, each behind its own row of 처리방침 3.12.
        if (kindAllowed()) style()?.let { put("style", it) }
        if (keepAllowed()) keepPayload()?.let { put("keep", it) }
    }

    /** 1.39 [OBS2-08] `keep: [{day, win, events}]` for the marks waiting, or null when none. */
    private fun keepPayload(): org.json.JSONArray? {
        val marks = KeepMarks.decode(prefs.getString(K_KEEP, null), today() - KeepMarks.KEEP_DAYS)
        if (marks.isEmpty()) return null
        return org.json.JSONArray().apply {
            for (m in marks) put(JSONObject().put("day", LocalDate.ofEpochDay(m.day).toString()).put("win", m.window).put("events", org.json.JSONArray(m.events)))
        }
    }

    /** Turning it off forgets everything not sent yet. */
    fun clear() = synchronized(lock) {
        prefs.edit().clear().apply()
        startup?.clear() // [PERF-16]
        lookups?.clear() // [STAT-08]
        setupTaps?.clear() // 1.39 [SETUP-20]
    }

    /** At start: without a yes, anything still kept is dropped (a cheap look when there is nothing). */
    fun forgetUnless(on: Boolean) {
        if (!on && prefs.contains(K_BOOK)) clear()
    }

    /**
     * Sends the finished days when [allowed] (the user's yes and the developer's switch) and not yet today.
     * A failure keeps them for tomorrow; days older than a week are dropped by [UsageBook].
     */
    suspend fun sendIfDue(allowed: Boolean, flagsOn: List<String>? = null): Boolean = withContext(Dispatchers.IO) {
        if (!allowed || baseUrl.isBlank()) return@withContext false
        val day = today()
        if (prefs.getLong(K_SENT_DAY, Long.MIN_VALUE) >= day) return@withContext false
        // 1.39 [OBS2-08] The marks this bundle carries; a mark added while it goes waits for the next one.
        val keepAt = synchronized(lock) { prefs.getString(K_KEEP, null) }
        val body = synchronized(lock) { book().bundle(day, BuildConfig.VERSION_NAME) { LocalDate.ofEpochDay(it).toString() } }
            ?: return@withContext false
        try {
            post(baseUrl, withStartup(JSONObject(withFlags(body, flagsOn))))
        } catch (e: CompanyException) {
            // A bad bundle would fail every day; it is dropped. No connection is tried tomorrow.
            if (e.code == "offline" || e.code == "rate_limited" || e.code.startsWith("http_5")) return@withContext false
        }
        synchronized(lock) {
            val e = prefs.edit().putString(K_BOOK, book().sent(day).encode()).putLong(K_SENT_DAY, day)
            if (prefs.getString(K_KEEP, null) == keepAt) e.remove(K_KEEP)
            e.apply()
        }
        startup?.sent() // [PERF-16]
        lookups?.sent() // [STAT-08]
        setupTaps?.sent() // 1.39 [SETUP-20]
        true
    }

    companion object {
        private const val K_BOOK = "book"
        private const val K_SENT_DAY = "sent_day"
        private const val K_LAST_OPEN = "last_open_day"
        private const val K_KEEP = "keep"

        /** 1.39 [STAT2-02] Set by [com.bamnat.app.AppContainer] to `flags.on(FlagCatalog.STATS_LIFE)`; off until then. */
        @Volatile var lifeAllowed: () -> Boolean = { false }
        /** 1.39 [OBS2-08] Set by [com.bamnat.app.AppContainer] to the `stats.kind` row; off until then. */
        @Volatile var kindAllowed: () -> Boolean = { false }
        /** 1.39 [OBS2-08] Set by [com.bamnat.app.AppContainer] to the `stats.keep` row; off until then. */
        @Volatile var keepAllowed: () -> Boolean = { false }
        /** 1.40 [DISC-06] Set by [com.bamnat.app.AppContainer] to the `stats.disc` row (처리방침 3.13); off until then. */
        @Volatile var discAllowed: () -> Boolean = { false }

        private fun installDayOf(context: Context, zone: ZoneId): Long? = runCatching {
            val c = context.applicationContext
            val first = c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime
            if (first <= 0L) null else java.time.Instant.ofEpochMilli(first).atZone(zone).toLocalDate().toEpochDay()
        }.getOrNull()
    }
}
