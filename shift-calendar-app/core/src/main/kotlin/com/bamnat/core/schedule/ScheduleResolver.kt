package com.bamnat.core.schedule

import com.bamnat.core.model.Attendance
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.model.WorkTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DaySource { PATTERN, OVERRIDE, NONE }

/**
 * [EXTRA-01] One piece of extra work placed on the clock: its own times, right after or before the
 * shift for 잔업·조출, or (an old record of hours only on a day without a shift) nowhere.
 */
data class ResolvedExtra(
    val entry: ExtraEntry,
    val kind: ExtraKind,
    /** Null when the record has no times and there is no shift to place it next to. */
    val from: LocalDateTime?,
    val to: LocalDateTime?,
    val breakMinutes: Int,
    val breakStart: LocalTime?,
    /** Paid minutes: the span less the break. */
    val minutes: Int,
    /** The shift type this work covered (대근), if the user said. */
    val covered: ShiftType?,
    /**
     * [PAYX-54] 1.38 PC 12.4.1: minutes of this piece inside the day's own shift (교육 09~13 on an 09~18 일근), taken out of
     * [minutes]: an hour cannot be worked twice, so it adds no hours, no rest problem and no overtime. 0 when apart.
     */
    val insideShiftMinutes: Int = 0,
) {
    /** No clock times, so night minutes are unknown and taken as none. */
    val estimated: Boolean get() = from == null
    val start: LocalTime? get() = from?.toLocalTime()
    val end: LocalTime? get() = to?.toLocalTime()
}

/** The final picture of one calendar day after applying pattern and user changes. */
data class ResolvedDay(
    val date: LocalDate,
    /** Shift type shown on the calendar, including off types; null when nothing is scheduled. */
    val shiftType: ShiftType?,
    val source: DaySource,
    val start: LocalTime?,
    val end: LocalTime?,
    val breakMinutes: Int,
    /** When the break starts, if the shift says; null means unknown (taken out evenly). */
    val breakStart: LocalTime? = null,
    /** Minutes of the scheduled shift (after break). */
    val scheduledMinutes: Int,
    val alarmSkipped: Boolean,
    val entry: DayEntry?,
    /** True when start/end come from this day's entry instead of the shift type. */
    val hasCustomTimes: Boolean = false,
    /** Leave taken on a working day [FEAT-10]; null on days off, where leave means nothing. */
    val leave: Leave? = null,
    /** Shift minutes the leave covers; paid leave pays them, unpaid leave deducts them. */
    val leaveMinutes: Int = 0,
    /** [EXTRA-01] Extra work this day, in the order the user added it. */
    val extras: List<ResolvedExtra> = emptyList(),
    /**
     * [DAYW-08] The shift before leave took its part: what an edit of the day's times starts from. Saving the
     * shrunk [start]/[end]/[breakMinutes] back would let the leave come off a second time.
     */
    val plannedStart: LocalTime? = start,
    val plannedEnd: LocalTime? = end,
    val plannedBreakMinutes: Int = breakMinutes,
    /**
     * 2.0.0 [RVH-44] 검토 TYPE-24·I30b-03 하루 두 구간: where the day stops between its two parts (가게·알바 '브레이크 타임', 일근·교대
     * '비는 시간'), on the clock, after leave took its part; null for a day of one part. Never work, never a break: [scheduledMinutes]
     * and [breakMinutes] leave it out (설계 docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 2-3절).
     */
    val gapFrom: LocalDateTime? = null,
    val gapTo: LocalDateTime? = null,
    /** [RVH-45] An hour's leave in the middle of the day (외출), on the clock: the day is two parts around it (설계 3-3절). */
    val leaveFrom: LocalDateTime? = null,
    val leaveTo: LocalDateTime? = null,
) {
    /** [DAYW-08] True when partial leave moved the clock times, so the day has a planned and a worked span. */
    val leaveShrunk: Boolean get() = leave != null && !leave.isFullDay && (plannedStart != start || plannedEnd != end)

    val isWorkDay: Boolean get() = scheduledMinutes > 0 || (start != null && end != null)
    val isOff: Boolean get() = !isWorkDay
    /**
     * A day with no shift and no leave: work on it is 휴무일 or 휴일 work [EXTRA-02]. [HRLY-01] A 결근
     * is a missed working day, not a rest day, so it is never picked as 주휴일 or paid as 휴일 work.
     */
    val isRestDay: Boolean get() = !isWorkDay && leave == null && entry?.attendance != Attendance.ABSENT
    val hasExtraWork: Boolean get() = extras.isNotEmpty()
    val extraMinutes: Int get() = extras.sumOf { it.minutes }

    /** [EXTRA-01] The first shift type some extra work covered (a 대근 for a 나이트), for two-colour cells. */
    val coveredShift: ShiftType? get() = extras.firstNotNullOfOrNull { it.covered }

    /** [EXTRA-01] Up to [max] marks of the day's kinds of extra work, e.g. "잔대". */
    fun extraMarks(max: Int = 2): String = extras.map { it.kind.mark }.distinct().take(max).joinToString("")

    /** Everything that counts toward weekly / monthly hours. */
    val totalMinutes: Int get() = scheduledMinutes + extraMinutes

    /** The shift on the clock, if it has times. */
    val shiftFrom: LocalDateTime? get() = if (start != null && end != null) date.atTime(start) else null
    val shiftTo: LocalDateTime? get() = shiftFrom?.let { endAfter(it, end!!) }

    /** [RVH-44] Minutes the day stops between its two parts; 0 for a day of one part. */
    val gapMinutes: Int get() = if (gapFrom != null && gapTo != null) Duration.between(gapFrom, gapTo).toMinutes().toInt() else 0

    /**
     * [RVH-44][RVH-45] The parts of the day on the clock, earliest first: one ([shiftFrom]~[shiftTo]) for a day of one part, two around
     * the gap or an hour's leave in the middle, none without times. [start]·[end] stay the first start and the last end, so what reads
     * only them is right; what asks when the work is (근무 중, 야간 분, 알람) reads these.
     */
    val segments: List<ClosedRange<LocalDateTime>>
        get() {
            val from = shiftFrom ?: return emptyList()
            val to = shiftTo ?: return emptyList()
            val holes = listOfNotNull(
                gapFrom?.let { f -> gapTo?.let { f..it } },
                leaveFrom?.let { f -> leaveTo?.let { f..it } },
            ).filter { it.start.isAfter(from) && it.endInclusive.isBefore(to) }.sortedBy { it.start }
            if (holes.isEmpty()) return listOf(from..to)
            val parts = mutableListOf<ClosedRange<LocalDateTime>>()
            var at = from
            for (h in holes) {
                if (h.start.isAfter(at)) parts += at..h.start
                if (h.endInclusive.isAfter(at)) at = h.endInclusive
            }
            if (to.isAfter(at)) parts += at..to
            return parts
        }

    /** [RVH-44] Two parts with a stop between them (a gap or an hour's leave in the middle). */
    val split: Boolean get() = segments.size > 1

    /**
     * [RVH-44] Whether the day's shift is being worked at [t]: inside a part, not in the gap between them (설계 2-3절, 갈래 I 4-2절 6번:
     * 오늘의 흐름·잠금 화면·위젯·워치·알림 미루기 all ask this one question). The start counts, the end does not.
     */
    fun workingAt(t: LocalDateTime): Boolean = segments.any { !t.isBefore(it.start) && t.isBefore(it.endInclusive) }

    /** [RVH-44] Minutes of the parts (the shift on the clock less the gap and an hour's leave in the middle), the break still in. */
    val segmentMinutes: Int get() = segments.sumOf { Duration.between(it.start, it.endInclusive).toMinutes().toInt() }

    companion object {
        /** [end] on the day of [from], or the next day when that is not after it (overnight; equal is 24 hours). */
        fun endAfter(from: LocalDateTime, end: LocalTime): LocalDateTime {
            val sameDay = from.toLocalDate().atTime(end)
            return if (sameDay.isAfter(from)) sameDay else sameDay.plusDays(1)
        }
    }
}

/**
 * Combines shift types, the active pattern and per-day entries into [ResolvedDay]s.
 * Pure and cheap: resolving a date is a couple of map lookups.
 */
class ScheduleResolver(
    shiftTypes: Collection<ShiftType>,
    patterns: List<WorkPattern>,
    entries: Collection<DayEntry>,
    /** [EXTRA-01] The user's kinds of extra work, for where each piece sits on the clock. */
    val kinds: ExtraKinds = ExtraKinds(),
    /** [HRLY-05] Take the legal break off days that have none set ([LegalBreak]). */
    val legalBreaks: Boolean = false,
) {
    constructor(shiftTypes: Collection<ShiftType>, pattern: WorkPattern?, entries: Collection<DayEntry>, kinds: ExtraKinds = ExtraKinds()) :
        this(shiftTypes, listOfNotNull(pattern), entries, kinds)

    /** Pattern periods, earliest first. */
    private val patterns: List<WorkPattern> = patterns.sortedBy { it.startDate }
    private val typesById: Map<Long, ShiftType> = shiftTypes.associateBy { it.id }
    private val entriesByDate: Map<LocalDate, DayEntry> = entries.associateBy { it.date }

    fun entryOn(date: LocalDate): DayEntry? = entriesByDate[date]

    /** The pattern period that fills [date], if any. */
    fun patternOn(date: LocalDate): WorkPattern? = PatternTimeline.on(patterns, date)

    /** Shift type id the pattern gives [date] (null for a day off or no pattern). */
    fun patternShiftIdOn(date: LocalDate): Long? = patternOn(date)?.shiftTypeIdOn(date)

    /**
     * [EASE-22] The kind of day on [date] as the calendar shows it (a day set by hand first, then the order), without
     * working out its hours: what a 근무마다 메모 asks for every day of years ahead. Null for nothing scheduled.
     */
    fun shiftIdOn(date: LocalDate): Long? = when (val o = entriesByDate[date]?.override ?: ShiftOverride.None) {
        ShiftOverride.Off -> null
        is ShiftOverride.Shift -> o.shiftTypeId.takeIf { it in typesById }
        ShiftOverride.None -> patternShiftIdOn(date)?.takeIf { it in typesById }
    }

    fun resolve(date: LocalDate): ResolvedDay {
        val day = resolveDay(date)
        return if (legalBreaks) LegalBreak.apply(day) else day
    }

    private fun resolveDay(date: LocalDate): ResolvedDay {
        val entry = entriesByDate[date]
        val (type, source) = when (val o = entry?.override ?: ShiftOverride.None) {
            ShiftOverride.Off -> null to DaySource.OVERRIDE
            // An override pointing at a deleted type falls back to "nothing scheduled".
            // [RVH-33] 검토 WORK-19 A kind of day whose times changed '오늘부터만' keeps the old ones on the days before.
            is ShiftOverride.Shift -> typesById[o.shiftTypeId]?.on(date) to DaySource.OVERRIDE
            ShiftOverride.None -> {
                val pattern = patternOn(date)
                val id = pattern?.shiftTypeIdOn(date)
                val t = id?.let { typesById[it]?.on(date) }
                t to if (pattern != null) DaySource.PATTERN else DaySource.NONE
            }
        }

        // Custom times only adjust a working day. On a day off they are ignored, so switching a
        // day to 휴무 can never leave hours behind. [HRLY-01] A 결근 is not worked either: the shift
        // stays on the calendar but has no hours and no pay.
        val absent = entry?.attendance == Attendance.ABSENT
        val dayOff = entry?.override == ShiftOverride.Off || type == null || type.isOff || absent
        val customTimes = entry?.hasCustomTimes == true && !dayOff
        val start: LocalTime?
        val end: LocalTime?
        val breakMinutes: Int
        if (customTimes) {
            start = entry!!.customStart
            end = entry.customEnd
            breakMinutes = entry.customBreakMinutes ?: type?.takeIf { it.hasTimes }?.breakMinutes ?: 0
        } else if (type != null && type.hasTimes && !absent) {
            start = type.start
            end = type.end
            breakMinutes = entry?.customBreakMinutes ?: type.breakMinutes
        } else {
            start = null
            end = null
            breakMinutes = 0
        }

        // [RVH-44] The day's gap between two parts: its own (그날만, 0 is '오늘은 이어서 일해요'), else the kind of day's. One that no longer
        // fits the day's times (a day's own times moved it out, or left a part under 30 minutes) is dropped: one part (설계 2-6절).
        val gapAt: LocalTime?
        val gapLength: Int
        run {
            val own = entry?.customGapMinutes
            val s = if (own != null) entry.customGapStart else type?.gapStart
            val m = own ?: (type?.gapMinutes ?: 0)
            val fits = start != null && end != null && WorkTime.gapFits(start, end, s, m)
            gapAt = s.takeIf { fits }
            gapLength = if (fits) m else 0
        }
        val fullScheduled = if (start != null && end != null) WorkTime.workMinutes(start, end, breakMinutes + gapLength) else 0
        // Leave only counts on a day with work to take off. Partial leave comes off the end of
        // the shift and the break shrinks in proportion, so 반차 of 09~18 (1h break) is 09~13:30.
        // 오전 반차 comes off the start instead (13:30~18), unless that would start after midnight:
        // the day's work stays on its own date, so such a shift keeps its leave at the end.
        // [RVH-44] On a day of two parts the leave is cut along the parts, the gap never counted (설계 2-7절): 뒤쪽 반차 of 11~15·17~21
        // leaves 11~15. [RVH-45] An hour's leave takes its own minutes, off the start, the end or the middle (외출), and a break it
        // passes over on the clock comes out with it, never shrunk in proportion (설계 3-3절).
        val leave = entry?.leave?.takeIf { fullScheduled > 0 }
        val cut = if (leave != null && start != null && end != null) {
            DayCut.of(date, start, end, breakMinutes, type?.breakStart, gapAt, gapLength, leave, fullScheduled)
        } else null
        val leaveMinutes = cut?.leaveMinutes ?: 0
        val workStart = if (cut != null) cut.start else start
        val workEnd = if (cut != null) cut.end else end
        val workBreak = cut?.breakMinutes ?: breakMinutes
        val scheduled = fullScheduled - leaveMinutes
        val shiftFrom = if (workStart != null && workEnd != null) date.atTime(workStart) else null
        val shiftTo = shiftFrom?.let { ResolvedDay.endAfter(it, workEnd!!) }
        val base = start?.let { date.atTime(it) }
        val gapOffset = if (cut != null) cut.gapOffset else gapAt?.let { WorkTime.offset(start!!, it) }
        val gapFrom = if (base != null && gapOffset != null && gapLength > 0) base.plusMinutes(gapOffset.toLong()) else null

        return ResolvedDay(
            date = date,
            shiftType = type,
            source = source,
            start = workStart,
            end = workEnd,
            breakMinutes = workBreak,
            // A partial leave shrinks the break, so its clock time is no longer known.
            breakStart = type?.breakStart?.takeIf { workStart != null && workBreak == breakMinutes && breakMinutes > 0 },
            scheduledMinutes = scheduled,
            alarmSkipped = entry?.alarmSkipped == true,
            entry = entry,
            hasCustomTimes = customTimes,
            leave = leave,
            leaveMinutes = leaveMinutes,
            extras = emptyList(),
            plannedStart = start,
            plannedEnd = end,
            plannedBreakMinutes = breakMinutes,
            gapFrom = gapFrom,
            gapTo = gapFrom?.plusMinutes(gapLength.toLong()),
            leaveFrom = cut?.holeFrom?.let { base!!.plusMinutes(it.toLong()) },
            leaveTo = cut?.holeTo?.let { base!!.plusMinutes(it.toLong()) },
        ).let { day ->
            // 2.0.0 [RVH-58] Placed once the day's parts are known: work in the gap between two parts is work (설계 2-3절).
            val list = entry?.extras?.takeIf { it.isNotEmpty() } ?: return@let day
            day.copy(extras = placeExtras(date, list, shiftFrom, shiftTo, day.segments))
        }
    }

    /**
     * [EXTRA-01] Puts each piece of extra work on the clock. Pieces with times keep them; a 대근 with
     * only the covered shift takes that shift's hours; 잔업 follows the shift and 조출 comes before it,
     * one after another when there are several; an old record of hours only follows the shift too,
     * or has no place on a day without one.
     */
    private fun placeExtras(
        date: LocalDate, list: List<ExtraEntry>, shiftFrom: LocalDateTime?, shiftTo: LocalDateTime?,
        parts: List<ClosedRange<LocalDateTime>> = listOfNotNull(shiftFrom?.let { f -> shiftTo?.let { f..it } }),
    ): List<ResolvedExtra> {
        var after = shiftTo
        var before = shiftFrom
        return list.map { e ->
            val kind = kinds.of(e.kindId)
            val covered = e.coveredShiftId?.let { typesById[it]?.on(date) }
            val asCovered = covered?.takeIf { !e.hasTimes && it.hasTimes }
            if (e.hasTimes || asCovered != null) {
                val from = date.atTime(e.start ?: asCovered!!.start!!)
                val to = ResolvedDay.endAfter(from, e.end ?: asCovered!!.end!!)
                val span = Duration.between(from, to).toMinutes().toInt()
                val brk = (if (e.hasTimes) e.breakMinutes else asCovered!!.breakMinutes).coerceIn(0, span)
                val brkStart = if (e.hasTimes) e.breakStart else asCovered!!.breakStart
                val x = ResolvedExtra(e, kind, from, to, brk, brkStart.takeIf { brk > 0 }, span - brk, covered)
                if (parts.size > 1) withinParts(x, parts) else withinShift(x, shiftFrom, shiftTo)
            } else {
                val minutes = e.minutes.coerceIn(0, WorkTime.MINUTES_PER_DAY).toLong()
                var from: LocalDateTime? = null
                var to: LocalDateTime? = null
                val b = before
                val a = after
                if (kind.timing is ExtraTiming.BeforeShift && b != null) {
                    from = b.minusMinutes(minutes); to = b; before = from
                } else if (a != null) {
                    from = a; to = a.plusMinutes(minutes); after = to
                }
                ResolvedExtra(e, kind, from, to, 0, null, minutes.toInt(), covered)
            }
        }
    }

    /**
     * [PAYX-54] 1.38 PC 12.4.1: a piece with its own times that runs into the day's shift keeps only the part outside it.
     * All inside (교육 09~13 in 09~18): no minutes, its times kept to show. One side out (16~20 after 09~18): cut to
     * 18~20. Both sides out: the times kept, the minutes less the shift's part.
     */
    private fun withinShift(x: ResolvedExtra, shiftFrom: LocalDateTime?, shiftTo: LocalDateTime?): ResolvedExtra {
        val from = x.from ?: return x
        val to = x.to ?: return x
        if (shiftFrom == null || shiftTo == null || !from.isBefore(shiftTo) || !to.isAfter(shiftFrom)) return x
        val overlap = Duration.between(maxOf(from, shiftFrom), minOf(to, shiftTo)).toMinutes().toInt()
        if (overlap <= 0) return x
        val span = Duration.between(from, to).toMinutes().toInt()
        return when {
            !from.isBefore(shiftFrom) && !to.isAfter(shiftTo) ->
                x.copy(breakMinutes = 0, breakStart = null, minutes = 0, insideShiftMinutes = x.minutes)
            from.isBefore(shiftFrom) && to.isAfter(shiftTo) -> {
                val paid = (x.minutes - overlap).coerceAtLeast(0)
                x.copy(minutes = paid, insideShiftMinutes = x.minutes - paid)
            }
            else -> {
                val newFrom = if (from.isBefore(shiftFrom)) from else shiftTo
                val newTo = if (from.isBefore(shiftFrom)) shiftFrom else to
                val newSpan = Duration.between(newFrom, newTo).toMinutes().toInt()
                // The break where it is on the clock, else its share of the part left.
                val bs = x.breakStart?.let { b -> newFrom.toLocalDate().atTime(b).let { if (it.isBefore(newFrom)) it.plusDays(1) else it } }
                val brk = if (bs != null) {
                    Duration.between(maxOf(bs, newFrom), minOf(bs.plusMinutes(x.breakMinutes.toLong()), newTo)).toMinutes().toInt().coerceAtLeast(0)
                } else if (span > 0) Math.round(x.breakMinutes * newSpan / span.toDouble()).toInt() else 0
                val paid = (newSpan - brk).coerceAtLeast(0)
                x.copy(from = newFrom, to = newTo, breakMinutes = brk, breakStart = x.breakStart.takeIf { brk > 0 }, minutes = paid,
                    insideShiftMinutes = (x.minutes - paid).coerceAtLeast(0))
            }
        }
    }

    /**
     * 2.0.0 [RVH-58] [withinShift] for a day of two parts (가게 브레이크 타임 11~15·17~21, 외출로 나뉜 날): only the time in the parts is
     * the shift's. A piece in the gap (대타 15~17) is all extra work; one that meets a single part is placed against that part as on a
     * day of one part (20~23 counts 21~23); one across the gap keeps its times as typed and counts the minutes outside the parts, the
     * break by its share.
     */
    private fun withinParts(x: ResolvedExtra, parts: List<ClosedRange<LocalDateTime>>): ResolvedExtra {
        val from = x.from ?: return x
        val to = x.to ?: return x
        val met = parts.filter { from.isBefore(it.endInclusive) && to.isAfter(it.start) }
        if (met.isEmpty()) return x
        if (met.size == 1) return withinShift(x, met[0].start, met[0].endInclusive)
        val inParts = met.sumOf { p -> Duration.between(maxOf(from, p.start), minOf(to, p.endInclusive)).toMinutes().toInt() }
        val span = Duration.between(from, to).toMinutes().toInt()
        val outside = (span - inParts).coerceAtLeast(0)
        if (outside == 0) return x.copy(breakMinutes = 0, breakStart = null, minutes = 0, insideShiftMinutes = x.minutes)
        val brk = if (span > 0) Math.round(x.breakMinutes * outside / span.toDouble()).toInt() else 0
        val paid = (outside - brk).coerceAtLeast(0)
        return x.copy(breakMinutes = brk, breakStart = x.breakStart.takeIf { brk > 0 }, minutes = paid,
            insideShiftMinutes = (x.minutes - paid).coerceAtLeast(0))
    }

    fun resolveRange(from: LocalDate, toInclusive: LocalDate): List<ResolvedDay> {
        if (toInclusive.isBefore(from)) return emptyList()
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(toInclusive) }
            .map(::resolve)
            .toList()
    }
}
