package com.bamnat.app.ui.calendar

import androidx.compose.runtime.saveable.SaverScope
import com.bamnat.core.schedule.TeamRoster
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** 2.0.0 검토 갈래 H: 달력 쪽 재현 시험(검토 01-달력). */
class ReviewHCalendarTest {
    /** [RVH-03] 검토 CAL-04: '그 주만 보기' starts its week where the month view does. */
    @Test fun theWeekOnlyViewStartsOnTheMonthViewsFirstDay() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(sunday, weekFirstDay(sunday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 9), weekFirstDay(sunday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 15), weekFirstDay(LocalDate.of(2026, 11, 21), DayOfWeek.SUNDAY))
        assertEquals("11월 셋째 주", weekOfMonthLabel(sunday, DayOfWeek.SUNDAY))
        // With Monday first, 11-15 (Sunday) ends the third row (10/26–11/1, 11/2–8, 11/9–15).
        assertEquals("11월 셋째 주", weekOfMonthLabel(sunday, DayOfWeek.MONDAY))
        // 2026-11-01 is a Sunday: with Monday first it sits on the first row with October's last days.
        assertEquals("11월 첫째 주", weekOfMonthLabel(LocalDate.of(2026, 11, 1), DayOfWeek.MONDAY))
    }

    /** [RVH-12] 검토 CAL-17: the 조 설정 being changed keeps through a turn of the phone (it is saved, then read back the same). */
    @Test fun theTeamSetupBeingChangedKeepsThroughATurn() {
        val roster = TeamRoster(listOf(TeamRoster.Team("A조", 0, true), TeamRoster.Team("B|조", 2, false), TeamRoster.Team("C조", 5, false)))
        val saved = with(TeamRosterSaver) { SaverScope { true }.save(roster) }!!
        assertEquals(roster, TeamRosterSaver.restore(saved))
    }

    /** [RVH-16] 검토 CAL-21: the 달력 모양 example week is in the calendar's own days, not 주·야·휴·대근 for a shop. */
    @Test fun theLookExampleWeekIsInTheCalendarsOwnDays() {
        assertTrue(sampleDays(WorkStyle.SHIFT).any { it.label == "야" })
        for (style in listOf(WorkStyle.DAY, WorkStyle.HOURLY, WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST)) {
            val days = sampleDays(style)
            assertFalse("$style", days.any { it.label == "야" || "대근" in it.memos })
            assertEquals("$style", 8, days.size)
        }
        assertTrue(sampleDays(WorkStyle.HOURLY).any { "대타" in it.memos })
    }

    /** [RVH-17] 검토 CAL-22: the 하루 화면 순서 dialog lists only the groups the calendar's day has, in its words. */
    @Test fun theDayOrderListsTheCalendarsOwnGroups() {
        val all = DayGroupKey.order(emptyList(), moneyFirst = true)
        assertEquals(listOf("돈", "메모", "영업", "알람", "몸", "기록"),
            all.filter { DayGroupKey.has(it, WorkStyle.BUSINESS) }.map { DayGroupKey.label(it, WorkStyle.BUSINESS) })
        assertEquals("매매", DayGroupKey.label(DayGroupKey.WORK, WorkStyle.INVEST))
        assertEquals("일정 더하기", DayGroupKey.label(DayGroupKey.EXTRA, WorkStyle.FIELD))
        assertTrue(DayGroupKey.has(DayGroupKey.LEAVE, WorkStyle.SHIFT))
        assertFalse(DayGroupKey.has(DayGroupKey.EXTRA, WorkStyle.INVEST))
    }
}
