package com.bamnat.app.diag

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.TinyCompanyServer
import com.bamnat.app.company.serverRequest
import com.bamnat.app.share.ShareLinkClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** [QA130-01] 1.30 AY: on a Play pre-launch (Firebase Test Lab) phone the app reads from our servers but writes nothing. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TestLabTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun robot(on: Boolean) {
        Settings.System.putString(context.contentResolver, TestLab.SETTING, if (on) "true" else "false")
        TestLab.attach(context)
        TestLab.forget()
    }

    @After fun person() = robot(false)

    @Test fun onlyWritesAreHeldBack() {
        assertTrue(TestLab.allows("POST", robot = false))
        assertTrue(TestLab.allows("GET", robot = true))
        assertTrue(TestLab.allows("HEAD", robot = true))
        for (write in listOf("POST", "PUT", "PATCH", "DELETE")) assertFalse(write, TestLab.allows(write, robot = true))
    }

    @Test fun theSettingTellsARobotsPhone() {
        robot(false)
        assertFalse(TestLab.isRobot())
        robot(true)
        assertTrue(TestLab.isRobot())
        Settings.System.putString(context.contentResolver, TestLab.SETTING, "false")
        TestLab.forget()
        assertFalse(TestLab.isRobot())
    }

    @Test fun aRobotsWriteNeverReachesTheServerButItsReadsDo() = TinyCompanyServer(listOf("한빛화학")).use { server ->
        robot(true)
        val sent = runCatching { serverRequest(server.base, "POST", "/api/events", JSONObject().put("day", "2026-10-27")) }
        assertEquals("offline", (sent.exceptionOrNull() as CompanyException).code)
        assertEquals(0, server.requests.get())
        // Search and the plaza still answer, so the robot sees the screens.
        serverRequest(server.base, "GET", "/api/companies?q=한빛")
        assertEquals(1, server.requests.get())

        robot(false)
        serverRequest(server.base, "POST", "/api/events", JSONObject().put("day", "2026-10-27"))
        assertEquals(2, server.requests.get())
    }

    @Test fun aRobotMakesNoShareLink() {
        robot(true)
        // No server at this address: a request that went out would fail otherwise, never with "test_lab".
        val made = runBlocking { runCatching { ShareLinkClient("http://127.0.0.1:9").create(JSONObject().put("title", "근무표")) } }
        assertEquals("test_lab", made.exceptionOrNull()?.message)
    }
}
