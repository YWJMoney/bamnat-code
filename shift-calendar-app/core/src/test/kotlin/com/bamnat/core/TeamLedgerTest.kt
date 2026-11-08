package com.bamnat.core

import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamLedger
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.WishKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 [P3-X-04] 공정성 장부: months add up, and 자동 채우기 leans against who already took more. */
class TeamLedgerTest {
    private val day = TeamShift(1, "D", "데이", 0, false, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val night = TeamShift(3, "N", "나이트", 0, false, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val off = TeamShift(9, "O", "오프", 0, true)
    private val team = Team("t", "6병동", shifts = listOf(day, night, off), need = mapOf(1L to 1, 3L to 1))
    private val people = listOf("a", "b", "c").mapIndexed { i, id -> TeamMember(id, "t", id.uppercase(), sort = i) }

    private fun month(ym: YearMonth, nightsFor: String): TeamBoard {
        var b = TeamBoard(ym, people)
        b.days.forEachIndexed { i, d ->
            if (i % 3 == 0) b = b.paint(nightsFor, d, 3)
            else b = b.paint(nightsFor, d, 9)
        }
        return b
    }

    @Test fun addsUpAndKeptShare() {
        val oct = month(YearMonth.of(2026, 10), "a")
        val nov = month(YearMonth.of(2026, 11), "a")
        val wishes = listOf(TeamWish("a", YearMonth.of(2026, 11).atDay(2), WishKind.OFF), TeamWish("a", YearMonth.of(2026, 11).atDay(1), WishKind.OFF))
        val rows = TeamLedger.of(team, people, listOf(TeamLedger.Month(oct), TeamLedger.Month(nov, wishes)))
        val a = rows.single { it.member.id == "a" }
        assertEquals(2, a.months)
        assertEquals(11 + 10, a.nights)
        assertEquals(2, a.asked)
        assertEquals("11/1 나이트라 못 지킴, 11/2 오프는 지킴", 50, a.keptPercent)
        assertEquals("b·c는 빈 달이라 셈하지 않아요", 0, rows.single { it.member.id == "b" }.months)
    }

    @Test fun fillGivesFewerNightsToWhoHadMore() {
        val ym = YearMonth.of(2026, 12)
        val rows = listOf(
            TeamLedger.Row(people[0], 6, 60, 10, 0, 0, 0, 0),
            TeamLedger.Row(people[1], 6, 30, 10, 0, 0, 0, 0),
            TeamLedger.Row(people[2], 6, 30, 10, 0, 0, 0, 0),
        )
        val past = TeamLedger.past(rows)
        assertTrue(past.getValue("a").nights > 0)
        val board = TeamAuto.fill(team, TeamBoard(ym, people), past = past).board
        fun nights(id: String) = board.days.count { board.cell(id, it)?.shiftId == 3L }
        assertTrue("a ${nights("a")} < b ${nights("b")}", nights("a") < nights("b"))
        val even = TeamAuto.fill(team, TeamBoard(ym, people)).board
        assertTrue("지난달이 없으면 고르게", kotlin.math.abs(even.days.count { even.cell("a", it)?.shiftId == 3L } - even.days.count { even.cell("b", it)?.shiftId == 3L }) <= 2)
    }
}
