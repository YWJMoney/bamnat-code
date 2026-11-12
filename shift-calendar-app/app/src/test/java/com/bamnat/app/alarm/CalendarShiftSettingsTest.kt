package com.bamnat.app.alarm

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.decodeShiftMissions
import com.bamnat.app.data.encodeShiftMissions
import com.bamnat.app.diag.EventLog
import com.bamnat.app.diag.Telemetry
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.model.ShiftRef
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.recipe.PhotoLook
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [R200-01] (검토 SET-01 같은 뿌리) 끄기 미션 and 근무 그림 are kept by calendar and kind of day: each calendar has its own
 * database, so shift 3 of an added calendar is not the main calendar's shift 3. Older backups (plain ids) come back as the
 * main calendar's, and a newer backup read by an older app keeps the main calendar's and leaves the rest alone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CalendarShiftSettingsTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val picture = "0123456789abcdef"
    private val other = "fedcba9876543210"

    @After fun tearDown() {
        c.settingsRepository.update { it.copy(alarmMission = "none", shiftMissions = emptyMap(), photoLook = "") }
    }

    private fun ring(type: Long, calendar: String?) =
        RingRequest("주간", LocalDate.now(), LocalTime.of(7, 0), 60, null, System.currentTimeMillis(), shiftTypeId = type, calendarId = calendar)

    @Test fun aMissionSetInAnAddedCalendarLeavesTheMainCalendarsSameIdAlone() {
        val settings = BootAlarms.RingSettings(mission = AlarmMission.MATH,
            shiftMissions = mapOf(ShiftRef.key("ab12cd", 3) to AlarmMission.SHAKE))
        assertEquals("the added calendar's shift 3", AlarmMission.SHAKE, settings.missionFor(ring(3, "ab12cd")))
        assertEquals("the main calendar's shift 3 keeps the default", AlarmMission.MATH, settings.missionFor(ring(3, null)))
        assertEquals("a memo schedule takes the default", AlarmMission.MATH, settings.missionFor(ring(0, "ab12cd")))
    }

    @Test fun theMissionsAndTheCalendarGoThroughALockedRebootCopy() {
        val keys = mapOf("3" to "shake", "ab12cd:3" to "math")
        assertEquals(keys, decodeShiftMissions(encodeShiftMissions(keys)))
        assertEquals("main first, as before", "3=shake,ab12cd:3=math", encodeShiftMissions(keys))
        val r = ring(3, "ab12cd")
        assertEquals("ab12cd", RingRequest.decode(r.encode())!!.calendarId)
        assertEquals("ab12cd", RingRequest.fromIntent(r.putInto(Intent()))!!.calendarId)
        assertNull(RingRequest.fromIntent(ring(3, null).putInto(Intent()))!!.calendarId)
        // A copy written before 2.0.0 has no calendar: the main one.
        val old = JSONObject(r.encode()).apply { remove("ring_calendar") }.toString()
        assertNull(RingRequest.decode(old)!!.calendarId)
    }

    @Test fun anAlarmFromAnAddedCalendarSaysWhichCalendar() {
        AppSettled.alarms(app)
        val day = ShiftType(1, "주간", "주", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(19, 0), alarmEnabled = true)
        val snap = ScheduleSnapshot(listOf(day), WorkPattern.Rotation(LocalDate.now().minusDays(3), listOf(1L)), emptyList())
        val scheduler = AlarmScheduler(app, Telemetry(EventLog(app), SettingsRepository(app)))
        scheduler.reschedule(listOf(RingingCalendar("알바", snap, "ab12cd")), true, LocalDateTime.now())
        assertEquals("ab12cd", scheduler.upcoming.value!!.plan.calendarId)
        scheduler.reschedule(listOf(RingingCalendar(null, snap, ShiftRef.MAIN)), true, LocalDateTime.now())
        assertNull("the main calendar's alarms read as before", scheduler.upcoming.value!!.plan.calendarId)
        scheduler.reschedule(snap, false)
    }

    /** The settings of a backup made before 2.0.0: plain shift ids, as every older phone wrote them. */
    private fun withOldSettings(json: String): String = JSONObject(json).apply {
        getJSONObject("settings").put("shiftMissions", "3=shake,5=none").put("photoLook", "icon.3=$picture")
    }.toString()

    @Test fun anOldBackupComesBackAsTheMainCalendars() = runBlocking {
        val added = c.calendars.begin("알바").also { c.calendars.finishSetup(it, WorkStyle.HOURLY) }
        try {
            val old = withOldSettings(c.backupManager.export())
            c.settingsRepository.update { it.copy(shiftMissions = emptyMap(), photoLook = "") }
            c.backupManager.restore(old)
            val back = c.settingsRepository.current
            assertEquals(mapOf("3" to "shake", "5" to "none"), back.shiftMissions)
            assertEquals(mapOf(3L to "shake", 5L to "none"), ShiftRef.of(back.shiftMissions, ShiftRef.MAIN))
            assertTrue("nothing for the added calendar", ShiftRef.of(back.shiftMissions, added).isEmpty())
            val look = PhotoLook.decode(back.photoLook)
            assertEquals(picture, look.iconFor(null, 3))
            assertNull(look.iconFor(added, 3))
        } finally {
            c.calendars.delete(added)
        }
    }

    @Test fun aNewBackupGoesRoundWithEachCalendarsOwn() = runBlocking {
        val added = c.calendars.begin("알바").also { c.calendars.finishSetup(it, WorkStyle.HOURLY) }
        try {
            val missions = mapOf("3" to "shake", ShiftRef.key(added, 3) to "math")
            val look = PhotoLook(icons = mapOf("3" to picture, ShiftRef.key(added, 3) to other)).encode()
            c.settingsRepository.update { it.copy(alarmMission = "none", shiftMissions = missions, photoLook = look) }
            val json = c.backupManager.export()
            c.settingsRepository.update { it.copy(shiftMissions = emptyMap(), photoLook = "") }
            c.backupManager.restore(json)
            val back = c.settingsRepository.current
            assertEquals(missions, back.shiftMissions)
            assertEquals(picture, PhotoLook.decode(back.photoLook).iconFor(ShiftRef.MAIN, 3))
            assertEquals(other, PhotoLook.decode(back.photoLook).iconFor(added, 3))
            // An app from before 2.0.0 reading this backup: the main calendar's stay, the rest is left out.
            val saved = JSONObject(json).getJSONObject("settings").getString("shiftMissions")
            assertEquals(mapOf(3L to "shake"), olderAppReads(saved))
        } finally {
            c.calendars.delete(added)
        }
    }

    @Test fun aCalendarDeletedForGoodTakesItsOwnAlong() {
        val added = c.calendars.begin("알바").also { c.calendars.finishSetup(it, WorkStyle.HOURLY) }
        c.settingsRepository.update {
            it.copy(shiftMissions = mapOf("3" to "shake", ShiftRef.key(added, 3) to "math"),
                photoLook = PhotoLook(icons = mapOf("3" to picture, ShiftRef.key(added, 3) to other)).encode())
        }
        c.calendars.delete(added)
        val s: AppSettings = c.settingsRepository.current
        assertEquals(mapOf("3" to "shake"), s.shiftMissions)
        assertEquals(mapOf("3" to picture), PhotoLook.decode(s.photoLook).icons)
    }

    @Test fun aSubstituteForChosenShiftsCoversAnyShiftInAnAddedCalendar() {
        val kinds = com.bamnat.core.model.ExtraKinds(listOf(
            com.bamnat.core.model.ExtraKind(1, "야간 대근", "야대", 0xFF000000, com.bamnat.core.model.ExtraTiming.AsShift(3L)),
            com.bamnat.core.model.ExtraKind(2, "대근", "대", 0xFF000000, com.bamnat.core.model.ExtraTiming.AsShift()),
        ))
        val there = kinds.forAddedCalendar()
        assertTrue(there.kinds.all { (it.timing as com.bamnat.core.model.ExtraTiming.AsShift).shiftTypeIds.isEmpty() })
        assertEquals(setOf(3L), (kinds.byId(1)!!.timing as com.bamnat.core.model.ExtraTiming.AsShift).shiftTypeIds)
    }

    /** How 1.39 read the line ([decodeShiftMissions] before [R200-01]). */
    private fun olderAppReads(text: String): Map<Long, String> = text.split(',').mapNotNull { part ->
        val (id, key) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        id.trim().toLongOrNull()?.let { it to key.trim() }?.takeIf { it.second.isNotEmpty() }
    }.toMap()
}
