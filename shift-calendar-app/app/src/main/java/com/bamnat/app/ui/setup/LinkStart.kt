package com.bamnat.app.ui.setup

import com.bamnat.app.share.SharedSchedule
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate

/**
 * 1.38 PC 12.1 유형별 빈틈(상): a new phone opened from a 근무표 link (a coworker's, or the one a 팀 리더 made for each member) starts
 * from it. The link already holds the kinds of work, their times and the days, so the first setup is filled from it: a roster that
 * comes every month, its days painted from [from] on (a past month is not the new calendar's), and it opens on the summary, where
 * the names are checked and one press starts. A kind of work with no time in the link stops at the times' screen instead.
 */
object LinkStart {
    fun draft(schedule: SharedSchedule, from: LocalDate): SetupDraft? {
        val types = schedule.types.map { it.asShiftType() }.distinctBy { it.id }
        if (types.none { !it.isOff }) return null
        val ids = types.map { it.id }.toSet()
        val painted = schedule.days.values.asSequence()
            .filter { !it.date.isBefore(from.withDayOfMonth(1)) }
            .mapNotNull { d ->
                val id = d.type?.let { -it - 1 }?.takeIf { it in ids }
                when {
                    id != null -> d.date to id
                    // Leave in the link is not guessed into a kind of work here; the calendar's own leave is the user's.
                    d.leave != null -> null
                    // A day the link says is off, with no kind of its own: a plain day off.
                    d.rest == true || d.type == null -> d.date to null
                    else -> null
                }
            }.toMap()
        val base = SetupDraft(
            style = WorkStyle.SHIFT, rosterMonthly = true, types = types, painted = painted,
            timesConfirmed = true, linkTitle = schedule.title.ifBlank { "받은" }.take(40),
        )
        val timed = types.filter { !it.isOff }.all { it.hasTimes } && base.timeProblems().isEmpty()
        return base.copy(step = if (timed) SetupStep.SUMMARY else SetupStep.TIMES, timesConfirmed = timed)
    }
}
