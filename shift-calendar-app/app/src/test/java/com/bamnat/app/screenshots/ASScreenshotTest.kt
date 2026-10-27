package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyApi
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.LeadLine
import com.bamnat.app.ui.company.CompanyFindScreen
import com.bamnat.app.ui.company.CompanyFindViewModel
import com.bamnat.app.ui.company.CompanyInlineResults
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SRCH-01][SRCH-06][SRCH-07][SRCH-10] 1.29 묶음 AS (사용자 2026-10-27 "광장에 대표판이 있는데 회사 검색 시 뜨지 않는다 … 늦게 떠서 안 뜨는
 * 것처럼 보인다"): the companies the phone already knows show on the first letters while the server is asked, a slow answer
 * is waited for and shown when it comes (1.28 threw it away at 2.5 s), no connection says so and keeps the public rows, and
 * 회사 찾기 shows each company's 대표 주기 as the line under the name box does. Shots 1280~1289, made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ASScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun lead(i: Int, pay: Boolean = true) = LeadLine("s$i", if (i == 6) "온산공장" else "", true,
        listOf("주간", "주간", null, null, "야간", "야간", null, null), null, setOf("휴무"), pay = pay, ok = 9 - i, bad = 0)

    private val known = listOf("(주)예시정밀", "예시화학", "예시물류", "예시제철", "예시전자", "예시중공업", "예시정유").mapIndexed { i, n ->
        CompanyHit("c$i", n, null, 1, 0, users = 20 - i, lead = lead(i, pay = i % 2 == 0), sites = if (i == 0) 2 else 1)
    }

    /** The made-up server: its answer waits for [gate] (or fails with [fail]), and the most used companies are [known]. */
    private inner class Server(private val base: CompanyApi) : CompanyApi by base {
        val gate = CompletableDeferred<Unit>()
        var fail = false
        var popularList = known
        override suspend fun search(query: String): List<CompanyHit> {
            gate.await()
            if (fail) throw CompanyException("offline")
            return known.filter { com.bamnat.app.company.CompanyText.norm(it.name).contains(com.bamnat.app.company.CompanyText.norm(query)) }
        }
        override suspend fun popular(): List<CompanyHit> = popularList
    }

    private fun server(readAhead: Boolean = true): Server = Server(company).also { s ->
        companyOn()
        c.companyApi = s
        if (!readAhead) s.popularList = emptyList()
        runBlocking { c.companySearch.warm(saver = false) }
    }

    private fun Screen.inline(query: String) {
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                CompanyInlineResults(query, onServer = { }, onPick = { _, _ -> })
            }
        }
        settle()
    }

    @Test fun theCompaniesThePhoneKnowsShowWhileTheServerIsAsked() = shoot("1280_as_inline_known_first", PHONE) {
        server().also { c.companySearch.slowAfterMs = 60_000 } // the asking step itself, before "조금 오래 걸려요"
        inline("예시")
        // On the first frame, before any answer: the most used companies read after the first month, 대표 주기 and all.
        assertTrue(shows("(주)예시정밀", exact = true))
        assertTrue(shows("대표 주기 주주휴휴야야휴휴", exact = false))
        assertTrue(shows("20명 쓰고 있어요 · 맞아요 9명 · 급여 방식 있음 · 사업장 2곳", exact = true))
        assertTrue(shows("같은 회사 분들 자료를 더 찾고 있어요", exact = true))
    }

    @Test fun aSlowAnswerIsWaitedForAndSaidSo() = shoot("1281_as_inline_slow", PHONE) {
        server(readAhead = false)
        inline("예시정밀")
        compose.mainClock.advanceTimeBy(3_000)
        waitFor(100)
        assertTrue(shows("연결이 느려 조금 오래 걸려요. 찾으면 바로 여기에 보여 드려요.", exact = true))
        assertFalse(shows("못 불러왔어요", exact = false))
    }

    @Test fun theLateAnswerShowsWhenItComes() = shoot("1282_as_inline_late_answer", PHONE) {
        val s = server(readAhead = false)
        inline("예시정밀")
        compose.mainClock.advanceTimeBy(3_000)
        waitFor(100)
        // 1.28 threw an answer away after 2.5 s: the 대표판 never showed. Now it shows when it comes.
        s.gate.complete(Unit)
        reveal("(주)예시정밀", exact = true)
        assertTrue(shows("대표 주기 주주휴휴야야휴휴", exact = false))
        assertFalse(shows("조금 오래 걸려요", exact = false))
    }

    @Test fun noConnectionSaysSoAndKeepsWhatThePhoneHas() = shoot("1283_as_inline_offline", PHONE) {
        val s = server()
        s.fail = true
        s.gate.complete(Unit)
        inline("예시")
        waitFor(400)
        assertTrue(shows("인터넷 연결을 확인해 주세요", exact = false))
        assertTrue(shows("(주)예시정밀", exact = true))
    }

    @Test fun companyFindKeepsTheKnownCompaniesWhileAsking() = shoot("1284_as_find_known_first", PHONE) {
        server()
        val vm = CompanyFindViewModel(c).apply { query.value = "예시" }
        content { CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        settle()
        assertTrue(shows("같은 회사 분들 자료를 더 찾고 있어요", exact = true))
        assertTrue(shows("(주)예시정밀", exact = true))
    }

    @Test fun companyFindSaysWhereTheListWillBe() = shoot("1285_as_find_skeleton", PHONE) {
        server(readAhead = false)
        val vm = CompanyFindViewModel(c).apply { query.value = "예시정밀" }
        content { CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        waitFor(400)
        assertTrue(shows("찾고 있어요…", exact = true))
    }

    @Test fun companyFindShowsEveryCompanysLead() = shoot("1286_as_find_every_lead", PHONE) {
        val s = server(readAhead = false)
        s.gate.complete(Unit)
        val vm = CompanyFindViewModel(c).apply { query.value = "예시" }
        content { CompanyFindScreen(vm, setup = false, onBack = {}, onOpen = {}, onByHand = {}, focus = false) }
        waitFor(400)
        // [SRCH-01] 1.28's 회사 찾기 said only "쓰는 설정 1개"; the 대표 주기 was on the line under the name box alone (and the
        // server gave it to the first five only). The 7th company here has its 사업장's 대표 주기 too.
        assertTrue(shows("(주)예시정밀", exact = true))
        assertTrue(shows("20명 쓰고 있어요 · 맞아요 9명 · 급여 방식 있음 · 사업장 2곳", exact = true))
        reveal("대표 주기 주주휴휴야야휴휴 · 온산공장", exact = true)
    }
}
