package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * [SRCH-06][SRCH-07][SRCH-08][SRCH-09][SRCH-10] 1.29 묶음 AS (사용자 2026-10-27 "늦게 떠서 안 뜨는 것처럼 보인다"): one way for every
 * company search: what the phone has at once, the server's answer added however late it comes, one request per name,
 * kept between runs, the most used companies read ahead unless the phone saves data; reads a screen stops waiting for
 * return at once, and a connection is used again. Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompanySearchTest {
    private val names = (1..30).map { "가상화학$it" } + listOf("현대가상", "GS가상칼텍스", "(주)예시정밀")

    private fun hit(i: Int, name: String) = CompanyHit("c%011d".format(i), name, null, 1, 0, users = 40 - i,
        lead = LeadLine("%016x".format(i), "", true, listOf("주간", null), null, setOf("휴무"), pay = true, ok = 2, bad = 0))

    /** A server in memory: [waitMs] before each answer, [fail] for none, and how many times it was asked. */
    private inner class Fake(var waitMs: Long = 0, var fail: Boolean = false) : CompanyApi by FakeBase() {
        val asked = AtomicInteger()
        val popularAsked = AtomicInteger()
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> {
            asked.incrementAndGet()
            if (waitMs > 0) delay(waitMs)
            if (fail) throw CompanyException("offline")
            val q = CompanyText.norm(query)
            return names.withIndex().filter { CompanyText.norm(it.value).contains(q) }.take(20).map { (i, n) -> hit(i, n) }
        }
        override suspend fun popular(): List<CompanyHit> {
            popularAsked.incrementAndGet()
            return names.withIndex().map { (i, n) -> hit(i, n) }.sortedByDescending { it.users }
        }
    }

    @Test fun thePhoneShowsTheLastAnswerWhatFewerLettersFoundAndTheMostUsedAtOnce() = runBlocking {
        val api = Fake()
        val s = CompanySearch({ api }, null)
        assertTrue(s.peek("가상").hits.isEmpty())
        s.search("가상화학2")
        // The same name any way it is written: the answer itself.
        assertTrue(s.peek("가상 화학2").exact)
        assertEquals(listOf("가상화학2", "가상화학20", "가상화학21"), s.peek("가상화학2").hits.take(3).map { it.name })
        // More letters: what the answer for fewer had that still fits, before any request.
        val more = s.peek("가상화학21")
        assertFalse(more.exact)
        assertEquals(listOf("가상화학21"), more.hits.map { it.name })
        // The most used companies read ahead: a first user's first letters already find them, 대표 주기 and all.
        assertTrue(s.peek("현대").hits.isEmpty())
        s.warm(saver = false)
        assertEquals(1, api.popularAsked.get())
        val hyundai = s.peek("현대").hits.single()
        assertEquals("현대가상", hyundai.name)
        assertTrue(hyundai.lead!!.pay)
        // 초성 and the other spelling too (GS ↔ 지에스).
        assertEquals("(주)예시정밀", s.peek("ㅇㅅㅈㅁ").hits.single().name)
        assertEquals("GS가상칼텍스", s.peek("지에스가상").hits.single().name)
        // Read again only twice a day.
        s.warm(saver = false)
        assertEquals(1, api.popularAsked.get())
    }

    @Test fun aLateAnswerIsShownNotThrownAway() = runBlocking {
        val api = Fake(waitMs = 400)
        val s = CompanySearch({ api }, null)
        val steps = s.states("가상화학3", pauseMs = 0, slowMs = 100).toList()
        assertEquals(listOf(CompanySearch.Stage.ASKING, CompanySearch.Stage.SLOW, CompanySearch.Stage.DONE), steps.map { it.stage })
        assertEquals("가상화학3", steps.last().hits.first().name)
        // A fresh answer is not asked for again.
        assertEquals(listOf(CompanySearch.Stage.DONE), s.states("가상화학3", pauseMs = 0).toList().map { it.stage })
        assertEquals(1, api.asked.get())
    }

    @Test fun noAnswerKeepsWhatThePhoneHadAndSaysWhy() = runBlocking {
        val api = Fake()
        val s = CompanySearch({ api }, null)
        s.warm(saver = false)
        api.fail = true
        val steps = s.states("현대", pauseMs = 0).toList()
        assertEquals(CompanySearch.Stage.FAILED, steps.last().stage)
        assertEquals("offline", steps.last().code)
        assertEquals(listOf("현대가상"), steps.last().hits.map { it.name })
    }

    @Test fun twoScreensAskingTheSameNameMakeOneRequest() = runBlocking {
        val api = Fake(waitMs = 200)
        val s = CompanySearch({ api }, null)
        val a = async { s.search("가상화학1") }
        val b = async { s.search("가상 화학1") }
        assertEquals(a.await(), b.await())
        assertEquals(1, api.asked.get())
    }

    @Test fun whatWasFoundIsKeptForTheNextRun() = runBlocking {
        val dir = Files.createTempDirectory("company-search").toFile()
        val file = File(dir, "company_search.json")
        val api = Fake()
        val first = CompanySearch({ api }, file)
        first.load()
        first.search("가상화학7")
        first.warm(saver = false)
        // The file is written on its own thread.
        withTimeoutOrNull(5_000) { while (!file.exists() || !file.readText().let { "가상화학7" in it && "현대가상" in it }) delay(20) }
        assertFalse("no amounts, no device: names and 대표 주기 only", file.readText().contains("device"))
        val next = CompanySearch({ api }, file)
        assertTrue(next.peek("가상화학7").hits.isEmpty()) // nothing read on the screen's thread
        next.load()
        assertTrue(next.peek("가상화학7").exact)
        assertEquals("현대가상", next.peek("현대").hits.single().name)
        dir.deleteRecursively()
        Unit
    }

    @Test fun aPhoneSavingDataReadsNothingAhead() = runBlocking {
        val api = Fake()
        val s = CompanySearch({ api }, null)
        s.warm(saver = true, mine = "c00000000001")
        assertEquals(0, api.popularAsked.get())
        assertNull(s.page("c00000000001"))
    }

    @Test fun aReadTheScreenStopsWaitingForReturnsAtOnceAndConnectionsAreUsedAgain() = runBlocking {
        TinyCompanyServer(names, latencyMs = 1_500).use { server ->
            val client = CompanyClient(server.base)
            val started = System.nanoTime()
            assertNull(withTimeoutOrNull(200) { client.search("가상") })
            val waited = (System.nanoTime() - started) / 1_000_000
            assertTrue("waited $waited ms (1.28: until the answer, 1,500 ms)", waited < 1_000)
            server.latencyMs = 0
            delay(1_600) // the first read ends by itself
            val before = server.connections.get()
            repeat(5) { client.search("가상화학$it") }
            assertEquals("five searches on one connection", before, server.connections.get())
        }
    }

    /**
     * [SPD-11] 1.30 AP1: the server's whole list read once (SP1 `/api/companies/all`) finds a company as it is typed the way
     * the server's search would (name, other spelling, 초성) and asks nothing; kept for the next run with its ETag, asked again
     * after ten minutes and answered 304.
     */
    @Test fun theWholeListFindsAsTheServerDoesWithoutAskingAndIsKeptWithItsETag() = runBlocking {
        TinyCompanyServer(names).use { server ->
            val dir = Files.createTempDirectory("company-all").toFile()
            val file = File(dir, "company_search.json")
            val s = CompanySearch({ CompanyClient(server.base) }, file)
            s.warm(saver = false)
            assertEquals(1, server.seen.count { it.path == "/api/companies/all" })
            assertEquals("the most used list is not needed beside it", 0, server.seen.count { it.path.startsWith("/api/companies/top") })
            val asked = server.requests.get()
            assertEquals("가상화학2", s.peek("가상화학2").hits.first().name)
            assertTrue(s.peek("가상화학2").enough)
            assertEquals("GS가상칼텍스", s.peek("지에스가상").hits.single().name)
            assertEquals("(주)예시정밀", s.peek("ㅇㅅㅈㅁ").hits.single().name)
            assertEquals("현대가상", s.search("현대").single().name)
            assertEquals(listOf(CompanySearch.Stage.DONE), s.states("가상화학", pauseMs = 0).toList().map { it.stage })
            assertEquals("found as typed, nothing asked", asked, server.requests.get())

            withTimeoutOrNull(5_000) { while (!File(dir, "company_all.json").exists()) delay(20) }
            assertFalse(File(dir, "company_all.json").readText().contains("device"))
            val next = CompanySearch({ CompanyClient(server.base) }, file, now = { System.currentTimeMillis() + CompanySearch.ALL_FRESH_MS + 1 })
            next.load()
            assertEquals("가상화학2", next.peek("가상화학2").hits.first().name)
            assertFalse("ten minutes old: asked again", next.peek("가상화학2").enough)
            next.warm(saver = false)
            val again = server.seen.last { it.path == "/api/companies/all" }
            assertEquals(server.etag, again.headers["if-none-match"])
            assertTrue(next.peek("가상화학2").enough)
            dir.deleteRecursively()
        }
        Unit
    }

    @Test fun aServerBefore130HasNoListAndTheMostUsedAreReadAsBefore() = runBlocking {
        TinyCompanyServer(names, old = true).use { server ->
            val s = CompanySearch({ CompanyClient(server.base) }, null)
            s.warm(saver = false)
            assertEquals(1, server.seen.count { it.path.startsWith("/api/companies/top") })
            assertEquals("현대가상", s.peek("현대").hits.single().name)
            assertFalse(s.peek("현대").enough)
        }
    }

    /** Everything a search does not need, refused. */
    private class FakeBase : CompanyApi {
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage = throw CompanyException("offline")
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: org.json.JSONObject, ask: Boolean): ReportStatus =
            throw CompanyException("offline")
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult =
            throw CompanyException("offline")
    }
}
