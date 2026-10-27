package com.bamnat.app.company

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * [SRCH-08][SRCH-14] 1.29 AS: a company server small enough to count what the app does to it: HTTP/1.1 with keep-alive, how
 * many connections were opened ([connections]) and requests made ([requests]), each answer after [latencyMs] (a far D1, a
 * slow phone network), the search over made-up [companies] the way the real one matches a name, and the most used list.
 */
class TinyCompanyServer(private val companies: List<String>, @Volatile var latencyMs: Long = 0) : AutoCloseable {
    private val socket = ServerSocket(0)
    val connections = AtomicInteger()
    val requests = AtomicInteger()
    val base: String get() = "http://127.0.0.1:${socket.localPort}"
    @Volatile private var open = true

    init {
        thread(isDaemon = true, name = "tiny-company") {
            while (open) {
                val s = runCatching { socket.accept() }.getOrNull() ?: break
                connections.incrementAndGet()
                thread(isDaemon = true) { serve(s) }
            }
        }
    }

    private fun serve(s: Socket) = s.use {
        val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val out = s.getOutputStream()
        while (open) {
            val line = runCatching { input.readLine() }.getOrNull() ?: return
            if (line.isBlank()) continue
            while (true) {
                val h = input.readLine() ?: return
                if (h.isEmpty()) break
            }
            requests.incrementAndGet()
            val path = line.split(" ")[1]
            if (latencyMs > 0) Thread.sleep(latencyMs)
            val body = answer(path).toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
                "Connection: keep-alive\r\n\r\n").toByteArray(Charsets.US_ASCII))
            out.write(body)
            out.flush()
        }
    }

    private fun hit(i: Int, name: String) = JSONObject().put("id", "c%011d".format(i)).put("name", name).put("adopted", 1).put("pending", 0)
        .put("users", 10 - i % 10).put("sites", 1)
        .put("lead", JSONObject().put("setup", "%016x".format(i)).put("dept", "").put("adopted", true)
            .put("steps", JSONArray(listOf("주간", "주간", JSONObject.NULL, JSONObject.NULL))).put("offs", JSONArray(listOf("휴무")))
            .put("pay", true).put("ok", 3).put("bad", 0))

    private fun answer(path: String): String {
        val list = when {
            path.startsWith("/api/companies/top") -> companies.withIndex().take(60)
            path.startsWith("/api/companies?q=") -> {
                val q = CompanyText.norm(URLDecoder.decode(path.substringAfter("q="), "UTF-8"))
                companies.withIndex().filter { CompanyText.norm(it.value).contains(q) }.take(20)
            }
            else -> emptyList()
        }
        return JSONObject().put("companies", JSONArray(list.map { (i, n) -> hit(i, n) })).toString()
    }

    override fun close() {
        open = false
        runCatching { socket.close() }
    }
}
