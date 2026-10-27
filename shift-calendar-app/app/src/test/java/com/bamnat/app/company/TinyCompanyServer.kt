package com.bamnat.app.company

import com.bamnat.core.plaza.CompanyMatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * [SRCH-08][SRCH-14] 1.29 AS: a company server small enough to count what the app does to it: HTTP/1.1 with keep-alive, how
 * many connections were opened ([connections]) and requests made ([requests]), each answer after [latencyMs] (a far D1, a
 * slow phone network), the search over made-up [companies] the way the real one matches a name, and the most used list.
 *
 * [SPD-11][SPD-13][SPD-16][SPD-18] 1.30 AP1: and what SP1 added: the whole list with its ETag (304 for If-None-Match), a
 * company's page with a member's room and room talk (`?with=room,talk`), the page with a join (`"with": "page"`), and a D1
 * bookmark on every answer; [old] answers as a server before 1.30 (no list, no room or talk in the page, no bookmark). Every
 * request is kept with its headers ([seen]) to check what the app sent.
 */
class TinyCompanyServer(private val companies: List<String>, @Volatile var latencyMs: Long = 0, @Volatile var old: Boolean = false) : AutoCloseable {
    private val socket = ServerSocket(0)
    val connections = AtomicInteger()
    val requests = AtomicInteger()
    val base: String get() = "http://127.0.0.1:${socket.localPort}"
    @Volatile private var open = true

    /** One request as it came: method, path and query, headers (lower case), body. */
    data class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: String)
    val seen = CopyOnWriteArrayList<Seen>()
    /** The whole list's ETag; another value makes it a new list. */
    @Volatile var etag = "\"list1\""
    /** The bookmark the next answer carries. */
    private val bookmarks = AtomicInteger()

    init {
        thread(isDaemon = true, name = "tiny-company") {
            while (open) {
                val s = runCatching { socket.accept() }.getOrNull() ?: break
                connections.incrementAndGet()
                thread(isDaemon = true) { serve(s) }
            }
        }
    }

    private fun InputStream.line(): String? {
        val b = StringBuilder()
        while (true) {
            val c = read()
            if (c < 0) return if (b.isEmpty()) null else b.toString()
            if (c == '\n'.code) return b.toString().trimEnd('\r')
            b.append(c.toChar())
        }
    }

    private fun serve(s: Socket) = s.use {
        val input = s.getInputStream().buffered()
        val out = s.getOutputStream()
        while (open) {
            val line = runCatching { input.line() }.getOrNull() ?: return
            if (line.isBlank()) continue
            val headers = HashMap<String, String>()
            while (true) {
                val h = input.line() ?: return
                if (h.isEmpty()) break
                headers[h.substringBefore(':').trim().lowercase()] = h.substringAfter(':').trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = ByteArray(length).also { b -> var n = 0; while (n < length) { val r = input.read(b, n, length - n); if (r < 0) break; n += r } }
            requests.incrementAndGet()
            val (method, path) = line.split(" ").let { it[0] to it[1] }
            seen += Seen(method, path, headers, String(body, Charsets.UTF_8))
            if (latencyMs > 0) Thread.sleep(latencyMs)
            val (code, text, extra) = answer(method, path, headers, String(body, Charsets.UTF_8))
            val bytes = text.toByteArray(Charsets.UTF_8)
            val more = extra + if (old) emptyMap() else mapOf("x-d1-bookmark" to "b${bookmarks.incrementAndGet()}")
            out.write(("HTTP/1.1 $code ${if (code == 304) "Not Modified" else if (code < 400) "OK" else "Error"}\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" + (if (code == 304) "" else "Content-Length: ${bytes.size}\r\n") +
                more.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "Connection: keep-alive\r\n\r\n").toByteArray(Charsets.UTF_8))
            if (code != 304) out.write(bytes)
            out.flush()
        }
    }

    fun id(i: Int) = "c%011d".format(i)

    private fun hit(i: Int, name: String) = JSONObject().put("id", id(i)).put("name", name).put("adopted", 1).put("pending", 0)
        .put("users", 10 - i % 10).put("sites", 1)
        .put("lead", JSONObject().put("setup", "%016x".format(i)).put("dept", "").put("adopted", true)
            .put("steps", JSONArray(listOf("주간", "주간", JSONObject.NULL, JSONObject.NULL))).put("offs", JSONArray(listOf("휴무")))
            .put("pay", true).put("ok", 3).put("bad", 0))

    private val setup = JSONObject().put("v", 1).put("rotation", JSONObject().put("steps", JSONArray(listOf("주간", "야간", JSONObject.NULL))))

    /** A company's page as the server gives it; a phone with X-Device "member" joined it. */
    private fun page(i: Int, member: Boolean) = JSONObject().put("id", id(i)).put("name", companies[i]).put("kind", "company").put("aliases", JSONArray())
        .put("member", member)
        .put("setups", JSONArray(listOf(JSONObject().put("id", "%016x".format(i)).put("status", "adopted").put("points", 3).put("persons", 3)
            .put("bad", 0).put("threshold", 3).put("dept", "").put("version", 1).put("setup", setup)
            .put("lines", JSONArray(listOf(JSONArray(listOf("순서 3일", "주간 야간 휴무"))))).put("lead", true))))

    private fun room(i: Int) = JSONObject().put("company", JSONObject().put("id", id(i)).put("name", companies[i]))
        .put("base", JSONObject().put("id", "%016x".format(i)).put("status", "adopted").put("dept", "").put("version", 1).put("setup", setup)
            .put("persons", 3).put("senders", 1).put("uses", 4).put("support", 4).put("mine", true))
        .put("others", JSONArray()).put("stable", false)

    private fun talk() = JSONObject().put("posts", JSONArray(listOf(JSONObject().put("id", "p1").put("who", 1).put("body", "주간이 8시에 시작해요")
        .put("at", "2026-10-27T01:00:00Z").put("mine", false))))

    private fun answer(method: String, path: String, headers: Map<String, String>, body: String): Triple<Int, String, Map<String, String>> {
        val member = headers["x-device"] == "member"
        val at = path.substringBefore('?')
        val company = Regex("/api/companies/(c\\d{11})").find(at)?.groupValues?.get(1)?.let { c -> companies.indices.firstOrNull { id(it) == c } }
        if (at == "/api/companies/all") {
            if (old) return Triple(404, JSONObject().put("error", "not_found").toString(), emptyMap())
            if (headers["if-none-match"] == etag) return Triple(304, "", mapOf("ETag" to etag))
            val all = companies.withIndex().map { (i, n) ->
                hit(i, n).put("aliases", JSONArray()).put("said", CompanyMatch.spoken(n)).put("cho", com.bamnat.core.plaza.Choseong.of(CompanyText.norm(n)))
            }
            return Triple(200, JSONObject().put("companies", JSONArray(all)).put("n", all.size).toString(), mapOf("ETag" to etag))
        }
        if (method == "POST" && at == "/api/members") {
            val o = JSONObject(body)
            val i = companies.indices.first { id(it) == o.getString("company") }
            val r = JSONObject().put("joined", true).put("company", id(i))
            if (!old && o.optString("with") == "page") r.put("page", page(i, true))
            return Triple(200, r.toString(), emptyMap())
        }
        if (method != "GET") return Triple(200, JSONObject().put("ok", true).toString(), emptyMap())
        if (company != null && at.endsWith("/room")) return Triple(if (member) 200 else 403, if (member) room(company).toString() else "{\"error\":\"member\"}", emptyMap())
        if (company != null && at.endsWith("/posts")) return Triple(200, talk().toString(), emptyMap())
        if (company != null) {
            val p = page(company, member)
            if (!old && member && path.contains("with=room,talk")) p.put("room", room(company)).put("talk", talk())
            return Triple(200, p.toString(), emptyMap())
        }
        val list = when {
            path.startsWith("/api/companies/top") -> companies.withIndex().take(60)
            path.startsWith("/api/companies?q=") -> {
                val q = CompanyText.norm(URLDecoder.decode(path.substringAfter("q="), "UTF-8"))
                companies.withIndex().filter { CompanyText.norm(it.value).contains(q) }.take(20)
            }
            else -> emptyList()
        }
        return Triple(200, JSONObject().put("companies", JSONArray(list.map { (i, n) -> hit(i, n) })).toString(), emptyMap())
    }

    override fun close() {
        open = false
        runCatching { socket.close() }
    }
}
