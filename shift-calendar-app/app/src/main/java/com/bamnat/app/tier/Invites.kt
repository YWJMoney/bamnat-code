package com.bamnat.app.tier

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.bamnat.app.BuildConfig
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.optStringOrNull
import com.bamnat.app.company.serverCall
import com.bamnat.app.company.serverRequest
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.tier.InviteTerms
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.resume

/**
 * [INVITE-05][INVITE-08] What the company server says about this install's invites: its code and link (null until
 * made), the rules ([terms]), friends who used the app for the period toward the next months ([progress] of
 * [InviteTerms.need]), friends still in their period ([waiting]), friends counted in all ([friends], over a cap
 * too), the months they gave ([given]), and how the invite this phone itself came by stands ([used]).
 */
data class InviteView(
    val code: String?,
    val link: String?,
    val terms: InviteTerms,
    val progress: Int,
    val waiting: Int,
    val friends: Int,
    val given: Int,
    val capReached: Boolean,
    val used: String?,
    val checkedAt: Instant,
)

/**
 * [INVITE-03][INVITE-08] How the server took this phone's report: "waiting" (in its period of use, [daysLeft] to go),
 * "qualified" (used it, counted toward the inviter's next months), "credited", "capped", "refused", "expired".
 */
data class InviteAnswer(val status: String, val reason: String? = null, val daysLeft: Int? = null)

/** [INVITE-02][INVITE-03] 친구 초대 on the company server; screenshots and tests answer with a fake. */
interface InviteApi {
    val enabled: Boolean
    /** [INVITE-08] The rules, with no install number: said before the user agrees to anything. */
    suspend fun rules(): InviteTerms
    suspend fun view(device: String): InviteView
    /** Makes (or gives again) this install's code; [phone] is the phone mark ([PhoneMark]). */
    suspend fun makeCode(device: String, phone: String): InviteView
    /** The code this phone came by: [source] "referrer" or "typed". */
    suspend fun use(device: String, code: String, source: String, phone: String): InviteAnswer
    /** This phone opened again (once a day): the period of use is counted by the server's clock. */
    suspend fun seen(device: String): InviteAnswer
}

class InviteClient(private val baseUrl: String = BuildConfig.COMPANY_BASE_URL) : InviteApi {
    override val enabled: Boolean get() = baseUrl.isNotBlank()

    override suspend fun rules(): InviteTerms = serverCall {
        if (!enabled) throw CompanyException("offline")
        readTerms(serverRequest(baseUrl, "GET", "/api/invites/rules", null, null))
    }

    override suspend fun view(device: String): InviteView = serverCall { readView(request("GET", "/api/invites", null, device)) }

    override suspend fun makeCode(device: String, phone: String): InviteView = serverCall {
        readView(request("POST", "/api/invites/code", JSONObject().put("phone", phone), device))
    }

    override suspend fun use(device: String, code: String, source: String, phone: String): InviteAnswer = serverCall {
        readAnswer(request("POST", "/api/invites/use", JSONObject().put("code", code).put("source", source).put("phone", phone), device))
    }

    override suspend fun seen(device: String): InviteAnswer = serverCall { readAnswer(request("POST", "/api/invites/seen", JSONObject(), device)) }

    private fun request(method: String, path: String, body: JSONObject?, device: String): JSONObject {
        if (!enabled) throw CompanyException("offline")
        return serverRequest(baseUrl, method, path, body, device)
    }

    companion object {
        fun readTerms(o: JSONObject): InviteTerms = InviteTerms.DEFAULT.let { d ->
            InviteTerms(need = o.optInt("need", d.need).coerceAtLeast(1), days = o.optInt("days", d.days).coerceAtLeast(1),
                months = o.optInt("months", d.months), capMonths = o.optInt("capMonths", d.capMonths))
        }

        fun readView(o: JSONObject, at: Instant = Today.instant()): InviteView = InviteView(
            code = o.optStringOrNull("code"), link = o.optStringOrNull("link"), terms = readTerms(o), progress = o.optInt("progress"),
            waiting = o.optInt("waiting"), friends = o.optInt("friends"), given = o.optInt("given"), capReached = o.optBoolean("capReached"),
            used = o.optStringOrNull("used"), checkedAt = o.optStringOrNull("checkedAt")?.let(Instant::parse) ?: at,
        )

        fun readAnswer(o: JSONObject): InviteAnswer =
            InviteAnswer(o.getString("status"), o.optStringOrNull("reason"), if (o.has("daysLeft") && !o.isNull("daysLeft")) o.getInt("daysLeft") else null)

        internal fun writeTerms(t: InviteTerms, o: JSONObject = JSONObject()): JSONObject =
            o.put("need", t.need).put("days", t.days).put("months", t.months).put("capMonths", t.capMonths)
    }
}

/**
 * [INVITE-04] This phone's mark for 친구 초대: SHA-256 of the app-scoped Android ID (the same after the app is installed
 * again with the same signing key, new after a factory reset). The id itself never leaves the phone, and the
 * advertising ID is never read with it (Play user data policy). The server hashes it again with its secret.
 */
object PhoneMark {
    @SuppressLint("HardwareIds") // [INVITE-04] Only to count a phone once for an invite, hashed here (처리방침 1-12).
    fun of(context: Context): String? {
        val id = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        return id?.takeIf { it.isNotBlank() }?.let(::hash)
    }

    fun hash(androidId: String): String =
        MessageDigest.getInstance("SHA-256").digest("bamnat-invite|$androidId".toByteArray()).joinToString("") { "%02x".format(it) }
}

/** [INVITE-03] Where the Play install referrer comes from; tests put a fake in its place. */
fun interface ReferrerSource {
    /** The referrer string, or null when Play has none or cannot say. */
    suspend fun read(context: Context): String?
}

/** [INVITE-03] Play's Install Referrer service, asked once (Install Referrer API 2.2). */
object PlayReferrer : ReferrerSource {
    override suspend fun read(context: Context): String? = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { cont ->
            val client = com.android.installreferrer.api.InstallReferrerClient.newBuilder(context.applicationContext).build()
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finish(value: String?) {
                if (done.compareAndSet(false, true)) {
                    runCatching { client.endConnection() }
                    cont.resume(value)
                }
            }
            cont.invokeOnCancellation { runCatching { client.endConnection() } }
            runCatching {
                client.startConnection(object : com.android.installreferrer.api.InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        val ok = responseCode == com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse.OK
                        finish(if (ok) runCatching { client.installReferrer.installReferrer }.getOrNull() else null)
                    }

                    override fun onInstallReferrerServiceDisconnected() = finish(null)
                })
            }.onFailure { finish(null) }
        }
    }
}

/**
 * [INVITE-02][INVITE-03] What this phone keeps about invites: the server's last view of its own code, the code
 * Play's referrer brought (read once), and how this phone's own report stands. Not in the backup: it is tied to
 * this install on the server, and the months move with the recovery code (company-worker claim moves the code too).
 */
data class InviteState(
    val view: InviteView? = null,
    /** [INVITE-08] The rules as the server last said them, for a phone without a code. */
    val terms: InviteTerms? = null,
    /** The referrer was read (whatever it said): Play asks it to be read once. */
    val referrerRead: Boolean = false,
    /** The code the referrer brought, waiting for the user's word on the calendar. */
    val found: String? = null,
    /** The calendar's card was answered or closed. */
    val offerClosed: Boolean = false,
    /** This phone's own report: the code and how the server took it. */
    val reportedCode: String? = null,
    val answer: InviteAnswer? = null,
    /** The day the once-a-day check last went. */
    val seenDay: LocalDate? = null,
) {
    /** Still in the period of use after the report. */
    val waiting: Boolean get() = answer?.status == "waiting"

    /** The rules to say: the view's, then the last ones read, then the defaults. */
    val rules: InviteTerms get() = view?.terms ?: terms ?: InviteTerms.DEFAULT
}

class InviteStore(context: Context, suffix: String = "") {
    // "invite", or "invite-a" for a test's second phone. Kept on this phone (SettingsFilesBackup.DEVICE_ONLY).
    private val prefs = context.applicationContext.getSharedPreferences("invite" + suffix, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<InviteState> = _state.asStateFlow()
    val current: InviteState get() = _state.value

    fun edit(change: (InviteState) -> InviteState) {
        _state.update(change)
        prefs.edit().putString(KEY, write(_state.value).toString()).apply()
    }

    private fun read(): InviteState = runCatching { parse(JSONObject(prefs.getString(KEY, null) ?: return InviteState())) }.getOrDefault(InviteState())

    companion object {
        private const val KEY = "state"
        private fun JSONObject.text(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)

        internal fun write(s: InviteState): JSONObject = JSONObject()
            .put("view", s.view?.let { v ->
                InviteClient.writeTerms(v.terms).put("code", v.code ?: JSONObject.NULL).put("link", v.link ?: JSONObject.NULL)
                    .put("progress", v.progress).put("waiting", v.waiting).put("friends", v.friends).put("given", v.given)
                    .put("capReached", v.capReached).put("used", v.used ?: JSONObject.NULL).put("checkedAt", v.checkedAt.toString())
            } ?: JSONObject.NULL)
            .put("terms", s.terms?.let { InviteClient.writeTerms(it) } ?: JSONObject.NULL)
            .put("referrerRead", s.referrerRead).put("found", s.found ?: JSONObject.NULL).put("offerClosed", s.offerClosed)
            .put("reportedCode", s.reportedCode ?: JSONObject.NULL)
            .put("answer", s.answer?.let {
                JSONObject().put("status", it.status).put("reason", it.reason ?: JSONObject.NULL).put("daysLeft", it.daysLeft ?: JSONObject.NULL)
            } ?: JSONObject.NULL)
            .put("seenDay", s.seenDay?.toString() ?: JSONObject.NULL)

        internal fun parse(o: JSONObject): InviteState = InviteState(
            view = o.optJSONObject("view")?.let { InviteClient.readView(it) },
            terms = o.optJSONObject("terms")?.let { InviteClient.readTerms(it) },
            referrerRead = o.optBoolean("referrerRead"), found = o.text("found"), offerClosed = o.optBoolean("offerClosed"),
            reportedCode = o.text("reportedCode"),
            answer = o.optJSONObject("answer")?.let { InviteClient.readAnswer(it) },
            seenDay = o.text("seenDay")?.let(LocalDate::parse),
        )
    }
}

/**
 * [INVITE-02][INVITE-03] 친구 초대 for this phone. Nothing here runs at the start of the app: [sync] is called once the
 * calendar has drawn (AppContainer.afterFirstFrame) and when the app comes to the front (syncPro), and only after
 * the first setup does it read Play's referrer, once. Everything sent goes only after the user's tap on a line that
 * says what goes (결정 D42).
 */
class Invites(
    private val context: Context,
    val store: InviteStore,
    private val api: () -> InviteApi,
    private val device: () -> String,
    private val phone: () -> String?,
    private val setupDone: () -> Boolean,
    /** When this app was installed; tests set their own. */
    var installedAt: () -> Instant?,
    private val referrer: () -> ReferrerSource,
) {
    private val lock = Mutex()

    /**
     * Reads the referrer once after the first setup, sends the once-a-day check while this phone is in its period of
     * use, and refreshes the inviter's view daily.
     */
    suspend fun sync(force: Boolean = false) = lock.withLock {
        val api = api()
        if (!api.enabled) return@withLock
        val s = store.current
        val now = Today.instant()
        if (!s.referrerRead && s.reportedCode == null && setupDone() && InviteRules.mayReadReferrer(installedAt(), now)) {
            val code = InviteRules.fromReferrer(runCatching { referrer().read(context) }.getOrNull())
            store.edit { it.copy(referrerRead = true, found = code) }
        }
        if (store.current.waiting && store.current.seenDay != Today.date()) {
            try {
                val answer = api.seen(device())
                store.edit { it.copy(answer = answer, seenDay = Today.date()) }
            } catch (e: CompanyException) {
                if (e.code == "not_found") store.edit { it.copy(answer = InviteAnswer("expired"), seenDay = Today.date()) }
            }
        }
        val view = store.current.view
        if (view?.code != null && (force || Duration.between(view.checkedAt, now) >= REFRESH)) {
            runCatching { api.view(device()) }.getOrNull()?.let { v -> store.edit { it.copy(view = v) } }
        }
    }

    /** [INVITE-02] After the user agreed on the line: this install's code and link (the same one when made before). */
    suspend fun makeLink(): InviteView {
        val mark = phone() ?: PhoneMark.hash("install:${device()}")
        val v = api().makeCode(device(), mark)
        store.edit { it.copy(view = v) }
        return v
    }

    /**
     * [INVITE-05][INVITE-08] The inviter's counts afresh, for the 프로 screen; without a code, only the rules (nothing
     * about this phone goes).
     */
    suspend fun refresh(): InviteView? {
        if (!api().enabled) return store.current.view
        if (store.current.view?.code == null) {
            runCatching { api().rules() }.getOrNull()?.let { t -> store.edit { it.copy(terms = t) } }
            return store.current.view
        }
        val v = api().view(device())
        store.edit { it.copy(view = v) }
        return v
    }

    /** [INVITE-08] The rules afresh for the friend's card, with no install number. */
    suspend fun loadRules(): InviteTerms {
        if (store.current.terms == null && api().enabled) runCatching { api().rules() }.getOrNull()?.let { t -> store.edit { it.copy(terms = t) } }
        return store.current.rules
    }

    /**
     * [INVITE-03] After the user agreed on the line: tells the server which code this phone came by. The day it is
     * sent is the first day of the period of use; the once-a-day check goes by itself from [sync].
     */
    suspend fun report(code: String, source: String): InviteAnswer {
        val clean = InviteRules.code(code) ?: throw CompanyException("code", "code")
        val mark = phone() ?: PhoneMark.hash("install:${device()}")
        val answer = api().use(device(), clean, source, mark)
        store.edit { it.copy(reportedCode = clean, answer = answer, offerClosed = true, found = null, seenDay = Today.date()) }
        return answer
    }

    /** The card on the calendar was closed without sending anything. */
    fun decline() = store.edit { it.copy(offerClosed = true) }

    /** [INVITE-03] A code typed by hand is taken within [InviteRules.TYPE_WITHIN] of the install, once, after the first setup. */
    fun mayType(now: Instant = Today.instant()): Boolean =
        store.current.reportedCode == null && setupDone() && InviteRules.mayType(installedAt(), now)

    /** The code the calendar card asks about, if any. */
    fun offer(): String? = store.current.takeIf { !it.offerClosed && it.reportedCode == null }?.found

    private companion object {
        val REFRESH: Duration = Duration.ofDays(1)
    }
}
