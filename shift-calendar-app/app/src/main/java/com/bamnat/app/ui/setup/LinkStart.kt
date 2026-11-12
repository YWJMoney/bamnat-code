package com.bamnat.app.ui.setup

import com.bamnat.app.share.ReceivedCalendarStore
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.ShareAudience
import com.bamnat.app.share.SharedSchedule
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import org.json.JSONObject

/**
 * 1.38 PC 12.1 유형별 빈틈(상): a new phone opened from a 근무표 link (a coworker's, or the one a 팀 리더 made for each member) starts
 * from it. The link already holds the kinds of work, their times and the days, so the first setup is filled from it: a roster that
 * comes every month, its days painted from [from] on (a past month is not the new calendar's), and it opens on the summary, where
 * the names are checked and one press starts. A kind of work with no time in the link stops at the times' screen instead.
 */
object LinkStart {
    /**
     * [RVG-15] 검토 TYPE-04 (사용자 결정 2026-11-12 '두 단추로 묻기'): whose 근무표 a link is, as the first screen asks it before
     * anything is filled: '내 근무표예요' starts the setup from it ([draft]), '가족·동료 것이에요' keeps it beside as a 받은 달력
     * ([keepTheirs]) and the user's own work is asked from the start.
     */
    enum class Owner { MINE, THEIRS }

    /**
     * Which of the two buttons leads (both are always there): a 팀 리더's member link holds that one member's days and is made for them
     * (`team`), so 내 근무표예요; a link its sender made for 가족·연인 or a 친구 is the sender's, so 가족·동료 것이에요. A coworker's link,
     * or one from before 1.33 (no `audience`), could be either: null, both alike.
     */
    fun lean(raw: JSONObject): Owner? = when {
        raw.optBoolean("team") -> Owner.MINE
        ShareAudience.of(raw.optString("audience")) in setOf(ShareAudience.FAMILY, ShareAudience.FRIEND) -> Owner.THEIRS
        else -> null
    }

    /**
     * '내 근무표예요': the link the user's own days come from, followed from now on ([com.bamnat.app.share.RosterFollower] AUTO).
     * [RVG-17] 검토 UIX-59: kept from [share], the copy the setup was filled from, at once: before, the waiting link was let go and
     * the link read again, and a failed second read (no signal) left the 리더's later changes out without a word. A change made since
     * comes in on the next look, compared with this copy.
     */
    fun keepMine(received: ReceivedCalendarStore, id: String, share: ReceivedShare) {
        received.put(id, share, mine = true, basis = share.schedule)
        received.open(null)
    }

    /**
     * '가족·동료 것이에요': kept as a 받은 달력 beside the user's own (only seen; its changes never go into their days), and the waiting
     * link let go, so the calendar does not offer it again after the setup.
     */
    fun keepTheirs(received: ReceivedCalendarStore, id: String, share: ReceivedShare) {
        received.put(id, share)
        received.open(null)
    }

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
