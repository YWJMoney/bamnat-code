package com.bamnat.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.bamnat.app.ui.calendar.CalendarToolSlots
import com.bamnat.app.ui.calendar.ToolFit
import com.bamnat.app.ui.components.SheetScrollGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [NAV-02][NAV-03][UX-10][UX-13] 1.25 Z: the parts under 396·397·399, each on its own. */
class ZPartsTest {
    /** [NAV-03] Every calendar the same tiles in the same places; what cannot be used now is grey, never gone. */
    @Test fun theToolTilesKeepTheirPlaces() {
        val fits = listOf(
            ToolFit(teams = true, receive = true),
            ToolFit(teams = true, receive = true, viewingReceived = true),
            ToolFit(teams = true, receive = true, hasShiftTypes = false, teamsReady = false),
        )
        val ids = fits.map { f -> CalendarToolSlots.build(f).map { it.id } }
        assertEquals("a received calendar or no shifts yet moves no tile", 1, ids.distinct().size)
        val viewing = CalendarToolSlots.build(fits[1]).associateBy { it.id }
        assertFalse(viewing.getValue(CalendarToolSlots.CUSTOMIZE).enabled)
        assertEquals("받은 달력을 보는 중", viewing.getValue(CalendarToolSlots.CUSTOMIZE).note)
        val empty = CalendarToolSlots.build(fits[2]).associateBy { it.id }
        assertEquals("근무 종류를 먼저 만들어요", empty.getValue(CalendarToolSlots.TYPE_MONTH).note)
        assertEquals("근무 순서를 정하면 써요", empty.getValue(CalendarToolSlots.TEAMS).note)
        // The ways to settings and help come last, apart: never in a tool's place.
        for (f in fits + ToolFit()) {
            val slots = CalendarToolSlots.build(f)
            assertEquals(listOf(CalendarToolSlots.VIEW_SETTINGS, CalendarToolSlots.HELP), slots.filter { it.away }.map { it.id })
            assertTrue(slots.takeWhile { !it.away }.size == slots.count { !it.away })
        }
        // A day worker's calendar has no 모든 조 and no 받기 at all, and 화면 꾸미기 is still the last tool.
        val day = CalendarToolSlots.build(ToolFit()).filter { !it.away }.map { it.id }
        assertFalse(CalendarToolSlots.TEAMS in day)
        assertEquals(CalendarToolSlots.CUSTOMIZE, day.last())
    }

    /** [NAV-02] Back from a tab goes to the tab before it; going round the tabs does not pile up. */
    @Test fun tabHistoryGoesBackOneTabAtATime() {
        TabHistory.clear()
        TabHistory.left("calendar", "money")
        TabHistory.left("money", "settings")
        assertEquals("money", TabHistory.previous())
        assertEquals("money", TabHistory.back())
        assertEquals("calendar", TabHistory.back())
        assertNull(TabHistory.back())
        // Round and round: the tab on screen is never in the list, and each other tab once.
        repeat(5) { TabHistory.left("calendar", "money"); TabHistory.left("money", "memos"); TabHistory.left("memos", "calendar") }
        assertEquals("memos", TabHistory.back())
        assertEquals("money", TabHistory.back())
        assertNull(TabHistory.back())
        TabHistory.clear()
    }

    /** [UX-10] The same inputs give the kept value back without working it out again. */
    @Test fun lastValuesAreKeptWithWhatTheyCameFrom() {
        LastValues.clear()
        assertFalse(LastValues.fresh("pay:main:2026-10", listOf(1, "a")))
        LastValues.put("pay:main:2026-10", listOf(1, "a"), 3_100_000L)
        assertTrue(LastValues.fresh("pay:main:2026-10", listOf(1, "a")))
        assertFalse(LastValues.fresh("pay:main:2026-10", listOf(2, "a")))
        assertEquals(3_100_000L, LastValues.value("pay:main:2026-10"))
        // Only so many screens are kept.
        repeat(100) { LastValues.put("k$it", emptyList(), it) }
        assertNull(LastValues.value("pay:main:2026-10"))
        assertEquals(99, LastValues.value("k99"))
        LastValues.clear()
    }

    /** [UX-13] A swipe that scrolled the day keeps its rest from the sheet; one from the top still moves it. */
    @Test fun aSwipeThatScrolledTheDayDoesNotCloseIt() = runBlocking {
        val guard = SheetScrollGuard()
        // From the top: the content had nothing to scroll, so the sheet gets the pull.
        assertEquals(Offset.Zero, guard.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput))
        assertEquals(Velocity.Zero, guard.onPostFling(Velocity.Zero, Velocity(0f, 2000f)))
        // Scrolling up the day, reaching its top in the same swipe: the rest and the fling stay here.
        assertEquals(Offset.Zero, guard.onPostScroll(Offset(0f, 120f), Offset.Zero, NestedScrollSource.UserInput))
        assertEquals(Offset(0f, 60f), guard.onPostScroll(Offset(0f, 10f), Offset(0f, 60f), NestedScrollSource.UserInput))
        assertEquals(Velocity(0f, 1800f), guard.onPostFling(Velocity.Zero, Velocity(0f, 1800f)))
        assertFalse(guard.scrolledThisSwipe)
        // Upward is never kept: the sheet can still open all the way.
        guard.onPostScroll(Offset(0f, -30f), Offset.Zero, NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, guard.onPostScroll(Offset.Zero, Offset(0f, -50f), NestedScrollSource.UserInput))
    }
}
