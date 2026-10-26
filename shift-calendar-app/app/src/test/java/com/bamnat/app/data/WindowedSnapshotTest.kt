package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.work.CalendarFocus
import com.bamnat.core.work.MonthOverview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PERF-07][PERF-08] 1.28 AO: the calendar's first month drawn from the months around it gives the same days and the
 * same totals as the whole history; the whole history replaces it; several calendars keep their own days; the
 * shared reading follows writes and a one-off read after a write still sees the write.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WindowedSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbs = mutableListOf<AppDatabase>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val month = YearMonth.of(2026, 10)

    private fun repo(): ScheduleRepository {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build().also { dbs += it }
        return ScheduleRepository(db, shareScope = scope)
    }

    @After fun close() {
        scope.cancel()
        dbs.forEach { it.close() }
    }

    /** Three years of a 주주야야휴 rotation with a changed day every third day, overtime and leave, nights over month edges. */
    private suspend fun ScheduleRepository.history(nightId: (Map<String, Long>) -> Long = { it.getValue("야간") }) {
        ensureDefaults()
        val types = shiftTypes.first().associate { it.name to it.id }
        savePattern(WorkPattern.Rotation(LocalDate.of(2023, 10, 1), listOf("주간", "주간", "야간", "야간", "휴무").map(types::getValue)))
        val start = LocalDate.of(2023, 10, 1)
        val days = generateSequence(start) { it.plusDays(3) }.takeWhile { it < LocalDate.of(2027, 3, 1) }.toList()
        updateDays(days) { it.copy(override = ShiftOverride.Shift(nightId(types))) }
        val extra = generateSequence(start.plusDays(1)) { it.plusDays(4) }.takeWhile { it < LocalDate.of(2027, 3, 1) }.toList()
        updateDays(extra) { it.copy(extras = it.extras + ExtraEntry(ExtraKind.OVERTIME_ID, LocalTime.of(18, 0), LocalTime.of(20, 30))) }
        // A night on the last day of each month around the window runs into the next month.
        for (m in -3..3) updateDay(month.plusMonths(m.toLong()).atEndOfMonth()) { it.copy(override = ShiftOverride.Shift(types.getValue("야간"))) }
        setLeave(month.atDay(14), Leave())
    }

    private val profile = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2023, 1, 1), 3_000_000))))

    @Test fun theWindowGivesTheSameDaysAndTotalsAsTheWholeHistory() = runBlocking {
        val r = repo()
        r.history()
        val whole = r.snapshot.first()
        val window = r.windowSnapshot(month)
        assertFalse(window.isComplete)
        assertTrue(whole.isComplete)
        assertTrue("fewer days read", window.entries.size < whole.entries.size)
        assertEquals(whole.shiftTypes, window.shiftTypes)
        assertEquals(whole.patterns, window.patterns)
        // The month on screen and one swipe either way: what the calendar may draw from the window.
        for (m in listOf(month.minusMonths(1), month, month.plusMonths(1))) {
            assertTrue(window.covers(m))
            assertEquals("days of $m", whole.resolver.resolveRange(m.atDay(1), m.atEndOfMonth()), window.resolver.resolveRange(m.atDay(1), m.atEndOfMonth()))
            assertEquals("overview of $m", MonthOverview.of(m, CalendarFocus.SHIFTS, whole.resolver), MonthOverview.of(m, CalendarFocus.SHIFTS, window.resolver))
            // The change line compares with the month before.
            val prev = m.minusMonths(1)
            assertEquals("versus of $m", MonthOverview.of(m, CalendarFocus.SHIFTS, whole.resolver).versus(MonthOverview.of(prev, CalendarFocus.SHIFTS, whole.resolver)),
                MonthOverview.of(m, CalendarFocus.SHIFTS, window.resolver).versus(MonthOverview.of(prev, CalendarFocus.SHIFTS, window.resolver)))
            assertEquals("hours of $m", WorkHoursCalculator(whole.resolver).month(m), WorkHoursCalculator(window.resolver).month(m))
            val calc = PayCalculator()
            assertEquals("pay of $m", calc.calculate(profile, m, whole.resolver::resolve).total, calc.calculate(profile, m, window.resolver::resolve).total)
        }
        // Outside it the window does not claim to know.
        assertFalse(window.covers(month.minusMonths(3)))
        assertFalse(window.covers(month.plusMonths(3)))
        assertTrue(window.covers(month.minusMonths(2).atDay(1)))
    }

    @Test fun theWholeHistoryReplacesTheWindowAndNothingFollowsIt() = runBlocking {
        val r = repo()
        r.history()
        val seen = withTimeout(10_000) { r.snapshotAround(month).takeWhile { !it.isComplete }.toList() }
        assertTrue("at most the window comes before the whole: $seen", seen.size <= 1)
        val all = withTimeout(10_000) { r.snapshotAround(month).take(seen.size + 1).toList() }
        assertTrue("the last value is the whole history", all.last().isComplete)
        val window = all.firstOrNull { !it.isComplete }
        if (window != null) {
            assertEquals(all.last().resolver.resolveRange(month.atDay(1), month.atEndOfMonth()), window.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()))
        }
    }

    @Test fun onceTheWholeHistoryIsReadTheWindowIsNotSent() = runBlocking {
        val r = repo()
        r.history()
        // Something already follows the schedule (the alarms, the notices): its reading is shared at once.
        val follower = scope.launch { r.live.collect { } }
        withTimeout(10_000) { r.live.first() }
        val first = withTimeout(10_000) { r.snapshotAround(month).first() }
        assertTrue("the shared whole history comes first", first.isComplete)
        follower.cancel()
    }

    @Test fun anotherMonthHasItsOwnWindow() = runBlocking {
        val r = repo()
        r.history()
        val later = month.plusMonths(5)
        val w = r.windowSnapshot(later)
        assertTrue(w.covers(later.minusMonths(1)) && w.covers(later.plusMonths(1)))
        assertFalse("the first month's window is not this one", w.covers(month))
        val whole = r.snapshot.first()
        assertEquals(whole.resolver.resolveRange(later.atDay(1), later.atEndOfMonth()), w.resolver.resolveRange(later.atDay(1), later.atEndOfMonth()))
    }

    @Test fun eachCalendarReadsItsOwnDays() = runBlocking {
        val main = repo()
        val added = repo()
        main.history()
        added.history(nightId = { it.getValue("주간") })
        val a = main.windowSnapshot(month)
        val b = added.windowSnapshot(month)
        assertEquals(main.snapshot.first().resolver.resolveRange(month.atDay(1), month.atEndOfMonth()), a.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()))
        assertEquals(added.snapshot.first().resolver.resolveRange(month.atDay(1), month.atEndOfMonth()), b.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()))
        assertFalse("two calendars, two schedules", a.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()) == b.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()))
    }

    @Test fun theSharedReadingFollowsWritesAndAOneOffReadSeesItsWrite() = runBlocking {
        val r = repo()
        r.history()
        val follower = scope.launch { r.live.collect { } }
        val day = month.atDay(20)
        r.setLeave(day, null)
        r.setLeave(day, Leave())
        // A one-off read right after a write (CompanyShare, the widgets) reads afresh.
        assertEquals(Leave(), r.snapshot.first().resolver.entryOn(day)?.leave)
        // The shared reading catches up with it.
        withTimeout(10_000) { r.live.first { it.resolver.entryOn(day)?.leave == Leave() } }
        r.setLeave(day, null)
        withTimeout(10_000) { r.live.first { it.resolver.entryOn(day)?.leave == null } }
        assertNull(r.snapshot.first().resolver.entryOn(day)?.leave)
        follower.cancel()
    }
}
