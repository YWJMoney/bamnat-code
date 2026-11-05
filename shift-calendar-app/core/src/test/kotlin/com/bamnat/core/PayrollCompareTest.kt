package com.bamnat.core

import com.bamnat.core.boss.PayrollCompare
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 PC 1차: 지난달 비교 must not say the opposite of the money (민수 10월 → 11월, 2026). */
class PayrollCompareTest {
    private val place = Workplace("p", "가게", fivePlus = true, payday = 10)
    private val nineToSix = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val minsu = StaffMember("s", "민수", base = 10_320,
        weekly = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).associateWith { nineToSix })

    @Test fun octoberToNovemberSaysFewerHoursAndTheHolidayWork() {
        val oct = PayrollLedger.month(place, listOf(minsu), YearMonth.of(2026, 10), emptyList())
        val nov = PayrollLedger.month(place, listOf(minsu), YearMonth.of(2026, 11), emptyList())
        // 10월 22일 × 8시간 = 176시간 (공휴일에 일한 16시간 포함), 11월 21일 × 8시간 = 168시간.
        assertEquals(176 * 60, oct.lines.single().workMinutes)
        assertEquals(168 * 60, nov.lines.single().workMinutes)
        assertEquals(2_146_560L, nov.lines.single().gross)
        val r = PayrollCompare.rows(nov, oct).single()
        assertEquals(-247_680L, r.diff)
        assertEquals("일한 시간 8시간 줄었어요 · 휴일근로 (8시간 이내) −247,680원", r.why)
    }
}

/** 1.38 PC 1차: 확인할 것 puts the danger first and folds 책임자 없음 into one line. */
class TeamSignSummaryTest {
    @Test fun dangerFirstAndNotesFolded() {
        val d = java.time.LocalDate.of(2026, 11, 1)
        val signs = (0 until 30).map { com.bamnat.core.team.TeamCheck.Sign(com.bamnat.core.team.TeamCheck.Kind.NO_CHARGE, d.plusDays(it.toLong()), null, "D 책임자 없음") } +
            com.bamnat.core.team.TeamCheck.Sign(com.bamnat.core.team.TeamCheck.Kind.RULE, d.plusDays(19), "m1", "준호: 나이트 다음 날 데이") +
            com.bamnat.core.team.TeamCheck.Sign(com.bamnat.core.team.TeamCheck.Kind.SHORT, d.plusDays(3), null, "D 1명 부족")
        val lines = com.bamnat.core.team.TeamCheck.summary(signs)
        assertEquals(3, lines.size)
        assertEquals("20일 준호: 나이트 다음 날 데이", lines[0].text)
        assertEquals("인원이 모자란 날 1일 · 4일 D 1명 부족", lines[1].text)
        assertEquals(30, lines[2].count)
        assertEquals("책임자 없는 근무 30번 · 1일 D, 2일 D, 3일 D, 4일 D …", lines[2].text)
    }
}
