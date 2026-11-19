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
    private fun team(confirmed: Boolean = true, empty: Int = 0, danger: Int = 0, short: Int = 0, next: Boolean = false, members: Int = 8, law: Int = 0) =
        TeamGlance("t1", "7병동", members, confirmed, 1, empty, danger, short, next, 40, law = law)

    private fun shop(five: Boolean = true, closed: Boolean = true, lines: Int = 3, paid: Int = 3, sent: Int = 3, staff: Int = 3, warn: Int = 0) =
        ShopGlance("p1", "우리 가게", staff, five, closed, lines, paid, sent, LocalDate.of(2026, 11, 10), warn)

    @Test fun allDoneEarlyInTheMonthIsQuiet() {
        assertEquals(emptyList<ManageDesk.Todo>(), ManageDesk.todos(LocalDate.of(2026, 11, 3), listOf(team()), listOf(shop())))
    }

    @Test fun dangerAndUnconfirmedComeFirst() {
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 20), listOf(team(confirmed = false, empty = 12, danger = 2, short = 3)), listOf(shop(sent = 1)))
        assertTrue(l.take(2).all { it.urgent })
        assertTrue(l[0].text, l[0].text.contains("기준 넘김 2건"))
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
        // 2.0.0 [RVJ-73] (PC-176) Made with 12월 confirmed and 근무별 인원 set: this month's every day is short, still not a to-do.
        val made = TeamGlance("t1", "6병동", 5, false, 0, 150, 0, 30, true, 0, painted = 0)
        assertEquals(emptyList<ManageDesk.Todo>(), ManageDesk.todos(LocalDate.of(2026, 11, 20), listOf(made), emptyList()))
        assertEquals(1, ManageDesk.todos(LocalDate.of(2026, 11, 20), listOf(made.copy(painted = 3)), emptyList()).count { it.key.startsWith("team-short") })
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

    /**
     * 2.0.0 [RVJ-50] 검토 PC-118: an urgent to-do is a 주의; only a team's 주 52시간 weeks are a 위험 (red). [RVJ-68] (총괄 HQ-107) 휴식·연속
     * are the team's 기준, a 주의 like the board's 확인할 것.
     */
    @Test fun onlyTheTeamsSignsAreADanger() {
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 26), listOf(team(confirmed = false, empty = 12, danger = 2, law = 1)), listOf(shop(five = false, closed = false)))
        assertEquals(listOf("team-danger"), l.filter { it.danger }.map { it.key.substringBefore("-t1") })
        assertEquals("7병동 11월: 주 52시간 넘김 1건 · 기준 넘김 1건(휴식·연속)", l.single { it.danger }.text)
        assertTrue("마감·확정·5인 이상은 주의", l.filter { it.urgent && !it.danger }.size >= 3)
        val rules = ManageDesk.todos(LocalDate.of(2026, 11, 26), listOf(team(danger = 3)), emptyList()).single { it.key.startsWith("team-danger") }
        assertEquals(true to false, rules.urgent to rules.danger)
    }

    /** 2.0.0 [RVJ-95] 검토 U05c-01·I05ca-03: 신고 기한 a week ahead, a past one not ticked, and each opens its own card. */
    @Test fun dueFilingsOfTheWeekAreToDos() {
        val today = LocalDate.of(2026, 11, 6)
        val s = shop().copy(dues = listOf(
            ManageDesk.DueSoon("withhold:p1:2026-11", LocalDate.of(2026, 11, 10), "원천세 신고·납부 (10월 지급분)"),
            ManageDesk.DueSoon("in-health:a", LocalDate.of(2026, 11, 13), "건강보험 자격 취득 신고 · 박마바"),
            ManageDesk.DueSoon("in-rest:a", LocalDate.of(2026, 12, 15), "국민연금·고용보험·산재보험 취득 신고 · 박마바"),
            ManageDesk.DueSoon("settle:b", LocalDate.of(2026, 11, 4), "퇴사 정산 · 최사아"),
            ManageDesk.DueSoon("brief:p1", today, "근로소득 간이지급명세서 (10월 지급분)"),
        ))
        val l = ManageDesk.todos(today, emptyList(), listOf(s)).filter { it.key.startsWith("shop-due") }
        // 7일 안(11/13까지)과 지난 것만: 12/15는 아직이에요.
        assertEquals(4, l.size)
        assertTrue(l.none { it.text.contains("국민연금") })
        val tax = l.single { it.text.contains("원천세") }
        assertEquals("우리 가게: 원천세 신고·납부 (10월 지급분) · 11월 10일까지 · 4일 남음", tax.text)
        assertEquals("4일 남음은 아직 주의 아님", false, tax.urgent)
        val health = l.single { it.text.contains("건강보험") }
        assertEquals(false, health.urgent)
        val past = l.single { it.text.contains("퇴사 정산") }
        assertTrue(past.text, past.text.endsWith("11월 4일까지였어요 · 2일 지났어요") && past.urgent)
        val now = l.single { it.text.contains("간이지급명세서") }
        assertTrue(now.text, now.text.endsWith("11월 6일 오늘까지") && now.urgent)
        assertTrue(l.all { it.open == ManageDesk.Open.FILINGS && !it.danger })
        // 3일 남음은 주의.
        assertTrue(ManageDesk.todos(LocalDate.of(2026, 11, 7), emptyList(), listOf(s)).single { it.text.contains("원천세") }.urgent)
    }

    /** [RVJ-95] A 근로계약서 left unsigned is asked about after [ManageDesk.CONTRACT_DAYS] days; it opens that person's contract. */
    @Test fun anUnsignedContractIsAskedAboutAfterAFewDays() {
        val s = shop().copy(unsigned = listOf(
            ManageDesk.Unsigned("a", "박마바", LocalDate.of(2026, 11, 2), expired = false),
            ManageDesk.Unsigned("b", "최사아", LocalDate.of(2026, 11, 4), expired = false),
            ManageDesk.Unsigned("c", "김하늘", LocalDate.of(2026, 10, 20), expired = true),
        ))
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 6), emptyList(), listOf(s)).filter { it.key.startsWith("shop-contract") }
        assertEquals(listOf("a", "c"), l.map { it.ref })
        assertEquals("우리 가게: 박마바님 근로계약서 서명을 4일째 기다려요", l[0].text)
        assertEquals("우리 가게: 김하늘님 근로계약서 서명 링크 기한이 지났어요 · 다시 보내요", l[1].text)
        assertTrue(l.all { it.open == ManageDesk.Open.CONTRACT && !it.urgent })
    }

    /** [RVJ-95] A 대타 someone said yes to waits on one press: urgent, its month, its card; a day gone by is not asked. */
    @Test fun aCoverSomeoneSaidYesToIsAToDo() {
        val s = shop().copy(coverYes = listOf(
            ManageDesk.CoverYes("k1", LocalDate.of(2026, 11, 8), "박마바", listOf("최사아")),
            ManageDesk.CoverYes("k2", LocalDate.of(2026, 12, 1), "최사아", listOf("박마바", "김하늘", "이바다")),
            ManageDesk.CoverYes("k3", LocalDate.of(2026, 11, 5), "김하늘", listOf("최사아", "박마바")),
        ))
        val l = ManageDesk.todos(LocalDate.of(2026, 11, 6), emptyList(), listOf(s)).filter { it.key.startsWith("shop-cover") }
        assertEquals(listOf("k1", "k2"), l.map { it.ref })
        assertEquals("우리 가게 11/8 박마바님 대타: 최사아님이 들어갈 수 있어요", l[0].text)
        assertEquals("우리 가게 12/1 최사아님 대타: 박마바님 외 2명이 들어갈 수 있어요", l[1].text)
        assertEquals(java.time.YearMonth.of(2026, 12), l[1].month)
        assertTrue(l.all { it.urgent && it.open == ManageDesk.Open.COVER })
        // 할 일 맨 위(급한 것 먼저).
        assertTrue(ManageDesk.todos(LocalDate.of(2026, 11, 6), emptyList(), listOf(s.copy(warnings = 2))).first().key.startsWith("shop-cover"))
    }
}
