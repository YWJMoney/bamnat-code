package com.bamnat.app.recipe

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PC 확인 0efe9dbc: a look with colours but no kinds of day draws the kind of work's example days with their letters. */
class ThemeSwatchTest {
    @Test fun aLookWithoutDaysTakesTheExampleWeek() {
        val look = ThemeSwatch(bg = 0xFFFFF8EE, accent = 0xFF2E7D32).withDays(WorkStyle.HOURLY)
        assertTrue(look.example)
        assertEquals(listOf("일", "쉼"), look.days.map { it.label })
        assertEquals(0xFFFFF8EE, look.bg)
        assertEquals(listOf("주", "야", "휴"), ThemeSwatch().withDays(WorkStyle.SHIFT).days.map { it.label })
    }

    @Test fun aLookWithItsOwnDaysKeepsThem() {
        val own = ThemeSwatch(days = listOf(ThemeSwatch.Day(0xFF000000, "A", null, "day")))
        assertEquals(own, own.withDays(WorkStyle.FIELD))
        assertFalse(own.withDays(WorkStyle.FIELD).example)
    }
}
