package com.bamnat.app.diag

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVL-88] (검토 PC-194) '멈춰서 안전 모드' comes after stops, not after the person closing the app again and again before
 * its month settled; a safe start asked for in 진단 stays asked for when the person then closes the app to try it.
 */
@RunWith(AndroidJUnit4::class)
class StartGuardTest {
    private val app get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun clean() = StartGuard.leave(app)

    @Test fun closingTheAppByHandAgainAndAgainIsNoStop() {
        StartGuard.leave(app)
        repeat(6) { StartGuard.begin(app, lastEnd = ApplicationExitInfo.REASON_USER_REQUESTED) }
        assertFalse("강제 종료", StartGuard.safe.value)
        repeat(6) { StartGuard.begin(app, lastEnd = StartGuard.SWIPED) }
        assertFalse("최근 앱에서 닫기", StartGuard.safe.value)
    }

    @Test fun stopsInARowStillOpenSafely() {
        StartGuard.leave(app)
        repeat(StartGuard.LIMIT + 1) { StartGuard.begin(app, lastEnd = ApplicationExitInfo.REASON_CRASH) }
        assertTrue(StartGuard.safe.value)
    }

    @Test fun withoutAndroidsRecordItCountsAsBefore() {
        StartGuard.leave(app)
        repeat(StartGuard.LIMIT + 1) { StartGuard.begin(app, lastEnd = null) }
        assertTrue(StartGuard.safe.value)
    }

    @Test fun aSafeStartAskedForInDiagnosticsSurvivesClosingTheApp() {
        StartGuard.leave(app)
        StartGuard.armNext(app)
        StartGuard.begin(app, lastEnd = ApplicationExitInfo.REASON_USER_REQUESTED)
        assertTrue(StartGuard.safe.value)
        assertTrue(StartGuard.byHand)
    }
}
