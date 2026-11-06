package com.bamnat.core

import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamSwap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 [P3-X-02] 바꾸기 요청: only trades that break nothing are offered, both ways. */
class TeamSwapTest {
    private val dec = YearMonth.of(2026, 12)
    private val day = TeamShift(1, "D", "데이", 0, false, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val night = TeamShift(3, "N", "나이트", 0, false, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val off = TeamShift(9, "O", "오프", 0, true)
    private val team = Team("t", "6병동", shifts = listOf(day, night, off))
    private val people = listOf("a", "b", "c").mapIndexed { i, id -> TeamMember(id, "t", id.uppercase(), sort = i) }

    @Test fun offersOnlySafeTrades() {
        var b = TeamBoard(dec, people)
        for (d in b.days) { b = b.paint("a", d, 9).paint("b", d, 9).paint("c", d, 9) }
        val d10 = dec.atDay(10)
        b = b.paint("a", d10, 1) // a: D on the 10th
        b = b.paint("c", d10.minusDays(1), 3) // c: N on the 9th, so a D on the 10th would be N→D
        val o = TeamSwap.options(team, b, RestRules())
        assertEquals("b may take a's D", listOf("b"), o["a"]?.get(d10))
        assertEquals(listOf("a"), o["b"]?.get(d10))
        assertFalse("c would have N→D", o["c"]?.get(d10).orEmpty().contains("a"))
        assertTrue("두 사람 다 쉬는 날은 바꿀 게 없어요", o["b"]?.get(dec.atDay(11)).isNullOrEmpty())
    }

    @Test fun lockedAndPastDaysAreNotOffered() {
        var b = TeamBoard(dec, people)
        for (d in b.days) { b = b.paint("a", d, 9).paint("b", d, 9).paint("c", d, 9) }
        b = b.paint("a", dec.atDay(5), 1).lock("a", dec.atDay(5), true).paint("a", dec.atDay(3), 1)
        val o = TeamSwap.options(team, b, RestRules(), from = dec.atDay(4))
        assertTrue(o["a"].orEmpty().isEmpty())
        val ask = TeamSwap.Ask("a", "b", dec.atDay(3), TeamSwap.State.YES)
        assertEquals(1L, TeamSwap.apply(b, ask).cell("b", dec.atDay(3))?.shiftId)
        assertEquals(9L, TeamSwap.apply(b, ask).cell("a", dec.atDay(3))?.shiftId)
    }
}
