package com.bamnat.app.ui.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.ui.MONTH_SWIPE_DP
import com.bamnat.app.ui.monthSwipeStep
import com.bamnat.app.ui.onboarding.QuietStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.39 [CALX-07][CALX-08] (사용자 2026-11-08): 새로 설정한 폰은 달력만 보기로 시작해 처음 달력에서 한 번 고르고, 전에 쓰던 폰은 보던 대로.
 * 근무·급여·가계부는 옆으로 밀어 달을 넘겨요.
 */
@RunWith(AndroidJUnit4::class)
class CalendarViewChoiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @After fun clear() {
        prefs.edit().clear().commit()
    }

    @Test fun aFreshSetupStartsOnTheCalendarAloneAndAsksOnce() {
        val s = QuietStart.newView(AppSettings())
        assertTrue(s.calendarOnly)
        assertFalse(s.viewAsked)
    }

    @Test fun aPhoneThatHadTheAppKeepsItsViewAndIsNotAsked() {
        val s = SettingsRepository(context).current
        assertFalse(s.calendarOnly)
        assertTrue(s.viewAsked)
    }

    @Test fun theAnswerIsKept() {
        val repo = SettingsRepository(context)
        repo.update { QuietStart.newView(it) }
        assertFalse(SettingsRepository(context).current.viewAsked)
        repo.update { it.copy(calendarOnly = false, viewAsked = true) }
        val again = SettingsRepository(context).current
        assertFalse(again.calendarOnly)
        assertTrue(again.viewAsked)
    }

    @Test fun theLineSaysWhatIsOnScreenAndTheOtherWay() {
        val only = calendarViewAskWords(calendarOnly = true)
        assertEquals("카드도 함께 보기", only.change)
        assertTrue(only.body.contains("나중에"))
        assertEquals("달력만 보기", calendarViewAskWords(calendarOnly = false).change)
    }

    @Test fun aSidewaysSwipeTurnsTheMonth() {
        val need = MONTH_SWIPE_DP.toFloat()
        assertEquals(1, monthSwipeStep(-need - 1f, need))
        assertEquals(-1, monthSwipeStep(need + 1f, need))
        assertNull(monthSwipeStep(need / 2, need))
        assertNull(monthSwipeStep(0f, need))
    }
}
