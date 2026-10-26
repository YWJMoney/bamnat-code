package com.bamnat.app.diag

import android.content.Context
import com.bamnat.app.BuildConfig
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.serverRequest
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
        prefs.edit().putString(K_BOOK, book().record(event, today()).encode()).apply()
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
    private fun withStartup(body: JSONObject): JSONObject = body.apply { startup?.payload()?.let { put("startup", it) } }

    /** Turning it off forgets everything not sent yet. */
    fun clear() = synchronized(lock) {
        prefs.edit().clear().apply()
        startup?.clear() // [PERF-16]
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
        val body = synchronized(lock) { book().bundle(day, BuildConfig.VERSION_NAME) { LocalDate.ofEpochDay(it).toString() } }
            ?: return@withContext false
        try {
            post(baseUrl, withStartup(JSONObject(withFlags(body, flagsOn))))
        } catch (e: CompanyException) {
            // A bad bundle would fail every day; it is dropped. No connection is tried tomorrow.
            if (e.code == "offline" || e.code == "rate_limited" || e.code.startsWith("http_5")) return@withContext false
        }
        synchronized(lock) { prefs.edit().putString(K_BOOK, book().sent(day).encode()).putLong(K_SENT_DAY, day).apply() }
        startup?.sent() // [PERF-16]
        true
    }

    private companion object {
        const val K_BOOK = "book"
        const val K_SENT_DAY = "sent_day"
    }
}
