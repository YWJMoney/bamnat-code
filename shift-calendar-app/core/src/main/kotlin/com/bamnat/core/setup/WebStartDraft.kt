package com.bamnat.core.setup

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.plaza.CycleShift
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.schedule.BundleShift
import com.bamnat.core.schedule.PatternPreset
import com.bamnat.core.schedule.PatternPresets
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.schedule.ShiftRole
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 2.0.0 [RVH-68] 검토 I50c-01 (RVR-31, 기존 계획 SPREAD-30): a 교대 first setup filled from what the person picked on bamnat.app's
 * 교대 달력 ([WebHandoff]): the kinds of work its letters name, the order, and the day its first letter is worked as the order's step
 * 0 (the answer to "오늘 무슨 근무예요?"). The letters carry no times, so the kinds come with the usual ones (the bundle's own when
 * the letters are a bundle's: 2교대, 3교대, 24시간 당번) and the setup opens on the times' screen to check them; the order and today
 * are answered already. A month's roster written once (간호사 듀티, `o-1`) is painted day by day from its first day, not repeated.
 */
object WebStartDraft {
    /** [d] (a 교대 setup) filled from [h], on the times' screen; null when [h] has no order with work in it or [d] is not 교대. */
    fun draft(h: WebHandoff, d: SetupDraft): SetupDraft? {
        val codes = h.codes ?: return null
        if (d.style != WorkStyle.SHIFT || codes.none { it in PublicCycle.WORK }) return null
        val work = PublicCycle.WORK.filter { it in codes }.toSet()
        // A month's roster written once is a hospital's (데이·이브닝·나이트); a repeating 주·오·야 order is 3교대.
        val bundle = ShiftBundles.all.filter { it.workCodes == work }.let { m -> m.firstOrNull { h.once && it.key == ShiftBundles.HOSPITAL } ?: m.firstOrNull() }
        // 주야비휴 is 2교대's day and night with a 비번 after the night: the 비번 joins the bundle's kinds, not turned into 휴무.
        val shifts = bundle?.shifts?.let { s -> if ('B' in codes && s.none { it.role == ShiftRole.AFTER_DUTY }) s.withAfter() else s }
            ?: own(work, 'B' in codes)
        val monthly = h.once && h.first != null
        val base = d.copy(rosterMonthly = h.once).withShifts(bundle?.key ?: SetupDraft.CUSTOM, shifts)
        val steps = PatternPresets.toSteps(PatternPreset(codes, "", codes), base.roles)
        val filled = when {
            monthly -> base.copy(painted = steps.mapIndexed { i, id -> h.first!!.plusDays(i.toLong()) to id }.toMap())
            // A roster written once with no first day: its kinds only; the days are painted by hand.
            h.once -> base
            else -> base.copy(rotation = steps, anchor = h.first, todayConfirmed = h.first != null)
        }
        return filled.copy(step = SetupStep.TIMES)
    }

    /**
     * SPREAD-31 '같은 조 동료에게 보내기': the site's 교대 달력 for [r], the letters of its kinds (a kind gone or a plain 쉬는 날 is 휴무)
     * from the latest start of the order on or before [today], so the link reads "11월 10일부터" and not the day it was first set. Null
     * when the order has no work or is longer than the site takes. Nothing about the person: the order and a day.
     */
    fun shiftPage(r: WorkPattern.Rotation, types: Map<Long, ShiftType>, today: LocalDate): String? {
        if (r.steps.isEmpty()) return null
        val codes = r.steps.joinToString("") { id -> (id?.let(types::get)?.let { ShiftRole.guess(it).code } ?: ShiftRole.OFF.code).toString() }
        if (codes.none { it in PublicCycle.WORK } || WebHandoff.read(codes)?.codes != codes) return null
        val n = r.steps.size.toLong()
        val first = r.anchorDate.plusDays(Math.floorDiv(ChronoUnit.DAYS.between(r.anchorDate, today), n) * n)
        return WebHandoff.shiftPage(codes, first)
    }

    private val offs: List<BundleShift> get() = ShiftBundles.byKey(ShiftBundles.DUTY)!!.shifts.filter { it.role.isOff }

    /** [this] with the 24시간 당번 bundle's 비번 put before its 휴무. */
    private fun List<BundleShift>.withAfter(): List<BundleShift> {
        val i = indexOfFirst { it.role == ShiftRole.OFF }.let { if (it < 0) size else it }
        return take(i) + offs.filter { it.role == ShiftRole.AFTER_DUTY } + drop(i)
    }

    /** The kinds the letters name, at their usual times (주간 07~19 · 야간 19~07, or 06~14·14~22·22~06 with an 오후), then 비번 and 휴무. */
    private fun own(work: Set<Char>, after: Boolean): List<BundleShift> {
        val three = 'E' in work
        return work.map { c -> BundleShift(ShiftRole.of(c)!!, CycleShift.usual(c, three).toShiftType()) } +
            offs.filter { it.role == ShiftRole.OFF || after && it.role == ShiftRole.AFTER_DUTY }.sortedBy { it.role != ShiftRole.AFTER_DUTY }
    }
}
