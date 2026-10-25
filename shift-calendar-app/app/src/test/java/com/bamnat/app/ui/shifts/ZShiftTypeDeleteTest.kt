package com.bamnat.app.ui.shifts

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.WorkPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * [SHFT-09] 1.25 Z 401 (U25-가): a shift type deleted from the list, its days and work order moved to another type or
 * emptied, and 되돌리기 putting every day, step and 대근 back as it was.
 */
@RunWith(AndroidJUnit4::class)
class ZShiftTypeDeleteTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = ScheduleRepository(db)
    private val day = LocalDate.of(2026, 10, 20)

    @After fun tearDown() = db.close()

    private suspend fun seeded(): Map<String, Long> {
        repo.ensureDefaults()
        val t = repo.shiftTypes.first().associate { it.name to it.id }
        val evening = t.getValue("오후")
        repo.savePattern(WorkPattern.Rotation(LocalDate.of(2026, 10, 1), listOf(t.getValue("주간"), evening, t.getValue("휴무"))))
        repo.setOverride(day, ShiftOverride.Shift(evening))
        repo.setOverride(day.plusDays(1), ShiftOverride.Shift(evening))
        val type = repo.shiftTypes.first().first { it.id == evening }
        repo.addExtra(day.plusDays(2), ExtraEntry(ExtraKind.COVER_ID, type.start, type.end, type.breakMinutes, coveredShiftId = evening))
        return t
    }

    @Test fun movedToAnotherTypeThenUndoneIsAsBefore() = runBlocking {
        val t = seeded()
        val evening = t.getValue("오후")
        val dayShift = t.getValue("주간")
        val before = repo.snapshot.first()
        assertEquals(ScheduleRepository.ShiftTypeUse(days = 2, inPattern = true), repo.shiftTypeUse(evening))

        val removal = repo.deleteShiftTypeUndoable(evening, replaceWith = dayShift)!!
        assertEquals("오후", removal.name)
        val after = repo.snapshot.first()
        assertTrue(after.shiftTypes.none { it.id == evening })
        // The calendar shows 주간 where 오후 was: the work order, the days put by hand and the 대근.
        assertEquals(dayShift, after.resolver.resolve(LocalDate.of(2026, 10, 2)).shiftType?.id)
        assertEquals(ShiftOverride.Shift(dayShift), after.entries.first { it.date == day }.override)
        assertEquals(dayShift, after.entries.first { it.date == day.plusDays(2) }.extras.single().coveredShiftId)

        repo.restoreShiftType(removal)
        val back = repo.snapshot.first()
        assertEquals(before.shiftTypes, back.shiftTypes)
        assertEquals(before.patterns, back.patterns)
        assertEquals(before.entries, back.entries)
    }

    @Test fun emptiedThenUndoneIsAsBefore() = runBlocking {
        val t = seeded()
        val evening = t.getValue("오후")
        val before = repo.snapshot.first()
        val removal = repo.deleteShiftTypeUndoable(evening)!!
        val after = repo.snapshot.first()
        assertFalse(after.entries.any { (it.override as? ShiftOverride.Shift)?.shiftTypeId == evening })
        assertNull(after.resolver.resolve(LocalDate.of(2026, 10, 2)).shiftType?.id)
        repo.restoreShiftType(removal)
        val back = repo.snapshot.first()
        assertEquals(before.shiftTypes, back.shiftTypes)
        assertEquals(before.patterns, back.patterns)
        assertEquals(before.entries, back.entries)
        // A type that is not there any more deletes nothing.
        assertNull(repo.deleteShiftTypeUndoable(9_999L))
    }
}
