package com.bamnat.app.imports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.imports.DutyRosterViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-74] (검토 I07ba-03) A roster fixed mid-month and sent again: only the days it changes are said before it goes in, and after it
 * the days changed, not the whole month again.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterAgainTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val dec = YearMonth.of(2026, 12)

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
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
    }

    private val days = (1..10).joinToString(" ")

    @Test fun theSameRosterAgainSaysOnlyTheDaysItChanges() = runBlocking {
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
        first.readText("2026년 12월 근무표\n$days\n주 주 야 야 휴 휴 주 주 야 야")
        idleUntil("the roster read") { first.ui.value.rows != null }
        first.apply()
        idleUntil("the month written") { runBlocking { c.scheduleRepository.snapshot.first() }.resolver.resolve(dec.atDay(10)).shiftType?.name == "야간" }
        // The same roster, two days fixed.
        val again = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
        again.readText("2026년 12월 근무표\n$days\n주 주 야 야 휴 휴 주 야 야 휴")
        idleUntil("the roster read again") { again.ui.value.rows != null }
        val snap = c.scheduleRepository.snapshot.first()
        val changed = DutyRosterViewModel.changes(again.ui.value, snap)
        assertEquals(listOf(8, 10), changed.map { it.first.dayOfMonth })
        assertEquals("주간" to "야간", changed[0].second to changed[0].third)
        val firstSaid = com.bamnat.app.ui.RecentFill.entry.value
        again.apply()
        idleUntil("the change written") { runBlocking { c.scheduleRepository.snapshot.first() }.resolver.resolve(dec.atDay(8)).shiftType?.name == "야간" }
        // The days are written before the line that says so.
        idleUntil("the change said") { com.bamnat.app.ui.RecentFill.entry.value.let { it != null && it !== firstSaid } }
        val said = com.bamnat.app.ui.RecentFill.entry.value!!.text
        assertTrue(said, said.startsWith("2026년 12월에서 바뀐 2일을 고쳤어요"))
    }

    /** 2.0.0 [RVL-75] (검토 I07ba-02) A roster whose weekdays are of the next month says so, and one tap moves it there. */
    @Test fun weekdaysOfAnotherMonthAreSaid() = runBlocking {
        val nov = dec.minusMonths(1)
        val names = listOf("일", "월", "화", "수", "목", "금", "토")
        val week = (1..10).joinToString(" ") { d -> names[dec.atDay(d).dayOfWeek.value % 7] }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readText("2026년 11월 근무표\n$days\n$week\n주 주 야 야 휴 휴 주 주 야 야")
        idleUntil("the roster read") { vm.ui.value.rows != null }
        assertEquals(com.bamnat.core.imports.WeekdayCheck.Verdict.OtherMonth(dec), vm.ui.value.weekdays)
        vm.weekdayMonth()
        assertEquals(dec, vm.ui.value.month)
        assertEquals(null, vm.ui.value.weekdays)
    }
}
