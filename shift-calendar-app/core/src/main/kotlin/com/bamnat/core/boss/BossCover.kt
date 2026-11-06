package com.bamnat.core.boss

import com.bamnat.core.work.StaffShift
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 1.38 [P3-X-01] 사장님 대타 찾기 (사용자 2026-11-06 "사장님 모드도 깊게"): someone cannot come on [date]; who could, and what each
 * costs the shop. The cost is the ledger's own: the same [PayrollLedger.line] with and without the 대근, so 요일별 시급, 연장 가산 past
 * 40시간 and the 4대보험 the boss pays all show up, not a guess of hours × 시급. Equal cost: who has worked less that week first.
 */
object BossCover {
    /**
     * One person who could come. [ok]: nothing stops it ([why] empty); [notes]: what it brings ("주휴수당이 새로 생겨요");
     * [extra]: what the month costs more (인건비, 4대보험 사업주 몫 포함); [weekMinutes]: their week with it.
     */
    data class Candidate(val staff: StaffMember, val ok: Boolean, val why: List<String>, val notes: List<String>, val extra: Long, val weekMinutes: Int)

    /** The hours [s] works on [d]: the day's own change, else the usual week; null on a day they do not work. */
    fun shiftOf(s: StaffMember, d: LocalDate, work: Collection<StaffWork>): StaffShift? {
        if (!s.workingOn(d)) return null
        val w = work.firstOrNull { it.staffId == s.id && it.date == d }
        return when {
            w == null -> s.weekly[d.dayOfWeek]
            w.kind in StaffWork.TIMED -> StaffShift(w.start!!, w.end!!)
            else -> null
        }
    }

    /** The row that puts [s] on [shift]: a 대근 on a day off in their week, the day's hours changed on a day in it (never paid twice). */
    fun row(s: StaffMember, d: LocalDate, shift: StaffShift): StaffWork =
        StaffWork(s.id, d, if (s.weekly[d.dayOfWeek] != null) WorkKind.NORMAL else WorkKind.COVER, shift.start, shift.end)

    /** The hours to fill: what the absent person was to work that day (a time the boss had set, else the usual week). */
    fun gapShift(staff: List<StaffMember>, work: List<StaffWork>, absentId: String, date: LocalDate): StaffShift? {
        val absent = staff.firstOrNull { it.id == absentId } ?: return null
        return work.firstOrNull { it.staffId == absentId && it.date == date }?.takeIf { it.kind in StaffWork.TIMED }?.let { StaffShift(it.start!!, it.end!!) }
            ?: absent.weekly[date.dayOfWeek]
    }

    fun candidates(place: Workplace, staff: List<StaffMember>, work: List<StaffWork>, absentId: String, date: LocalDate): List<Candidate> {
        val shift = gapShift(staff, work, absentId, date) ?: return emptyList()
        val month = YearMonth.from(date)
        val weekStart = date.with(TemporalAdjusters.previousOrSame(place.weekStart))
        val week = (0..6L).map(weekStart::plusDays)
        // 결근·연차인 사람에게는 묻지 않아요: that day is already theirs off for a reason.
        val away = work.filter { it.date == date && (it.kind == WorkKind.ABSENT || it.kind == WorkKind.LEAVE) }.map { it.staffId }.toSet()
        return staff.filter { it.id != absentId && it.id !in away && it.workingOn(date) && shiftOf(it, date, work) == null }.map { s ->
            val trial = work.filterNot { it.staffId == s.id && it.date == date } + row(s, date, shift)
            val before = PayrollLedger.line(place, s, month, work)
            val after = PayrollLedger.line(place, s, month, trial)
            fun weekOf(w: List<StaffWork>) = week.sumOf { d -> shiftOf(s, d, w)?.paidMinutes(place.legalBreaks) ?: 0 }
            val was = weekOf(work)
            val now = weekOf(trial)
            val why = ArrayList<String>()
            val notes = ArrayList<String>()
            // 주 52시간 (근로기준법 제53조) binds a place of 5 or more; not answered yet counts as 5 or more, as the ledger does.
            if (now > 52 * 60 && place.fivePlus != false) why += "그 주 ${now / 60}시간, 52시간을 넘어요"
            if (s.birth != null && java.time.Period.between(s.birth, date).years < 18 && now > 35 * 60) why += "18세 미만은 주 35시간까지예요"
            // 주휴수당은 소정근로시간으로 정해져요(제18조 제3항): a 대근 does not make a 주 15시간 미만 person eligible, so no note for it.
            if (was <= 40 * 60 && now > 40 * 60 && place.fivePlus != false) notes += "주 40시간을 넘어 연장 가산이 붙어요"
            Candidate(s, why.isEmpty(), why, notes, after.employerTotal - before.employerTotal, now)
        }.sortedWith(compareBy({ !it.ok }, { it.extra }, { it.weekMinutes }, { it.staff.name }))
    }

    /** The day's rows once [taker] comes: the 대근 with the absent person's hours (the absent person's own row stays as the boss set it). */
    fun cover(staff: List<StaffMember>, work: List<StaffWork>, absentId: String, date: LocalDate, taker: String): StaffWork? {
        val shift = gapShift(staff, work, absentId, date) ?: return null
        return staff.firstOrNull { it.id == taker }?.let { row(it, date, shift) }
    }
}
