package com.bamnat.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 1.39 [OBS2-08] 유형별 흐름·기능과 머묾 (사용자 2026-11-08 '유형별·기능 둘만', 처리방침 3.12): nothing of it goes before its rows are on;
 * after, the bundle carries the kind of work and, on coming back, the last open day's features with the window (7 or 30).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class UsageKeepTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val prefs get() = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
    private val today get() = Today.date(zone).toEpochDay()
    private val sent = ArrayList<JSONObject>()

    @Before fun clean() {
        prefs.edit().clear().commit()
        UsageStats.kindAllowed = { false }
        UsageStats.keepAllowed = { false }
        sent.clear()
    }

    @After fun reset() {
        UsageStats.kindAllowed = { false }
        UsageStats.keepAllowed = { false }
        prefs.edit().clear().commit()
    }

    private fun stats() = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, b -> sent += b },
        installDay = { null }, style = { "SHIFT" })

    /** A phone last opened [away] days ago, when it looked at 급여. */
    private fun lastOpened(away: Long) {
        val day = today - away
        val book = UsageBook().record(UsageEvent.OPEN, day).record(UsageEvent.PAY_VIEW, day).record(UsageEvent.LIFE_NEW, day)
        prefs.edit().putString("book", book.encode()).putLong("last_open_day", day).commit()
    }

    @Test fun beforeThePolicyRowsNeitherTheKindNorTheFeaturesGo() = runBlocking {
        lastOpened(3)
        val s = stats()
        s.record(UsageEvent.OPEN)
        assertTrue(s.sendIfDue(true))
        assertFalse(sent.single().has("style"))
        assertFalse(sent.single().has("keep"))
        assertNull(prefs.all["keep"])
    }

    @Test fun comingBackWithinAWeekSendsTheLastDaysFeaturesOnceWithTheKind() = runBlocking {
        UsageStats.kindAllowed = { true }
        UsageStats.keepAllowed = { true }
        lastOpened(3)
        val s = stats()
        s.record(UsageEvent.OPEN)
        s.record(UsageEvent.OPEN) // the same day again adds nothing
        assertTrue(s.preview()!!.contains("\"keep\""))
        assertTrue(s.sendIfDue(true))
        val body = sent.single()
        assertEquals("SHIFT", body.getString("style"))
        val keep = body.getJSONArray("keep")
        assertEquals(1, keep.length())
        val mark = keep.getJSONObject(0)
        assertEquals(LocalDate.ofEpochDay(today - 3).toString(), mark.getString("day"))
        assertEquals(7, mark.getInt("win"))
        // life_* is about the phone, not a feature.
        assertEquals("[\"open\",\"pay_view\"]", mark.getJSONArray("events").toString())
        assertNull("sent marks are gone", prefs.all["keep"])
    }

    @Test fun twoWeeksAwayIsTheThirtyDayWindowAndLongerSaysNothing() {
        UsageStats.keepAllowed = { true }
        lastOpened(14)
        stats().record(UsageEvent.OPEN)
        assertTrue(prefs.getString("keep", "")!!.contains("\t30\t"))

        prefs.edit().clear().commit()
        lastOpened(31)
        stats().record(UsageEvent.OPEN)
        assertNull(prefs.all["keep"])
    }

    @Test fun turningUsageStatsOffForgetsWaitingMarks() {
        UsageStats.keepAllowed = { true }
        lastOpened(2)
        val s = stats()
        s.record(UsageEvent.OPEN)
        assertTrue(prefs.contains("keep"))
        s.clear()
        assertNull(prefs.all["keep"])
    }
}
