package com.bamnat.app.company

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/**
 * 1.36 [SEC-11] 보안 점검 L4: when the server asks (428 integrity) before a write others see, the phone asks Play for a verdict once
 * and sends it to /api/integrity; the write then goes again by itself. Play answers only for the app installed from Play on a phone
 * that meets device integrity, so ids a script makes cannot post, report or count as invited friends. The server keeps a verdict a
 * week, so this is about once a week at most, and never while the server's switch (INTEGRITY_MODE) is off.
 *
 * The nonce is the install id and the Korean day, as the server checks (integrity.js integrityNonce).
 */
object IntegrityProof {
    /** Asks Play for a token with this nonce (set by the app at start, with its context); null when it cannot. */
    @Volatile var engine: ((nonce: String) -> String?)? = null

    @Volatile private var failedAt = 0L
    private const val WAIT_AFTER_FAIL_MS = 60 * 60 * 1000L

    fun nonce(device: String, nowMs: Long = System.currentTimeMillis()): String {
        val day = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString()
        val d = MessageDigest.getInstance("SHA-256").digest("bamnat-integrity|$device|$day".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(d)
    }

    /** True once the server has this install's verdict; false (and quiet for an hour) when Play or the server said no. */
    fun prove(baseUrl: String, device: String, send: (JSONObject) -> Unit = { body ->
        serverExchange(baseUrl, "POST", "/api/integrity", body, device)
    }): Boolean {
        val now = System.currentTimeMillis()
        if (now - failedAt < WAIT_AFTER_FAIL_MS) return false
        val token = runCatching { engine?.invoke(nonce(device, now)) }.getOrNull()
        val ok = token != null && runCatching { send(JSONObject().put("device", device).put("token", token)) }.isSuccess
        if (!ok) failedAt = now
        return ok
    }

    /** For tests. */
    internal fun forget() { failedAt = 0L }
}
