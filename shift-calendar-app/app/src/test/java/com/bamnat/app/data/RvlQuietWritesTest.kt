package com.bamnat.app.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.diag.UsageStats
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * 2.0.0 [RVL-67] (검토 DATA-24) Small work the main thread did on every tap: a settings change put all of the settings again, and every
 * screen move unpacked the whole usage book. Now only the changed keys are written and the book is read again only when its text changed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RvlQuietWritesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val usage get() = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
    private val zone = ZoneId.of("Asia/Seoul")
    private val heard = mutableListOf<String?>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> heard += key }

    @Before fun clean() {
        settings.edit().clear().commit()
        usage.edit().clear().commit()
    }

    @After fun reset() {
        settings.unregisterOnSharedPreferenceChangeListener(listener)
        settings.edit().clear().commit()
        usage.edit().clear().commit()
    }

    @Test fun aSettingsChangeWritesOnlyWhatChanged() {
        val repo = SettingsRepository(context)
        repo.update { it.copy(showLunar = false) }
        val before = settings.all.toMap()
        settings.registerOnSharedPreferenceChangeListener(listener)
        // Nothing changed: nothing is written, nobody is told.
        repo.update { it }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(emptyList<String?>(), heard)
        // One switch: that key only, and the rest stays as it was.
        repo.update { it.copy(showLunar = true) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, heard.size)
        assertEquals(true, repo.current.showLunar)
        assertEquals(before.filterKeys { it != heard.single() }, settings.all.filterKeys { it != heard.single() })
        // What a fresh read sees is what was kept.
        assertEquals(repo.current, SettingsRepository(context).current)
    }

    @Test fun theUsageBookIsReadAgainOnlyWhenItsTextChanged() {
        UsageStats.kindAllowed = { false }
        val stats = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, _ -> }, installDay = { null })
        stats.record(UsageEvent.PAY_VIEW)
        stats.record(UsageEvent.PAY_VIEW)
        val day = Today.date(zone).toEpochDay()
        assertEquals(2, UsageBook.decode(usage.getString("book", null)).days[day]?.get(UsageEvent.PAY_VIEW.key))
        // Put back from outside (a backup, a test): the next event counts from what is saved now, not from what was held.
        usage.edit().putString("book", UsageBook().record(UsageEvent.PAY_VIEW, day).encode()).commit()
        stats.record(UsageEvent.PAY_VIEW)
        assertEquals(2, UsageBook.decode(usage.getString("book", null)).days[day]?.get(UsageEvent.PAY_VIEW.key))
        // Cleared: it starts again from nothing.
        stats.clear()
        stats.record(UsageEvent.PAY_VIEW)
        assertTrue(UsageBook.decode(usage.getString("book", null)).days[day]?.get(UsageEvent.PAY_VIEW.key) == 1)
    }
}
