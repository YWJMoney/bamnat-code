package com.bamnat.core.plaza

/**
 * [PAYF-02] 1.29 묶음 AP (사용자 2026-10-27 "안 뜨는 게 아니라 늦게 떠서 안 뜨는 것처럼 보인다"): what the 급여 방식 찾기 box
 * lists, at once from the phone and then with the company server's answer laid over it. The phone knows the companies of the
 * public cycles (1.26의 148곳) and the one the user already named; the server knows which have a pay way and how many said
 * 맞아요 (결정 D79: never its values). Rows never wait for the server, and a late answer is never mistaken for "없어요".
 */
object PayFindRows {
    /** A company of the server as the box needs it (the app's CompanyHit, without what the pay box never shows). */
    data class Server(val id: String, val name: String, val alias: String? = null, val users: Int = 0, val pay: Boolean = false,
        val ok: Int = 0, val dept: String = "", val doubted: Boolean = false, val setup: String? = null)

    /**
     * One line: a company the server has ([serverId], with [pay] and [ok]) or one the phone knows by name only ([publicId] a public
     * row, null for the company the user already named). [industry] and [form] say what the public rows know of it.
     */
    data class Row(
        val name: String,
        val serverId: String? = null,
        val pay: Boolean = false,
        val ok: Int = 0,
        val users: Int = 0,
        val dept: String = "",
        val doubted: Boolean = false,
        val publicId: String? = null,
        val industry: String = "",
        val form: String = "",
        /** The company the user already works for (회사 이름 초안·쓰는 회사·공개 자료로 넣은 주기). */
        val known: Boolean = false,
        /** The server's 대표 주기 the pay way belongs to, to use that very one. */
        val setup: String? = null,
    ) {
        val onServer: Boolean get() = serverId != null
    }

    /** Two letters at least, as the company server and the public search ask. */
    fun ready(query: String): Boolean = PublicCycles.norm(query.trim()).length >= 2

    /**
     * The rows the phone has for [query]: the companies the user already named first ([known], when they match), then the public
     * rows, one per company (its first 사업장 stands for the rest), best match first.
     */
    fun local(query: String, known: List<String> = emptyList(), rows: List<PublicCycle> = PublicCycles.all, limit: Int = 8): List<Row> {
        if (!ready(query)) return emptyList()
        val q = query.trim()
        val mine = known.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(PublicCycles::norm)
            .filter { name -> matches(name, q) }
            .map { Row(it, known = true) }
        val public = PublicCycles.search(q, rows = rows).distinctBy { PublicCycles.norm(it.company) }
            .map { Row(it.company, publicId = it.id, industry = it.industry, form = it.formName) }
        return (mine + public).distinctBy { PublicCycles.norm(it.name) }.take(limit)
    }

    /** Whether [name] is what [q] looks for: in it, said the other way, or one 자모 off (as the company search takes it). */
    fun matches(name: String, q: String): Boolean {
        val n = PublicCycles.norm(name)
        val w = PublicCycles.norm(q)
        if (w.isEmpty()) return false
        if (w in n) return true
        if (Choseong.isQuery(q)) return q.filter { !it.isWhitespace() } in Choseong.of(name)
        return CompanyMatch.fit(name, q) != null
    }

    /**
     * [local] with the server's [server] laid over it: the server's companies first (a pay way first, then most 맞아요), each taking
     * the public row of the same name into it, then the phone's rows the server does not have. Null [server] (not answered yet,
     * or no server at all) leaves [local] as it is.
     */
    fun merge(local: List<Row>, server: List<Server>?, limit: Int = 8): List<Row> {
        if (server == null) return local.take(limit)
        val byName = local.associateBy { PublicCycles.norm(it.name) }
        val fromServer = server.sortedWith(compareByDescending<Server> { it.pay && !it.doubted }.thenByDescending { it.ok }.thenByDescending { it.users })
            .map { s ->
                val same = byName[PublicCycles.norm(s.name)] ?: s.alias?.let { byName[PublicCycles.norm(it)] }
                Row(s.name, s.id, s.pay, s.ok, s.users, s.dept, s.doubted, same?.publicId, same?.industry.orEmpty(), same?.form.orEmpty(),
                    known = same?.known == true, setup = s.setup)
            }
        val taken = fromServer.flatMap { listOfNotNull(PublicCycles.norm(it.name)) }.toSet() +
            server.mapNotNull { it.alias?.let(PublicCycles::norm) }
        return (fromServer + local.filter { PublicCycles.norm(it.name) !in taken }).take(limit)
    }

    /**
     * [PAYG-06] 1.30 묶음 BA (사용자 2026-10-27 "급여 적용 부분에서는 근무주기 적용한 회사가 있으면 뜨고, 없으면 검색, 검색에도
     * 없으면 직접 설정하는 흐름"): where 급여 방식 찾기 opens.
     */
    enum class Start {
        /** The pay already uses this company's way: said so, with 자세히 고치기 and 다른 회사 찾기. */
        USING,
        /** The company the user works for has a pay way coworkers made: "‘현대제철’ 급여 방식을 쓸까요?" at once. */
        CONFIRM,
        /** The company the user works for, with no pay way yet (or none on the 광장 at all): 직접 만들기 is the main button. */
        BY_HAND,
        /** Nothing known yet, or the server not answered: the box, as before. */
        SEARCH,
    }

    /**
     * [PAYG-06] The row of [rows] that is the company the user already works for ([known], as named on the phone), by its name
     * or alias as the server spells it; null when none is.
     */
    fun knownRow(known: String, rows: List<Row>): Row? {
        val k = PublicCycles.norm(known.trim())
        if (k.length < 2) return null
        return rows.firstOrNull { PublicCycles.norm(it.name) == k } ?: rows.firstOrNull { it.known && it.onServer }
    }

    /**
     * [PAYG-06] Where the box opens: [using] (the pay uses a company way now) first, then the user's own company as the server
     * answered it ([known]; null while it has not answered, or with no company known). A way with many 달라요 is not offered by
     * itself: the box shows it with its warning instead. [answered] false keeps the box while the answer is still coming.
     */
    fun startStep(known: Row?, using: Boolean = false, answered: Boolean = known != null): Start = when {
        using -> Start.USING
        known == null || !answered -> Start.SEARCH
        known.pay && !known.doubted -> Start.CONFIRM
        known.pay -> Start.SEARCH
        else -> Start.BY_HAND
    }

    /** "급여 방식 있음 · 맞아요 4명", the only thing said of a pay way before it is used (결정 D79). */
    fun payLine(r: Row): String = when {
        r.pay && r.doubted -> "급여 방식 있음 · 달라요가 많아요"
        r.pay -> "급여 방식 있음 · 맞아요 ${r.ok}명"
        r.onServer -> "급여 방식은 아직 없어요"
        r.publicId != null -> "근무주기 공개 자료만 있어요 · 급여 방식은 아직 없어요"
        // [PAYG-06] The user's own company the 광장 does not have at all, told apart from one with no pay way (R1 2장).
        r.known -> "광장에 아직 없는 회사예요 · 첫 급여 방식을 만들어 주세요"
        else -> "급여 방식은 아직 없어요"
    }
}
