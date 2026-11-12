package com.bamnat.core.schedule

import com.bamnat.core.model.Leave
import com.bamnat.core.model.WorkTime
import java.time.LocalDate
import java.time.LocalTime

/**
 * Where leave takes its part of a working day ([ScheduleResolver]). Offsets are minutes from the day's start on the clock.
 *
 * - 반차·반반차·0.75일 (quarters, [FEAT-10]): as before 2.0.0, the leave is that part of the day's work, the break shrinks in
 *   proportion and the clock is cut from the end (or the start, 오전), so 반차 of 09~18 with an hour's break is 09~13:30.
 * - 2.0.0 [RVH-44] On a day of two parts the cut goes along the parts and never counts the gap (설계 2-7절): 뒤쪽 반차 of 11~15·17~21
 *   leaves 11~15, of 10~15·17~20 leaves 10~14. A cut that passes the gap takes it too: one part is left.
 * - [RVH-45] An hour's leave takes its own minutes of work, off the start, the end or the middle (외출, [Leave.at]). A break whose
 *   clock time is known and that the cut passes over comes out with it, never shrunk in proportion (설계 3-3절: 09~11 two hours'
 *   leave is 120 minutes and the 12:00 break stays). A day with a gap takes no leave in the middle; it comes off the end.
 */
internal data class DayCut(
    val start: LocalTime?,
    val end: LocalTime?,
    val breakMinutes: Int,
    val leaveMinutes: Int,
    /** The gap still in the day after the cut, from the day's start; null when there is none or the leave took it. */
    val gapOffset: Int?,
    /** An hour's leave in the middle of the day, from the day's start. */
    val holeFrom: Int? = null,
    val holeTo: Int? = null,
) {
    /** A stretch of the clock that is not work: the gap, or a break whose time is known. [to] is not in it. */
    private data class Hole(val from: Int, val to: Int)

    companion object {
        fun of(
            date: LocalDate,
            start: LocalTime,
            end: LocalTime,
            breakMinutes: Int,
            breakStart: LocalTime?,
            gapAt: LocalTime?,
            gap: Int,
            leave: Leave,
            fullScheduled: Int,
        ): DayCut {
            if (leave.isFullDay) return DayCut(null, null, 0, fullScheduled, null)
            val span = WorkTime.spanMinutes(start, end)
            val gapHole = gapAt?.takeIf { gap > 0 }?.let { WorkTime.offset(start, it) }?.let { Hole(it, it + gap) }
            val base = date.atTime(start)
            fun sameDate(offset: Int) = base.plusMinutes(offset.toLong()).toLocalDate() == date
            fun gapLeft(from: Int, to: Int) = gapHole?.takeIf { it.from > from && it.to < to }?.from
            fun at(offset: Int) = start.plusMinutes(offset.toLong())

            val hours = leave.minutes
            if (hours == null) {
                // 반차·반반차·0.75일, as before 2.0.0 on a day of one part.
                val leaveMinutes = Math.round(fullScheduled * leave.quarters / 4.0).toInt()
                val workBreak = Math.round(breakMinutes * (4 - leave.quarters) / 4.0).toInt()
                val off = leaveMinutes + breakMinutes - workBreak
                val holes = listOfNotNull(gapHole)
                val later = forward(0, off, holes, skipLanding = true)
                val (from, to) = if (leave.offStart && sameDate(later)) later to span else 0 to backward(span, off, holes)
                return DayCut(at(from), at(to), workBreak, leaveMinutes, gapLeft(from, to))
            }

            // [RVH-45] An hour's leave as long as the day's work or longer is the whole day off.
            if (hours >= fullScheduled) return DayCut(null, null, 0, fullScheduled, null)
            val restAt = breakStart?.takeIf { breakMinutes > 0 }?.let { WorkTime.offset(start, it) }
                ?.takeIf { it + breakMinutes <= span }
            val rest = restAt?.let { Hole(it, it + breakMinutes) }
            val holes = listOfNotNull(gapHole, rest).sortedBy { it.from }
            fun restIn(from: Int, to: Int): Int = rest?.let { (minOf(it.to, to) - maxOf(it.from, from)).coerceAtLeast(0) } ?: 0
            fun offEnd(): DayCut {
                val to = backward(span, hours, holes)
                return DayCut(at(0), at(to), breakMinutes - restIn(to, span), hours, gapLeft(0, to))
            }

            val middle = leave.at?.takeIf { gapHole == null }?.let { WorkTime.offset(start, it) }
            if (middle != null && middle > 0 && middle < span) {
                // 외출: from its own time for its minutes of work; a break it passes over on the clock comes out with it.
                val back = forward(middle, hours, listOfNotNull(rest), skipLanding = false)
                if (back < span) return DayCut(at(0), at(span), breakMinutes - restIn(middle, back), hours, null, middle, back)
                return offEnd()
            }
            if (leave.offStart || middle == 0) {
                val from = forward(0, hours, holes, skipLanding = true)
                if (sameDate(from)) return DayCut(at(from), at(span), breakMinutes - restIn(0, from), hours, gapLeft(from, span))
            }
            return offEnd()
        }

        /**
         * The offset after [minutes] of work from [from], stepping over [holes]. Landing just where a hole starts steps over it too
         * when [skipLanding] (a day cut at the start begins with work, not with a break or the gap).
         */
        private fun forward(from: Int, minutes: Int, holes: List<Hole>, skipLanding: Boolean): Int {
            var p = from
            var left = minutes
            for (h in holes.sortedBy { it.from }) {
                if (h.to <= p) continue
                if (h.from <= p) { p = h.to; continue }
                val run = h.from - p
                if (left < run || (left == run && !skipLanding)) return p + left
                left -= run
                p = h.to
            }
            return p + left
        }

        /** The offset [minutes] of work back from [from], stepping over [holes]; landing just where one ends steps over it too. */
        private fun backward(from: Int, minutes: Int, holes: List<Hole>): Int {
            var p = from
            var left = minutes
            for (h in holes.sortedByDescending { it.from }) {
                if (h.from >= p) continue
                if (h.to >= p) { p = h.from; continue }
                val run = p - h.to
                if (left < run) return p - left
                left -= run
                p = h.from
            }
            return p - left
        }
    }
}
