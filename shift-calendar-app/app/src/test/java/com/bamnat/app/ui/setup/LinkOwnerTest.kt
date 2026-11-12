package com.bamnat.app.ui.setup

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.share.ReceivedCalendarStore
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.RosterFollower
import com.bamnat.app.ui.onboarding.FirstStyleScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * [RVG-15] 검토 TYPE-04 (사용자 결정 2026-11-12 '두 단추로 묻기'): a new phone opened from a 근무표 link was offered '이 근무표로 시작'
 * first, whoever the link was for, so a 가족·친구's 교대 근무 became the receiver's own calendar and followed every change. The first
 * screen now asks whose it is. [RVG-17] 검토 UIX-59: '내 근무표예요' keeps the copy it was filled from (no second read that can fail
 * after the waiting link was let go), and the buttons wait, saying so, while the link is read.
 */
@RunWith(AndroidJUnit4::class)
class LinkOwnerTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val file = File.createTempFile("received", ".json").apply { delete(); deleteOnExit() }
    private fun store() = ReceivedCalendarStore(file, writer = { it.run() })

    private fun raw(audience: String? = null, team: Boolean = false) = JSONObject()
        .put("v", 1).put("title", "민지")
        .put("types", JSONArray().put(JSONObject().put("id", 1).put("name", "데이").put("short", "D").put("color", "#2196F3").put("time", "07:00-15:00")))
        .put("days", JSONArray().put(JSONObject().put("date", "2026-11-12").put("type", 1)))
        .apply { audience?.let { put("audience", it) }; if (team) put("team", true) }

    private fun share(raw: JSONObject) = ReceivedShare(raw, Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2026-11-12T00:00:00Z"), "e1")

    @Test fun aFamilyLinkLeansTheirsAndATeamLinkMine() {
        assertEquals(LinkStart.Owner.THEIRS, LinkStart.lean(raw("family")))
        assertEquals(LinkStart.Owner.THEIRS, LinkStart.lean(raw("friend")))
        // A 팀 리더 makes each member a link of their own days only: it is the receiver's.
        assertEquals(LinkStart.Owner.MINE, LinkStart.lean(raw(team = true)))
        // A coworker's link, or one from before 1.33, holds the sender's own days: kept beside unless the user says it is theirs.
        assertEquals(LinkStart.Owner.THEIRS, LinkStart.lean(raw("coworker")))
        assertEquals(LinkStart.Owner.THEIRS, LinkStart.lean(raw()))
        assertEquals("not read yet: the side that changes nothing leads", LinkStart.Owner.THEIRS, LinkStart.lean(null))
    }

    @Test fun theirsIsKeptBesideAndNeverPutIntoMyDays() {
        val received = store()
        received.open("abc12345")
        LinkStart.keepTheirs(received, "abc12345", share(raw("family")))
        assertNull("the waiting link is let go: the calendar does not offer it again after the setup", received.opening.value)
        val cal = received.get("abc12345")!!
        assertFalse(cal.mine)
        assertEquals("only seen beside mine; the sender's changes never go into my days", RosterFollower.Mode.OFF, RosterFollower.mode(cal))
    }

    @Test fun mineIsKeptFromTheCopyTheSetupWasFilledFrom() {
        val received = store()
        received.open("abc12345")
        val got = share(raw(team = true))
        LinkStart.keepMine(received, "abc12345", got)
        assertNull(received.opening.value)
        val cal = received.get("abc12345")!!
        assertTrue(cal.mine)
        // What later changes are compared with is the copy the days came from, kept at once (no second read to fail).
        assertEquals(got.schedule.toString(), cal.basis.toString())
        assertEquals(RosterFollower.Mode.AUTO, RosterFollower.mode(cal))
    }

    private fun window(content: @androidx.compose.runtime.Composable () -> Unit, test: () -> Unit) {
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a -> a.setContent { com.bamnat.app.ui.theme.BamnatTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    @Test fun theFirstScreenAsksWhoseItIs() {
        var mine = 0
        var theirs = 0
        window({
            FirstStyleScreen(onPick = {}, onSkip = {}, linkWaiting = true, linkTitle = "민지", linkLean = LinkStart.Owner.THEIRS,
                onLinkStart = { mine++ }, onLinkTheirs = { theirs++ })
        }) {
            compose.onNodeWithText("이 근무표로 시작").assertDoesNotExist()
            compose.onNodeWithText("가족·동료 것이에요").assertIsEnabled().performClick()
            assertEquals(1, theirs)
            compose.onNodeWithText("내 근무표예요").assertIsEnabled().performClick()
            assertEquals(1, mine)
        }
    }

    @Test fun theButtonsWaitWhileTheLinkIsRead() {
        window({ FirstStyleScreen(onPick = {}, onSkip = {}, linkWaiting = true, linkLoading = true, onLinkTheirs = {}) }) {
            compose.onNodeWithText("근무표를 불러오는 중…").assertExists()
            compose.onNodeWithText("내 근무표예요").assertIsNotEnabled()
            compose.onNodeWithText("가족·동료 것이에요").assertIsNotEnabled()
        }
    }

    @Test fun theirsKeptSaysWhereItWent() {
        var closed = false
        window({ FirstStyleScreen(onPick = {}, onSkip = {}, theirsKept = "민지", onTheirsClose = { closed = true }) }) {
            compose.onNodeWithText("‘민지’ 근무표는 받은 달력에 넣었어요").assertExists()
            compose.onNodeWithText("내 근무표예요").assertDoesNotExist()
            // 디자인 품질 조건 3: answered, the card is one sentence (the question under it already asks for the user's own work).
            compose.onNodeWithText("이제 내 일을 골라 주세요.").assertDoesNotExist()
            compose.onNodeWithContentDescription("자세한 설명 보기").performClick()
            compose.onNodeWithText("달력 이름을 눌러", substring = true).assertExists()
            compose.onNodeWithContentDescription("받은 달력 안내 닫기").performClick()
            assertTrue(closed)
        }
    }

    /** 디자인 품질 조건 9: the card has a visible close; closed, neither answer is taken (the calendar offers the link later). */
    @Test fun theLinkCardClosesWithoutAnAnswer() {
        var closed = 0
        var answered = 0
        window({
            FirstStyleScreen(onPick = { answered++ }, onSkip = {}, onLater = {}, linkWaiting = true, linkTitle = "민지",
                onLinkStart = { answered++ }, onLinkTheirs = { answered++ }, onLinkClose = { closed++ })
        }) {
            compose.onNodeWithContentDescription("받은 근무표 카드 닫기, 첫 설정 뒤에 다시 물어요").performClick()
            assertEquals(1, closed)
            assertEquals(0, answered)
            // 디자인 품질 조건 2: '파일로 시작' at the top is the only file start (no second one at the bottom).
            compose.onNodeWithText("백업이나 회사 파일로 시작할게요").assertDoesNotExist()
            compose.onNodeWithText("파일로 시작").assertExists()
        }
    }

    /** [RVG-17] 디자인 품질 조건 7: no signal, a slow server, a link gone, each in its own words. */
    @Test fun aLinkNotReadSaysWhy() {
        assertTrue(LinkStart.problem(java.io.IOException()).startsWith("인터넷에 연결되지 않았어요"))
        assertTrue(LinkStart.problem(java.net.SocketTimeoutException()).startsWith("근무표를 읽는 데 시간이 너무 걸렸어요"))
        assertTrue(LinkStart.problem(com.bamnat.app.share.ShareLinkException("not_found")).startsWith("이 링크는 끊겼거나"))
        assertTrue(LinkStart.problem(com.bamnat.app.share.ShareLinkException("server")).startsWith("서버가 잠시 바빠요"))
    }
}
