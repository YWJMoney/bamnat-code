package com.bamnat.app.company

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.36 [SEC-11] The nonce matches the server's (integrity.js) and a failed verdict waits an hour. */
@RunWith(RobolectricTestRunner::class) // org.json
class IntegrityProofTest {
    @Test fun nonceIsTheInstallAndTheKoreanDay() {
        // 2026-11-30T16:00Z is 2026-12-01 in Korea: same nonce as the server's integrityNonce(raw, that time).
        val a = IntegrityProof.nonce("test-device-00000001", java.time.Instant.parse("2026-11-30T16:00:00Z").toEpochMilli())
        val b = IntegrityProof.nonce("test-device-00000001", java.time.Instant.parse("2026-12-01T14:59:00Z").toEpochMilli())
        assertEquals(a, b)
        assertEquals("y1GEuqNN23C7lUy8L_ZOI4F8r-YuR9WiOlJwGAQA2Io", a)
        assertEquals(43, a.length)
        assertTrue(a.none { it == '+' || it == '/' || it == '=' })
    }

    @Test fun sendsTheTokenAndWaitsAfterAFailure() {
        IntegrityProof.forget()
        val sent = mutableListOf<JSONObject>()
        IntegrityProof.engine = { n -> "token-for-$n" }
        assertTrue(IntegrityProof.prove("https://x", "test-device-00000001") { sent += it })
        assertEquals("test-device-00000001", sent.single().getString("device"))
        IntegrityProof.engine = { null }
        assertFalse(IntegrityProof.prove("https://x", "test-device-00000001") { sent += it })
        IntegrityProof.engine = { n -> "token-for-$n" }
        assertFalse("an hour of quiet after Play said no", IntegrityProof.prove("https://x", "test-device-00000001") { sent += it })
        assertEquals(1, sent.size)
        IntegrityProof.forget()
        IntegrityProof.engine = null
    }
}
