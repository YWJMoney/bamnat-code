package com.bamnat.core.plaza

/**
 * [PLAZA-34] 1.27 묶음 AI (사용자 2026-10-25: "사용자가 주기를 바꾸거나 급여설정을 바꾸면 해당 내용을 나눌까요로 … 내용 수정시
 * 물어보게"): what changed in the setup that would go to the company's plaza since the one last shared or asked about, in
 * the plaza's own words ([SetupFacts]), so the ask says it ("야간 가산 50% → 60%") and the same values saved again ask
 * nothing. Only what the plaza file says counts: amounts are never in it.
 */
object ChangeAsk {
    /** Most lines the ask shows; the rest is "그 밖에 n가지". */
    const val MAX_LINES = 3

    sealed interface Result {
        /** Nothing the plaza would see is different. */
        data object Same : Result
        /** Nothing was shared or asked before: the whole setup is new to the plaza. */
        data object First : Result
        data class Changed(val lines: List<String>, val more: Int) : Result
    }

    /** [base] the file last shared or asked about (null for none), [now] the file as it would go now. */
    fun of(base: String?, now: String): Result {
        if (base.isNullOrBlank()) return Result.First
        val rows = runCatching { SetupCompare.changes(base, now) }.getOrElse { return Result.First }
        if (rows.isEmpty()) return Result.Same
        val lines = rows.map { r ->
            when {
                r.before == null -> "${r.label} ${r.after} 새로"
                r.after == null -> "${r.label} 뺌"
                else -> "${r.label} ${r.before} → ${r.after}"
            }
        }
        return Result.Changed(lines.take(MAX_LINES), (lines.size - MAX_LINES).coerceAtLeast(0))
    }

    /** "야간 가산 50% → 60% · 주간 07:00~19:00 → 08:00~20:00" for a one-line note, null for none. */
    fun line(r: Result): String? = (r as? Result.Changed)?.let { c ->
        c.lines.joinToString(" · ") + if (c.more > 0) " 외 ${c.more}가지" else ""
    }
}
