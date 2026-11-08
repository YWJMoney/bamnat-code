package com.bamnat.core

import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCover
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.WishKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 [P3-X-01] 결원 메우기: who can take a night someone cannot come to, best first, and why the others cannot. */
class TeamCoverTest {
    private val dec = YearMonth.of(2026, 12)
    private val day = TeamShift(1, "D", "데이", 0, false, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val night = TeamShift(3, "N", "나이트", 0, false, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val off = TeamShift(9, "O", "오프", 0, true)
    private val team = Team("t", "6병동", shifts = listOf(day, night, off))
    private val people = listOf("a", "b", "c", "d", "e").mapIndexed { i, id -> TeamMember(id, "t", id.uppercase(), sort = i) }

    @Test fun rankedAndExplained() {
        val d = dec.atDay(10)
        var b = TeamBoard(dec, people).paint("a", d, 3) // a cannot come to the 10th's night
        b = b.paint("b", d, 1) // b works that day: not asked
        b = b.paint("c", d.plusDays(1), 1) // c has a D the next morning: N→D
        b = b.paint("e", dec.atDay(3), 1).paint("e", dec.atDay(4), 1) // e has worked more this month
        val wishes = listOf(TeamWish("d", d, WishKind.OFF))
        val got = TeamCover.candidates(team, b, TeamCover.Gap("a", d, 3), RestRules(), wishes)
        assertEquals(listOf("E", "C", "D"), got.map { it.member.name }.let { l -> listOf(l.first()) + l.drop(1).sorted() })
        assertTrue(got.first().ok)
        val c = got.single { it.member.id == "c" }
        assertFalse(c.ok)
        assertTrue(c.why.joinToString(), c.why.any { it.contains("나이트") || it.contains("쉬") })
        assertEquals(listOf("오프 신청한 날이에요"), got.single { it.member.id == "d" }.why)
        assertTrue("b는 그날 일해서 빠져요", got.none { it.member.id == "b" })
    }

    @Test fun applyMovesTheShiftEvenIfLocked() {
        val d = dec.atDay(10)
        val b = TeamBoard(dec, people).paint("a", d, 3).lock("a", d, true)
        val after = TeamCover.apply(team, b, TeamCover.Gap("a", d, 3), "e")
        assertEquals(9L, after.cell("a", d)?.shiftId)
        assertEquals(3L, after.cell("e", d)?.shiftId)
    }
}
