package com.bamnat.core.schedule

import com.bamnat.core.Fixtures
import com.bamnat.core.formula.DayFactsBook
import com.bamnat.core.formula.FormulaName
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorDay
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.ShiftLooks
import com.bamnat.core.recipe.ShiftRole
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * [LINK-01] 1.39 (사용자 2026-11-07 "제작물을 입히면 대근·특근 등 근무 입력을 테마가 인식 못함"): a 교대 calendar whose day off is itself a kind
 * (휴무), with a 야간 대근 and a 특근 on days off, 잔업 on a working day, a full-day 연차 and a day changed by hand — every 작품 surface reads them
 * the way the calendar's own cell shows them.
 */
class DayWorkTest {
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val f = Fixtures
    // 야간 → 주간 → 휴무 → 휴무, from 10/1.
    private val pattern = WorkPattern.Rotation(oct1, listOf(f.night.id, f.day.id, f.off.id, f.off.id))
    private val entries = listOf(
        DayEntry(oct1, leave = Leave()),
        DayEntry(oct1.plusDays(1), extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, minutes = 120))),
        DayEntry(oct1.plusDays(2), extras = listOf(ExtraEntry(ExtraKind.COVER_ID, LocalTime.of(22, 0), LocalTime.of(7, 0), 60, coveredShiftId = f.night.id))),
        DayEntry(oct1.plusDays(3), extras = listOf(ExtraEntry(ExtraKind.SPECIAL_ID, LocalTime.of(9, 0), LocalTime.of(18, 0), 60))),
        DayEntry(oct1.plusDays(4), override = ShiftOverride.Shift(f.day.id)),
    )
    private val r = ScheduleResolver(f.all, pattern, entries)
    private fun on(d: Int) = DayWork.of(r.resolve(oct1.plusDays(d.toLong() - 1)))

    @Test fun aCoverOnADayOffIsTheCoveredShift() {
        val w = on(3)
        assertEquals("야간", w.name)
        assertEquals(ShiftRole.NIGHT, w.role)
        assertTrue(w.worked); assertFalse(w.off); assertTrue(w.cover); assertTrue(w.extra)
        assertEquals(8 * 60, w.totalMinutes)
        assertEquals(f.night.colorArgb, w.colorArgb)
        assertEquals(setOf("role.night", "extra", "cover"), w.states)
    }

    @Test fun specialWorkOnADayOffIsAWorkedDay() {
        val w = on(4)
        assertEquals("특근", w.name)
        assertEquals(ShiftRole.DAY, w.role)
        assertTrue(w.worked); assertFalse(w.cover)
        assertEquals(listOf("특근"), w.extraKinds)
        assertEquals(setOf("role.day", "extra"), w.states)
    }

    @Test fun overtimeAddsToTheShiftsHours() {
        val w = on(2)
        assertEquals(ShiftRole.DAY, w.role)
        assertEquals(8 * 60, w.shiftMinutes)
        assertEquals(120, w.extraMinutes)
        assertEquals(10 * 60, w.totalMinutes)
        assertEquals(setOf("role.day", "extra"), w.states)
    }

    @Test fun aFullLeaveIsADayOffAndAnEditIsSaid() {
        val leave = on(1)
        assertEquals(ShiftRole.OFF, leave.role)
        assertTrue(leave.off); assertTrue(leave.fullLeave)
        assertEquals(setOf("role.off", "leave"), leave.states)
        val edited = on(5)
        assertEquals(ShiftRole.DAY, edited.role)
        assertTrue("edited" in edited.states)
        assertFalse("직접 바꾼 날이 아님", "edited" in DayWork.of(r.resolve(oct1.plusDays(2)), hasPattern = true).states)
        assertNull(DayWork.of(r.resolve(oct1.minusDays(1))).role)
        assertEquals(ShiftRole.OFF, on(7).role)
    }

    @Test fun formulasReadTheExtraWork() {
        val book = DayFactsBook(oct1, "교대", shifts = { d ->
            val w = DayWork.of(r.resolve(d))
            w.name?.let {
                DayFactsBook.DayShift(it, (w.role ?: ShiftRole.OTHER).key, minutes = if (w.worked) w.totalMinutes else 0, off = w.off,
                    extras = w.extraKinds, extraMinutes = w.extraMinutes, cover = w.cover, edited = w.edited)
            }
        })
        val cover = book.values(oct1.plusDays(2))
        assertEquals(false, cover[FormulaName.OFF.ko])
        assertEquals("야간", cover[FormulaName.ROLE.ko])
        assertEquals(8.0, cover[FormulaName.HOURS.ko])
        assertEquals(true, cover[FormulaName.COVER.ko])
        assertEquals(listOf("대근"), cover[FormulaName.EXTRA.ko])
        val overtime = book.values(oct1.plusDays(1))
        assertEquals(10.0, overtime[FormulaName.HOURS.ko])
        assertEquals(2.0, overtime[FormulaName.EXTRA_HOURS.ko])
        assertEquals(8.0, overtime[FormulaName.SHIFT_HOURS.ko])
        // 10/2 잔업 10h + 10/3 대근 8h + 10/4 특근 8h + 10/5 주간 8h (10/1 연차): 그 주 일한 시간과 쉬지 않고 이어 일한 날.
        assertEquals(4, book.values(oct1.plusDays(4))[FormulaName.STREAK.ko])
    }

    @Test fun theShiftLookAndDecorationsFollowTheWork() {
        // 쉬는 날이어도 특근을 넣은 날은 '쉬는 날 모양'이 아니에요; 그날 밤 대근 중에는 야간 모양.
        assertEquals(ShiftLooks.Mode.NORMAL, ShiftLookMode.at(LocalDateTime.of(2026, 10, 4, 12, 0), r::resolve))
        assertEquals(ShiftLooks.Mode.NIGHT, ShiftLookMode.at(LocalDateTime.of(2026, 10, 3, 23, 0), r::resolve))
        assertEquals(ShiftLooks.Mode.OFF, ShiftLookMode.at(LocalDateTime.of(2026, 10, 7, 12, 0), r::resolve))
        val w = on(3)
        val day = DecorDay(10, 3, 6, w.role?.key, false, false, work = w.states)
        assertTrue(DecorItem(com.bamnat.core.recipe.DecorKind.EMOJI, "⭐", Decor.CELL, on = "cover").standsOn(day))
        assertTrue(DecorItem(com.bamnat.core.recipe.DecorKind.EMOJI, "⭐", Decor.CELL, on = "role.night").standsOn(day))
        assertFalse(DecorItem(com.bamnat.core.recipe.DecorKind.EMOJI, "⭐", Decor.CELL, on = "leave").standsOn(day))
        assertEquals(null, Decor.onProblem("cover"))
    }
}
