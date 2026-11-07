package com.bamnat.app.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 1.39 [SETUP-20] 첫 설정 누른 횟수: only a new install, only until its first setup is saved, one bucket edge, sent once when allowed. */
@RunWith(AndroidJUnit4::class)
class SetupTapStatsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun fresh() {
        context.getSharedPreferences("setup_taps", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun aNewInstallCountsUntilItsSetupIsSavedAndSendsOnlyWhenAllowed() {
        var allowed = false
        val stats = SetupTapStats(context) { allowed }
        stats.start(setupDone = false, setupLater = false)
        repeat(17) { stats.tap() }
        // The app was closed half way: the count goes on from where it was.
        val again = SetupTapStats(context) { allowed }
        again.start(setupDone = false, setupLater = false)
        repeat(4) { again.tap() }
        again.finish(WorkStyle.SHIFT)
        repeat(50) { again.tap() } // the calendar after it is not counted
        assertEquals("SHIFT" to 25, again.pending())
        assertNull("before 처리방침 3.12 or without a yes nothing goes", again.payload())
        allowed = true
        val p = again.payload()!!
        assertEquals("SHIFT", p.getString("style"))
        assertEquals(25, p.getInt("taps"))
        again.sent()
        assertNull(again.payload())
        // Never counted again on this phone.
        again.start(setupDone = false, setupLater = false)
        again.tap()
        again.finish(WorkStyle.DAY)
        assertNull(again.pending())
    }

    @Test fun aPhoneWithItsSetupAlreadyOrPutOffSendsNothing() {
        val old = SetupTapStats(context) { true }
        old.start(setupDone = true, setupLater = false)
        old.tap()
        old.finish(WorkStyle.SHIFT)
        assertNull(old.payload())

        context.getSharedPreferences("setup_taps", Context.MODE_PRIVATE).edit().clear().commit()
        val later = SetupTapStats(context) { true }
        later.start(setupDone = false, setupLater = false)
        repeat(8) { later.tap() }
        later.end() // 나중에
        later.finish(WorkStyle.HOURLY)
        assertNull(later.payload())
    }

    @Test fun turningUsageStatsOffForgetsWhatWaits() {
        val stats = SetupTapStats(context) { true }
        stats.start(setupDone = false, setupLater = false)
        repeat(3) { stats.tap() }
        stats.finish(WorkStyle.BUSINESS)
        assertEquals("BUSINESS" to 5, stats.pending())
        stats.clear()
        assertNull(stats.payload())
    }
}
