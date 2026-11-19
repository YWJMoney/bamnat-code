package com.bamnat.app.data

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * 2.0.0 [RVL-76] (검토 I12b-02) 날짜별 되돌리기: a month wiped by mistake, or a 가계부 line removed, back as it was at an earlier moment;
 * going back can itself be undone, and moments older than a week go.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimeMachineTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val main = PersonalCalendar.MAIN
    private val dec = LocalDate.of(2026, 12, 1)
    private var now = 1_800_000_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var machine: TimeMachine

    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000 // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" } // real clock: a timeout for waiting, not a date
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Before fun ready() {
        waitFor("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
        dir = File(app.cacheDir, "time_machine_test").apply { deleteRecursively() }
        machine = machine(settle = 60 * 60_000L)
    }

    /** A machine of its own over the main calendar; a long [settle] leaves every copy to the test. */
    private fun machine(settle: Long) = TimeMachine(dir, scope, clock = { now }, settleMillis = settle, firstDelayMillis = 60 * 60_000L)
        .also { it.watch(main, c.database) }

    @After fun done() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test fun aMonthWipedByMistakeComesBackAndGoingBackCanBeUndone() = runBlocking {
        val repo = c.scheduleRepository
        val types = repo.shiftTypes.first()
        (0L until 10L).forEach { repo.setOverride(dec.plusDays(it), ShiftOverride.Shift(types.first().id)) }
        val filled = machine.keep(main, TimeMachine.Part.DAYS)
        assertNotNull(filled)
        // Nothing changed: no second copy.
        now += 1_000
        assertNull(machine.keep(main, TimeMachine.Part.DAYS))
        // The mistake: the ten days cleared.
        now += 60 * 60_000
        (0L until 10L).forEach { repo.resetDay(dec.plusDays(it)) }
        assertNotNull(machine.keep(main, TimeMachine.Part.DAYS))
        // The copy the same as now is not offered; the one before the mistake is.
        val points = machine.points(main, TimeMachine.Part.DAYS)
        assertEquals(listOf(filled!!.at), points.map { it.at })
        val diff = machine.diff(points.single())
        assertEquals((0L until 10L).map { dec.plusDays(it) }, diff.days)
        // Back to it: the days are there again, and each says so in 최근 바꾼 기록.
        now += 60_000
        val before = machine.restore(points.single())
        assertNotNull(before)
        waitFor("the days back") { runBlocking { repo.snapshot.first() }.entries.count { it.date in dec..dec.plusDays(9) } == 10 }
        assertTrue(repo.changesOn(dec).first().first().text.endsWith("때로 되돌림"))
        // 되돌리기: as it was just before going back.
        machine.restore(before!!)
        waitFor("the days cleared again") { runBlocking { repo.snapshot.first() }.entries.none { it.date in dec..dec.plusDays(9) } }
    }

    @Test fun aKindDeletedByMistakeComesBackWithItsDays() = runBlocking {
        val repo = c.scheduleRepository
        val morning = repo.saveShiftType(repo.shiftTypes.first().first { !it.isOff }.copy(id = 0, name = "모닝", label = "모"))
        (0L until 3L).forEach { repo.setOverride(dec.plusDays(it), ShiftOverride.Shift(morning)) }
        val with = machine.keep(main, TimeMachine.Part.DAYS)!!
        now += 60 * 60_000
        repo.deleteShiftType(morning)
        assertNotNull(machine.keep(main, TimeMachine.Part.DAYS))
        val back = machine.points(main, TimeMachine.Part.DAYS).single()
        assertEquals(with.at, back.at)
        val diff = machine.diff(back)
        assertTrue(diff.kinds)
        assertEquals((0L until 3L).map { dec.plusDays(it) }, diff.days)
        machine.restore(back)
        // The days point at a kind that is there again, not at nothing.
        waitFor("the kind and its days back") {
            runBlocking { repo.snapshot.first() }.let { s -> (0L until 3L).all { s.resolver.resolve(dec.plusDays(it)).shiftType?.name == "모닝" } }
        }
    }

    @Test fun aLedgerLineRemovedComesBackWithItsOwnId() = runBlocking {
        machine = machine(settle = 50)
        val money = c.moneyRepository
        val line = money.save(MoneyEntry(date = dec, kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비", note = "점심"))
        assertNotNull(machine.keep(main, TimeMachine.Part.MONEY))
        now += 60 * 60_000
        money.delete(line)
        // Watched: the change is kept by itself once it settles.
        waitFor("the change kept") { runBlocking { machine.points(main, TimeMachine.Part.MONEY, all = true) }.size == 2 }
        val back = machine.points(main, TimeMachine.Part.MONEY).first()
        assertEquals(TimeMachine.Diff(back = 1), machine.diff(back))
        machine.restore(back)
        waitFor("the line back") { runBlocking { money.entries.first() }.any { it.id == line.id && it.amount == 12_000L } }
    }

    @Test fun momentsOlderThanAWeekGoAndOnlyOneADayStaysPastTheFirstDay() = runBlocking {
        val repo = c.scheduleRepository
        val type = repo.shiftTypes.first().first().id
        // Three moments on one day, eight days ago, and one now.
        listOf(0L, 1L, 2L).forEach { i ->
            now = 1_800_000_000_000L + i * 60 * 60_000
            repo.setOverride(dec.plusDays(i), ShiftOverride.Shift(type))
            machine.keep(main, TimeMachine.Part.DAYS)
        }
        now = 1_800_000_000_000L + 8 * 24 * 60 * 60_000L
        repo.resetDay(dec)
        machine.keep(main, TimeMachine.Part.DAYS)
        assertEquals(1, machine.points(main, TimeMachine.Part.DAYS, all = true).size)
        // Two days later: the day before keeps only its last moment.
        repeat(3) { i ->
            now += 60 * 60_000
            repo.setOverride(dec.plusDays(10L + i), ShiftOverride.Shift(type))
            machine.keep(main, TimeMachine.Part.DAYS)
        }
        now += 2 * 24 * 60 * 60_000L
        repo.resetDay(dec.plusDays(10))
        machine.keep(main, TimeMachine.Part.DAYS)
        val days = machine.points(main, TimeMachine.Part.DAYS, all = true).map { java.time.Instant.ofEpochMilli(it.at).atZone(com.bamnat.core.time.Today.zone()).toLocalDate() }
        assertEquals(days.size, days.distinct().size)
    }
}
