package com.bamnat.core.model

import java.time.LocalDate
import java.time.LocalTime

/** How a single day differs from the pattern. */
sealed interface ShiftOverride {
    /** Follow the pattern. */
    data object None : ShiftOverride
    /** Force a day off regardless of the pattern. */
    data object Off : ShiftOverride
    /** Work this shift type instead of the pattern's. */
    data class Shift(val shiftTypeId: Long) : ShiftOverride
}

/**
 * Everything the user changed on one date. A date without an entry follows the pattern.
 */
data class DayEntry(
    val date: LocalDate,
    val override: ShiftOverride = ShiftOverride.None,
    /** Optional start/end/break for this day only, e.g. a part-timer who left early. */
    val customStart: LocalTime? = null,
    val customEnd: LocalTime? = null,
    val customBreakMinutes: Int? = null,
    /** Skip the shift alarm on this day only. */
    val alarmSkipped: Boolean = false,
    /**
     * Alarms set by hand for this date. They replace the shift's alarms on this day;
     * null means the day follows its shift type.
     */
    val customAlarms: List<DayAlarm>? = null,
    /** 연차·반차·반반차 taken on this day [FEAT-10]. */
    val leave: Leave? = null,
    /**
     * [EXTRA-01] Work on top of the day's own shift, or on a day off: 잔업, 특근, 대근 … in the order
     * the user added them. A 대근 for a 나이트 [FEAT-02] is one of these with the night's times.
     */
    val extras: List<ExtraEntry> = emptyList(),
    /** [HRLY-01] How the day went for a part-timer: 지각, 조퇴 or 결근; null for a normal day. */
    val attendance: Attendance? = null,
    /** [SHFT-03] Who the day was swapped with (근무 교환), or empty. */
    val swapWith: String = "",
    /**
     * 2.0.0 [RVH-44] This day's own gap between two parts, as [customStart]·[customEnd] are its own times: null follows the shift,
     * [customGapMinutes] 0 is '오늘은 이어서 일해요' (no gap this day). A gap that no longer fits the day's times is not used (설계 2-6절).
     */
    val customGapStart: LocalTime? = null,
    val customGapMinutes: Int? = null,
) {
    val hasCustomTimes: Boolean get() = customStart != null && customEnd != null

    /** True when this entry changes nothing and can be deleted. */
    val isEmpty: Boolean
        get() = override == ShiftOverride.None && customStart == null && customEnd == null &&
            customBreakMinutes == null && !alarmSkipped && customAlarms == null && leave == null && extras.isEmpty() &&
            attendance == null && swapWith.isBlank() && customGapStart == null && customGapMinutes == null
}

/**
 * [HRLY-01] A part-timer's attendance. 지각 and 조퇴 keep the week's 개근 for 주휴수당; 결근 does not
 * and the day is not worked (고용노동부 해석: 지각·조퇴는 결근이 아님).
 */
enum class Attendance(val label: String, val code: Int) {
    LATE("지각", 1), EARLY("조퇴", 2), ABSENT("결근", 3);

    companion object {
        fun fromCode(code: Int): Attendance? = entries.firstOrNull { it.code == code }
    }
}

/** An alarm at a clock time on one date, set from the calendar. */
/**
 * One alarm set by hand for a day. [DAYW-09] [dayBefore] rings on the evening before (a night shift's 23:00 nap
 * alarm), which the day sheet used to drop on saving (I 리뷰 P13). 2.0.0 [RVI-17] (검토 ALM-08) [dayAfter] rings on the
 * day after (a night shift's [EASE-18] 다음날 14:00), which a nap's wake or the day sheet used to drop or move to the day.
 */
data class DayAlarm(val time: LocalTime, val enabled: Boolean = true, val dayBefore: Boolean = false, val dayAfter: Boolean = false) {
    /** When it rings for the shift day [date]. */
    fun at(date: java.time.LocalDate): java.time.LocalDateTime =
        (if (dayBefore) date.minusDays(1) else if (dayAfter) date.plusDays(1) else date).atTime(time)

    /** "전날 23:00", "다음날 14:00" or "07:00" without the time format, which the caller adds: the day part only. */
    val dayWord: String get() = if (dayBefore) "전날 " else if (dayAfter) "다음날 " else ""

    companion object {
        /** 2.0.0 [RVI-17] The alarm of shift day [date] that rings at [at], or null when [at] is not within a day of it. */
        fun ringingAt(date: java.time.LocalDate, at: java.time.LocalDateTime, enabled: Boolean = true): DayAlarm? =
            when (at.toLocalDate()) {
                date.minusDays(1) -> DayAlarm(at.toLocalTime(), enabled, dayBefore = true)
                date -> DayAlarm(at.toLocalTime(), enabled)
                date.plusDays(1) -> DayAlarm(at.toLocalTime(), enabled, dayAfter = true)
                else -> null
            }
    }
}

/** Kinds of leave; [paid] keeps the day's pay, [annual] counts against 연차 [FEAT-10]. */
enum class LeaveKind(val label: String, val paid: Boolean, val annual: Boolean) {
    ANNUAL("연차", paid = true, annual = true),
    PAID("유급 휴가", paid = true, annual = false),
    UNPAID("무급 휴가", paid = false, annual = false),
    /** [PAYX-18] 보상휴가 (근로기준법 제57조): paid, taken from the 보상휴가 earned, not from 연차. Last, so codes stay. */
    COMP("보상휴가", paid = true, annual = false),
    ;

    companion object {
        fun fromCode(code: Int): LeaveKind? = entries.getOrNull(code - 1)
    }

    val code: Int get() = ordinal + 1
}

/**
 * Leave on one day in quarters of a day [FEAT-10]: 4 = 종일, 2 = 반차, 1 = 반반차.
 * Partial leave is taken from the end of the shift, or from its start when [fromStart] (오전 반차:
 * coming in later, so the morning alarm moves too).
 *
 * 2.0.0 [RVH-45] 검토 TYPE-24·I30b-04 시간 단위 연차: [minutes] (30-minute steps) is the leave's own length, and [quarters] the
 * nearest quarter of a day for what reads only the quarters (an older app's backup, [legacyQuarters]); [at] is where a leave in
 * the middle of the day starts (외출), else it is off the start ([fromStart]) or the end. 설계 3절.
 */
data class Leave(
    val kind: LeaveKind = LeaveKind.ANNUAL,
    val quarters: Int = 4,
    val fromStart: Boolean = false,
    val minutes: Int? = null,
    val at: LocalTime? = null,
) {
    init {
        require(quarters in 1..4) { "quarters must be 1..4" }
        require(minutes == null || minutes in 1 until 24 * 60 && quarters < 4) { "an hour's leave is under a day and never a whole one" }
    }

    /** [RVH-45] Leave of so many hours, not a part of a day. */
    val hourly: Boolean get() = minutes != null

    /** Days of 연차 it takes: quarters, or [RVH-45] its minutes over a day of [dayMinutes] (설계 3-2절, 1일 기준 고정값). */
    fun days(dayMinutes: Int = DAY_MINUTES): Double = minutes?.let { it.toDouble() / dayMinutes.coerceAtLeast(1) } ?: (quarters / 4.0)
    val days: Double get() = days()
    val isFullDay: Boolean get() = quarters == 4 && minutes == null
    /** Taken off the start of the shift; a whole day has no start or end to take it from. */
    val offStart: Boolean get() = fromStart && !isFullDay

    /** 연차, 반차, 반반차, or "0.75일 연차"; [RVH-45] "연차 2시간", "무급 휴가 1시간 30분". */
    val label: String
        get() {
            minutes?.let { m -> return kind.label + " " + hoursText(m) }
            val base = if (kind == LeaveKind.ANNUAL) "" else kind.label + " "
            val part = when (quarters) {
                4 -> return if (kind == LeaveKind.ANNUAL) "연차" else kind.label
                2 -> base + "반차"
                1 -> base + "반반차"
                else -> "0.75일 " + kind.label
            }
            return if (offStart) "오전 $part" else part
        }

    /** Short mark for calendar cells; [RVH-45] an hour's leave has its kind's mark (its length is said in words beside it, 설계 5-3절). */
    val short: String get() = if (minutes != null) kindMark else when (quarters) { 4 -> kindMark; 3 -> "¾"; 2 -> "반"; else -> "¼" }
    private val kindMark: String get() = when (kind) { LeaveKind.UNPAID -> "무"; LeaveKind.COMP -> "보"; else -> "연" }

    /**
     * [RVH-45] What a 근무표 link says of this leave for an app before 2.0.0 (설계 4-4절, 총괄 1-2): such an app reads a leave without
     * '반' or '0.75' as a whole day off. An hour's leave of half a day ([dayMinutes]) or more says '반차' with its hours; a shorter one
     * says nothing (the day shows as worked), never a whole day. Null: no leave for the old key.
     */
    fun linkText(kinds: Boolean, dayMinutes: Int = DAY_MINUTES): String? {
        val m = minutes ?: return null
        if (m * 2 < dayMinutes) return null
        // At most 12 letters, as a link keeps it ('무급 반차 4.5시간').
        val hours = if (m % 60 == 0) "${m / 60}시간" else String.format(java.util.Locale.ROOT, "%.1f시간", m / 60.0)
        val mark = when (kind) { LeaveKind.UNPAID -> "무급 "; LeaveKind.PAID -> "유급 "; LeaveKind.COMP -> "보상 "; else -> "" }
        return (if (kinds) mark else "") + "반차 " + hours
    }

    /**
     * [RVH-45] This leave with the hours the database and a backup keep beside its [code] (`leaveMinutes`, `leaveAt` minute of the
     * day): a whole day or a value out of range stays as the code says.
     */
    fun withHours(minutes: Int?, atMinute: Int?): Leave {
        val m = minutes?.takeIf { it in 1 until 24 * 60 && quarters < 4 } ?: return this
        val at = atMinute?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }
        return copy(minutes = m, at = at, fromStart = fromStart && at == null)
    }

    /** [RVH-45] Where a middle leave starts, as the database keeps it (minute of the day); null off the start or the end. */
    val atMinute: Int? get() = at?.let { it.hour * 60 + it.minute }

    /** One stored integer: kind code × 10 + quarters, plus 100 when taken off the start; 0 means no leave. */
    val code: Int get() = kind.code * 10 + quarters + if (offStart) FROM_START else 0

    companion object {
        val QUARTER_OPTIONS = listOf(4, 2, 1, 3)

        /** [RVH-45] The day an hour's leave is a part of until the user says otherwise: 8 hours (설계 3-2절 `leaveDayMinutes`). */
        const val DAY_MINUTES = 480

        /** [RVH-45] An hour's leave: [minutes] (30-minute steps), its quarters the nearest under a whole day ([legacyQuarters]). */
        fun hours(kind: LeaveKind, minutes: Int, fromStart: Boolean = false, at: LocalTime? = null, dayMinutes: Int = DAY_MINUTES): Leave =
            Leave(kind, legacyQuarters(minutes, dayMinutes), fromStart = fromStart && at == null, minutes = minutes, at = at)

        /** [RVH-45] The nearest quarter of a day of [dayMinutes] for [minutes], 1 to 3: an older app never reads it as a whole day. */
        fun legacyQuarters(minutes: Int, dayMinutes: Int = DAY_MINUTES): Int =
            Math.round(minutes * 4.0 / dayMinutes.coerceAtLeast(1)).toInt().coerceIn(1, 3)

        /** "2시간", "1시간 30분", "30분". */
        fun hoursText(minutes: Int): String = when {
            minutes % 60 == 0 -> "${minutes / 60}시간"
            minutes < 60 -> "${minutes}분"
            else -> "${minutes / 60}시간 ${minutes % 60}분"
        }

        /** Added to [code] for leave off the start; an app before 1.22 reads such a code as no leave. */
        private const val FROM_START = 100

        fun fromCode(code: Int): Leave? {
            if (code <= 0) return null
            val rest = code % FROM_START
            val kind = LeaveKind.fromCode(rest / 10) ?: return null
            val q = rest % 10
            return if (q in 1..4) Leave(kind, q, fromStart = code >= FROM_START && q < 4) else null
        }
    }
}
