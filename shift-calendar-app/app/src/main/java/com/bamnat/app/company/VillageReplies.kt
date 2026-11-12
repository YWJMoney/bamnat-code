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
    private const val K_COUNT = "reply_new_count"

    /**
     * [TOWN-07] PC 확인 649e4c4c 제안 (새 답이 있으면 마을 탭에 점): how many new answers there are as last worked out — once a run
     * after the app starts ([refresh]), and each time 광장's line or 내 글 looks.
     */
    private val _count = kotlinx.coroutines.flow.MutableStateFlow(0)
    val count: kotlinx.coroutines.flow.StateFlow<Int> = _count
    @Volatile private var store: android.content.SharedPreferences? = null
    @Volatile private var worked = false
    fun publish(n: Int) {
        worked = true
        _count.value = n.coerceAtLeast(0)
        runCatching { store?.edit()?.putInt(K_COUNT, _count.value)?.apply() }
    }

    /** PC 확인 a54724f7 (점이 앱을 켜고 18초 뒤에 생겨요): the count last worked out shows at once as the app starts. */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = runCatching { prefs.getInt(K_COUNT, 0) }.getOrDefault(0)
        if (store == null) store = prefs
        // A count worked out on this run already is newer than the stored one; store it now that there is somewhere to.
        if (!worked) _count.value = last else if (last != _count.value) runCatching { prefs.edit().putInt(K_COUNT, _count.value).apply() }
    }

    /** Asks the server once and sets [count]; nothing on a phone that never agreed to the plaza's rules. */
    suspend fun refresh(c: com.bamnat.app.AppContainer) {
        if (store == null) restore(c.appContext)
        if (!c.companyApi.enabled || c.settingsRepository.current.plazaRulesAgreed <= 0) return
        val posts = runCatching { c.companyApi.myPosts(c.companyStore.device) }.getOrNull() ?: return
        val fresh = fresh(c.appContext, posts)
        publish(fresh.values.sum())
        // 2.0.0 [RVN-19] HQ-51 the same answers as rows of the 알림함 (사람들 반응), from this one ask.
        runCatching { PlazaInbox.answers(c, posts, fresh) }
    }

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

    /** How recent a post must be, on the very first look, for its answers to count as new. */
    private val RECENT: java.time.Duration = java.time.Duration.ofDays(7)

    /**
     * New answers per post since this phone last saw them. PC 확인 41e73582 (첫 기준이 답이 온 뒤에 잡히면 그 답이 끝내 새 답이 안 돼요):
     * on the very first look, posts of the last 7 days count all their answers as new; only older ones are noted as seen.
     */
    fun fresh(context: Context, posts: List<MyPost>, now: java.time.Instant = com.bamnat.core.time.Today.instant()): Map<String, Int> {
        val counts = others(posts)
        val was = seen(context) ?: run {
            val old = posts.filter { it.parent == null && it.at.isBefore(now.minus(RECENT)) }.map { it.id }.toSet()
            write(context, counts.filterKeys { it in old })
            counts.filterKeys { it in old }
        }
        return counts.mapValues { (id, n) -> (n - (was[id] ?: 0)).coerceAtLeast(0) }.filterValues { it > 0 }
    }

    /**
     * PC 확인 41e73582: answers read in the talk itself count as seen too, so 광장's line does not keep saying them. [posts] are a
     * talk's posts as (id, parent, mine).
     */
    fun seenInTalk(context: Context, posts: List<Triple<String, String?, Boolean>>) {
        val mineTop = posts.filter { it.third && it.second == null }.map { it.first }.toSet()
        if (mineTop.isEmpty()) return
        val read = posts.filter { it.second in mineTop && !it.third }.groupingBy { it.second!! }.eachCount()
        val was = seen(context).orEmpty().toMutableMap()
        var changed = false
        for (id in mineTop) {
            val n = read[id] ?: 0
            if (n > (was[id] ?: 0)) {
                was[id] = n; changed = true
                // 2.0.0 [RVN-19] HQ-51 read where they are: the 알림함's row about them has said what it had to.
                runCatching { com.bamnat.app.inbox.Inbox.remove(context, "plaza.post:$id") }
            }
        }
        if (changed || seen(context) == null) write(context, was)
    }

    private fun write(context: Context, counts: Map<String, Int>) {
        val o = JSONObject()
        counts.forEach { (id, n) -> o.put(id, n) }
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_SEEN, o.toString()).apply() }
    }

    /** What [posts] show now counts as seen (kept for the posts still listed, so the note stays small). */
    fun markSeen(context: Context, posts: List<MyPost>) { write(context, others(posts)); publish(0) }
}
