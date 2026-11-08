package com.bamnat.core.schedule

import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import java.time.LocalDate

/**
 * 1.39 [TEAM-22] D9 받은 근무표가 바뀌면 내 달력도 (사용자 2026-11-07 '전부 1.39로'): a 리더 fixes the team's roster and sends it again on
 * the same link; a member whose own days came from that link (or who put its days in once) gets the changed days in their own
 * calendar, so the 근무 알람 does not ring for the old shift. Only days the link itself changed are looked at, and a day the member
 * changed by hand after it came (their calendar no longer says what the old link said) is kept and only reported.
 */
object RosterFollow {
    /** What the link says for one day: one of the sender's shifts, or a day with nothing (쉬는 날). Leave is the member's own. */
    sealed interface Their {
        data class Shift(val id: Long) : Their
        data object Rest : Their
    }

    /**
     * [apply]: the days to write, with what goes in; [kept]: days the link changed but the member had changed by hand, left as they are;
     * [lines]: "12/5 주간 → 야간" for each applied day, in date order.
     */
    data class Plan(val apply: Map<LocalDate, ShiftOverride>, val kept: List<LocalDate>, val lines: List<String>) {
        val isEmpty: Boolean get() = apply.isEmpty() && kept.isEmpty()
    }

    /**
     * [old] is the link as this phone had it before (null when there was no copy: nothing is taken as changed by hand), [new] as it
     * came now. [pairs] maps the sender's shift ids to the member's ([RosterAdopt.match]); [resolve] reads the member's calendar now.
     * Days before [from] are not touched (a closed past), nor a day whose shift has no pair.
     */
    fun plan(
        old: Map<LocalDate, Their>?,
        new: Map<LocalDate, Their>,
        pairs: Map<Long, Long?>,
        mine: List<ShiftType>,
        resolve: (LocalDate) -> ResolvedDay,
        from: LocalDate? = null,
    ): Plan {
        val byId = mine.associateBy { it.id }
        fun target(t: Their?): ShiftOverride? = when (t) {
            null -> null
            Their.Rest -> ShiftOverride.Off
            is Their.Shift -> pairs[t.id]?.takeIf { it in byId }?.let { ShiftOverride.Shift(it) }
        }
        fun says(d: LocalDate, o: ShiftOverride): Boolean {
            val now = resolve(d).shiftType
            return when (o) {
                is ShiftOverride.Shift -> now?.id == o.shiftTypeId
                ShiftOverride.Off -> now == null || now.isOff
                ShiftOverride.None -> true
            }
        }
        fun label(d: LocalDate): String = resolve(d).shiftType?.label?.takeIf { it.isNotBlank() } ?: "쉬는 날"
        val apply = sortedMapOf<LocalDate, ShiftOverride>()
        val kept = mutableListOf<LocalDate>()
        val lines = mutableListOf<String>()
        for ((d, t) in new.toSortedMap()) {
            if (from != null && d.isBefore(from)) continue
            val was = old?.get(d)
            if (old != null && was == t) continue // the link did not change this day
            val to = target(t) ?: continue
            if (says(d, to)) continue // the member's calendar says it already
            val before = target(was)
            if (old != null && before != null && !says(d, before)) { kept += d; continue }
            apply[d] = to
            val toLabel = when (to) {
                is ShiftOverride.Shift -> byId[to.shiftTypeId]?.label?.takeIf { it.isNotBlank() } ?: byId[to.shiftTypeId]?.name ?: ""
                else -> "쉬는 날"
            }
            lines += "${d.monthValue}/${d.dayOfMonth} ${label(d)} → $toLabel"
        }
        return Plan(apply, kept, lines)
    }
}
