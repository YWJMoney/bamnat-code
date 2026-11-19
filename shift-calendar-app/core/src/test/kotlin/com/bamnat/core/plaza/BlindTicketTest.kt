package com.bamnat.core.plaza

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVQ-59] 검토 I22c-03: the app's half of the day ticket, against the server's numbers (company-worker test/rvq59-fair-ticket). */
class BlindTicketTest {
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    @Test
    fun `the full hash is the server's, byte for byte`() {
        // node: fairHash("bamnat-ticket|1|2027-01-12|" + "A" * 43, 256) in server/company-worker/src/fair-ticket.js
        val h = BlindTicket.fullHash("bamnat-ticket|1|2027-01-12|" + "A".repeat(43), 256).toString(16)
        assertEquals(510, h.length)
        assertTrue(h.take(40), h.startsWith("233af6324d3f15967cdec772252ef54d8e84a228"))
        assertTrue(h.takeLast(40), h.endsWith("6c19c04f4a5ab8f758dcca6adfc27f33e140678b"))
    }

    @Test
    fun `a blinded ticket signed by the server unblinds into a signature of the words the server never saw`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val pub = pair.public as RSAPublicKey
        val priv = pair.private as RSAPrivateCrtKey
        val key = requireNotNull(BlindTicket.Key.of(b64.encodeToString(pub.modulus.toByteArray()), b64.encodeToString(pub.publicExponent.toByteArray())))
        val words = BlindTicket.words("2027-01-12")
        assertTrue(Regex("""^bamnat-ticket\|1\|2027-01-12\|[A-Za-z0-9_-]{43}$""").matches(words))
        val pending = BlindTicket.blind(key, words)
        val blinded = BigInteger(1, Base64.getUrlDecoder().decode(pending.blinded))
        assertNotEquals("the server sees only the blinded number", BlindTicket.fullHash(words, key.bytes), blinded)
        // The server: blinded^d mod n (src/fair-ticket.js blindSign).
        val sig = blinded.modPow(priv.privateExponent, priv.modulus)
        val ticket = requireNotNull(BlindTicket.unblind(key, pending, b64.encodeToString(sig.toByteArray())))
        assertEquals(words, ticket.t)
        val s = BigInteger(1, Base64.getUrlDecoder().decode(ticket.s))
        assertEquals(BlindTicket.fullHash(words, key.bytes), s.modPow(pub.publicExponent, pub.modulus))
        assertEquals(256, Base64.getUrlDecoder().decode(ticket.s).size)
        // A wrong answer is not a ticket.
        assertNull(BlindTicket.unblind(key, pending, b64.encodeToString(sig.add(BigInteger.ONE).toByteArray())))
        // Two tickets of one day are two different words (nothing ties them).
        assertNotEquals(words, BlindTicket.words("2027-01-12"))
    }

    @Test
    fun `a key under 2048 bits is not taken`() {
        val small = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.genKeyPair().public as RSAPublicKey
        assertNull(BlindTicket.Key.of(b64.encodeToString(small.modulus.toByteArray()), b64.encodeToString(small.publicExponent.toByteArray())))
    }
}
