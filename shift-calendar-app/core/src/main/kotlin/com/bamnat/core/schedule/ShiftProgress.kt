package com.bamnat.core.schedule

import com.bamnat.core.sleep.SleepSchedule
import java.time.Duration
import java.time.LocalDateTime

/**
 * 2.0.0 [RVI-97] (검토 I03c-03) 근무가 흐르는 잠금 화면: during work, how far through it and how long to its end, the thing most looked
 * at on shift. The same blocks of work as 오늘의 흐름 ([SleepSchedule.work]: the shift, a night's run over midnight, a 대근), so the lock
 * screen, the watch's 끝까지 and the calendar never count differently.
 */
object ShiftProgress {
    data class Now(
        val from: LocalDateTime, val to: LocalDateTime, val name: String, val colorArgb: Long?, val elapsedMinutes: Long, val totalMinutes: Long,
        /** 2.0.0 [RVIB-21] (결정 19) This part stops at [to] and work goes on after it: count to the 브레이크 타임·비는 시간, not to 퇴근. */
        val pausesAtEnd: Boolean = false,
    ) {
        val leftMinutes: Long get() = (totalMinutes - elapsedMinutes).coerceAtLeast(0)
    }

    /**
     * The work going on at [now] among [work] (the one ending first when two meet), or null. 2.0.0 [RVIB-21] (결정 19) On a day of two
     * parts it is the part [now] is in, so the bar fills to the stop between them; in the stop itself nothing is going ([stopOf]).
     */
    fun of(work: List<SleepSchedule.Work>, now: LocalDateTime): Now? =
        work.flatMap { w -> w.parts.map { w to it } }
            .filter { (_, p) -> !now.isBefore(p.start) && now.isBefore(p.endInclusive) && p.endInclusive.isAfter(p.start) }
            .minByOrNull { (_, p) -> p.endInclusive }?.let { (w, p) ->
                Now(p.start, p.endInclusive, w.name, w.colorArgb, Duration.between(p.start, now).toMinutes(),
                    Duration.between(p.start, p.endInclusive).toMinutes(), pausesAtEnd = p.endInclusive.isBefore(w.to))
            }

    /** [RVIB-21] The stop between two parts [now] is in (its start and when work starts again), or null. */
    fun stopOf(work: List<SleepSchedule.Work>, now: LocalDateTime): ClosedRange<LocalDateTime>? = work.firstNotNullOfOrNull { it.stopAt(now) }

    /** [RVIB-21] Every moment the picture changes from [now] on: each part's start and end, earliest first. */
    fun changes(work: List<SleepSchedule.Work>, now: LocalDateTime): List<LocalDateTime> =
        work.flatMap { w -> w.parts.flatMap { listOf(it.start, it.endInclusive) } }.filter { it.isAfter(now) }.distinct().sorted()

    /** "퇴근까지 3시간 20분", "마감까지 45분": [untilEnd] is the kind of work's word ([com.bamnat.core.sleep.TodayFlow.WorkTalk.untilEnd]). */
    fun left(n: Now, untilEnd: String): String {
        val h = n.leftMinutes / 60
        val m = n.leftMinutes % 60
        return "$untilEnd " + when {
            h == 0L -> "${m}분"
            m == 0L -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }

    /** The status bar chip: "3:20" left. */
    fun chip(n: Now): String = "%d:%02d".format(n.leftMinutes / 60, n.leftMinutes % 60)

    /** When to draw it again: every [stepMinutes] while on, and at the end. */
    fun nextDraw(n: Now, now: LocalDateTime, stepMinutes: Long = 15): LocalDateTime = minOf(now.plusMinutes(stepMinutes), n.to)
}
