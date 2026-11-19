package com.bamnat.app.company

import android.content.Context
import android.content.SharedPreferences
import com.bamnat.core.plaza.SharePart
import com.bamnat.core.plaza.ShareWay
import com.bamnat.core.plaza.ShareWays
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
 *
 * [CALI-01] 1.30 BB: one per calendar ([CompanyScope]). The main calendar's is the preferences file it always was; an added
 * calendar's is a file in its own folder ([CompanyScopeStorage]) and asks [phone] (the main one) for what is the phone's
 * and not a calendar's: the install id and how sharing by itself was chosen (설정 › 개인정보·앱 정보).
 */
/** 2.0.0 [RVN-22] COMP-60-1 One lock for making the install id (every store of the phone shares its prefs file). */
private val DEVICE_LOCK = Any()

class CompanyStore internal constructor(private val prefs: SharedPreferences, private val phone: CompanyStore?) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("company", Context.MODE_PRIVATE), null)

    /** Random and made here, so the server can count one phone once without knowing whose it is. */
    val device: String
        get() = phone?.device ?: prefs.getString("device", null) ?: made()

    /**
     * 2.0.0 [RVN-22] 검토 COMP-60-1: made once even when the first run's reads ask at the same moment (two ids, one of them kept
     * by no request a 모두 지우기 could reach).
     */
    private fun made(): String = synchronized(DEVICE_LOCK) {
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).commit() }
    }

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

    private val _publicUse = MutableStateFlow(PublicUse.read(prefs.getString("public_use", null)))
    /**
     * [SPAT-03] The public row this phone's cycle came from and how far the pay and plaza steps have gone; on this phone
     * only (not in the backup's "company" part), as the other invitation memories.
     */
    val publicUse: StateFlow<PublicUse?> = _publicUse.asStateFlow()

    fun setPublicUse(u: PublicUse?) {
        _publicUse.value = u
        prefs.edit().apply { if (u == null) remove("public_use") else putString("public_use", u.json()) }.apply()
    }

    fun updatePublicUse(change: (PublicUse) -> PublicUse) {
        _publicUse.value?.let { setPublicUse(change(it)) }
    }

    /**
     * 2.0.0 [RVN-22] 검토 I10bc-03 (COMP-44): how each part goes to the company's plaza after a change ([ShareWays]: 근무주기·근무
     * 시간·급여 계산 방식, each 저절로·묻기·안 함). The phone's one choice, whichever calendar asks ([CALI-01]). A phone from before
     * keeps what it said ([ShareWays.fromOld]: [PLAZA-07] 결정 D79's one switch and [RVN-11]'s pay yes).
     */
    private val _shareWays = MutableStateFlow(if (phone == null) readWays() else ShareWays())
    val shareWays: StateFlow<ShareWays> = phone?.shareWays ?: _shareWays.asStateFlow()

    private fun readWays(): ShareWays = ShareWays.decode(prefs.getString("share_ways", null))
        ?: ShareWays.fromOld(if (prefs.contains("auto_share")) prefs.getBoolean("auto_share", false) else null, prefs.getBoolean("auto_pay", false))

    /**
     * [PLAZA-07] The one switch it was ([ShareWays.legacy]): true when the cycle goes by itself, null asked, false never. Kept for the
     * screens that still ask it (근무주기 나누기, 첫 설정의 상자).
     */
    private val _autoShare = MutableStateFlow(if (phone == null) _shareWays.value.legacy else null)
    /** [CALI-01] The phone's one choice, whichever calendar asks. */
    val autoShare: StateFlow<Boolean?> = phone?.autoShare ?: _autoShare.asStateFlow()

    /** 2.0.0 [RVN-11] COMP-44 Whether the pay way goes by itself ([ShareWays.pay] 저절로). */
    private val _autoPay = MutableStateFlow(phone == null && _shareWays.value.pay == ShareWay.AUTO)
    val autoPay: StateFlow<Boolean> = phone?.autoPay ?: _autoPay.asStateFlow()

    /** 2.0.0 [RVN-22] I10bc-03 The phone's choice, all three parts at once. */
    fun setShareWays(ways: ShareWays) {
        if (phone != null) return phone.setShareWays(ways)
        _shareWays.value = ways
        _autoShare.value = ways.legacy
        _autoPay.value = ways.pay == ShareWay.AUTO
        prefs.edit().putString("share_ways", ways.encode()).remove("auto_share").remove("auto_pay").apply()
    }

    /**
     * [PLAZA-34] 1.27 AI, as the one switch: null asks about every part again, true 저절로 for the cycle and its hours (the pay way
     * as it was: [RVN-11] a 근무주기 yes is not a pay yes), false 다시 묻지 않기 for every part.
     */
    fun setAutoShare(on: Boolean?) {
        val now = shareWays.value
        setShareWays(when (on) {
            null -> ShareWays()
            true -> now.copy(cycle = ShareWay.AUTO, times = ShareWay.AUTO, pay = if (now.pay == ShareWay.NEVER) ShareWay.ASK else now.pay)
            false -> ShareWays(ShareWay.NEVER, ShareWay.NEVER, ShareWay.NEVER)
        })
        if (on != true) autoDirty = false
        // 2.0.0 [RVN-13] COMP-47 나누지 않기 also drops a 나눌게요 still waiting for a connection.
        if (on == false) autoOnce = false
    }

    /** [RVN-11] The pay way 저절로 (true) or asked (false). */
    fun setAutoPay(on: Boolean) = setShareWays(shareWays.value.copy(pay = if (on) ShareWay.AUTO else ShareWay.ASK))

    /**
     * [PLAZA-34] 1.27 AI: the plaza file last shared, asked about or decided on (no amounts: CompanyShare.exportForServer), which a
     * saved change is held against so the same values saved again ask nothing. 2.0.0 [RVN-22] I10bc-03 Each part is as it was last
     * decided: a part declined or set to 안 함 takes the new values here, though the plaza never got them ([plazaHas]).
     */
    var autoBase: String?
        get() = prefs.getString("auto_base", null)
        set(v) = prefs.edit().apply { if (v == null) remove("auto_base") else putString("auto_base", v) }.apply()

    /**
     * 2.0.0 [RVN-22] I10bc-03 The file the plaza has of this calendar (the last that went, by hand or by itself), which a part not
     * going now is taken from, so one part goes without the others. A phone from before reads its [autoBase] once something went.
     */
    var plazaHas: String?
        get() = prefs.getString("plaza_has", null) ?: autoBase?.takeIf { prefs.getLong("auto_sent_at", 0) > 0 || sent.value.isNotEmpty() || applied.value != null }
        set(v) = prefs.edit().apply { if (v == null) remove("plaza_has") else putString("plaza_has", v) }.apply()

    /** What went is what the plaza has, and the base later changes are said against. */
    fun markShared(file: String) {
        autoBase = file
        plazaHas = file
    }

    /**
     * 2.0.0 [RVN-22] I10bc-03 The parts held on the phone until [heldUntil] (저절로: the next morning; 나눌게요 without a connection:
     * the next one), with the words of what changed. 취소 drops them on the phone: nothing reached the server (COMP-46).
     */
    val heldParts: Set<SharePart>
        get() = prefs.getString("held_parts", null)?.split(',')?.mapNotNullTo(sortedSetOf()) { n -> SharePart.entries.firstOrNull { it.name == n } }
            // A phone from before: a 나눌게요 waiting for a connection was the whole setup, a change waiting for its day the 저절로 parts.
            ?: when {
                prefs.getBoolean("auto_once", false) -> shareWays.value.of(ShareWay.AUTO) + shareWays.value.of(ShareWay.ASK)
                prefs.getBoolean("auto_dirty", false) -> shareWays.value.of(ShareWay.AUTO)
                else -> emptySet()
            }
    var heldLine: String?
        get() = prefs.getString("held_line", null)
        set(v) = prefs.edit().apply { if (v == null) remove("held_line") else putString("held_line", v) }.apply()
    /** When the held parts go (epoch ms); 0 as soon as there is a connection. */
    var heldUntil: Long
        get() = prefs.getLong("held_until", 0)
        set(v) = prefs.edit().putLong("held_until", v).apply()

    fun hold(parts: Set<SharePart>, line: String?, until: Long) {
        prefs.edit().putString("held_parts", parts.joinToString(",") { it.name }).putLong("held_until", until)
            .apply { if (line == null) remove("held_line") else putString("held_line", line) }
            .remove("auto_dirty").remove("auto_once").apply()
        _held.value = parts.isNotEmpty()
    }

    fun dropHeld() {
        prefs.edit().remove("held_parts").remove("held_line").remove("held_until").remove("auto_dirty").remove("auto_once").apply()
        _held.value = false
    }

    private val _held = MutableStateFlow(heldParts.isNotEmpty())
    /** Whether something of this calendar is held on the phone, for the line that says so with 취소. */
    val held: StateFlow<Boolean> = _held.asStateFlow()

    /** A change is held to be shared ([heldParts]); false drops it. */
    var autoDirty: Boolean
        get() = heldParts.isNotEmpty()
        set(v) { if (!v) dropHeld() }
    /** 2.0.0 [RVN-13] COMP-47 A 나눌게요 given without a connection waits for one: held with no morning to wait for. */
    var autoOnce: Boolean
        get() = heldParts.isNotEmpty() && heldUntil == 0L
        set(v) { if (!v && heldUntil == 0L) dropHeld() }
    var autoSentAt: Long
        get() = prefs.getLong("auto_sent_at", 0)
        set(v) = prefs.edit().putLong("auto_sent_at", v).apply()

    /**
     * 2.0.0 [RVN-10] COMP-46 Reports a 되돌리기 could not take back for want of a connection: taken back once online
     * ([AutoShare.takeBackWaiting], [OutboxJob]), so the 광장 does not keep what the user took back.
     */
    val takeBackLater: Set<String>
        get() = prefs.getString("take_back_later", null)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    fun takeBackLater(id: String, waiting: Boolean) {
        val now = if (waiting) takeBackLater + id else takeBackLater - id
        prefs.edit().apply { if (now.isEmpty()) remove("take_back_later") else putString("take_back_later", now.joinToString(",")) }.apply()
    }

    /** 2.0.0 [RVN-13] COMP-48 쓰고 있어요·맞아요·회사 빼기 that could not reach the server, oldest first ([SmallSends], [OutboxJob]). */
    internal val keptCalls: List<SmallSends.Call>
        get() = SmallSends.read(prefs.getString("kept_calls", null))

    /** The newest word for a thing takes the older's place (and goes to the end, so the order sent is the order said). */
    @Synchronized
    internal fun keepCall(call: SmallSends.Call) = writeCalls(keptCalls.filter { it.key != call.key } + call)

    @Synchronized
    internal fun dropCall(key: String) {
        val now = keptCalls
        if (now.any { it.key == key }) writeCalls(now.filter { it.key != key })
    }

    private fun writeCalls(calls: List<SmallSends.Call>) =
        prefs.edit().apply { if (calls.isEmpty()) remove("kept_calls") else putString("kept_calls", SmallSends.write(calls)) }.apply()

    private val _member = MutableStateFlow(readMember())
    /** [PLAZA-07] The company this calendar joined, as the server last said: (id, name). [CALI-02] Each calendar its own. */
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
        // [PLAZA-34] And what it had of this phone: the next save is the first again.
        autoBase = null
        plazaHas = null
        // 2.0.0 [RVN-10] COMP-43 모두 지우기 means nothing goes up by itself afterwards: 저절로 나누기 is back to 묻기 (the phone's one
        // choice; [RVN-22] every 저절로 part, 안 함 stays), and a change held on the phone or a take-back waiting for a connection has
        // nothing left to do.
        shareWays.value.let { w -> w.of(ShareWay.AUTO).takeIf { it.isNotEmpty() }?.let { setShareWays(w.with(it, ShareWay.ASK)) } }
        dropHeld()
        // COMP-48 What waited to go is about what the server no longer has.
        prefs.edit().remove("take_back_later").remove("kept_calls").apply()
    }

    fun setApplied(a: AppliedSetup?) {
        _applied.value = a
        // [J133-92] 1.33 빌드 7 확인 (내 설정 나누기 맨 위 '공개 자료 확인·고침 · 현대제철', 회사 칸 'GS칼텍스'): another company's setup
        // put in over a public cycle not yet sent ends that cycle's turn, so the two are never offered as one.
        if (a != null) _publicUse.value?.takeIf { it.sentId == null && CompanyText.norm(it.company) != CompanyText.norm(a.company) }
            ?.let { setPublicUse(null) }
        prefs.edit().putString("applied", a?.let {
            JSONObject().put("companyId", it.companyId).put("company", it.company).put("dept", it.dept).put("setup", it.setup)
                .put("version", it.version ?: -1).put("adopted", it.adopted).put("at", it.at.toString())
                .put("vote", it.vote ?: JSONObject.NULL).toString()
        }).apply()
    }

    fun voted(ok: Boolean?) {
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
