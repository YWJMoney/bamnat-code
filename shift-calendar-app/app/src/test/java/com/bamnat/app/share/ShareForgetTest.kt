package com.bamnat.app.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.team.TeamReceiveSync
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.team.AskRound
import com.bamnat.core.team.AskRules
import com.bamnat.core.team.SwapRound
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCall
import com.bamnat.core.team.TeamCover
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.39 [PRIV-06] 모두 지우기 cuts every link this phone made on the share server with the key it holds, and forgets the link here;
 * what the server could not be reached for stays to be tried again. The team, the staff and a signed contract stay on the phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShareForgetTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val api = FakeApi()
    private val month = YearMonth.of(2026, 12)

    /** Records each delete as "kind:id"; ids in [offline] fail as with no connection. */
    private class FakeApi : ShareApi {
        override val enabled = true
        val cut = mutableListOf<String>()
        val offline = mutableSetOf<String>()
        /** Ids whose answer cannot be read (not offline, not a refusal). */
        val broken = mutableSetOf<String>()
        private fun go(kind: String, id: String, token: String) {
            if (id in offline) throw IOException("offline")
            if (id in broken) throw IllegalStateException("an answer it could not read")
            require(token.isNotEmpty())
            cut += "$kind:$id"
        }
        override suspend fun create(payload: JSONObject) = error("not used")
        override suspend fun update(state: ShareLinkState, payload: JSONObject) = error("not used")
        override suspend fun delete(state: ShareLinkState) = go("share", state.id, state.deleteToken)
        override suspend fun read(id: String) = error("not used")
        override suspend fun askDelete(id: String, token: String) = go("ask", id, token)
        override suspend fun swapDelete(id: String, token: String) = go("swap", id, token)
        override suspend fun callDelete(id: String, token: String) = go("call", id, token)
        override suspend fun boardDelete(id: String, token: String) = go("board", id, token)
        override suspend fun tiredDelete(id: String, token: String) = go("tired", id, token)
        override suspend fun clockDelete(id: String, token: String) = go("clock", id, token)
        override suspend fun signDelete(id: String, token: String) = go("sign", id, token)
    }

    private fun link(id: String) = ShareLinkState(id, "https://share.example/s/$id", "tok-$id", Today.instant().plus(Duration.ofDays(20)), "민지")

    @Before fun setUp() {
        c.shareApi = api
        c.shareLinks.clear()
    }

    @After fun tearDown() = c.shareLinks.clear()

    private fun seed() = runBlocking {
        c.shareLinks.save(link("CalLinkAb3dEfGh5Jk"))
        val store = c.phaseThree
        val shifts = listOf(TeamShift(1, "D", "데이", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(15, 0)), TeamShift(4, "O", "오프", 0xFF9E9E9E, true))
        store.saveTeam(Team("t1", "3병동", "수간호사", shifts,
            ask = AskRound("A1", "atok", month, AskRules(), mapOf("m1" to "https://s.example/a/K1")),
            swap = SwapRound("S1", "stok", month, listOf("m1", "m2")),
            calls = listOf(TeamCall("C1", "ktok", TeamCover.Gap("m1", month.atDay(7), 1), listOf("m2")))))
        store.saveMembers(listOf(TeamMember("m1", "t1", "김가나"), TeamMember("m2", "t1", "이다라", linkId = "L2", linkToken = "tok2"),
            TeamMember("m3", "t1", "박마바", linkId = "L3", linkToken = "tok3")))
        app.getSharedPreferences("team_board_links", android.content.Context.MODE_PRIVATE).edit()
            .putString("t1", JSONObject().put("id", "B1").put("token", "btok").put("url", "https://s.example/b/B1").toString())
            .putString("tired:t1", JSONObject().put("id", "T1").put("token", "ttok").toString()).apply()
        store.saveWorkplace(Workplace("w1", "가나다카페", calls = listOf(TeamCall("C2", "k2", TeamCover.Gap("s1", month.atDay(3), 0), listOf("s2")))))
        store.saveStaff(StaffMember("s1", "최사아", clockId = "K1", clockToken = "ctok",
            contract = StaffContract.Sent("근로계약서", emptyList(), 1L, linkId = "G1", token = "gtok", url = "https://s.example/g/G1",
                signedName = "최사아", signedAt = 2L)), "w1")
    }

    @Test fun everyKindIsCutWithItsKeyAndForgottenHere() = runBlocking {
        seed()
        assertNotNull("what it would cut is said first", ShareForget.held(c))

        val result = ShareForget.all(c)

        assertEquals(listOf("share:CalLinkAb3dEfGh5Jk", "share:L2", "share:L3", "ask:A1", "swap:S1", "call:C1", "board:B1", "tired:T1",
            "call:C2", "clock:K1", "sign:G1"), api.cut)
        assertEquals(ShareForget.Result(11, 0), result)
        assertNull(c.shareLinks.link.value)
        val store = c.phaseThree
        val team = store.teamNow("t1")!!
        assertNull(team.ask); assertNull(team.swap); assertTrue(team.calls.isEmpty())
        assertTrue("the team and its people stay", store.members("t1").first().map { it.name } == listOf("김가나", "이다라", "박마바"))
        assertTrue(store.members("t1").first().all { it.linkId == null && it.linkToken == null })
        assertNull(TeamReceiveSync.board(app, "t1")); assertNull(TeamReceiveSync.tiredBox(app, "t1"))
        assertTrue(store.workplaceNow("w1")!!.calls.isEmpty())
        val s = store.allStaff().single()
        assertNull(s.clockId)
        assertEquals("the signed contract stays on the phone", "최사아", s.contract?.signedName)
        assertNull(s.contract?.linkId); assertNull(s.contract?.token)
        assertNull("nothing left to say", ShareForget.held(c))
        assertTrue(ShareForget.all(c).let { it.cut == 0 && it.left == 0 })
    }

    @Test fun whatCouldNotBeReachedStaysToTryAgain() = runBlocking {
        seed()
        api.offline += setOf("L3", "C2", "CalLinkAb3dEfGh5Jk")

        val first = ShareForget.all(c)
        assertEquals(3, first.left)
        assertNotNull(c.shareLinks.link.value)
        assertEquals("tok3", c.phaseThree.members("t1").first().first { it.id == "m3" }.linkToken)
        assertEquals(1, c.phaseThree.workplaceNow("w1")!!.calls.size)
        assertFalse(first.leftWords.isNullOrEmpty())

        api.offline.clear()
        val again = ShareForget.all(c)
        assertEquals(ShareForget.Result(3, 0), again)
        assertNull(c.shareLinks.link.value)
    }

    /** 1.39 발굴 1-1: what was left is cut by itself once online, only that; a link made since stays. */
    @Test fun whatWasLeftGoesByItselfAndOnlyThat() = runBlocking {
        seed()
        api.offline += setOf("L3", "C2", "CalLinkAb3dEfGh5Jk")
        assertEquals(3, ShareForget.all(c).left)
        assertEquals(setOf("share:L3", "call:C2", "share:CalLinkAb3dEfGh5Jk"), ShareForget.pending(c))
        val jobs = app.getSystemService(android.app.job.JobScheduler::class.java)
        assertNotNull("a job waits for the connection", jobs.allPendingJobs.firstOrNull { it.id == 4408 })

        // Made after 모두 지우기: not the user's ask to cut.
        c.phaseThree.saveMembers(listOf(TeamMember("m9", "t1", "새사람", linkId = "L9", linkToken = "tok9")))
        api.offline.clear()
        api.cut.clear()
        assertTrue(ShareForget.retry(c))
        assertEquals(setOf("share:L3", "call:C2", "share:CalLinkAb3dEfGh5Jk"), api.cut.toSet())
        assertTrue(ShareForget.pending(c).isEmpty())
        assertEquals("tok9", c.phaseThree.members("t1").first().first { it.id == "m9" }.linkToken)
        assertNull(c.shareLinks.link.value)
        assertTrue("nothing waits: the job finds nothing", ShareForget.retry(c))
    }

    @Test fun aPhoneThatMadeNoLinkHearsNothingAboutThem() = runBlocking {
        assertNull(ShareForget.held(c))
        assertEquals(ShareForget.Result(0, 0), ShareForget.all(c))
        assertTrue(api.cut.isEmpty())
    }

    /** 2.0.0 [RVL-58] (검토 DATA-22) One link's unexpected trouble leaves that link only, said as not gone yet. */
    @Test fun oneLinksOddTroubleLeavesOnlyThatLink() = runBlocking {
        seed()
        api.broken += "L3"
        val r = ShareForget.all(c)
        assertEquals(ShareForget.Result(10, 1), r)
        assertEquals(setOf("share:L3"), ShareForget.pending(c))
        assertEquals("공유 링크 1개는 아직 못 지웠어요. 인터넷이 연결되면 저절로 마저 지워요", r.leftWords)
    }

    /** 2.0.0 [RVL-58] (검토 DATA-22) A stop the cutting did not expect: not "0 links", and every held link waits to be cut by itself. */
    @Test fun aStopIsNotNothingToCutAndEveryHeldLinkWaits() = runBlocking {
        seed()
        val r = ShareForget.allOrLater(c) { throw IllegalStateException("stopped half way") }
        assertTrue(r.failed)
        assertEquals(0, r.cut)
        val held = setOf("share:CalLinkAb3dEfGh5Jk", "share:L2", "share:L3", "ask:A1", "swap:S1", "call:C1", "board:B1", "tired:T1",
            "call:C2", "clock:K1", "sign:G1")
        assertEquals(held, ShareForget.pending(c))
        assertEquals(held.size, r.left)
        val jobs = app.getSystemService(android.app.job.JobScheduler::class.java)
        assertNotNull("a job waits to cut them", jobs.allPendingJobs.firstOrNull { it.id == 4408 })
        // What 모두 지우기 says: the links first, never "서버에 지울 것이 없었어요".
        val words = com.bamnat.app.company.ForgetCounts(linksLeft = r.left, linksFailed = r.failed).words()
        assertEquals("공유 링크 11개는 아직 못 지웠어요. 인터넷이 연결되면 저절로 마저 지워요", words)
        assertEquals("보낸 규칙 1을 지웠어요. 공유 링크는 아직 못 지웠어요. 인터넷이 연결되면 저절로 마저 지워요",
            com.bamnat.app.company.ForgetCounts(reports = 1, linksFailed = true).words())

        // Online again: the job cuts them all.
        assertTrue(ShareForget.retry(c))
        assertEquals(held, api.cut.toSet())
        assertTrue(ShareForget.pending(c).isEmpty())
        assertNull(ShareForget.held(c))
    }
}
