package com.bamnat.core.plaza

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 2.0.0 [RVQ-59] 검토 I22c-03: 누구인지 모르게 한 사람 한 번만 세는 '쓰는 분'의 앱 쪽 계산(서버 company-worker src/fair-ticket.js와 같은 셈).
 *
 * 하루 한 장: 앱이 표 글자([words])를 만들고, 난수 r로 가린 수 H(words)·r^e mod n만 서버에 보내 서명받아요([blind]). 서버가 돌려준
 * 서명을 r⁻¹로 풀면([unblind]) 그 표의 서명이 되고, 서버는 서명할 때 표를 보지 못했으니 쓸 때 어느 휴대폰 것인지 몰라요. 앱은 작품을 넣을
 * 때 {t: 표 글자, s: 서명}을 함께 보내요. 깃발 market.fair_count(처리방침 3.15)가 켜졌을 때만, 그 전에는 받지도 보내지도 않아요.
 * 앱에서 부르는 곳(RecipeRepository의 countUse·sendKept)은 갈래 M이 이어요.
 */
object BlindTicket {
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    /** The server's public key as /api/recipes/ticket gives it (base64url n and e). */
    data class Key(val n: BigInteger, val e: BigInteger) {
        val bytes: Int get() = (n.bitLength() + 7) / 8

        companion object {
            fun of(n: String, e: String): Key? = runCatching { Key(BigInteger(1, unb64.decode(n)), BigInteger(1, unb64.decode(e))) }
                .getOrNull()?.takeIf { it.n.bitLength() >= 2048 && it.e.signum() > 0 }
        }
    }

    /** One blinded ticket on its way: [words] stays on the phone, [blinded] goes to the server, [r] unblinds its answer. */
    data class Pending(val words: String, val blinded: String, val r: BigInteger)

    /** A spent-ready ticket: what goes with 쓰는 분 as `ticket: {t, s}`. */
    data class Ticket(val t: String, val s: String)

    /** `bamnat-ticket|1|<KST 날>|<32바이트 난수>`: the day lets the server take a ticket only on its day and the next. */
    fun words(day: String, random: SecureRandom = SecureRandom()): String =
        "bamnat-ticket|1|$day|" + b64.encodeToString(ByteArray(32).also(random::nextBytes))

    /** SHA-256 of (words ‖ 4-byte counter) end to end, one byte shorter than n (the server's fairHash). */
    fun fullHash(words: String, nBytes: Int): BigInteger {
        val msg = words.toByteArray(Charsets.UTF_8)
        val out = ByteArray(nBytes - 1)
        var at = 0
        var i = 0
        while (at < out.size) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(msg)
            md.update(ByteBuffer.allocate(4).putInt(i).array())
            val h = md.digest()
            System.arraycopy(h, 0, out, at, minOf(32, out.size - at))
            at += 32
            i++
        }
        return BigInteger(1, out)
    }

    fun blind(key: Key, words: String, random: SecureRandom = SecureRandom()): Pending {
        var r: BigInteger
        do {
            r = BigInteger(key.n.bitLength() - 1, random)
        } while (r <= BigInteger.ONE || r.gcd(key.n) != BigInteger.ONE)
        val m = fullHash(words, key.bytes).multiply(r.modPow(key.e, key.n)).mod(key.n)
        return Pending(words, b64.encodeToString(fixed(m, key.bytes)), r)
    }

    /** The server's {sig} for [pending] made into the ticket's own signature, or null when it does not check (a wrong key or answer). */
    fun unblind(key: Key, pending: Pending, blindSig: String): Ticket? {
        val sb = runCatching { BigInteger(1, unb64.decode(blindSig)) }.getOrNull() ?: return null
        val s = sb.multiply(pending.r.modInverse(key.n)).mod(key.n)
        if (s.modPow(key.e, key.n) != fullHash(pending.words, key.bytes)) return null
        return Ticket(pending.words, b64.encodeToString(fixed(s, key.bytes)))
    }

    private fun fixed(v: BigInteger, len: Int): ByteArray {
        val raw = v.toByteArray().let { if (it.size > len && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return ByteArray(len - raw.size) + raw
    }
}
