package com.bamnat.app.company

import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.core.plaza.OpenTimes
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVN-22] 검토 I10bc-02 열기 직전에 미리 받기: the opens are kept on this phone only, one mark per half hour, and the next
 * reading ahead is set from them (and the shifts and alarms), never sooner than twenty-five minutes on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReadAheadJobTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 11, 18)

    @After fun clock() = Today.reset()

    @Test fun theNextReadingAheadComesFromThisPhonesOwnOpens() = runBlocking {
        Today.fix(day.atTime(17, 50).atZone(zone))
        val kept = (3L downTo 1L).fold(emptyMap<Long, Long>()) { k, d -> OpenTimes.record(k, day.minusDays(d).atTime(21, 10)) }
        app.getSharedPreferences("open_times", 0).edit().putString("opens", OpenTimes.encode(kept)).commit()
        val next = ReadAheadJob.nextFor(app.container, day.atTime(17, 50))
        assertNotNull(next)
        assertTrue("$next", !next!!.isBefore(day.atTime(18, 15)) && !next.isAfter(day.atTime(21, 0)))
    }

    @Test fun anOpenIsOneMarkForItsHalfHourOnThisPhone() = runBlocking {
        Today.fix(day.atTime(7, 40).atZone(zone))
        ReadAheadJob.sawOpen(app)
        val prefs = app.getSharedPreferences("open_times", 0)
        withTimeoutOrNull(5_000) { while (prefs.getString("opens", null) == null) delay(20) }
        assertEquals(mapOf(day.toEpochDay() to (1L shl 15)), OpenTimes.decode(prefs.getString("opens", null)))
        // Whether a reading ahead is set depends on the phone using a company; one that does not has none.
        val pending = app.getSystemService(JobScheduler::class.java).getPendingJob(4423)
        if (!ReadAheadJob.wanted(app.container)) assertNull(pending)
    }
}
