package com.bamnat.core.sleep

import java.time.Duration
import java.time.LocalDateTime

/**
 * 2.0.0 [RVI-18] (검토 T03-08) 가게·현장·투자 have no 근무표 to plan sleep by, so no 오늘의 흐름 line on the today widget or the watch:
 * they still get one line about sleep, from the next alarm and the sleep target ("07:00 알람 · 00:00에 자면 7시간"), or
 * "자는 중 · 23:40부터" while a sleep is open.
 */
object RestLine {
    /** An alarm further off than this is not tonight's to sleep by. */
    const val ALARM_WITHIN_HOURS = 16L

    /** [changesAt]: when the line says something else (the bedtime passing), for the widget's next redraw. */
    data class Line(val text: String, val sleeping: Boolean, val changesAt: LocalDateTime?)

    fun of(now: LocalDateTime, log: List<SleepEntry>, alarm: LocalDateTime?, alarmsOn: Boolean, targetMinutes: Int): Line? {
        log.firstOrNull { it.end == null && !it.start.isAfter(now) }?.let { return Line("자는 중 · ${hm(it.start)}부터", true, null) }
        if (!alarmsOn || alarm == null || !alarm.isAfter(now) || Duration.between(now, alarm).toMinutes() >= ALARM_WITHIN_HOURS * 60) return null
        val bed = alarm.minusMinutes(targetMinutes.toLong())
        return if (now.isBefore(bed)) Line("${hm(alarm)} 알람 · ${hm(bed)}에 자면 ${SleepInsights.hours(targetMinutes.toLong())}", false, bed)
        else Line("${hm(alarm)} 알람 · 지금 자면 ${SleepInsights.hours(Duration.between(now, alarm).toMinutes())}", false, alarm)
    }

    private fun hm(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
}
