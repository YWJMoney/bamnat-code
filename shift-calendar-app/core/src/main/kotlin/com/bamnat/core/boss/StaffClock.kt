package com.bamnat.core.boss

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 1.38 [P3-B-11] One press of 출근 ([inside]) or 퇴근 on a staff member's 출퇴근 링크, at the server's time. */
data class StaffPunch(val staffId: String, val at: Instant, val inside: Boolean)

/**
 * 1.38 [P3-B-11] 출퇴근 기록 (경쟁 앱 조사 제안, 사용자 2026-11-06 "넷 다 1.38에"): the presses become days, and the days that went
 * differently from the usual week become [StaffWork] rows the boss can put in with one tap. Nothing is put in by itself: anyone with
 * the link can press it, so a 기록 is what the boss checks, like a paper sheet on the wall.
 */
object StaffClock {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    /** A 퇴근 this long after a 출근 still closes it (a night shift with 연장). */
    private val LONGEST: Duration = Duration.ofHours(18)

    /** A day's work by the presses: the first 출근 and the last 퇴근 that closed it; either may be missing. */
    data class Day(val date: LocalDate, val start: LocalTime?, val end: LocalTime?)

    fun days(punches: List<StaffPunch>, zone: ZoneId = ZONE): List<Day> {
        val out = LinkedHashMap<LocalDate, Day>()
        var open: Pair<LocalDate, Instant>? = null
        var closed = false
        for (p in punches.sortedBy { it.at }) {
            val t = p.at.atZone(zone)
            if (p.inside) {
                // A second 출근 while open (pressed again) keeps the first; one after a 퇴근 starts the next day's work.
                if (open == null || closed || Duration.between(open.second, p.at) > LONGEST) {
                    closed = false
                    open = t.toLocalDate() to p.at
                    out.merge(t.toLocalDate(), Day(t.toLocalDate(), t.toLocalTime().withNano(0), null)) { a, b -> if (a.start == null) a.copy(start = b.start) else a }
                }
            } else {
                val from = open?.takeIf { Duration.between(it.second, p.at) <= LONGEST }
                val date = from?.first ?: t.toLocalDate()
                out[date] = (out[date] ?: Day(date, null, null)).copy(end = t.toLocalTime().withNano(0))
                // A 퇴근 pressed twice moves the end to the later one; a new 출근 starts a new day.
                open = from
                closed = true
            }
        }
        return out.values.sortedBy { it.date }
    }

    /**
     * The rows to put in for [days] against [s]'s usual week: 지각 (started late), 조퇴 (left early), 시간 바꿈 (other hours or a day
     * with no usual work). Days already written by the boss, days whose start or end is missing, and days that went as usual are left
     * out. Late or early only by more than [graceMinutes] (a minute or two at the door is not 지각).
     */
    fun suggest(s: StaffMember, days: List<Day>, written: Set<LocalDate>, graceMinutes: Int = 0): List<StaffWork> = days.mapNotNull { d ->
        val start = d.start ?: return@mapNotNull null
        val end = d.end ?: return@mapNotNull null
        if (d.date in written || !s.workingOn(d.date) || start == end) return@mapNotNull null
        val usual = s.weekly[d.date.dayOfWeek] ?: return@mapNotNull StaffWork(s.id, d.date, WorkKind.NORMAL, start, end)
        // a later than b by more than the grace, on a clock face (23:50 → 00:10 is 20 minutes later, not 23 hours earlier).
        fun past(a: LocalTime, b: LocalTime): Boolean {
            val m = Math.floorMod(a.toSecondOfDay() / 60 - b.toSecondOfDay() / 60, 1440).let { if (it > 720) it - 1440 else it }
            return m > graceMinutes
        }
        val late = past(start, usual.start)
        val early = past(usual.end, end)
        val sameStart = !past(start, usual.start) && !past(usual.start, start)
        val sameEnd = !past(end, usual.end) && !past(usual.end, end)
        when {
            sameStart && sameEnd -> null
            late && !early -> StaffWork(s.id, d.date, WorkKind.LATE, start, end)
            early && !late -> StaffWork(s.id, d.date, WorkKind.EARLY, start, end)
            else -> StaffWork(s.id, d.date, WorkKind.NORMAL, start, end)
        }
    }
}
