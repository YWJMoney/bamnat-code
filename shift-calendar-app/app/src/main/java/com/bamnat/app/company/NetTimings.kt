package com.bamnat.app.company

/**
 * [SPD-17] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다"): how long the company server took, kept in
 * this phone's memory only (never stored, never sent), for 설정 › 도움 › 자가 점검's 조회 속도 card. Each request keeps its
 * kind (검색·회사·광장·토론·쓰기·기타), how long it took and how it ended; never its address, what was typed or the device.
 * A screen that shows something from the phone at once says so ([cached]), and the ways the user feels a wait are timed
 * from the tap to what they waited for ([start]/[end]).
 * [STAT-08] 1.31: from 처리방침 3.5's date, with the user's yes, the same times also go to [sink] for 사용 통계 (kind and time only).
 */
object NetTimings {
    enum class Kind(val label: String, val key: String) {
        SEARCH("검색", "search"), COMPANY("회사", "company"), PLAZA("광장", "plaza"), TALK("토론", "talk"), WRITE("보내기", "write"), OTHER("기타", "other"),
    }

    enum class Result { OK, NOT_MODIFIED, REFUSED, OFFLINE }

    data class Call(val kind: Kind, val ms: Long, val result: Result)

    /** What the user waits for, from the tap to what they wanted on screen. */
    enum class Wait(val label: String, val key: String) {
        FIND("찾기 시작 → 첫 결과", "find"),
        COMPANY("회사 누름 → 대표판 보임", "company"),
        PLAZA("광장 열기 → 글 보임", "plaza"),
    }

    /** The card's numbers: the last [count] requests, their middle and slowest, and how many screens showed at once. */
    data class Summary(
        val count: Int,
        val medianMs: Long,
        val maxMs: Long,
        val slow: Int,
        val offline: Int,
        val shown: Int,
        val shownAtOnce: Int,
        val waits: Map<Wait, Pair<Long, Long>>,
    ) {
        /** Of the screens shown, the share that came from the phone at once, as a percent. */
        val atOncePercent: Int get() = if (shown == 0) 0 else 100 * shownAtOnce / shown
    }

    const val KEEP = 50
    /** Past this a wait is slow: the search's own "조금 오래 걸려요" ([CompanySearch.SLOW_MS]). */
    const val SLOW_MS = CompanySearch.SLOW_MS

    private val calls = ArrayDeque<Call>()
    private val shown = ArrayDeque<Boolean>()
    private val waits = HashMap<Wait, ArrayDeque<Long>>()

    /** One request to the company server, from [serverRequest]. */
    fun record(method: String, path: String, ms: Long, result: Result) {
        val kind = kindOf(method, path)
        synchronized(this) {
            calls.addLast(Call(kind, ms, result))
            while (calls.size > KEEP) calls.removeFirst()
        }
        // [STAT-08] 1.31 N4: an answer that came (not a refusal or no connection), by kind only.
        if (kind != Kind.OTHER && (result == Result.OK || result == Result.NOT_MODIFIED)) sink?.invoke("call", kind.key, ms)
    }

    /**
     * [STAT-08] 1.31 N4: where each timed lookup also goes ([com.bamnat.app.diag.LookupStats], which keeps it only for 사용 통계
     * with 처리방침 3.5 in force and the user's yes); null keeps them in memory only, as before 1.31.
     */
    @Volatile var sink: ((kind: String, step: String, ms: Long) -> Unit)? = null

    /** A screen showed what it needed: from the phone at once ([atOnce]) or after asking. */
    fun cached(atOnce: Boolean) = synchronized(this) {
        shown.addLast(atOnce)
        while (shown.size > KEEP) shown.removeFirst()
    }

    /** When a wait began, for [end]. */
    fun start(): Long = System.nanoTime()

    /** The wait that began at [startedAt] ended (what the user waited for is on screen). */
    fun end(wait: Wait, startedAt: Long) {
        val ms = (System.nanoTime() - startedAt) / 1_000_000
        synchronized(this) {
            val list = waits.getOrPut(wait) { ArrayDeque() }
            list.addLast(ms)
            while (list.size > KEEP) list.removeFirst()
        }
        sink?.invoke("wait", wait.key, ms) // [STAT-08]
    }

    fun summary(): Summary = synchronized(this) {
        val ms = calls.map { it.ms }.sorted()
        Summary(
            count = calls.size,
            medianMs = median(ms),
            maxMs = ms.lastOrNull() ?: 0,
            slow = calls.count { it.ms > SLOW_MS },
            offline = calls.count { it.result == Result.OFFLINE },
            shown = shown.size,
            shownAtOnce = shown.count { it },
            waits = waits.filterValues { it.isNotEmpty() }.mapValues { (_, l) -> l.sorted().let { median(it) to it.last() } },
        )
    }

    /** Recent requests of one [kind], newest last (tests and the card). */
    fun calls(kind: Kind? = null): List<Call> = synchronized(this) { calls.filter { kind == null || it.kind == kind } }

    fun clear() = synchronized(this) {
        calls.clear()
        shown.clear()
        waits.clear()
    }

    private fun median(sorted: List<Long>): Long = if (sorted.isEmpty()) 0 else sorted[sorted.size / 2]

    /** The kind of a request by its path alone: what was asked about stays out of it. */
    internal fun kindOf(method: String, path: String): Kind {
        if (method != "GET") return Kind.WRITE
        val p = path.substringBefore('?')
        return when {
            p == "/api/companies" || p == "/api/companies/all" || p == "/api/companies/top" -> Kind.SEARCH
            p.endsWith("/posts") -> Kind.TALK
            p.startsWith("/api/companies/") -> Kind.COMPANY
            p == "/api/board" || p == "/api/plazas" || p == "/api/terms" || p == "/api/activity" || p == "/api/rates" -> Kind.PLAZA
            else -> Kind.OTHER
        }
    }
}
