package com.bamnat.app.alarm

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalTime

/**
 * 2.0.0 [RVI-02][RVI-03] The alarm that rings when the ringing screen cannot start: it keeps sounding until answered,
 * puts the screen on, and has 끄기 and 다시 울림; 일어났나요? and the early smart-wake ring fall back to it too.
 */
@RunWith(AndroidJUnit4::class)
class FallbackAlarmTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val ring get() = RingRequest("주간", Today.date(), LocalTime.of(7, 0), 60, null, Today.millis())

    @Before fun allow() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun shown(): Notification? = shadowOf(manager).getNotification(Notifications.FALLBACK_ALARM_ID)

    @Test fun theFallbackKeepsRingingAndHasTwoButtons() {
        Notifications.postFallbackAlarm(context, ring)
        val n = shown()
        assertNotNull(n)
        n!!
        // [RVI-02] Before: the channel's sound once (a single 띵) and nothing to press.
        assertTrue(n.flags and Notification.FLAG_INSISTENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(n.fullScreenIntent)
        assertEquals(ring.title, n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("주간 근무 알람", ring.title)
        val labels = n.actions.map { it.title.toString() }
        assertEquals(2, labels.size)
        assertEquals("끄기", labels[0])
        assertTrue(labels[1], Regex("\\d+분 뒤 다시 울림").matches(labels[1]))
        // Both answer without unlocking the phone.
        assertTrue(n.actions.all { !it.isAuthenticationRequired })
        // 끄기 takes it away.
        val off = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals(ComponentName(context, AlarmActionReceiver::class.java), off.component)
        AlarmActionReceiver().onReceive(context, off)
        assertNull(shown())
    }

    @Test fun aRefusedStartRingsTheFallback() {
        // [RVI-03] Android refuses a foreground service from the background (ForegroundServiceStartNotAllowedException is an IllegalStateException).
        val refusing = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent?) = throw IllegalStateException("not allowed from the background")
        }
        assertFalse(AlarmService.startOrFallback(refusing, ring))
        assertNotNull(shown())
    }
}
