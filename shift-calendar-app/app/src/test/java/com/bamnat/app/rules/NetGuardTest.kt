package com.bamnat.app.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * 2.0.0 [RVR-42] (총괄 HQ-116): the app's tests never reach a server outside this computer.
 *
 * The debug build's addresses are the live ones (gradle.properties, [com.bamnat.app.share.ShareServerTest] reads them), so the
 * guard in tools/net-guard (a Java agent in every test JVM, app/build.gradle.kts) must refuse them before a connection is
 * made. Runs in Robolectric like the tests that build the app's container: java.net is the same JVM's there.
 * If this fails, the agent is not loaded and every test that starts the app talks to api.bamnat.app again.
 */
@RunWith(AndroidJUnit4::class)
class NetGuardTest {
    @Test fun theGuardIsTheJvmsProxyChoice() {
        assertEquals("bamnat.netguard.NetGuard", ProxySelector.getDefault()?.javaClass?.name)
    }

    @Test fun theLiveServersAreRefusedBeforeAnyConnection() {
        val live = listOf(BuildConfig.COMPANY_BASE_URL, BuildConfig.SHARE_BASE_URL, BuildConfig.COMPANY_FALLBACK_URL,
            BuildConfig.SHARE_FALLBACK_URL, BuildConfig.REMOTE_BASE_URL, "https://bamnat.app/", "https://example.com/")
            .filter { it.isNotBlank() }
        assertTrue("the debug build has its servers", live.size >= 4)
        for (base in live) {
            val conn = URL(base.trimEnd('/') + "/api/hello").openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000
            try {
                conn.responseCode
                fail("$base was reached from a test")
            } catch (e: ConnectException) {
                assertTrue("$base: ${e.message}", e.message.orEmpty().startsWith("bamnat net guard"))
            }
        }
    }

    @Test fun aPlainSocketOutsideIsRefusedToo() {
        try {
            Socket().use { it.connect(InetSocketAddress("api.bamnat.app", 443), 2_000) }
            fail("a socket to api.bamnat.app opened from a test")
        } catch (e: ConnectException) {
            assertTrue(e.message, e.message.orEmpty().startsWith("bamnat net guard"))
        }
    }

    /** The server tests (tools/server_app_tests.py) run a server on this computer: that stays open. */
    @Test fun aServerOnThisComputerStillAnswers() {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val answer = thread {
                server.accept().use { s ->
                    s.getInputStream().bufferedReader().let { r -> while (r.readLine().orEmpty().isNotEmpty()) Unit }
                    s.getOutputStream().write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            val conn = URL("http://127.0.0.1:${server.localPort}/api/hello").openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000
            conn.readTimeout = 2_000
            assertEquals(204, conn.responseCode)
            answer.join(2_000)
        }
    }
}
