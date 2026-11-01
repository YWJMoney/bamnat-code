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

/** PC 확인 f39bcfbe: 이달의 테마's picks bring their looks, read into the contest. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ContestLooksTest {
    @Test fun picksCarryTheirLooks() {
        val o = org.json.JSONObject("""{"month":"2026-11","theme":null,"entries":0,"mine":[],
            "last":{"month":"2026-10","theme":"t","picks":[{"code":"AAAA1111","title":"a","look":{"bg":"FF000000"}},{"code":"BBBB2222","title":"b"}]}}""")
        val k = com.bamnat.app.company.contestOf(o)
        assertEquals(listOf("AAAA1111" to "a", "BBBB2222" to "b"), k.picks)
        assertEquals(setOf("AAAA1111"), k.pickLooks.keys)
        assertEquals(0xFF000000, k.pickLooks.getValue("AAAA1111").bg)
    }
}
