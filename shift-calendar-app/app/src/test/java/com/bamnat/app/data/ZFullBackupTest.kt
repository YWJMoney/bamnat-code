package com.bamnat.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [BAK-01] 1.25 Z 402 (U8): "모든 설정과 기록을 백업해서 새 휴대폰으로". End to end through the app's own backup: every
 * setting and every settings file set, backed up, the phone wiped to a new one's state, restored, and all of it the
 * same again; what stays on the phone (Z-D3) stays. Two guards keep it so: a new [AppSettings] field and a new
 * preferences file each have to be put in the backup or on the phone's list with a reason.
 */
@RunWith(AndroidJUnit4::class)
class ZFullBackupTest {
    private val app = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    /** Every field that travels set away from its default. */
    private fun sample(s: AppSettings, shiftId: Long) = s.copy(
        weekStart = DayOfWeek.MONDAY, weeklyLimitHours = 60, averageLimitHours = 48, themeMode = ThemeMode.DARK,
        dynamicColor = true, showHoursInCells = false, showLunar = true, vibrate = false, gradualVolume = false,
        snoozeMinutes = 9, ringMinutes = 15, alarmsEnabled = false, hideDayAlarmNotice = true, shareUsageStats = true,
        plazaNews = false, plazaHidden = com.bamnat.core.plaza.HideList().hide("w".repeat(64), "글쓴이", 1_000L).encode(), plazaRulesAgreed = 2,
        workStyle = WorkStyle.SHIFT, rosterMonthly = true,
        extraKinds = ExtraKinds(ExtraKinds.DEFAULTS.map { if (it.id == ExtraKind.COVER_ID) it.copy(name = "대신 근무") else it }),
        memoInCells = true, calendarMemos = false, restCheck = false, strictRest = true, restRules = "11h", sleepTips = false,
        todayShiftNotice = true, briefing = true, commuteNotice = true, bedtimeNotice = true, sleepingNotice = false,
        quietWhileAsleep = false, moneyNotices = false, leaveNotice = false, sleepTargetMinutes = 8 * 60, commuteMinutes = 40,
        restDayCompared = true, phoneDayView = PhoneDayView.WEEK, showDayCard = true, appLock = true,
        calendarLayout = "cards:a", statsLayout = "stats:b", dayGroupOrder = listOf("memo", "money"), phoneCalendars = listOf("3"),
        alarmMission = "math", shiftMissions = mapOf(shiftId to "math"), wakeCheckMinutes = 10, widgetMemo = false,
        backupPhotos = false, moneyLayout = "money:c", memoLayout = "memo:d", rateHint = false, alarmSuggest = false,
    )

    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val marksFile get() = File(app.filesDir, "recurring_marks.json")

    @Test fun everySettingAndRecordComesBackOnANewPhone() = runBlocking {
        c.scheduleRepository.ensureDefaults()
        val shiftId = c.scheduleRepository.shiftTypes.first().first().id
        c.settingsRepository.update { sample(it, shiftId) }
        val wanted = c.settingsRepository.current
        // A value in every settings file that travels, and one of each kind.
        prefs("work_type_prefs").edit().putInt("cap_main", 52).putString("tax_main", "WITHHOLDING_3_3").commit()
        prefs("company").edit().putString("applied", "{\"id\":\"c1\"}").putStringSet("sent", setOf("r1", "r2")).putString("device", "this-phone").commit()
        prefs("plaza_rate_hint").edit().putString("own", "간호사|서울").commit()
        prefs("calendar_view").edit().putBoolean("listView", true).putStringSet("redWorked", setOf("2026-10")).commit()
        prefs("auto_record").edit().putLong("since", 123L).putFloat("weight", 0.5f).commit()
        prefs("share_group").edit().putString("group", "A조").commit()
        prefs("calendar_import").edit().putBoolean("auto", true).putString("chosen", "7,8").commit()
        val marks = RecurringMarks(marksFile)
        marks.put(mapOf(5L to LocalDate.of(2026, 10, 25)))
        val file = c.backupManager.export()
        assertTrue(JSONObject(file).has("settingsFiles"))

        // A new phone: nothing set, nothing in the files, its own install id and calendar choice.
        c.settingsRepository.update { AppSettings() }
        for (p in SettingsFilesBackup.PARTS) prefs(p.name).edit().clear().commit()
        prefs("company").edit().putString("device", "new-phone").commit()
        prefs("calendar_import").edit().putString("chosen", "1").commit()
        marksFile.delete()
        assertEquals(emptyMap<Long, LocalDate>(), marks.all())

        c.backupManager.restore(file)

        val back = c.settingsRepository.current
        for (f in settingFields()) {
            if (f.name in SettingsFilesBackup.SETTINGS_ON_PHONE) continue
            assertEquals("설정 ${f.name}", f.get(wanted), f.get(back))
        }
        assertEquals(52, prefs("work_type_prefs").getInt("cap_main", 0))
        assertEquals("{\"id\":\"c1\"}", prefs("company").getString("applied", null))
        assertEquals(setOf("r1", "r2"), prefs("company").getStringSet("sent", null))
        assertEquals("간호사|서울", prefs("plaza_rate_hint").getString("own", null))
        assertEquals(true, prefs("calendar_view").getBoolean("listView", false))
        assertEquals(setOf("2026-10"), prefs("calendar_view").getStringSet("redWorked", null))
        assertEquals(123L, prefs("auto_record").getLong("since", 0))
        assertEquals(0.5f, prefs("auto_record").getFloat("weight", 0f))
        assertEquals("A조", prefs("share_group").getString("group", null))
        assertEquals(true, prefs("calendar_import").getBoolean("auto", false))
        // What is this phone's own stays this phone's own.
        assertEquals("new-phone", prefs("company").getString("device", null))
        assertEquals("1", prefs("calendar_import").getString("chosen", null))
        // The repeating money lines know how far they were written, read again without restarting the app.
        assertEquals(mapOf(5L to LocalDate.of(2026, 10, 25)), marks.all())
        c.settingsRepository.update { AppSettings() }
    }

    /** [BAK-01] Guard: a new setting goes in the backup (and in [sample]) or on the phone's list, never nowhere. */
    @Test fun everySettingIsBackedUpOrKeptOnThePhoneOnPurpose() {
        val fresh = AppSettings()
        val sampled = sample(fresh, 1L)
        val names = settingFields().map { it.name }.toSet()
        val missing = settingFields().filter { f -> f.name !in SettingsFilesBackup.SETTINGS_ON_PHONE && f.get(fresh) == f.get(sampled) }.map { it.name }
        assertEquals("백업에도 '휴대폰에만' 목록에도 없는 설정 칸", emptyList<String>(), missing)
        assertEquals("목록에만 있고 설정에는 없는 칸", emptySet<String>(), SettingsFilesBackup.SETTINGS_ON_PHONE.keys - names)
    }

    /** [BAK-01] Guard: every preferences file the app writes is backed up, carried in a part of its own, or kept here with a reason. */
    @Test fun everySettingsFileIsBackedUpOrKeptOnThePhoneOnPurpose() {
        val known = SettingsFilesBackup.PARTS.map { it.name }.toSet() + SettingsFilesBackup.ELSEWHERE +
            SettingsFilesBackup.DEVICE_ONLY.keys.filter { ':' !in it }
        val found = sortedSetOf<String>()
        val unread = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            Regex("""getSharedPreferences\(\s*"([^"]+)"""").findAll(text).forEach { found += it.groupValues[1] }
            Regex("""getSharedPreferences\(\s*([A-Za-z_.]+)""").findAll(text).forEach { m ->
                val ref = m.groupValues[1]
                when {
                    ref == "part.name" -> Unit
                    ref.startsWith("prefsName") -> found += "share_link"
                    else -> Regex("""const val $ref = "([^"]+)"""").find(text)?.let { found += it.groupValues[1] } ?: run { unread += "${f.name}: $ref" }
                }
            }
        }
        assertEquals("이름을 읽지 못한 설정 파일", emptyList<String>(), unread)
        assertTrue("찾은 설정 파일이 너무 적어요: $found", found.size >= 20)
        assertEquals("백업에도 '휴대폰에만' 목록에도 없는 설정 파일", emptySet<String>(), found - known)
    }

    private fun settingFields() = AppSettings::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .onEach { it.isAccessible = true }
}
