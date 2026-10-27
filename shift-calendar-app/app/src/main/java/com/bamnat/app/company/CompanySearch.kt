package com.bamnat.app.company

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import com.bamnat.core.plaza.Choseong
import com.bamnat.core.plaza.CompanyMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [SRCH-06][SRCH-07][SRCH-09][SRCH-10] 1.29 묶음 AS (사용자 2026-10-27): "광장에 대표판이 있는데 회사 검색 시 뜨지 않는다 … 안 뜨는 게
 * 아니라 늦게 떠서 안 뜨는 것처럼 보인다", "데이터 검색에 관한 모든 로딩이 너무 느려서 처음 사용하는 사용자가 불편하다".
 *
 * One way every company search goes, so the first letter already shows something and the server's answer is added when
 * it comes, never thrown away for being late:
 *  - [peek] (no waiting, safe on the main thread): the last answer for the same name, else what an answer for its first
 *    letters had that still fits, plus the most used companies ([popular], kept after the first month is drawn) that fit.
 *  - [states]: that at once, then the server after a pause in typing; past [SLOW_MS] a "조금 오래 걸려요" step, and the
 *    answer whenever it comes (up to [GIVE_UP_MS]); one request for the same name however many screens ask ([ask]).
 *  - [search]: the same as one call (치는 대로 찾기's [com.bamnat.app.ui.company.inlineSearch], 급여 방식 찾기).
 * What is kept is public (company names, their 대표 주기 line), on this phone only, in the cache folder (not backed up).
 *
 * [SPD-11][SPD-12][SPD-13][SPD-15] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다. 획기적으로 속도를 빠르게"):
 *  - [peek] also looks in the server's whole list of companies ([CompanyApi.allCompanies], kept in [allFile] with its ETag): a
 *    name the phone never asked about is found as it is typed, and while the list is newer than [ALL_FRESH_MS] the server is
 *    not asked at all (0 requests); with nothing found it is asked once. The list is asked for again with its ETag (304).
 *  - [warm] reads it ahead on any connection but 데이터 절약·절전 (1.29 skipped mobile data, where most first users are), and
 *    the screens that search start it at once if it has not run yet ([warmCompanies]).
 *  - [cache] keeps what the plaza screens read ([PageCache]) between runs, and [prefetch] reads the first companies of a list
 *    ahead, so the company screen opens with its page at once.
 */
class CompanySearch(
    private val api: () -> CompanyApi,
    /** Where the answers and the most used companies are kept between runs; null keeps them in memory only (tests). */
    private val file: File?,
    private val now: () -> Long = { com.bamnat.core.time.Today.millis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** [SPD-11] Where the whole list of companies is kept (apart: it is written only when it changed). */
    private val allFile: File? = file?.let { File(it.parentFile, "company_all.json") },
    /** [SPD-13] Where the plaza screens' pages are kept. */
    pageFile: File? = file?.let { File(it.parentFile, "plaza_cache.json") },
) {
    /** [SPD-13] What the plaza screens last read: shown first, read again quietly. */
    val cache = PageCache(pageFile, now, scope)

    /** What a screen shows for a name: the companies known so far and where the server's answer stands. */
    data class State(val query: String, val hits: List<CompanyHit>, val stage: Stage, val code: String? = null) {
        val waiting: Boolean get() = stage == Stage.ASKING || stage == Stage.SLOW
    }

    enum class Stage {
        /** Fewer than two letters. */
        IDLE,
        /** Shown from the phone; the server is being asked. */
        ASKING,
        /** Asked [SLOW_MS] ago and still waiting: said so, and the answer still shows when it comes. */
        SLOW,
        /** The server's answer (or the phone's, with no company server). */
        DONE,
        /** No answer ([State.code] says why: offline, …); what the phone had stays. */
        FAILED,
    }

    private class Kept(val hits: List<CompanyHit>, val at: Long)

    private val lock = Any()
    private val answers = object : LinkedHashMap<String, Kept>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Kept>?) = size > MAX_ANSWERS
    }
    @Volatile private var top: Kept? = null
    private val asking = ConcurrentHashMap<String, Deferred<List<CompanyHit>>>()
    /** [SPD-11] The whole list as the server last gave it: its companies, ETag, and when it was last found unchanged. */
    private class Whole(val list: List<Entry>, val etag: String?, val at: Long, @Volatile var checked: Long)
    @Volatile private var whole: Whole? = null
    @Volatile private var loaded = file == null
    private val writing = Mutex()

    /** The key a name is kept by: written any way search takes it (띄어쓰기·(주)·대소문자), 초성 as typed. */
    internal fun key(q: String): String = q.trim().let { t -> if (Choseong.isQuery(t)) t.filter { !it.isWhitespace() } else CompanyText.norm(t) }

    /** The most used companies kept, newest list; empty before the first list came. */
    val popularHits: List<CompanyHit> get() = top?.hits.orEmpty()

    /**
     * [SRCH-07] What the phone already has for [q], at once: the last answer for it, else the companies an answer for its
     * first letters had that still fit, and the most used companies that fit. [exact] when it is the answer for [q] itself.
     */
    fun peek(q: String): Peek {
        val k = key(q)
        if (k.length < 2) return Peek(emptyList(), exact = false, age = Long.MAX_VALUE)
        val cho = Choseong.isQuery(q)
        val fits = { h: CompanyHit -> fits(h, q, k, cho) }
        synchronized(lock) {
            answers[k]?.let { return Peek(it.hits, exact = true, age = now() - it.at) }
            // [SPD-11] The whole list has every company: what fits in it is the answer, as of when it was read.
            whole?.let { w -> return Peek(find(w.list, q, k, cho), exact = false, age = now() - w.checked, whole = true) }
            // An answer for fewer letters had every company with them (fewer than the server's 20), so those that still fit are all.
            val shorter = answers.entries.filter { (key, kept) -> key.length >= 2 && k.startsWith(key) && kept.hits.size < SERVER_LIMIT }
                .maxByOrNull { it.key.length }
            val fromShorter = shorter?.value?.hits?.filter(fits).orEmpty()
            val fromTop = top?.hits?.filter(fits).orEmpty().sortedByDescending { it.users }
            val seen = HashSet<String>()
            return Peek((fromShorter + fromTop).filter { seen.add(it.id) }.take(SERVER_LIMIT), exact = false, age = Long.MAX_VALUE)
        }
    }

    /** [whole]: from the server's whole list of companies ([SPD-11]), [age] since it was last found the same. */
    data class Peek(val hits: List<CompanyHit>, val exact: Boolean, val age: Long, val whole: Boolean = false) {
        /** [SPD-11] The whole list is new enough to be the answer without asking (and found something). */
        val enough: Boolean get() = whole && hits.isNotEmpty() && age < ALL_FRESH_MS
    }

    /**
     * [SPD-11] A company of the whole list with every way it is found worked out once (on reading it, off the screen's thread):
     * its name and other names as search sees them, as said in Korean letters, and their 초성.
     */
    private class Entry(known: KnownCompany) {
        val hit = known.hit
        val names: List<String> = listOf(known.hit.name) + known.aliases
        val norms: List<String> = names.map(CompanyText::norm)
        val saids: List<String> = listOf(known.said.takeIf { it.isNotEmpty() && it != "-" } ?: CompanyMatch.spoken(known.hit.name)) +
            known.aliases.map(CompanyMatch::spoken)
        val chos: List<String> = listOf(known.cho.takeIf { it.isNotEmpty() && it != "-" } ?: Choseong.of(norms[0])) +
            known.aliases.map { Choseong.of(CompanyText.norm(it)) }
    }

    private class Found(val e: Entry, val at: Int, val exact: Boolean, val starts: Boolean)

    /**
     * [SPD-11] What the server's search would answer for [q] from the whole list: the same matching (name or other name as
     * written or as said, or 초성) and order (the name itself, a rule in use, starting with it, shorter, then by name), 20 at most.
     */
    private fun find(list: List<Entry>, q: String, k: String, cho: Boolean): List<CompanyHit> {
        val said = CompanyMatch.spoken(q).take(60)
        val sayable = said.length >= 2
        val found = list.mapNotNull { e ->
            val at = if (cho) e.chos.indexOfFirst { k in it }
            else e.norms.indices.firstOrNull { i -> k in e.norms[i] || (sayable && said in e.saids[i]) } ?: -1
            if (at < 0) return@mapNotNull null
            val exact = !cho && e.norms.indices.any { i -> e.norms[i] == k || (i == 0 && e.saids[0] == said) }
            val starts = if (cho) e.chos[0].startsWith(k) else e.norms[0].startsWith(k) || (sayable && e.saids[0].startsWith(said))
            Found(e, at, exact, starts)
        }
        return found.sortedWith(compareByDescending<Found> { it.exact }.thenByDescending { it.e.hit.adopted > 0 }.thenByDescending { it.starts }
            .thenBy { it.e.hit.name.length }.thenComparator { a, b -> KOREAN.compare(a.e.hit.name, b.e.hit.name) })
            .take(SERVER_LIMIT)
            .map { f -> f.e.hit.copy(alias = if (f.at <= 0) null else f.e.names[f.at]) }
    }

    private fun fits(h: CompanyHit, q: String, k: String, cho: Boolean): Boolean {
        val names = listOfNotNull(h.name, h.alias)
        return if (cho) names.any { k in Choseong.of(it) }
        else names.any { n -> k in CompanyText.norm(n) || CompanyMatch.fit(n, q)?.let { it <= 1 } == true }
    }

    /**
     * [SRCH-07] The server's answer for [q], one request however many ask at once; kept for [peek]. A fresh answer
     * ([FRESH_MS]) is given back without asking again. With nothing for [q], the other spelling once (GS ↔ 지에스, AH-I1).
     */
    fun ask(q: String): Deferred<List<CompanyHit>> {
        val k = key(q)
        asking[k]?.takeIf { it.isActive }?.let { return it }
        // Started at once on the caller's thread: a phone's own fake answers there, a real client moves to its own thread.
        val job = scope.async(start = CoroutineStart.UNDISPATCHED) {
            try {
                val first = api().search(q)
                val hits = if (first.isNotEmpty()) first
                else CompanyMatch.variants(q).drop(1).firstOrNull()?.let { other -> api().search(other) } ?: first
                keep(k, hits)
                hits
            } finally {
                asking.remove(k)
            }
        }
        if (!job.isCompleted) asking[k] = job
        return job
    }

    /** [SRCH-07] The server's answer for [q] (a fresh one kept is given at once). */
    suspend fun search(q: String): List<CompanyHit> {
        val p = peek(q)
        if (p.exact && p.age < FRESH_MS) return p.hits
        if (p.enough) return p.hits
        return ask(q).await()
    }

    /** When [states] says "조금 오래 걸려요": [SLOW_MS], longer only in a screenshot that shows the asking step. */
    @androidx.annotation.VisibleForTesting
    internal var slowAfterMs: Long = SLOW_MS

    /**
     * [SRCH-06][SRCH-10] What to show for [q] as time goes: the phone's at once, the server's when it answers. Collect it
     * per name (a new letter cancels only the waiting, not the request, whose answer is kept).
     */
    fun states(q: String, pauseMs: Long = PAUSE_MS, slowMs: Long = slowAfterMs): Flow<State> = flow {
        val k = key(q)
        if (k.length < 2) { emit(State(q, emptyList(), Stage.IDLE)); return@flow }
        val p = peek(q)
        if (!api().enabled) { emit(State(q, p.hits, Stage.DONE)); return@flow }
        if (p.exact && p.age < FRESH_MS) { emit(State(q, p.hits, Stage.DONE)); return@flow }
        // [SPD-11] The whole list read lately found it: that is the answer, nothing asked.
        if (p.enough) { emit(State(q, p.hits, Stage.DONE)); return@flow }
        emit(State(q, p.hits, Stage.ASKING))
        // A pause in typing before asking, so each letter is not a request; what the phone has is already on screen.
        if (!p.exact) delay(pauseMs)
        val job = ask(q)
        var got = withTimeoutOrNull(slowMs) { runCatching { job.await() } }
        if (got == null) {
            emit(State(q, p.hits, Stage.SLOW))
            got = withTimeoutOrNull(GIVE_UP_MS - slowMs) { runCatching { job.await() } } ?: Result.failure(CompanyException("slow"))
        }
        got.fold(
            onSuccess = { emit(State(q, it, Stage.DONE)) },
            onFailure = { e -> emit(State(q, p.hits, Stage.FAILED, (e as? CompanyException)?.code ?: "offline")) },
        )
    }

    /** Forgets the answer kept for [q], so the next look asks the server (다시 찾기). */
    fun forgetAnswer(q: String) {
        synchronized(lock) { answers.remove(key(q)) }
    }

    private fun keep(k: String, hits: List<CompanyHit>) {
        synchronized(lock) { answers[k] = Kept(hits, now()) }
        save()
    }

    /** [SRCH-09] A company's page as last read, for its screen to show at once while it reads again; null for none. */
    fun page(id: String): CompanyPage? = cache.bundle(id)?.page

    fun keepPage(page: CompanyPage) = cache.keepPage(page)

    /** Forgets what was kept about [companyId]'s page (after this phone changed something there). */
    fun forgetPage(companyId: String) = cache.forget(companyId)

    private val prefetched = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * [SPD-15] 1.30 AP1: the first [PREFETCH] companies of a list the user sees (회사 찾기's answer, 규칙 광장's first page) read
     * ahead as anyone reads them (no X-Device: not counted in a member's reads of the day), so tapping one opens its page at
     * once. Not the companies in [skip] (this phone's, whose page it reads as a member), not one kept already, once a run each.
     */
    fun prefetch(ids: List<String>, skip: Set<String> = emptySet()) {
        if (!api().enabled) return
        ids.asSequence().distinct().filter { it !in skip }.take(PREFETCH).filter { cache.bundle(it) == null && prefetched.add(it) }.toList().forEach { id ->
            scope.launch { runCatching { api().bundle(id, null) }.getOrNull()?.let { b -> if (cache.bundle(id) == null) cache.keepBundle(b) } }
        }
    }

    /**
     * [SRCH-09] After the first month is drawn: what was kept is read back, and with data to spare (not [saver]) the most
     * used companies (every [TOP_EVERY_MS]) and [mine], the company this phone uses, are read ahead, so the first search
     * and 우리 회사 광장 show at once. Nothing here stops the app when it fails.
     *
     * [SPD-11][SPD-12] 1.30 AP1: the whole list of companies first (asked again after [ALL_FRESH_MS], with its ETag: usually
     * nothing comes back); a server before 1.30 has none and the most used companies are read as before. [mine]'s page with
     * its room and room talk ([device]: as the member reads it) is read again when older than [MINE_EVERY_MS].
     */
    suspend fun warm(saver: Boolean, mine: String? = null, device: String? = null) {
        load()
        cache.load()
        if (saver || !api().enabled) return
        val w = whole
        if (w == null || now() - w.checked >= ALL_FRESH_MS) {
            val got = runCatching { api().allCompanies(w?.etag) }
            got.getOrNull()?.let(::keepWhole)
            if (got.isSuccess && got.getOrNull() == null) w?.checked = now()
            // A server before 1.30 (or none answered): the most used companies, as in 1.29.
            if (got.isFailure && w == null) readTop()
        }
        if (mine != null) {
            val kept = cache.keptBundle(mine)
            if (kept == null || !cache.fresh(kept, MINE_EVERY_MS)) runCatching { api().bundle(mine, device) }.getOrNull()?.let(cache::keepBundle)
        }
    }

    private suspend fun readTop() {
        val kept = top
        if (kept == null || now() - kept.at > TOP_EVERY_MS) {
            runCatching { api().popular() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                top = Kept(list, now())
                save()
            }
        }
    }

    @Volatile private var warming: kotlinx.coroutines.Job? = null
    @Volatile private var warmedAt = Long.MIN_VALUE / 2

    /**
     * [SPD-12] 1.30 AP1: [warm] now, from a screen that searches or shows the plaza (회사 찾기, 급여 방식 찾기, 광장), unless it
     * ran within [WARM_AGAIN_MS] or is running. Returns at once; the screen shows what comes as it comes.
     */
    fun warmSoon(saver: Boolean, mine: String? = null, device: String? = null) {
        if (warming?.isActive == true || now() - warmedAt in 0 until WARM_AGAIN_MS) return
        warmedAt = now()
        warming = scope.launch { runCatching { warm(saver, mine, device) } }
    }

    /** Waits for a [warmSoon] on its way (tests). */
    internal suspend fun warmed() {
        warming?.join()
    }

    private fun keepWhole(list: CompanyList) {
        val at = now()
        whole = Whole(list.companies.map(::Entry), list.etag, at, at)
        val f = allFile ?: return
        scope.launch {
            writing.withLock {
                runCatching {
                    val text = JSONObject().put("v", ALL_FILE_VERSION).put("etag", list.etag ?: JSONObject.NULL).put("at", at)
                        .put("companies", JSONArray(list.companies.map { it.toJson() })).toString()
                    f.parentFile?.mkdirs()
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                }
            }
        }
    }

    /** Reads what the last run kept (once; off the main thread). */
    suspend fun load() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            writing.withLock {
                if (loaded) return@withLock
                runCatching { file?.takeIf { it.exists() }?.readText()?.let(::read) }
                runCatching { allFile?.takeIf { it.exists() }?.readText()?.let(::readWhole) }
                loaded = true
            }
        }
    }

    private fun read(text: String) {
        val o = JSONObject(text)
        if (o.optInt("v") != FILE_VERSION) return
        val hits = { a: JSONArray? -> (0 until (a?.length() ?: 0)).mapNotNull { runCatching { companyHit(a!!.getJSONObject(it)) }.getOrNull() } }
        o.optJSONObject("top")?.let { t -> top = Kept(hits(t.optJSONArray("hits")), t.optLong("at")) }
        val list = o.optJSONArray("answers") ?: JSONArray()
        synchronized(lock) {
            for (i in 0 until list.length()) {
                val a = list.getJSONObject(i)
                val at = a.optLong("at")
                // Older than a day is not worth showing first.
                if (now() - at < KEEP_MS) answers.putIfAbsent(a.getString("q"), Kept(hits(a.optJSONArray("hits")), at))
            }
        }
    }

    /** [SPD-11] The whole list as kept: its ETag still asks whether it changed, however old it is. */
    private fun readWhole(text: String) {
        val o = JSONObject(text)
        if (o.optInt("v") != ALL_FILE_VERSION || whole != null) return
        val a = o.optJSONArray("companies") ?: return
        val list = (0 until a.length()).mapNotNull { runCatching { knownCompany(a.getJSONObject(it)) }.getOrNull() }
        val at = o.optLong("at")
        whole = Whole(list.map(::Entry), o.optStringOrNull("etag"), at, at)
    }

    private fun save() {
        val f = file ?: return
        if (!loaded) return
        scope.launch {
            writing.withLock {
                val text = synchronized(lock) {
                    JSONObject().put("v", FILE_VERSION)
                        .put("top", top?.let { t -> JSONObject().put("at", t.at).put("hits", JSONArray(t.hits.map { it.toJson() })) } ?: JSONObject.NULL)
                        .put("answers", JSONArray(answers.entries.toList().takeLast(MAX_SAVED).map { (q, kept) ->
                            JSONObject().put("q", q).put("at", kept.at).put("hits", JSONArray(kept.hits.map { it.toJson() }))
                        }))
                        .toString()
                }
                runCatching {
                    f.parentFile?.mkdirs()
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                }
            }
        }
    }

    companion object {
        /** [SPAT-07] The same pause as 치는 대로 찾기 had (AH-D1). */
        const val PAUSE_MS = 300L
        /** Then "조금 오래 걸려요"; the answer still shows when it comes. */
        const val SLOW_MS = 2_500L
        /** The client's own limits (6 s to connect since 1.30, 15 s to read) end a request before this. */
        const val GIVE_UP_MS = 25_000L
        /** An answer this new is given again without asking (as the server keeps its own for 30 s). */
        const val FRESH_MS = 20_000L
        const val KEEP_MS = 24 * 60 * 60 * 1000L
        const val TOP_EVERY_MS = 12 * 60 * 60 * 1000L
        const val SERVER_LIMIT = 20
        const val MAX_ANSWERS = 60
        const val MAX_SAVED = 30
        const val FILE_VERSION = 1
        /** [SPD-11] The whole list this new is the answer without asking; older, it is asked again with its ETag. */
        const val ALL_FRESH_MS = 10 * 60 * 1000L
        const val ALL_FILE_VERSION = 1
        /** [SPD-12] This phone's company page with its room is read again when older than this. */
        const val MINE_EVERY_MS = 30 * 60 * 1000L
        /** [SPD-12] A screen's [warmSoon] after another's within this does nothing. */
        const val WARM_AGAIN_MS = 60 * 1000L
        /** [SPD-15] How many of a list's first companies are read ahead. */
        const val PREFETCH = 3
        private val KOREAN: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.KOREAN)

        /**
         * [SRCH-09] Whether to save data now: the phone's 데이터 절약 (Data Saver) on for this app, or 절전 모드. Reading ahead
         * waits for a better time; what the user asks for still goes.
         * [SPD-12] 1.30 AP1: mobile data no longer counts (1.29 read nothing ahead on it, so most first users, who set up on LTE,
         * got none of it): the whole list is asked with its ETag (nothing comes back unless it changed) and a page is a few KB.
         */
        fun saveData(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm?.isPowerSaveMode == true) return true
            return runCatching { cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(false)
        }
    }
}

/**
 * [SPD-12] 1.30 AP1: [CompanySearch.warmSoon] with what this phone knows: whether it saves data now, and the company the main
 * calendar uses or joined (read as its member). A screen that searches or shows the plaza calls it as it opens; the start of
 * the app (after the first month) calls [CompanySearch.warm] with the same ([warmCompaniesNow]).
 */
fun com.bamnat.app.AppContainer.warmCompanies() {
    val store = companyStore
    companySearch.warmSoon(CompanySearch.saveData(appContext), store.applied.value?.companyId ?: store.member.value?.first, store.device)
}

/** [SPD-12] As [warmCompanies], waited for (the app's start, already off the screen's thread). */
suspend fun com.bamnat.app.AppContainer.warmCompaniesNow() {
    val store = companyStore
    companySearch.warm(CompanySearch.saveData(appContext), store.applied.value?.companyId ?: store.member.value?.first, store.device)
}

/** [SPD-15] The companies whose pages this phone reads as a member (every calendar's): never read ahead as anyone. */
fun com.bamnat.app.AppContainer.ownCompanies(): Set<String> = companyScopes().flatMap { s ->
    listOfNotNull(s.store.member.value?.first, s.store.applied.value?.companyId)
}.toSet()

/** [SPD-15] Reads [ids]' first companies ahead, unless the phone saves data. */
fun com.bamnat.app.AppContainer.prefetchCompanies(ids: List<String>) {
    if (CompanySearch.saveData(appContext)) return
    companySearch.prefetch(ids, ownCompanies())
}
