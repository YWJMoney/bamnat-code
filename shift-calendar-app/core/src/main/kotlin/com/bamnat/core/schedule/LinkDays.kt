package com.bamnat.core.schedule

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import java.time.LocalDate

/**
 * [RVG-20] 2.0.0 검토 HQ-09 (조건 10): '내 근무표예요' was picked for a 가족·동료's 근무표 link, and it is found after the first setup.
 * '넣지 않기' only stops later changes; the days that link already put into the user's own calendar stayed. These are the days to
 * take out ([Split.theirs]) and the days the user changed by hand since, which stay ([Split.kept]).
 *
 * A day is still the link's when the user's own entry there is exactly what the link put in: the same shift (or 쉬는 날) as the
 * link says now or said when the days were last matched ([RosterFollow]'s basis), and nothing else of the user's on it (times of
 * its own, leave, extra work, an alarm, 지각·조퇴, a swap). A day with nothing on it is not counted: there is nothing to take out.
 */
object LinkDays {
    data class Split(val theirs: List<LocalDate>, val kept: List<LocalDate>)

    /**
     * [now] and [basis] are what the link says for each day ([RosterFollow.Their]); [pairs] maps the sender's shift ids to the user's
     * ([RosterAdopt.match]); [resolve] reads the user's calendar now. Days before [from] were never put in by the link.
     */
    fun split(
        now: Map<LocalDate, RosterFollow.Their>,
        basis: Map<LocalDate, RosterFollow.Their>?,
        pairs: Map<Long, Long?>,
        mine: List<ShiftType>,
        resolve: (LocalDate) -> ResolvedDay,
        from: LocalDate? = null,
    ): Split {
        val ids = mine.map { it.id }.toSet()
        fun target(t: RosterFollow.Their?): ShiftOverride? = when (t) {
            null -> null
            RosterFollow.Their.Rest -> ShiftOverride.Off
            is RosterFollow.Their.Shift -> pairs[t.id]?.takeIf { it in ids }?.let { ShiftOverride.Shift(it) }
        }
        val theirs = mutableListOf<LocalDate>()
        val kept = mutableListOf<LocalDate>()
        for (d in (now.keys + basis?.keys.orEmpty()).toSortedSet()) {
            if (from != null && d.isBefore(from)) continue
            val targets = setOfNotNull(target(now[d]), target(basis?.get(d)))
            if (targets.isEmpty()) continue
            val entry = resolve(d).entry ?: continue
            if (entry.override == ShiftOverride.None) continue
            if (entry.override in targets && !entry.hasOwn()) theirs += d else kept += d
        }
        return Split(theirs, kept)
    }

    /** Anything of the user's own on the day besides the shift it was given. */
    private fun DayEntry.hasOwn(): Boolean = !copy(override = ShiftOverride.None).isEmpty
}
