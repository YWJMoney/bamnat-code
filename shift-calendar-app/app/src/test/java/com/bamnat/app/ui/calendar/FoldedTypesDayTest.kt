package com.bamnat.app.ui.calendar

import com.bamnat.app.data.DefaultShiftTypes
import com.bamnat.app.data.FoldedTypes
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.model.ShiftType
import com.bamnat.core.work.WorkStyle
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36 PC 재검토: 알바 → 현장 puts 오전·오후 알바 away ('안 쓰는 종류'); nothing is deleted. */
class FoldedTypesDayTest {
    private val base = DefaultShiftTypes.all().mapIndexed { i, t -> t.copy(id = i + 1L) }
    private val morning = ShiftType(20, "오전 알바", "전", 0xFF00A88F, LocalTime.of(9, 0), LocalTime.of(13, 0))
    private val afternoon = ShiftType(21, "오후 알바", "후", 0xFFE0A100, LocalTime.of(13, 0), LocalTime.of(18, 30))
    private val snap = ScheduleSnapshot(base + morning + afternoon, null, emptyList())
    private val folded = setOf(20L, 21L)

    @Test fun aFoldedKindIsNotOffered() {
        val names = dayTypes(snap, WorkStyle.FIELD, null, folded).map { it.name }
        assertTrue("오전 알바" !in names && "오후 알바" !in names)
        assertEquals(base.size, names.size)
    }

    @Test fun aDayThatHasItKeepsIt() {
        assertTrue(dayTypes(snap, WorkStyle.FIELD, 20L, folded).any { it.id == 20L })
    }

    @Test fun backTo알바ItShowsAgain() {
        // After 되돌리기 to 알바 its own kinds show even while still marked folded.
        assertEquals(snap.shiftTypes, dayTypes(snap, WorkStyle.HOURLY, null, folded))
    }

    @Test fun everyKindFoldedLeavesTheDaysOff() {
        // PC: with nothing else left, showing them all again hid the fold; the day sheet says how to add one instead.
        val off = base.first { it.isOff }
        val only = ScheduleSnapshot(listOf(morning, off), null, emptyList())
        assertEquals(listOf(off), dayTypes(only, WorkStyle.FIELD, null, setOf(20L)))
    }

    @Test fun summaryLineLeavesFoldedOut() {
        assertEquals("쓰는 근무 없음 · 안 쓰는 종류 2개", com.bamnat.app.ui.settings.shiftsLine(listOf(morning, afternoon), folded))
        assertTrue(com.bamnat.app.ui.settings.shiftsLine(snap.shiftTypes, folded).endsWith("· 안 쓰는 종류 2개"))
    }

    @Test fun whatEachKindOfWorkStartsWith() {
        assertEquals(setOf("오전 알바", "오후 알바", "저녁 알바"), FoldedTypes.startNames(WorkStyle.HOURLY))
        assertTrue(FoldedTypes.startNames(WorkStyle.FIELD).isEmpty())
        assertTrue((FoldedTypes.startNames(WorkStyle.HOURLY) - FoldedTypes.startNames(WorkStyle.FIELD)).size == 3)
    }
}
