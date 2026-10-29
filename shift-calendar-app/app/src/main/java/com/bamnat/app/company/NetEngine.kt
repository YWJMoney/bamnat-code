package com.bamnat.app.company

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * [SPD-26] 1.31 I2 (상세리뷰 I2, 사용자 2026-10-27 "조회하는 로딩이 너무 느리다"): how the app talks to its servers.
 *
 * - With Google Play services' Cronet (play-services-cronet, a few hundred KB, the network code itself comes with Play
 *   services), requests go over HTTP/3 (QUIC) or HTTP/2, which Cloudflare speaks: one connection for every request, and a
 *   weak signal (a factory, an underground car park) loses less time. Without it (no Play services, an old one, a test),
 *   the same [HttpURLConnection] as before.
 * - [hello] opens the connection (and wakes the server) when a lookup screen opens, before the first letter is typed.
 *
 * Nothing is sent that was not sent before: Cronet is only the way the same requests go. Its own reporting is not turned on.
 */
object NetEngine {
    @Volatile private var engine: org.chromium.net.CronetEngine? = null
    @Volatile private var started = false
    @Volatile private var helloAt = Long.MIN_VALUE / 2

    /** Whether requests go through Cronet now (자가 점검's 조회 속도 says so). */
    val fast: Boolean get() = engine != null

    /** Asks Play services for Cronet once, in the background; until it answers, requests go as before. */
    fun start(context: Context) {
        if (started || isRobolectric) return
        started = true
        val app = context.applicationContext
        runCatching {
            com.google.android.gms.net.CronetProviderInstaller.installProvider(app).addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                // Built off the main thread: it reads its files.
                Thread({
                    engine = runCatching {
                        org.chromium.net.CronetEngine.Builder(app).enableQuic(true).enableHttp2(true).enableBrotli(true).build()
                    }.getOrNull()
                }, "bamnat-net").start()
            }
        }
    }

    /** A connection to [url]: Cronet's when it is there, the phone's own otherwise. */
    fun open(url: URL): HttpURLConnection =
        engine?.let { e -> runCatching { e.openConnection(url) as HttpURLConnection }.getOrNull() } ?: (url.openConnection() as HttpURLConnection)

    /**
     * Opens the connection to [baseUrl] ahead of the first lookup (GET /api/hello, an empty answer), at most every
     * [HELLO_EVERY_MS]. Run off the main thread; a failure changes nothing.
     */
    fun hello(baseUrl: String, now: Long = System.currentTimeMillis()) { // real clock: a connection's age, not a calendar time
        if (baseUrl.isBlank() || now - helloAt < HELLO_EVERY_MS) return
        helloAt = now
        if (!com.bamnat.app.diag.TestLab.allows("GET")) return
        runCatching {
            val conn = open(URL(baseUrl.trimEnd('/') + "/api/hello"))
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = CONNECT_MS
            conn.responseCode
            drain(conn)
        }
    }

    /** Within this the connection is still open (Cloudflare keeps an idle one about a minute). */
    const val HELLO_EVERY_MS = 45_000L

    private val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"
}
