package com.bamnat.app.ui.settings

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalTime

/**
 * 1.39.1 [R391-05] 검토 SET-01: the 알람 page shows the calendar picked, but its '첫 알람' chips and '모든 알람 한 번에 옮기기'
 * saved 내 달력's shift with the same number (each calendar has its own database, so ids repeat).
 */
@RunWith(AndroidJUnit4::class)
class AlarmPageCalendarTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    private fun settle() = repeat(25) {
        Thread.sleep(20)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun back() = c.calendars.setActive(PersonalCalendar.MAIN)

    private fun shift(name: String) = ShiftType(0, name, name.take(1), 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(16, 0),
        alarmEnabled = true, alarms = listOf(AlarmSpec(60)))

    @Test fun theChipsAndTheMoveChangeTheCalendarShownOnly() {
        val mine = runBlocking { c.scheduleRepository.saveShiftType(shift("본업")) }
        val id = c.calendars.begin("알바 달력")
        c.calendars.finishSetup(id, WorkStyle.SHIFT)
        c.calendars.setActive(id)
        val other = c.calendars.repository(id)
        val theirs = runBlocking { other.saveShiftType(shift("알바")) }
        val vm = SettingsViewModel(c)

        vm.setFirstAlarm(theirs, 90)
        settle()
        vm.moveAllAlarms(15)
        settle()
        val shown = runBlocking { other.shiftTypes.first() }.first { it.id == theirs }
        assertEquals(listOf(105), shown.alarms.map { it.offsetMinutes })
        // 2.0.0 검토 PC-14 (9차: 두 번째 달력을 보는 채 '첫 알람' 칩이 눌러도 아무것도 안 바뀜): the page reads the same calendar it saves to,
        // so the chip it drew shows the new first alarm.
        val page = runBlocking { c.calendars.activeSnapshot.first() }.shiftTypes.first { it.id == theirs }
        assertEquals(listOf(105), page.alarms.map { it.offsetMinutes })
        // 내 달력 is left alone, whatever its shift with that number.
        val main = runBlocking { c.scheduleRepository.shiftTypes.first() }
        assertEquals(listOf(60), main.first { it.id == mine }.alarms.map { it.offsetMinutes })
    }
}
