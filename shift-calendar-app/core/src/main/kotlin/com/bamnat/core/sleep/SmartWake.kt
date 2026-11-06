package com.bamnat.core.sleep

/**
 * 1.38 [SLEEP-08] 깨우는 구간: within [minutes] before the alarm, ring as soon as the sleep turns light: the phone or the
 * sheets moved, or a word was said, at least [MIN_MOVES] times [MOVES_APART_MILLIS] apart within [LIGHT_WITHIN_MILLIS], all
 * inside the window (PC 11차: one bump is not a light sleep). Only earlier, never later: when nothing moved the on-time alarm
 * rings as always.
 */
object SmartWakeRule {
    val CHOICES_MINUTES = listOf(0, 10, 20, 30)
    const val LIGHT_WITHIN_MILLIS = 90_000L
    /** Too close to the alarm to be worth it: let the alarm ring. */
    const val MIN_EARLY_MILLIS = 60_000L
    const val MIN_MOVES = 2
    const val MOVES_APART_MILLIS = 5_000L

    fun windowStart(triggerAt: Long, minutes: Int): Long? = if (minutes <= 0) null else triggerAt - minutes * 60_000L

    /** Whether to ring now, at [now], for the alarm at [triggerAt] with its window of [minutes]. */
    fun wakeNow(now: Long, triggerAt: Long, minutes: Int, moves: List<Long>): Boolean {
        val from = windowStart(triggerAt, minutes) ?: return false
        if (now < from || triggerAt - now < MIN_EARLY_MILLIS) return false
        val recent = moves.filter { it >= from && it <= now && now - it <= LIGHT_WITHIN_MILLIS }.sorted()
        if (recent.isEmpty()) return false
        // Moves this far apart count apart: a shake's many readings in one second are one move.
        var count = 1
        var last = recent.first()
        for (t in recent.drop(1)) if (t - last >= MOVES_APART_MILLIS) { count++; last = t }
        return count >= MIN_MOVES
    }

    /** "얕은 잠이라 12분 일찍 깨웠어요". */
    fun line(earlyMinutes: Long): String = "얕은 잠이라 ${earlyMinutes}분 일찍 깨웠어요"

    fun label(minutes: Int): String = if (minutes <= 0) "끔" else "${minutes}분"
}
