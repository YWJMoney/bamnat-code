package com.bamnat.core.schedule

import com.bamnat.core.model.ShiftType
import java.time.LocalDate

/**
 * [RVH-33] 검토 WORK-19 검토 뿌리 고침 6 '굳은 달'과 같은 물음: a change that would reach days already past asks "오늘부터만 / 지난 날도",
 * with how many past days the second one changes, so last month's hours, stats and pay never move without a word.
 *
 * How other parts use it (갈래 J 굳은 달, K): ask with [Way] and [line] in P's common small window; [FROM_TODAY][Way.FROM_TODAY] is
 * the one picked already. Count the past days the change reaches yourself (a kind of day: [pastDays]) and say them with [line].
 */
object SinceChoice {
    enum class Way(val label: String) {
        /** The change counts from today; the days before stay as they were. The usual answer. */
        FROM_TODAY("오늘부터만"),

        /** The past days take the change too. */
        PAST_TOO("지난 날도"),
    }

    /** "지난 날 23일의 시간도 바뀌어요": what [Way.PAST_TOO] changes, said beside it ([what] is 시간, 금액…). */
    fun line(pastDays: Int, what: String = "시간"): String =
        if (pastDays <= 0) "지난 날에는 바뀌는 것이 없어요" else "지난 날 ${pastDays}일의 ${what}도 바뀌어요"

    /**
     * The past days, from [since] to the day before [today], worked as kind [typeId] on its own times (not a day with times of its
     * own, which a change of the kind does not reach): the days [Way.PAST_TOO] would change. [since] is where the calendar starts
     * (its first order or day written), at most [MAX_DAYS] back.
     */
    fun pastDays(resolver: ScheduleResolver, typeId: Long, today: LocalDate, since: LocalDate): Int {
        var day = maxOf(since, today.minusDays(MAX_DAYS))
        var n = 0
        while (day.isBefore(today)) {
            if (resolver.shiftIdOn(day) == typeId && resolver.entryOn(day)?.hasCustomTimes != true) n++
            day = day.plusDays(1)
        }
        return n
    }

    /** Whether saving [changed] over [before] needs the question: its hours differ and some past day works it. */
    fun asks(before: ShiftType?, changed: ShiftType, pastDays: Int): Boolean =
        before != null && before.id != 0L && !before.isOff && !before.sameTimes(changed) && pastDays > 0

    /** [changed] saved the way picked, from [today]. */
    fun apply(before: ShiftType, changed: ShiftType, way: Way, today: LocalDate): ShiftType = when (way) {
        Way.FROM_TODAY -> before.withTimesFrom(changed, today)
        Way.PAST_TOO -> before.withTimesEverywhere(changed)
    }

    /** Two years: the order fills that far back on the first save. */
    const val MAX_DAYS = 2L * 366
}
