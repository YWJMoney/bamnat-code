package com.bamnat.core.design

import java.time.Duration
import java.time.LocalDateTime

/** 1.39 [FLOW-28] What a piece of the day dial is. */
enum class DialKind { Shift, SleepPlan, Sleep }

/**
 * 1.39 [FLOW-28] One arc of the 24-hour dial: where it starts on the clock ([startHour] 0 to 24, 0시 at the top) and how many hours
 * it runs. A night shift 19:00–07:00 is one arc of 12 hours over the top, never two halves. [color] is the shift's own colour (ARGB),
 * null for the dial's sleep colours.
 */
data class DialArc(val startHour: Float, val sweepHours: Float, val kind: DialKind, val color: Long? = null)

/**
 * 1.39 [FLOW-28] 하루 다이얼 (흐름·디자인 계획 9-3, 10-5): the day's story ([DayStoryItems]) on a 24-hour clock. The ring holds the 24
 * hours from a little before [now] (3 hours, or from the start of what is going on now, so the shift worked now stays whole) — mostly
 * what is ahead, as on 오늘의 흐름's strip. Whatever runs past the ring's ends is cut there. Alarms and now are points on the clock.
 */
object DayDialArcs {
    data class Dial(val arcs: List<DialArc>, val alarms: List<Float>, val nowHour: Float, val from: LocalDateTime) {
        val isEmpty: Boolean get() = arcs.isEmpty() && alarms.isEmpty()
    }

    fun of(items: List<StoryItem>, now: LocalDateTime, shiftColor: (StoryItem) -> Long? = { null }): Dial {
        val ongoing = items.filter { i -> i.end != null && !i.start.isAfter(now) && i.end.isAfter(now) && i.kind != StoryKind.SleepPlan }
            .minOfOrNull { it.start }
        val from = minOf(now.minusHours(3), ongoing ?: now).let { if (Duration.between(it, now).toHours() >= 20) now.minusHours(20) else it }
        val to = from.plusDays(1)
        fun clock(t: LocalDateTime): Float = t.hour + t.minute / 60f
        val arcs = items.mapNotNull { i ->
            val kind = when (i.kind) {
                StoryKind.Shift -> DialKind.Shift
                StoryKind.Sleep -> DialKind.Sleep
                StoryKind.SleepPlan -> DialKind.SleepPlan
                else -> return@mapNotNull null
            }
            val end = i.end ?: return@mapNotNull null
            if (!i.start.isBefore(to) || !end.isAfter(from)) return@mapNotNull null
            val a = maxOf(i.start, from)
            val b = minOf(end, to)
            DialArc(clock(a), Duration.between(a, b).toMinutes() / 60f, kind, if (kind == DialKind.Shift) shiftColor(i) else null)
        }.sortedBy { it.kind.ordinal }
        val alarms = items.filter { it.kind == StoryKind.Alarm && !it.start.isBefore(from) && it.start.isBefore(to) }.map { clock(it.start) }
        return Dial(arcs, alarms, clock(now), from)
    }

    /** For a screen reader: what is on the ring in time order ("19:00부터 07:00까지 야간, 17:30 출근 알람"). */
    fun says(items: List<StoryItem>, dial: Dial): String {
        val to = dial.from.plusDays(1)
        fun hm(t: LocalDateTime): String = "%02d:%02d".format(t.hour, t.minute)
        return items.filter { it.kind != StoryKind.Money && it.kind != StoryKind.Memo && it.start.isBefore(to) && (it.end ?: it.start) >= dial.from }
            .sortedBy { it.start }
            .joinToString(", ") { i -> i.end?.let { "${hm(i.start)}부터 ${hm(it)}까지 ${i.title}" } ?: "${hm(i.start)} ${i.title}" }
    }
}
