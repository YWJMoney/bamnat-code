package com.bamnat.core.setup

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 1.39 [FLOW-03] 첫 설정 끝 '한 달이 채워지는 순간': the one line said over the calendar as it fills, from the days just saved.
 * "이번 달 야간 8번 · 쉬는 날 10일 · 첫 알람 내일 06:00". Counted from today to the month's end; with under a week left the next
 * month is counted too ("다음 달까지"). A night shift is named first (교대 근무자가 가장 먼저 세는 것), else the shift worked most;
 * a calendar with one kind of work says "일하는 날 n일". Nothing when no day works (an empty order, a roster left for later).
 */
object FillMoment {
    data class Line(val head: String, val rest: String) {
        val text: String get() = if (rest.isEmpty()) head else "$head · $rest"
    }

    fun of(days: List<ResolvedDay>, today: LocalDate, firstAlarm: LocalDateTime? = null): Line? {
        val month = YearMonth.from(today)
        val nextToo = month.atEndOfMonth().toEpochDay() - today.toEpochDay() < 6
        val end = if (nextToo) month.plusMonths(1).atEndOfMonth() else month.atEndOfMonth()
        val span = days.filter { !it.date.isBefore(today) && !it.date.isAfter(end) }
        val work = span.filter { it.isWorkDay && it.shiftType?.isOff != true }
        if (work.isEmpty()) return null
        val when_ = if (nextToo) "다음 달까지" else "이번 달"
        val byType = work.groupBy { it.shiftType?.id }.filterKeys { it != null }
        val night = byType.values.filter { group -> group.first().let { d -> d.start != null && d.end != null && !d.end.isAfter(d.start) } }
            .maxByOrNull { it.size }
        val lead = night ?: byType.values.maxByOrNull { it.size }
        val head = if (lead != null && byType.size > 1) "$when_ ${lead.first().shiftType!!.name} ${lead.size}번"
        else "$when_ 일하는 날 ${work.size}일"
        val rest = buildList {
            val off = span.count { it.isRestDay }
            if (off > 0) add("쉬는 날 ${off}일")
            firstAlarm?.let { add("첫 알람 ${dayWord(it.toLocalDate(), today)} ${"%02d:%02d".format(it.hour, it.minute)}") }
        }.joinToString(" · ")
        return Line(head, rest)
    }

    private fun dayWord(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        today.plusDays(2) -> "모레"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}
