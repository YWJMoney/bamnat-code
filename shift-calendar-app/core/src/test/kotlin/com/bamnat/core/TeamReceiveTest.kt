package com.bamnat.core

import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamReceive
import com.bamnat.core.team.TeamShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** 1.38 [P3-L-15] 받기: 바뀐 날 on each member's link after 다시 확정, and the ward's rows. */
class TeamReceiveTest {
    private val dec = YearMonth.of(2026, 12)
    private val team = Team("t", "6병동", shifts = listOf(TeamShift(1, "D", "데이", 0xFF3B82F6, false), TeamShift(2, "E", "이브닝", 0xFFF59E0B, false), TeamShift(9, "O", "오프", 0xFF9E9E9E, true)))
    private val people = listOf(TeamMember("a", "t", "가", sort = 0), TeamMember("b", "t", "나", sort = 1))

    @Test fun changedDaysOnlyAfterTheFirstConfirm() {
        val was = TeamBoard(dec, people).paint("a", dec.atDay(3), 1).paint("b", dec.atDay(3), 9).paint("a", dec.atDay(5), 1)
        val confirmed = was.cells.mapValues { it.value.shiftId }
        val now = was.paint("a", dec.atDay(3), 2).paint("a", dec.atDay(5), null).paint("b", dec.atDay(7), 1)
        val c = TeamReceive.changes(team, now, confirmed)
        assertEquals(mapOf(dec.atDay(3) to "바뀜: D→E", dec.atDay(5) to "바뀜: D→없음"), c["a"])
        assertEquals(mapOf(dec.atDay(7) to "바뀜: 없음→D"), c["b"])
        assertTrue("첫 확정엔 바뀐 날이 없어요", TeamReceive.changes(team, now, emptyMap()).isEmpty())
        val merged = TeamReceive.merge(mapOf(dec.atDay(3) to "같이 쉬어요: 나"), c.getValue("a"))
        assertEquals("바뀜: D→E · 같이 쉬어요: 나", merged[dec.atDay(3)])
    }

    @Test fun rowsAndColors() {
        val b = TeamBoard(dec, people).paint("a", dec.atDay(1), 1).paint("b", dec.atDay(2), 2)
        val rows = TeamReceive.rows(team, b, people)
        assertEquals(31, rows[0].size)
        assertEquals("D", rows[0][0])
        assertEquals("E", rows[1][1])
        assertEquals("", rows[1][0])
        assertEquals("#3B82F6", TeamReceive.colors(team)["D"])
    }
}
