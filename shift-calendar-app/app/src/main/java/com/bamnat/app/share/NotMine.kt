package com.bamnat.app.share

import com.bamnat.app.AppContainer
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.schedule.LinkDays
import com.bamnat.core.schedule.RosterAdopt
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first

/**
 * [RVG-20] 2.0.0 검토 HQ-09 (조건 10): '내 근무표예요' was picked on the first screen for a 가족·동료's 근무표 link, and it is found after
 * the setup. '넣지 않기' only stopped later changes; the days already in stayed. '내 근무표가 아니에요' on the received calendar takes
 * out only the days that link put in ([plan], said before anything changes), keeps the link beside as theirs, and gives one 되돌리기
 * for both ([apply]).
 */
object NotMine {
    /** What would come out of the user's calendar now, and what stays because the user changed it by hand; null when the link is gone. */
    suspend fun plan(c: AppContainer, id: String): LinkDays.Split? {
        val cal = c.received.get(id) ?: return null
        val now = cal.schedule ?: return null
        val basis = cal.basis?.let { runCatching { SharedSchedule.parse(it) }.getOrNull() }
        val snap = c.scheduleRepository.snapshot.first()
        // The link's kinds as they are now, and as they were when the days were matched (an id the sender has since taken out).
        val pairs = basis?.let { RosterAdopt.match(RosterFollower.theirTypes(it), snap.shiftTypes) }.orEmpty() +
            RosterAdopt.match(RosterFollower.theirTypes(now), snap.shiftTypes)
        // The setup filled the days from the first of the month the link was kept in (LinkStart.draft); nothing before is the link's.
        val from = cal.addedAt.atZone(Today.zone()).toLocalDate().withDayOfMonth(1)
        return LinkDays.split(RosterFollower.theirDays(now), basis?.let(RosterFollower::theirDays), pairs, snap.shiftTypes,
            snap.resolver::resolve, from)
    }

    /**
     * Takes [split]'s days out (each back to nothing of its own), makes the link theirs and stops following it. The returned
     * 되돌리기 puts back only what this changed: those days as they were, and the link as the user's own.
     */
    suspend fun apply(c: AppContainer, id: String, split: LinkDays.Split): suspend () -> Unit {
        val before = c.received.get(id) ?: return {}
        val was = c.scheduleRepository.updateDays(split.theirs) { e -> e.copy(override = ShiftOverride.None) }
        c.received.notMine(id)
        RosterFollower.close(c, id)
        RosterFollower.sync(c.appContext, c)
        c.telemetry.event("setup_link", "not_mine")
        return {
            c.scheduleRepository.updateDays(was.keys.toList()) { e -> e.copy(override = was[e.date]?.override ?: ShiftOverride.None) }
            c.received.putBack(before)
            RosterFollower.sync(c.appContext, c)
        }
    }
}
