package com.bamnat.core.plaza

/**
 * [PLAZA-02] Two company settings files side by side, item by item (U2: "어떤 점이 다른지 제대로 비교"): what the
 * company's setup says ([before]) and what the other one says ([after]). [before] or [after] is null where only
 * one of them has the item.
 */
data class CompareRow(val key: String, val group: FactGroup, val label: String, val before: String?, val after: String?, val field: String) {
    val same: Boolean get() = before == after
    val state: State get() = when {
        before == after -> State.SAME
        before == null -> State.ADDED
        after == null -> State.REMOVED
        else -> State.CHANGED
    }

    enum class State { SAME, CHANGED, ADDED, REMOVED }
}

/** [PLAZA-02] One line of a proposal as the server takes it (`POST /api/posts`, kind proposal): at most 80 letters a side. */
data class ProposalLine(val field: String, val now: String, val want: String)

object SetupCompare {
    /** Most lines a proposal may carry (the server's readChange). */
    const val MAX_LINES = 6
    private const val MAX_TEXT = 80

    fun rows(before: String, after: String): List<CompareRow> = rows(SetupFacts.read(before), SetupFacts.read(after))

    /** Every item of either, in group order, the first file's order first within a group. */
    fun rows(before: List<SetupFact>, after: List<SetupFact>): List<CompareRow> {
        val a = before.associateBy { it.key }
        val b = after.associateBy { it.key }
        val keys = LinkedHashSet<String>().apply { before.forEach { add(it.key) }; after.forEach { add(it.key) } }
        return keys.map { k ->
            val f = a[k] ?: b.getValue(k)
            CompareRow(k, f.group, f.label, a[k]?.value, b[k]?.value, (b[k] ?: f).field)
        }.sortedBy { it.group.ordinal }
    }

    /** Only the rows that differ. */
    fun changes(before: String, after: String): List<CompareRow> = rows(before, after).filter { !it.same }

    /**
     * The differing rows as proposal lines: "근무 시각 · 주간" now → want. More than [MAX_LINES] differences keep the
     * first five and say how many more in a last line, so nothing reads as the whole when it is not.
     */
    fun proposalLines(rows: List<CompareRow>): List<ProposalLine> {
        val diff = rows.filter { !it.same }
        val lines = diff.map { r ->
            // [ITEM-04] 1.30 BC: a company's one amount stays in the setup, never in the words (the server takes no amount in them).
            val now = scrub("${r.label}: ${r.before ?: "없음"}")
            val want = scrub("${r.label}: ${r.after ?: "없음"}")
            ProposalLine(r.field, cut(now), cut(if (want == now) "$want (금액 바뀜)" else want))
        }
        if (lines.size <= MAX_LINES) return lines
        val rest = lines.size - (MAX_LINES - 1)
        return lines.take(MAX_LINES - 1) + ProposalLine("other", "", "그 밖에 ${rest}가지가 달라요 (비교 보기에서 모두 보여요)")
    }

    /** "다른 점 3가지: 야간 · 주간 · 식대" for a card, null when nothing differs. */
    fun summary(rows: List<CompareRow>, max: Int = 3): String? {
        val diff = rows.filter { !it.same }
        if (diff.isEmpty()) return null
        return "다른 점 ${diff.size}가지: " + diff.take(max).joinToString(" · ") { it.label } + if (diff.size > max) " 외" else ""
    }

    private fun cut(s: String): String = if (s.length <= MAX_TEXT) s else s.take(MAX_TEXT - 1) + "…"

    private val AMOUNT = Regex("""\s*\d[\d,]*\s*원""")

    /** "식대: 매달 회사 공통 100,000원" → "식대: 매달 회사 공통 정액". */
    internal fun scrub(s: String): String = s.replace(AMOUNT, " 정액").trim()
}

/**
 * [ITEM-08] 1.30 BC (계약): a proposal made ready for the user, who only presses 보내기: the company's version with one thing
 * changed, the lines it makes and a reason to start from. 묶음 BA's 기본급 screen opens it when the user's 통상시급 hours differ
 * from the company's (사용자 2026-10-27 "수정한다면 해당 내용을 의견으로 제시해서 사람들이 볼수 있게").
 */
data class ProposalDraft(val base: String, val text: String, val lines: List<ProposalLine>, val why: String) {
    companion object {
        /**
         * The company's version [base] with its 통상시급 hours set to [hours]; null when [base] is not a company file with pay, the
         * hours are outside 100~320, or they are already the same. [from] says where the user's number came from ("명세서").
         */
        fun hours(base: String, hours: Int, from: String = "명세서"): ProposalDraft? {
            val e = SetupEdit.of(base) ?: return null
            if (!e.hasPay || hours !in SetupEdit.HOURS_MIN..SetupEdit.HOURS_MAX) return null
            val was = e.monthlyHours
            if (was == hours) return null
            val text = e.monthlyHours(hours).text
            val lines = SetupCompare.proposalLines(SetupCompare.rows(base, text))
            if (lines.isEmpty()) return null
            return ProposalDraft(base, text, lines, "${from}에 통상시급 기준 시간이 ${hours}시간으로 나와요" + (was?.let { " (지금 ${it}시간)" } ?: ""))
        }
    }
}
