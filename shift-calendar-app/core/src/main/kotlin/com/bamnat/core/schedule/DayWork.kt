package com.bamnat.core.schedule

import com.bamnat.core.model.Attendance
import com.bamnat.core.recipe.ShiftRole

/**
 * [LINK-01] 1.39 작품이 보는 하루 (사용자 2026-11-07 "제작물을 입히면 교대근무자 대근·특근 등 근무 입력을 테마가 인식 못함. 스튜디오
 * 작품은 설정·입력과 완벽히 상호작용하게 앱 코드 바탕 위에 그려져야"): what every 작품 surface — 달력 칸의 부품 상태(`role.*`·`extra`…),
 * 꾸밈 층, 수식 값(근무종류·쉬는날·근무시간·추가근무…), 위젯 칸, 근무에 따라 바뀌는 모양 — reads of one calendar day, worked out in this
 * one place from the same [ResolvedDay] the app's own calendar draws. Before, each surface took `shiftType ?: coveredShift`, and as a
 * 교대 calendar's day off is itself a kind (비·휴, [com.bamnat.core.model.ShiftType.isOff]) the 대근 behind it never showed: a 대근·특근
 * day was drawn and counted as a day off, and 잔업·특근 hours were missing from 근무시간.
 *
 * How the day reads:
 * - A working day: its own kind ([kind]), with any 잔업·특근·대근 on top as [extras] ([extra], [cover]).
 * - A day off (or no shift) with a 대근: the covered kind, as the calendar's own cell tints it ([EXTRA-01]); [worked] true.
 * - A day off with other extra work only (특근 09~18): the first piece's kind by its name and clock ([ShiftRole.guess]); [worked] true.
 * - A full-day 연차 or a 결근: off ([ShiftRole.OFF]) with [leave] / [absent], whatever kind it would have been.
 */
data class DayWork(
    /** The kind of work's name as the day shows it (주간, 야간 for a 야간 대근, 특근 on a day off with only 특근); null when nothing is on it. */
    val name: String?,
    /** [ShiftRole]: null only when nothing is on the day at all. */
    val role: ShiftRole?,
    /** Somebody works that day: a working shift, or extra work on a day off. */
    val worked: Boolean,
    /** Minutes of the scheduled shift (after break and leave). */
    val shiftMinutes: Int,
    /** Minutes of 잔업·특근·대근 that day. */
    val extraMinutes: Int,
    /** The names of the day's kinds of extra work, in the order added (잔업·특근·대근·my own). */
    val extraKinds: List<String>,
    /** A 대근 (extra work that covered a kind of shift). */
    val cover: Boolean,
    /** The leave's name (연차·반차…), or null. */
    val leave: String?,
    val fullLeave: Boolean,
    val absent: Boolean,
    /** Set by hand: a kind or times different from the 근무 순서 ([DaySource.OVERRIDE] over an order, or own times). */
    val edited: Boolean,
    /** "HH:mm" of the work the day shows (the 대근's own times on a day off). */
    val start: java.time.LocalTime?,
    val end: java.time.LocalTime?,
    /** The kind's colour (ARGB) as the calendar's own cell tints the day, or null. */
    val colorArgb: Long?,
    /** A work alarm is set for the day (the kind's, or the day's own), and not turned off for it. */
    val alarm: Boolean = false,
) {
    val extra: Boolean get() = extraKinds.isNotEmpty()
    val totalMinutes: Int get() = shiftMinutes + extraMinutes
    /** Off for 쉬는날: no work at all, or a full day of leave or 결근. */
    val off: Boolean get() = !worked

    /**
     * The blueprint states this day adds to a part ([com.bamnat.core.recipe.BlueprintParts.STATES]): its `role.*`, then 1.39 `leave` (연차·반차),
     * `extra` (잔업·특근·대근이 있는 날), `cover` (대근), `edited` (직접 바꾼 날).
     */
    val states: Set<String> get() = buildSet {
        role?.let { add("role." + it.key) }
        if (leave != null) add("leave")
        if (extra) add("extra")
        if (cover) add("cover")
        if (edited) add("edited")
    }

    companion object {
        /** States [states] may add, for screens that list them (스튜디오 부품 도구의 상태 칩). */
        val STATES = listOf("leave", "extra", "cover", "edited")

        val NONE = DayWork(null, null, false, 0, 0, emptyList(), false, null, false, false, false, null, null, null)

        fun of(day: ResolvedDay, hasPattern: Boolean = true): DayWork {
            val own = day.shiftType
            val leave = day.leave
            val fullLeave = leave?.isFullDay == true
            val absent = day.entry?.attendance == Attendance.ABSENT
            val covered = day.coveredShift
            val extras = day.extras
            val kinds = extras.map { it.kind.name.ifBlank { it.kind.mark } }.distinct()
            val edited = day.hasCustomTimes || (day.source == DaySource.OVERRIDE && hasPattern)
            val working = day.isWorkDay && !fullLeave && !absent
            val first = extras.firstOrNull()
            val (name, role, times) = when {
                working && own != null -> Triple(own.name, ShiftRole.guess(own.name, false, (day.start ?: own.start)?.hour, own.durationMinutes),
                    day.start to day.end)
                working -> Triple("근무", ShiftRole.guess("", false, day.start?.hour, day.scheduledMinutes), day.start to day.end)
                covered != null && !covered.isOff -> {
                    val e = extras.first { it.covered != null }
                    Triple(covered.name, ShiftRole.guess(covered.name, false, (e.start ?: covered.start)?.hour, covered.durationMinutes),
                        (e.start ?: covered.start) to (e.end ?: covered.end))
                }
                first != null -> Triple(first.kind.name.ifBlank { first.kind.mark },
                    ShiftRole.guess(first.kind.name, false, first.start?.hour, first.minutes).let { if (it == ShiftRole.OFF) ShiftRole.OTHER else it },
                    first.start to first.end)
                own != null || fullLeave || absent -> Triple(own?.name ?: leave?.label, ShiftRole.OFF, null to null)
                else -> Triple(null, null, null to null)
            }
            val color = when {
                working -> own?.colorArgb
                covered != null && !covered.isOff -> covered.colorArgb
                first != null -> first.kind.colorArgb
                else -> null
            }
            return DayWork(
                name = name, role = role, worked = working || extras.isNotEmpty(),
                shiftMinutes = if (working) day.scheduledMinutes else 0,
                extraMinutes = day.extraMinutes, extraKinds = kinds, cover = covered != null,
                leave = leave?.label, fullLeave = fullLeave, absent = absent, edited = edited,
                start = times.first, end = times.second, colorArgb = color,
                alarm = (working || covered != null) && !day.alarmSkipped &&
                    (day.entry?.customAlarms?.let { it.isNotEmpty() } ?: ((if (working) own else covered)?.alarmEnabled == true)),
            )
        }
    }
}
