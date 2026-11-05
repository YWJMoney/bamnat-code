package com.bamnat.core.sleep

import com.bamnat.core.schedule.ScheduleResolver
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * [HLTH-01][P3-04] 1.38 Phase 3 M23 (설계 2-3, 순번 510 [SLP-08 완료]): the sleep the schedule planned ([SleepSchedule]: after work
 * that ran overnight, the night before an early start, the nap before a first night) against the sleep that was measured by a
 * watch or the phone, or written down. Worked out on the phone every time it is shown and never stored (Health Connect stays
 * the source, 설계 1-3 "M23은 DB를 바꾸지 않아요"); nothing here goes to a server.
 *
 * Words stay at "견주기": it says how the night went against the plan, never a diagnosis or a forecast (설계 2-3 위험, the
 * same line as [SleepPlan]). The 피로 요인 check (순번 511) is the next 판, not this one.
 */
object PlanVsActual {
    /** A recorded sleep belongs to a planned one when it overlaps the plan widened by this on either side. */
    const val MATCH_SLACK_MINUTES = 3 * 60L
    /** Less than the plan by more than this is "적게 잠". */
    const val SHORT_MINUTES = 60L
    /** A planned sleep this short (a nap) is shown but never called short. */
    const val NAP_MAX_MINUTES = 120L
    const val WINDOW_DAYS = 28L

    enum class Kind(val label: String) { AFTER_NIGHT("퇴근 뒤 잠"), BEFORE_EARLY("이른 근무 전 잠"), NAP("야간 전 낮잠") }

    enum class Verdict(val label: String) {
        /** Within [SHORT_MINUTES] of the plan (or more). */
        ON_PLAN("계획만큼"),
        SHORT("계획보다 적게"),
        /** Nothing was recorded around the planned time (the watch was off, say): not counted as short. */
        NO_RECORD("기록 없음"),
    }

    /** One planned sleep and what was recorded around it. */
    data class Night(
        val date: LocalDate,
        val kind: Kind,
        val planFrom: LocalDateTime,
        val planTo: LocalDateTime,
        /** The recorded sleeps that overlap it, earliest first (two pieces of one broken sleep, say). */
        val actual: List<SleepEntry>,
    ) {
        val planMinutes: Long get() = Duration.between(planFrom, planTo).toMinutes()
        /** Minutes asleep in [actual] (pieces summed, overlaps counted once by [SleepMerge] before this). */
        val actualMinutes: Long? get() = actual.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.sum()
        val actualFrom: LocalDateTime? get() = actual.minOfOrNull { it.start }
        val actualTo: LocalDateTime? get() = actual.mapNotNull { it.end }.maxOrNull()
        /** actual − plan; negative is less sleep than planned. */
        val diffMinutes: Long? get() = actualMinutes?.let { it - planMinutes }
        val measured: Boolean get() = actual.any { it.imported }
        val verdict: Verdict get() = when {
            actualMinutes == null -> Verdict.NO_RECORD
            kind != Kind.NAP && planMinutes > NAP_MAX_MINUTES && diffMinutes!! < -SHORT_MINUTES -> Verdict.SHORT
            else -> Verdict.ON_PLAN
        }

        /** "계획 23:30~07:00 (7시간 30분) · 실제 00:10~06:20 (6시간 10분) · 1시간 20분 적게". */
        val line: String get() {
            val plan = "계획 ${clock(planFrom)}~${clock(planTo)} (${SleepInsights.hours(planMinutes)})"
            val got = actualMinutes ?: return "$plan · 기록 없음"
            val span = if (actualFrom != null && actualTo != null) "${clock(actualFrom!!)}~${clock(actualTo!!)} " else ""
            val diff = diffMinutes!!
            val tail = when {
                abs(diff) < 10 -> "계획과 거의 같아요"
                diff < 0 -> "${SleepInsights.hours(-diff)} 적게"
                else -> "${SleepInsights.hours(diff)} 더"
            }
            return "$plan · 실제 $span(${SleepInsights.hours(got)}) · $tail"
        }
    }

    /** The last weeks put together: how many planned sleeps had a record, how many were short, and the average gap. */
    data class Summary(val nights: List<Night>) {
        val recorded: List<Night> get() = nights.filter { it.verdict != Verdict.NO_RECORD && it.kind != Kind.NAP }
        val short: Int get() = recorded.count { it.verdict == Verdict.SHORT }
        val averageDiff: Long? get() = recorded.mapNotNull { it.diffMinutes }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        /** Average gap after work that ran overnight, the night the plan matters most. */
        val afterNightDiff: Long? get() = recorded.filter { it.kind == Kind.AFTER_NIGHT }.mapNotNull { it.diffMinutes }
            .takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
        /** Too few to say anything about the weeks (one night is a night, not a habit). */
        val enough: Boolean get() = recorded.size >= MIN_FOR_SUMMARY

        val headline: String? get() {
            if (!enough) return null
            val avg = averageDiff ?: return null
            return when {
                avg <= -SHORT_MINUTES -> "계획보다 평균 ${SleepInsights.hours(-avg)} 적게 잤어요"
                avg < -10 -> "계획보다 평균 ${SleepInsights.hours(-avg)} 적게, 거의 계획대로예요"
                else -> "계획만큼 잤어요"
            } + " · 견준 잠 ${recorded.size}번 중 적게 잔 날 ${short}번"
        }
    }

    const val MIN_FOR_SUMMARY = 3

    /**
     * The planned sleeps from [from] to [to] and the records around each. [log] is the merged log ([SleepMerge.merge] of what
     * was written down and what Health Connect gave). A record is matched to one plan only, the first it overlaps.
     */
    fun nights(
        log: List<SleepEntry>,
        resolver: ScheduleResolver,
        from: LocalDate,
        to: LocalDate,
        alarmsOn: Boolean = true,
        targetMinutes: Int = SleepPlan.DEFAULT_TARGET_MINUTES,
        commuteMinutes: Int = SleepPlan.COMMUTE_MINUTES.toInt(),
    ): List<Night> = of(SleepSchedule.of(resolver, from, to, alarmsOn, targetMinutes, commuteMinutes), log, from, to)

    /** As [nights], from a plan already made (the day view makes one for its tips). */
    fun of(schedule: SleepSchedule, log: List<SleepEntry>, from: LocalDate, to: LocalDate): List<Night> {
        data class Planned(val date: LocalDate, val kind: Kind, val from: LocalDateTime, val to: LocalDateTime)
        val planned = buildList {
            for (s in schedule.sleeps) {
                val kind = if (s.after != null) Kind.AFTER_NIGHT else Kind.BEFORE_EARLY
                // The day a sleep is "of": the work it follows, or the morning it ends on.
                val date = s.after?.date ?: s.to.toLocalDate()
                add(Planned(date, kind, s.from, s.to))
            }
            for (n in schedule.naps) add(Planned(n.before.date, Kind.NAP, n.from, n.to))
        }.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.from }

        val closed = log.filter { it.end != null && it.end.isAfter(it.start) }.sortedBy { it.start }
        val used = HashSet<SleepEntry>()
        return planned.map { p ->
            val lo = p.from.minusMinutes(MATCH_SLACK_MINUTES)
            val hi = p.to.plusMinutes(MATCH_SLACK_MINUTES)
            // A nap only takes a record that starts before the night shift it is for; a main sleep any that overlaps.
            val hits = closed.filter { it !in used && it.start < hi && it.end!! > lo }
                .filter { p.kind != Kind.NAP || (it.minutes ?: 0) <= NAP_MAX_MINUTES * 2 }
            used += hits
            Night(p.date, p.kind, p.from, p.to, hits)
        }
    }

    /** The night of [date] worth a line in the day view: the main sleep before the nap. */
    fun forDay(nights: List<Night>, date: LocalDate): List<Night> = nights.filter { it.date == date }.sortedBy { it.kind != Kind.AFTER_NIGHT && it.kind != Kind.BEFORE_EARLY }

    fun summary(nights: List<Night>): Summary = Summary(nights)

    private fun clock(t: LocalDateTime): String = "%02d:%02d".format(t.hour, t.minute)
}
