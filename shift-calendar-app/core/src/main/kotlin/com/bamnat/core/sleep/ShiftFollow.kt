package com.bamnat.core.sleep

import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.schedule.ScheduleResolver
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2.0.0 [RVIB-10] (검토 I03c-04) 근무를 바꾸면 한 줄로 끝. After today's or tomorrow's work was changed, whether the alarm, the sleep plan and
 * the watch followed was looked up one by one. The change's own line now says it: "내일 야간으로 바꿨어요 · 알람 17:30 · 잘 시간
 * 08:00–15:00 · 워치에도 보냈어요". Only for a working day in the next two days; further off, the plan is still to come.
 */
object ShiftFollow {
    /** " · 알람 17:30 · 잘 시간 08:00–15:00 · 워치에도 보냈어요", or "" when there is nothing to add. [alarmSaid]: the line already says the alarm. */
    fun line(
        date: LocalDate,
        now: LocalDateTime,
        resolver: ScheduleResolver,
        alarmsOn: Boolean,
        alarmSaid: Boolean,
        watch: Boolean,
        /** 오늘의 흐름 on: the sleep plan is the user's to see. */
        sleep: Boolean = true,
        targetMinutes: Int = SleepPlan.DEFAULT_TARGET_MINUTES,
        commuteMinutes: Int = SleepPlan.COMMUTE_MINUTES.toInt(),
    ): String {
        val today = now.toLocalDate()
        if (date.isBefore(today) || date.isAfter(today.plusDays(1))) return ""
        val work = SleepSchedule.work(resolver.resolve(date))
        if (work.isEmpty()) return ""
        val alarm = if (alarmSaid || !alarmsOn) null
            else AlarmPlanner.alarmsOn(resolver, date).map { it.triggerAt }.filter { it.isAfter(now) }.minOrNull()
        val plan = SleepSchedule.of(resolver, date, date, alarmsOn, targetMinutes, commuteMinutes)
        val bed = if (!sleep) null else plan.sleeps.firstOrNull { it.before?.date == date && it.to.isAfter(now) }
            ?: plan.sleeps.firstOrNull { it.after?.date == date && it.to.isAfter(now) }
        return listOfNotNull(
            alarm?.let { "알람 " + hm(it) },
            bed?.let { "잘 시간 ${hm(it.from)}–${hm(it.to)}" },
            "워치에도 보냈어요".takeIf { watch },
        ).joinToString("") { " · $it" }
    }

    private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
}
