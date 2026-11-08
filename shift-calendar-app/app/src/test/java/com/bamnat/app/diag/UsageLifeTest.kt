package com.bamnat.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * 1.39 [STAT2-02] 진입·머묾·회복: the day's first open adds its LIFE_ counts only once `stats.life` is on (처리방침 3.12), each day once;
 * the last open day stays on the phone and goes when 사용 통계 is turned off.
 */
@RunWith(AndroidJUnit4::class)
// A plain Application: BamnatApp builds its container on its own thread, and the container's start() clears
// usage_stats when 사용 통계 is off, racing these checks in a long run (6차 전체 시험에서 둘이 비어 실패).
@Config(sdk = [35], application = android.app.Application::class)
class UsageLifeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val prefs get() = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
    private val today get() = Today.date(zone).toEpochDay()

    @Before fun clean() {
        prefs.edit().clear().commit()
        UsageStats.lifeAllowed = { false }
    }

    @After fun reset() {
        UsageStats.lifeAllowed = { false }
        prefs.edit().clear().commit()
    }

    private fun stats(install: Long?) = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, _ -> }, installDay = { install })

    private fun counts(): Map<String, Int> = UsageBook.decode(prefs.getString("book", null)).days[today].orEmpty()

    @Test fun beforeThePolicyFlagNothingOfItIsCounted() {
        stats(today).record(UsageEvent.OPEN)
        assertEquals(mapOf("open" to 1), counts())
        assertEquals(today, prefs.getLong("last_open_day", 0))
    }

    @Test fun theInstallDayCountsAsEntryOnceAndAWeekAwayAsRecovery() {
        UsageStats.lifeAllowed = { true }
        val s = stats(today)
        s.record(UsageEvent.OPEN)
        s.record(UsageEvent.OPEN)
        assertEquals(mapOf("open" to 2, "life_new" to 1), counts())

        prefs.edit().clear().putLong("last_open_day", today - 8).commit()
        stats(today - 40).record(UsageEvent.OPEN)
        assertEquals(mapOf("open" to 1, "life_back7" to 1), counts())
    }

    @Test fun turningUsageStatsOffForgetsTheLastOpenDay() {
        UsageStats.lifeAllowed = { true }
        val s = stats(null)
        s.record(UsageEvent.OPEN)
        assertFalse(counts().keys.any { it.startsWith("life_") })
        s.clear()
        assertNull(prefs.all["last_open_day"])
    }
}
