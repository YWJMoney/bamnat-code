package com.bamnat.core.pay

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave as DayLeave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.37 [LEDH-04] '이날 일하면?': one day changed and the month's pay worked out again, nothing saved.
 * A day off: 대근 of a shift type (a [ExtraKind.COVER_ID] extra, as the day sheet would write it).
 * A working day: 연차 (paid, so a 월급 stays the same and that is said), or 쉬는 날 instead.
 * The 세후 change comes from [TakeHome] on the month before and after, when a tax way was picked.
 */
object DayWhatIf {
    sealed interface Change {
        val label: String
        data class Cover(val type: ShiftType) : Change { override val label get() = "${type.name} 대근하면" }
        data object Leave : Change { override val label get() = "연차 쓰면" }
        data object Off : Change { override val label get() = "쉬는 날로 바꾸면" }
    }

    data class Answer(val change: Change, val gross: Long, val net: Long?) {
        /** "+132,000원 (세후 약 +118,000원)" is said by the screen; this is whether anything moves. */
        val same: Boolean get() = gross == 0L
    }

    /** What can be asked of [day]: 대근 of up to three working types on a day off; 연차 and 쉬는 날 on a working day. */
    fun changes(day: ResolvedDay, types: List<ShiftType>): List<Change> =
        if (day.isOff && day.leave == null) types.filter { !it.isOff && it.start != null && it.end != null }.take(3).map { Change.Cover(it) }
        else if (day.isWorkDay && day.leave == null) listOf(Change.Leave, Change.Off)
        else emptyList()

    /** The day's entry with [change] made on it. */
    fun entry(old: DayEntry?, date: LocalDate, change: Change): DayEntry {
        val e = old ?: DayEntry(date)
        return when (change) {
            is Change.Cover -> e.copy(extras = e.extras + ExtraEntry(ExtraKind.COVER_ID, change.type.start, change.type.end,
                breakMinutes = change.type.breakMinutes, coveredShiftId = change.type.id))
            Change.Leave -> e.copy(leave = DayLeave())
            Change.Off -> e.copy(override = ShiftOverride.Off)
        }
    }

    /**
     * The month of [date] with [after] (the schedule with the change) against [before]. Net is null without a tax way
     * ([PayProfile.taxChosen]).
     */
    fun answer(
        profile: PayProfile, date: LocalDate, change: Change,
        before: (LocalDate) -> ResolvedDay, after: (LocalDate) -> ResolvedDay,
        calculator: PayCalculator = PayCalculator(),
    ): Answer {
        val month = profile.period.monthOf(date)
        val a = calculator.calculate(profile, month, before, checks = false).total
        val b = calculator.calculate(profile, month, after, checks = false).total
        val net = if (!profile.taxChosen) null else {
            fun take(g: Long) = TakeHome.estimate(g, profile.taxMode, profile.taxFamily, date, profile.taxChildren).net
            take(b) - take(a)
        }
        return Answer(change, b - a, net)
    }

    /** How much of what is left of a goal the change would fill: "‘여행’ 봉투 남은 돈의 12%". */
    fun envelopeShare(won: Long, left: Long): Int? = if (won <= 0 || left <= 0) null else ((won * 100 + left - 1) / left).toInt().coerceAtMost(100)
}
