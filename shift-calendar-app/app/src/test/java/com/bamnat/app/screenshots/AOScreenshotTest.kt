package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.StartupTrace
import com.bamnat.app.ui.calendar.FirstMonth
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [PERF-07][PERF-11][PERF-15] 1.28 묶음 AO's screenshots, numbers 1220~1222 (순번 457): the first month after the start
 * (the parts below it come once it is on screen), a heavy user's month drawn from the window and then the whole history
 * with the same days, and the 알람 점검 screen's line with this phone's last start time.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AOScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    @Test fun firstMonthThenTheRest() = shoot("1220_ao_first_month", FOLDED) {
        settle()
        assertTrue("the first month was drawn", FirstMonth.settled.value)
        assertNotNull("the month was marked", StartupTrace.millis(StartupTrace.Step.CALENDAR))
        assertNotNull("this phone keeps the opening", c.startupStats.last)
    }

    /** Three years every other day a night, every fourth day 90 minutes' overtime: 548 changed days. */
    private fun heavyUser() = runBlocking {
        val repo = c.scheduleRepository
        repo.ensureDefaults()
        val types = repo.shiftTypes.first().associateBy { it.name }
        val today = Today.date()
        repo.savePattern(WorkPattern.Rotation(today.minusYears(3), listOf("주간", "주간", "야간", "야간", "휴무").map { types.getValue(it).id }))
        repo.updateDays((0 until 3 * 365 step 2).map { today.minusDays(it.toLong()) }) {
            it.copy(override = ShiftOverride.Shift(types.getValue("야간").id))
        }
        repo.updateDays((0 until 3 * 365 step 4).map { today.minusDays(it.toLong()) }) {
            it.copy(extras = it.extras + ExtraEntry(kindId = com.bamnat.core.model.ExtraKind.OVERTIME_ID, minutes = 90))
        }
    }

    @Test fun heavyUsersMonthIsTheSameFromTheWindow() = shoot("1221_ao_heavy_month", PHONE, style(WorkStyle.SHIFT) {
        heavyUser()
        settle()
        val repo = c.scheduleRepository
        val month = YearMonth.from(Today.date())
        val window = runBlocking { repo.windowSnapshot(month) }
        val whole = runBlocking { repo.snapshot.first() }
        assertTrue(window.covers(month.minusMonths(1)) && window.covers(month.plusMonths(1)))
        for (d in month.minusMonths(1).atDay(1).datesUntil(month.plusMonths(2).atDay(1))) {
            assertEquals("$d", whole.resolver.resolve(d), window.resolver.resolve(d))
        }
        assertTrue(shows("야간"))
    })

    @Test fun diagnosticsShowsTheLastStart() = shoot("1222_ao_diag_startup", PHONE) {
        settle()
        val vm = com.bamnat.app.ui.diag.DiagnosticsViewModel(c)
        content { com.bamnat.app.ui.diag.DiagnosticsScreen(vm, onBack = {}) }
        settle()
        reveal("마지막 시작")
        assertTrue(shows("마지막 시작: 첫 달까지"))
    }
}
