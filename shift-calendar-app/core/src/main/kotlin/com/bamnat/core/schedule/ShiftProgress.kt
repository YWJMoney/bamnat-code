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
    data class Now(val from: LocalDateTime, val to: LocalDateTime, val name: String, val colorArgb: Long?, val elapsedMinutes: Long, val totalMinutes: Long) {
        val leftMinutes: Long get() = (totalMinutes - elapsedMinutes).coerceAtLeast(0)
    }

    /** The work going on at [now] among [work] (the one ending first when two meet), or null. */
    fun of(work: List<SleepSchedule.Work>, now: LocalDateTime): Now? =
        work.filter { !now.isBefore(it.from) && now.isBefore(it.to) && it.to.isAfter(it.from) }.minByOrNull { it.to }?.let { w ->
            Now(w.from, w.to, w.name, w.colorArgb, Duration.between(w.from, now).toMinutes(), Duration.between(w.from, w.to).toMinutes())
        }

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
