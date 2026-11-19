package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.ImportedEventEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 2.0.0 [RVL-50] (검토 U12-04) What a restore changes, said first: how old the file is, what gets fewer, what more.
 * 2.0.0 [RVL-53] (검토 DATA-15) The events brought in from the phone's calendar go in the backup, and a backup put back replaces them
 * (an older one without them empties the list).
 */
@RunWith(AndroidJUnit4::class)
class RestoreDiffAndImportedEventsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    private val today = LocalDate.of(2026, 11, 12)
    private fun s(memos: Int = 0, money: Int = 0, days: Int = 0, types: Int = 4, pay: Boolean = false, staff: Int = 0) =
        BackupSummary(shiftTypes = types, days = days, memos = memos, exportedAtMillis = 0, moneyLines = money, hasPay = pay, staff = staff)

    @Test fun fewerComesFirstInTheUsersWords() {
        val d = RestoreDiff.of(s(memos = 4, money = 9, days = 30), s(memos = 12, money = 40, days = 27), today.minusDays(12), today)
        assertEquals("이 파일은 12일 전 것이에요", d.ageLine)
        assertEquals("메모 8개·돈 기록 31줄이 줄어요", d.fewerLine)
        assertEquals("바꾼 날짜 3일이 늘어요", d.moreLine)
    }

    @Test fun todayYesterdayAndNoDate() {
        assertEquals("오늘 저장한 파일이에요", RestoreDiff.of(s(), s(), today, today).ageLine)
        assertEquals("어제 저장한 파일이에요", RestoreDiff.of(s(), s(), today.minusDays(1), today).ageLine)
        assertNull(RestoreDiff.of(s(), s(), null, today).ageLine)
        // The same counts say nothing of more or fewer (the records may still differ).
        val same = RestoreDiff.of(s(memos = 3), s(memos = 3), today, today)
        assertNull(same.fewerLine)
        assertNull(same.moreLine)
    }

    @Test fun aPaySetupThatGoesIsSaid() {
        assertEquals("급여 설정이 없어져요", RestoreDiff.of(s(), s(pay = true), today, today).fewerLine)
        assertEquals("직원 2명이 줄고, 급여 설정이 없어져요", RestoreDiff.of(s(), s(pay = true, staff = 2), today, today).fewerLine)
    }

    @Test fun theParticleFollowsTheLastSound() {
        assertEquals("가", RestoreDiff.subject("메모 8개"))
        assertEquals("이", RestoreDiff.subject("돈 기록 31줄"))
        assertEquals("이", RestoreDiff.subject("사업장 1곳"))
        assertEquals("이", RestoreDiff.subject("직원 2명"))
    }

    @Test fun importedEventsGoInTheBackupAndComeBack() = runBlocking {
        val backups = BackupManager(db, SettingsRepository(context))
        ScheduleRepository(db).ensureDefaults()
        val dao = db.importedEventDao()
        dao.upsert(ImportedEventEntity("7@2026-11-03T19:00", 20760, null, 1))
        dao.upsert(ImportedEventEntity("9@2026-11-04T09:00", 20761, 3, 2))
        val json = backups.export()
        assertEquals(2, JSONObject(json).getJSONArray(BackupManager.IMPORTED_EVENTS).length())
        // Brought in after the backup: the backup put back has its own list only.
        dao.upsert(ImportedEventEntity("11@2026-11-10T09:00", 20767, null, 3))
        backups.restore(json)
        assertEquals(listOf("7@2026-11-03T19:00", "9@2026-11-04T09:00"), dao.all().map { it.eventKey })
        assertEquals(3L, dao.all().last().memoId)
    }

    @Test fun anOlderBackupEmptiesTheList() = runBlocking {
        val backups = BackupManager(db, SettingsRepository(context))
        ScheduleRepository(db).ensureDefaults()
        val old = JSONObject(backups.export()).apply { remove(BackupManager.IMPORTED_EVENTS) }.toString()
        db.importedEventDao().upsert(ImportedEventEntity("7@2026-11-03T19:00", 20760, null, 1))
        backups.restore(old)
        assertTrue("its days and memos are the backup's now: offered again at the next 가져오기", db.importedEventDao().keys().isEmpty())
    }
}
