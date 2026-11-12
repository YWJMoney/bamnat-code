package com.bamnat.app.inbox

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.NoticeLinks
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVP-43] 검토 HQ-49·50: the 알림함's rows other features put ([Inbox.post]). People first, then what the app says, one 권유 at a
 * time; what the user said no to does not come again, and the rows stay on the phone for 30 days.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InboxTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val zone = ZoneId.of("Asia/Seoul")
    private lateinit var inbox: Inbox

    @Before fun open() {
        Today.fix(ZonedDateTime.of(2026, 11, 12, 21, 0, 0, 0, zone))
        inbox = Inbox.get(app)
    }

    @After fun close() = Today.reset()

    private fun row(key: String, section: InboxSection, kind: String = "market.reaction", at: Long = Today.millis(), line: String = "댓글 1개") =
        InboxRow(key, kind, section, "내 작품 ‘봄’", line, target = NoticeLinks.Target.MONEY, at = at)

    @Test fun theSameKeyReplacesItsRowAndComesFirst() {
        inbox.post(row("market.work:1", InboxSection.PEOPLE, at = Today.millis() - 60_000))
        inbox.post(row("market.work:2", InboxSection.PEOPLE, at = Today.millis() - 30_000))
        inbox.post(row("market.work:1", InboxSection.PEOPLE, line = "댓글 2개 · 좋아요 5개"))
        val rows = inbox.rows.value
        assertEquals(listOf("market.work:1", "market.work:2"), rows.map { it.key })
        assertEquals("댓글 2개 · 좋아요 5개", rows.first().line)
    }

    @Test fun peopleComeBeforeWhatTheAppSaysAndSuggestionsLast() {
        inbox.post(row("s", InboxSection.SUGGEST, kind = "invite.friend"))
        inbox.post(row("n", InboxSection.NOTICE, kind = "plaza.notice"))
        inbox.post(row("p", InboxSection.PEOPLE, at = Today.millis() - 3_600_000))
        assertEquals(listOf("p", "n", "s"), inbox.rows.value.map { it.key })
    }

    @Test fun oneSuggestionAtATime() {
        inbox.post(row("invite", InboxSection.SUGGEST, kind = "invite.friend"))
        inbox.post(row("rule", InboxSection.SUGGEST, kind = "suggest.companyRule"))
        assertEquals(listOf("rule"), inbox.rows.value.map { it.key })
    }

    @Test fun whatTheUserSaidNoToDoesNotComeAgain() {
        inbox.post(row("market.work:1", InboxSection.PEOPLE))
        inbox.mute("market.reaction", "내 작품에 온 반응")
        assertTrue(inbox.rows.value.isEmpty())
        assertFalse(Inbox.post(app, row("market.work:2", InboxSection.PEOPLE)))
        assertEquals("내 작품에 온 반응", inbox.label("market.reaction"))
        inbox.unmute("market.reaction")
        assertTrue(Inbox.post(app, row("market.work:2", InboxSection.PEOPLE)))
        // 권유 끄기: no suggestion at all, the others still come.
        inbox.setSuggestOff(true)
        assertFalse(inbox.post(row("invite", InboxSection.SUGGEST, kind = "invite.friend")))
        assertTrue(inbox.post(row("plaza", InboxSection.NOTICE, kind = "plaza.notice")))
    }

    @Test fun clearAllIsTakenBackOnce() {
        inbox.post(row("a", InboxSection.PEOPLE))
        inbox.post(row("b", InboxSection.NOTICE, kind = "plaza.notice"))
        val gone = inbox.clearAll()
        assertTrue(inbox.rows.value.isEmpty())
        inbox.putBack(gone)
        assertEquals(setOf("a", "b"), inbox.rows.value.map { it.key }.toSet())
    }

    @Test fun rowsStayThirtyDaysAndAreReadBackFromThePhone() {
        val day = 24L * 60 * 60 * 1000
        inbox.post(row("old", InboxSection.PEOPLE, at = Today.millis() - 31 * day))
        inbox.post(row("new", InboxSection.PEOPLE, at = Today.millis() - 2 * day))
        assertEquals(listOf("new"), inbox.rows.value.map { it.key })
        Inbox.reset()
        val again = Inbox.get(app)
        assertEquals(listOf("new"), again.rows.value.map { it.key })
        assertEquals(NoticeLinks.Target.MONEY, again.rows.value.single().target)
    }
}
