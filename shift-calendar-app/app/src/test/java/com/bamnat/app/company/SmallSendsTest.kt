package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * 2.0.0 [RVN-13] 검토 COMP-48: 쓰고 있어요·맞아요·회사 빼기 that could not reach the server wait on the phone and go once online,
 * the last word for a thing winning; a refusal is not kept; 모두 지우기 forgets what waited.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SmallSendsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val c get() = context.appContainer
    private val store get() = c.companyStore
    private lateinit var before: CompanyApi
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val api = Flaky(calls)

    @Before fun start() {
        before = c.companyApi
        c.companyApi = api
        store.keptCalls.forEach { store.dropCall(it.key) }
        calls.clear()
    }

    @After fun end() {
        store.keptCalls.forEach { store.dropCall(it.key) }
        c.companyApi = before
    }

    @Test fun whatCouldNotGoWaitsAndGoesOnceOnlineTheLastWordWinning() = runBlocking {
        val waitingBefore = OutboxJob.waiting(c)
        api.down = true
        assertFalse(SmallSends.send(c, store, SmallSends.use("c1", "s1", true, "")))
        assertFalse(SmallSends.send(c, store, SmallSends.vote("c1", "s1", ok = true, verified = true)))
        // 쓰기 then 그만: only 그만 is kept, and after the 맞아요 (the order the user said them).
        assertFalse(SmallSends.send(c, store, SmallSends.use("c1", "s1", false, "")))
        assertFalse(SmallSends.send(c, store, SmallSends.leave("c2")))
        assertEquals(listOf("vote|c1|s1|", "use|c1|s1|", "leave|c2||"), store.keptCalls.map { it.key })
        assertEquals(listOf(true, false, true), store.keptCalls.map { it.on })
        // 서버 연결 점검 counts them as waiting.
        assertEquals(waitingBefore + 3, OutboxJob.waiting(c))
        // Still down: nothing goes, nothing is lost.
        assertFalse(SmallSends.flush(c, store))
        assertEquals(3, store.keptCalls.size)
        api.down = false
        calls.clear()
        assertTrue(SmallSends.flush(c, store))
        assertEquals(listOf("vote:c1:s1:true:true", "use:c1:s1:false:", "leave:c2"), calls)
        assertEquals(emptyList<SmallSends.Call>(), store.keptCalls)
    }

    @Test fun aRefusalIsNotKeptAndAWordThatWentTakesTheWaitingOnesPlace() = runBlocking {
        api.down = true
        SmallSends.send(c, store, SmallSends.use("c1", "s1", true, "cal-a"))
        assertEquals(1, store.keptCalls.size)
        // Online again and said anew: it goes, and the older waiting word for it is gone.
        api.down = false
        assertTrue(SmallSends.send(c, store, SmallSends.use("c1", "s1", false, "cal-a")))
        assertEquals(emptyList<SmallSends.Call>(), store.keptCalls)
        // The version is gone on the server: refused, so not kept to be refused again.
        api.refuse = true
        assertFalse(SmallSends.send(c, store, SmallSends.vote("c1", "gone", ok = true)))
        assertEquals(emptyList<SmallSends.Call>(), store.keptCalls)
    }

    @Test fun aWaitingDisagreeKeepsItsWhereAndTheLineSaysItWillGo() = runBlocking {
        // 2.0.0 [RVN-16] 검토 U10b-03: 달라요(old) pressed with no connection waits with its where and goes with it.
        SmallSends.later(c, store, SmallSends.vote("c1", "s1", ok = false, why = "old"))
        // 맞아요 never carries a where.
        assertEquals("", SmallSends.vote("c1", "s2", ok = true, why = "old").why)
        assertEquals(listOf("old"), store.keptCalls.map { it.why })
        calls.clear()
        assertTrue(SmallSends.flush(c, store))
        assertEquals(listOf("vote:c1:s1:false:false:old"), calls)
        assertEquals("인터넷이 연결되면 ‘달라요’를 보낼게요", SmallSends.waitsLine(false))
        assertTrue(SmallSends.waits(CompanyException("offline")))
        assertTrue(SmallSends.waits(java.io.IOException("reset")))
        assertFalse(SmallSends.waits(CompanyException("not_found")))
        assertFalse(SmallSends.waits(IllegalStateException()))
    }

    @Test fun aVoteOnTheInviteCardWithNoConnectionWaitsAndSaysSoInOneLine() {
        // 2.0.0 [RVN-16] 검토 U10b-03: 맞아요 on the card under the calendar, offline: the card stops asking, the word waits, one line says it will go.
        store.setApplied(AppliedSetup("c1", "가상화학", "", "s1", 1, true, com.bamnat.core.time.Today.instant()))
        val notes = Collections.synchronizedList(mutableListOf<String>())
        val listen = c.appScope.launch { com.bamnat.app.ui.PutOnNotice.notes.collect { notes += it.text } }
        try {
            api.down = true
            val vm = com.bamnat.app.ui.company.CompanyNudgeViewModel(c)
            var said: String? = "not yet"
            vm.vote(true) { said = it }
            var n = 0
            while (said == "not yet" && n++ < 200) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10) }
            assertEquals(null, said)
            assertEquals(true, store.applied.value?.vote)
            assertEquals(listOf("vote|c1|s1|"), store.keptCalls.map { it.key })
            n = 0
            while (notes.isEmpty() && n++ < 100) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10) }
            assertEquals(listOf("인터넷이 연결되면 ‘맞아요’를 보낼게요"), notes.toList())
            // Online again: it goes, and nothing is left waiting.
            api.down = false
            calls.clear()
            assertTrue(runBlocking { SmallSends.flush(c, store) })
            assertEquals(listOf("vote:c1:s1:true:false"), calls)
        } finally {
            listen.cancel()
            store.setApplied(null)
        }
    }

    @Test fun aSavedListReadsBackAndABrokenOneIsNothing() {
        val calls = listOf(SmallSends.use("c1", "s1", true, "cal-a"), SmallSends.vote("c1", "s1", ok = false, why = "name"), SmallSends.leave("c2"))
        assertEquals(calls, SmallSends.read(SmallSends.write(calls)))
        assertEquals(emptyList<SmallSends.Call>(), SmallSends.read("{"))
        assertEquals(emptyList<SmallSends.Call>(), SmallSends.read(null))
        // A row of a kind this phone does not know (or with no company) is left out, the rest kept.
        assertEquals(listOf("leave|c2||"), SmallSends.read("""[{"kind":"jump","company":"c1"},{"kind":"use","company":""},{"kind":"leave","company":"c2"}]""").map { it.key })
    }

    @Test fun deletingEverythingForgetsWhatWaited() = runBlocking {
        api.down = true
        SmallSends.send(c, store, SmallSends.leave("c1"))
        assertEquals(1, store.keptCalls.size)
        store.forgetSent()
        assertEquals(emptyList<SmallSends.Call>(), store.keptCalls)
    }

    private class Flaky(private val calls: MutableList<String>) : CompanyApi {
        @Volatile var down = false
        @Volatile var refuse = false
        override val enabled = true
        override suspend fun search(query: String): List<CompanyHit> = emptyList()
        override suspend fun company(id: String): CompanyPage = throw CompanyException("offline")
        override suspend fun terms(): CompanyTerms = CompanyTerms(3, 0)
        override suspend fun report(device: String, company: String, dept: String, verified: Boolean, note: String, setup: JSONObject, ask: Boolean): ReportStatus =
            throw CompanyException("offline")
        override suspend fun status(id: String): ReportStatus? = null
        override suspend fun takeBack(device: String, id: String) = Unit
        override suspend fun forgetMine(device: String): Int = 0
        private fun gate() {
            if (down) throw CompanyException("offline")
            if (refuse) throw CompanyException("not_found")
        }
        override suspend fun vote(device: String, company: String, setup: String, ok: Boolean, verified: Boolean, why: String?): VoteResult {
            gate(); calls += "vote:$company:$setup:$ok:$verified" + (why?.let { ":$it" } ?: ""); return VoteResult(1, 1, 0, 3, false)
        }
        override suspend fun use(device: String, company: String, setup: String, on: Boolean, cal: String): Int {
            gate(); calls += "use:$company:$setup:$on:$cal"; return 1
        }
        override suspend fun leave(device: String, company: String) { gate(); calls += "leave:$company" }
    }
}
