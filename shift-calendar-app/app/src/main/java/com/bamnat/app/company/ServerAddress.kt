package com.bamnat.app.company

import com.bamnat.app.BuildConfig
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * [SRV-30] 1.36 (사용자 2026-10-31 도메인 연결): the app's servers live at their own names now (api.bamnat.app,
 * s.bamnat.app); the old workers.dev names stay up as a spare. A request that never reached the new name (the name not found,
 * the connection refused, a certificate not ready) is tried once at the spare, and for [STICK_MS] after that the app goes
 * straight to the spare, so a bad network or a DNS hiccup costs one slow request, not every one.
 *
 * Only failures where nothing reached the server count: a request that may have arrived is never sent twice.
 */
object ServerAddress {
    /** Primary address to its spare, from the build (blank: none, e.g. a debug build pointed at the PC). */
    private val spares: Map<String, String> = buildMap {
        put(BuildConfig.COMPANY_BASE_URL, BuildConfig.COMPANY_FALLBACK_URL)
        put(BuildConfig.SHARE_BASE_URL, BuildConfig.SHARE_FALLBACK_URL)
    }.mapKeys { it.key.trimEnd('/') }.filter { it.key.isNotBlank() && it.value.isNotBlank() }

    private val usingSpareUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    internal fun spareOf(base: String, table: Map<String, String> = spares): String? = table[base.trimEnd('/')]

    /** Where to send now: the spare while it is the one that worked last, the address itself otherwise. */
    fun now(base: String, at: Long = System.currentTimeMillis(), table: Map<String, String> = spares): String { // real clock: how long ago a request failed
        val spare = spareOf(base, table) ?: return base
        return if ((usingSpareUntil[base.trimEnd('/')] ?: 0L) > at) spare else base
    }

    /** True when [e] says the request never reached the server, so sending it elsewhere is safe. */
    fun neverArrived(e: Throwable): Boolean = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SSLHandshakeException -> true
        else -> e is IOException && e.message.orEmpty().let { m ->
            // Cronet's own errors (NetworkException) name the cause in their message.
            NEVER_ARRIVED.any { m.contains(it) }
        }
    }

    /**
     * Runs [send] at [base] (or the spare in use); when it fails before reaching the server and there is another
     * address, runs it once there and remembers which one worked.
     */
    fun <T> call(base: String, at: Long = System.currentTimeMillis(), table: Map<String, String> = spares, send: (String) -> T): T { // real clock: how long ago a request failed
        val key = base.trimEnd('/')
        val spare = spareOf(key, table) ?: return send(base)
        val first = now(key, at, table)
        val other = if (first == key) spare else key
        return try {
            send(first)
        } catch (e: IOException) {
            if (!neverArrived(e)) throw e
            val out = send(other)
            if (other == spare) usingSpareUntil[key] = at + STICK_MS else usingSpareUntil.remove(key)
            out
        }
    }

    internal fun forget() = usingSpareUntil.clear()

    const val STICK_MS = 30 * 60_000L
    private val NEVER_ARRIVED = listOf("ERR_NAME_NOT_RESOLVED", "ERR_CONNECTION_REFUSED", "ERR_ADDRESS_UNREACHABLE",
        "ERR_CERT_", "ERR_SSL_", "ERR_NAME_RESOLUTION_FAILED")
}
