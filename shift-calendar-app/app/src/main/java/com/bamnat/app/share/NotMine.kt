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

/**
 * 2.0.0 [RVG-31] 검토 PC-95 (조건 10): '내 달력에 넣기' on a received calendar and its 되돌리기. Putting the days in also made the
 * link followed (its copy the one my days were matched to); 되돌리기 took the days back but left that, so the same days counted
 * as '직접 바꾼 날' and the card said '0일 내 달력에 넣기'. The 되돌리기 now puts back only what this changed: the days, and the
 * link's own answers as they were.
 */
object PutIn {
    /** What [apply] put in, and the way back. */
    class Undo(val days: Int, val back: suspend () -> Unit)

    suspend fun apply(c: AppContainer, id: String, todo: Map<java.time.LocalDate, ShiftOverride>): Undo {
        val before = c.received.get(id)
        val was = todo.mapValues { (d, o) -> c.scheduleRepository.updateDay(d) { it.copy(override = o) } }
        // 1.39 [TEAM-22] Put in once: when the 리더 changes this link later, the change is offered (or as set below).
        c.received.markAdopted(id)
        RosterFollower.sync(c.appContext, c)
        return Undo(was.size) {
            was.forEach { (d, e) -> c.scheduleRepository.restoreDay(d, e) }
            before?.let { c.received.putBack(it) }
            RosterFollower.sync(c.appContext, c)
        }
    }
}
