package com.bamnat.core.model

/**
 * [R200-01] A kind of day named across the user's calendars. Each calendar keeps its own database, so the same shift
 * id is a different kind of day in each; settings that are kept by kind of day (끄기 미션, 근무 그림) key it with the
 * calendar too: "3" for the main calendar (what every older phone and backup already wrote) and "<calendar>:3" for an
 * added one. An older app reads only the plain ones and leaves the rest alone.
 */
object ShiftRef {
    const val MAIN = "main"
    private val KEY = Regex("^(?:([a-z0-9]{1,32}):)?(\\d{1,18})$")

    fun key(calendarId: String?, shiftId: Long): String =
        if (calendarId == null || calendarId == MAIN) shiftId.toString() else "$calendarId:$shiftId"

    fun isKey(s: String): Boolean = KEY.matches(s)

    /** The calendar a key belongs to: [MAIN] for a plain id. */
    fun calendarOf(key: String): String? = KEY.matchEntire(key)?.let { it.groupValues[1].ifEmpty { MAIN } }

    fun shiftOf(key: String): Long? = KEY.matchEntire(key)?.groupValues?.get(2)?.toLongOrNull()

    /** Only [calendarId]'s entries, by shift id. */
    fun <V> of(map: Map<String, V>, calendarId: String?): Map<Long, V> {
        val cal = calendarId ?: MAIN
        return map.mapNotNull { (k, v) -> if (calendarOf(k) == cal) shiftOf(k)?.let { it to v } else null }.toMap()
    }

    /** [map] without [calendarId]'s entries (a calendar deleted for good). */
    fun <V> without(map: Map<String, V>, calendarId: String): Map<String, V> = map.filterKeys { calendarOf(it) != calendarId }
}
