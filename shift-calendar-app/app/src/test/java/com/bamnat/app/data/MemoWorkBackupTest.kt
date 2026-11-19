package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.WorkAnchor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * 2.0.0 (DB 35) [RVIB-04] 검토 I07ac-01: a memo told by the work ('출근 1시간 전') goes to a new phone as it is, through the backup
 * file that 설정 › 데이터 › 새 휴대폰으로 옮기기 makes. A backup of 1.39.0 (DB 33) or of 34 has no such key and its memos are told by
 * the clock, as they were there.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MemoWorkBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val newPhone = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val settings = SettingsRepository(context)
    private val at = LocalDateTime.of(2026, 11, 12, 21, 0)
    private val beforeWork = WorkAnchor(WorkAnchor.From.START, -60)

    @Before fun days() = runBlocking {
        ScheduleRepository(db).ensureDefaults()
        ScheduleRepository(newPhone).ensureDefaults()
    }

    @After fun close() {
        db.close()
        newPhone.close()
    }

    private fun memo(id: Long, work: WorkAnchor?) =
        Memo(id = id, date = at.toLocalDate(), title = "인수인계 $id", reminder = MemoReminder(at, ring = false, notify = true, work = work)).toEntity()

    @Test fun aMemoToldByTheWorkGoesToANewPhone() = runBlocking {
        db.memoDao().insert(memo(1, beforeWork))
        db.memoDao().insert(memo(2, null))
        val backup = BackupManager(db, settings).export()
        assertEquals("S-60", JSONObject(backup).getJSONArray("memos").let { a -> (0 until a.length()).map(a::getJSONObject) }
            .first { it.getLong("id") == 1L }.getString("remindWork"))
        BackupManager(newPhone, settings).restore(backup)
        assertEquals(MemoReminder(at, ring = false, notify = true, work = beforeWork), newPhone.memoDao().get(1)!!.toModel().reminder)
        assertEquals(MemoReminder(at, ring = false, notify = true), newPhone.memoDao().get(2)!!.toModel().reminder)
        // And back again, as the next backup from the new phone.
        assertEquals(backup.let(::memosOf), BackupManager(newPhone, settings).export().let(::memosOf))
    }

    @Test fun aBackupOf139Or34IsToldByTheClock() = runBlocking {
        db.memoDao().insert(memo(1, beforeWork))
        val root = JSONObject(BackupManager(db, settings).export())
        val memos = root.getJSONArray("memos")
        // What 1.39.0 (DB 33) and 34 wrote: no 'remindWork' at all.
        for (i in 0 until memos.length()) memos.getJSONObject(i).remove("remindWork")
        BackupManager(newPhone, settings).restore(root.toString())
        val back = newPhone.memoDao().get(1)!!
        assertNull(back.remindWork)
        assertEquals(MemoReminder(at, ring = false, notify = true), back.toModel().reminder)
        // One a later app wrote that this one cannot read is told by the clock too, not dropped.
        memos.getJSONObject(0).put("remindWork", "Q9")
        BackupManager(newPhone, settings).restore(root.toString())
        assertNull(newPhone.memoDao().get(1)!!.remindWork)
        assertEquals(at, newPhone.memoDao().get(1)!!.toModel().reminder!!.at)
    }

    private fun memosOf(json: String): String = JSONObject(json).getJSONArray("memos").toString()
}
