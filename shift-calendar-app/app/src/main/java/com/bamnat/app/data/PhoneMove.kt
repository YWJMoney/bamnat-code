package com.bamnat.app.data

import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * 2.0.0 [RVL-05][RVL-06][RVL-07] (검토 DATA-02·DATA-10·U12-03·T12-04·T12-05·T12-06) A phone the user's data came to by Android's own way
 * (구글 계정으로 되살리기, 휴대폰 옮기기·스마트 스위치) opens whole, not half.
 *
 * Android takes the database and the files, but of the preference files only settings.xml (backup_rules.xml): 입사일, 모든 조, 근무 약어,
 * 끈 기능, 회사·급여 방식, 희망 오프, 테마 옷장, the links the user made and the 프로 recovery code stayed behind, and nothing knew the
 * phone was a new one (it opened straight on the calendar, '첫 설정 끝' being in settings.xml). Now:
 * - [keep] writes what stays behind ([BackupManager.carryOut]) to [carry] among the files, and the keys ([BackupManager.carryKeys], with
 *   the received links followed, [com.bamnat.app.share.ReceivedCalendarStore.links]) to [keys], which Android takes only sealed: a
 *   phone-to-phone move, or a cloud backup encrypted with the phone's lock (data_extraction_rules.xml, requireFlags). Before Android 9
 *   that cannot be asked for, so there the keys stay on the phone.
 * - [here] is in no-backup storage and never goes. Missing while [carry] or [keys] is there, this is a phone the data came to: [arrive]
 *   takes them in once, and [moved] says over the calendar what came, what to allow again and the received links to join again.
 */
class PhoneMove(
    private val carry: File,
    private val keys: File,
    /** Says this phone already took its carry (or never had one to take): never in a backup. */
    private val here: File,
    /** What [moved] says, until closed (no-backup storage too). */
    private val state: File,
    private val out: () -> JSONObject,
    private val outKeys: () -> JSONObject,
    private val received: () -> JSONArray,
    /** Takes a carry in; returns how many of the user's links came along. */
    private val take: (JSONObject) -> Int,
    /** False before Android 9, where Android cannot be asked to take the keys only sealed. */
    private val sealedKeys: Boolean = true,
    private val clock: () -> Instant = Today::instant,
) {
    /** A link someone shared that the old phone followed, to be read again here (its days are not carried, SHR-02). */
    data class Waiting(val id: String, val title: String, val mine: Boolean, val adopted: Boolean, val follow: String?)

    /**
     * What came with the move: [links] of the user's own links (with their keys), [waiting] received links to join again, [keys]
     * false when the sealed part held the user's own link keys or 프로 recovery code and did not come (a cloud backup of a phone
     * without a screen lock leaves it out), [lostReceived] received links the old phone followed that did not come for the same
     * reason, [parts] how many kinds of settings were taken in.
     */
    data class Moved(val at: Instant, val links: Int, val waiting: List<Waiting>, val keys: Boolean, val parts: Int, val lostReceived: Int = 0)

    private val _moved = MutableStateFlow(load())
    /** The card's state; null once closed or on a phone nothing came to. */
    val moved: StateFlow<Moved?> = _moved.asStateFlow()

    /**
     * At the app's start, before anything writes the carry: on the first start of a phone the data came to, takes the carry in (once)
     * and returns what came. Null on every other start.
     */
    @Synchronized
    fun arrive(): Moved? {
        if (here.exists()) return null
        val o = read(carry)
        val k = read(keys)
        val moved = if (o == null && k == null) null else {
            val all = JSONObject()
            o?.let { p -> p.keys().forEach { all.put(it, p.get(it)) } }
            // The sealed part's 프로 (with its code) over the plain one.
            k?.let { p -> p.keys().forEach { if (it != RECEIVED) all.put(it, p.get(it)) } }
            val links = runCatching { take(all) }.getOrDefault(0)
            val waiting = k?.optJSONArray(RECEIVED)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::waiting) } }.orEmpty()
            // What the sealed part held, as the plain carry says it (only counts, never a key): an old phone with nothing to seal lost nothing.
            val held = o?.optJSONObject(SEALED)
            Moved(clock(), links, waiting,
                keys = k != null || held?.optBoolean("mine", true) == false,
                parts = (o?.length() ?: 0) - (if (held != null) 1 else 0),
                lostReceived = if (k != null) 0 else held?.optInt(RECEIVED) ?: 0)
        }
        moved?.let(::save)
        mark()
        _moved.value = moved ?: _moved.value
        return moved
    }

    /** Writes what Android's backup should take now: at each start, after [arrive], and when the app goes to the background. */
    @Synchronized
    fun keep() {
        // Before [arrive] ran, the carry here may be the one that came with the phone: it is never written over unread.
        if (!here.exists()) return
        val sealed = outKeys().put(RECEIVED, received())
        val mine = sealed.has("pro") || (sealed.optJSONArray("shareLinks")?.length() ?: 0) > 0
        write(carry, out().put(SEALED, JSONObject().put("mine", mine).put(RECEIVED, sealed.optJSONArray(RECEIVED)?.length() ?: 0)))
        if (sealedKeys) write(keys, sealed) else keys.delete()
    }

    /** A received link was read again here (or is gone for good): it no longer waits. */
    @Synchronized
    fun joined(ids: Collection<String>) {
        val m = _moved.value ?: return
        val next = m.copy(waiting = m.waiting.filter { it.id !in ids })
        save(next)
        _moved.value = next
    }

    /** The card was closed: it does not come back. */
    @Synchronized
    fun close() {
        state.delete()
        _moved.value = null
    }

    private fun mark() {
        here.parentFile?.mkdirs()
        here.writeText(clock().toString())
    }

    private fun waiting(o: JSONObject): Waiting? {
        val id = o.optString("id").takeIf { it.matches(LINK_ID) } ?: return null
        return Waiting(id, o.optString("title"), o.optBoolean("mine"), o.optBoolean("adopted"), o.optString("follow").takeIf { it.isNotEmpty() })
    }

    private fun save(m: Moved) = write(state, JSONObject().put("at", m.at.toString()).put("links", m.links).put("keys", m.keys).put("parts", m.parts)
        .put("lostReceived", m.lostReceived)
        .put("waiting", JSONArray().apply {
            m.waiting.forEach { w ->
                put(JSONObject().put("id", w.id).put("title", w.title).put("mine", w.mine).put("adopted", w.adopted).putOpt("follow", w.follow))
            }
        }))

    private fun load(): Moved? = read(state)?.let { o ->
        runCatching {
            Moved(Instant.parse(o.getString("at")), o.optInt("links"),
                o.optJSONArray("waiting")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::waiting) } }.orEmpty(),
                o.optBoolean("keys"), o.optInt("parts"), o.optInt("lostReceived"))
        }.getOrNull()
    }

    private fun read(f: File): JSONObject? = runCatching { f.takeIf { it.exists() }?.readText()?.let(::JSONObject) }.getOrNull()

    /** Whole or not at all: a half-written carry is never what Android takes. */
    private fun write(f: File, o: JSONObject) {
        runCatching {
            f.parentFile?.mkdirs()
            val part = File(f.parentFile, f.name + ".part")
            part.writeText(o.toString())
            if (!part.renameTo(f)) {
                f.writeText(o.toString())
                part.delete()
            }
        }
    }

    companion object {
        const val RECEIVED = "received"
        /** In the plain carry: whether the sealed part holds the user's own keys, and how many received links (never a key). */
        const val SEALED = "sealedHolds"
        /** The carry, among the files Android's backup takes. */
        const val CARRY = "carry/settings.json"
        /** The sealed part, in the app's own folder outside files (domain root in the backup rules). */
        const val KEYS = "carry_keys/keys.json"
        private val LINK_ID = Regex("[A-Za-z0-9]{16,32}")
    }
}
