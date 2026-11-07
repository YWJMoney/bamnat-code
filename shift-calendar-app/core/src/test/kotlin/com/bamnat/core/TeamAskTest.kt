package com.bamnat.core

import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.AskRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAsk
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.WishKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 [P3-L-12] 병동 신청표 "평일 2명, 주말 3명" (사용자 2026-11-06). */
class TeamAskTest {
    private val dec = YearMonth.of(2026, 12)
    private val mon = dec.atDay(7) // 월
    private val sat = dec.atDay(5)
    private val r = AskRules()
    private fun w(m: String, d: LocalDate, k: WishKind = WishKind.OFF, at: Long = 0) = TeamWish(m, d, k, at = at)

    @Test fun dayCapsArePaperRules() {
        val two = listOf(w("a", mon), w("b", mon))
        assertEquals(TeamAsk.No.DAY_FULL, TeamAsk.check(r, dec, two, w("c", mon), 0))
        assertNull("주말은 3명", TeamAsk.check(r, dec, listOf(w("a", sat), w("b", sat)), w("c", sat), 0))
        assertEquals(TeamAsk.No.DAY_FULL, TeamAsk.check(r, dec, listOf(w("a", sat), w("b", sat), w("d", sat)), w("c", sat), 0))
        assertNull("공휴일 25일은 주말처럼", TeamAsk.check(r.copy(holidays = setOf(dec.atDay(25))), dec,
            listOf(w("a", dec.atDay(25)), w("b", dec.atDay(25))), w("c", dec.atDay(25)), 0))
        assertNull("자기 신청 바꾸기는 두 번 세지 않아요", TeamAsk.check(r, dec, two, w("a", mon, WishKind.LEAVE), 0))
        assertNull("근무 희망은 자리를 차지하지 않아요", TeamAsk.check(r, dec, two, TeamWish("c", mon, WishKind.SHIFT, shiftId = 1), 0))
        assertNull("공정 배정이면 넘쳐도 받아요", TeamAsk.check(r.copy(firstCome = false), dec, two, w("c", mon), 0))
    }

    @Test fun personCapDeadlineKindsBlocked() {
        val mine = (1..4).map { w("a", dec.atDay(it + 6)) }
        assertEquals(TeamAsk.No.MINE_FULL, TeamAsk.check(r.copy(perPerson = 4), dec, mine, w("a", dec.atDay(20)), 0))
        assertEquals(TeamAsk.No.CLOSED, TeamAsk.check(r.copy(closesAt = 100), dec, emptyList(), w("a", mon), 100))
        assertEquals(TeamAsk.No.KIND, TeamAsk.check(r, dec, emptyList(), w("a", mon, WishKind.EDU), 0))
        assertEquals(TeamAsk.No.BLOCKED, TeamAsk.check(r.copy(blocked = setOf(mon)), dec, emptyList(), w("a", mon), 0))
        assertEquals(TeamAsk.No.MONTH, TeamAsk.check(r, dec, emptyList(), w("a", LocalDate.of(2027, 1, 1)), 0))
    }

    @Test fun orderFirstComeOrFair() {
        val all = listOf(w("a", mon, at = 3), w("b", mon, at = 1), w("c", mon, at = 2))
        val first = TeamAsk.order(r, all).single()
        assertEquals(listOf("b", "c"), first.inside.map { it.memberId })
        assertEquals(listOf("a"), first.over.map { it.memberId })
        val fair = TeamAsk.order(r.copy(firstCome = false), all, misses = mapOf("a" to 2)).single()
        assertEquals(listOf("a", "b"), fair.inside.map { it.memberId })
        assertEquals("오프 1", TeamAsk.mine(all, "a"))
    }

    @Test fun kindsSurviveTheRow() {
        assertEquals(WishKind.SHIFT to 3L, WishKind.parse(WishKind.SHIFT.code(3)))
        assertEquals(WishKind.LEAVE to null, WishKind.parse("LEAVE"))
        assertNull(WishKind.parse("NEWER_KIND"))
    }

    private val day = TeamShift(1, "D", "데이", 0, false, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val night = TeamShift(3, "N", "나이트", 0, false, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val off = TeamShift(9, "O", "오프", 0, true)
    private val leave = TeamShift(10, "연", "연차", 0, true)
    private val team = Team("t", "6병동", shifts = listOf(day, night, off, leave), need = mapOf(1L to 1, 3L to 1))
    private val people = listOf(TeamMember("a", "t", "가"), TeamMember("b", "t", "나"), TeamMember("c", "t", "다"))

    /** The paper's "NN off" margin: 11/29·30 N then 12/1 D is told on 12/1, and auto-fill does not do it. */
    @Test fun lastMonthIsCarried() {
        val nov = TeamBoard(YearMonth.of(2026, 11), people).paint("a", LocalDate.of(2026, 11, 29), 3).paint("a", LocalDate.of(2026, 11, 30), 3)
        val b = TeamBoard(dec, people).paint("a", dec.atDay(1), 1)
        val signs = TeamCheck.check(team, b, RestRules(), prev = nov)
        assertTrue(signs.any { it.memberId == "a" && it.date == dec.atDay(1) })
        assertTrue("지난달 없으면 모름", TeamCheck.check(team, b, RestRules()).none { it.memberId == "a" && it.date == dec.atDay(1) })
        assertEquals("··NN", TeamCheck.tail(team, nov, "a"))
        val plan = TeamAuto.fill(team, TeamBoard(dec, people), rules = RestRules(), prev = nov)
        assertTrue("12/1 a는 데이를 받지 않아요", plan.board.cell("a", dec.atDay(1))?.shiftId != 1L)
    }

    @Test fun leaveIsPaintedAsLeaveAndShiftWishKept() {
        val wishes = listOf(TeamWish("a", dec.atDay(3), WishKind.LEAVE), TeamWish("b", dec.atDay(4), WishKind.SHIFT, shiftId = 3))
        val plan = TeamAuto.fill(team, TeamBoard(dec, people), wishes, RestRules())
        assertEquals(10L, plan.board.cell("a", dec.atDay(3))?.shiftId)
        assertEquals(3L, plan.board.cell("b", dec.atDay(4))?.shiftId)
        assertEquals(2, plan.wishesKept)
    }
}
