package com.bamnat.app.ui.settings

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVO-02][RVO-03] 검토 U11a-01·SET-05·T11a-14·T11a-07: a calendar's pages say whose they are, and the phone's pages go by 내 달력;
 * the alarm card's button reads in the work's own word.
 */
class SettingsCalendarBandTest {
    @Test fun theBandSaysWhoseCalendarThePageSetsUp() {
        assertEquals("알바 달력의 알람", calendarBandLine("알바 달력", "알람"))
        assertEquals("가게 장부의 매출과 경비", calendarBandLine("가게 장부", SettingsPage.PAY.titleFor(WorkStyle.BUSINESS)))
    }

    @Test fun onlyTheCalendarsPagesFollowTheCalendarOnScreen() {
        assertEquals(setOf(SettingsPage.WORK, SettingsPage.ALARM, SettingsPage.PAY), CALENDAR_PAGES)
        listOf(SettingsPage.NOTICES, SettingsPage.CALENDAR, SettingsPage.DATA, SettingsPage.PRIVACY).forEach { assertFalse(it.name, it in CALENDAR_PAGES) }
        assertTrue(SettingsPage.entries.all { it in CALENDAR_PAGES || it.titleFor(WorkStyle.BUSINESS) == it.title })
    }

    @Test fun theAlarmCardsButtonIsInTheWorksWords() {
        assertEquals("근무 종류", alarmKindsButton(WorkStyle.SHIFT))
        assertEquals("근무 종류", alarmKindsButton(null))
        assertEquals("영업시간", alarmKindsButton(WorkStyle.BUSINESS))
        assertEquals("일정 종류", alarmKindsButton(WorkStyle.FIELD))
        assertEquals("투자 알람", alarmKindsButton(WorkStyle.INVEST))
        WorkStyle.entries.filter { it != WorkStyle.SHIFT }.forEach { assertFalse(it.name, "근무 종류" == alarmKindsButton(it) && it != WorkStyle.DAY && it != WorkStyle.HOURLY) }
    }
}
