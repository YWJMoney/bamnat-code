package com.bamnat.core

import com.bamnat.core.manage.ManageDesk
import com.bamnat.core.manage.ManageDesk.ShopGlance
import com.bamnat.core.manage.ManageDesk.TeamGlance
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 관리 화면 할 일 (사용자 2026-11-05). */
class ManageDeskTest {
    private fun team(confirmed: Boolean = true, empty: Int = 0, danger: Int = 0, short: Int = 0, next: Boolean = false, members: Int = 8) =
        TeamGlance("t1", "7병동", members, confirmed, 1, empty, danger, short, next, 40)

    private fun shop(five: Boolean = true, closed: Boolean = true, lines: Int = 3, paid: Int = 3, sent: Int = 3, staff: Int = 3, warn: Int = 0) =
        ShopGlance("p1", "우리 가게", staff, five, closed, lines, paid, sent, LocalDate.of(2026, 11, 10), warn)

    @Test fun allDoneEarlyInTheMonthIsQuiet() {
        assertEquals(emptyList<ManageDesk.Todo>(), ManageDesk.todos(LocalDate.of(2026, 11, 3), listOf(team()), listOf(shop())))
    }

    @Test fun dangerAndUnconfirmedComeFirst() {
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 20), listOf(team(confirmed = false, empty = 12, danger = 2, short = 3)), listOf(shop(sent = 1)))
        assertTrue(l.take(2).all { it.urgent })
        assertTrue(l[0].text, l[0].text.contains("위험 2개"))
        assertTrue(l[1].text, l[1].text.contains("확정 전") && l[1].text.contains("빈칸 12"))
        // 15일부터 다음 달 짜기, 25일 전에는 급하지 않음.
        val nextJob = l.single { it.key.startsWith("team-next") }
        assertEquals(false, nextJob.urgent)
        assertTrue(nextJob.text.contains("12월"))
        assertTrue(l.any { it.text.contains("명세서를 아직 안 보낸 직원 2명") })
        assertTrue(ManageDesk.todos(LocalDate.of(2026, 11, 26), listOf(team()), emptyList()).single().urgent)
    }

    @Test fun aTeamMadeForNextMonthIsNotAskedForThisOne() {
        val t = TeamGlance("t1", "7병동", 8, false, 0, 240, 0, 0, false, 240, painted = 0)
        assertEquals(emptyList<ManageDesk.Todo>(), ManageDesk.todos(LocalDate.of(2026, 11, 3), listOf(t), emptyList()))
    }

    @Test fun aTeamWithNobodyHasNothingToDo() {
        assertEquals(0, ManageDesk.todos(LocalDate.of(2026, 11, 28), listOf(team(confirmed = false, members = 0)), emptyList()).size)
    }

    @Test fun theShopsLedgerIsDueThreeDaysBeforePayday() {
        val early = ManageDesk.todos(LocalDate.of(2026, 11, 6), emptyList(), listOf(shop(closed = false)))
        assertEquals(false, early.single().urgent)
        val due = ManageDesk.todos(LocalDate.of(2026, 11, 7), emptyList(), listOf(shop(closed = false)))
        assertTrue(due.single().urgent)
        assertTrue(due.single().text, due.single().text.contains("10월 대장을 닫기 전") && due.single().text.contains("11/10"))
        // 5인 여부를 모르면 맨 위, 급함.
        val five = ManageDesk.todos(LocalDate.of(2026, 11, 1), emptyList(), listOf(shop(five = false)))
        assertTrue(five.single().urgent && five.single().text.contains("5인"))
        // 닫은 뒤 지급일이 지나도 지급 표시가 없으면 급함.
        assertTrue(ManageDesk.todos(LocalDate.of(2026, 11, 10), emptyList(), listOf(shop(paid = 2))).single().urgent)
    }

    /** 2.0.0 [RVJ-50] 검토 PC-118: an urgent to-do is a 주의; only a team's 휴식·연속·주 52시간 signs are a 위험 (red). */
    @Test fun onlyTheTeamsSignsAreADanger() {
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 26), listOf(team(confirmed = false, empty = 12, danger = 2)), listOf(shop(five = false, closed = false)))
        assertEquals(listOf("team-danger"), l.filter { it.danger }.map { it.key.substringBefore("-t1") })
        assertTrue("마감·확정·5인 이상은 주의", l.filter { it.urgent && !it.danger }.size >= 3)
    }
}
