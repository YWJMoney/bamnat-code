package com.bamnat.app.ui.stats

import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.schedule.PatternTimeline
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVH-94] 검토 PC-212·PC-12 (오늘 처음 깐 사람에게 적은 적 없는 지난달 숫자): the month from which a calendar's figures are the
 * user's own. The rule 돈 already keeps for its pay estimates ([J133-37] MoneyScreen): the month this app was first put on the
 * phone, or the first month written by hand if earlier (a backup brought over keeps its months). The calendar still shows the
 * order before it; 근무·급여 and 건강 지표 count only what was in use, so a month before it reads as empty, not as worked.
 */
internal object FactsSince {
    fun month(installed: LocalDate?, snapshot: ScheduleSnapshot): YearMonth? =
        listOfNotNull(
            installed?.let(YearMonth::from),
            snapshot.entries.filter { !it.isEmpty }.minOfOrNull { it.date }?.let(YearMonth::from),
        ).minOrNull()

    /** [snapshot] with its orders from [since] on; the days written by hand stay, whenever they are. */
    fun clip(snapshot: ScheduleSnapshot, since: YearMonth?): ScheduleSnapshot =
        if (since == null || snapshot.patterns.none { it.startDate.isBefore(since.atDay(1)) }) snapshot
        else snapshot.copy(patterns = PatternTimeline.since(snapshot.patterns, since.atDay(1)))

    fun of(installed: java.time.Instant?, snapshot: ScheduleSnapshot): ScheduleSnapshot =
        clip(snapshot, month(installed?.atZone(com.bamnat.core.time.Today.zone())?.toLocalDate(), snapshot))
}
