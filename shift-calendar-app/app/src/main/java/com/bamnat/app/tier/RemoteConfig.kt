package com.bamnat.app.tier

import android.content.Context
import com.bamnat.core.ops.AppNotice
import com.bamnat.app.flags.FlagJson
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagOverride
import com.bamnat.core.flags.Force
import com.bamnat.core.flags.PolicyCalendar
import com.bamnat.core.flags.PolicyVersion
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.tier.AdRules
import com.bamnat.core.tier.TrialConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import com.bamnat.core.time.Today

/**
 * [MON-02] What the company server's `GET /api/config` says. [FLAG-06] Since 1.28 the 정식 출시 switch is retired into
 * the dark-merge book: [flags] are the server's overrides of its rows, [policy] its 처리방침 dates and [serverTime] its
 * clock (for LEGAL rows). A 1.27 server sends none of them; [overrides] then reads [billingLive], [freeFeatures] and
 * [off] as overrides, so a 1.28 phone behaves exactly as 1.27 did until the server is redeployed (AL-D6).
 * A phone that never heard from the server has no override: every row follows the app's own conditions.
 */
data class LaunchConfig(
    /** The 1.27 switch as the server still sends it for older apps; 1.28 reads it only from a server without [flags]. */
    val billingLive: Boolean = false,
    val launchedAt: Instant? = null,
    val thanksMonths: Int = 0,
    /** 1.27: pro features opened to everyone; read only from a server without [flags] (then `free.<FEATURE>`). */
    val freeFeatures: Set<Feature> = emptySet(),
    val adQuietDays: Int = AdRules.QUIET_DAYS,
    val trialAskDays: Int = TrialConsent.ASK_BEFORE.toDays().toInt(),
    val fetchedAt: Instant? = null,
    /** [SRV-04] The developer app's notices, shown one at a time over the calendar. */
    val notices: List<AppNotice> = emptyList(),
    /** [SRV-04] 1.27: features the developer paused; read only from a server without [flags]. */
    val off: Set<ServerSwitch> = emptySet(),
    /** [FLAG-06] The server's overrides by key; null from a server that does not know flags (1.27). */
    val flags: Map<String, FlagOverride>? = null,
    /** [FLAG-03] The server's 처리방침 dates, laid over [PolicyCalendar.SEED]. */
    val policy: List<PolicyVersion> = emptyList(),
    /** [FLAG-04] The server's clock when it answered. */
    val serverTime: Instant? = null,
    /** [FLAG-04] The phone's time since boot when the answer came, and which boot (to carry [serverTime] forward). */
    val fetchedElapsed: Long? = null,
    val fetchedBoot: Int? = null,
) {
    /**
     * [FLAG-06] The overrides the phone evaluates: the server's [flags], or, from a 1.27 server, its switch read as
     * forces (AL-D6): 정식 출시 on/off → billing·ads forced on/off, 무료로 연 기능 → `free.*` on, 쉬는 기능 → off.
     */
    fun overrides(): Map<String, FlagOverride> = flags ?: buildMap {
        // A phone that never heard from a server ([fetchedAt] null) has no switch to read: the rows' own conditions decide.
        val launch = if (billingLive) Force.ON else if (fetchedAt != null) Force.OFF else null
        if (launch != null) {
            put(FlagCatalog.BILLING, FlagOverride(force = launch, why = "정식 출시 스위치(옛 서버)"))
            put(FlagCatalog.ADS, FlagOverride(force = launch, why = "정식 출시 스위치(옛 서버)"))
        }
        freeFeatures.forEach { put(FlagCatalog.FREE_PREFIX + it.name, FlagOverride(force = Force.ON, why = "무료로 연 기능(옛 서버)")) }
        off.forEach { put(FlagCatalog.switchKey(it), FlagOverride(force = Force.OFF, why = "기능 스위치(옛 서버)")) }
    }

    /** [FLAG-03] The app's 처리방침 dates with the server's laid over. */
    val calendar: PolicyCalendar get() = PolicyCalendar.SEED.merged(policy)

    fun toJson(): JSONObject = JSONObject().put("billingLive", billingLive).put("launchedAt", launchedAt?.toString() ?: JSONObject.NULL)
        .put("thanksMonths", thanksMonths).put("freeFeatures", JSONArray(freeFeatures.map { it.name }))
        .put("adQuietDays", adQuietDays).put("trialAskDays", trialAskDays).put("fetchedAt", fetchedAt?.toString() ?: JSONObject.NULL)
        .put("notices", JSONArray(notices.map { n ->
            JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("level", if (n.important) "important" else "info")
                .put("from", Instant.ofEpochMilli(n.from).toString()).put("until", Instant.ofEpochMilli(n.until).toString())
                .put("minApp", n.minApp ?: JSONObject.NULL).put("maxApp", n.maxApp ?: JSONObject.NULL)
                .put("audience", n.audience?.let { a ->
                    JSONObject().put("makers", JSONArray(a.makers.toList())).put("minSdk", a.minSdk ?: JSONObject.NULL)
                        .put("maxSdk", a.maxSdk ?: JSONObject.NULL).put("workTypes", JSONArray(a.workTypes.toList()))
                        .put("features", JSONArray(a.features.toList()))
                } ?: JSONObject.NULL)
                .put("action", n.action?.let { x -> JSONObject().put("kind", x.kind).put("label", x.label ?: JSONObject.NULL).put("url", x.url ?: JSONObject.NULL) }
                    ?: JSONObject.NULL)
        }))
        .put("off", JSONArray(off.map { it.key }))
        .put("flags", flags?.let { FlagJson.toJson(it) } ?: JSONObject.NULL)
        .put("policy", FlagJson.toJson(policy))
        .put("serverTime", serverTime?.toString() ?: JSONObject.NULL)
        .put("fetchedElapsed", fetchedElapsed ?: JSONObject.NULL).put("fetchedBoot", fetchedBoot ?: JSONObject.NULL)

    companion object {
        /** The server's answer read strictly: a wrong shape is an error, so the last good answer stays. */
        fun parse(o: JSONObject, now: Instant): LaunchConfig {
            // [FLAG-06] A 1.27 server always sends billingLive; a 1.28 one sends flags (and billingLive for older apps).
            val flags = if (!o.has("flags") || o.isNull("flags")) null else FlagJson.overrides(o.getJSONObject("flags"))
            if (!o.has("billingLive") && flags == null) throw JSONException("billingLive")
            val live = if (o.has("billingLive")) o.getBoolean("billingLive") else false
            val names = o.optJSONArray("freeFeatures")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            return LaunchConfig(
                billingLive = live,
                launchedAt = if (o.isNull("launchedAt") || !o.has("launchedAt")) null else Instant.parse(o.getString("launchedAt")),
                thanksMonths = o.optInt("thanksMonths", 0).coerceIn(0, 12),
                // Unknown names (a feature of a newer app) are skipped; a free feature cannot be locked from here.
                freeFeatures = names.mapNotNull { n -> Feature.entries.firstOrNull { it.name == n } }.filter { it.minTier > Tier.FREE }.toSet(),
                adQuietDays = o.optInt("adQuietDays", AdRules.QUIET_DAYS).coerceIn(0, 30),
                trialAskDays = o.optInt("trialAskDays", TrialConsent.ASK_BEFORE.toDays().toInt()).coerceIn(1, 30),
                fetchedAt = now,
                notices = o.optJSONArray("notices")?.let { a -> (0 until a.length()).map { notice(a.getJSONObject(it)) } }.orEmpty().take(AppNoticesMax),
                // A switch of a newer server is skipped; this app has nothing of it to pause.
                off = o.optJSONArray("off")?.let { a -> (0 until a.length()).mapNotNull { ServerSwitch.of(a.getString(it)) } }.orEmpty().toSet(),
                flags = flags,
                policy = if (!o.has("policy") || o.isNull("policy")) emptyList() else FlagJson.policy(o.getJSONArray("policy")),
                serverTime = if (!o.has("serverTime") || o.isNull("serverTime")) null else Instant.parse(o.getString("serverTime")),
                // Kept by the store, not sent by the server: only a saved answer has them.
                fetchedElapsed = if (!o.has("fetchedElapsed") || o.isNull("fetchedElapsed")) null else o.getLong("fetchedElapsed"),
                fetchedBoot = if (!o.has("fetchedBoot") || o.isNull("fetchedBoot")) null else o.getInt("fetchedBoot"),
            )
        }

        private const val AppNoticesMax = com.bamnat.core.ops.AppNotices.MAX

        /** [SRV-04] One notice, strictly: a wrong shape fails the whole answer and the last good one stays. */
        private fun notice(n: JSONObject): AppNotice {
            val id = n.getString("id")
            if (!Regex("^n[A-Za-z0-9]{9}$").matches(id)) throw JSONException("id")
            val version = { k: String -> if (!n.has(k) || n.isNull(k)) null else n.getInt(k) }
            return AppNotice(
                id = id, title = n.getString("title").take(40), body = n.getString("body").take(500),
                important = n.getString("level") == "important",
                from = Instant.parse(n.getString("from")).toEpochMilli(), until = Instant.parse(n.getString("until")).toEpochMilli(),
                minApp = version("minApp"), maxApp = version("maxApp"),
                audience = n.optJSONObject("audience")?.let(::audience),
                action = n.optJSONObject("action")?.let(::action),
            )
        }

        /** [SRV-09] Parts this app does not know are left out, so a newer server's notice is not lost. */
        private fun audience(a: JSONObject): com.bamnat.core.ops.NoticeAudience {
            fun set(k: String) = a.optJSONArray(k)?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }.orEmpty()
            fun int(k: String) = if (!a.has(k) || a.isNull(k)) null else a.getInt(k)
            return com.bamnat.core.ops.NoticeAudience(set("makers").map { it.lowercase() }.toSet(), int("minSdk"), int("maxSdk"), set("workTypes"), set("features"))
        }

        /** [SRV-09] A button this app cannot open is dropped; an address must be https. */
        private fun action(x: JSONObject): com.bamnat.core.ops.NoticeAction? {
            val kind = x.optString("kind").takeIf { it in com.bamnat.core.ops.NoticeAction.KINDS } ?: return null
            val url = if (x.isNull("url")) null else x.optString("url").takeIf { it.startsWith("https://") }
            if (kind == "URL" && url == null) return null
            val label = if (x.isNull("label")) null else x.optString("label").takeIf { it.isNotBlank() }?.take(12)
            return com.bamnat.core.ops.NoticeAction(kind, label, url)
        }

        fun fromSaved(text: String?): LaunchConfig? = runCatching {
            val o = JSONObject(text ?: return null)
            parse(o, Instant.parse(o.getString("fetchedAt")))
        }.getOrNull()
    }
}

/**
 * [MON-02] The last [LaunchConfig] on this phone; asked again once a day. [FLAG-11] 1.28: the 3.1 hold (결정 D84) is no
 * longer a mask here but the `billing`·`ads` rows' policy condition ([com.bamnat.app.flags.Flags]); this keeps the yes.
 */
class RemoteConfigStore(
    context: Context,
    /** [FLAG-04] Time since boot and which boot, to carry the server's clock forward; tests put their own. */
    private val elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val boot: () -> Int? = { bootCount(context) },
) {
    private val prefs = context.applicationContext.getSharedPreferences("launch_config", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(LaunchConfig.fromSaved(prefs.getString(KEY, null)) ?: LaunchConfig())
    val config: StateFlow<LaunchConfig> = _config.asStateFlow()
    val current: LaunchConfig get() = _config.value

    /** [FLAG-03] The 처리방침 versions this phone said yes to (휴대폰에만: 동의 기록은 새 휴대폰에서 다시 물어요). */
    private val _agreed = MutableStateFlow(readAgreed())
    val agreed: StateFlow<Set<String>> = _agreed.asStateFlow()

    private fun readAgreed(): Set<String> = setOfNotNull(prefs.getString(POLICY_KEY, null))

    /** [PRIV-03] The 3.1 yes. */
    fun policyAgreed(): Boolean = PolicyCalendar.agreedAtLeast(_agreed.value, com.bamnat.core.ops.PolicyConsent.VERSION)

    fun agreePolicy(now: Instant = Today.instant(), version: String = com.bamnat.core.ops.PolicyConsent.VERSION) {
        prefs.edit().putString(POLICY_KEY, version).putString(POLICY_AT, now.toString()).apply()
        _agreed.value = readAgreed()
    }

    /**
     * [FLAG-04] The server's clock now: its time when it last answered plus the time since, on the same boot. After a
     * reboot the time since is not known, so the server's time as it was (it is at least that late). Null before any
     * answer with a server time.
     */
    fun serverNow(): Instant? {
        val c = current
        val at = c.serverTime ?: return null
        val then = c.fetchedElapsed ?: return at
        val now = elapsed()
        val sameBoot = c.fetchedBoot == boot() && now >= then
        return if (sameBoot) at.plusMillis(now - then) else at
    }

    /** Due when never asked, a day has passed, or the clock went back. */
    fun due(now: Instant): Boolean {
        val at = current.fetchedAt ?: return true
        return now.isBefore(at) || Duration.between(at, now) >= EVERY
    }

    /** [FLAG-04] A fresh answer is stamped with the time since boot, so its server time can be carried forward. */
    fun save(c: LaunchConfig) {
        val stamped = if (c.serverTime != null && c.fetchedElapsed == null) c.copy(fetchedElapsed = elapsed(), fetchedBoot = boot()) else c
        _config.value = stamped
        prefs.edit().putString(KEY, stamped.toJson().toString()).apply()
    }

    /** Asks the server when due; no answer (offline, a sign-in page) keeps the last one. */
    suspend fun refresh(api: TierApi, now: Instant = Today.instant(), force: Boolean = false): Boolean {
        if (!api.enabled || (!force && !due(now))) return false
        val fresh = try {
            api.config(now)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        save(fresh)
        return true
    }

    companion object {
        private const val KEY = "config"
        private const val POLICY_KEY = "policy_agreed"
        private const val POLICY_AT = "policy_agreed_at"
        val EVERY: Duration = Duration.ofDays(1)

        /** [FLAG-04] Which boot this is (Android 7+); null when the phone does not say. */
        fun bootCount(context: Context): Int? = runCatching {
            android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
        }.getOrNull()
    }
}
