package com.bamnat.app.company

import android.content.Context
import org.json.JSONObject

/**
 * [TOWN-02] 1.37 묶음 D (사용자 2026-11-04 "진짜 커뮤니티"): answers to what I wrote in any plaza, said where I look.
 *
 * 1.36 said "내 글에 새 답" only on the card of the room on screen, so a 교대·일근 phone (whose 광장 opens on the rule plaza) or a
 * question asked in another room never showed its answers until the daily 소식. Here the answers others wrote under each of my
 * posts ([CompanyApi.myPosts]: my replies under my own question taken out) are compared with what this phone last saw
 * (device-only `village_ui`, never backed up). The first look only takes note, so nothing old turns up as new.
 */
object VillageReplies {
    private const val PREFS = "village_ui"
    private const val K_SEEN = "reply_seen"

    /** Answers by others under each of my top posts, by post id. */
    fun others(posts: List<MyPost>): Map<String, Int> {
        val mineUnder = posts.mapNotNull { it.parent }.groupingBy { it }.eachCount()
        return posts.filter { it.parent == null && !it.hidden }
            .associate { it.id to (it.replies - (mineUnder[it.id] ?: 0)).coerceAtLeast(0) }
    }

    private fun seen(context: Context): Map<String, Int>? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_SEEN, null) ?: return null
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { o.optInt(it) }
    }.getOrNull()

    /** New answers per post since this phone last saw them; on the very first look, none (and what is there now is noted). */
    fun fresh(context: Context, posts: List<MyPost>): Map<String, Int> {
        val now = others(posts)
        val was = seen(context) ?: run { markSeen(context, posts); return emptyMap() }
        return now.mapValues { (id, n) -> (n - (was[id] ?: 0)).coerceAtLeast(0) }.filterValues { it > 0 }
    }

    /** What [posts] show now counts as seen (kept for the posts still listed, so the note stays small). */
    fun markSeen(context: Context, posts: List<MyPost>) {
        val o = JSONObject()
        others(posts).forEach { (id, n) -> o.put(id, n) }
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_SEEN, o.toString()).apply() }
    }
}
