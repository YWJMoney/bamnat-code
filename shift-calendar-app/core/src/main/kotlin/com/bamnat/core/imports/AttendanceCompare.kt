package com.bamnat.core.imports

import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.Attendance
import com.bamnat.core.schedule.ResolvedDay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.32 [WTYP-14] 조사 대조 W11 (근무유형 조사 5장 사무직: 시프티 "퇴근이 1분 빠르게 찍혀 조퇴 처리"): the company's 근태 export
 * set beside my own record, day by day, without writing anything. Each day says where the two differ (출근, 퇴근, 연장,
 * 쉰 날·결근) so the user can take it to the company with their own times.
 */
object AttendanceCompare {
    /** One day where the company and I differ. Minutes are the company's less mine (negative: the company counts less). */
    data class Diff(
        val date: LocalDate,
        val lines: List<String>,
        /** Work time the company counts less than me (0 when it counts as much or more). */
        val shortMinutes: Int,
        /** The company's 퇴근 is earlier than mine. */
        val leftEarlier: Boolean,
    )

    data class Result(val compared: Int, val diffs: List<Diff>) {
        val shortMinutes: Int get() = diffs.sumOf { it.shortMinutes }
        val leftEarlierDays: Int get() = diffs.count { it.leftEarlier }
        /** "14일 비교 · 3일 달라요 · 회사 기록이 1시간 12분 적어요". */
        val summary: String get() = "${compared}일 비교 · " + if (diffs.isEmpty()) "모두 같아요" else "${diffs.size}일 달라요" +
            (if (shortMinutes > 0) " · 회사 기록이 ${HoursFormat.korean(shortMinutes)} 적어요" else "")
    }

    /** Differences this small are the clock's, not the record's. */
    const val TOLERANCE_MINUTES = 0

    /** My day's first and last minute: the shift and the extra work next to it. */
    fun mine(day: ResolvedDay): Pair<LocalDateTime, LocalDateTime>? {
        val starts = listOfNotNull(day.shiftFrom) + day.extras.mapNotNull { it.from }
        val ends = listOfNotNull(day.shiftTo) + day.extras.mapNotNull { it.to }
        val from = starts.minOrNull() ?: return null
        val to = ends.maxOrNull() ?: return null
        return from to to
    }

    /** The day's difference; null when they agree or the company row says nothing to compare. */
    fun diff(row: AttendanceRow, day: ResolvedDay): Diff? {
        val lines = mutableListOf<String>()
        var short = 0
        var earlier = false
        val myAbsent = day.entry?.attendance == Attendance.ABSENT
        val mineSpan = mine(day)
        when {
            row.absent && !myAbsent && mineSpan != null -> lines += "회사엔 결근 · 내 기록엔 ${clock(mineSpan.first)}–${clock(mineSpan.second)} 근무"
            row.leave?.isFullDay == true && day.leave == null && mineSpan != null -> lines += "회사엔 ${row.leave.label} · 내 기록엔 근무"
            row.leave != null && day.leave?.kind != row.leave.kind -> lines += "회사엔 ${row.leave.label} · 내 기록엔 " + (day.leave?.label ?: "휴가 없음")
        }
        if (row.hasTimes && mineSpan != null) {
            val cIn = row.date.atTime(row.start!!)
            val cOut = if (row.end!!.isAfter(row.start)) row.date.atTime(row.end) else row.date.plusDays(1).atTime(row.end)
            val inDiff = minutes(mineSpan.first, cIn)
            val outDiff = minutes(mineSpan.second, cOut)
            if (kotlin.math.abs(inDiff) > TOLERANCE_MINUTES) {
                lines += "출근 회사 ${clock(cIn)} · 내 ${clock(mineSpan.first)} (" + (if (inDiff > 0) "${HoursFormat.korean(inDiff)} 늦게" else "${HoursFormat.korean(-inDiff)} 일찍") + " 찍힘)"
            }
            if (kotlin.math.abs(outDiff) > TOLERANCE_MINUTES) {
                earlier = outDiff < 0
                lines += "퇴근 회사 ${clock(cOut)} · 내 ${clock(mineSpan.second)} (" + (if (outDiff < 0) "${HoursFormat.korean(-outDiff)} 일찍" else "${HoursFormat.korean(outDiff)} 늦게") + " 찍힘)"
            }
            short += (inDiff.coerceAtLeast(0) + (-outDiff).coerceAtLeast(0))
        } else if (row.hasTimes && mineSpan == null && !day.isWorkDay) {
            lines += "회사엔 ${clock(row.date.atTime(row.start!!))}–${clock(row.date.atTime(row.end!!))} 근무 · 내 기록엔 쉬는 날"
        }
        row.overtimeMinutes?.let { co ->
            val my = day.extraMinutes
            if (kotlin.math.abs(co - my) > TOLERANCE_MINUTES && (co > 0 || my > 0)) {
                lines += "연장 회사 ${HoursFormat.korean(co)} · 내 ${HoursFormat.korean(my)}" + if (co < my) " (${HoursFormat.korean(my - co)} 적음)" else ""
                if (!row.hasTimes) short += (my - co).coerceAtLeast(0)
            }
        }
        if (lines.isEmpty()) return null
        return Diff(row.date, lines, short, earlier)
    }

    /** Every row of the export beside my record, oldest first. */
    fun compare(rows: List<AttendanceRow>, resolve: (LocalDate) -> ResolvedDay): Result {
        val used = rows.filter { !it.isEmpty || it.workMinutes != null }.distinctBy { it.date }.sortedBy { it.date }
        return Result(used.size, used.mapNotNull { diff(it, resolve(it.date)) })
    }

    /** Copyable lines for the company: "10/15(목) 퇴근 회사 18:59 · 내 19:00 (1분 일찍 찍힘)". */
    fun text(result: Result, dayName: (LocalDate) -> String): String =
        (listOf("근태 기록 비교 (내 기록: 밤낮)", result.summary) + result.diffs.flatMap { d -> d.lines.map { "${dayName(d.date)} $it" } }).joinToString("\n")

    private fun minutes(a: LocalDateTime, b: LocalDateTime): Int = Duration.between(a, b).toMinutes().toInt()

    private fun clock(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
}
