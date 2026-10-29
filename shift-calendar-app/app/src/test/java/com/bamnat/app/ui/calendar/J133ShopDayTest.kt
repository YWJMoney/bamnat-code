package com.bamnat.app.ui.calendar

import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.DefaultShiftTypes
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.WorkStyle
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [J133B-02] 1.33 빌드 8 확인: a 가게 calendar's day sheet and hours, in a shop owner's terms. */
class J133ShopDayTest {
    private val types = DefaultShiftTypes.all().mapIndexed { i, t -> t.copy(id = i + 1L) } +
        com.bamnat.core.model.ShiftType(9, "영업", "영", 0xFF00A88F, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(22, 0))
    private val monday = LocalDate.of(2026, 11, 2)
    private val open = WorkPattern.Weekly(monday, DayOfWeek.entries.associateWith { if (it == DayOfWeek.MONDAY) 4L else 9L })

    @Test fun aShopOffersTheDaysItUses() {
        val snap = ScheduleSnapshot(types, open, emptyList())
        assertEquals(listOf("휴무", "영업"), dayTypes(snap, WorkStyle.BUSINESS, null).map { it.name })
        // A shift put on a day by hand stays offered; other kinds of work see every shift.
        val moved = ScheduleSnapshot(types, open, listOf(DayEntry(monday.plusDays(1), ShiftOverride.Shift(2))))
        assertEquals(listOf("오후", "휴무", "영업"), dayTypes(moved, WorkStyle.BUSINESS, null).map { it.name })
        assertEquals(types, dayTypes(snap, WorkStyle.SHIFT, null))
        // Nothing used yet: everything, so the first day can be put in.
        assertEquals(types, dayTypes(ScheduleSnapshot(types, null, emptyList()), WorkStyle.BUSINESS, null))
    }

    @Test fun aShopsWeekIsNotAnEmployeesWeek() {
        val shop = AppSettings(workStyle = WorkStyle.BUSINESS).limits
        assertFalse(com.bamnat.core.hours.WorkHoursCalculator(ScheduleSnapshot(types, open, emptyList()).resolver, DayOfWeek.MONDAY, shop)
            .week(monday).overLimit)
        assertTrue(com.bamnat.core.hours.WorkHoursCalculator(
            ScheduleSnapshot(types, WorkPattern.Weekly(monday, DayOfWeek.entries.associateWith { 9L }), emptyList()).resolver,
            DayOfWeek.MONDAY, AppSettings(workStyle = WorkStyle.SHIFT, weeklyLimitHours = 64).limits,
        ).week(monday).overLimit)
    }
}
