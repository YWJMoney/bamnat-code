package com.bamnat.app.ui.settings

import com.bamnat.app.data.AppSettings
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVIB-21] 둘째 구간 알림 after 알림 한눈에 became the page ([RVO-22]): the board lists it, and 설정 찾기 finds it, only where a
 * 근무 종류 has a 브레이크 타임·비는 시간; its words are the kind of work's.
 */
class GapRowPlaceTest {
    private val settings = AppSettings()

    @Test fun onTheBoardOnlyWithAGap() {
        assertFalse(NoticeBoard.shown(settings, WorkStyle.BUSINESS).any { it.key == NoticeBoard.GAP_KEY })
        val row = NoticeBoard.shown(settings, WorkStyle.BUSINESS, hasGap = true).single { it.key == NoticeBoard.GAP_KEY }
        assertEquals(GAP_ROW, row.label(WorkStyle.BUSINESS))
        assertTrue(row.does(WorkStyle.BUSINESS).startsWith("다시 시작 10분 전에 소리 없이 알려 줘요. 브레이크 타임이 있는 날만이에요."))
        assertTrue(row.does(WorkStyle.SHIFT).contains("비는 시간이 있는 날만"))
        // An investor's days have no parts.
        assertFalse(NoticeBoard.shown(settings, WorkStyle.INVEST, hasGap = true).any { it.key == NoticeBoard.GAP_KEY })
    }

    @Test fun foundOnlyWhereThePageDrawsIt() {
        assertFalse(SettingsIndex.entries(style = WorkStyle.HOURLY).any { it.title == GAP_ROW })
        assertTrue(SettingsIndex.entries(style = WorkStyle.HOURLY, gap = true).any { it.title == GAP_ROW })
        assertTrue(SettingsIndex.search("브레이크 타임", SettingsIndex.entries(style = WorkStyle.BUSINESS, gap = true)).any { it.title == GAP_ROW })
    }
}
