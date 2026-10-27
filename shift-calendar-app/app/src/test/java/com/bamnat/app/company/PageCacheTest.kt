package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * [SPD-13] 1.30 묶음 AP1 (사용자 2026-10-27 "여전히 조회하는 로딩이 너무 느리다"): what the plaza screens read is kept on the phone
 * between runs, in the server's own words, and comes back the same; a page read alone keeps its room and talk; old things
 * and too many go; 내 활동 is kept by a mark of the install, without the install id or the 시세 amounts it sent. Made-up companies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PageCacheTest {
    private val at = Instant.parse("2026-10-27T03:00:00Z")
    private var clock = at.toEpochMilli()

    private val file = """{"format":"bamnat-company","v":1,"rotation":{"steps":["주간","야간",null]},"pay":{"items":[{"name":"자격수당","cadence":"MONTHLY"}]}}"""

    private fun page(id: String = "c00000000001", member: Boolean = true) = CompanyPage(
        id, "(주)가상화학", listOf("가상화학"), listOf(
            SharedSetup("0123456789abcdef", true, 5, 3, 1, 3, "생산팀", 2, file, listOf("근무" to "주간 08:00~20:00", "순서 3일" to "주간 야간 휴무"),
                trust = 75, agree = 75, posts = 4, uses = 9, senders = 2, checked = 1, by = listOf("야간요정"), checkedLong = true, support = 12,
                payHidden = false, stable = true, lead = true, doubted = false, opinion = Opinion(4, 1, mapOf("order" to 1), "ok")),
            SharedSetup("fedcba9876543210", false, 2, 1, 0, 3, "포장팀", null, file, emptyList(), payHidden = true, doubted = true),
        ), kind = "company", job = "factory", region = "31", member = member, limited = false,
    )

    private val room = RoomPage("c00000000001", "(주)가상화학",
        base = RoomVersion("0123456789abcdef", true, "생산팀", 2, file, 3, 2, 9, 12),
        mine = true, first = false,
        others = listOf(RoomVersion("fedcba9876543210", false, "생산팀", null, file, 1, 1, 0, 1, changes = listOf("순서: 주간 야간 휴무 휴무"))),
        stable = true, stablePhones = 3, stableDays = 30, stableUses = 9)

    private val talk = Talk(listOf(
        TalkPost("p1", 1, "sender", true, "이렇게 돌아요", at.minusSeconds(3600), false, nick = "야간요정"),
        TalkPost("p2", 2, null, false, "맞아요", at, true, hidden = "가려졌어요", parent = "p1", agree = 2, disagree = 1, trust = 67, myVote = true),
        TalkPost("p3", 3, null, false, "주간이 줄었어요", at, false, kind = "proposal",
            change = listOf(ChangeLine("shift", "08:00", "09:00", "공지")), proposal = ProposalState("s9", false, 2, 3, file),
            author = "a".repeat(64), review = true),
    ), mutedUntil = at.plusSeconds(60), past = true, hiddenByYou = 2, leftPosts = 7)

    private val board = Board(listOf(
        BoardItem("c00000000001", "(주)가상화학", "0123456789abcdef", "생산팀", true, 5, 3, 1, 3, 75, 75, 4, 2, at, "주간 08:00~20:00", "순서 3일", true,
            kind = "company", job = "factory", region = "31", uses = 9, by = listOf("야간요정")),
        BoardItem("c00000000002", "가상물류", "fedcba9876543210", "", false, 1, 1, 0, 3, 33, 100, 0, null, at, "주간", "순서 7일", false),
    ), next = 20)

    private val activity = Activity("야간요정",
        listOf(ActivityReport("r1", "c00000000001", "(주)가상화학", "생산팀", "0123456789abcdef", "adopted", 2, 3, 0, 14, at)),
        listOf(ActivityProposal("p5", "c00000000002", "가상물류", "s1", "주간이 줄었어요", 2, 0, 67, at)),
        listOf(Rate("t1", "construction", "31", "형틀목수", "day", 2026, 250_000, "", "pending", 1, 0, 33, true, null, null, at)),
        listOf(ActivityReward(3, "paid", "rule", at)))

    private fun temp(): File = Files.createTempDirectory("page-cache").toFile()

    @Test fun everythingKeptComesBackTheSameInTheNextRun() = runBlocking {
        val dir = temp()
        val f = File(dir, "plaza_cache.json")
        val first = PageCache(f, now = { clock })
        first.load()
        first.keepBundle(CompanyBundle(page(), room, talk))
        first.keepBoard("false||||recent", board)
        first.keepTalk("c00000000002", "fedcba9876543210", talk)
        first.keepTerms(CompanyTerms(3, 3, 75, 6))
        first.keepPlazas(listOf(PlazaJob("factory", "제조·생산", 12, 5, 0)))
        first.keepActivity("device-0001-secret", activity)
        first.flush()
        assertTrue(f.exists())
        val text = f.readText()
        assertFalse("never the install id", text.contains("device-0001-secret"))
        assertFalse("never the 시세 amount this phone sent", text.contains("250000"))

        val next = PageCache(f, now = { clock })
        assertNull("nothing read on the screen's thread", next.bundle("c00000000001"))
        next.load()
        val b = next.bundle("c00000000001")!!
        assertEquals(page(), b.page)
        assertEquals(room, b.room)
        assertEquals(talk, b.talk)
        assertEquals(board, next.board("false||||recent")!!.value)
        assertEquals(talk, next.talk("c00000000002", "fedcba9876543210")!!.value)
        assertEquals(CompanyTerms(3, 3, 75, 6), next.terms()!!.value)
        assertEquals(listOf(PlazaJob("factory", "제조·생산", 12, 5, 0)), next.plazas()!!.value)
        assertEquals(activity.copy(rates = emptyList()), next.activity("device-0001-secret")!!.value)
        assertNull("another install's 내 활동 is not this one's", next.activity("device-0002"))
        dir.deleteRecursively()
        Unit
    }

    @Test fun aPageReadAloneKeepsItsRoomAndTalkAndARoomTalkIsTheBundles() {
        val c = PageCache(null, now = { clock })
        c.keepBundle(CompanyBundle(page(), room, talk))
        val newer = page().copy(name = "(주)가상화학 본사")
        c.keepPage(newer)
        val b = c.bundle("c00000000001")!!
        assertEquals("(주)가상화학 본사", b.page.name)
        assertEquals(room, b.room)
        assertEquals(talk, b.talk)
        // A page read as anyone (not a member) does not carry a member's room along.
        c.keepPage(page(member = false))
        assertNull(c.bundle("c00000000001")!!.room)
        // The room's talk is the company's: kept there, read from there.
        c.keepBundle(CompanyBundle(page(), room, talk))
        assertEquals(talk, c.talk("c00000000001", com.bamnat.core.plaza.StylePlaza.ROOM)!!.value)
        val shorter = talk.copy(posts = talk.posts.take(1))
        clock += 1000
        c.keepTalk("c00000000001", com.bamnat.core.plaza.StylePlaza.ROOM, shorter)
        assertEquals(shorter, c.bundle("c00000000001")!!.talk)
        assertEquals(shorter, c.talk("c00000000001", com.bamnat.core.plaza.StylePlaza.ROOM)!!.value)
    }

    @Test fun theOldestGoAndTwoWeeksOldIsNotShown() = runBlocking {
        val c = PageCache(null, now = { clock })
        (1..PageCache.MAX_BUNDLES + 2).forEach { c.keepBundle(CompanyBundle(page(id = "c%011d".format(it)))) }
        assertNull(c.bundle("c00000000001"))
        assertNull(c.bundle("c00000000002"))
        assertNotNull(c.bundle("c%011d".format(PageCache.MAX_BUNDLES + 2)))

        val dir = temp()
        val f = File(dir, "plaza_cache.json")
        val kept = PageCache(f, now = { clock })
        kept.load()
        kept.keepBundle(CompanyBundle(page()))
        kept.keepTerms(CompanyTerms(3, 3))
        kept.flush()
        assertTrue(kept.fresh(kept.terms(), PageCache.DAY_MS))
        clock += PageCache.KEEP_MS + 1
        assertFalse(kept.fresh(kept.terms(), PageCache.DAY_MS))
        val later = PageCache(f, now = { clock })
        later.load()
        assertNull(later.bundle("c00000000001"))
        assertNull(later.terms())
        dir.deleteRecursively()
        Unit
    }

    @Test fun aServersAnswerAndWhatIsKeptAreReadByTheSameWords() {
        // The server's own page (as SP1's ?with=room,talk sends it) reads as the cache writes it back.
        val server = org.json.JSONObject(page().toJson().toString()).put("room", room.toJson()).put("talk", talk.toJson())
        assertEquals(page(), companyPage(server))
        assertEquals(room, roomPage(server.getJSONObject("room")))
        assertEquals(talk, talkOf(server.getJSONObject("talk")))
        assertEquals(board, boardOf(board.toJson()))
    }
}
