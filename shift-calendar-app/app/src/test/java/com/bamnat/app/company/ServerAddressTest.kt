package com.bamnat.app.company

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** [SRV-30] The new names first; the old workers.dev name only when a request never reached the new one. */
class ServerAddressTest {
    private val table = mapOf("https://api.bamnat.app" to "https://old.workers.dev")

    @After fun clear() = ServerAddress.forget()

    @Test fun goesToTheNewNameWhenItAnswers() {
        val tried = mutableListOf<String>()
        assertEquals("ok", ServerAddress.call("https://api.bamnat.app", 0, table) { tried += it; "ok" })
        assertEquals(listOf("https://api.bamnat.app"), tried)
    }

    @Test fun nameNotFoundTriesTheOldOneAndStaysThereAWhile() {
        val tried = mutableListOf<String>()
        val out = ServerAddress.call("https://api.bamnat.app", 0, table) {
            tried += it
            if (it.contains("bamnat.app")) throw UnknownHostException("api.bamnat.app") else "spare"
        }
        assertEquals("spare", out)
        assertEquals("https://old.workers.dev", ServerAddress.now("https://api.bamnat.app", 1_000, table))
        assertEquals("https://api.bamnat.app", ServerAddress.now("https://api.bamnat.app", ServerAddress.STICK_MS + 1, table))
        assertEquals(2, tried.size)
    }

    @Test fun aRequestThatMayHaveArrivedIsNeverSentTwice() {
        var n = 0
        try {
            ServerAddress.call("https://api.bamnat.app", 0, table) { n++; throw SocketTimeoutException("read timed out") }
            fail()
        } catch (e: IOException) {
            assertEquals(1, n)
        }
    }

    @Test fun cronetSaysWhyInItsMessage() {
        assertTrue(ServerAddress.neverArrived(IOException("Exception in CronetUrlRequest: net::ERR_NAME_NOT_RESOLVED")))
        assertFalse(ServerAddress.neverArrived(IOException("net::ERR_CONNECTION_RESET")))
    }

    @Test fun anAddressWithNoSpareIsLeftAlone() {
        assertEquals("http://10.0.2.2:8789", ServerAddress.now("http://10.0.2.2:8789", 0, table))
        var n = 0
        try {
            ServerAddress.call("http://10.0.2.2:8789", 0, table) { n++; throw UnknownHostException() }
            fail()
        } catch (e: IOException) {
            assertEquals(1, n)
        }
    }
}
