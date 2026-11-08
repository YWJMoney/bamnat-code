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
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        repo.restorePatterns(listOf(cycle, next))
        val was = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, was, WorkStyle.SHIFT)
        assertEquals(today, repo.patterns.first().single().endDate)
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.BUSINESS)
        assertEquals(1, repo.patterns.first().size)
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS, WorkStyle.SHIFT)
        assertEquals(was, repo.patterns.first())
        assertEquals(null, c.foldedTypes.stopped(PersonalCalendar.MAIN, WorkStyle.SHIFT))
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
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, repo.patterns.first(), WorkStyle.SHIFT)
        val mine = com.bamnat.core.model.WorkPattern.Rotation(today, listOf(day, day, null, null))
        repo.restorePatterns(listOf(mine))
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.FIELD, WorkStyle.SHIFT)
        assertEquals(listOf<com.bamnat.core.model.WorkPattern>(mine), repo.patterns.first())
        TypeFoldAsk.asking.value = null
        repo.clearPattern()
    }

    /** 1.37 PC 0068727b: 현장 → 자영업 (영업일 saved from today) → 교대 brings 교대's cycle back; 알바 in between does not take it. */
    @Test fun aShopsDaysGoAndOnlyShiftWorkTakesItsCycleBack() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        repo.restorePatterns(listOf(com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))))
        val was = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, was, WorkStyle.SHIFT)
        repo.changePatternFrom(com.bamnat.core.model.WorkPattern.Weekly(today, java.time.DayOfWeek.entries.associateWith { day }), today)
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS, WorkStyle.HOURLY)
        assertTrue("알바는 교대 주기를 되살리지 않아요", repo.patterns.first() != was)
        assertNotNull(c.foldedTypes.stopped(PersonalCalendar.MAIN, WorkStyle.SHIFT))
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.SHIFT)
        assertEquals(was, repo.patterns.first())
        TypeFoldAsk.asking.value = null
        repo.clearPattern()
    }

    /** 1.37 PC deca4f1f: 교대 → 알바 with 접어 두기 stops 교대's cycle too; the 알바 days saved meanwhile go when 교대 comes back. */
    @Test fun anyFoldStopsTheOldCycleAndShiftWorkTakesItBack() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        assertTrue(TypeFoldAsk.stopsCycle(WorkStyle.SHIFT, WorkStyle.HOURLY) && !TypeFoldAsk.stopsCycle(WorkStyle.SHIFT, WorkStyle.SHIFT))
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        repo.restorePatterns(listOf(com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))))
        val was = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, was, WorkStyle.SHIFT)
        repo.changePatternFrom(com.bamnat.core.model.WorkPattern.Weekly(today.plusDays(1), java.time.DayOfWeek.entries.associateWith { day }), today.plusDays(1))
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.SHIFT)
        assertEquals(was, repo.patterns.first())
        TypeFoldAsk.asking.value = null
        repo.clearPattern()
    }

    /** 1.37 PC 70710298: back in 교대, folding the 알바 kinds does not stop the 교대 cycle that just came back. */
    @Test fun onlyTheOldWorksOwnScheduleIsStopped() {
        val d = com.bamnat.core.time.Today.date()
        val rotation = com.bamnat.core.model.WorkPattern.Rotation(d, listOf(1L, null))
        val weekly = com.bamnat.core.model.WorkPattern.Weekly(d, java.time.DayOfWeek.entries.associateWith { 1L })
        assertTrue(TypeFoldAsk.ownSchedule(WorkStyle.SHIFT, rotation))
        assertTrue(!TypeFoldAsk.ownSchedule(WorkStyle.HOURLY, rotation))
        assertTrue(TypeFoldAsk.ownSchedule(WorkStyle.HOURLY, weekly) && TypeFoldAsk.ownSchedule(WorkStyle.DAY, weekly))
        assertTrue(!TypeFoldAsk.ownSchedule(WorkStyle.SHIFT, weekly) && !TypeFoldAsk.ownSchedule(WorkStyle.HOURLY, null))
    }

    /** 1.37 PC 67d13335: 일근 (월~금 from tomorrow) → 알바 with 접어 두기 stops the 월~금; back to 일근 brings it, back to 교대 the order. */
    @Test fun dayWorksDaysAreStoppedAndComeBackOfTheirOwn() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        repo.restorePatterns(listOf(com.bamnat.core.model.WorkPattern.Rotation(today.minusDays(30), listOf(day, null))))
        val shift = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, shift, WorkStyle.SHIFT)
        repo.changePatternFrom(com.bamnat.core.model.WorkPattern.Weekly(today.plusDays(1),
            java.time.DayOfWeek.entries.associateWith { d -> day.takeIf { d.value <= 5 } }), today.plusDays(1))
        val dayWork = repo.patterns.first()
        assertTrue(TypeFoldAsk.ownSchedule(WorkStyle.DAY, TypeFoldAsk.going(dayWork, today)))
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, dayWork, WorkStyle.DAY)
        assertTrue("내일부터의 월~금이 멈춰요", TypeFoldAsk.going(repo.patterns.first(), today) == null)
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.DAY)
        assertEquals(dayWork, repo.patterns.first())
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.DAY, WorkStyle.SHIFT)
        assertEquals(shift, repo.patterns.first())
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.SHIFT, WorkStyle.DAY)
        assertEquals("교대로 갈 때 맡겨 둔 월~금이 돌아와요", dayWork.last(), repo.patterns.first().last())
        TypeFoldAsk.asking.value = null
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        repo.clearPattern()
    }

    /** [SETUP-16] 1.37 사용자 ④: 교대 → 일근 puts in its own 일근 (09~18) and leaves 교대's 주간 time as it was; 교대 주간 can be put away. */
    @Test fun dayWorkGetsItsOwn일근AndLeavesTheShiftsDayAlone() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        val before = repo.shiftTypes.first().first { it.name == "주간" }
        val added = com.bamnat.app.ui.addStyleTypes(c, PersonalCalendar.MAIN, WorkStyle.DAY)
        val types = repo.shiftTypes.first()
        val own = types.single { it.name == "일근" }
        assertEquals(listOf(own.id), added)
        assertEquals(java.time.LocalTime.of(9, 0) to java.time.LocalTime.of(18, 0), own.start to own.end)
        assertEquals(before, types.first { it.name == "주간" })
        assertTrue("한 번만 넣어요", com.bamnat.app.ui.addStyleTypes(c, PersonalCalendar.MAIN, WorkStyle.DAY).isEmpty())
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.SHIFT, WorkStyle.DAY)
        val ask = TypeFoldAsk.asking.value!!
        assertTrue(ask.types.any { it.name == "주간" } && ask.types.none { it.name == "일근" } && ask.adds.isEmpty())
        TypeFoldAsk.asking.value = null
        repo.deleteShiftType(own.id)
    }

    /** ③ 알바 요일은 빈칸에서 직접: days of the week with no day given start empty; 일근 starts 월~금. */
    @Test fun anHourlyWeekStartsEmpty() {
        val today = com.bamnat.core.time.Today.date()
        val empty = PatternDraft().apply { initFrom(null, today, null, weekly = true) }
        assertEquals(PatternKind.WEEKLY, empty.kind)
        assertTrue(empty.weekly.all { it == null })
        val day = PatternDraft().apply { initFrom(null, today, 7L) }
        assertEquals(listOf(7L, 7L, 7L, 7L, 7L, null, null), day.weekly.toList())
    }

    /** 1.37 PC 978d10f1 MED-2: 일근 → 알바 (토·일 saved from tomorrow) → 일근 brings 월~금 back, and 알바 gets its 토·일 back after. */
    @Test fun dayWorkComesBackAfterHourlyDays() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        val today = com.bamnat.core.time.Today.date()
        val day = repo.shiftTypes.first().first { !it.isOff }.id
        repo.restorePatterns(listOf(com.bamnat.core.model.WorkPattern.Weekly(today.minusDays(30),
            java.time.DayOfWeek.entries.associateWith { d -> day.takeIf { d.value <= 5 } })))
        val dayWork = repo.patterns.first()
        TypeFoldAsk.stopCycle(c, PersonalCalendar.MAIN, dayWork, WorkStyle.DAY)
        repo.changePatternFrom(com.bamnat.core.model.WorkPattern.Weekly(today.plusDays(1),
            java.time.DayOfWeek.entries.associateWith { d -> day.takeIf { d.value >= 6 } }), today.plusDays(1))
        val hourly = repo.patterns.first()
        repo.addHourlyTypes()
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.DAY)
        assertEquals("월~금이 돌아와요", dayWork, repo.patterns.first())
        // 1.37 PC 62a56fdb: the 알바 kinds' 접어 두기 does not stop the 월~금 that just came back.
        val ask = TypeFoldAsk.asking.value!!
        assertTrue(ask.resumed)
        org.junit.Assert.assertFalse(TypeFoldAsk.stops(ask, repo.patterns.first(), today))
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.DAY, WorkStyle.HOURLY)
        assertEquals("토·일이 돌아와요", hourly.last(), repo.patterns.first().last())
        org.junit.Assert.assertFalse(TypeFoldAsk.asking.value?.let { TypeFoldAsk.stops(it, repo.patterns.first(), today) } ?: false)
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.HOURLY, WorkStyle.DAY)
        assertEquals("두 번째도 월~금이 돌아와요", dayWork.last(), repo.patterns.first().last())
        TypeFoldAsk.asking.value = null
        WorkStyle.entries.forEach { c.foldedTypes.stop(PersonalCalendar.MAIN, it, null) }
        repo.clearPattern()
    }

    /**
     * 1.38 사용자 2026-11-06 (가게 영업·사장님처럼 교대가 없는 유형에 주야가 보임): a 자영업 calendar starts with 주간·오후·야간 put away,
     * a kind the shop uses (영업일 순서, a day) stays, and one the user takes back out is not put away again.
     */
    @Test fun dayByDayWorkStartsWithTheShiftKindsPutAway() = runBlocking {
        val c = app.container
        val cal = "quiet-test"
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        val types = repo.shiftTypes.first()
        val day = types.first { it.name == "주간" }
        val night = types.first { it.name == "야간" }
        val off = types.first { it.isOff }
        val shop = repo.saveShiftType(com.bamnat.core.model.ShiftType(0, "영업", "영", 0xFF00A88F, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(21, 0)))
        val all = repo.shiftTypes.first()
        // 야간 is on a day the user put in: kept. 영업 and 휴무 are the shop's own.
        val away = TypeFoldAsk.quietKinds(WorkStyle.BUSINESS, all, emptySet()) { it == night.id }
        assertEquals(setOf(day.id, all.first { it.name == "오후" }.id), away.toSet())
        assertTrue(shop !in away && off.id !in away)
        assertTrue(WorkStyle.entries.filter { it != WorkStyle.SHIFT }.all(TypeFoldAsk::quietStyle))
        assertTrue(!TypeFoldAsk.quietStyle(WorkStyle.SHIFT) && !TypeFoldAsk.quietStyle(null))
        // 알바 keeps its own 오전 알바, 일근 its 일근; 교대's 주간 goes for both.
        repo.addHourlyTypes()
        val withHourly = repo.shiftTypes.first()
        val morning = withHourly.first { it.name == "오전 알바" }.id
        assertTrue(morning !in TypeFoldAsk.quietKinds(WorkStyle.HOURLY, withHourly, emptySet()) { false })
        assertTrue(day.id in TypeFoldAsk.quietKinds(WorkStyle.HOURLY, withHourly, emptySet()) { false })
        assertTrue(morning in TypeFoldAsk.quietKinds(WorkStyle.DAY, withHourly, emptySet()) { false })
        withHourly.filter { it.name.endsWith("알바") }.forEach { repo.deleteShiftType(it.id) }
        // Once per calendar and kind of work: taken back out, it stays out.
        assertTrue(!c.foldedTypes.quieted(cal, WorkStyle.BUSINESS))
        c.foldedTypes.markQuiet(cal, WorkStyle.BUSINESS)
        assertTrue(c.foldedTypes.quieted(cal, WorkStyle.BUSINESS) && !c.foldedTypes.quieted(cal, WorkStyle.FIELD))
        // The marks are not folded kinds.
        assertEquals(null, c.foldedTypes.state.value[cal])
    }

    /** 1.38 사용자 2026-11-06: the main calendar of a 자영업 puts 주간·오후·야간 away by itself; 교대 again brings them back. */
    @Test fun quietShiftKindsFoldsTheMainCalendarOnce() = runBlocking {
        val c = app.container
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        repo.ensureDefaults()
        repo.clearPattern()
        val names = repo.shiftTypes.first().filter { !it.isOff }.map { it.name }.toSet()
        c.foldedTypes.unfold(PersonalCalendar.MAIN, repo.shiftTypes.first().map { it.id })
        TypeFoldAsk.quietShiftKinds(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS)
        val folded = c.foldedTypes.of(PersonalCalendar.MAIN)
        assertEquals(repo.shiftTypes.first().filter { it.name in setOf("주간", "오후", "야간") }.map { it.id }.toSet(), folded)
        assertTrue(names.containsAll(setOf("주간", "오후", "야간")))
        // Taken back out, the next start leaves it out.
        val back = folded.first()
        c.foldedTypes.unfold(PersonalCalendar.MAIN, back)
        TypeFoldAsk.quietShiftKinds(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS)
        assertTrue(back !in c.foldedTypes.of(PersonalCalendar.MAIN))
        // Back to 교대: its own kinds come out.
        TypeFoldAsk.asking.value = null
        TypeFoldAsk.after(c, PersonalCalendar.MAIN, WorkStyle.BUSINESS, WorkStyle.SHIFT)
        assertTrue(c.foldedTypes.of(PersonalCalendar.MAIN).isEmpty())
    }
}
