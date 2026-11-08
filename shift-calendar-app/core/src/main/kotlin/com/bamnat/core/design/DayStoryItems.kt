package com.bamnat.core.design

import com.bamnat.core.alarm.PlannedAlarm
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepPlan
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [FLOW-08] 내 하루 이야기: what goes on a day's story line (흐름·디자인 계획 3-4). The shifts of the day and of the
 * day before (a night shift that runs into this day is one line, "어제 19:00–07:00"), the alarms that ring on it, the
 * sleeps that touch it (and the planned ones, dashed), the money with a time and the memos with a time. [DayStory.arrange]
 * then cuts them to the day and puts them in order.
 */
object DayStoryItems {
    fun of(
        date: LocalDate,
        resolver: ScheduleResolver,
        alarms: List<PlannedAlarm> = emptyList(),
        sleeps: List<SleepEntry> = emptyList(),
        plans: List<SleepPlan.Tip> = emptyList(),
        money: List<MoneyEntry> = emptyList(),
        memos: List<StoryItem> = emptyList(),
        /** 1.39 [FLOW-30] 하루 시작 시각: money dated [date] whose time is in the small hours of the next day ([DayBoundary]). */
        dayStartsAt: java.time.LocalTime = DayBoundary.DEFAULT_START,
    ): List<StoryItem> {
        val out = mutableListOf<StoryItem>()
        // The day before first: its night shift is the first thing of this day.
        for (d in listOf(date.minusDays(1), date)) {
            val r = resolver.resolve(d)
            val s = r.start ?: continue
            val e = r.end ?: continue
            if (!r.isWorkDay) continue
            val from = d.atTime(s)
            val to = if (e.isAfter(s)) d.atTime(e) else d.plusDays(1).atTime(e)
            val name = r.shiftType?.name ?: "근무"
            val detail = listOfNotNull(
                r.scheduledMinutes.takeIf { it > 0 }?.let { "일하는 시간 ${hours(it.toLong())}" },
                r.leave?.label,
            ).joinToString(" · ").ifEmpty { null }
            out += StoryItem(from, to, StoryKind.Shift, name, detail, id = "shift@$d")
        }
        alarms.forEach { a ->
            out += StoryItem(a.triggerAt, null, StoryKind.Alarm, if (a.shiftTypeId != 0L) "출근 알람" else "알람", id = "alarm@${a.triggerAt}")
        }
        // A sleep that is still going on is a moment ("잠들었어요"); a finished one a span with its length.
        sleeps.forEach { s ->
            val end = s.end
            out += if (end == null) StoryItem(s.start, null, StoryKind.Sleep, "잠드는 중", id = "sleep@${s.start}")
            else StoryItem(s.start, end, StoryKind.Sleep, "잠 ${hours(Duration.between(s.start, end).toMinutes())}",
                if (s.auto) "휴대폰이 알아챈 잠" else null, id = "sleep@${s.start}")
        }
        // A planned sleep shows only where no real sleep covers it: a plan the user kept says nothing new.
        plans.filter { it.until != null && it.kind != SleepPlan.Kind.CAFFEINE && it.kind != SleepPlan.Kind.DROWSY }.forEach { p ->
            val until = p.until!!
            val slept = sleeps.any { s -> s.start.isBefore(until) && (s.end ?: LocalDateTime.MAX).isAfter(p.at) }
            if (!slept) out += StoryItem(p.at, until, StoryKind.SleepPlan, "${p.kind.label} 계획", p.text, id = "plan@${p.kind}@${p.at}")
        }
        money.forEach { m ->
            val t = m.time ?: return@forEach
            if (m.kind == MoneyKind.TRANSFER) return@forEach
            val what = m.merchant.ifBlank { m.note.ifBlank { m.category } }.ifBlank { m.kind.label }
            val sign = if (m.kind == MoneyKind.INCOME) "+" else ""
            // 1.39 [FLOW-30] Written at 02:10 in this day's night shift (or before 하루 시작 시각) it is the next morning's 02:10.
            val next = m.date.plusDays(1).atTime(t)
            val at = if (DayBoundary.dayOf(next, resolver::resolve, dayStartsAt) == m.date) next else m.date.atTime(t)
            out += StoryItem(at, null, StoryKind.Money, "$what $sign${"%,d".format(m.amount)}원",
                m.category.takeIf { it.isNotBlank() && it != what }, tone = if (m.kind == MoneyKind.INCOME) "good" else null,
                id = "money:${m.id}@${m.date}")
        }
        out += memos
        return out
    }

    /**
     * True when the line says more than the work group already does: a sleep, money or memo on it. A day of only a
     * shift and its alarm keeps to the cards (계획 3-4: no line for a day with nothing written).
     */
    fun worthShowing(lines: List<StoryLineItem>): Boolean =
        lines.any { it.item.kind == StoryKind.Sleep || it.item.kind == StoryKind.Money || it.item.kind == StoryKind.Memo }

    private fun hours(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}분"
            m == 0L -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }
}
