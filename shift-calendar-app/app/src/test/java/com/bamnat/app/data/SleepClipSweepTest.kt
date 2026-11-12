package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.SleepEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 1.39.1 [R391-04] 검토 ALM-03: '짧은 소리는 7일 뒤 지워요' only ran when the next night was saved, a night cut before it
 * was saved left its files with no row to find them by, and deleting a sleep kept its sounds.
 */
@RunWith(AndroidJUnit4::class)
class SleepClipSweepTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val zone = ZoneId.of("Asia/Seoul")
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val dir = File(context.cacheDir, "sweep-${System.nanoTime()}").apply { mkdirs() }
    private val night = LocalDateTime.of(2026, 11, 1, 23, 0)

    @After fun close() { db.close(); dir.deleteRecursively() }

    private fun clip(name: String, at: Long) = File(dir, name).apply { writeText("wav"); setLastModified(at) }

    @Test fun oldClipsGoWithoutAnotherNightAndOrphansGoAfterADay() = runBlocking {
        var now = millis(night.plusDays(9))
        val sleep = SleepRepository(db, clock = { now }, zone = { zone })
        val dao = db.sleepDao()
        val at = millis(night.plusHours(2))
        dao.insertEvent(SleepEventEntity(startMillis = at, endMillis = at + 3000, kind = "snore", level = 12f, file = "old.wav"))
        val kept = dao.insertEvent(SleepEventEntity(startMillis = at + 9000, endMillis = at + 12000, kind = "talk", level = 9f, file = "kept.wav"))
        sleep.keepClip(kept, true)
        clip("old.wav", at); clip("kept.wav", at)
        // A night cut before it was saved: files, no rows. One from two days ago, one still being written tonight.
        clip("cut.wav", now - 2 * 86_400_000L)
        clip("tonight.wav", now - 3_600_000L)

        sleep.sweepClips(dir, 7)
        assertFalse("7일 지난 클립", File(dir, "old.wav").exists())
        assertTrue("보관한 클립", File(dir, "kept.wav").exists())
        assertFalse("저장 전에 끊긴 밤의 파일", File(dir, "cut.wav").exists())
        assertTrue("아직 듣는 밤의 파일", File(dir, "tonight.wav").exists())
        assertEquals("the times and kinds stay", 2, dao.allEvents().size)
    }

    @Test fun deletingASleepTakesItsSoundsAndClips() = runBlocking {
        var now = millis(night)
        val sleep = SleepRepository(db, clock = { now }, zone = { zone })
        val dao = db.sleepDao()
        val id = sleep.add(night, night.plusHours(7))
        val other = sleep.add(night.plusDays(1), night.plusDays(1).plusHours(7))
        val at = millis(night.plusHours(3))
        dao.insertEvent(SleepEventEntity(startMillis = at, endMillis = at + 3000, kind = "talk", level = 9f, file = "talk.wav"))
        val next = millis(night.plusDays(1).plusHours(3))
        dao.insertEvent(SleepEventEntity(startMillis = next, endMillis = next + 3000, kind = "snore", level = 9f, file = "next.wav"))
        clip("talk.wav", at); clip("next.wav", next)
        now = millis(night.plusDays(2))

        sleep.deleteWithSounds(id) { File(dir, it).delete() }
        assertFalse(File(dir, "talk.wav").exists())
        assertTrue("another night's clip stays", File(dir, "next.wav").exists())
        assertEquals(listOf("next.wav"), dao.allEvents().map { it.file })
        assertEquals(listOf(other), dao.all().map { it.id })
    }
}
