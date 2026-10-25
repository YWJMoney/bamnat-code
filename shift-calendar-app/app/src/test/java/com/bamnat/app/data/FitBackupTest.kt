package com.bamnat.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** [FIT-02] What the user added under 더 보기 stays with its calendar, through a backup and back. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FitBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test fun addedToolsSurviveTheBackup() = runBlocking {
        val guide = GuideStore.get(context)
        val backups = BackupManager(db, SettingsRepository(context), guide = guide)
        ScheduleRepository(db).ensureDefaults()
        val key = FitTable.item(FitSurface.CALENDAR_TOOLS, "pattern")!!.key
        guide.setAdded("inv", key, true)
        assertTrue(FitTable.visible(FitSurface.CALENDAR_TOOLS, "pattern", WorkStyle.INVEST, guide.current.addedTo("inv")))
        val json = backups.export()
        guide.setAdded("inv", key, false)
        assertFalse(FitTable.visible(FitSurface.CALENDAR_TOOLS, "pattern", WorkStyle.INVEST, guide.current.addedTo("inv")))
        backups.restore(json)
        assertEquals(setOf(key), guide.current.addedTo("inv"))
        // 빼기 puts it back away.
        guide.setAdded("inv", key, false)
        assertEquals(emptySet<String>(), guide.current.addedTo("inv"))
    }
}
