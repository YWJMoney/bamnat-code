package com.bamnat.app.imports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.RosterDraftStore
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.RosterReader
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.journal.RosterFrom
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVL-72] (검토 T07b-01·T07b-02·I07bb-02) 한 달 근무 넣기 with a 알바 시간표 or 현장 공수표: read from pasted text, kept in the draft,
 * put on the calendar as work at its times, and a 공수 as that day's 일당 일감 (one 되돌리기 takes both back).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimeSheetInTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)
    private val near = LocalDate.of(2026, 11, 12)

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
    }

    private val codes = DutyCodes(emptyList())

    @Test fun aPastedTimeSheetReadsAsTimesAndARosterOfCodesAsBefore() {
        val r = RosterReader.text("11/3 09:00-15:00\n11/4 9-3\n11/6 6h\n11/8 휴무", codes, near)
        assertEquals(nov, r.month)
        assertNull(r.error)
        assertEquals(DutyCell.Hours(9 * 60, 360), r.rows.single().cells[4])
        // 알바·현장: nothing found is said in the words of a 시간표.
        assertEquals(RosterReader.NOTHING_TIMED, RosterReader.text("아무 말", codes, near, timeFirst = true).error)
        assertEquals(RosterReader.NOTHING, RosterReader.text("아무 말", codes, near).error)
        assertTrue(RosterReader.looksLikeRoster("월 9-15\n수 9-15\n금 18-22", codes))
        assertTrue(RosterReader.text("1 2 3 4 5 6 7\n김민지 D D E E N N O", codes, near).rows.single().cells.values.none { it is DutyCell.Hours })
    }

    @Test fun timesWeekdaysAndUnitsComeBackFromTheDraft() {
        val row = RosterRow("김알바", mapOf(3 to DutyCell.Hours(9 * 60, 360), 4 to DutyCell.Hours(null, 300), 5 to DutyCell.Units(1.5)),
            weekly = mapOf(DayOfWeek.MONDAY to DutyCell.Hours(18 * 60, 240), DayOfWeek.SUNDAY to DutyCell.Off))
        val d = RosterDraftStore.Draft(nov, listOf(row), 0, mapOf(6 to DutyCell.Units(0.5), 7 to DutyCell.Hours(22 * 60, 480)), emptyMap(),
            1, RosterFrom.TEXT, nov, chosen = true, withOutside = true, at = Instant.parse("2026-11-12T00:00:00Z"))
        assertEquals(d, RosterDraftStore.decode(RosterDraftStore.encode(d)))
    }

    @Test fun aTimeSheetGoesOnTheCalendarAsWorkAtItsTimes() = runBlocking {
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readText("11/3 09:00-15:00\n11/5 18-23\n11/7 6h")
        idleUntil("the sheet read") { vm.ui.value.rows != null }
        val u = vm.ui.value
        assertEquals(nov, u.month)
        assertTrue(u.row!!.timed)
        assertTrue("days not worked are not marked as missed", u.doubtful.isEmpty() && u.missing.isEmpty())
        vm.apply()
        fun at(d: Int) = runBlocking { c.scheduleRepository.snapshot.first() }.let { s -> s.entries.firstOrNull { it.date == nov.atDay(d) } to s.resolver.resolve(nov.atDay(d)) }
        idleUntil("the times written") { at(5).first?.customStart == LocalTime.of(18, 0) }
        assertEquals(LocalTime.of(23, 0), at(5).first!!.customEnd)
        assertEquals(LocalTime.of(9, 0), at(3).first!!.customStart)
        assertTrue("a day of the 시간표 is a working day", at(3).second.isWorkDay && at(5).second.isWorkDay)
        // "6h" ends six hours after it starts.
        val seven = at(7).first!!
        assertEquals(6L * 60, java.time.Duration.between(seven.customStart, seven.customEnd).toMinutes().let { if (it < 0) it + 1440 else it })
    }

    @Test fun aUnitsSheetGoesInAsTheDaysJobsAndComesBackOut() = runBlocking {
        val money = c.calendars.money(PersonalCalendar.MAIN)
        money.book.first().jobs.filter { it.units != null }.forEach { money.delete(it) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readText("11월 공수\n성명 1 2 3 4 5\n홍길동 1 1 0.5 1 1.5")
        idleUntil("the sheet read") { vm.ui.value.rows != null }
        assertEquals(DutyCell.Units(0.5), vm.ui.value.cells[3])
        val firstSaid = com.bamnat.app.ui.RecentFill.entry.value
        vm.apply()
        idleUntil("the jobs written") { runBlocking { money.book.first() }.jobs.count { it.units != null && it.date.monthValue == 11 } == 5 }
        val jobs = money.book.first().jobs.filter { it.units != null }.associateBy { it.date.dayOfMonth }
        assertEquals(0.5, jobs.getValue(3).units!!, 0.0)
        assertEquals("1.5공", jobs.getValue(5).title)
        // One 되돌리기 takes the jobs back out.
        idleUntil("the fill said") { com.bamnat.app.ui.RecentFill.entry.value.let { it != null && it !== firstSaid } }
        val undo = com.bamnat.app.ui.RecentFill.entry.value!!
        undo.undo()
        idleUntil("the jobs gone") { runBlocking { money.book.first() }.jobs.none { it.units != null } }
    }
}
