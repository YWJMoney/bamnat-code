package com.bamnat.app.company

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 1.36 [VIL-13] 마을 V13 간직하기: plaza posts kept to read again, as they were when kept, on this phone only (the server keeps no list of
 * what anyone saved). Newest first, at most [MAX].
 */
class SavedPosts private constructor(context: Context) {
    data class Saved(val id: String, val room: String, val roomName: String, val body: String, val kind: String, val at: Instant, val savedAt: Instant)

    private val prefs = context.getSharedPreferences("village_saved", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<Saved>> = _items.asStateFlow()

    fun has(id: String): Boolean = _items.value.any { it.id == id }

    fun save(p: TalkPost, room: String, roomName: String, now: Instant = com.bamnat.core.time.Today.instant()) {
        write((listOf(Saved(p.id, room, roomName, p.body, p.kind, p.at, now)) + _items.value.filter { it.id != p.id }).take(MAX))
    }

    fun remove(id: String) = write(_items.value.filter { it.id != id })

    /** Puts back one taken out (스낵바 되돌리기), where it was. */
    fun restore(s: Saved) = write((_items.value + s).distinctBy { it.id }.sortedByDescending { it.savedAt }.take(MAX))

    private fun write(list: List<Saved>) {
        prefs.edit().putString(KEY, JSONArray(list.map {
            JSONObject().put("id", it.id).put("room", it.room).put("name", it.roomName).put("body", it.body).put("kind", it.kind)
                .put("at", it.at.toString()).put("saved", it.savedAt.toString())
        }).toString()).apply()
        _items.value = list
    }

    private fun read(): List<Saved> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Saved(it.getString("id"), it.optString("room"), it.optString("name"), it.optString("body"), it.optString("kind", "talk"),
                Instant.parse(it.getString("at")), Instant.parse(it.getString("saved")))
        }
    }.getOrDefault(emptyList())

    companion object {
        const val MAX = 200
        private const val KEY = "items"
        @Volatile private var one: SavedPosts? = null
        fun of(context: Context): SavedPosts = one ?: synchronized(this) { one ?: SavedPosts(context.applicationContext).also { one = it } }
    }
}

/** 1.36 [VIL-13] One of my posts or replies in the plazas, as GET /api/posts/mine lists it. */
data class MyPost(val id: String, val room: String, val roomName: String, val body: String, val kind: String, val parent: String?,
    val replies: Int, val at: Instant, val hidden: Boolean)

internal fun myPostsOf(o: JSONObject): List<MyPost> {
    val a = o.optJSONArray("items") ?: return emptyList()
    return (0 until a.length()).map { a.getJSONObject(it) }.map {
        MyPost(it.getString("id"), it.optString("room"), it.optString("name"), it.optString("body"), it.optString("kind", "talk"),
            it.optString("parent").takeIf { p -> p.isNotEmpty() }, it.optInt("replies"), Instant.parse(it.getString("at")), it.optBoolean("hidden"))
    }
}

/** 1.36 [VIL-06] What 마을 검색 found on the plazas and 요청 판 (장터 is searched through the recipe list). */
data class VillageFound(val posts: List<FoundPost>, val requests: List<FoundRequest>)
data class FoundPost(val id: String, val room: String, val roomName: String, val body: String, val kind: String, val replies: Int, val at: Instant)
data class FoundRequest(val id: String, val body: String, val likes: Int, val state: String, val code: String?)

internal fun villageFoundOf(o: JSONObject): VillageFound {
    val p = o.optJSONArray("posts") ?: JSONArray()
    val r = o.optJSONArray("requests") ?: JSONArray()
    return VillageFound(
        (0 until p.length()).map { p.getJSONObject(it) }.map {
            FoundPost(it.getString("id"), it.optString("room"), it.optString("name"), it.optString("body"), it.optString("kind", "talk"),
                it.optInt("replies"), Instant.parse(it.getString("at")))
        },
        (0 until r.length()).map { r.getJSONObject(it) }.map {
            FoundRequest(it.getString("id"), it.optString("body"), it.optInt("likes"), it.optString("state"), it.optString("code").takeIf { c -> c.isNotEmpty() })
        },
    )
}

/** 1.36 [VIL-19] 이달의 주제 공모: this month's theme (null: none), how many are in, mine, and last month's picks as (code, title). */
data class Contest(val month: String, val theme: String?, val entries: Int, val mine: List<String>, val lastTheme: String?,
    val picks: List<Pair<String, String>>)

internal fun contestOf(o: JSONObject): Contest {
    val mine = o.optJSONArray("mine") ?: JSONArray()
    val last = o.optJSONObject("last")
    val picks = last?.optJSONArray("picks") ?: JSONArray()
    return Contest(o.optString("month"), o.optString("theme").takeIf { it.isNotEmpty() && !o.isNull("theme") }, o.optInt("entries"),
        (0 until mine.length()).map { mine.getString(it) }, last?.optString("theme"),
        (0 until picks.length()).map { picks.getJSONObject(it) }.map { it.optString("code") to it.optString("title") })
}
