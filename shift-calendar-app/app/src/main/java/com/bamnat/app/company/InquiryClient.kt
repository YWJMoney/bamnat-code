package com.bamnat.app.company

import android.content.Context
import androidx.core.content.edit
import com.bamnat.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant

/**
 * What a question is about [ADMIN-02]; [code] is the server's word for it. Every question, suggestion and
 * company way goes this one way since 1.14.1 [INQ-01]; a company way names the workplace, so it is not
 * [public] and only the developer reads it.
 */
enum class InquiryKind(val code: String, val label: String, val hint: String, val public: Boolean = true) {
    QUESTION("question", "궁금해요", "쓰는 법이나 계산이 궁금할 때"),
    BUG("bug", "앱이 이상해요", "알람이 안 울리거나 계산이 다르거나 화면이 멈출 때"),
    IDEA("idea", "건의·제안", "있었으면 하는 기능이나 바뀌면 좋을 점"),
    COMPANY("company", "회사 방식 알려 주기", "회사의 수당·근무 계산 방식이 앱에 없을 때. 개발자만 봐요", public = false),
    OTHER("other", "그 밖의 이야기", "칭찬이나 불편한 점, 무엇이든"),
    /** 1.36 [GRW-01] 스튜디오 "원하는 꾸미기가 없나요?": 비슷한 부탁끼리 묶여 셋이 모이면 장터 요청 판에 올라가요. */
    STUDIO("studio", "원하는 꾸미기 부탁", "스튜디오에 없는 꾸미기·도구가 있을 때. 같은 부탁이 셋 모이면 장터에 올라가요", public = false),
    ;

    companion object {
        fun of(code: String): InquiryKind = entries.firstOrNull { it.code == code } ?: QUESTION
    }
}

data class InquiryMessage(val fromDeveloper: Boolean, val text: String, val at: Instant)

/**
 * One question and its answers [ADMIN-02]. [status]: open (waiting for the developer), answered or closed.
 * [public]: in 모두의 문의; [same]: how many said 나도 그래요; [hidden]: why it is out of 모두의 문의, if it is.
 */
data class Inquiry(
    val id: String,
    val kind: InquiryKind,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<InquiryMessage>,
    val public: Boolean = false,
    val same: Int = 0,
    val hidden: String? = null,
    /** [INQ-02] Where it stands (received, checking, answered, planned, building, released) and the version it came in. */
    val stage: String = "received",
    val version: String? = null,
    /** [INQ-03] What the asker said of the answer: true 도움이 됐어요, false 아쉬워요, null not yet. */
    val rating: Boolean? = null,
    /** [SRV-06] How many photos came with it. */
    val photos: Int = 0,
    /** [SRV-06] Why the photos were left out when it was sent: "paused" or "full"; null when they went (or there were none). */
    val photosSkipped: String? = null,
    /** 1.36 [GRW-01] 스튜디오 요구: 같은 부탁을 한 분 수와 장터에 오르는 수(3), 보낸 때만. */
    val studioAskers: Int = 0,
    val studioGoal: Int = 0,
    /** 1.39 [LOOP-16] 해결됐나요: 1 해결됐어요, -1 아직이에요, 0 아직 답하지 않음. [askFixed]면 두 단추를 보여요(고친 판 안내를 받은 뒤). */
    val fixed: Int = 0,
    val askFixed: Boolean = false,
) {
    val answers: Int get() = messages.count { it.fromDeveloper }
    val waiting: Boolean get() = status == "open"
}

/** [ADMIN-02] A question in 모두의 문의 as the list shows it; [topicName] is the server's sorting of it. */
data class PublicInquiry(
    val id: String,
    val kind: InquiryKind,
    val status: String,
    val preview: String,
    val answers: Int,
    val same: Int,
    val sameByMe: Boolean,
    val mine: Boolean,
    val topic: String,
    val topicName: String,
    val updatedAt: Instant,
    /** [COMM-11] The writer sealed by the server, as in the plaza; kept to hide this writer. Null on one's own. */
    val author: String? = null,
)

/**
 * One page of 모두의 문의, how many there are in each topic, and where the next page starts;
 * [hiddenByYou] how many this phone's 이 사람 글 안 보기 left out [COMM-11].
 */
data class PublicPage(
    val items: List<PublicInquiry>,
    val topics: List<Pair<String, String>>,
    val counts: Map<String, Int>,
    val next: Int?,
    val hiddenByYou: Int = 0,
)

/** A public question with every message; [hidden] only reaches its writer. */
data class PublicThread(
    val id: String,
    val kind: InquiryKind,
    val status: String,
    val topicName: String,
    val same: Int,
    val sameByMe: Boolean,
    val flaggedByMe: Boolean,
    val mine: Boolean,
    val createdAt: Instant,
    val messages: List<InquiryMessage>,
    val hidden: String?,
    /** [COMM-11] The writer's mark for 이 사람 글 안 보기; null on one's own. */
    val author: String? = null,
)

/** Why someone 신고s a public question: the server's code and the words for it. */
enum class InquiryFlag(val code: String, val label: String) {
    ABUSE("abuse", "욕설·비방"),
    PERSONAL("personal", "개인 정보가 있어요"),
    SPAM("spam", "광고·도배"),
}

/**
 * What goes with a question, all shown before it is sent: the app and phone always, the alarm
 * check only when the user ticks it. Never a name, contact, amount or anything from the calendar
 * beyond what the alarm check itself says. [pay] is what 급여 > 계산이 이상해요 adds: the month's rules and
 * each line's hours and rate, again without an amount, and only while the user leaves it ticked.
 */
data class InquiryInfo(
    val app: String,
    val model: String,
    val android: String,
    val diag: String? = null,
    val pay: String? = null,
    /** [INQ-01] 회사 방식 알려 주기: the kind's settings in words, without an amount. */
    val setup: String? = null,
    /** [SRV-06] Up to two screenshots or photos, already made small and re-saved as JPEG ([InquiryPhotos]). */
    val photos: List<ByteArray> = emptyList(),
    /** [INQ-06] 모두의 문의에 올리기, the switch above 보내기; off keeps it with the developer. A private kind never goes up. */
    val public: Boolean = true,
    /** 1.36 [GRW-01] 스튜디오 요구: 보낸 분의 근무 유형(WorkStyle 이름), 장터 요청 판의 어느 칸에 올릴지. */
    val studio: String? = null,
)

/** [ADMIN-02] 설정 > 문의하기 through the company server; screenshots and tests answer with a fake. */
interface InquiryApi {
    /** False in a build without the company server: 문의하기 opens a mail app instead. */
    val enabled: Boolean
    suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo): Inquiry
    suspend fun mine(device: String): List<Inquiry>
    suspend fun more(device: String, id: String, text: String): Inquiry
    suspend fun delete(device: String, id: String)
    /** 모두의 문의: one topic or all ([topic] null), newest activity or most 나도 그래요 first. */
    suspend fun board(device: String, topic: String?, bySame: Boolean, after: Int): PublicPage
    suspend fun boardThread(device: String, id: String): PublicThread
    /** 나도 그래요, or taking it back; the count after. */
    suspend fun same(device: String, id: String, on: Boolean): Int
    suspend fun flag(device: String, id: String, reason: InquiryFlag)
    /** [INQ-03] 도움이 됐어요 (true), 아쉬워요 (false) or taking it back (null), once there is an answer. */
    suspend fun rate(device: String, id: String, good: Boolean?): Inquiry

    /**
     * [SRV-08] The same question sent again with the same [requestId] (a retry after a lost answer, or
     * the queue of [InquiryOutbox]) gets the first answer back rather than a second question.
     */
    suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo, requestId: String): Inquiry =
        send(device, kind, text, info)

    suspend fun more(device: String, id: String, text: String, requestId: String): Inquiry = more(device, id, text)

    /** 1.36 [PUSH-01] This phone's FCM token for 문의 답 바로 알림, or null to forget it. True when the server can send now. */
    suspend fun pushPhone(device: String, token: String?): Boolean = false

    /** 1.38 [EASE-31] 밤낮 Q&A: the developer's answers to what is asked often, picked to show in 마을. Empty without a server. */
    suspend fun qa(): List<PublicQa> = emptyList()

    /** 1.39 [LOOP-16] 고친 판 안내를 받은 문의에 '해결됐어요'(true)·'아직이에요'(false). */
    suspend fun fixed(device: String, id: String, ok: Boolean): Inquiry = throw CompanyException("not_found")

    /** 1.39 [LOOP-17] 지금 알려진 문제. 서버가 없으면 비어요. */
    suspend fun known(device: String): List<KnownIssue> = emptyList()

    /** 1.39 [LOOP-17] '나도 겪어요'(on) 또는 되돌리기: 그 뒤의 목록. 누르면 그 문제에 묶인 짧은 문의가 내 문의에 생겨요. */
    suspend fun knownMe(device: String, id: String, on: Boolean): List<KnownIssue> = known(device)
}

/**
 * 1.39 [LOOP-17] 지금 알려진 문제 하나: 여럿이 겪는 것만 서버가 보여 줘요. [state] checking(확인 중)·fixing(고치는 중)·fixed(고침, [version]에서),
 * [me] 같은 일을 알린 수, [mine] 이 폰이 '나도 겪어요'를 눌렀는지.
 */
data class KnownIssue(val id: String, val title: String, val state: String, val version: String?, val me: Int, val mine: Boolean,
    /** Work types (WorkStyle names) it is shown to; empty = everyone. */
    val styles: Set<String> = emptySet()) {
    /** PC 1.39 G10: a problem of another kind of work (a rotation's, on a shop's phone) is not shown; one already pressed always is. */
    fun showsFor(style: com.bamnat.core.work.WorkStyle?): Boolean = mine || styles.isEmpty() || style == null || style.name in styles
}

/** 1.38 [EASE-31] One 밤낮 Q&A: the question as the developer put it, and the answer for everyone. */
data class PublicQa(val id: Long, val question: String, val answer: String, val topicName: String)

class InquiryClient(
    private val baseUrl: String = BuildConfig.COMPANY_BASE_URL,
    /** [COMM-11] The X-Hide header, the same writers the plaza hides; null for none. */
    private val hidden: () -> String? = { null },
) : InquiryApi {
    override val enabled: Boolean get() = baseUrl.isNotBlank()

    private fun hide(): Map<String, String> = hidden()?.let { mapOf("X-Hide" to it) }.orEmpty()

    override suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo): Inquiry =
        send(device, kind, text, info, newRequestId())

    override suspend fun send(device: String, kind: InquiryKind, text: String, info: InquiryInfo, requestId: String): Inquiry = serverCall {
        val i = JSONObject().put("app", info.app).put("model", info.model).put("android", info.android)
        info.diag?.takeIf { it.isNotBlank() }?.let { i.put("diag", it.take(DIAG_MAX)) }
        info.pay?.takeIf { it.isNotBlank() }?.let { i.put("pay", it.take(PAY_MAX)) }
        info.setup?.takeIf { it.isNotBlank() }?.let { i.put("setup", it.take(SETUP_MAX)) }
        info.studio?.takeIf { it.isNotBlank() }?.let { i.put("studio", it.take(12)) }
        // [ADMIN-02] The form says the question goes to 모두의 문의; the server keeps it private without this.
        val body = JSONObject().put("v", 1).put("device", device).put("kind", kind.code).put("text", text.trim()).put("info", i)
            .put("public", kind.public && info.public)
        if (info.photos.isNotEmpty()) {
            body.put("photos", org.json.JSONArray(info.photos.take(InquiryPhotos.MAX).map { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }))
        }
        inquiry(onceRequest(baseUrl, "/api/inquiries", body, requestId = requestId))
    }

    override suspend fun qa(): List<PublicQa> = serverCall {
        serverRequest(baseUrl, "GET", "/api/qa").optJSONArray("items").objects().mapNotNull {
            val q = it.optString("q").trim()
            val a = it.optString("a").trim()
            if (q.isEmpty() || a.isEmpty()) null else PublicQa(it.optLong("id"), q, a, it.optString("topicName"))
        }
    }

    override suspend fun mine(device: String): List<Inquiry> = serverCall {
        // 1.39 [LOOP-15] 이 앱의 판: 이미 고친 판이면 서버가 묶인 문의에 바로 안내하고 '반영'으로 보여요(단계적 출시 중에도 이 폰에만).
        serverRequest(baseUrl, "GET", "/api/inquiries", device = device, headers = mapOf("X-App" to APP))
            .optJSONArray("inquiries").objects().map(::inquiry)
    }

    override suspend fun fixed(device: String, id: String, ok: Boolean): Inquiry = serverCall {
        inquiry(serverRequest(baseUrl, "POST", "/api/inquiries/$id/fixed", JSONObject().put("ok", ok), device, headers = mapOf("X-App" to APP)))
    }

    override suspend fun known(device: String): List<KnownIssue> = serverCall {
        knownList(serverRequest(baseUrl, "GET", "/api/known", device = device))
    }

    override suspend fun knownMe(device: String, id: String, on: Boolean): List<KnownIssue> = serverCall {
        knownList(serverRequest(baseUrl, "POST", "/api/known/$id/me", JSONObject().put("on", on).put("app", APP), device))
    }

    private fun knownList(o: JSONObject) = o.optJSONArray("items").objects().mapNotNull {
        val id = it.optString("id")
        val title = it.optString("title").trim()
        if (id.isEmpty() || title.isEmpty()) null
        else KnownIssue(id, title, it.optString("state", "checking"), it.optStringOrNull("version"), it.optInt("me"), it.optBoolean("mine"),
            it.optJSONArray("styles")?.let { a -> (0 until a.length()).map { n -> a.optString(n) }.filter(String::isNotEmpty).toSet() }.orEmpty())
    }

    override suspend fun more(device: String, id: String, text: String): Inquiry = more(device, id, text, newRequestId())

    override suspend fun more(device: String, id: String, text: String, requestId: String): Inquiry = serverCall {
        inquiry(onceRequest(baseUrl, "/api/inquiries/$id/messages", JSONObject().put("text", text.trim()), device, requestId))
    }

    /** One already gone counts as deleted. */
    override suspend fun pushPhone(device: String, token: String?): Boolean = serverCall {
        val body = JSONObject().put("device", device).apply { if (token != null) put("token", token) }
        serverRequest(baseUrl, if (token != null) "POST" else "DELETE", "/api/push/phone", body, device = device).optBoolean("on")
    }

    override suspend fun delete(device: String, id: String) = serverCall {
        try {
            serverRequest(baseUrl, "DELETE", "/api/inquiries/$id", device = device)
        } catch (e: CompanyException) {
            if (e.code != "not_found") throw e
        }
        Unit
    }

    override suspend fun board(device: String, topic: String?, bySame: Boolean, after: Int): PublicPage = serverCall {
        val query = listOfNotNull(topic?.let { "topic=$it" }, "sort=same".takeIf { bySame }, "after=$after".takeIf { after > 0 })
            .joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
        val o = serverRequest(baseUrl, "GET", "/api/inquiries/public$query", device = device, headers = hide())
        val names = o.optJSONObject("topicNames")
        val counts = o.optJSONObject("topics")
        PublicPage(
            items = o.optJSONArray("inquiries").objects().map {
                PublicInquiry(
                    id = it.getString("id"),
                    kind = InquiryKind.of(it.optString("kind")),
                    status = it.optString("status", "open"),
                    preview = it.optString("preview"),
                    answers = it.optInt("answers"),
                    same = it.optInt("same"),
                    sameByMe = it.optBoolean("sameByMe"),
                    mine = it.optBoolean("mine"),
                    topic = it.optString("topic", "etc"),
                    topicName = it.optString("topicName", "기타"),
                    updatedAt = Instant.parse(it.getString("updatedAt")),
                    author = it.optStringOrNull("author")?.takeIf { a -> com.bamnat.core.plaza.HideList.MARK.matches(a) },
                )
            },
            topics = names?.keys()?.asSequence()?.map { it to names.optString(it) }?.toList().orEmpty(),
            counts = counts?.keys()?.asSequence()?.associateWith { counts.optInt(it) }.orEmpty(),
            next = if (o.isNull("next")) null else o.optInt("next"),
            hiddenByYou = o.optInt("hiddenByYou"),
        )
    }

    override suspend fun boardThread(device: String, id: String): PublicThread = serverCall {
        val o = serverRequest(baseUrl, "GET", "/api/inquiries/public/$id", device = device, headers = hide())
        PublicThread(
            id = o.getString("id"),
            kind = InquiryKind.of(o.optString("kind")),
            status = o.optString("status", "open"),
            topicName = o.optString("topicName", "기타"),
            same = o.optInt("same"),
            sameByMe = o.optBoolean("sameByMe"),
            flaggedByMe = o.optBoolean("flaggedByMe"),
            mine = o.optBoolean("mine"),
            createdAt = Instant.parse(o.getString("createdAt")),
            messages = messages(o),
            hidden = o.optJSONObject("hidden")?.optString("text"),
            author = o.optStringOrNull("author")?.takeIf { com.bamnat.core.plaza.HideList.MARK.matches(it) },
        )
    }

    override suspend fun same(device: String, id: String, on: Boolean): Int = serverCall {
        serverRequest(baseUrl, "POST", "/api/inquiries/$id/same", JSONObject().put("device", device).put("on", on)).optInt("same")
    }

    override suspend fun flag(device: String, id: String, reason: InquiryFlag) = serverCall {
        onceRequest(baseUrl, "/api/inquiries/$id/flag", JSONObject().put("device", device).put("reason", reason.code))
        Unit
    }

    override suspend fun rate(device: String, id: String, good: Boolean?): Inquiry = serverCall {
        inquiry(serverRequest(baseUrl, "POST", "/api/inquiries/$id/rating", JSONObject().put("good", good ?: JSONObject.NULL), device))
    }

    private fun messages(o: JSONObject) = o.optJSONArray("messages").objects().map {
        InquiryMessage(fromDeveloper = it.optString("from") == "admin", text = it.optString("text"), at = Instant.parse(it.getString("at")))
    }

    private fun inquiry(o: JSONObject) = Inquiry(
        id = o.getString("id"),
        kind = InquiryKind.of(o.optString("kind")),
        status = o.optString("status", "open"),
        createdAt = Instant.parse(o.getString("createdAt")),
        updatedAt = Instant.parse(o.getString("updatedAt")),
        messages = messages(o),
        public = o.optBoolean("public"),
        same = o.optInt("same"),
        hidden = o.optJSONObject("hidden")?.optString("text"),
        stage = o.optJSONObject("progress")?.optString("stage")?.ifEmpty { null } ?: "received",
        version = o.optJSONObject("progress")?.takeUnless { it.isNull("version") }?.optString("version")?.ifEmpty { null },
        rating = when (o.optInt("rating")) { 1 -> true; -1 -> false; else -> null },
        photos = o.optInt("photos"),
        photosSkipped = o.optStringOrNull("photosSkipped"),
        studioAskers = o.optJSONObject("studio")?.optInt("askers") ?: 0,
        studioGoal = o.optJSONObject("studio")?.optInt("goal") ?: 0,
        fixed = o.optInt("fixed"),
        askFixed = o.optBoolean("askFixed"),
    )

    companion object {
        /** 1.39 [LOOP-15] 서버에 알리는 이 앱의 판: "1.39.0 (53)". */
        val APP: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        /** [SRV-08] A random id for one request, kept with it so a retry is the same request. */
        fun newRequestId(): String = java.util.UUID.randomUUID().toString()

        /** The longest question the server takes, and the longest alarm check. */
        const val TEXT_MAX = 2000
        const val DIAG_MAX = 6000
        const val PAY_MAX = 3000
        const val SETUP_MAX = 3000
    }
}

/**
 * [ADMIN-02] The developer's answers this phone has already shown, so a new one can be pointed out.
 * The questions themselves stay on the server, found by the random install id of [CompanyStore].
 */
class InquiryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("inquiry", Context.MODE_PRIVATE)

    private val _unread = MutableStateFlow(prefs.getInt("unread", 0))
    /** Answers not yet looked at, as of the last time the questions were fetched. */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** This phone has sent a question, so looking for answers is worth a call to the server. */
    var asked: Boolean
        get() = prefs.getBoolean("asked", false)
        set(v) = prefs.edit().putBoolean("asked", v).apply()

    fun seen(id: String): Int = prefs.getInt("seen_$id", 0)

    /** 1.38 [EASE-32] Questions already thanked for in 새로 생긴 것 ("보내 주신 의견으로 고쳤어요"), so each is said once. */
    fun thanked(id: String): Boolean = id in prefs.getStringSet("thanked", emptySet()).orEmpty()

    fun markThanked(ids: Collection<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet("thanked", prefs.getStringSet("thanked", emptySet()).orEmpty() + ids).apply()
    }

    /**
     * 1.38 [EASE-32] (오늘근무 분석 P13·P14, R9 응대): the user's own questions whose fix is in the version now installed:
     * 반영 at [version] or before, not thanked for yet. Each as (id, a short line of what was asked).
     */
    fun fixedNow(list: List<Inquiry>, version: String): List<Pair<String, String>> = list
        .filter { it.stage == "released" && it.version != null && compareVersion(it.version, version) <= 0 && !thanked(it.id) }
        .map { i -> i.id to (i.messages.firstOrNull { !it.fromDeveloper }?.text.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            .let { if (it.length > 40) it.take(39) + "…" else it }.ifEmpty { "보내 주신 의견" }) }

    /** After fetching: counts answers the user has not opened yet. */
    fun update(list: List<Inquiry>) {
        val n = list.sumOf { (it.answers - seen(it.id)).coerceAtLeast(0) }
        _unread.value = n
        prefs.edit().putInt("unread", n).apply()
    }

    /** The user opened [inquiry]: its answers so far are read. */
    fun read(inquiry: Inquiry, all: List<Inquiry>) {
        prefs.edit().putInt("seen_${inquiry.id}", inquiry.answers).apply()
        update(all)
    }

    fun forget(id: String) {
        prefs.edit {
            remove("seen_$id")
            remove("reply_$id")
        }
    }

    private val _draft = MutableStateFlow(loadDraft())
    /** [UX-05] A question being written and not sent yet. It stays on this phone and is never sent by itself. */
    val draft: StateFlow<InquiryDraft?> = _draft.asStateFlow()

    private fun loadDraft(): InquiryDraft? {
        val text = prefs.getString("draft_text", null)?.takeIf { it.isNotBlank() } ?: return null
        return InquiryDraft(prefs.getString("draft_kind", null)?.let(InquiryKind::of), text)
    }

    /** Keeps [text] for later; a blank one clears the draft. */
    fun saveDraft(kind: InquiryKind?, text: String) {
        val next = InquiryDraft(kind, text).takeIf { text.isNotBlank() }
        if (next == _draft.value) return
        _draft.value = next
        prefs.edit {
            if (next == null) {
                remove("draft_text")
                remove("draft_kind")
            } else {
                putString("draft_text", text)
                if (kind != null) putString("draft_kind", kind.code) else remove("draft_kind")
            }
        }
    }

    fun clearDraft() = saveDraft(null, "")

    /** [UX-05] More to say on question [id], kept until it is sent. */
    fun reply(id: String): String = prefs.getString("reply_$id", "").orEmpty()

    fun saveReply(id: String, text: String) {
        prefs.edit { if (text.isBlank()) remove("reply_$id") else putString("reply_$id", text) }
    }

    private val _outbox = MutableStateFlow(loadOutbox())
    /** [INQ-05] Questions waiting to be sent, oldest first; at most [OUTBOX_MAX]. */
    val outbox: StateFlow<List<QueuedInquiry>> = _outbox.asStateFlow()

    private fun loadOutbox(): List<QueuedInquiry> = runCatching {
        val a = org.json.JSONArray(prefs.getString("outbox", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(QueuedInquiry::of) }
    }.getOrDefault(emptyList())

    private fun saveOutbox(list: List<QueuedInquiry>) {
        _outbox.value = list
        prefs.edit { putString("outbox", org.json.JSONArray(list.map { it.toJson() }).toString()) }
    }

    /** False when [OUTBOX_MAX] already wait. */
    fun queue(q: QueuedInquiry): Boolean {
        if (_outbox.value.size >= OUTBOX_MAX) return false
        saveOutbox(_outbox.value.filter { it.requestId != q.requestId } + q)
        return true
    }

    fun unqueue(requestId: String) = saveOutbox(_outbox.value.filter { it.requestId != requestId })

    fun refused(requestId: String, code: String) = saveOutbox(_outbox.value.map { if (it.requestId == requestId) it.copy(refused = code) else it })

    companion object {
        const val OUTBOX_MAX = 5
    }
}

/** [UX-05] A question not sent yet: what it is about (if chosen) and what is written so far. */
data class InquiryDraft(val kind: InquiryKind?, val text: String)

/**
 * [INQ-05] A question waiting to be sent ([InquiryOutbox]); [refused] is the server's word when it would
 * not take it, so the user can change it.
 */
data class QueuedInquiry(
    val requestId: String,
    val kind: InquiryKind,
    val text: String,
    val info: InquiryInfo,
    val at: Instant,
    val refused: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", requestId).put("kind", kind.code).put("text", text).put("at", at.toString())
        .put("app", info.app).put("model", info.model).put("android", info.android)
        .apply {
            info.diag?.let { put("diag", it) }
            info.pay?.let { put("pay", it) }
            info.setup?.let { put("setup", it) }
            if (!info.public) put("public", false)
            refused?.let { put("refused", it) }
        }

    companion object {
        fun of(o: JSONObject): QueuedInquiry? = runCatching {
            QueuedInquiry(
                requestId = o.getString("id"), kind = InquiryKind.of(o.optString("kind")), text = o.getString("text"),
                info = InquiryInfo(o.optString("app"), o.optString("model"), o.optString("android"),
                    diag = o.optStringOrNull("diag"), pay = o.optStringOrNull("pay"), setup = o.optStringOrNull("setup"),
                    public = o.optBoolean("public", true)),
                at = Instant.parse(o.getString("at")), refused = o.optStringOrNull("refused"),
            )
        }.getOrNull()
    }
}

/** 1.38 [EASE-32] "1.38.0" against "1.37.2": numbers part by part; letters after a number are ignored. */
internal fun compareVersion(a: String, b: String): Int {
    fun parts(v: String) = v.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val x = parts(a); val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = (x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0)
        if (d != 0) return d
    }
    return 0
}
