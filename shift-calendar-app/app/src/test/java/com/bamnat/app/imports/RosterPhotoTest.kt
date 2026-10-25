package com.bamnat.app.imports

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.RecentFill
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.RosterReader
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterHint
import com.bamnat.core.imports.RosterWord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

/**
 * [READ-04][READ-05][READ-06][READ-10][READ-11] 한 달 근무 넣기 from a photo, end to end on the app (1.25 순번 369~372,
 * 사용자 2026-10-25 "아무 근표를 갖다 넣어도 어느조에 해당하는지 물어보지도 않고 … 실행 자체를 못함"): a roster photographed
 * at 2.5° asks whose line it is before anything goes on, the answer is remembered for next month, a photo that read no
 * row of dates is read with two taps, and the month put in can be taken back from the calendar after its screen closed.
 * ML Kit does not run here: [RosterScanner.fake] gives the words a photo would.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterPhotoTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
    }

    @After fun noFake() { RosterScanner.fake = null }

    @Test fun severalLinesAskWhoseFirstAndTheAnswerIsKept() = runBlocking {
        RosterScanner.fake = { roster(2.5) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null }
        val u = vm.ui.value
        assertEquals(NAMES, u.rows!!.map { it.label })
        // Nothing on the calendar, and nothing to put in, until the user says which line is theirs.
        assertFalse(u.chosen)
        assertNull(u.row)
        assertTrue(u.writable.isEmpty())
        assertNotNull(u.scan)
        vm.pick(1)
        assertEquals(nov.lengthOfMonth(), vm.ui.value.writable.size)
        assertEquals("이수진", c.dutyCodes.mine(PersonalCalendar.MAIN))
        // Next month's roster from the same ward: the same line is picked, no question.
        val next = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov.plusMonths(1))
        next.readPhoto(Uri.parse("content://test/roster2.jpg"))
        idleUntil("the second photo read") { next.ui.value.rows != null }
        assertTrue(next.ui.value.chosen)
        assertEquals("이수진", next.ui.value.row?.label)
    }

    @Test fun thePickedLineGoesInAndComesBackOutFromTheCalendar() = runBlocking {
        RosterScanner.fake = { roster(-1.5) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null }
        vm.pick(vm.ui.value.rows!!.indexOfFirst { it.label == "박하늘" })
        vm.apply()
        idleUntil("the month put in") { vm.ui.value.applied != null }
        val t = types()
        val snap = c.scheduleRepository.snapshot.first()
        (1..nov.lengthOfMonth()).forEach { d ->
            val want = codeOf(2, d)
            val got = snap.resolver.resolve(nov.atDay(d))
            if (want == "연차") assertNotNull("day $d", got.entry?.leave)
            else assertEquals("day $d", t.getValue(want).id, got.shiftType?.id)
        }
        // [READ-11] The screen is gone; 도구 › 방금 넣은 것 되돌리기 takes the month back.
        val recent = RecentFill.current(PersonalCalendar.MAIN)
        assertNotNull(recent)
        RecentFill.undo(recent!!)
        idleUntil("the month taken back") {
            runBlocking { c.scheduleRepository.snapshot.first().entries.none { it.date.month == nov.month && it.date.year == 2026 && (it.leave != null || it.override != com.bamnat.core.model.ShiftOverride.None) } }
        }
        assertNull(RecentFill.current(PersonalCalendar.MAIN))
    }

    @Test fun noRowOfDatesIsReadWithTwoTaps() = runBlocking {
        // The dates came out as dots: nothing reads by itself, and the screen says to tap.
        val words = roster(1.0, days = false)
        RosterScanner.fake = { words }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the photo read") { !vm.ui.value.reading && vm.ui.value.scan != null }
        assertTrue(vm.ui.value.rows.isNullOrEmpty() || vm.ui.value.rows!!.none { it.cells.size >= 28 })
        val dot = words.filter { it.text == "·" }.let { it[it.size / 2] }
        val mine = words.first { it.text == "최유리" }
        vm.readTapped(RosterHint(dot.centerX to dot.centerY, mine.centerX to mine.centerY))
        idleUntil("the tapped read") { vm.ui.value.rows?.singleOrNull()?.label == "최유리" }
        assertTrue(vm.ui.value.chosen)
        assertEquals(nov.lengthOfMonth(), vm.ui.value.writable.size)
    }

    @Test fun aPhotoWithNoTextSaysWhatToDo() = runBlocking {
        RosterScanner.fake = { emptyList() }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/blank.jpg"))
        idleUntil("the photo read") { !vm.ui.value.reading && vm.ui.value.error != null }
        assertEquals(RosterReader.problem(com.bamnat.core.imports.RosterPhotoRead.Problem.NO_TEXT), vm.ui.value.error)
    }

    @Test fun theLineRememberedTravelsInTheBackup() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.bamnat.app.data.db.AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val book = com.bamnat.app.data.DutyCodeBook(app)
            book.learn("main", "M", DutyCell.Shift(7))
            book.rememberMine("main", "B조")
            val backups = com.bamnat.app.data.BackupManager(db, com.bamnat.app.data.SettingsRepository(app), dutyCodes = { book })
            com.bamnat.app.data.ScheduleRepository(db).ensureDefaults()
            val json = backups.export()
            book.import(emptyMap())
            assertNull(book.mine("main"))
            backups.restore(json)
            assertEquals("B조", book.mine("main"))
            assertEquals(DutyCell.Shift(7), book.of("main")["M"])
            // The remembered line is not a code: the book itself has only what codes mean.
            assertEquals(setOf("M"), book.of("main").keys)
        } finally {
            db.close()
        }
    }

    private fun types() = runBlocking { c.scheduleRepository.shiftTypes.first() }.associateBy { it.name }

    companion object {
        val NAMES = listOf("김민지", "이수진", "박하늘", "최유리")
        private val PATTERN = listOf("주간", "주간", "야간", "야간", "휴무", "휴무")

        /** Person [p]'s cell on day [d]; 박하늘 takes 연차 on the 10th. */
        fun codeOf(p: Int, d: Int): String = if (p == 2 && d == 10) "연차" else PATTERN[(d + p * 2) % PATTERN.size]

        /** A November roster of [NAMES] as a phone photo's words would come, tilted [deg]°. */
        fun roster(deg: Double, days: Boolean = true, code: (Int, Int) -> String = ::codeOf): List<RosterWord> {
            val words = mutableListOf<RosterWord>()
            val a = Math.toRadians(deg)
            fun add(t: String, x: Int, y: Int, w: Int) {
                val cx = x + w / 2.0; val cy = y + 20.0
                val rx = cx * cos(a) - cy * sin(a); val ry = cx * sin(a) + cy * cos(a)
                words += RosterWord(t, (rx - w / 2).toInt(), (ry - 20).toInt(), (rx + w / 2).toInt(), (ry + 20).toInt(), angle = deg.toFloat())
            }
            add("2026년", 400, 0, 120); add("11월", 530, 0, 70); add("근무표", 610, 0, 120)
            for (d in 1..30) add(if (days) "$d" else "·", 300 + (d - 1) * 110, 100, if (days) 22 * "$d".length else 10)
            NAMES.forEachIndexed { i, n ->
                add(n, 100, 180 + i * 70, 120)
                for (d in 1..30) add(code(i, d), 300 + (d - 1) * 110, 180 + i * 70, 70)
            }
            return words
        }
    }
}
