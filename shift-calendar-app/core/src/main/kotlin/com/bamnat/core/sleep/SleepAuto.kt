package com.bamnat.core.sleep

import java.time.Duration
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-01] 자동으로 잡은 잠: sleep the phone noticed by itself (Google Play services' sleep segments, which read
 * light and stillness on the phone), cleaned up with what the app already knows, and offered to the user as one line in
 * the morning: "07:40–14:10, 맞나요?". Nothing goes into the log before the user says yes, except after they have said yes
 * [TRUST_STREAK] times in a row without fixing one, when it goes in by itself with a 자동 mark they can still fix.
 *
 * Why the phone and not only the microphone (사용자 관찰/공개 정보): the sleep app we looked at counts quiet time with the
 * phone in hand as deep sleep and needs a tap every night; the phone's own stillness, light and the alarm the user turned
 * off say more about being awake than sound does, and need no tap.
 */
object SleepAuto {
    /** Two pieces closer than this are one sleep with a short waking (the bathroom at 4). */
    const val MERGE_GAP_MINUTES = 30L
    /** Shorter than this is not offered; a nap the user wants is still one tap away. */
    const val MIN_MINUTES = 40L
    /** An alarm turned off this long before the end, or after it, is when the sleep really ended. */
    const val ALARM_BEFORE_END_MINUTES = 60L
    const val ALARM_AFTER_END_MINUTES = 30L
    /** Offered only once it is over for a while, so a sleep still going on is not cut. */
    const val SETTLE_MINUTES = 10L
    /** Offered for this long; after that a sleep nobody confirmed is let go. */
    const val OFFER_HOURS = 30L
    const val TRUST_STREAK = 2

    data class Segment(val start: LocalDateTime, val end: LocalDateTime) {
        val minutes: Long get() = Duration.between(start, end).toMinutes()
        /** A stable name for the sleep, for "the user said no to this one". */
        val key: String get() = "$start/$end"
    }

    /**
     * The sleeps to offer, oldest first: [segments] joined across short wakings, trimmed to the alarm the user turned off
     * ([alarmsOff]), without what is already in [logged] (the user's own taps and Health Connect) or was [declined].
     */
    fun candidates(
        segments: List<Segment>,
        logged: List<SleepEntry>,
        alarmsOff: List<LocalDateTime>,
        declined: Set<String>,
        now: LocalDateTime,
    ): List<Segment> {
        val merged = merge(segments.filter { it.end.isAfter(it.start) })
        return merged.asSequence()
            .map { s -> trimToAlarm(s, alarmsOff) }
            .filter { it.minutes in MIN_MINUTES..SleepDebt.MAX_SLEEP_MINUTES }
            .filter { !it.end.isAfter(now.minusMinutes(SETTLE_MINUTES)) && Duration.between(it.end, now).toHours() < OFFER_HOURS }
            .filter { s -> logged.none { e -> overlaps(s, e) } }
            .filter { it.key !in declined }
            .toList()
    }

    internal fun merge(segments: List<Segment>): List<Segment> {
        val sorted = segments.sortedBy { it.start }
        val out = ArrayList<Segment>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && !s.start.isAfter(last.end.plusMinutes(MERGE_GAP_MINUTES))) {
                out[out.size - 1] = Segment(last.start, maxOf(last.end, s.end))
            } else out += s
        }
        return out
    }

    internal fun trimToAlarm(s: Segment, alarmsOff: List<LocalDateTime>): Segment {
        val off = alarmsOff.filter { a ->
            a.isAfter(s.start.plusMinutes(MIN_MINUTES)) &&
                !a.isBefore(s.end.minusMinutes(ALARM_BEFORE_END_MINUTES)) && !a.isAfter(s.end.plusMinutes(ALARM_AFTER_END_MINUTES))
        }.minOrNull() ?: return s
        return Segment(s.start, off)
    }

    private fun overlaps(s: Segment, e: SleepEntry): Boolean {
        val end = e.end ?: LocalDateTime.MAX
        return s.start < end && e.start < s.end
    }

    /** After this many yeses in a row, sleeps go in by themselves. A fix or a no starts the count again. */
    fun trusted(streak: Int): Boolean = streak >= TRUST_STREAK

    fun line(s: Segment, now: LocalDateTime): String {
        val day = when (s.end.toLocalDate()) {
            now.toLocalDate() -> ""
            now.toLocalDate().minusDays(1) -> "어제 "
            else -> "${s.end.monthValue}/${s.end.dayOfMonth} "
        }
        return "$day${hm(s.start)}–${hm(s.end)} · ${SleepInsights.hours(s.minutes)}"
    }

    private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
}
