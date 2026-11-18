package com.bamnat.app.ui.work

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.CalendarBook
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.work.ShopDay
import com.bamnat.core.work.ShopRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * [RVH-36] 검토 WORK-23·TYPE-23 검토 I02b-03 A shop's week as it really is: a bar that closes at two in the morning gets a short day of
 * half its hours (not one hour), a Saturday that closes early is 단축영업 every week, the break leaves the day kinds' hours, a
 * Sunday off every other week goes onto the calendar, and all of it comes back from the saved calendar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShopHoursTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val today = LocalDate.of(2026, 11, 16) // a Monday

    @Before fun ready() {
        val end = System.currentTimeMillis() + 60_000
        while (runBlocking { c.scheduleRepository.shiftTypes.first().isEmpty() }) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for the first shifts" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun types() = runBlocking { c.scheduleRepository.shiftTypes.first() }
    private fun type(name: String) = types().first { !it.isOff && it.name == name }

    @Test fun aBarThatClosesAfterMidnightGetsAShortDayOfHalfItsHours() = runBlocking {
        val rules = ShopRules(open = LocalTime.of(17, 0), close = LocalTime.of(2, 0))
        ShopKeeper.apply(c, PersonalCalendar.MAIN, rules, today)
        val short = type(ShopKeeper.SHORT_NAME)
        assertEquals(LocalTime.of(17, 0), short.start)
        assertEquals(LocalTime.of(21, 30), short.end)
    }

    @Test fun aWeekOfShortSaturdaysABreakAndSundaysOffEveryOtherWeek() = runBlocking {
        val rules = ShopRules(open = LocalTime.of(11, 0), close = LocalTime.of(22, 0), breakStart = LocalTime.of(15, 0), breakMinutes = 120)
            .withDay(DayOfWeek.SATURDAY, ShopDay.SHORT, today).copy(shortClose = LocalTime.of(16, 0))
            .withDay(DayOfWeek.SUNDAY, ShopDay.EVERY_OTHER, today)
            .withDay(DayOfWeek.MONDAY, ShopDay.CLOSED, today)
        ShopKeeper.apply(c, PersonalCalendar.MAIN, rules, today)
        val open = type(ShopKeeper.OPEN_NAME)
        assertEquals(120, open.breakMinutes)
        assertEquals(LocalTime.of(15, 0), open.breakStart)
        val short = type(ShopKeeper.SHORT_NAME)
        assertEquals(LocalTime.of(11, 0) to LocalTime.of(16, 0), short.start to short.end)
        assertEquals("a short day that closes at 16 has no 15~17 break", 0, short.breakMinutes)

        val snap = c.scheduleRepository.snapshot.first()
        val sat = today.with(DayOfWeek.SATURDAY)
        assertEquals(short.id, snap.resolver.resolve(sat).shiftType?.id)
        assertEquals(open.id, snap.resolver.resolve(today.plusDays(1)).shiftType?.id)
        assertTrue("Monday is closed every week", !snap.resolver.resolve(today).isWorkDay)
        // This week's Sunday is closed, next week's is open, the one after closed again.
        val sun = today.with(DayOfWeek.SUNDAY)
        val byDate = snap.entries.associateBy { it.date }
        assertEquals(ShiftOverride.Off, byDate[sun]?.override)
        assertTrue(byDate[sun.plusWeeks(1)]?.override != ShiftOverride.Off)
        assertEquals(ShiftOverride.Off, byDate[sun.plusWeeks(2)]?.override)

        // A short day to 18 holds the break.
        ShopKeeper.apply(c, PersonalCalendar.MAIN, rules.copy(shortClose = LocalTime.of(18, 0)), today)
        assertEquals(120, type(ShopKeeper.SHORT_NAME).breakMinutes)
        // The break off again: both day kinds lose it.
        ShopKeeper.apply(c, PersonalCalendar.MAIN, rules.copy(breakStart = null, breakMinutes = 0), today)
        assertEquals(0, type(ShopKeeper.OPEN_NAME).breakMinutes)
        assertNull(type(ShopKeeper.SHORT_NAME).breakStart)
    }

    @Test fun theShopsWeekComesBackFromTheSavedCalendar() {
        val rules = ShopRules(
            open = LocalTime.of(17, 0), close = LocalTime.of(2, 0), closedWeekdays = setOf(DayOfWeek.MONDAY),
            shortWeekdays = setOf(DayOfWeek.SATURDAY), shortOpen = LocalTime.of(12, 0), shortClose = LocalTime.of(20, 0),
            breakStart = LocalTime.of(15, 0), breakMinutes = 90, everyOther = setOf(DayOfWeek.SUNDAY), everyOtherFrom = today,
        )
        assertEquals(rules, CalendarBook.shopOf(CalendarBook.shopJson(rules)))
        // A shop saved before 2.0.0 reads as it was, with none of the new parts.
        val old = org.json.JSONObject().put("open", "10:00").put("close", "22:00").put("closed", "SUNDAY").put("monthly", "").put("holidays", false).put("auto", true)
        assertEquals(ShopRules(closedWeekdays = setOf(DayOfWeek.SUNDAY)), CalendarBook.shopOf(old))
    }

    @Test fun thePastKeepsTheHoursItHadWhenTheShopChangesThem() = runBlocking {
        ShopKeeper.apply(c, PersonalCalendar.MAIN, ShopRules(open = LocalTime.of(10, 0), close = LocalTime.of(20, 0)), today.minusDays(30))
        ShopKeeper.apply(c, PersonalCalendar.MAIN, ShopRules(open = LocalTime.of(11, 0), close = LocalTime.of(22, 0)), today)
        val open = type(ShopKeeper.OPEN_NAME)
        assertEquals(LocalTime.of(10, 0), open.on(today.minusDays(3)).start)
        assertEquals(LocalTime.of(11, 0), open.on(today).start)
    }
}
