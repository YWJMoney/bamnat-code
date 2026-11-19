package com.bamnat.app.company

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * [SPD-26] 1.31 I2 (상세리뷰 I2, 사용자 2026-10-27 "조회하는 로딩이 너무 느리다"): how the app talks to its servers.
 *
 * 2.0.0 [RVN-23] (PC-187, 높음): Google Play services' Cronet (play-services-cronet 18.1.1, used since 1.31) is gone. It is
 * loaded from Play services as a dynamic module, and when Play services updates that module the app is killed even on screen
 * (PC, Android 17 fold emulator, 1.39.0: 'DynamiteLoaderV2Impl: Module config changed, forcing restart due to module
 * cronet_dynamite', then SIG 9, exit reason SIGNALED): a sleep record or a ringing alarm could be cut off. Now:
 *
 * - Android 14 (API 34) and later: the phone's own [android.net.http.HttpEngine] (the same Chromium network stack, HTTP/3 (QUIC)
 *   and HTTP/2, which Cloudflare speaks: one connection for every request, and a weak signal loses less time). Source:
 *   https://developer.android.com/reference/android/net/http/HttpEngine ("Added in API level 34, Also in S Extensions 7";
 *   [android.net.http.HttpEngine.Builder] enables HTTP/2 and QUIC by default, Brotli off; `openConnection(URL)` gives an
 *   HttpURLConnection, without HttpsURLConnection, the HTTP cache or a CookieHandler, none of which the app uses). It lives in
 *   the system's own module, not in Play services; a module of the system (APEX) is "only activated after reboot"
 *   (https://source.android.com/docs/core/ota/apex), so an update does not stop the app. That HttpEngine is in such a module is
 *   read from its 'S Extensions 7' note: 확인 필요 (its own page does not say which module).
 *   S Extensions 7 would also bring it to some Android 12·13 phones; not used, so those go as before (the PC check asked for 14+).
 * - Before that, and whenever the engine cannot be made: the same [HttpURLConnection] as before.
 * - [hello] opens the connection (and wakes the server) when a lookup screen opens, before the first letter is typed.
 *
 * Nothing is sent that was not sent before: the engine is only the way the same requests go.
 */
object NetEngine {
    @Volatile private var engine: Any? = null
    @Volatile private var started = false
    @Volatile private var helloAt = Long.MIN_VALUE / 2

    /** Whether requests go through the phone's HttpEngine now (자가 점검's 연결 방식 says so). */
    val fast: Boolean get() = engine != null

    /** Whether this phone has the engine at all (Android 14 and later). */
    internal fun available(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Makes the engine once, off the main thread (it reads its files); until it is there, requests go as before. */
    fun start(context: Context) {
        if (started || isRobolectric || !available()) return
        started = true
        val app = context.applicationContext
        Thread({ engine = build(app) }, "bamnat-net").start()
    }

    private fun build(app: Context): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            android.net.http.HttpEngine.Builder(app).setEnableQuic(true).setEnableHttp2(true).setEnableBrotli(true).build()
        }.getOrNull()
    }

    /** A connection to [url]: the engine's when it is there, the phone's own [HttpURLConnection] otherwise. */
    fun open(url: URL): HttpURLConnection = viaEngine(url) ?: (url.openConnection() as HttpURLConnection)

    private fun viaEngine(url: URL): HttpURLConnection? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val e = engine as? android.net.http.HttpEngine ?: return null
        return runCatching { e.openConnection(url) as HttpURLConnection }.getOrNull()
    }

    /**
     * The engine's own kind of failure ([android.net.http.NetworkException.getErrorCode]); null for any other failure, and on a
     * phone before Android 14.
     */
    internal fun errorCode(e: Throwable): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) (e as? android.net.http.NetworkException)?.errorCode else null

    /**
     * Whether [e] is a time running out (the three words' 시간 넘김), from the old connection ([SocketTimeoutException]) or the engine
     * (its timeout kinds, [android.net.http.NetworkException.ERROR_TIMED_OUT] 4 and ERROR_CONNECTION_TIMED_OUT 6).
     */
    fun timedOut(e: Throwable): Boolean = e is SocketTimeoutException || errorCode(e)?.let { it in TIMED_OUT } == true

    /**
     * Whether the engine says the request never reached the server (the name not found, refused, no route, no network at all), so
     * it may go to the spare address ([ServerAddress]). ERROR_HOSTNAME_NOT_RESOLVED 1, ERROR_INTERNET_DISCONNECTED 2,
     * ERROR_CONNECTION_REFUSED 7, ERROR_ADDRESS_UNREACHABLE 9 (the page above).
     */
    internal fun neverArrived(e: Throwable): Boolean = errorCode(e)?.let { it in NEVER_ARRIVED } == true

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

    /** android.net.http.NetworkException's ERROR_TIMED_OUT and ERROR_CONNECTION_TIMED_OUT. */
    internal val TIMED_OUT = setOf(4, 6)
    /** ERROR_HOSTNAME_NOT_RESOLVED, ERROR_INTERNET_DISCONNECTED, ERROR_CONNECTION_REFUSED, ERROR_ADDRESS_UNREACHABLE. */
    internal val NEVER_ARRIVED = setOf(1, 2, 7, 9)

    private val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"
}
