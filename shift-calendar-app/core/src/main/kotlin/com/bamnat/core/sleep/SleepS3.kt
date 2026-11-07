package com.bamnat.core.sleep

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 1.38 [SLEEP-10] 근무 바뀜 적응 계획: when the work moves a long way (day to night, night to day) within [LEAD_DAYS], the
 * days before move the bedtime by [STEP_MINUTES] a day towards it, with light and caffeine lines and how strong the
 * evidence for each is. A reference for the schedule, not medical advice.
 */
object ShiftAdapt {
    const val LEAD_DAYS = 3
    const val STEP_MINUTES = 30
    /** A move of this many hours or more is worth getting ready for. */
    const val BIG_HOURS = 6

    enum class Strength(val label: String) { STRONG("근거 강함"), MODERATE("근거 보통") }

    data class Step(val date: LocalDate, val shiftMinutes: Int, val toward: SleepSchedule.Work, val lines: List<Pair<String, Strength>>) {
        /** "야간까지 2일 · 오늘은 평소보다 1시간 늦게 자요". */
        val head: String get() {
            val days = Duration.between(date.atStartOfDay(), toward.date.atStartOfDay()).toDays()
            val m = abs(shiftMinutes)
            val how = if (m % 60 == 0) "${m / 60}시간" else if (m > 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
            return "${toward.name}까지 ${days}일 · 오늘은 평소보다 $how ${if (shiftMinutes > 0) "늦게" else "일찍"} 자요"
        }
    }

    /** Clock hours of a start, -12..12 apart from another. */
    internal fun hoursApart(a: LocalDateTime, b: LocalDateTime): Double {
        val ma = a.hour * 60 + a.minute; val mb = b.hour * 60 + b.minute
        var d = (mb - ma) / 60.0
        while (d > 12) d -= 24
        while (d < -12) d += 24
        return d
    }

    /**
     * The step for [today], or null. [work] is the work around today (the last few days and the next [LEAD_DAYS] + 1),
     * oldest first. The usual is the last work that started before today.
     */
    fun stepFor(today: LocalDate, work: List<SleepSchedule.Work>): Step? {
        val usual = work.lastOrNull { it.date.isBefore(today) } ?: return null
        val ahead = work.filter { it.date.isAfter(today) && !it.date.isAfter(today.plusDays(LEAD_DAYS.toLong())) }
        // Today's own work already in the old rhythm counts as usual; the first far one ahead is the change.
        val change = ahead.firstOrNull { abs(hoursApart(usual.from, it.from)) >= BIG_HOURS } ?: return null
        // Nothing in between should already be in the new rhythm (then the change began).
        if (work.any { it.date.isAfter(usual.date) && it.date.isBefore(change.date) && abs(hoursApart(usual.from, it.from)) >= BIG_HOURS }) return null
        val daysLeft = Duration.between(today.atStartOfDay(), change.date.atStartOfDay()).toDays().toInt()
        val k = LEAD_DAYS - daysLeft + 1
        // Towards nights the body moves later (staying up is easier than getting up earlier); back to days, earlier.
        fun night(t: LocalDateTime) = t.hour >= 18 || t.hour < 4
        val later = when {
            night(change.from) && !night(usual.from) -> true
            night(usual.from) && !night(change.from) -> false
            else -> hoursApart(usual.from, change.from) > 0
        }
        val shift = (if (later) 1 else -1) * STEP_MINUTES * k
        val lines = if (later) listOf(
            "늦게까지 밝은 빛 쬐기, 아침 햇빛은 선글라스로 줄이기" to Strength.MODERATE,
            "카페인은 잘 시간 6시간 전까지" to Strength.STRONG,
        ) else listOf(
            "일어나자마자 밝은 빛 쬐기, 저녁엔 화면 어둡게" to Strength.MODERATE,
            "카페인은 잘 시간 6시간 전까지" to Strength.STRONG,
        )
        return Step(today, shift, change, lines)
    }
}

/**
 * 1.38 [SLEEP-13] 원인 찾기: in the user's own mornings only, a habit that came with a tired morning more often than usual.
 * Says nothing under [MIN_TIMES] times, and only a clear difference: "‘늦은 카페인’ 고른 8번 중 6번 피곤했어요".
 */
object SleepCauses {
    const val MIN_TIMES = 5
    private const val MIN_RATE = 0.6
    private const val MIN_LIFT = 0.25

    data class Finding(val tag: SleepTag, val times: Int, val tired: Int) {
        /** "‘늦은 카페인’ 고른 8번 중 6번 피곤했어요". */
        val line: String get() = "‘${tag.label}’ 고른 ${times}번 중 ${tired}번 피곤했어요"
    }

    private fun tired(c: SleepCheck) = (c.mood?.value ?: 3) <= SleepMood.TIRED.value

    fun find(checks: List<SleepCheck>): List<Finding> {
        val answered = checks.filter { it.mood != null }
        if (answered.size < MIN_TIMES) return emptyList()
        return SleepTag.entries.mapNotNull { t ->
            val with = answered.filter { t in it.tags }
            val without = answered.filter { t !in it.tags }
            if (with.size < MIN_TIMES) return@mapNotNull null
            val rate = with.count(::tired).toDouble() / with.size
            val base = if (without.isEmpty()) 0.0 else without.count(::tired).toDouble() / without.size
            if (rate >= MIN_RATE && rate - base >= MIN_LIFT) Finding(t, with.size, with.count(::tired)) else null
        }.sortedByDescending { it.tired.toDouble() / it.times }
    }
}

/**
 * 1.38 [SLEEP-20] 근무 맞춤 회복 도전: not a fixed bedtime but the sleep each shift planned. A day counts when its planned
 * sleeps were kept ([PlanVsActual.Verdict.ON_PLAN]) and none was short; a day with no record neither counts nor breaks.
 * Rings at [RINGS]; the reward for the last ring is given once, by the reward ledger's rules.
 */
object RecoveryChallenge {
    val RINGS = listOf(14, 21)

    data class Progress(val start: LocalDate, val met: List<LocalDate>, val short: List<LocalDate>) {
        val days: Int get() = met.size
        val nextRing: Int? get() = RINGS.firstOrNull { it > days }
        val done: Boolean get() = days >= RINGS.last()
        /** "21일 중 9일 채웠어요". */
        val line: String get() = if (done) "${RINGS.last()}일을 다 채웠어요" else "${nextRing}일 중 ${days}일 채웠어요"
    }

    fun progress(start: LocalDate, nights: List<PlanVsActual.Night>, today: LocalDate): Progress {
        val inRange = nights.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
        val byDay = inRange.groupBy { it.date }
        val short = byDay.filterValues { l -> l.any { it.verdict == PlanVsActual.Verdict.SHORT } }.keys.sorted()
        val met = byDay.filterValues { l -> l.any { it.verdict == PlanVsActual.Verdict.ON_PLAN } && l.none { it.verdict == PlanVsActual.Verdict.SHORT } }
            .keys.sorted()
        return Progress(start, met, short)
    }
}

/**
 * 1.38 [SLEEP-22] 조치 전후 비교: the 7 mornings up to a day the user marked (a new pillow, no late coffee) against the 7 after,
 * in snoring minutes and morning mood. Says nothing with fewer than 3 nights on either side.
 */
object BeforeAfter {
    const val DAYS = 7L
    const val MIN_NIGHTS = 3

    data class Side(val nights: Int, val snoreMinutes: Double?, val mood: Double?)
    data class Result(val before: Side, val after: Side) {
        val line: String? get() {
            val parts = buildList {
                if (before.snoreMinutes != null && after.snoreMinutes != null)
                    add("코골이 하룻밤 ${before.snoreMinutes.roundToInt()}분 → ${after.snoreMinutes.roundToInt()}분")
                if (before.mood != null && after.mood != null)
                    add("아침 기분 ${"%.1f".format(before.mood)} → ${"%.1f".format(after.mood)}")
            }
            return parts.joinToString(" · ").ifEmpty { null }
        }
    }

    /** [snoreByNight] snoring minutes by wake date (measured nights only); [moodByNight] the morning mood (1..5). */
    fun compare(marked: LocalDate, snoreByNight: Map<LocalDate, Long>, moodByNight: Map<LocalDate, Int>): Result? {
        fun side(range: ClosedRange<LocalDate>): Side {
            val s = snoreByNight.filterKeys { it in range }.values
            val m = moodByNight.filterKeys { it in range }.values
            return Side(maxOf(s.size, m.size), s.takeIf { it.size >= MIN_NIGHTS }?.average(), m.takeIf { it.size >= MIN_NIGHTS }?.average())
        }
        // 1.39 G5 점검: the change is marked during the day ("오늘 바꾼 것"), so the night that ends on the marked morning was
        // still before it; the first night after is the one that wakes the next day.
        val before = side(marked.minusDays(DAYS - 1)..marked)
        val after = side(marked.plusDays(1)..marked.plusDays(DAYS))
        return Result(before, after).takeIf { it.line != null }
    }
}
