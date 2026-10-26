package com.bamnat.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.StartupTrace
import com.bamnat.app.StartupTrace.Kind
import com.bamnat.app.StartupTrace.Sample
import com.bamnat.app.appContainer
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
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
 * [PERF-15][PERF-16] 1.28 AO: startup times stay on the phone for 진단; with the user's yes and the 3.2 flag they go
 * with 사용 통계 as bucketed middle values, never otherwise, and never the times of openings made before the yes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StartupStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var allowed = true
    private val zone = ZoneId.of("Asia/Seoul")

    @Before fun clean() {
        context.appContainer // [QA-09] the start's own clean-up first
        context.getSharedPreferences("startup_stats", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun reset() {
        StartupStats.policyAllows = { false }
    }

    private fun stats() = StartupStats(context, allowed = { allowed })

    @Test fun bucketsHideTheExactTime() {
        assertEquals(100L, StartupStats.bucket(0))
        assertEquals(100L, StartupStats.bucket(100))
        assertEquals(700L, StartupStats.bucket(501))
        assertEquals(1_000L, StartupStats.bucket(999))
        assertEquals(20_000L, StartupStats.bucket(45_000))
        assertEquals("1.2초", StartupStats.seconds(1_234))
        assertEquals("0.1초", StartupStats.seconds(20))
    }

    @Test fun theMiddleValueOfEachKindIsSent() {
        val s = stats()
        s.record(Sample(Kind.COLD, 300, 150, 1_900))
        s.record(Sample(Kind.COLD, 0, 120, 1_100))
        s.record(Sample(Kind.COLD, 900, 400, 5_200))
        s.record(Sample(Kind.WARM, 0, 0, 260))
        val p = s.payload()!!
        assertEquals(setOf("container", "activity", "firstMonth", "warm"), p.keys().asSequence().toSet())
        assertEquals(300L, p.getLong("container"))
        assertEquals(200L, p.getLong("activity"))
        assertEquals(2_000L, p.getLong("firstMonth"))
        assertEquals(setOf("container", "firstMonth"), p.getJSONObject("warm").keys().asSequence().toSet())
        assertEquals(300L, p.getJSONObject("warm").getLong("firstMonth"))
        assertEquals(Sample(Kind.WARM, 0, 0, 260), s.last)
        assertEquals("마지막 시작: 첫 달까지 0.3초 (다시 열기)", StartupStats.describe(s.last))
        s.sent()
        assertNull("what went is not sent again", s.payload())
        assertEquals("진단 keeps the last one", Sample(Kind.WARM, 0, 0, 260), s.last)
    }

    @Test fun withoutTheYesNothingIsKeptForLater() {
        allowed = false
        val s = stats()
        s.record(Sample(Kind.COLD, 0, 100, 900))
        assertNull(s.payload())
        assertEquals("진단 still shows it", 900L, s.last!!.firstMonth)
        // A yes given later does not send openings from before it.
        allowed = true
        assertNull(s.payload())
        assertEquals("마지막 시작: 첫 달까지 0.9초 (새로 열기)", StartupStats.describe(s.last))
        assertEquals("마지막 시작: 아직 잰 적 없어요(달력이 열리면 재요)", StartupStats.describe(null))
    }

    @Test fun theUsageBundleCarriesItOnlyWhenAllowed() = runBlocking {
        val today = LocalDate.now(zone).toEpochDay()
        val prefs = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
        prefs.edit().putString("book", UsageBook().record(UsageEvent.OPEN, today - 1).encode()).commit()
        val s = stats()
        s.record(Sample(Kind.COLD, 120, 90, 1_300))
        val sent = mutableListOf<JSONObject>()
        val usage = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, body -> sent += body }, startup = s)
        // The preview shows it too, before it goes.
        assertTrue(JSONObject(usage.preview()!!).has("startup"))
        assertTrue(usage.sendIfDue(allowed = true))
        val body = sent.single()
        assertEquals(1_500L, body.getJSONObject("startup").getLong("firstMonth"))
        assertNull("sent once", s.payload())
        // Not allowed (no 3.2 flag): the bundle is as before, nothing of startup in it.
        allowed = false
        s.record(Sample(Kind.COLD, 0, 90, 1_300))
        prefs.edit().putString("book", UsageBook().record(UsageEvent.OPEN, today - 1).encode()).remove("sent_day").commit()
        assertTrue(usage.sendIfDue(allowed = true))
        assertFalse(sent.last().has("startup"))
        assertEquals(setOf("v", "app", "days"), sent.last().keys().asSequence().toSet())
    }

    @Test fun theAppKeepsItOffUntilThePolicyFlagAndTheYes() {
        val c = context.appContainer
        c.setUsageStats(true)
        c.startupStats.record(Sample(Kind.COLD, 0, 100, 800))
        assertNull("3.2 not in force (stats.more off): nothing kept to send", c.startupStats.payload())
        StartupStats.policyAllows = { true }
        c.startupStats.record(Sample(Kind.COLD, 0, 100, 800))
        assertEquals(1_000L, c.startupStats.payload()!!.getLong("firstMonth"))
        // Turning 사용 통계 off forgets what waited.
        c.setUsageStats(false)
        assertNull(c.startupStats.payload())
        assertEquals(800L, c.startupStats.last!!.firstMonth)
    }

    @Test fun aSampleFromTheTraceIsKept() {
        StartupTrace.begin()
        StartupTrace.activityCreated(restoring = false)
        StartupTrace.containerReached(0)
        val sample = StartupTrace.monthDrawn()!!
        stats().record(sample)
        assertEquals(Kind.COLD, stats().last!!.kind)
    }
}
