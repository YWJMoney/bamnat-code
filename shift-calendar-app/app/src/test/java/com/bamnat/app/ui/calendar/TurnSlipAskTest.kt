package com.bamnat.app.ui.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.TurnSlipCheck
import com.bamnat.core.model.Leave
import com.bamnat.core.schedule.TurnSlip
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
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
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 [RVH-64] 검토 PC-72 '이 날 맞아요?' (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md 2·4절): who is asked, once, and that X
 * folds the card but keeps the update notice's line, 닫기 after the answer ends it, and what was picked stays until then (조건 10).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TurnSlipAskTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    /** An hour from now: the update to 2.0.0 came after the taps. */
    private val updated: Instant get() = Today.instant().plusSeconds(3600)

    @Before fun clean() {
        TurnSlipAsk.resetForTest()
        app.getSharedPreferences("turn_slip", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun after() {
        TurnSlipAsk.resetForTest()
        c.settingsRepository.update { it.copy(workStyle = null) }
    }

    /**
     * A 연차 put today on next month's grid, two weeks into it: where 1.36.1~1.39.0 put a tap on this month's same cell after a month
     * turn ([TurnSlip.meant]).
     */
    private suspend fun slipped(): LocalDate {
        val week = c.settingsRepository.current.weekStart
        val next = TurnSlip.firstCell(YearMonth.from(Today.date()).plusMonths(1), week)
        val date = next.plusDays(14)
        c.scheduleRepository.setLeave(date, Leave())
        return date
    }

    private fun again() = runBlocking {
        TurnSlipAsk.resetForTest()
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = true)
    }

    @Test fun aNewPhoneIsNeverAskedNorLaterOn() = runBlocking {
        slipped()
        TurnSlipAsk.load(c, updatedAt = null, setupDone = true)
        assertTrue(TurnSlipAsk.loaded.value)
        assertNull(TurnSlipAsk.ask.value)
        // Updated later (2.0.1): a phone that came new to 2.0.0 is still not asked.
        again()
        assertNull(TurnSlipAsk.ask.value)
    }

    @Test fun aPhoneNotSetUpOrUpdatedBeforeTheTurnWentOverIsNotAsked() = runBlocking {
        slipped()
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = false)
        assertNull(TurnSlipAsk.ask.value)
        clean()
        TurnSlipAsk.load(c, updatedAt = TurnSlip.SINCE.minusDays(1).atStartOfDay(Today.zone()).toInstant(), setupDone = true)
        assertNull(TurnSlipAsk.ask.value)
    }

    @Test fun anUpdatedPhoneIsAskedThenFoldedThenDoneForGood() = runBlocking {
        val date = slipped()
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = true)
        val ask = TurnSlipAsk.ask.value!!
        assertEquals(date, ask.rows.single().line.date)
        assertEquals(date.minusDays(ask.rows.single().line.days), ask.rows.single().line.meant)
        assertFalse(ask.folded)
        assertTrue(ask.rows.single().what, ask.rows.single().what.contains(Leave().label))

        // X on the card: folded, next run too (the update notice's line is told apart, once).
        TurnSlipAsk.fold(app)
        again()
        assertTrue(TurnSlipAsk.ask.value!!.folded)
        assertFalse(TurnSlipAsk.told(app))
        TurnSlipAsk.markTold(app)
        assertTrue(TurnSlipAsk.told(app))

        // 조건 10: closed by X and opened again, what was picked is still picked.
        TurnSlipAsk.show()
        val row = TurnSlipAsk.ask.value!!.rows.single()
        TurnSlipAsk.picks[row.found] = TurnSlipCheck.Choice.MOVE
        TurnSlipAsk.closed()
        assertFalse(TurnSlipAsk.showing.value)
        TurnSlipAsk.show()
        assertEquals(TurnSlipCheck.Choice.MOVE, TurnSlipAsk.picks[row.found])

        // 이대로 할게요 writes it down at once: a phone closed before 닫기 is not asked again.
        TurnSlipAsk.answered(app, TurnSlipCheck.Done.NOTHING)
        assertNotNull(TurnSlipAsk.finished.value)
        TurnSlipAsk.done(app)
        assertNull(TurnSlipAsk.ask.value)
        assertTrue(TurnSlipAsk.picks.isEmpty())
        again()
        assertNull(TurnSlipAsk.ask.value)
    }

    @Test fun theLineIsKeptUntilTheUpdateSoALaterUpdateDoesNotMoveIt() = runBlocking {
        val date = slipped()
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = true)
        assertEquals(date, TurnSlipAsk.ask.value!!.rows.single().line.date)
        // A later run with a later update time: the first one stays, so a tap made on 2.0.0 is never asked about.
        TurnSlipAsk.resetForTest()
        TurnSlipAsk.load(c, updatedAt = updated.plusSeconds(86_400), setupDone = true)
        assertEquals(1, TurnSlipAsk.ask.value!!.rows.size)
    }

    @Test fun anInvestorIsAskedOnlyAboutMemosAndMoney() = runBlocking {
        slipped()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.INVEST) }
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = true)
        // 설계 4절 조건 13: 투자 puts no work on the calendar; with nothing else found, nothing is asked.
        assertNull(TurnSlipAsk.ask.value)
    }

    @Test fun aLineNoLongerThereIsLeftAloneAndTheOthersAreDone() = runBlocking {
        val date = slipped()
        TurnSlipAsk.load(c, updatedAt = updated, setupDone = true)
        val row = TurnSlipAsk.ask.value!!.rows.single()
        // A money line that is no longer there next to a day that is: the day moves, the line is left alone.
        val gone = row.found.copy(line = row.found.line.copy(source = row.found.line.source.copy(kind = TurnSlip.Kind.MONEY, id = 987_654)))
        val done = c.turnSlip.apply(mapOf(row.found to TurnSlipCheck.Choice.MOVE, gone to TurnSlipCheck.Choice.MOVE))
        assertEquals(1, done.count)
        assertEquals(0, done.missed)
        assertNotNull(c.scheduleRepository.entryNow(row.line.meant)?.leave)
        c.turnSlip.undo(done)
        assertNotNull(c.scheduleRepository.entryNow(date)?.leave)
    }
}
