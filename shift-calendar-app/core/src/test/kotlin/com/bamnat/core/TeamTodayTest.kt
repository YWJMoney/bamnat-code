package com.bamnat.core

import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/** 1.39 [FLOW-23] 팀 오늘 판 머리: 출근 수·빈자리 and the day's kinds in the order they start. */
class TeamTodayTest {
    private val dec = YearMonth.of(2026, 12)
    private val day = TeamShift(1, "주", "주간", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(19, 0))
    private val night = TeamShift(2, "야", "야간", 0xFF673AB7, false, LocalTime.of(19, 0), LocalTime.of(7, 0))
    private val off = TeamShift(3, "휴", "휴무", 0xFF9E9E9E, true)
    private val office = TeamShift(4, "교", "교육", 0xFF4CAF50, false)
    private fun people(n: Int) = (0 until n).map { TeamMember("m$it", "t", "사람$it", sort = it) }

    @Test fun `twelve at work, one short on nights, steps by start time`() {
        // The paint bar has 야간 first; the line still reads 주간 then 야간.
        val team = Team("t", "새병동", shifts = listOf(night, day, off), need = mapOf(1L to 6, 2L to 7))
        val d = dec.atDay(5)
        var b = TeamBoard(dec, people(14))
        (0 until 6).forEach { b = b.paint("m$it", d, 1) }
        (6 until 12).forEach { b = b.paint("m$it", d, 2) }
        b = b.paint("m12", d, 3)
        val t = TeamToday.of(team, b, d)!!
        assertEquals("오늘 출근 12명 · 빈자리 1", t.head())
        assertEquals(listOf("주간" to 6, "야간" to 6), t.steps.map { it.shift.name to it.count })
        assertEquals(listOf(0, 1), t.steps.map { it.short })
        assertEquals(1, t.unpainted)
    }

    @Test fun `a kind without a time comes last, days off are not work`() {
        val team = Team("t", "가게", shifts = listOf(office, day, off))
        val d = dec.atDay(1)
        val b = TeamBoard(dec, people(3)).paint("m0", d, 4).paint("m1", d, 1).paint("m2", d, 3)
        val t = TeamToday.of(team, b, d)!!
        assertEquals("오늘 출근 2명", t.head())
        assertEquals(listOf("주간", "교육"), t.steps.map { it.shift.name })
    }

    @Test fun `nothing painted yet says so, another month is nothing`() {
        val team = Team("t", "새병동", shifts = listOf(day, night, off))
        val b = TeamBoard(dec, people(2))
        assertEquals("오늘 근무가 아직 칠해지지 않았어요", TeamToday.of(team, b, dec.atDay(2))!!.head())
        assertNull(TeamToday.of(team, b, dec.minusMonths(1).atDay(30)))
        // With 근무별 인원 set, an empty day is all 빈자리.
        val need = team.copy(need = mapOf(1L to 2))
        assertEquals("오늘 출근 0명 · 빈자리 2", TeamToday.of(need, b, dec.atDay(2))!!.head())
    }
}
