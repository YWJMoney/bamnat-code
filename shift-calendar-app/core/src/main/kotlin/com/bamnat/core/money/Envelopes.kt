package com.bamnat.core.money

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 1.37 [LEDH-11][LEDH-13] What an envelope is for: a goal the user saves toward, or tax kept aside. */
enum class EnvelopeKind(val code: Int, val label: String) {
    GOAL(0, "모으기"),
    /** [LEDH-13] 세금 봉투: 현장 3.3%·종소세, 자영업 부가세·종소세, kept aside from money that comes in. */
    TAX(1, "세금"),
    ;

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: GOAL
    }
}

/**
 * 1.37 [LEDH-11] 모으기 봉투: money set aside for something, with a goal ([target]) and maybe a day ([due]). The money
 * stays in the user's accounts; an envelope only says what part of it is for what, as a paper envelope would.
 * [sharePercent] puts that share of each pay (or, for a 세금 봉투, of each money in) into it when pay is split.
 */
data class Envelope(
    val id: Long = 0,
    val name: String,
    val target: Long = 0,
    val due: LocalDate? = null,
    val kind: EnvelopeKind = EnvelopeKind.GOAL,
    /** Share of money in that goes here when it is split, 0..100; 0 is none. */
    val sharePercent: Int = 0,
    val emoji: String = "",
    val order: Int = 0,
    /** Finished or put away: kept with its moves, not offered any more. */
    val closed: Boolean = false,
    val created: LocalDate? = null,
) {
    companion object {
        const val MAX_NAME = 20
    }
}

/** 1.37 [LEDH-11] Money put in (positive) or taken out (negative) of an envelope on [date]. */
data class EnvelopeMove(
    val id: Long = 0,
    val envelopeId: Long,
    val date: LocalDate,
    val amount: Long,
    val note: String = "",
    /** [LEDH-12] The ledger line whose money this came from (a pay, an allowance), when a rule or a split put it here. */
    val entryId: Long? = null,
)

/** 1.37 [LEDH-11] An envelope with what is in it and when it may be full. */
data class EnvelopeStatus(
    val envelope: Envelope,
    val saved: Long,
    /** Saved per month over the last three months, to say when the goal is reached at this pace. */
    val perMonth: Long,
    /** The day the goal is reached at [perMonth], or null without a goal or a pace. */
    val eta: LocalDate?,
    /** What must go in each month from now to be full by [Envelope.due]; null without a due day or once full. */
    val neededPerMonth: Long?,
) {
    val left: Long get() = (envelope.target - saved).coerceAtLeast(0)
    val full: Boolean get() = envelope.target > 0 && saved >= envelope.target
    val fraction: Float get() = if (envelope.target <= 0) 0f else (saved.toFloat() / envelope.target).coerceIn(0f, 1f)
    /** Behind: at this pace the goal comes after its day. */
    val late: Boolean get() = envelope.due != null && !full && (eta == null || eta.isAfter(envelope.due))
}

object Envelopes {
    /** 1.37 결정 5(추천): three envelopes free; more with 프로. The 세금 봉투 does not count. */
    const val FREE_COUNT = 3

    fun saved(id: Long, moves: List<EnvelopeMove>): Long = moves.filter { it.envelopeId == id }.sumOf { it.amount }

    /** [LEDH-11] Each open envelope with its money and pace, in the user's order. */
    fun status(envelopes: List<Envelope>, moves: List<EnvelopeMove>, today: LocalDate): List<EnvelopeStatus> =
        envelopes.filter { !it.closed }.sortedWith(compareBy({ it.order }, { it.id })).map { e -> statusOf(e, moves, today) }

    fun statusOf(e: Envelope, moves: List<EnvelopeMove>, today: LocalDate): EnvelopeStatus {
        val mine = moves.filter { it.envelopeId == e.id }
        val saved = mine.sumOf { it.amount }
        val since = today.minusDays(90)
        val recent = mine.filter { it.date.isAfter(since) && !it.date.isAfter(today) }.sumOf { it.amount }
        // Three months of pace, or the months (rounded up) since the first money went in when that is shorter.
        val first = mine.minOfOrNull { it.date }
        val months = if (first == null) 3.0 else Math.ceil(ChronoUnit.DAYS.between(maxOf(first, since), today) / 30.0).coerceIn(1.0, 3.0)
        val perMonth = (recent / months).toLong().coerceAtLeast(0)
        val left = (e.target - saved).coerceAtLeast(0)
        val eta = when {
            e.target <= 0 -> null
            left == 0L -> today
            perMonth <= 0 -> null
            else -> today.plusDays(Math.ceil(left * 30.0 / perMonth).toLong())
        }
        val needed = e.due?.takeIf { left > 0 && it.isAfter(today) }?.let { d ->
            val m = Math.round(ChronoUnit.DAYS.between(today, d) / 30.0).coerceAtLeast(1L).toDouble()
            Math.ceil(left / m).toLong()
        }
        return EnvelopeStatus(e, saved, perMonth, eta, needed)
    }

    /**
     * [LEDH-11] "야간 4번 더 하면": how many more shifts paying [perShift] take-home would fill what is [left]; null
     * without a pay per shift.
     */
    fun shiftsFor(left: Long, perShift: Long?): Int? =
        if (perShift == null || perShift <= 0 || left <= 0) null else Math.ceil(left.toDouble() / perShift).toInt()

    /** [LEDH-11] How many days sooner the goal comes with [extra] more put in now, at the same pace. */
    fun soonerBy(s: EnvelopeStatus, extra: Long, today: LocalDate): Int? {
        val eta = s.eta ?: return null
        if (extra <= 0 || s.perMonth <= 0) return null
        val left = (s.left - extra).coerceAtLeast(0)
        val earlier = if (left == 0L) today else today.plusDays(Math.ceil(left * 30.0 / s.perMonth).toLong())
        return ChronoUnit.DAYS.between(earlier, eta).toInt().coerceAtLeast(0)
    }

    /**
     * [LEDH-11][LEDH-13] 월급 나누기: [amount] spread over the envelopes with a share, each its [Envelope.sharePercent] of
     * it in whole won (a goal that is full takes nothing more). [taxOnly] splits only into 세금 봉투, for money in that
     * is not pay (일감·매출). The shares never add past the amount.
     */
    fun split(amount: Long, envelopes: List<Envelope>, moves: List<EnvelopeMove>, taxOnly: Boolean = false): Map<Long, Long> {
        if (amount <= 0) return emptyMap()
        var left = amount
        val out = LinkedHashMap<Long, Long>()
        for (e in envelopes.filter { !it.closed && it.sharePercent > 0 && (!taxOnly || it.kind == EnvelopeKind.TAX) }.sortedWith(compareBy({ it.kind != EnvelopeKind.TAX }, { it.order }, { it.id }))) {
            var share = amount * e.sharePercent.coerceIn(0, 100) / 100
            if (e.kind == EnvelopeKind.GOAL && e.target > 0) share = minOf(share, (e.target - saved(e.id, moves)).coerceAtLeast(0))
            share = minOf(share, left)
            if (share > 0) { out[e.id] = share; left -= share }
        }
        return out
    }

    /** Whether one more goal envelope may be made free ([pro] makes it always). */
    fun canAdd(envelopes: List<Envelope>, pro: Boolean): Boolean =
        pro || envelopes.count { !it.closed && it.kind == EnvelopeKind.GOAL } < FREE_COUNT

    /** [LEDH-13] A 세금 봉투's suggested share: 3.3% withheld work keeps 종소세 aside at 5%, 자영업 부가세 at 10% of card·cash sales. */
    fun taxShareFor(style: com.bamnat.core.work.WorkStyle?): Int = when (style) {
        com.bamnat.core.work.WorkStyle.FIELD -> 5
        com.bamnat.core.work.WorkStyle.BUSINESS -> 10
        else -> 0
    }
}
