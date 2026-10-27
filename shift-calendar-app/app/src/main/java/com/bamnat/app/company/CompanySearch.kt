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
 */
class CompanySearch(
    private val api: () -> CompanyApi,
    /** Where the answers and the most used companies are kept between runs; null keeps them in memory only (tests). */
    private val file: File?,
    private val now: () -> Long = { com.bamnat.core.time.Today.millis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
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
    private val pages = object : LinkedHashMap<String, Pair<CompanyPage, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<CompanyPage, Long>>?) = size > MAX_PAGES
    }
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
            // An answer for fewer letters had every company with them (fewer than the server's 20), so those that still fit are all.
            val shorter = answers.entries.filter { (key, kept) -> key.length >= 2 && k.startsWith(key) && kept.hits.size < SERVER_LIMIT }
                .maxByOrNull { it.key.length }
            val fromShorter = shorter?.value?.hits?.filter(fits).orEmpty()
            val fromTop = top?.hits?.filter(fits).orEmpty().sortedByDescending { it.users }
            val seen = HashSet<String>()
            return Peek((fromShorter + fromTop).filter { seen.add(it.id) }.take(SERVER_LIMIT), exact = false, age = Long.MAX_VALUE)
        }
    }

    data class Peek(val hits: List<CompanyHit>, val exact: Boolean, val age: Long)

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
    fun page(id: String): CompanyPage? = synchronized(lock) { pages[id]?.first }

    fun keepPage(page: CompanyPage) {
        synchronized(lock) { pages[page.id] = page to now() }
    }

    /** [SRCH-13] The plaza's first pages read in this run (by tab, name and filter), for its screen to show at once. */
    val boardPages = Pages<Board>(12)

    /** A few things kept in memory by key, the least used going first. */
    class Pages<T>(private val max: Int) {
        private val map = object : LinkedHashMap<String, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > max
        }

        @Synchronized fun get(key: String): T? = map[key]
        @Synchronized fun put(key: String, value: T) { map[key] = value }
    }

    /** Forgets what was kept about [companyId]'s page (after this phone changed something there). */
    fun forgetPage(companyId: String) {
        synchronized(lock) { pages.remove(companyId) }
    }

    /**
     * [SRCH-09] After the first month is drawn: what was kept is read back, and with data to spare (not [saver]) the most
     * used companies (every [TOP_EVERY_MS]) and [mine], the company this phone uses, are read ahead, so the first search
     * and 우리 회사 광장 show at once. Nothing here stops the app when it fails.
     */
    suspend fun warm(saver: Boolean, mine: String? = null) {
        load()
        if (saver || !api().enabled) return
        val kept = top
        if (kept == null || now() - kept.at > TOP_EVERY_MS) {
            runCatching { api().popular() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
                top = Kept(list, now())
                save()
            }
        }
        if (mine != null && page(mine) == null) runCatching { api().company(mine) }.getOrNull()?.let(::keepPage)
    }

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
        /** The client's own limits (10 s to connect, 15 s to read) end a request before this. */
        const val GIVE_UP_MS = 25_000L
        /** An answer this new is given again without asking (as the server keeps its own for 30 s). */
        const val FRESH_MS = 20_000L
        const val KEEP_MS = 24 * 60 * 60 * 1000L
        const val TOP_EVERY_MS = 12 * 60 * 60 * 1000L
        const val SERVER_LIMIT = 20
        const val MAX_ANSWERS = 60
        const val MAX_SAVED = 30
        const val MAX_PAGES = 8
        const val FILE_VERSION = 1

        /**
         * [SRCH-09] Whether to save data now: the phone's 데이터 절약 (Data Saver) on for this app, or 절전 모드. Reading ahead
         * waits for a better time; what the user asks for still goes. A metered connection is checked only where the app may
         * see the network's state (it asks for no new permission for it, AS-D4): the list read ahead is about 18 KB, twice a day.
         */
        fun saveData(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm?.isPowerSaveMode == true) return true
            if (runCatching { cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }.getOrDefault(false)) return true
            return runCatching { cm?.isActiveNetworkMetered == true }.getOrDefault(false)
        }
    }
}
