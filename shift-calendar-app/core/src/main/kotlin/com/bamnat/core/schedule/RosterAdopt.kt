package com.bamnat.core.schedule

import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import java.time.LocalDate
import java.time.LocalTime

/**
 * 1.38 [P3-X-03] 받은 근무표를 내 달력으로 (사용자 2026-11-06 "밤낮 없이는 안 될 정도로"): a 근무표 link from the 리더 (or anyone) becomes
 * the user's own days, so their alarms ring and their pay screen counts it. Each of the sender's shifts is paired with one of the
 * user's by its letter, then its name, then its hours; only the days that differ from the user's calendar are written.
 */
object RosterAdopt {
    data class Theirs(val id: Long, val short: String, val name: String, val start: LocalTime?, val end: LocalTime?, val off: Boolean)

    private fun norm(s: String) = s.trim().lowercase().replace(" ", "")

    /** Their shift id → mine, or null when nothing of the user's fits (the screen asks for those). */
    fun match(theirs: List<Theirs>, mine: List<ShiftType>): Map<Long, Long?> = theirs.associate { t ->
        val pick = mine.firstOrNull { norm(it.label) == norm(t.short) && it.isOff == t.off }
            ?: mine.firstOrNull { norm(it.name) == norm(t.name) && it.isOff == t.off }
            ?: mine.firstOrNull { !t.off && !it.isOff && t.start != null && it.start == t.start && it.end == t.end }
            ?: if (t.off) mine.firstOrNull { it.isOff } else null
        t.id to pick?.id
    }

    /**
     * The days to write: [days] (their shift id, or null for a day with nothing) read through [pairs], where the user's calendar
     * ([resolve]) says something else. A day with no pair, or nothing on it, is left as it is.
     */
    fun changes(days: Map<LocalDate, Long?>, pairs: Map<Long, Long?>, mine: List<ShiftType>, resolve: (LocalDate) -> ResolvedDay): Map<LocalDate, ShiftOverride> {
        val byId = mine.associateBy { it.id }
        return days.toSortedMap().mapNotNull { (d, theirId) ->
            val myId = theirId?.let(pairs::get) ?: return@mapNotNull null
            val type = byId[myId] ?: return@mapNotNull null
            val now = resolve(d).shiftType
            if (now?.id == myId) return@mapNotNull null
            d to ShiftOverride.Shift(type.id)
        }.toMap()
    }
}
