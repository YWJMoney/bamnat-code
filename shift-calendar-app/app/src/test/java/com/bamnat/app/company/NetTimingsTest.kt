package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.diag.SpeedCheck
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [SPD-17] 1.30 묶음 AP1: the company server's answers timed on this phone, by kind of path only (never what was asked), the
 * last 50 kept, and 자가 점검's 조회 속도 card saying them in a few lines. [SPD-02][SPD-19] With them, the client's session:
 * the bookmark goes back, a read right after a write asks past the server's copies, and 6 s to connect.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NetTimingsTest {
    @Before @After fun clear() = NetTimings.clear()

    @Test fun eachRequestIsKeptByItsKindOnly() {
        assertEquals(NetTimings.Kind.SEARCH, NetTimings.kindOf("GET", "/api/companies?q=%EA%B0%80%EC%83%81"))
        assertEquals(NetTimings.Kind.SEARCH, NetTimings.kindOf("GET", "/api/companies/all"))
        assertEquals(NetTimings.Kind.COMPANY, NetTimings.kindOf("GET", "/api/companies/c00000000001?with=room,talk"))
        assertEquals(NetTimings.Kind.COMPANY, NetTimings.kindOf("GET", "/api/companies/c00000000001/room"))
        assertEquals(NetTimings.Kind.TALK, NetTimings.kindOf("GET", "/api/companies/c00000000001/setups/room/posts"))
        assertEquals(NetTimings.Kind.PLAZA, NetTimings.kindOf("GET", "/api/board?tab=voting&from=0"))
        assertEquals(NetTimings.Kind.WRITE, NetTimings.kindOf("POST", "/api/posts"))
        assertEquals(NetTimings.Kind.OTHER, NetTimings.kindOf("GET", "/api/inquiries"))
        NetTimings.record("GET", "/api/companies?q=secret-name", 12, NetTimings.Result.OK)
        assertFalse(NetTimings.calls().toString().contains("secret"))
    }

    @Test fun theCardSaysTheMiddleTheSlowestAndHowOftenAScreenShowedAtOnce() {
        assertEquals(listOf("아직 잰 것이 없어요. 회사 찾기나 광장을 열면 재요."), SpeedCheck.lines(NetTimings.summary()))
        (1..60).forEach { NetTimings.record("GET", "/api/board", it * 10L, NetTimings.Result.OK) }
        NetTimings.record("GET", "/api/companies/all", 3_000, NetTimings.Result.NOT_MODIFIED)
        NetTimings.record("GET", "/api/terms", 6_100, NetTimings.Result.OFFLINE)
        repeat(3) { NetTimings.cached(true) }
        NetTimings.cached(false)
        NetTimings.end(NetTimings.Wait.COMPANY, System.nanoTime() - 40_000_000)
        val s = NetTimings.summary()
        assertEquals(NetTimings.KEEP, s.count)
        assertEquals(6_100, s.maxMs)
        assertEquals(2, s.slow)
        assertEquals(1, s.offline)
        assertEquals(75, s.atOncePercent)
        val lines = SpeedCheck.lines(s)
        assertTrue(lines.toString(), lines[0].startsWith("서버 응답 최근 50개: 보통 "))
        assertTrue(lines[0].endsWith("가장 느림 6.1초"))
        assertEquals("느린 응답(2.5초 넘음) 2번 · 연결 안 됨 1번", lines[1])
        assertEquals("기다리지 않고 바로 보인 화면: 75% (4번 중 3번)", lines[2])
        assertTrue(lines[3], lines[3].startsWith("회사 누름 → 대표판 보임: 보통 "))
    }

    @Test fun aRealRequestIsTimedAndTheSessionsBookmarkGoesBack() = runBlocking {
        TinyCompanyServer(listOf("가상화학", "가상물류")).use { s ->
            val client = CompanyClient(s.base, device = { "member" })
            client.company(s.id(0))
            client.company(s.id(1))
            assertEquals(2, NetTimings.calls(NetTimings.Kind.COMPANY).size)
            // The first read carries no bookmark; the next one carries what the first answer gave.
            assertEquals(null, s.seen[0].headers["x-d1-bookmark"])
            assertEquals("b1", s.seen[1].headers["x-d1-bookmark"])
            assertEquals(null, s.seen[1].headers["cache-control"])
            // Right after a write, a read asks past the server's short-lived copies.
            client.use("member", s.id(0), "0000000000000000", true, "main")
            client.board(adopted = false)
            assertEquals("no-cache", s.seen.last().headers["cache-control"])
            assertEquals(6_000, CONNECT_MS)
        }
    }
}
