package com.bamnat.app.company

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [SPD-13] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다 … 느려서 사용에 거부감이 생길 수 있다"): what the
 * plaza screens last read, kept on this phone so a screen seen once opens with it at once, without a spinner, and reads
 * again quietly (a thin line on top while it does). Before 1.30 a company's page and the plaza's first pages were kept in
 * memory only, so every run of the app started from a spinner, and 회사 정보 광장 kept nothing at all.
 *
 * What is kept is what the server already showed this phone, in the server's own words ([toJson] writes what [companyPage]
 * and the others read): companies' pages with their room and room talk ([bundle], the company this phone joined and the last
 * ones seen), the plaza's first pages by tab and filter ([board]), the talks opened last ([talk]), the terms and the job
 * plazas (a day), and 내 활동 ([activity], by this phone's install, without the rates it sent). Nothing the user typed into
 * their pay: the server never sends amounts of anyone's own (결정 D79), and 내 활동's own 시세 amounts are left out. It is in
 * the cache folder (not backed up, the phone may clear it), so 처리방침 is the same as 1.29's search list (AS).
 */
class PageCache(
    /** Where it is kept between runs; null keeps it in memory only (tests). */
    private val file: File?,
    private val now: () -> Long = { com.bamnat.core.time.Today.millis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** One thing kept with when it was read. */
    class Kept<T>(val value: T, val at: Long)

    private class Lru<T>(private val max: Int) {
        val map = object : LinkedHashMap<String, Kept<T>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Kept<T>>?) = size > max
        }
    }

    private val lock = Any()
    private val bundles = Lru<CompanyBundle>(MAX_BUNDLES)
    private val boards = Lru<Board>(MAX_BOARDS)
    private val talks = Lru<Talk>(MAX_TALKS)
    private val activities = Lru<Activity>(1)
    @Volatile private var termsKept: Kept<CompanyTerms>? = null
    @Volatile private var plazasKept: Kept<List<PlazaJob>>? = null

    /** Whether what the last run kept was read ([load]); before that every look finds nothing. */
    @Volatile var loaded: Boolean = file == null
        private set
    private val writing = Mutex()
    private val saveLock = Any()
    private var dirty = false
    private var saving: Job? = null

    // ---- 회사 화면 · 회사 정보 광장 ----

    /** [SPD-13] 공통 계약: company [id]'s page (and room, room talk) as last read; null for none. */
    fun bundle(id: String): CompanyBundle? = keptBundle(id)?.value

    fun keptBundle(id: String): Kept<CompanyBundle>? = synchronized(lock) { bundles.map[id] }

    /**
     * [SPD-13] 공통 계약: keeps [b] for its company. A page read without the room and talk (read ahead, or by a screen that
     * needs the page only) keeps the room and talk already kept for the same company.
     */
    fun keepBundle(b: CompanyBundle) {
        synchronized(lock) {
            val before = bundles.map[b.page.id]?.value
            // [SPD-24] 1.31 R7: a page without pay ways (read too often today) never takes the place of a whole one kept.
            if (b.page.limited && before != null && !before.page.limited) return
            val kept = if (before != null && b.room == null && b.talk == null && b.page.member == before.page.member) b.copy(room = before.room, talk = before.talk) else b
            bundles.map[b.page.id] = Kept(kept, now())
        }
        save()
    }

    /** A page alone ([keepBundle] keeps its room and talk). */
    fun keepPage(page: CompanyPage) = keepBundle(CompanyBundle(page))

    /** Forgets [id]'s page (after this phone changed something there the server must say first). */
    fun forget(id: String) {
        synchronized(lock) { bundles.map.remove(id) }
        save()
    }

    // ---- 규칙 광장 ----

    /** The first page of the plaza for [key] (tab, name typed, filter), as last read. */
    fun board(key: String): Kept<Board>? = synchronized(lock) { boards.map[key] }

    fun keepBoard(key: String, b: Board) {
        synchronized(lock) { boards.map[key] = Kept(b, now()) }
        save()
    }

    // ---- 토론 ----

    /** The talk on [company]'s [setup], as last read; a room's from its company's bundle when that is newer. */
    fun talk(company: String, setup: String): Kept<Talk>? = synchronized(lock) {
        val own = talks.map["$company/$setup"]
        val fromBundle = if (setup != com.bamnat.core.plaza.StylePlaza.ROOM) null
        else bundles.map[company]?.let { k -> k.value.talk?.let { Kept(it, k.at) } }
        listOfNotNull(own, fromBundle).maxByOrNull { it.at }
    }

    fun keepTalk(company: String, setup: String, t: Talk) {
        synchronized(lock) {
            talks.map["$company/$setup"] = Kept(t, now())
            // The room's talk inside its company's bundle is the same talk: kept new there too.
            if (setup == com.bamnat.core.plaza.StylePlaza.ROOM) bundles.map[company]?.let { k -> bundles.map[company] = Kept(k.value.copy(talk = t), k.at) }
        }
        save()
    }

    // ---- 약관 · 광장 목록 · 내 활동 ----

    fun terms(): Kept<CompanyTerms>? = termsKept

    fun keepTerms(t: CompanyTerms) {
        termsKept = Kept(t, now())
        save()
    }

    fun plazas(): Kept<List<PlazaJob>>? = plazasKept

    fun keepPlazas(list: List<PlazaJob>) {
        plazasKept = Kept(list, now())
        save()
    }

    /** 내 활동 of the install [device] (kept under a mark of it, never the install id itself). */
    fun activity(device: String): Kept<Activity>? = synchronized(lock) { activities.map[mark(device)] }

    fun keepActivity(device: String, a: Activity) {
        // The 시세 amounts this phone sent stay out of the file; they show again when the screen reads afresh.
        synchronized(lock) { activities.map[mark(device)] = Kept(a.copy(rates = emptyList()), now()) }
        save()
    }

    fun forgetActivity() {
        synchronized(lock) { activities.map.clear() }
        save()
    }

    /** Whether [k] is newer than [ms]. */
    fun fresh(k: Kept<*>?, ms: Long): Boolean = k != null && now() - k.at in 0 until ms

    private fun mark(device: String): String = Integer.toHexString(device.hashCode())

    // ---- 파일 ----

    /** Reads what the last run kept (once; off the main thread). */
    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            writing.withLock {
                if (loaded) return@withLock
                runCatching { file?.takeIf { it.exists() }?.readText()?.let(::read) }
                loaded = true
            }
        }
    }

    private fun read(text: String) {
        val o = JSONObject(text)
        if (o.optInt("v") != FILE_VERSION) return
        val old = { at: Long -> now() - at !in 0 until KEEP_MS }
        fun <T> each(key: String, into: Lru<T>, make: (JSONObject) -> T) {
            val a = o.optJSONArray(key) ?: return
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val at = e.optLong("at")
                if (old(at)) continue
                val v = runCatching { make(e.getJSONObject("value")) }.getOrNull() ?: continue
                into.map.putIfAbsent(e.getString("key"), Kept(v, at))
            }
        }
        synchronized(lock) {
            each("bundles", bundles) { b ->
                CompanyBundle(companyPage(b.getJSONObject("page")), b.optJSONObject("room")?.let(::roomPage), b.optJSONObject("talk")?.let(::talkOf))
            }
            each("boards", boards, ::boardOf)
            each("talks", talks, ::talkOf)
            each("activity", activities, ::activityOf)
        }
        o.optJSONObject("terms")?.let { t -> if (!old(t.optLong("at"))) termsKept = Kept(termsOf(t.getJSONObject("value")), t.optLong("at")) }
        o.optJSONObject("plazas")?.let { t -> if (!old(t.optLong("at"))) plazasKept = Kept(plazaJobs(t.getJSONObject("value")), t.optLong("at")) }
    }

    private fun write(): String = synchronized(lock) {
        fun <T> list(l: Lru<T>, json: (T) -> JSONObject) = JSONArray(l.map.entries.toList().map { (k, v) ->
            JSONObject().put("key", k).put("at", v.at).put("value", json(v.value))
        })
        JSONObject().put("v", FILE_VERSION)
            .put("bundles", list(bundles) { b ->
                JSONObject().put("page", b.page.toJson()).apply {
                    b.room?.let { put("room", it.toJson()) }
                    b.talk?.let { put("talk", it.toJson()) }
                }
            })
            .put("boards", list(boards) { it.toJson() })
            .put("talks", list(talks) { it.toJson() })
            .put("activity", list(activities) { it.toJson() })
            .apply {
                termsKept?.let { put("terms", JSONObject().put("at", it.at).put("value", it.value.toJson())) }
                plazasKept?.let { put("plazas", JSONObject().put("at", it.at).put("value", plazasJson(it.value))) }
            }
            .toString()
    }

    /** Written a moment after the last change, on its own thread: a screen that keeps three things writes once. */
    private fun save() {
        val f = file ?: return
        if (!loaded) return
        synchronized(saveLock) {
            dirty = true
            if (saving != null) return
            saving = scope.launch {
                while (true) {
                    delay(SAVE_AFTER_MS)
                    synchronized(saveLock) { dirty = false }
                    writing.withLock {
                        val text = write()
                        runCatching {
                            f.parentFile?.mkdirs()
                            val tmp = File(f.parentFile, f.name + ".tmp")
                            tmp.writeText(text)
                            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                        }
                    }
                    synchronized(saveLock) { if (!dirty) { saving = null; return@launch } }
                }
            }
        }
    }

    /** Waits for a write that is on its way (tests). */
    internal suspend fun flush() {
        synchronized(saveLock) { saving }?.join()
    }

    companion object {
        const val FILE_VERSION = 1
        /** The company this phone joined and the last ones seen. */
        const val MAX_BUNDLES = 8
        /** The plaza's first pages by tab and filter. */
        const val MAX_BOARDS = 6
        const val MAX_TALKS = 6
        /** Older than this is not shown first (the screen still reads afresh). */
        const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
        /** The terms and the job plazas change rarely: not asked again within a day. */
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val SAVE_AFTER_MS = 500L
    }
}

// [SPD-13] What PageCache keeps, written in the server's own words, so the same reading ([companyPage] and the others) reads
// it back. PageCacheTest checks each one comes back the same.

internal fun CompanyPage.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("kind", kind).put("job", job).put("region", region)
    .put("aliases", JSONArray(aliases)).put("member", member).put("limited", limited)
    .put("setups", JSONArray(setups.map { s ->
        JSONObject().put("id", s.id).put("status", if (s.adopted) "adopted" else "pending").put("points", s.points).put("persons", s.persons)
            .put("bad", s.bad).put("threshold", s.threshold).put("dept", s.dept).put("version", s.version ?: JSONObject.NULL)
            .put("setup", JSONObject(s.text)).put("lines", JSONArray(s.lines.map { (a, b) -> JSONArray(listOf(a, b)) }))
            .put("trust", s.trust).put("agree", s.agree).put("posts", s.posts).put("uses", s.uses).put("senders", s.senders)
            .put("checked", s.checked).put("by", JSONArray(s.by)).put("checkedLong", s.checkedLong).put("support", s.support)
            .put("payHidden", s.payHidden).put("stable", s.stable).put("lead", s.lead).put("doubted", s.doubted)
            .put("opinion", JSONObject().put("ok", s.opinion.ok).put("bad", s.opinion.bad).put("why", JSONObject(s.opinion.why))
                .put("mine", s.opinion.mine ?: JSONObject.NULL))
    }))

private fun RoomVersion.toJson(): JSONObject = JSONObject().put("id", setup).put("status", if (adopted) "adopted" else "pending").put("dept", dept)
    .put("version", version ?: JSONObject.NULL).put("setup", JSONObject(text)).put("persons", persons).put("senders", senders).put("uses", uses)
    .put("support", support).put("changes", JSONArray(changes))

internal fun RoomPage.toJson(): JSONObject = JSONObject().put("company", JSONObject().put("id", companyId).put("name", company))
    .put("base", base?.toJson()?.put("mine", mine)?.put("first", first) ?: JSONObject.NULL)
    .put("others", JSONArray(others.map { it.toJson() })).put("stable", stable)
    .put("stableRule", JSONObject().put("phones", stablePhones).put("days", stableDays).put("uses", stableUses))

internal fun Talk.toJson(): JSONObject = JSONObject()
    .put("posts", JSONArray(posts.map { p ->
        JSONObject().put("id", p.id).put("who", p.who).put("stance", p.stance ?: JSONObject.NULL).put("verified", p.verified).put("body", p.body)
            .put("at", p.at.toString()).put("mine", p.mine).put("hidden", p.hidden?.let { JSONObject().put("text", it) } ?: JSONObject.NULL)
            .put("nick", p.nick ?: JSONObject.NULL).put("parent", p.parent ?: JSONObject.NULL).put("kind", p.kind)
            .put("change", JSONArray(p.change.map { JSONObject().put("field", it.field).put("now", it.now).put("want", it.want).put("why", it.why) }))
            .put("agree", p.agree).put("disagree", p.disagree).put("trust", p.trust).put("myVote", p.myVote ?: JSONObject.NULL)
            .put("proposal", p.proposal?.let { r ->
                JSONObject().put("setup", r.setup).put("status", if (r.adopted) "adopted" else "pending").put("persons", r.persons)
                    .put("threshold", r.threshold).put("text", r.text ?: JSONObject.NULL)
            } ?: JSONObject.NULL)
            .put("author", p.author ?: JSONObject.NULL).put("review", p.review)
    }))
    .put("mutedUntil", mutedUntil?.toString() ?: JSONObject.NULL).put("past", past).put("hiddenByYou", hiddenByYou)
    .apply { leftPosts?.let { put("left", JSONObject().put("posts", it)) } }

internal fun Board.toJson(): JSONObject = JSONObject().put("next", next ?: JSONObject.NULL)
    .put("items", JSONArray(items.map { i ->
        JSONObject().put("id", i.setup).put("status", if (i.adopted) "adopted" else "pending").put("dept", i.dept).put("points", i.points)
            .put("persons", i.persons).put("bad", i.bad).put("threshold", i.threshold).put("trust", i.trust).put("agree", i.agree)
            .put("posts", i.posts).put("version", i.version ?: JSONObject.NULL).put("at", i.at.toString()).put("gist", i.gist)
            .put("cycle", i.cycle).put("pay", i.pay).put("uses", i.uses).put("by", JSONArray(i.by))
            .put("company", JSONObject().put("id", i.companyId).put("name", i.company).put("kind", i.kind).put("job", i.job).put("region", i.region))
    }))

internal fun CompanyTerms.toJson(): JSONObject =
    JSONObject().put("threshold", threshold).put("rewardMonths", rewardMonths).put("agree", agree).put("firstMonths", firstMonths)

internal fun plazasJson(list: List<PlazaJob>): JSONObject = JSONObject().put("jobs", JSONArray(list.map {
    JSONObject().put("key", it.key).put("name", it.name).put("adopted", it.adopted).put("voting", it.voting).put("rates", it.rates)
}))

internal fun Activity.toJson(): JSONObject = JSONObject().put("nick", nick ?: JSONObject.NULL)
    .put("reports", JSONArray(reports.map { r ->
        JSONObject().put("id", r.id).put("company", JSONObject().put("id", r.companyId).put("name", r.company)).put("dept", r.dept)
            .put("setup", r.setup).put("status", r.status).put("version", r.version ?: JSONObject.NULL).put("persons", r.persons)
            .put("bad", r.bad).put("uses", r.uses).put("at", r.at.toString())
    }))
    .put("proposals", JSONArray(proposals.map { p ->
        JSONObject().put("id", p.id).put("company", JSONObject().put("id", p.companyId).put("name", p.company)).put("setup", p.setup)
            .put("body", p.body).put("agree", p.agree).put("disagree", p.disagree).put("trust", p.trust).put("at", p.at.toString())
    }))
    .put("rates", JSONArray())
    .put("rewards", JSONArray(rewards.map { w ->
        JSONObject().put("months", w.months).put("status", w.status).put("why", w.why).put("at", w.at.toString())
    }))
