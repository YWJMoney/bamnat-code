package com.bamnat.app

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.alarm.SystemEventReceiver
import com.bamnat.core.diag.DiagEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVL-64] (검토 DATA-23·DATA-29 ③) What other apps can hand this one: a file only as another app's content:// address, and the
 * exported system-event receiver works only for the system's own broadcasts in its filter.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RvlOutsideTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    /**
     * 2.0.0 [RVL-14] (검토 DATA-06·DATA-29 ③) A backup opened while the app is already open comes to onNewIntent: it is taken there
     * as on a first open (before, the app only came forward and the file was never read). Both go through [IncomingFiles.readAny].
     */
    @Test fun aFileOpenedWhileTheAppIsOpenIsTaken() {
        try {
            val backup = Uri.parse("content://com.android.providers.downloads.documents/document/42")
            assertTrue(IncomingFiles.readAny(Intent(Intent.ACTION_VIEW, backup)))
            assertEquals(backup, IncomingFiles.take())
            assertFalse(IncomingFiles.readAny(Intent(Intent.ACTION_VIEW, Uri.parse("file:///data/data/com.bamnat.app/files/x.bamnat"))))
            assertNull(IncomingFiles.take())
            // A shared roster photo still goes to 한 달 근무 넣기, not the backup dialog.
            assertTrue(IncomingFiles.readAny(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,
                Uri.parse("content://media/external/images/1"))))
            assertNull(IncomingFiles.take())
            assertTrue(IncomingRoster.take() is IncomingRoster.Shared.Photo)
        } finally {
            IncomingFiles.take(); IncomingRoster.take()
        }
        val main = java.io.File("src/main/java/com/bamnat/app/MainActivity.kt").readText()
        val onNew = main.substringAfter("override fun onNewIntent(").substringBefore("\n    }\n")
        assertTrue("onNewIntent reads what another app hands in", "IncomingFiles.readAny(intent)" in onNew)
        assertTrue("onCreate too", main.substringBefore("override fun onNewIntent(").contains("IncomingFiles.readAny(intent)"))
    }

    @Test fun aFileComesOnlyAsAnotherAppsContentAddress() {
        val messenger = Uri.parse("content://com.kakao.talk.FileProvider/external/밤낮-백업.bamnat")
        assertEquals(messenger, IncomingFiles.uriOf(Intent(Intent.ACTION_VIEW, messenger)))
        assertEquals(messenger, IncomingFiles.uriOf(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, messenger)))
        // file:// (this app would read the path with its own rights) and this app's own provider (its private files) are not taken.
        assertNull(IncomingFiles.uriOf(Intent(Intent.ACTION_VIEW, Uri.parse("file:///data/data/com.bamnat.app/databases/bamnat.db"))))
        val own = Uri.parse("content://${BuildConfig.APPLICATION_ID}.share/cache/share/밤낮-백업.bamnat")
        assertNull(IncomingFiles.uriOf(Intent(Intent.ACTION_VIEW, own)))
        assertNull(IncomingFiles.uriOf(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, own)))
        assertFalse(IncomingFiles.read(Intent(Intent.ACTION_VIEW, own)))
        assertNull(IncomingFiles.uriOf(Intent(Intent.ACTION_VIEW, Uri.parse("content:///no-authority"))))
        // A roster photo goes the same way.
        assertNull(IncomingRoster.sharedOf(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, own)))
        assertTrue(IncomingRoster.sharedOf(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,
            Uri.parse("content://media/external/images/1"))) is IncomingRoster.Shared.Photo)
    }

    @Test fun theSystemEventReceiverWorksOnlyForTheSystemsBroadcasts() {
        val before = app.container.telemetry.log.read().count { it.name == DiagEvents.SYSTEM_EVENT }
        // Another app naming the receiver with an action of its own: nothing is set again (and nothing is noted).
        SystemEventReceiver().onReceive(app, Intent("com.example.WAKE_EVERYTHING"))
        SystemEventReceiver().onReceive(app, Intent())
        assertEquals(before, app.container.telemetry.log.read().count { it.name == DiagEvents.SYSTEM_EVENT })
        assertEquals(setOf("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED", "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED", "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"), SystemEventReceiver.ACCEPTED)
        // The manifest's filter names the same five.
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val block = manifest.substringAfter(".alarm.SystemEventReceiver").substringBefore("</receiver>")
        val listed = Regex("""action android:name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(SystemEventReceiver.ACCEPTED, listed)
    }
}
