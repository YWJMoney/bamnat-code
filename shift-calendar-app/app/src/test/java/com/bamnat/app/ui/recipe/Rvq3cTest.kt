package com.bamnat.app.ui.recipe

import com.bamnat.app.company.InquiryPhotos
import com.bamnat.app.recipe.FontAbout
import com.bamnat.app.recipe.RecipePost
import com.bamnat.app.recipe.RecipeRequest
import com.bamnat.core.time.Today
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 Q1 3c (앱): what the 장터·문의 screens say from what the 4c·4e servers send. 검토 U22-05·U22-06·U22b-01·U22b-05·U22b-06·
 * U22b-08·U22b-10·I22bb-05·I22bb-06.
 */
@RunWith(RobolectricTestRunner::class)
class Rvq3cTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Before fun clock() = Today.fix(ZonedDateTime.of(2026, 11, 20, 9, 0, 0, 0, seoul))
    @After fun back() = Today.reset()

    private fun req(state: String, since: Instant? = null, reopen: Instant? = null, checking: Boolean = false, confirmed: Boolean = false) =
        RecipeRequest("r1", null, "주말 알바 시급 표", 3, state, null, Instant.parse("2026-11-10T00:00:00Z"), mine = false, taking = false,
            liked = false, makingSince = since, reopenAt = reopen, checking = checking, confirmed = confirmed)

    // [RVQ-44] U22-06: '만드는 중'에 끝이 보여요.
    @Test fun makingSaysHowLongAndWhenItOpens() {
        val since = ZonedDateTime.of(2026, 11, 15, 22, 0, 0, 0, seoul).toInstant()
        val reopen = ZonedDateTime.of(2026, 11, 29, 22, 0, 0, 0, seoul).toInstant()
        assertEquals("만드는 중 D+5 · 11월 29일에 다시 열려요", requestState(req("making", since, reopen), LocalDate.of(2026, 11, 20)))
        // An older server: no since, the word as before.
        assertEquals(stateWord("making"), requestState(req("making")))
    }

    // [RVQ-44] U22b-08: 완성은 요청한 분의 확인을 기다리고, 맞다고 하면 그렇게 적어요.
    @Test fun doneWaitsForTheOneWhoAsked() {
        assertEquals("완성 확인 기다림", requestState(req("done", checking = true)))
        assertEquals("완성 · 요청한 분이 확인했어요", requestState(req("done", confirmed = true)))
        assertEquals(stateWord("done"), requestState(req("done")))
    }

    // [RVQ-37] U22b-06: 글자·숫자가 없는 찾기는 서버에 묻지 않고 '글자로 적어 주세요'.
    @Test fun aSearchNeedsAWord() {
        assertTrue(searchable("간호"))
        assertTrue(searchable("3교대"))
        assertTrue(searchable("🌙"))
        assertFalse(searchable("!!!"))
        assertFalse(searchable("·~-"))
    }

    // [RVQ-50] U22b-01: 이어 쓰기 사진은 문의 하나에 모두 4장, 한 번에 2장까지.
    @Test fun followUpPhotosKeepToFour() {
        assertEquals(2, InquiryPhotos.left(0))
        assertEquals(2, InquiryPhotos.left(2))
        assertEquals(1, InquiryPhotos.left(3))
        assertEquals(0, InquiryPhotos.left(4))
        assertEquals(0, InquiryPhotos.left(9))
    }

    // [RVQ-46] U22b-10·I22bb-05: 글꼴을 받는 분께 '만든 곳 · 써도 되는 범위'.
    @Test fun aFontSaysWhoMadeItAndWhatMayBeDone() {
        val o = JSONObject("""{"id":"u1","name":"새벽체","license":"ofl","credit":{"maker":"새벽 글꼴 공방","says":"ofl"}}""")
        assertEquals("만든 곳 새벽 글꼴 공방 · 공개 글꼴(OFL) · 누구나 쓸 수 있어요", FontAbout.of(o)!!.credit())
        // The font's own words say nothing: what its sender chose.
        assertEquals("올린 분이 무료 공개 글꼴로 밝혔어요", FontAbout.of(JSONObject("""{"name":"손글씨","license":"free"}"""))!!.credit())
        assertEquals("만든 곳 (c) 2024 Kim · 공개 글꼴(Apache) · 누구나 쓸 수 있어요",
            FontAbout.of(JSONObject("""{"name":"A","license":"ofl","credit":{"copyright":"(c) 2024 Kim","says":"apache"}}"""))!!.credit())
        // An older server: the name only, no line.
        assertNull(FontAbout("옛 글꼴").credit())
        assertNull(FontAbout.of(JSONObject("""{"id":"u1"}""")))
    }

    // [RVQ-42] U22b-05·I22bb-06: 판 번호는 사람이 말하는 판으로.
    @Test fun whichAppSeesIt() {
        assertEquals("1.39", appVersionName(53))
        assertEquals("2.0", appVersionName(54))
        assertNull(appVersionName(null))
        assertNull(appVersionName(99))
        assertEquals("밤낮 1.39부터 보여요 · 그보다 옛 앱의 장터에는 보이지 않아요", seenFromLine(53))
        assertNull(seenFromLine(null))
    }

    private fun post(id: String, minute: Int, parent: String? = null) = RecipePost(id, 1, null, id, Instant.parse("2026-11-20T00:00:00Z").plusSeconds(minute * 60L),
        "opinion", parent, false, false, null, 0, 0)

    // [RVQ-43] U22-05: 최신부터, 새 답이 달린 이야기는 위로.
    @Test fun talkIsNewestFirst() {
        val posts = listOf(post("a", 1), post("b", 2), post("c", 3), post("a1", 10, parent = "a"))
        assertEquals(listOf("a", "b", "c"), talkTops(posts).map { it.id })
        assertEquals(listOf("a", "c", "b"), talkTops(posts, newestFirst = true).map { it.id })
    }
}
