package com.bamnat.app.inbox

import android.content.Context
import androidx.core.content.edit
import com.bamnat.app.NoticeLinks
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 2.0.0 [RVP-43] Where a row sits in the 알림함: what people did first, then what the app says, what it suggests last (HQ-49). */
enum class InboxSection {
    /** 사람들 반응: a comment, a like, 맞아요·달라요, an answer on something of the user's. */
    PEOPLE,
    /** 안내: what the app has to say (the calendar's notices are here too). */
    NOTICE,
    /** 권유: a feature not tried yet, inviting a friend, putting a rule of one's own on the plaza. One at a time. */
    SUGGEST,
}

/**
 * 2.0.0 [RVP-43] One row of the 알림함, put by a feature with [Inbox.post] (검토 HQ-49 '알림함 줄을 다른 갈래가 넣을 수 있는 길').
 *
 * [key] names the row: the same key again replaces it and brings it to the top of its [section] (a 묶음: "댓글 3개 · 좋아요 5개" with
 * [count] 8). [kind] is what '다시 보지 않기' turns off ("market.reaction", "plaza.reaction", "invite.friend", "suggest.companyRule":
 * the area, a dot, the reason). A tap goes to [target] (a [NoticeLinks.Target], with [payload] and [calendar] as a system notice would
 * pass them) or to [feature]. [at] is when it happened.
 */
data class InboxRow(
    val key: String,
    val kind: String,
    val section: InboxSection,
    val title: String,
    val line: String,
    val target: NoticeLinks.Target? = null,
    val payload: String? = null,
    val calendar: String? = null,
    val feature: FeatureRoute? = null,
    val at: Long = Today.millis(),
    val count: Int = 1,
) {
    /** Goes where the row says (nothing when it names no place). */
    fun open() {
        when {
            target != null -> NoticeLinks.open(target, calendar, payload)
            feature != null -> com.bamnat.app.ui.guide.FeatureLinks.open(feature)
        }
    }
}

/**
 * 2.0.0 [RVP-43] 검토 HQ-49·50 (사용자 11-12 20:38·20:40 KST "알림이 있다면 … 표식으로 알려 주고 전체 지움 등을 넣어서 비울 수 있게",
 * "사람들과 상호작용하는 모든 알림이 알림함으로", "거부를 하면 여러 번 반복해서 알림으로 보여 주지 마"): the rows other features put
 * in the bell's 알림함, on this phone only. Nothing here is sent anywhere.
 *
 * The rows themselves stay on the phone ([FILE], SettingsFilesBackup.DEVICE_ONLY), at most [MAX_ROWS] and [KEEP_DAYS] days. What the user
 * turned off ('다시 보지 않기' by kind, and 권유 끄기) is in the backup ([MUTED_FILE], SettingsFilesBackup.PARTS), so a new phone does
 * not ask again what was said no to.
 */
class Inbox private constructor(context: Context) {
    internal val app: Context = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mutedPrefs = app.getSharedPreferences(MUTED_FILE, Context.MODE_PRIVATE)
    private val _rows = MutableStateFlow(read())
    private val _muted = MutableStateFlow(mutedPrefs.getStringSet(K_KINDS, emptySet()).orEmpty().toSet())
    private val _suggestOff = MutableStateFlow(mutedPrefs.getBoolean(K_SUGGEST_OFF, false))

    /** The rows, the newest first within each section, people first. */
    val rows: StateFlow<List<InboxRow>> = _rows.asStateFlow()
    /** The kinds turned off with '다시 보지 않기'. */
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()
    /** 권유 끄기: no suggestion rows at all (설정 › 기능 켜고 끄기, beside '안 써 본 기능 알려 주기'). */
    val suggestOff: StateFlow<Boolean> = _suggestOff.asStateFlow()

    /** Whether a row of [kind] in [section] may come in. */
    fun allowed(kind: String, section: InboxSection): Boolean =
        kind !in _muted.value && !(section == InboxSection.SUGGEST && _suggestOff.value)

    /** Puts [row] in; false (and nothing put) when its kind is turned off. A 권유 takes the place of the one before it. */
    fun post(row: InboxRow): Boolean {
        if (!allowed(row.kind, row.section)) return false
        edit { list ->
            val rest = list.filter { it.key != row.key && !(row.section == InboxSection.SUGGEST && it.section == InboxSection.SUGGEST) }
            listOf(row) + rest
        }
        return true
    }

    fun remove(key: String) = edit { list -> list.filter { it.key != key } }

    /** '전체 지움': every row goes; what went comes back with [putBack] (되돌리기). */
    fun clearAll(): List<InboxRow> {
        val gone = _rows.value
        edit { emptyList() }
        return gone
    }

    fun putBack(rows: List<InboxRow>) = edit { list -> (rows + list).distinctBy { it.key } }

    /** '다시 보지 않기': rows of [kind] go and do not come again; [label] is how 기능 켜고 끄기 names it to turn back on. */
    fun mute(kind: String, label: String = kind) {
        mutedPrefs.edit { putString(K_LABEL + kind, KIND_NAMES[kind] ?: label) }
        setMuted(_muted.value + kind)
        edit { list -> list.filter { it.kind != kind } }
    }

    fun unmute(kind: String) = setMuted(_muted.value - kind)

    /** The words a turned-off [kind] was shown with. */
    fun label(kind: String): String = mutedPrefs.getString(K_LABEL + kind, null) ?: kind

    fun setSuggestOff(off: Boolean) {
        _suggestOff.value = off
        mutedPrefs.edit { putBoolean(K_SUGGEST_OFF, off) }
        if (off) edit { list -> list.filter { it.section != InboxSection.SUGGEST } }
    }

    private fun setMuted(kinds: Set<String>) {
        _muted.value = kinds
        mutedPrefs.edit { putStringSet(K_KINDS, kinds) }
    }

    private fun edit(change: (List<InboxRow>) -> List<InboxRow>) {
        synchronized(this) {
            val now = Today.millis()
            val next = change(_rows.value)
                .filter { now - it.at < KEEP_DAYS * DAY_MS }
                .sortedWith(compareBy<InboxRow> { it.section.ordinal }.thenByDescending { it.at })
                .take(MAX_ROWS)
            _rows.value = next
            prefs.edit { putString(K_ROWS, JSONArray(next.map(::encode)).toString()) }
        }
    }

    private fun read(): List<InboxRow> = runCatching {
        val a = JSONArray(prefs.getString(K_ROWS, null) ?: return emptyList())
        val now = Today.millis()
        (0 until a.length()).mapNotNull { decode(a.optJSONObject(it) ?: return@mapNotNull null) }.filter { now - it.at < KEEP_DAYS * DAY_MS }
    }.getOrDefault(emptyList())

    private fun encode(r: InboxRow): JSONObject = JSONObject()
        .put("key", r.key).put("kind", r.kind).put("section", r.section.name).put("title", r.title).put("line", r.line)
        .put("at", r.at).put("count", r.count)
        .apply {
            r.target?.let { put("target", it.name) }
            r.payload?.let { put("payload", it) }
            r.calendar?.let { put("calendar", it) }
            r.feature?.let { f -> put("place", f.place.name); f.focus?.let { put("focus", it) } }
        }

    private fun decode(o: JSONObject): InboxRow? {
        val section = InboxSection.entries.firstOrNull { it.name == o.optString("section") } ?: return null
        return InboxRow(
            key = o.optString("key").ifEmpty { return null },
            kind = o.optString("kind"),
            section = section,
            title = o.optString("title"),
            line = o.optString("line"),
            target = o.optString("target").takeIf { it.isNotEmpty() }?.let { n -> NoticeLinks.Target.entries.firstOrNull { it.name == n } },
            payload = o.optString("payload").takeIf { o.has("payload") },
            calendar = o.optString("calendar").takeIf { o.has("calendar") },
            feature = o.optString("place").takeIf { it.isNotEmpty() }?.let { n ->
                Place.entries.firstOrNull { it.name == n }?.let { FeatureRoute(it, o.optString("focus").takeIf { o.has("focus") }) }
            },
            at = o.optLong("at", Today.millis()),
            count = o.optInt("count", 1),
        )
    }

    companion object {
        /** The rows, on this phone only. */
        private const val FILE = "inbox"
        /** What the user turned off, in the backup. */
        const val MUTED_FILE = "inbox_muted"
        private const val K_ROWS = "rows"
        private const val K_KINDS = "kinds"
        private const val K_SUGGEST_OFF = "suggest_off"
        private const val K_LABEL = "label:"
        /**
         * [RVM-36] HQ-52 How 기능 켜고 끄기 names a kind whose rows each say something different (a row's title names one work, but
         * '다시 보지 않기' turns off the kind for every work).
         */
        val KIND_NAMES = mapOf(
            "market.reaction" to "내 작품에 온 반응",
            "market.decided" to "내 작품 심사 결과",
        )
        const val MAX_ROWS = 60
        const val KEEP_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000

        @Volatile private var instance: Inbox? = null

        /** The one store of the app (a new app, as each test has, reads its own files). */
        fun get(context: Context): Inbox = instance?.takeIf { it.app === context.applicationContext } ?: synchronized(this) {
            instance?.takeIf { it.app === context.applicationContext } ?: Inbox(context).also { instance = it }
        }

        /** Puts [row] in the 알림함; false when the user turned its kind off. Nothing is sent anywhere. */
        fun post(context: Context, row: InboxRow): Boolean = get(context).post(row)

        fun remove(context: Context, key: String) = get(context).remove(key)

        /** For tests: the store read again from its files. */
        internal fun reset() {
            instance = null
        }
    }
}
