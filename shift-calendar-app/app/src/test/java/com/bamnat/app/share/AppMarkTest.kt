package com.bamnat.app.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.team.TeamReceiveSync
import com.bamnat.app.tier.LaunchConfig
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.PolicyVersion
import com.bamnat.core.team.TeamMember
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 2.0.0 [RVJ-83] SPREAD-38 (처리방침 3.14 14번): a leader's link this phone puts in by itself carries one mark from the 3.14 day on, taken
 * back when it is no longer put in so (or taken out); a word made here, never a name. The leader reads one number, only from 3.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppMarkTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val id = "Ab3dEfGh5JkLmNoPqRsTuv"

    private class Api : ShareApi {
        override val enabled = true
        val marks = linkedMapOf<String, String>()
        var calls = 0
        var counted: List<Pair<String, String>>? = null
        override suspend fun create(payload: JSONObject) = error("not used")
        override suspend fun update(state: ShareLinkState, payload: JSONObject) = error("not used")
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String): ReceivedShare = error("not used")
        override suspend fun appMark(id: String, mark: String) { calls++; marks[id] = mark }
        override suspend fun appUnmark(id: String, mark: String) { calls++; if (marks[id] == mark) marks.remove(id) }
        override suspend fun appCount(links: List<Pair<String, String>>): AppCount { counted = links; return AppCount(5, 3) }
    }

    private val api = Api()

    private fun share(watch: Boolean) = ReceivedShare(JSONObject("""{"v":2,"title":"간호2팀","team":true,"types":[],"days":[]}"""),
        Instant.now().plus(Duration.ofDays(30)), Instant.now(), "t1", watch = watch)

    private fun on314() {
        val today = LocalDate.now()
        c.remoteConfig.save(LaunchConfig(policy = listOf(PolicyVersion("3.14", today.minusDays(31), today.minusDays(1))),
            serverTime = Instant.now(), fetchedAt = Instant.now()))
        // The rows are worked out again on the app's scope.
        repeat(200) { if (c.flags.on(FlagCatalog.TEAM_APP_COUNT)) return; Thread.sleep(20) }
    }

    @After fun tearDown() {
        c.received.remove(id)
        File(app.noBackupFilesDir, "app_marks.json").delete()
    }

    @Test fun `nothing is sent before 3_14`() = runBlocking {
        c.shareApi = api
        c.received.put(id, share(watch = true), mine = true)
        assertFalse(c.flags.on(FlagCatalog.TEAM_APP_COUNT))
        AppMark.sync(c)
        assertEquals(0, api.calls)
        assertNull(TeamReceiveSync.appCount(c, members(4)))
        assertNull("no count asked before 3.14", api.counted)
    }

    @Test fun `a watched link put in by itself carries a word of its own, and loses it when it is no longer`() = runBlocking {
        c.shareApi = api
        on314()
        assertTrue(c.flags.on(FlagCatalog.TEAM_APP_COUNT))
        // A link nobody watches, or one only looked at, carries nothing.
        c.received.put(id, share(watch = false), mine = true)
        AppMark.sync(c)
        assertTrue(api.marks.isEmpty())
        c.received.put(id, share(watch = true))
        AppMark.sync(c)
        assertTrue("a link only looked at (넣지 않기) is not 앱으로 받음", api.marks.isEmpty())
        // The member's own link (바로 내 달력에 넣기 by default).
        c.received.put(id, share(watch = true), mine = true)
        AppMark.sync(c)
        val word = api.marks.getValue(id)
        assertTrue(word, word.length >= 16 && word.none { it.isWhitespace() })
        assertFalse("never a name", word.contains("간호"))
        // Asking again sends nothing more.
        val calls = api.calls
        AppMark.sync(c)
        assertEquals(calls, api.calls)
        // 물어보기: taken back with the same word.
        c.received.setFollow(id, RosterFollower.Mode.ASK.key)
        AppMark.sync(c)
        assertTrue(api.marks.isEmpty())
        // 바로 넣기 again, then the link taken out of the app.
        c.received.setFollow(id, RosterFollower.Mode.AUTO.key)
        AppMark.sync(c)
        assertEquals(1, api.marks.size)
        c.received.remove(id)
        AppMark.sync(c)
        assertTrue(api.marks.isEmpty())
        assertFalse("nothing left to take back", File(app.noBackupFilesDir, "app_marks.json").exists())
    }

    @Test fun `the leader asks with every member's link and shows the number`() = runBlocking {
        c.shareApi = api
        on314()
        assertNull("under 3 links there is nothing to ask", TeamReceiveSync.appCount(c, members(2)))
        assertNull(api.counted)
        assertEquals(5, TeamReceiveSync.appCount(c, members(4) + TeamMember("x", "t", "링크 없음")))
        assertEquals(listOf("L0" to "k0", "L1" to "k1", "L2" to "k2", "L3" to "k3"), api.counted)
        assertEquals("앱으로 받는 팀원 5명 · 바뀐 날이 그분들 달력과 알람에 저절로 들어가요", TeamReceiveSync.appLine(5))
    }

    private fun members(n: Int) = (0 until n).map { TeamMember("m$it", "t", "팀원$it", linkId = "L$it", linkToken = "k$it") }
}
