package com.bamnat.app.ops

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.diag.CrashLog
import com.bamnat.app.diag.CrashStats
import com.bamnat.app.diag.UsageStats
import com.bamnat.core.stats.CrashMarks
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVL-90] (검토 I12c-03, 처리방침 3.14 문장표 6, 깃발 stats.crash) 앱이 멈춘 곳 횟수: nothing is kept before the row is on, a
 * finished day goes with 사용 통계 as kind·place·value·count and nothing else, and turning 사용 통계 off forgets it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CrashStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private var on = false
    private val stats get() = CrashStats(context, zone = { zone }, allowedNow = { on })

    @Before fun day() {
        // The app builds its container as it starts (forgetUnless, the rows' hooks): wait for it first (OperationsAppTest QA-09).
        context.appContainer
        Today.fix(ZonedDateTime.of(2026, 12, 22, 10, 0, 0, 0, zone))
        context.getSharedPreferences("crash_stats", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun back() {
        Today.reset()
        CrashStats.current = null
        CrashStats.allowed = { false }
        CrashLog.clear(context)
        com.bamnat.app.diag.MainStall.closing = false
    }

    @Test fun beforeTheRowIsOnNothingIsKept() {
        stats.record(CrashMarks.CRASH, listOf("PayViewModel.save:212"))
        on = true
        Today.fix(ZonedDateTime.of(2026, 12, 23, 10, 0, 0, 0, zone))
        assertNull(stats.payload())
    }

    @Test fun aFinishedDayGoesAsKindPlaceValueAndCountThenIsGone() {
        on = true
        val at = listOf("PayViewModel.save:212", "a.b.c.d:12")
        repeat(2) { stats.record(CrashMarks.CRASH, at) }
        assertNull("today is still running", stats.payload())

        Today.fix(ZonedDateTime.of(2026, 12, 23, 9, 0, 0, 0, zone))
        stats.record(CrashMarks.ANR, listOf("Backup.write:88"))
        val sent = stats.payload()!!
        assertEquals(1, sent.length())
        val m = sent.getJSONObject(0)
        assertEquals(setOf("day", "kind", "sig", "at", "n"), m.keys().asSequence().toSet())
        assertEquals("2026-12-22", m.getString("day"))
        assertEquals("crash", m.getString("kind"))
        assertEquals(CrashMarks.sig("crash", at), m.getString("sig"))
        assertEquals(at, (0 until m.getJSONArray("at").length()).map { m.getJSONArray("at").getString(it) })
        assertEquals(2, m.getInt("n"))

        stats.sent()
        assertNull(stats.payload())
        Today.fix(ZonedDateTime.of(2026, 12, 24, 9, 0, 0, 0, zone))
        assertEquals("today's stays for the next bundle", "anr", stats.payload()!!.getJSONObject(0).getString("kind"))
        // The row going off again sends nothing (what waits is not lost if it comes back the same week).
        on = false
        assertNull(stats.payload())
    }

    @Test fun itRidesWithTheUsageBundleAndTurningUsageOffForgetsIt() = runBlocking {
        on = true
        val crashes = stats
        val today = Today.date(zone).toEpochDay()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit()
            .putString("book", UsageBook().record(UsageEvent.OPEN, today - 1).encode()).commit()
        crashes.record(CrashMarks.CRASH, listOf("PayViewModel.save:212"), Today.millis() - 86_400_000L)
        val sent = mutableListOf<JSONObject>()
        val usage = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, body -> sent += body }, crashes = crashes)
        assertTrue(JSONObject(usage.preview()!!).has("crash"))
        assertTrue(usage.sendIfDue(allowed = true))
        assertEquals("PayViewModel.save:212", sent.single().getJSONArray("crash").getJSONObject(0).getJSONArray("at").getString(0))
        assertNull("sent once", crashes.payload())

        crashes.record(CrashMarks.CRASH, listOf("PayViewModel.save:212"), Today.millis() - 86_400_000L)
        usage.clear()
        assertNull(crashes.payload())
    }

    @Test fun theCrashHandlerCountsAStopOnlyWhenAllowed() {
        val s = CrashStats(context, zone = { zone })
        CrashStats.current = s
        val error = IllegalStateException("never sent")
        error.stackTrace = arrayOf(StackTraceElement("com.bamnat.app.ui.pay.PayViewModel", "save", "PayViewModel.kt", 212))
        CrashLog.stopped(context, Thread.currentThread(), error, null)
        CrashStats.allowed = { true }
        CrashLog.stopped(context, Thread.currentThread(), error, null)
        Today.fix(ZonedDateTime.of(2026, 12, 23, 9, 0, 0, 0, zone))
        val m = s.payload()!!.getJSONObject(0)
        assertEquals(1, m.getInt("n"))
        assertEquals("PayViewModel.save:212", m.getJSONArray("at").getString(0))
        assertFalse(m.toString().contains("never sent"))
    }
}
