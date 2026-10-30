package com.bamnat.core.recipe

import com.bamnat.core.recipe.ThemeLease.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FREE-10] 테마 기간: 30 days, a whole ad for 30 more, 아니요 keeps the history, never for mine, 프로 or with ads off. */
class ThemeLeaseTest {
    private val theme = ThemeLease.Wear("벚꽃 파스텔 교대", "builtin:벚꽃 파스텔 교대", "{}", 0, 0)
    private val day = 20_000L

    @Test fun runsThirtyDaysThenAsks() {
        val l = ThemeLease().wear(theme, day, free = true)
        assertEquals(State.OK, l.state(day, adsOn = true, pro = false))
        assertEquals(State.SOON, l.state(day + 27, adsOn = true, pro = false))
        assertEquals(State.OVER, l.state(day + 30, adsOn = true, pro = false))
        assertTrue(l.ask(day + 30, adsOn = true, pro = false))
        // Closed without an answer: the theme stays, asked again another day.
        val later = l.later(day + 30)
        assertFalse(later.ask(day + 30, adsOn = true, pro = false))
        assertTrue(later.ask(day + 31, adsOn = true, pro = false))
        assertEquals(theme.title, later.worn?.title)
    }

    @Test fun nothingRunsForMineProOrAdsOff() {
        val l = ThemeLease().wear(theme, day, free = true)
        assertEquals(State.NONE, l.state(day + 40, adsOn = false, pro = false))
        assertEquals(State.NONE, l.state(day + 40, adsOn = true, pro = true))
        val mine = ThemeLease().wear(theme.copy(code = "", own = true), day, free = false)
        assertEquals(State.NONE, mine.state(day + 400, adsOn = true, pro = false))
        assertFalse(ThemeLease().needsAd(own = true, adsOn = true, pro = false))
        assertFalse(ThemeLease().needsAd(own = false, adsOn = true, pro = true))
    }

    @Test fun firstTwoFreeThenAnAd() {
        var l = ThemeLease()
        assertFalse(l.needsAd(own = false, adsOn = true, pro = false))
        l = l.wear(theme, day, free = true).wear(theme.copy(title = "오션", code = "builtin:오션"), day, free = true)
        assertTrue(l.needsAd(own = false, adsOn = true, pro = false))
        assertEquals("같은 테마는 기록에 한 번", 2, l.wear(theme, day + 1, free = false).history.size)
    }

    @Test fun extendAndNoAdAndTakeOff() {
        val l = ThemeLease().wear(theme, day, free = true)
        assertEquals(day + 60, l.extend(day + 30).worn?.until)
        // Extended early, the days left are kept.
        assertEquals(day + 60, l.extend(day + 10).worn?.until)
        val n = l.noAd(day + 30, "2026-12-01", "no_fill").noAd(day + 30, "2026-12-01", "no_fill").noAd(day + 30, "2026-12-01", "weird")
        assertEquals(2, n.freeLog["2026-12-01|no_fill"])
        assertEquals(1, n.freeLog["2026-12-01|other"])
        assertEquals(day + 30 + 3 * ThemeLease.NO_AD_DAYS, n.worn?.until)
        val off = l.takeOff()
        assertNull(off.worn)
        assertEquals("기록은 남아요", theme.title, off.history.first().title)
    }

    @Test fun proEndingGivesThirtyDays() {
        val l = ThemeLease().wear(theme, day, free = true)
        assertEquals(day + 100 + 30, l.proEnded(day + 100).worn?.until)
    }

    @Test fun writtenAndReadBack() {
        val l = ThemeLease().wear(theme.copy(body = "{\"title\":\"탭\tand\nline\"}"), day, free = true).noAd(day, "2026-12-01", "offline").later(day)
        assertEquals(l, ThemeLease.decode(ThemeLease.encode(l)))
        assertEquals(ThemeLease(), ThemeLease.decode(""))
    }
}
