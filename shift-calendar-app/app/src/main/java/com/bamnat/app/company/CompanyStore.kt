package com.bamnat.app.company

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** A report this phone sent, kept so its status can be looked up and it can be taken back [COMM-02]. */
data class SentReport(val id: String, val company: String, val dept: String, val ask: Boolean, val at: Instant)

/** The shared setup in use on this phone, for 맞았어요·달라요 [COMM-03]. */
data class AppliedSetup(
    val companyId: String,
    val company: String,
    val dept: String,
    val setup: String,
    val version: Int?,
    val adopted: Boolean,
    val at: Instant,
    /** true 맞았어요, false 달라요, null not said yet. */
    val vote: Boolean? = null,
)

/**
 * A company the user typed and set up by hand, waiting to be shared with coworkers [COMM-02].
 * [fix]: the user said a shared setup was wrong and is correcting it [COMM-03].
 */
data class CompanyDraft(val company: String, val dept: String = "", val fix: Boolean = false)

/**
 * [COMM-02] What this phone keeps about sharing company setups: a random install id, what it sent,
 * the shared setup it uses and a company typed during setup. Never an amount.
 */
class CompanyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("company", Context.MODE_PRIVATE)

    /** Random and made here, so the server can count one phone once without knowing whose it is. */
    val device: String
        get() = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }

    private val _sent = MutableStateFlow(readSent())
    val sent: StateFlow<List<SentReport>> = _sent.asStateFlow()

    private val _applied = MutableStateFlow(readApplied())
    val applied: StateFlow<AppliedSetup?> = _applied.asStateFlow()

    private val _draft = MutableStateFlow(readDraft())

    private fun readDraft() = prefs.getString("draft", null)?.let { s ->
        runCatching { JSONObject(s).let { CompanyDraft(it.getString("company"), it.optString("dept"), it.optBoolean("fix")) } }.getOrNull()
    }
    val draft: StateFlow<CompanyDraft?> = _draft.asStateFlow()

    private val _pendingPay = MutableStateFlow(prefs.getString("pending_pay", null))
    /** The applied setup's file until pay is set up from it with the user's own amounts. */
    val pendingPayFlow: StateFlow<String?> = _pendingPay.asStateFlow()
    var pendingPay: String?
        get() = _pendingPay.value
        set(v) {
            _pendingPay.value = v
            prefs.edit().putString("pending_pay", v).apply()
        }

    private val _voteLater = MutableStateFlow(prefs.getLong("vote_later", 0))
    /** 맞았어요·달라요 is not asked again before this time (epoch ms) after "나중에". */
    val voteLater: StateFlow<Long> = _voteLater.asStateFlow()

    fun voteLaterUntil(at: Long) {
        _voteLater.value = at
        prefs.edit().putLong("vote_later", at).apply()
    }

    private val _plazaEntryClosed = MutableStateFlow(prefs.getString("plaza_entry_closed", null))
    /** [COMM-12] The company name whose "같은 회사 분들 규칙 보기" card was put away; a new name shows it again. */
    val plazaEntryClosed: StateFlow<String?> = _plazaEntryClosed.asStateFlow()

    fun closePlazaEntry(company: String) {
        _plazaEntryClosed.value = company
        prefs.edit().putString("plaza_entry_closed", company).apply()
    }

    /**
     * [PLAZA-04] When the invitation to share a hand-made setup may show again (epoch ms): "다음에" waits 14, then 30, then
     * 90 days ([com.bamnat.core.plaza.ShareAsk]); "그만 볼게요" ends it. A phone that closed it before 1.25 waits 14 days.
     */
    private val _inviteDue = MutableStateFlow(readInviteDue())

    private fun readInviteDue(): Long =
        if (prefs.contains("invite_due") || !prefs.getBoolean("invite_closed", false)) prefs.getLong("invite_due", 0)
        else com.bamnat.core.plaza.ShareAsk.nextDue(1, com.bamnat.core.time.Today.millis()).also {
            prefs.edit().putLong("invite_due", it).putInt("invite_laters", 1).apply()
        }
    val inviteDue: StateFlow<Long> = _inviteDue.asStateFlow()
    private val _inviteNever = MutableStateFlow(prefs.getBoolean("invite_never", false))
    val inviteNever: StateFlow<Boolean> = _inviteNever.asStateFlow()

    /** "다음에": one more wait, each longer than the one before. */
    fun inviteLater(now: Long) {
        val laters = prefs.getInt("invite_laters", 0) + 1
        val due = com.bamnat.core.plaza.ShareAsk.nextDue(laters, now)
        _inviteDue.value = due
        prefs.edit().putInt("invite_laters", laters).putLong("invite_due", due).apply()
    }

    /** "그만 볼게요". */
    fun inviteNever() {
        _inviteNever.value = true
        prefs.edit().putBoolean("invite_never", true).apply()
    }

    private val _handMade = MutableStateFlow(prefs.getBoolean("hand_made", false))
    /** [PLAZA-04] The first setup was finished by hand, with no company's setup and no company name: worth sharing too. */
    val handMade: StateFlow<Boolean> = _handMade.asStateFlow()

    fun setHandMade(on: Boolean) {
        _handMade.value = on
        prefs.edit().putBoolean("hand_made", on).apply()
    }

    /**
     * [PLAZA-07] 결정 D79: whether the work order and pay way go to the company's plaza by themselves after each change
     * (amounts and names left out). Null until the user answered the one-time ask; false also after 설정 turned it off.
     */
    private val _autoShare = MutableStateFlow(if (prefs.contains("auto_share")) prefs.getBoolean("auto_share", false) else null)
    val autoShare: StateFlow<Boolean?> = _autoShare.asStateFlow()

    fun setAutoShare(on: Boolean) {
        _autoShare.value = on
        prefs.edit().putBoolean("auto_share", on).apply { if (!on) putBoolean("auto_dirty", false) }.apply()
    }

    /** A change waits to be shared, at most once a day ([autoSentAt] the last time, epoch ms). */
    var autoDirty: Boolean
        get() = prefs.getBoolean("auto_dirty", false)
        set(v) = prefs.edit().putBoolean("auto_dirty", v).apply()
    var autoSentAt: Long
        get() = prefs.getLong("auto_sent_at", 0)
        set(v) = prefs.edit().putLong("auto_sent_at", v).apply()

    private val _member = MutableStateFlow(readMember())
    /** [PLAZA-07] The company this phone's main calendar joined, as the server last said: (id, name). */
    val member: StateFlow<Pair<String, String>?> = _member.asStateFlow()

    private fun readMember(): Pair<String, String>? = prefs.getString("member", null)?.let { s ->
        runCatching { JSONObject(s).let { it.getString("id") to it.optString("name") } }.getOrNull()
    }

    fun setMember(id: String?, name: String = "") {
        _member.value = id?.let { it to name }
        prefs.edit().putString("member", id?.let { JSONObject().put("id", it).put("name", name).toString() }).apply()
    }

    /** [BAK-01] After a restore wrote the file anew: what travels in a backup is read again. */
    fun reload() {
        _sent.value = readSent()
        _applied.value = readApplied()
        _draft.value = readDraft()
        _pendingPay.value = prefs.getString("pending_pay", null)
        _plazaEntryClosed.value = prefs.getString("plaza_entry_closed", null)
        // [PLAZA-04] The invitation's own waits stay on the phone (X-D5); a 1.24 backup's 닫음 waits 14 days as on update.
        _inviteDue.value = readInviteDue()
    }

    fun addSent(r: SentReport) = writeSent(listOf(r) + _sent.value.filter { it.id != r.id })

    fun removeSent(id: String) = writeSent(_sent.value.filter { it.id != id })

    /** After everything this phone sent was deleted on the server. */
    fun forgetSent() {
        writeSent(emptyList())
        _applied.value?.let { setApplied(it.copy(vote = null)) }
        // [PLAZA-07] The server forgot the companies joined with everything else.
        setMember(null)
    }

    fun setApplied(a: AppliedSetup?) {
        _applied.value = a
        prefs.edit().putString("applied", a?.let {
            JSONObject().put("companyId", it.companyId).put("company", it.company).put("dept", it.dept).put("setup", it.setup)
                .put("version", it.version ?: -1).put("adopted", it.adopted).put("at", it.at.toString())
                .put("vote", it.vote ?: JSONObject.NULL).toString()
        }).apply()
    }

    fun voted(ok: Boolean) {
        _applied.value?.let { setApplied(it.copy(vote = ok)) }
    }

    fun setDraft(d: CompanyDraft?) {
        _draft.value = d
        prefs.edit().putString("draft", d?.let { JSONObject().put("company", it.company).put("dept", it.dept).put("fix", it.fix).toString() }).apply()
        // A new company typed by hand is a new thing to share: asked about at once again ("그만 볼게요" still holds).
        if (d != null) {
            _inviteDue.value = 0
            prefs.edit().putLong("invite_due", 0).putInt("invite_laters", 0).apply()
        }
    }

    private fun writeSent(list: List<SentReport>) {
        // The newest 50 are plenty to follow; older ones stay on the server until taken back or deleted.
        val kept = list.take(50)
        _sent.value = kept
        prefs.edit().putString("sent", JSONArray(kept.map {
            JSONObject().put("id", it.id).put("company", it.company).put("dept", it.dept).put("ask", it.ask).put("at", it.at.toString())
        }).toString()).apply()
    }

    private fun readSent(): List<SentReport> = runCatching {
        JSONArray(prefs.getString("sent", "[]")).objects().map {
            SentReport(it.getString("id"), it.getString("company"), it.optString("dept"), it.optBoolean("ask"), Instant.parse(it.getString("at")))
        }
    }.getOrDefault(emptyList())

    private fun readApplied(): AppliedSetup? = runCatching {
        val o = JSONObject(prefs.getString("applied", null) ?: return null)
        AppliedSetup(
            companyId = o.getString("companyId"), company = o.getString("company"), dept = o.optString("dept"), setup = o.getString("setup"),
            version = o.optInt("version", -1).takeIf { it > 0 }, adopted = o.optBoolean("adopted"), at = Instant.parse(o.getString("at")),
            vote = if (o.isNull("vote")) null else o.getBoolean("vote"),
        )
    }.getOrNull()
}
