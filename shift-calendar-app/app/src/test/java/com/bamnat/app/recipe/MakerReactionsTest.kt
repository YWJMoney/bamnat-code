package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxSection
import com.bamnat.core.guide.Place
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVM-36] 검토 HQ-52: what people did with my works comes to the 알림함, one row a work (P's shape "내 작품 ‘…’" · "질문 1개 ·
 * 좋아요 5개"), adding up while the row stays, starting again once it is gone; 안좋아요 and reports are never told; a row goes to the work
 * (its 이야기 when a 글 came), a work taken off 장터 to 내 작품.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MakerReactionsTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val zone = ZoneId.of("Asia/Seoul")
    private lateinit var before: RecipeApi
    private val answers = ArrayDeque<ProfileInbox>()
    private val asked = mutableListOf<Instant?>()

    @Before fun start() {
        Today.fix(ZonedDateTime.of(2026, 11, 12, 21, 0, 0, 0, zone))
        before = c.recipeApi
        c.recipeApi = object : RecipeApi by before {
            override val enabled = true
            override suspend fun inbox(device: String, since: Instant?): ProfileInbox? {
                asked += since
                return answers.removeFirstOrNull() ?: box()
            }
        }
    }

    @After fun end() {
        c.recipeApi = before
        Today.reset()
    }

    private fun at(minAgo: Long) = Instant.ofEpochMilli(Today.millis() - minAgo * 60_000)
    private fun box(
        likes: List<ProfileInbox.Reaction> = emptyList(), talk: List<ProfileInbox.Reaction> = emptyList(),
        uses: List<ProfileInbox.Reaction> = emptyList(), decided: List<ProfileInbox.Reaction> = emptyList(), followers: Int = 0,
        remixes: List<Triple<String, String, String>> = emptyList(),
    ) = ProfileInbox("m1", followers, emptyList(), emptyList(), remixes, emptyList(), emptyList(), at = Instant.ofEpochMilli(Today.millis()),
        likes = likes, talk = talk, uses = uses, decided = decided)
    private fun like(code: String, n: Int, title: String = "밤바다") = ProfileInbox.Reaction("like", code, title, n, at(5))
    private fun post(code: String, id: String, kind: String, body: String, title: String = "밤바다") =
        ProfileInbox.Reaction(kind, code, title, 1, at(3), post = id, body = body)
    private fun use(code: String, n: Int, total: Int, title: String = "밤바다") = ProfileInbox.Reaction("use", code, title, n, at(60), total = total)

    private val inbox get() = Inbox.get(c.appContext)
    private fun round(now: Long = Today.millis()) = runBlocking { c.recipes.reactions(now) }
    private fun later(minutes: Long) = Today.millis() + minutes * 60_000

    @Test fun aPhoneWithNoWorkAsksNothing() {
        answers += box(likes = listOf(like("BAMBADA1", 5)))
        assertEquals(0, round())
        assertTrue("작품을 올린 적 없는 휴대폰은 묻지 않아요", asked.isEmpty())
    }

    @Test fun oneWorkIsOneRowWithItsWordsTalkFirst() {
        c.recipes.hasProfile(true)
        answers += box(
            likes = listOf(like("BAMBADA1", 5), like("SPRING22", 2, "봄")),
            talk = listOf(post("BAMBADA1", "p2", "review", "정말 편해요"), post("BAMBADA1", "p1", "question", "야간에도 되나요?")),
            uses = listOf(use("BAMBADA1", 3, 214)),
        )
        assertEquals(2, round())
        val rows = inbox.rows.value
        val work = rows.first { it.key == MakerReactions.workKey("BAMBADA1") }
        assertEquals(InboxSection.PEOPLE, work.section)
        assertEquals("내 작품 ‘밤바다’", work.title)
        assertEquals("질문 1개 · 후기 1개 · 좋아요 5개 · 새로 넣은 분 3명", work.line)
        assertEquals(10, work.count)
        assertEquals(NoticeLinks.Target.RECIPE, work.target)
        assertEquals("글이 왔으면 이야기로 가요", "BAMBADA1#talk", work.payload)
        val spring = rows.first { it.key == MakerReactions.workKey("SPRING22") }
        assertEquals("좋아요 2개", spring.line)
        assertEquals("좋아요만 왔으면 작품 맨 위로", "SPRING22", spring.payload)
        assertTrue("안좋아요는 말하지 않아요", rows.none { "안좋아요" in it.line })
    }

    @Test fun theOneTalkIsQuotedAndTheNumbersAddUpWhileTheRowStays() {
        c.recipes.hasProfile(true)
        answers += box(talk = listOf(post("BAMBADA1", "p1", "question", "야간 근무 날에도 알람이 같이 바뀌나요? 궁금해서 여쭤봐요")))
        round()
        assertEquals("질문: 야간 근무 날에도 알람이 같이 바뀌나요?…", inbox.rows.value.single().line)
        // The same 글 again (a round that overlapped) is not counted twice; new 좋아요 add to the row still up.
        answers += box(likes = listOf(like("BAMBADA1", 4)), talk = listOf(post("BAMBADA1", "p1", "question", "야간 근무 날에도")))
        round(later(11))
        val row = inbox.rows.value.single()
        assertEquals("글 하나뿐일 때만 글을 옮겨요", "질문 1개 · 좋아요 4개", row.line)
        assertEquals(5, row.count)
        answers += box(likes = listOf(like("BAMBADA1", 1)))
        round(later(22))
        assertEquals("질문 1개 · 좋아요 5개", inbox.rows.value.single().line)
        // Opened (or closed): the next ones start a row of their own.
        inbox.remove(MakerReactions.workKey("BAMBADA1"))
        answers += box(likes = listOf(like("BAMBADA1", 2)))
        round(later(33))
        assertEquals("좋아요 2개", inbox.rows.value.single().line)
    }

    @Test fun usersAreCountedFromTheWholeNumberSoADayAskedTwiceSaysOnlyTheNewOnes() {
        c.recipes.hasProfile(true)
        answers += box(uses = listOf(use("BAMBADA1", 3, 214)))
        round()
        assertEquals("새로 넣은 분 3명", inbox.rows.value.single().line)
        // The server counts by the day: asked again the same day it says 5 since the morning, the whole number went 214 → 216.
        answers += box(uses = listOf(use("BAMBADA1", 5, 216)))
        round(later(11))
        assertEquals("새로 넣은 분 5명", inbox.rows.value.single().line)
        // Nothing new: nothing said.
        inbox.remove(MakerReactions.workKey("BAMBADA1"))
        answers += box(uses = listOf(use("BAMBADA1", 5, 216)))
        assertEquals(0, round(later(22)))
        assertTrue(inbox.rows.value.isEmpty())
    }

    @Test fun askedAtMostEveryTenMinutesFromWhereTheServerLeftOff() {
        c.recipes.hasProfile(true)
        round()
        round(later(5))
        assertEquals("10분 안에는 다시 묻지 않아요", 1, asked.size)
        round(later(11))
        assertEquals(listOf(null, Instant.ofEpochMilli(Today.millis())), asked)
    }

    @Test fun whatTheReviewDecidedIsANoticeAndAWorkTakenOffOpensMyWorks() {
        c.recipes.hasProfile(true)
        answers += box(decided = listOf(
            ProfileInbox.Reaction("decided", "BAMBADA1", "밤바다", at = at(9), state = "visible"),
            ProfileInbox.Reaction("decided", "SPRING22", "봄", at = at(8), state = "returned", note = "표지 사진의 글자를 지워 주세요"),
            ProfileInbox.Reaction("decided", "WINTER33", "겨울", at = at(7), state = "hidden", why = "flags"),
        ))
        round()
        val rows = inbox.rows.value.associateBy { it.key }
        val up = rows.getValue(MakerReactions.decidedKey("BAMBADA1"))
        assertEquals(InboxSection.NOTICE, up.section)
        assertEquals("장터에 올라갔어요. 이제 누구나 볼 수 있어요", up.line)
        assertEquals("BAMBADA1", up.payload)
        val back = rows.getValue(MakerReactions.decidedKey("SPRING22"))
        assertEquals("돌려 드렸어요: 표지 사진의 글자를 지워 주세요", back.line)
        assertNull(back.target)
        assertEquals(Place.RECIPE_MINE, back.feature?.place)
        val held = rows.getValue(MakerReactions.decidedKey("WINTER33"))
        assertFalse("신고는 말하지 않아요", "신고" in held.line)
    }

    @Test fun followersAndNewVersionsOfMyWorkComeToo() {
        c.recipes.hasProfile(true)
        answers += box(followers = 2, remixes = listOf(Triple("BAMBADA2", "밤바다 야간판", "밤바다")))
        round()
        answers += box(followers = 1)
        round(later(11))
        val rows = inbox.rows.value.associateBy { it.key }
        assertEquals("새 팔로워 3명", rows.getValue(MakerReactions.FOLLOWERS_KEY).line)
        assertEquals(Place.PROFILE, rows.getValue(MakerReactions.FOLLOWERS_KEY).feature?.place)
        val remix = rows.getValue(MakerReactions.remixKey("BAMBADA2"))
        assertEquals("내 작품 ‘밤바다’", remix.title)
        assertEquals("고친 판 ‘밤바다 야간판’이 올라왔어요", remix.line)
    }

    @Test fun turnedOffItIsNotAskedAndTheSwitchNamesEveryWork() {
        c.recipes.hasProfile(true)
        inbox.mute(MakerReactions.KIND, "내 작품 ‘밤바다’")
        inbox.mute(MakerReactions.KIND_DECIDED, "내 작품 ‘봄’")
        assertEquals(0, round())
        assertTrue(asked.isEmpty())
        assertEquals("내 작품에 온 반응", inbox.label(MakerReactions.KIND))
        assertEquals("내 작품 심사 결과", inbox.label(MakerReactions.KIND_DECIDED))
    }

    @Test fun aRowsPayloadOpensOnlyAWorksCode() {
        assertEquals("BAMBADA1" to true, MakerReactions.opened("BAMBADA1#talk"))
        assertEquals("BAMBADA1" to false, MakerReactions.opened("BAMBADA1"))
        assertNull(MakerReactions.opened("../settings#talk"))
        assertNull(MakerReactions.opened(null))
    }

    @Test fun whatTheRowCountedSurvivesTheAppStartingAgain() {
        val t = mapOf("BAMBADA1" to MakerReactions.Tally("밤바다", mapOf("question" to 1), 4, 2, "질문: 되나요", 99))
        assertEquals(t, MakerReactions.decode(MakerReactions.encode(t)))
        assertEquals(mapOf("A" to 3), MakerReactions.decodeTotals(MakerReactions.encodeTotals(mapOf("A" to 3))))
    }
}
