package com.bamnat.core.memo

import com.bamnat.core.text.Josa
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 1.39 [CAL2-20] 약속 겹침 경고 (두 앱 조사 X5, 근무를 모르는 두 앱은 못 하는 것): a time put on a memo is checked against the
 * calendar's own shifts the moment it is written. "야간(22:00~07:00)과 겹쳐요", and after a shift that ends in the early morning
 * the hours its worker is most likely asleep: "야간 퇴근(07:00) 뒤 3시간이에요 · 잘 시간일 수 있어요". A warning only; the time
 * is kept as written. A shift without hours, a day off and a calendar with no shifts (투자) warn of nothing.
 */
object ShiftClash {
    /**
     * The hours after an early-morning end ([SLEEP_ENDS]) that are taken as sleep. 2.0.0 [RVI-42] (검토 MEMO-21) The same 7 hours
     * as the day off after a night ([ShiftMemoTime.offTime]), from one function ([sleptUntil]): a 03~05시 퇴근 put the 쉬는 날 notice at
     * 09:00 and this warned of that very time as 잘 시간.
     */
    const val SLEEP_HOURS = ShiftMemoTime.SLEEP_HOURS
    /** A shift that ends at or before this (and after midnight) is a night: its worker sleeps after it. */
    val SLEEP_ENDS: LocalTime = LocalTime.of(10, 0)

    /** 2.0.0 [RVI-42] When the sleep after a shift of [start]~[end] ends, for a night that ends in the morning; null for any other. */
    fun sleptUntil(start: LocalTime, end: LocalTime): LocalTime? =
        if (!end.isAfter(start) && !end.isAfter(SLEEP_ENDS)) end.plusHours(SLEEP_HOURS) else null

    data class Clash(val text: String, val sleep: Boolean)

    /** What [at] runs into on [days] (a day before it included, for a night that ends that morning), or null. */
    fun of(at: LocalDateTime, days: List<ShiftMemoTime.Day>): Clash? {
        var sleep: Clash? = null
        for (d in days) {
            val start = d.start ?: continue
            val end = d.end ?: continue
            if (!d.work || d.name.isBlank()) continue
            val from = d.date.atTime(start)
            val to = if (end.isAfter(start)) d.date.atTime(end) else d.date.plusDays(1).atTime(end)
            val hours = "${hm(start)}~${hm(end)}"
            if (!at.isBefore(from) && at.isBefore(to)) return Clash("${d.name}($hours)${Josa.gwawa(d.name)} 겹쳐요", sleep = false)
            if (sleep == null && sleptUntil(start, end) != null && !at.isBefore(to) && at.isBefore(to.plusHours(SLEEP_HOURS))) {
                val after = Duration.between(to, at).toMinutes()
                val gap = if (after < 60) "${after}분" else "${after / 60}시간" + (if (after % 60 >= 30) " 반" else "")
                sleep = Clash("${d.name} 퇴근(${hm(end)}) 뒤 ${gap}이에요 · 잘 시간일 수 있어요", sleep = true)
            }
        }
        return sleep
    }

    /**
     * 2.0.0 [RVI-79] (검토 U07a-10·T07a-05) The first time from [at] that runs into neither a 근무 nor the sleep after a night, rounded
     * up to the half hour ([at] itself when it is free): where the schedule dialog starts. It started at the next round hour or 09:00,
     * which on a 교대 calendar was often in the middle of a 야간 or the sleep after it; now it is after the shift (퇴근 뒤) or when
     * that sleep ends.
     */
    fun free(at: LocalDateTime, days: List<ShiftMemoTime.Day>): LocalDateTime {
        var t = at
        repeat(FREE_TRIES) {
            val until = busyUntil(t, days) ?: return t
            t = until.plusMinutes(((30 - until.minute % 30) % 30).toLong()).withSecond(0).withNano(0)
        }
        return t
    }

    private const val FREE_TRIES = 4

    /** When what [at] runs into on [days] ends: the shift's end, or the end of the sleep after a night; null when [at] is free. */
    private fun busyUntil(at: LocalDateTime, days: List<ShiftMemoTime.Day>): LocalDateTime? {
        var sleep: LocalDateTime? = null
        for (d in days) {
            val start = d.start ?: continue
            val end = d.end ?: continue
            if (!d.work || d.name.isBlank()) continue
            val from = d.date.atTime(start)
            val to = if (end.isAfter(start)) d.date.atTime(end) else d.date.plusDays(1).atTime(end)
            if (!at.isBefore(from) && at.isBefore(to)) return to
            if (sleptUntil(start, end) != null && !at.isBefore(to) && at.isBefore(to.plusHours(SLEEP_HOURS))) {
                val woke = to.plusHours(SLEEP_HOURS)
                if (sleep == null || woke.isAfter(sleep)) sleep = woke
            }
        }
        return sleep
    }

    private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}
