package com.bamnat.app.r131

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.RateLimits
import com.bamnat.app.diag.LookupStats
import com.bamnat.app.ui.calendar.FirstFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.YearMonth
import kotlinx.coroutines.runBlocking

/** 1.31 (1.30 상세리뷰 반영): the phone's half that has no screen of its own. */
@RunWith(AndroidJUnit4::class)
class R131AppTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** [STAT-08] Nothing is kept before 3.5 and the yes; after, the middle value per step, on a bucket edge, and only known steps. */
    @Test fun lookupTimesAreKeptOnlyWhileAllowedAndSentAsBucketedMiddles() {
        var allowed = false
        val stats = LookupStats(context) { allowed }
        stats.sent()
        stats.record("wait", "find", 650)
        assertTrue("before 3.5 nothing is kept", stats.pending().isEmpty())
        assertNull(stats.payload())
        allowed = true
        listOf(650L, 900L, 2_400L).forEach { stats.record("wait", "find", it) }
        stats.record("call", "search", 280)
        stats.record("wait", "secret", 100)
        stats.record("cold", "find", 100)
        val p = stats.payload()!!
        assertEquals(1000L, p.getJSONObject("wait").getLong("find"))
        assertEquals(300L, p.getJSONObject("call").getLong("search"))
        assertEquals(setOf("wait", "call"), p.keys().asSequence().toSet())
        stats.sent()
        assertNull(stats.payload())
    }

    /** [SPD-28] The kept month comes back as it was drawn; a broken or short file draws nothing. */
    @Test fun theFirstFrameComesBackAsKept() {
        val month = YearMonth.of(2026, 11)
        val days = (1..30).map { FirstFrame.Day(if (it % 3 == 0) null else 0xFF3366CCL, if (it % 3 == 0) "" else "주") }
        val frame = FirstFrame.Frame(month, DayOfWeek.SUNDAY, days)
        assertEquals(frame, FirstFrame.decode(FirstFrame.encode(frame)))
        assertNull(FirstFrame.decode("2026-11|SUNDAY|ff,주"))
        assertNull(FirstFrame.decode("not a frame"))
    }

    /**
     * [SPD-21] Against a 1.31 server: names and counts come in two parts and are joined; both unchanged is "nothing new" (null);
     * a new user changes only the counts, and the names are not sent again.
     */
    @Test fun theWholeListComesAsNamesAndCountsAndOnlyWhatChangedIsSentAgain() = runBlocking {
        com.bamnat.app.company.TinyCompanyServer(listOf("가상화학", "가상물류"), split = true).use { s ->
            val client = com.bamnat.app.company.CompanyClient(s.base)
            val first = client.allCompanies(null)!!
            assertEquals(listOf("가상화학", "가상물류"), first.companies.map { it.hit.name })
            assertEquals(10, first.companies.first().hit.users)
            assertNull("nothing changed", client.allCompanies(first.etag))
            s.usersAdded = 2
            val before = s.seen.size
            val next = client.allCompanies(first.etag)!!
            assertEquals(12, next.companies.first().hit.users)
            val asked = s.seen.drop(before)
            assertEquals(s.namesTag, asked.first { it.path.contains("part=names") }.headers["if-none-match"])
            assertTrue(next.etag!!.endsWith(s.countsTag))
        }
        Unit
    }

    /** [LIM-03] The words say why and when, from the server's retryAfter. */
    @Test fun aRefusalSaysWhyAndWhen() {
        val wifi = RateLimits.of(CompanyException("rate_limited", null, 1_500, "network"), "회사 들어가기")
        assertTrue(wifi, wifi.startsWith("지금은 회사 들어가기를 잠시 할 수 없어요."))
        assertTrue(wifi, wifi.contains("같은 와이파이") && wifi.contains("모바일 데이터") && wifi.contains("25분쯤 뒤"))
        assertEquals("1분쯤 뒤", RateLimits.wait(30))
        assertEquals("2시간쯤 뒤", RateLimits.wait(5_000))
        assertEquals("내일", RateLimits.wait(30_000))
        assertTrue(RateLimits.text().contains("잠시 뒤 다시 해 주세요."))
        // Any other refusal keeps its old words (and does not call itself).
        assertEquals("인터넷에 연결되지 않았어요. 연결한 뒤 다시 해 주세요", com.bamnat.app.ui.company.companyProblem(CompanyException("offline")))
        assertTrue(com.bamnat.app.ui.company.companyProblem(CompanyException("rate_limited", null, 120, "device")).contains("2분쯤 뒤"))
    }
}
