package com.bamnat.core.sleep

import java.time.Duration
import java.time.LocalDateTime

/**
 * 1.38 [SLEEP-03] 아침 한 장: after a sleep, one tap for how the user feels and a few tags for what came before it.
 * No score (SLP-13): the mood is the user's own word, kept next to how long they slept against the plan.
 */
enum class SleepMood(val value: Int, val label: String) {
    DRAINED(1, "아주 피곤"),
    TIRED(2, "피곤"),
    OKAY(3, "보통"),
    FRESH(4, "개운"),
    GREAT(5, "아주 개운");

    companion object {
        fun of(value: Int?): SleepMood? = entries.firstOrNull { it.value == value }
    }
}

/** What came before the sleep. Short words a tired thumb can read; day sleepers' light and noise among them. */
enum class SleepTag(val key: String, val label: String) {
    CAFFEINE("caffeine", "늦은 카페인"),
    ALCOHOL("alcohol", "술"),
    LATE_MEAL("meal", "야식"),
    EXERCISE("exercise", "운동"),
    LATE_PHONE("phone", "자기 전 폰"),
    LIGHT("light", "밝았음"),
    NOISE("noise", "시끄러웠음"),
    STRESS("stress", "스트레스");

    companion object {
        fun of(key: String): SleepTag? = entries.firstOrNull { it.key == key }
        fun encode(tags: Set<SleepTag>): String = entries.filter { it in tags }.joinToString(",") { it.key }
        fun decode(text: String?): Set<SleepTag> =
            text.orEmpty().split(',').mapNotNull { of(it.trim()) }.toSet()
    }
}

/** The answer for one sleep, matched to it by its times (a sleep from Health Connect has no row of ours). */
data class SleepCheck(
    val id: Long = 0,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val mood: SleepMood?,
    val tags: Set<SleepTag> = emptySet(),
    val note: String? = null,
) {
    fun matches(e: SleepEntry): Boolean {
        val end2 = e.end ?: return false
        // The same sleep, or one the user moved by a few minutes since answering.
        return start < end2 && e.start < end && Duration.between(maxOf(start, e.start), minOf(end, end2)).toMinutes() * 2 >=
            minOf(Duration.between(start, end).toMinutes(), Duration.between(e.start, end2).toMinutes())
    }
}

object SleepMorning {
    /** Asked about only for a while after waking; later it would be a guess. */
    const val ASK_HOURS = 14L
    /** A doze is not asked about. */
    const val MIN_MINUTES = 60L

    /** The latest finished sleep still worth asking about, answered or not, or null. */
    fun latest(log: List<SleepEntry>, now: LocalDateTime): SleepEntry? =
        log.asSequence()
            .filter { it.end != null && !it.end.isAfter(now) && Duration.between(it.end, now).toHours() < ASK_HOURS }
            .filter { (it.minutes ?: 0) in MIN_MINUTES..SleepDebt.MAX_SLEEP_MINUTES }
            .sortedByDescending { it.end }
            .firstOrNull()

    /** The latest finished sleep the user has not answered for, still worth asking about, or null. */
    fun pending(log: List<SleepEntry>, checks: List<SleepCheck>, now: LocalDateTime): SleepEntry? =
        latest(log, now)?.takeIf { e -> checks.none { it.matches(e) } }

    fun checkFor(e: SleepEntry, checks: List<SleepCheck>): SleepCheck? = checks.firstOrNull { it.matches(e) }

    /** One folded line after answering: "개운 · 늦은 카페인, 시끄러웠음". */
    fun summary(c: SleepCheck): String = listOfNotNull(c.mood?.label, c.tags.sortedBy { it.ordinal }.joinToString(", ") { it.label }.ifBlank { null })
        .joinToString(" · ").ifBlank { "답했어요" }
}
