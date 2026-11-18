package com.bamnat.core.plaza

/**
 * 2.0.0 [RVN-22] 검토 I10bc-03 (COMP-44): what goes to the company's plaza, in three parts the user decides one by one. Before,
 * one switch ('저절로 나누기') and, from [RVN-11], the pay way's own yes; the consent's reach was blurred. Now each part is
 * 저절로 (held on the phone until the next morning, 취소 never reaches the server), 묻기 (asked with what changed in that part only)
 * or 안 함 (never sent; a change of it is not asked about again).
 */
enum class SharePart(val label: String) {
    /** The shifts there are and their order (교대 순서·요일별 근무·매달 근무표). */
    CYCLE("근무주기"),
    /** The hours of a shift that stays (07:00~19:00 → 08:00~20:00, 휴게). */
    TIMES("근무 시간"),
    /** How pay is worked out: 사업장, 가산율, 휴일·휴게, 연장·휴무일 근무, 수당 (never an amount: the plaza file has none). */
    PAY("급여 계산 방식"),
}

enum class ShareWay(val label: String) { AUTO("저절로"), ASK("묻기"), NEVER("안 함") }

/** The phone's choice for each [SharePart]. */
data class ShareWays(
    val cycle: ShareWay = ShareWay.ASK,
    val times: ShareWay = ShareWay.ASK,
    val pay: ShareWay = ShareWay.ASK,
) {
    operator fun get(part: SharePart): ShareWay = when (part) {
        SharePart.CYCLE -> cycle
        SharePart.TIMES -> times
        SharePart.PAY -> pay
    }

    fun with(part: SharePart, way: ShareWay): ShareWays = when (part) {
        SharePart.CYCLE -> copy(cycle = way)
        SharePart.TIMES -> copy(times = way)
        SharePart.PAY -> copy(pay = way)
    }

    fun with(parts: Collection<SharePart>, way: ShareWay): ShareWays = parts.fold(this) { w, p -> w.with(p, way) }

    /** Which of [parts] go [way]. */
    fun of(way: ShareWay, parts: Collection<SharePart> = SharePart.entries): Set<SharePart> = parts.filterTo(LinkedHashSet()) { get(it) == way }

    /**
     * The one switch it was before 2.0.0, for the screens that still ask it (근무주기 나누기, 첫 설정의 상자): true when the cycle goes by
     * itself, null when it is asked, false when it never goes.
     */
    val legacy: Boolean?
        get() = when (cycle) {
            ShareWay.AUTO -> true
            ShareWay.ASK -> null
            ShareWay.NEVER -> false
        }

    fun encode(): String = SharePart.entries.joinToString(",") { "${it.name}=${get(it).name}" }

    companion object {
        fun decode(text: String?): ShareWays? {
            if (text.isNullOrBlank()) return null
            var w = ShareWays()
            for (pair in text.split(',')) {
                val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return null
                val part = SharePart.entries.firstOrNull { it.name == k } ?: return null
                val way = ShareWay.entries.firstOrNull { it.name == v } ?: return null
                w = w.with(part, way)
            }
            return w
        }

        /**
         * A phone from before: null asked about everything, true 저절로 for the cycle and its hours (and the pay way only after the
         * yes that named it, [RVN-11] autoPay), false 다시 묻지 않기 for everything.
         */
        fun fromOld(autoShare: Boolean?, autoPay: Boolean): ShareWays = when (autoShare) {
            null -> ShareWays()
            true -> ShareWays(ShareWay.AUTO, ShareWay.AUTO, if (autoPay) ShareWay.AUTO else ShareWay.ASK)
            false -> ShareWays(ShareWay.NEVER, ShareWay.NEVER, ShareWay.NEVER)
        }
    }
}

object ShareParts {
    private const val OFF = "쉬는 날"

    /**
     * The part a compared row is: a shift that is there before and after, worked both times, with other hours is [SharePart.TIMES];
     * a shift added, taken away or turned into a day off, and the order, is [SharePart.CYCLE]; the rest is [SharePart.PAY].
     */
    fun of(row: CompareRow): SharePart = when {
        row.key.startsWith("shift:") && row.before != null && row.after != null && row.before != OFF && row.after != OFF -> SharePart.TIMES
        row.group == FactGroup.SHIFT || row.group == FactGroup.CYCLE -> SharePart.CYCLE
        else -> SharePart.PAY
    }

    /** The part a fact of one file is, for a file the plaza has nothing of yet. */
    fun of(fact: SetupFact): SharePart = when {
        fact.key.startsWith("shift:") && fact.value != OFF -> SharePart.TIMES
        fact.group == FactGroup.SHIFT || fact.group == FactGroup.CYCLE -> SharePart.CYCLE
        else -> SharePart.PAY
    }

    /** The parts that differ between [base] and [now]; with no [base], every part [now] has (the cycle always). */
    fun changed(base: String?, now: String): Set<SharePart> {
        if (base.isNullOrBlank()) return sortedSetOf<SharePart>().apply {
            add(SharePart.CYCLE)
            SetupFacts.read(now).forEach { add(of(it)) }
        }
        val rows = runCatching { SetupCompare.changes(base, now) }.getOrElse { return SharePart.entries.toSet() }
        return rows.mapTo(sortedSetOf()) { of(it) }
    }

    /** What changed in [parts] only, as the ask and the one line say it ([ChangeAsk]); First with no [base]. */
    fun lines(base: String?, now: String, parts: Set<SharePart>): ChangeAsk.Result {
        if (base.isNullOrBlank()) return ChangeAsk.Result.First
        val rows = runCatching { SetupCompare.changes(base, now) }.getOrElse { return ChangeAsk.Result.First }
        return ChangeAsk.of(rows.filter { of(it) in parts })
    }

    /** "근무주기·근무 시간" for the words that name what goes. */
    fun words(parts: Collection<SharePart>): String = SharePart.entries.filter { it in parts }.joinToString("·") { it.label }
}
