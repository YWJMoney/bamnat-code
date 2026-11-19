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

    /** 2.0.0 [RVJ-98] 검토 U05-06: 오늘·내일 바뀐 팀원은 6시간을 기다리지 않고 리더가 바로 알려요. */
    @Test fun todayAndTomorrowChangesAreToldNow() {
        val was = TeamBoard(dec, people).paint("a", dec.atDay(10), 1).paint("a", dec.atDay(11), 1).paint("b", dec.atDay(11), 9).paint("b", dec.atDay(20), 1)
        val confirmed = was.cells.mapValues { it.value.shiftId }
        val now = was.paint("a", dec.atDay(11), 2).paint("b", dec.atDay(11), 1).paint("b", dec.atDay(20), 2)
        val soon = TeamReceive.soon(team, now, confirmed, dec.atDay(10))
        assertEquals(listOf("가" to listOf("내일 D→E"), "나" to listOf("내일 O→D")), soon.map { it.member.name to it.days })
        assertEquals("오늘 바뀐 것도", listOf("오늘 D→E"), TeamReceive.soon(team, now, confirmed, dec.atDay(11)).first().days)
        assertTrue("20일은 오늘·내일이 아니에요", TeamReceive.soon(team, now, confirmed, dec.atDay(18)).isEmpty())
        assertTrue("첫 확정엔 없어요", TeamReceive.soon(team, now, emptyMap(), dec.atDay(10)).isEmpty())
        assertEquals("6병동 근무가 바뀌었어요.\n가: 내일 D→E https://s/a\n나: 내일 O→D\n자기 링크에서 바뀐 근무를 한 번 봐 주세요.",
            TeamReceive.soonMessage(team, soon, mapOf("a" to "https://s/a")))
    }
}
