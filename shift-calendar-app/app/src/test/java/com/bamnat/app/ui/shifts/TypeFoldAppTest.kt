package com.bamnat.app.ui.shifts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.36 PC 재검토: 알바 → 현장 asks once, and 접어 두기 puts the three 알바 kinds away. */
@RunWith(AndroidJUnit4::class)
class TypeFoldAppTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @Test fun foldingPutsThe알바KindsAway() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        repo.addHourlyTypes()
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.FIELD)
        val ask = TypeFoldAsk.asking.value
        assertNotNull(ask)
        assertEquals(3, ask!!.types.size)
        // 교대 이름(주간·오후·야간)이 남아 있으니 따로 넣을 것이 없어요.
        assertTrue(ask.adds.isEmpty() && !ask.empty)
        c.foldedTypes.fold(ask.calendar, ask.types.map { it.id })
        assertEquals(ask.types.map { it.id }.toSet(), c.foldedTypes.state.value[PersonalCalendar.MAIN])
        val vm = ShiftsViewModel(c, PersonalCalendar.MAIN)
        assertEquals(ask.types.map { it.id }.toSet(), vm.folded.first())
        // Back to 알바: out again by itself.
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.HOURLY)
        assertEquals(null, c.foldedTypes.state.value[PersonalCalendar.MAIN])
    }

    @Test fun nothingLeftBringsTheNewKindsOwn() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        repo.shiftTypes.first().filter { !it.isOff }.forEach { repo.deleteShiftType(it.id) }
        repo.addHourlyTypes()
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.SHIFT)
        assertEquals(listOf("주간", "오후", "야간"), TypeFoldAsk.asking.value!!.adds.map { it.name })
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.FIELD)
        assertTrue(TypeFoldAsk.asking.value!!.empty)
    }

    /** [SETUP-16] 1.37 PC 122d18a0: 접어 두기 into 현장·자영업·투자 stops the cycle after today; 되돌리기 brings it back as it was. */
    @Test fun foldingIntoDayByDayWorkStopsTheCycleAndUndoBringsItBack() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        val cycle = com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))
        repo.savePattern(cycle)
        assertTrue(TypeFoldAsk.stopsCycle(WorkStyle.FIELD) && TypeFoldAsk.stopsCycle(WorkStyle.INVEST) && TypeFoldAsk.stopsCycle(WorkStyle.BUSINESS))
        assertTrue(!TypeFoldAsk.stopsCycle(WorkStyle.HOURLY) && !TypeFoldAsk.stopsCycle(WorkStyle.SHIFT))
        repo.endPatternsAfter(today)
        assertEquals(today, repo.patterns.first().single().endDate)
        TypeFoldAsk.undo(c, TypeFoldAsk.Done(PersonalCalendar.MAIN, emptyList(), "", listOf(cycle)))
        kotlinx.coroutines.withTimeout(5_000) { while (repo.patterns.first().singleOrNull()?.endDate != null) kotlinx.coroutines.delay(20) }
        assertEquals(listOf<com.bamnat.core.model.WorkPattern>(cycle), repo.patterns.first())
        repo.clearPattern()
    }

    /** [SETUP-16] 1.37 PC b98f9d1e: the 되돌리기 missed, going back to 교대 brings the stopped cycle back; to 현장 again it stays stopped. */
    @Test fun backToShiftWorkBringsTheStoppedCycleBack() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        val cycle = com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))
        val next = com.bamnat.core.model.WorkPattern.Rotation(today.plusDays(10), listOf(day, day, null))
        repo.restorePatterns(listOf(cycle, next))
        val was = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, was)
        assertEquals(today, repo.patterns.first().single().endDate)
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.BUSINESS)
        assertEquals(1, repo.patterns.first().size)
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS, WorkStyle.SHIFT)
        assertEquals(was, repo.patterns.first())
        assertEquals(null, c.foldedTypes.stopped(PersonalCalendar.MAIN))
        TypeFoldAsk.asking.value = null
        repo.clearPattern()
    }

    /** A cycle changed by hand after it was stopped is the user's: going back leaves it as it is. */
    @Test fun aCycleSetAgainByHandIsNotReplaced() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        repo.restorePatterns(listOf(com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))))
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, repo.patterns.first())
        val mine = com.bamnat.core.model.WorkPattern.Rotation(today, listOf(day, day, null, null))
        repo.restorePatterns(listOf(mine))
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.SHIFT)
        assertEquals(listOf<com.bamnat.core.model.WorkPattern>(mine), repo.patterns.first())
        TypeFoldAsk.asking.value = null
        repo.clearPattern()
    }
}
