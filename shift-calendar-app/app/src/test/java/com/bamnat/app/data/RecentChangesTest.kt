package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 2.0.0 [RVH-65] 검토 PC-73 최근 바꾼 기록: the days changed by hand across months, the one changed last first, each with its changes
 * newest first, and nothing from before the stretch asked for.
 */
@RunWith(AndroidJUnit4::class)
class RecentChangesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private var now = at(2026, 11, 1)
    private val schedule = ScheduleRepository(db, clock = { now })

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, m, d, h, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()

    @After fun close() = db.close()

    @Test fun daysAcrossMonthsComeLastChangedFirstEachWithItsChanges() = runBlocking {
        now = at(2026, 9, 1)
        schedule.setOverride(LocalDate.of(2026, 9, 3), ShiftOverride.Off) // before the stretch: not shown
        now = at(2026, 11, 2)
        schedule.setLeave(LocalDate.of(2026, 12, 17), Leave())
        now = at(2026, 11, 3)
        schedule.setOverride(LocalDate.of(2026, 11, 20), ShiftOverride.Off)
        now = at(2026, 11, 4)
        schedule.setAlarmSkipped(LocalDate.of(2026, 12, 17), true)

        val days = schedule.recentChanges(at(2026, 10, 1)).first()
        assertEquals(listOf(LocalDate.of(2026, 12, 17), LocalDate.of(2026, 11, 20)), days.map { it.first })
        val first = days.first().second
        assertEquals(2, first.size)
        assertEquals(at(2026, 11, 4), first.first().atMillis)
    }
}
