package com.bamnat.app.imports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.RosterDraftStore
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.journal.RosterFrom
import com.bamnat.core.model.LeaveKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVL-20] (검토 SCAN-22·U07b-15, 조건 10) 한 달 근무 넣기: what was typed or read is kept in no-backup storage until put on the
 * calendar or thrown away, so the app closed in the background brings it back, said once with 처음부터.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterDraftTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)
    private val dir get() = File(app.noBackupFilesDir, "roster_drafts")

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
        dir.deleteRecursively()
    }

    @Test fun everyCellAndLineComesBackAsItWas() {
        val row = RosterRow("김간호", mapOf(1 to DutyCell.Shift(3), 2 to DutyCell.Off, 3 to DutyCell.Leave(LeaveKind.ANNUAL, 2, true),
            4 to DutyCell.Unknown("병:가", leave = true)), guessed = setOf(1), notes = mapOf(1 to "*"), unsure = setOf(2), contested = setOf(3),
            line = 2, outside = mapOf(LocalDate.of(2026, 10, 31) to DutyCell.Off), y = 140, filled = setOf(4))
        val d = RosterDraftStore.Draft(nov, listOf(row), 0, mapOf(5 to DutyCell.Shift(1), 6 to null), mapOf("병가" to DutyCell.Leave(LeaveKind.PAID)),
            7, RosterFrom.PHOTO, nov, chosen = false, withOutside = false, at = Instant.parse("2026-11-12T00:00:00Z"))
        assertEquals(d, RosterDraftStore.decode(RosterDraftStore.encode(d)))
    }

    @Test fun aMonthTypedComesBackAfterTheAppClosed() {
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        val day = runBlocking { c.scheduleRepository.shiftTypes.first().first { !it.isOff } }
        first.tap(1)
        first.put(DutyCell.Shift(day.id))
        first.put(DutyCell.Shift(day.id))
        idleUntil("the draft written") { File(dir, "${PersonalCalendar.MAIN}.json").exists() }
        // The process is gone; the screen is made again.
        val again = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        assertTrue(again.ui.value.resumed)
        assertEquals(mapOf(1 to DutyCell.Shift(day.id), 2 to DutyCell.Shift(day.id)), again.ui.value.cells)
        assertEquals(3, again.ui.value.cursor)
        // 처음부터 throws it away for good.
        again.startOver()
        assertFalse(again.ui.value.resumed)
        assertTrue(again.ui.value.cells.isEmpty())
        assertFalse(File(dir, "${PersonalCalendar.MAIN}.json").exists())
        assertFalse(DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).ui.value.resumed)
    }

    @Test fun nothingComesBackOncePutOnOrThrownAway() {
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        val day = runBlocking { c.scheduleRepository.shiftTypes.first().first { !it.isOff } }
        vm.tap(1)
        vm.put(DutyCell.Shift(day.id))
        idleUntil("the draft written") { File(dir, "${PersonalCalendar.MAIN}.json").exists() }
        vm.discard()
        assertNull(RosterDraftStore(dir).read(PersonalCalendar.MAIN))
        assertFalse(DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).ui.value.resumed)
    }
}
