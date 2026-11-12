package com.bamnat.app.imports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.RosterDraftStore
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.imports.copiedLine
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.MonthFill.CopyWay
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.journal.RosterFrom
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 갈래 L 4차, 한 달 근무 넣기: [RVL-39] (검토 U07b-16) 지난달과 같게 — 내 순서 이어서 · 요일에 맞춰 · 날짜 그대로, [RVL-36] (검토
 * U07b-04) 노란 칸 하나씩 '맞아요', [RVL-38] (검토 U07b-06) 이미 넣어 둔 연차는 '그 날은 그대로 두기', [RVL-40] (검토 T07b-03) 가게·투자에는
 * 연차 키가 없음.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RosterMonthWaysTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)
    private val dir get() = File(app.noBackupFilesDir, "roster_drafts")

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
        dir.deleteRecursively()
    }

    private val types get() = runBlocking { c.scheduleRepository.shiftTypes.first() }
    private fun id(name: String) = types.first { it.name == name }.id

    /** D D N N O O O O from 9월 1일: an order of 8 that does not fit a week, so it slips three days a month. */
    private fun rotation() = WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf("주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무").map(::id))

    @Test fun lastMonthCarriesMyOrderOn() {
        val r = rotation()
        val snap = ScheduleSnapshot(types, r, emptyList())
        val (cells, way, order) = DutyRosterViewModel.lastMonth(nov, snap)
        assertEquals(CopyWay.ORDER, way)
        assertEquals(8, order)
        assertEquals((1..30).associateWith { DutyCell.Shift(r.shiftTypeIdOn(nov.atDay(it))!!) }, cells)
        // Day for day (as before) is what 10월 1일 had on 11월 1일, which the order has moved on from.
        val (dates, used, _) = DutyRosterViewModel.lastMonth(nov, snap, CopyWay.DATES)
        assertEquals(CopyWay.DATES, used)
        assertEquals(DutyCell.Shift(r.shiftTypeIdOn(LocalDate.of(2026, 10, 1))!!), dates[1])
        assertNotEquals(cells, dates)
    }

    @Test fun aWeekGoesOnByWeekday() {
        val week = DayOfWeek.entries.associateWith { if (it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY) id("휴무") else id("주간") }
        val snap = ScheduleSnapshot(types, WorkPattern.Weekly(LocalDate.of(2026, 9, 1), week), emptyList())
        val (byWeekday, way, _) = DutyRosterViewModel.lastMonth(nov, snap, CopyWay.WEEKDAYS)
        assertEquals(CopyWay.WEEKDAYS, way)
        assertEquals((1..30).associateWith { DutyCell.Shift(week.getValue(nov.atDay(it).dayOfWeek)!!) }, byWeekday)
        // A week is an order of 7: carried on, it is the same as by weekday.
        val (best, bestWay, order) = DutyRosterViewModel.lastMonth(nov, snap)
        assertEquals(CopyWay.ORDER to 7, bestWay to order)
        assertEquals(byWeekday, best)
        // Day for day put 10월 1일 (목) on 11월 1일 (일): a work day on a Sunday.
        assertEquals(DutyCell.Shift(id("주간")), DutyRosterViewModel.lastMonth(nov, snap, CopyWay.DATES).first[1])
    }

    @Test fun aRosterWithNoOrderGoesByWeekday() {
        val kinds = listOf("주간", "야간", "휴무").map(::id)
        val random = java.util.Random(7)
        val sep = LocalDate.of(2026, 9, 1)
        val entries = (0 until 61).map { DayEntry(sep.plusDays(it.toLong()), ShiftOverride.Shift(kinds[random.nextInt(3)])) }
        val snap = ScheduleSnapshot(types, null as WorkPattern?, entries)
        val (cells, way, order) = DutyRosterViewModel.lastMonth(nov, snap)
        assertEquals(CopyWay.WEEKDAYS, way)
        assertNull(order)
        val byDate = entries.associate { it.date to DutyCell.Shift((it.override as ShiftOverride.Shift).shiftTypeId) }
        assertEquals((1..30).associateWith { d -> byDate.getValue(nov.atDay(d).minusWeeks(if (d <= 28) 4 else 5)) }, cells)
        // My order asked for when there is none: by weekday.
        assertEquals(CopyWay.WEEKDAYS, DutyRosterViewModel.lastMonth(nov, snap, CopyWay.ORDER).second)
    }

    @Test fun anotherWayGoesOverTheSameDaysAndOneUndoTakesItAllBack() {
        val snap = ScheduleSnapshot(types, rotation(), emptyList())
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.tap(1)
        vm.put(DutyCell.Shift(id("야간")))
        val typed = vm.ui.value.cells
        vm.sameAsLastMonth(snap)
        var u = vm.ui.value
        assertEquals(CopyWay.ORDER, u.copied!!.way)
        assertEquals("지난달까지 이어 온 8일 순서대로 넣었어요", copiedLine(u.copied!!))
        assertEquals(2, u.history.size)
        vm.sameAsLastMonth(snap, CopyWay.DATES)
        u = vm.ui.value
        assertEquals(CopyWay.DATES, u.copied!!.way)
        assertEquals("지난달을 날짜 그대로 넣었어요", copiedLine(u.copied!!))
        assertEquals(DutyRosterViewModel.lastMonth(nov, snap, CopyWay.DATES).first, u.cells)
        // Changing the way was not a step of its own: one 되돌리기 is back to what was typed.
        assertEquals(2, u.history.size)
        vm.undo()
        assertEquals(typed, vm.ui.value.cells)
        assertNull(vm.ui.value.copied)
        // The next key ends the choice.
        vm.sameAsLastMonth(snap)
        vm.put(DutyCell.Shift(id("주간")))
        assertNull(vm.ui.value.copied)
    }

    @Test fun nothingLastMonthIsSaid() = runBlocking {
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        val said = async(start = CoroutineStart.UNDISPATCHED) { vm.messages.first() }
        vm.sameAsLastMonth(ScheduleSnapshot(types, null as WorkPattern?, emptyList()))
        assertEquals("지난달에 넣은 근무가 없어 가져올 게 없어요", said.await().message)
        assertTrue(vm.ui.value.cells.isEmpty())
        assertTrue(vm.ui.value.history.isEmpty())
    }

    @Test fun aYellowCellRightAsReadIsNoLongerYellow() {
        val day = DutyCell.Shift(id("주간"))
        val row = RosterRow("김민지", (1..5).associateWith { day }, unsure = setOf(2, 4))
        RosterDraftStore(dir).write(PersonalCalendar.MAIN, RosterDraftStore.Draft(nov, listOf(row), 0, emptyMap(), emptyMap(), 1,
            RosterFrom.PHOTO, nov, chosen = true, withOutside = true, at = Instant.now()))
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        assertEquals(setOf(2, 4), vm.ui.value.doubtful)
        vm.rightAsRead(2)
        assertEquals(setOf(4), vm.ui.value.doubtful)
        assertNull(vm.ui.value.same)
        assertEquals(day, vm.ui.value.cells[2])
        // The last one is the photo looked at.
        vm.rightAsRead(4)
        assertTrue(vm.ui.value.doubtful.isEmpty())
        assertEquals(true, vm.ui.value.same)
        // A day with nothing read is not right as read.
        val before = vm.ui.value
        vm.rightAsRead(20)
        assertEquals(before, vm.ui.value)
    }

    @Test fun leaveAlreadyThereStaysWhenAsked() {
        val repo = c.calendars.repository(PersonalCalendar.MAIN)
        val fifth = nov.atDay(5)
        runBlocking { repo.setLeave(fifth, Leave()) }
        val day = id("주간")
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.tap(1)
        repeat(7) { vm.put(DutyCell.Shift(day)) }
        val snap = runBlocking { repo.snapshot.first() }
        assertEquals(setOf(fifth), DutyRosterViewModel.overwritten(vm.ui.value, snap))
        assertEquals(7, DutyRosterViewModel.writes(vm.ui.value, snap))
        vm.keepMine(true)
        assertEquals(6, DutyRosterViewModel.writes(vm.ui.value, snap))
        vm.apply()
        fun entry(d: Int) = runBlocking { repo.snapshot.first() }.entries.firstOrNull { it.date == nov.atDay(d) }
        idleUntil("the days written") { vm.ui.value.applied != null }
        assertEquals(6, vm.ui.value.applied)
        assertEquals(ShiftOverride.Shift(day), entry(4)?.override)
        assertEquals(ShiftOverride.Shift(day), entry(6)?.override)
        assertEquals(Leave(), entry(5)?.leave)
        // Brought back after the app closed, the choice is still there.
        assertTrue(RosterDraftStore.decode(RosterDraftStore.encode(RosterDraftStore.Draft(nov, null, 0, emptyMap(), emptyMap(), 1,
            RosterFrom.TYPED, null, true, true, Instant.now(), keepMine = true))).keepMine)
    }

    @Test fun leaveKeysOnlyForWorkWithLeave() {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.BUSINESS) }
        assertFalse(DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).leaveKeys)
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.INVEST) }
        assertFalse(DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).leaveKeys)
        listOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY).forEach { s ->
            c.settingsRepository.update { it.copy(workStyle = s) }
            assertTrue(s.name, DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).leaveKeys)
        }
        c.settingsRepository.update { it.copy(workStyle = null) }
        assertTrue(DutyRosterViewModel(c, PersonalCalendar.MAIN, nov).leaveKeys)
    }
}
