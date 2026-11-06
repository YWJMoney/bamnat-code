package com.bamnat.core

import com.bamnat.core.boss.StaffClock
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffPunch
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.38 [P3-B-11] 출퇴근 기록: presses → days → the rows the boss may put in. */
class StaffClockTest {
    private fun at(d: String, t: String) = LocalDate.parse(d).atTime(LocalTime.parse(t)).atZone(StaffClock.ZONE).toInstant()
    private fun p(d: String, t: String, inside: Boolean) = StaffPunch("s", at(d, t), inside)
    private val s = StaffMember("s", "가", hiredOn = LocalDate.of(2026, 1, 1),
        weekly = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)), DayOfWeek.TUESDAY to StaffShift(LocalTime.of(22, 0), LocalTime.of(6, 0))))

    @Test fun daysPairFirstInAndLastOutAcrossMidnight() {
        val days = StaffClock.days(listOf(
            p("2026-11-02", "09:05", true), p("2026-11-02", "09:05", true), p("2026-11-02", "18:00", false),
            p("2026-11-03", "21:58", true), p("2026-11-04", "06:10", false),
            p("2026-11-04", "22:00", true),
        ))
        assertEquals(listOf(
            StaffClock.Day(LocalDate.of(2026, 11, 2), LocalTime.of(9, 5), LocalTime.of(18, 0)),
            StaffClock.Day(LocalDate.of(2026, 11, 3), LocalTime.of(21, 58), LocalTime.of(6, 10)),
            StaffClock.Day(LocalDate.of(2026, 11, 4), LocalTime.of(22, 0), null),
        ), days)
    }

    @Test fun suggestsLateEarlyAndOtherHoursOnly() {
        val days = listOf(
            StaffClock.Day(LocalDate.of(2026, 11, 2), LocalTime.of(9, 20), LocalTime.of(18, 0)),  // 월 지각
            StaffClock.Day(LocalDate.of(2026, 11, 9), LocalTime.of(9, 0), LocalTime.of(17, 0)),   // 월 조퇴
            StaffClock.Day(LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), LocalTime.of(18, 0)),  // 월 평소대로
            StaffClock.Day(LocalDate.of(2026, 11, 3), LocalTime.of(22, 0), LocalTime.of(7, 0)),   // 화 야간 연장
            StaffClock.Day(LocalDate.of(2026, 11, 7), LocalTime.of(10, 0), LocalTime.of(14, 0)),  // 토: 평소 없는 날
            StaffClock.Day(LocalDate.of(2026, 11, 23), LocalTime.of(9, 30), null),                // 퇴근 없음
            StaffClock.Day(LocalDate.of(2026, 11, 30), LocalTime.of(9, 30), LocalTime.of(18, 0)), // 사장님이 이미 고친 날
        )
        val rows = StaffClock.suggest(s, days, written = setOf(LocalDate.of(2026, 11, 30)))
        assertEquals(listOf(WorkKind.LATE, WorkKind.EARLY, WorkKind.NORMAL, WorkKind.NORMAL), rows.map { it.kind })
        assertEquals(listOf(2, 9, 3, 7), rows.map { it.date.dayOfMonth })
        assertEquals("문 앞 3분은 지각이 아니게", 0, StaffClock.suggest(s, listOf(StaffClock.Day(LocalDate.of(2026, 11, 2), LocalTime.of(9, 3), LocalTime.of(18, 0))), emptySet(), graceMinutes = 5).size)
    }
}
