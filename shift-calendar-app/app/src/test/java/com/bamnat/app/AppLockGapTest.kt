package com.bamnat.app

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** 2.0.0 [RVL-09] (검토 DATA-11): 앱 잠금 never stays open in silence. */
@RunWith(AndroidJUnit4::class)
class AppLockGapTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))

    @After fun open() = AppLock.release()

    /** Away long while a file picker was in front: the lock waits only for the picker's result, then closes. */
    @Test fun awayLongWithAPickerLocksOnceTheResultIsIn() {
        keyguard.setIsDeviceSecure(true)
        AppLock.onStart(context, on = true, awaitingResult = false)
        AppLock.release()
        AppLock.onStop(changingConfigurations = false)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        AppLock.onStart(context, on = true, awaitingResult = true)
        assertFalse("결과를 받기 전에는 덮지 않아요", AppLock.locked.value)
        AppLock.onResume()
        assertTrue(AppLock.locked.value)
    }

    /** A short look at the picker does not lock. */
    @Test fun aShortPickerDoesNotLock() {
        keyguard.setIsDeviceSecure(true)
        AppLock.onStart(context, on = true, awaitingResult = false)
        AppLock.release()
        AppLock.onStop(changingConfigurations = false)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        AppLock.onStart(context, on = true, awaitingResult = true)
        AppLock.onResume()
        assertFalse(AppLock.locked.value)
    }

    /** The phone's own lock was taken off: the calendar says 앱 잠금 is resting. */
    @Test fun withoutThePhonesLockItSaysItRests() {
        keyguard.setIsDeviceSecure(false)
        assertTrue(AppLock.resting(context, on = true))
        assertFalse(AppLock.resting(context, on = false))
        keyguard.setIsDeviceSecure(true)
        assertFalse(AppLock.resting(context, on = true))
    }
}
