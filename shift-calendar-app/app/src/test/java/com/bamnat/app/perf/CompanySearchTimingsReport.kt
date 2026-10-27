package com.bamnat.app.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyClient
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanySearch
import com.bamnat.app.company.TinyCompanyServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [SRCH-14] 1.29 AS: a first user's company search timed end to end, the 1.28 way and the 1.29 way side by side, against a
 * company server on this machine ([TinyCompanyServer]) that answers after a set time, printed as `[AS]` lines. JVM and a
 * local socket, not a phone on a mobile network: the server's wait stands for the network and a far database, and only
 * the before/after on the same run means anything. The 1.28 way is its code copied here (its request,
 * connection, the 2.5 s limit around a blocking read, 회사 찾기's collectLatest); 1.29 is the app's own code.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanySearchTimingsReport {
    private val names = (1..40).map { "가상화학$it" } + listOf("현대가상제철", "예시정밀")

    private fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

    /** 1.28's serverRequest, as it was. */
    private fun oldRequest(base: String, path: String): JSONObject {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } catch (e: IOException) {
            throw CompanyException("offline")
        } finally {
            conn.disconnect()
        }
    }

    /** 1.28's search: the blocking read inside withContext, which a time limit around it cannot cut short. */
    private suspend fun oldSearch(base: String, q: String): List<String> = withContext(Dispatchers.IO) {
        val o = oldRequest(base, "/api/companies?q=" + URLEncoder.encode(q, "UTF-8"))
        val a = o.getJSONArray("companies")
        (0 until a.length()).map { a.getJSONObject(it).getString("name") }
    }

    @Test fun aTimeLimitAroundASlowSearch() = runBlocking {
        TinyCompanyServer(names, latencyMs = 3_000).use { s ->
            var t = System.nanoTime()
            val old = withTimeoutOrNull(2_500) { oldSearch(s.base, "가상") }
            val oldMs = ms(t)
            t = System.nanoTime()
            val now = withTimeoutOrNull(2_500) { CompanyClient(s.base).search("가상") }
            val nowMs = ms(t)
            println("[AS] 2.5초 제한 안의 3초 걸리는 검색: 1.28은 ${oldMs}ms 뒤에야 끝나고 답은 버림, 1.29는 ${nowMs}ms에 기다림을 멈춤(요청은 끝까지 가서 남음)")
            assertNull(old)
            assertNull(now)
            assertTrue(oldMs >= 2_900)
            assertTrue(nowMs < 2_900)
        }
    }

    /**
     * 치는 대로 찾기 for a first user typing "가상화학" while the server takes 3 s (a cold server, a slow network): when does a
     * company row with its 대표 주기 show? 1.28: the pause, then 2.5 s at most for an answer that comes at 3 s, so never.
     * 1.29: the most used companies read after the first month (on Wi-Fi) show at once; with none read ahead (데이터 절약),
     * the answer when it comes, with "조금 오래 걸려요" between.
     */
    @Test fun firstCompanyRowUnderTheNameBox() = runBlocking {
        TinyCompanyServer(names, latencyMs = 3_000).use { s ->
            // 1.28: produceState { delay(300); withTimeoutOrNull(2500) { inlineSearch } ?: Slow }.
            var t = System.nanoTime()
            val old = run {
                delay(300)
                withTimeoutOrNull(2_500) { oldSearch(s.base, "가상화학") }
            }
            val oldMs = ms(t)
            // 1.29, nothing read ahead.
            val cold = CompanySearch({ CompanyClient(s.base) }, null)
            t = System.nanoTime()
            var slowAt = -1L
            var coldAt = -1L
            cold.states("가상화학").collect { st ->
                if (st.stage == CompanySearch.Stage.SLOW) slowAt = ms(t)
                if (st.stage == CompanySearch.Stage.DONE && st.hits.isNotEmpty()) coldAt = ms(t)
            }
            // 1.29 with the most used companies read ahead after the first month.
            s.latencyMs = 0
            val warm = CompanySearch({ CompanyClient(s.base) }, null)
            warm.warm(saver = false)
            s.latencyMs = 3_000
            t = System.nanoTime()
            val firstWarm = warm.states("가상화학").first { it.hits.isNotEmpty() }
            val warmAt = ms(t)
            println("[AS] 처음 사용자 '가상화학' 첫 회사 줄(서버 3초): 1.28 ${if (old == null) "안 보임(${oldMs}ms에 버림)" else "${oldMs}ms"}, " +
                "1.29 미리 받은 목록 ${warmAt}ms(${firstWarm.hits.size}곳, 대표 주기 ${firstWarm.hits.count { it.lead != null }}곳), " +
                "1.29 미리 받기 없음 ${coldAt}ms('조금 오래 걸려요' ${slowAt}ms)")
            assertNull("1.28 threw the late answer away", old)
            assertTrue(warmAt < 100)
            assertTrue(coldAt in 3_000..6_000)
            assertTrue(slowAt in 2_500..3_200)
        }
    }

    /**
     * 회사 찾기: "가상" then "가상화" 0.4 s later while each answer takes 1.5 s. 1.28's collectLatest waited for the first blocking
     * request before even pausing for the second; 1.29 lets it go and asks at once.
     */
    @Test fun theNextLetterDoesNotWaitForTheLastRequest() = runBlocking {
        TinyCompanyServer(names, latencyMs = 1_500).use { s ->
            suspend fun timeTyping(search: suspend (String) -> List<String>): Long {
                val query = MutableStateFlow("")
                val done = CompletableDeferred<Long>()
                val t = System.nanoTime()
                val job = launch {
                    query.collectLatest { q ->
                        if (q.length < 2) return@collectLatest
                        delay(300)
                        val hits = search(q)
                        if (q == "가상화" && hits.isNotEmpty()) done.complete(ms(t))
                    }
                }
                query.value = "가상"
                delay(400)
                query.value = "가상화"
                val at = done.await()
                job.cancel()
                return at
            }
            val old = timeTyping { q -> oldSearch(s.base, q) }
            val app = CompanySearch({ CompanyClient(s.base) }, null)
            val now = timeTyping { q -> app.states(q, pauseMs = 0).first { it.stage == CompanySearch.Stage.DONE }.hits.map(CompanyHit::name) }
            println("[AS] 회사 찾기 '가상'→'가상화'(0.4초 뒤, 답마다 1.5초): 1.28 ${old}ms, 1.29 ${now}ms")
            assertTrue(old >= 3_000)
            assertTrue(now < old - 800)
        }
    }
}
