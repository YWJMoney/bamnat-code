package com.bamnat.app.data

import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 2.0.0 [RVK-13] 뿌리 7 '조용히 버려지는 것 없애기' (검토 MONEY-43·60·62, U06b-13, I06ba-06): whenever the app on its own leaves
 * something out as a second copy, or joins two things into one, nothing is lost without a word. What it did is kept here for
 * [KEEP_DAYS] days with why, listed under '자동으로 정리한 것' and put back with one tap.
 *
 * Kept on this phone only, in the no-backup folder (a payment text never leaves the phone), at most [MAX] at a time.
 *
 * For other areas (급여 PAYU-52, 레시피 RCP-08, 달력 CAL-05): call [add] where the app drops or joins something, with an [Item.kind]
 * of your area ("pay.…", "recipe.…", "calendar.…") and what is needed to put it back in [Item.payload], and [register] the way
 * back once at start (AppContainer). [items] filtered by [Item.area] is what a screen lists; [restore] puts one back.
 * How to: docs/design/자동으로-정리한-것.md.
 */
class AutoTidy(private val file: File?, private val clock: () -> Long = Today::millis) {
    data class Item(
        val id: String,
        val at: Long,
        /** "money", "invest", "pay", "recipe", "calendar": which screen lists it. */
        val area: String,
        val calendarId: String,
        /** What it was, as a list shows it: "스타벅스 4,500원". */
        val title: String,
        /** Why the app did it, in one sentence: "카드와 은행이 같은 결제를 알려 와 한 줄만 적었어요". */
        val why: String,
        /** The way back, [register]ed by its area. */
        val kind: String,
        /** What the way back needs; never shown. */
        val payload: String,
    ) {
        /** Whole days it is still kept, as the list says it: 30 the moment it is added, 1 on its last day. */
        fun daysLeft(now: Long): Int = ((at + KEEP_MILLIS - now + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceIn(1, KEEP_DAYS.toInt())
    }

    private val lock = Mutex()
    private val _items = MutableStateFlow(read())
    /** Newest first. */
    val items: StateFlow<List<Item>> = _items.asStateFlow()
    private val restorers = mutableMapOf<String, suspend (Item) -> Boolean>()

    /** The way back for items of [kind]: true when it was put back (the item then goes from the list). */
    fun register(kind: String, restore: suspend (Item) -> Boolean) {
        synchronized(restorers) { restorers[kind] = restore }
    }

    suspend fun add(area: String, calendarId: String, title: String, why: String, kind: String, payload: String): Item = lock.withLock {
        val item = Item(UUID.randomUUID().toString(), clock(), area, calendarId, title, why, kind, payload)
        _items.value = (listOf(item) + _items.value).take(MAX)
        write()
        item
    }

    /** Puts [id] back with its area's way back. False when it is gone or could not be put back; then it stays listed. */
    suspend fun restore(id: String): Boolean {
        val item = _items.value.firstOrNull { it.id == id } ?: return false
        val way = synchronized(restorers) { restorers[item.kind] } ?: return false
        val ok = runCatching { way(item) }.getOrDefault(false)
        if (ok) forget(id)
        return ok
    }

    /** Leaves [id] out of the list for good (the user saw it and it was right). */
    suspend fun forget(id: String) = lock.withLock {
        _items.value = _items.value.filter { it.id != id }
        write()
    }

    /** Drops what is older than [KEEP_DAYS] days; at each app start. */
    suspend fun purge() = lock.withLock {
        val now = clock()
        val keep = _items.value.filter { now - it.at <= KEEP_MILLIS }
        if (keep.size != _items.value.size) {
            _items.value = keep
            write()
        }
    }

    private fun read(): List<Item> {
        val f = file?.takeIf { it.exists() } ?: return emptyList()
        return runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                Item(o.getString("id"), o.getLong("at"), o.optString("area"), o.optString("calendar"), o.optString("title"),
                    o.optString("why"), o.optString("kind"), o.optString("payload"))
            }
        }.getOrDefault(emptyList()).filter { clock() - it.at <= KEEP_MILLIS }
    }

    private suspend fun write() {
        val f = file ?: return
        val text = JSONArray(_items.value.map { i ->
            JSONObject().put("id", i.id).put("at", i.at).put("area", i.area).put("calendar", i.calendarId).put("title", i.title)
                .put("why", i.why).put("kind", i.kind).put("payload", i.payload)
        }).toString()
        withContext(Dispatchers.IO) {
            runCatching {
                f.parentFile?.mkdirs()
                val tmp = File(f.path + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
            }
        }
    }

    companion object {
        const val AREA_MONEY = "money"
        const val AREA_INVEST = "invest"
        const val KEEP_DAYS = 30L
        const val KEEP_MILLIS = KEEP_DAYS * 24 * 60 * 60 * 1000L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX = 300
    }
}
