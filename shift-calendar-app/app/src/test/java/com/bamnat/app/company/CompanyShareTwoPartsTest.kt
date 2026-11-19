package com.bamnat.app.company

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleRepository
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.pay.PayStore
import com.bamnat.core.model.WorkPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * 2.0.0 [RVH-44] 결정 19번 하루 두 구간 in the company file (설계 4-4절, 총괄 11-12: 파일은 v4 그대로, 키 gapStart·gap·rest·restStart,
 * rest + gap = break). An app before 2.0.0 reads "break"·"breakStart" only and works the same minutes; 2.0.0 reads the parts apart.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanyShareTwoPartsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val store = PayStore(context)
    private val today = LocalDate.of(2026, 11, 12)

    @After fun close() {
        db.close()
        runBlocking { store.save(null) }
    }

    private suspend fun shop(): ScheduleRepository {
        val schedule = ScheduleRepository(db)
        schedule.ensureDefaults()
        val day = schedule.snapshot.first().shiftTypes.first { it.name == "주간" }
        schedule.saveShiftType(day.copy(start = LocalTime.of(11, 0), end = LocalTime.of(22, 0), breakMinutes = 30, breakStart = LocalTime.of(12, 0),
            gapStart = LocalTime.of(15, 0), gapMinutes = 120))
        schedule.setFirstPattern(WorkPattern.Rotation(today, listOf(day.id, day.id, null), today), today)
        return schedule
    }

    private fun types(text: String) = JSONObject(text).getJSONArray("shiftTypes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    @Test fun theFileSaysBothWaysAndTheSumHolds() = runBlocking {
        val share = CompanyShare(shop(), store, settings, today = { today })
        val text = share.export("예시식당", withAmounts = false)
        assertEquals("새 판 없음: 구간은 새 키로만", CompanyShare.BASE_VERSION, JSONObject(text).getInt("v"))
        // The file that goes to the company server (갈래 D HQ-08 checks rest + gap = break there) says the same.
        val server = share.exportForServer(cycleOnly = true).toString()
        assertTrue(JSONObject(server).getInt("v") <= CompanyShare.VERSION)
        for (t in types(text) + types(server)) {
            if (!t.has("gap")) {
                assertFalse("${t.getString("name")}: 한 구간은 새 키 없음", t.has("rest") || t.has("restStart") || t.has("gapStart"))
                continue
            }
            assertEquals("rest + gap = break", t.getInt("break"), t.getInt("rest") + t.getInt("gap"))
            assertEquals(150, t.getInt("break"))
            assertEquals("옛 앱: 구간 사이 시작부터 쉬는 것으로", "15:00", t.getString("breakStart"))
            assertEquals("12:00", t.getString("restStart"))
            assertEquals("15:00", t.getString("gapStart"))
        }
        assertEquals(1, types(text).count { it.has("gap") })
        assertEquals(1, types(server).count { it.has("gap") })
    }

    @Test fun twoPointOhReadsThePartsApartAndAnOldFileStaysOnePart() = runBlocking {
        val share = CompanyShare(shop(), store, settings, today = { today })
        val text = share.export("예시식당", withAmounts = false)
        val day = share.read(text).types.single { it.name == "주간" }
        assertTrue(day.hasGap)
        assertEquals(30, day.breakMinutes)
        assertEquals(LocalTime.of(12, 0), day.breakStart)
        assertEquals(LocalTime.of(15, 0), day.gapStart)
        assertEquals(120, day.gapMinutes)
        // What an app before 2.0.0 sees: the same minutes worked (11~22 less 150).
        val old = day.copy(breakMinutes = day.breakMinutes + day.gapMinutes, gapStart = null, gapMinutes = 0)
        assertEquals(old.workMinutes, 11 * 60 - 150)

        // A file without "gap" (before 2.0.0): its "break" is the break, never guessed to be a gap.
        val root = JSONObject(text)
        val a = root.getJSONArray("shiftTypes")
        for (i in 0 until a.length()) listOf("rest", "restStart", "gapStart", "gap").forEach { a.getJSONObject(i).remove(it) }
        val plain = share.read(root.toString()).types.single { it.name == "주간" }
        assertFalse(plain.hasGap)
        assertEquals(150, plain.breakMinutes)
        assertEquals(LocalTime.of(15, 0), plain.breakStart)
        assertNull(plain.gapStart)

        // A gap that does not fit the day (a hand-made file) is not used: the old keys stand.
        val bad = JSONObject(text)
        val b = bad.getJSONArray("shiftTypes")
        for (i in 0 until b.length()) b.getJSONObject(i).takeIf { it.has("gap") }?.put("gapStart", "21:50")
        val notFit = share.read(bad.toString()).types.single { it.name == "주간" }
        assertFalse(notFit.hasGap)
        assertEquals(150, notFit.breakMinutes)
    }

    @Test fun applyingAFileGivesTheSameShopAndKeepsTheUsersOwnName() = runBlocking {
        val text = CompanyShare(shop(), store, settings, today = { today }).export("예시식당", withAmounts = false)
        val other = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val theirs = ScheduleRepository(other).also { it.ensureDefaults() }
            CompanyShare(theirs, store, settings, today = { today }).apply(text, rotation = true)
            val day = theirs.snapshot.first().shiftTypes.single { it.name == "주간" }
            assertTrue(day.hasGap)
            assertEquals(LocalTime.of(15, 0), day.gapStart)
            assertEquals(120, day.gapMinutes)
            assertEquals(30, day.breakMinutes)
        } finally {
            other.close()
        }
    }
}
