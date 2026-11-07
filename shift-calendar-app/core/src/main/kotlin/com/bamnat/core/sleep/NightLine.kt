package com.bamnat.core.sleep

import java.time.LocalDateTime

/**
 * 1.39 [FLOW-13] 밤 화면's one line under the clock: what a shift worker most wants to check before sleeping, the next work and
 * whether its alarm is set ("내일 07:00 주간 · 알람 06:00 켜짐"). The alarm comes from the calendar, so the line only confirms it.
 */
object NightLine {
    data class Line(val work: String?, val alarm: String, val alarmOn: Boolean) {
        val text: String get() = listOfNotNull(work, alarm).joinToString(" · ")
    }

    /**
     * [work] is the next work after [now] (SleepSchedule.work), [alarm] the next alarm that will ring (null when none),
     * [alarmsOn] the app's 근무 알람 switch. With no work in the week the line says only the alarm.
     * [shifts] false for 가게·현장·투자 (no 근무표 to sleep by, 유형별 빈틈): no "이번 주 근무 없음", and the alarm is just 알람.
     */
    fun of(now: LocalDateTime, work: SleepSchedule.Work?, alarm: LocalDateTime?, alarmsOn: Boolean, shifts: Boolean = true): Line {
        val w = work?.let { "${day(now, it.from)} ${hm(it.from)} ${it.name}" }
        val a = when {
            !alarmsOn -> if (shifts) "근무 알람 꺼짐" else "알람 꺼짐"
            alarm == null -> "울릴 알람 없음"
            // Said beside its work, the alarm needs no day of its own ("내일 07:00 주간 · 알람 06:00 켜짐").
            else -> "알람 ${if (work != null && alarm.toLocalDate() == work.from.toLocalDate()) "" else day(now, alarm) + " "}${hm(alarm)} 켜짐"
        }
        return Line(w ?: if (shifts) "이번 주 근무 없음" else null, a, alarmsOn && alarm != null)
    }

    /** "오늘", "내일", "모레" or "11/15": one day word for the night screen and the 자는 중 notice (1.39 PC a483ca15). */
    fun day(now: LocalDateTime, at: LocalDateTime): String = when (at.toLocalDate()) {
        now.toLocalDate() -> "오늘"
        now.toLocalDate().plusDays(1) -> "내일"
        now.toLocalDate().plusDays(2) -> "모레"
        else -> "${at.monthValue}/${at.dayOfMonth}"
    }

    private fun hm(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
}
