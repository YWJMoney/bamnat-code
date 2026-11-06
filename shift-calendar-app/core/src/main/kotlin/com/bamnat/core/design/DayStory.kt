package com.bamnat.core.design

import java.time.LocalDate
import java.time.LocalDateTime

/** 1.38 [DSG-05] What one line of a day's story is about (흐름·디자인 계획 10-4). */
enum class StoryKind { Shift, Alarm, Sleep, SleepPlan, Money, Memo, Team }

/**
 * 1.38 [DSG-05] One thing in a day's story: a span ([end] set: a shift, a sleep) or a moment (an alarm, a spend). [tone]
 * is the meaning colour's name ("good", "caution", "danger" or null), so this stays free of the app's theme.
 */
data class StoryItem(
    val start: LocalDateTime,
    val end: LocalDateTime? = null,
    val kind: StoryKind,
    val title: String,
    val detail: String? = null,
    val tone: String? = null,
    val id: String = "$kind@$start",
)

/** A [StoryItem] as the day shows it: cut to the day, and saying when it began before or runs past it. */
data class StoryLineItem(
    val item: StoryItem,
    val from: LocalDateTime,
    val to: LocalDateTime?,
    val fromYesterday: Boolean,
    val intoTomorrow: Boolean,
)

/**
 * 1.38 [DSG-05] How a day's story is laid out (흐름·디자인 계획 3-4, 10-1): everything that touches the day, in time order,
 * a night shift or a sleep that crosses midnight kept as one line that says "어제부터" or "내일까지" instead of two
 * halves. Two moments at the same minute keep the order they were given (a shift before its alarm).
 */
object DayStory {
    fun arrange(day: LocalDate, items: List<StoryItem>): List<StoryLineItem> {
        val dayStart = day.atStartOfDay()
        val dayEnd = day.plusDays(1).atStartOfDay()
        return items.mapNotNull { i ->
            val end = i.end
            val touches = if (end == null) !i.start.isBefore(dayStart) && i.start.isBefore(dayEnd)
                else i.start.isBefore(dayEnd) && end.isAfter(dayStart)
            if (!touches) return@mapNotNull null
            val from = if (i.start.isBefore(dayStart)) dayStart else i.start
            val to = end?.let { if (it.isAfter(dayEnd)) dayEnd else it }
            StoryLineItem(i, from, to, fromYesterday = i.start.isBefore(dayStart), intoTomorrow = end != null && end.isAfter(dayEnd))
        }.sortedWith(compareBy { it.from })
    }

    /** The line "지금" sits after: the last item that has begun by [now], or -1 when none has (or [now] is another day). */
    fun nowAfter(lines: List<StoryLineItem>, now: LocalDateTime): Int = lines.indexOfLast { !it.from.isAfter(now) }
}
