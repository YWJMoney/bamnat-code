package com.bamnat.app.alarm

import android.os.Looper
import com.bamnat.app.BamnatApp
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

/**
 * CI 12차: the app builds its container on a thread of its own when a test starts, and that container plans the alarms
 * once (at the real clock, from the app's own data) a little later. On a slow runner that planning landed after a test's
 * own and cancelled or replaced its alarm. A test that plans alarms waits for it here first, instead of a fixed sleep.
 */
object AppSettled {
    fun alarms(app: BamnatApp, timeoutMillis: Long = 10_000) {
        val c = runBlocking { app.awaitContainer() }
        val until = System.nanoTime() + timeoutMillis * 1_000_000
        while (c.alarmScheduler.plannings == 0 && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        // A planning the first one set off (a change of the app's own data) is let through too.
        Thread.sleep(400)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
