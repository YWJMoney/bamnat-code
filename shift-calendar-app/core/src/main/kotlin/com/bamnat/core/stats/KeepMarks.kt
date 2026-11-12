package com.bamnat.core.stats

/**
 * 1.39 [OBS2-08] 기능과 머묾 (사용자 2026-11-08 관측 데이터 카드 '유형별·기능 둘만', 깃발 `stats.keep`, 처리방침 3.12).
 *
 * On the first open of a day, the phone looks at the day it was last opened. If that day was at most [WINDOWS] days ago, the
 * features used that day "came back": within 7 days, or within 30. Only that goes with 사용 통계: the day the features were used,
 * the window (7 or 30) and their names. The server adds it up per day and name and divides by the phones that used the name that
 * day (usage_events), so 'came back within 7 days after using X' needs no phone id and never links two days on the server.
 *
 * A phone that never opens again sends nothing, which is the point: the ones missing from the count are the ones who left.
 * [UsageEvent.OPEN] is in the list too, so every feature can be compared with all phones that day.
 */
object KeepMarks {
    /** Came back within 7 days, or within 8~30. Longer gaps say nothing about one day's features. */
    val WINDOWS = listOf(7L, 30L)

    /** The 진입·머묾 marks (life_*) are about the phone, not a feature, and are left out; so are 1.40's disc_* card totals. */
    fun counts(e: UsageEvent): Boolean = e !in UsageEvent.POLICY_312 && e !in UsageEvent.POLICY_313

    /** What came back: the day the features were used, the window it falls in and the names. */
    data class Mark(val day: Long, val window: Long, val events: List<String>)

    /**
     * On [today]'s first open, with [lastOpenDay] the open before it and [usedOn] the features used on a day (from [UsageBook.seen]).
     * Null when there was no earlier open, it was today already, it was too long ago, or nothing counted was used that day.
     */
    fun of(lastOpenDay: Long?, today: Long, usedOn: (Long) -> Set<String>): Mark? {
        if (lastOpenDay == null || lastOpenDay >= today) return null
        val gap = today - lastOpenDay
        val window = WINDOWS.firstOrNull { gap <= it } ?: return null
        val names = usedOn(lastOpenDay).filter { k -> UsageEvent.of(k)?.let(::counts) == true }.sorted()
        return if (names.isEmpty()) null else Mark(lastOpenDay, window, names)
    }

    /** One waiting mark per line: "day\twindow\tname,name". */
    fun encode(marks: List<Mark>): String = marks.joinToString("\n") { "${it.day}\t${it.window}\t${it.events.joinToString(",")}" }

    /** Marks back, dropping lines that are not ours and any older than [keepFrom] (an epoch day). */
    fun decode(text: String?, keepFrom: Long): List<Mark> = text.orEmpty().lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 3) return@mapNotNull null
        val day = p[0].toLongOrNull()?.takeIf { it >= keepFrom } ?: return@mapNotNull null
        val window = p[1].toLongOrNull()?.takeIf { it in WINDOWS } ?: return@mapNotNull null
        val names = p[2].split(',').filter { k -> UsageEvent.of(k)?.let(::counts) == true }
        if (names.isEmpty()) null else Mark(day, window, names)
    }.toList()

    /** Marks wait at most this many days (the server takes a used day up to 40 days back). */
    const val KEEP_DAYS = 35L
    /** At most this many marks wait (one a day at most, so a month and a bit). */
    const val MAX_MARKS = 40
}
