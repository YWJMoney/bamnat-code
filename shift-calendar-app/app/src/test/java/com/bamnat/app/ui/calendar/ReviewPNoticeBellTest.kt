package com.bamnat.app.ui.calendar

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.MainActivity
import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.consent.ConsentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVP-43][RVP-45] 검토 HQ-49·HQ-50·PC-81 (사용자 11-12 "달력 위에 뜨는 것은 꼭 봐야 하는 것들로만", "전체 지움 등을 넣어서 비울
 * 수 있게", "거부를 하면 여러 번 반복해서 알림으로 보여 주지 마"): only an urgent notice is the band over the month, the rest wait in the
 * bell with their number; an urgent one that comes after the calendar settled waits there too, so the month never moves; the 알림함 puts
 * what people did first and what is suggested last, and empties with 전체 지움; a kind turned off does not come again.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class ReviewPNoticeBellTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @androidx.compose.runtime.Composable
    private fun box() = Box(Modifier.fillMaxWidth().height(40.dp))

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit, test: () -> Unit) {
        app.container.consentStore.markSeen(ConsentItem.entries)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a -> a.setContent { BamnatTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    @Test fun onlyWhatHasToBeSeenStandsOverTheMonth() {
        val state = NoticeStackState()
        show({
            Column {
                NoticeStack(listOf(
                    Notice("tip", "오늘의 팁", line = "팁 한 줄") { box() },
                    Notice("alarm", "알람", line = "알람이 안 울릴 수 있어요", urgent = true) { box() },
                ), state = state)
                NoticeBell(state)
            }
        }) {
            compose.onNodeWithText("알람이 안 울릴 수 있어요").assertIsDisplayed()
            compose.onNodeWithText("팁 한 줄").assertDoesNotExist()
            // The bell's number is what its sheet holds, the band's notice too ('알림 2' when it opens).
            assertEquals(2, state.inBell)
            compose.onNodeWithContentDescription("알림 2개").assertExists()
        }
    }

    @Test fun anUrgentNoticeThatComesAfterTheCalendarSettledWaitsInTheBell() {
        var late by mutableStateOf(false)
        val heights = mutableListOf<Int>()
        val state = NoticeStackState()
        show({
            NoticeStack(listOf(Notice("lookUndo", "입힌 모양", line = "스튜디오에서 입힌 모양", urgent = true) { if (late) box() }),
                Modifier.onSizeChanged { heights += it.height }, state = state)
        }) {
            compose.mainClock.advanceTimeBy(SETTLE_MS + 100)
            compose.waitForIdle()
            assertTrue("열고 0.7초면 자리가 정해져요", state.settled)
            late = true
            compose.waitForIdle()
            assertTrue("PC-81: 늦게 온 띠는 달을 밀지 않아요 $heights", heights.all { it == 0 })
            assertEquals(1, state.inBell)
        }
    }

    /**
     * [RVM-17] PC-57 with PC-81: the look the studio just put on is the band on the first visit to the calendar only, and stays for that
     * visit; once the calendar is left it is a row of the 알림함 for the day.
     */
    @Test fun theLookJustPutOnIsTheBandOnTheFirstVisitOnly() {
        com.bamnat.app.data.LookUndo.keep(app, mapOf("theme" to "before"))
        var visit by mutableStateOf(true)
        var band: Boolean? = null
        show({ if (visit) band = rememberLookUndo()?.band }) {
            assertEquals("입힌 뒤 처음 보는 달력: 띠", true, band)
            visit = false
            compose.waitForIdle()
            assertTrue("떠나면 본 것으로", com.bamnat.app.data.LookUndo.seen(app))
            band = null
            visit = true
            compose.waitForIdle()
            assertEquals("다시 오면 알림함 줄", false, band)
        }
    }

    /**
     * 검토 PC-139 (통합 4차 PC, 높음): 알림을 끈 폰에서 '전체 지움'이 '알림이 꺼져 있어 …' 경고까지 지워, 앱을 다시 열거나 권한을 켰다 꺼도 다시
     * 안 나왔어요. What keeps an alarm from working stays in the bell until fixed (no X, no ⋮), 전체 지움 leaves it, and fixed then broken
     * again its band comes back. PC-140: what was cleared is taken back inside the sheet, not behind it.
     */
    @Test fun whatKeepsAnAlarmFromWorkingStaysUntilFixed() {
        var broken by mutableStateOf(true)
        val state = NoticeStackState()
        show({
            Column {
                NoticeStack(buildList {
                    if (broken) add(Notice("alarm:notifications", "알림 꺼짐", line = "알림이 꺼져 있어 알람이 소리만 나요", urgent = true, keep = true) { box() })
                    add(Notice("tip", "오늘의 팁", line = "팁 한 줄") { box() })
                }, state = state)
                NoticeBell(state)
            }
        }) {
            compose.onNodeWithText("알림이 꺼져 있어 알람이 소리만 나요").assertIsDisplayed()
            state.openSheet(null)
            compose.waitForIdle()
            compose.onNodeWithContentDescription("닫기: 알림 꺼짐").assertDoesNotExist()
            compose.onNodeWithContentDescription("더 보기: 알림 꺼짐").assertDoesNotExist()
            compose.onNodeWithText("전체 지움").performClick()
            compose.waitForIdle()
            assertEquals("남는 것은 알람 경고 하나", 1, state.inBell)
            compose.onNodeWithText("알림 꺼짐").assertExists()
            // PC-140: 되돌리기 is in the sheet, under its title.
            compose.onNodeWithText("되돌리기").performClick()
            compose.waitForIdle()
            assertEquals(2, state.inBell)
            compose.onNodeWithText("전체 지움").performClick()
            compose.waitForIdle()
            state.sheetOpen = false
            compose.waitForIdle()
            assertTrue("띠는 그대로", state.snoozed.none { it.startsWith("alarm") })
            // The band's X puts it in the bell for this visit only; fixed and broken again, the band comes again.
            compose.onNodeWithContentDescription("알림함에 넣기").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("알림이 꺼져 있어 알람이 소리만 나요").assertDoesNotExist()
            assertEquals("종에는 남아요", 1, state.inBell)
            assertTrue("앱을 다시 열면 다시 띠", "alarm:notifications" !in NoticeSnooze.today(app))
            broken = false
            compose.waitForIdle()
            broken = true
            compose.waitForIdle()
            assertTrue("고쳤다 다시 꺼지면 다시 알려요", "alarm:notifications" !in state.snoozed)
        }
    }

    @Test fun theInboxPutsPeopleFirstSuggestionsLastAndEmptiesWithClearAll() {
        Inbox.post(app, InboxRow("market.work:1", "market.reaction", InboxSection.PEOPLE, "내 작품 ‘봄’", "댓글 2개 · 좋아요 5개",
            target = NoticeLinks.Target.MONEY))
        val state = NoticeStackState(sheet = true)
        show({
            NoticeStack(listOf(
                Notice("discover", "이런 기능도 있어요", line = "안 써 본 기능 하나", section = InboxSection.SUGGEST) { box() },
                Notice("tip", "오늘의 팁", line = "팁 한 줄") { box() },
            ), state = state)
        }) {
            val people = compose.onNodeWithText("사람들 반응").getBoundsInRoot().top
            val notice = compose.onNodeWithText("안내").getBoundsInRoot().top
            val suggest = compose.onNodeWithText("권유").getBoundsInRoot().top
            assertTrue("반응 → 안내 → 권유: $people $notice $suggest", people < notice && notice < suggest)
            compose.onNodeWithText("전체 지움").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("새 알림이 없어요").assertExists()
            assertTrue(Inbox.get(app).rows.value.isEmpty())
            assertEquals(0, state.inBell)
        }
    }

    @Test fun aKindTurnedOffDoesNotComeAgain() {
        val state = NoticeStackState()
        show({
            NoticeStack(listOf(Notice("discover", "이런 기능도 있어요", line = "안 써 본 기능 하나", section = InboxSection.SUGGEST,
                kind = "suggest.discover") { box() }), state = state)
        }) {
            assertEquals(1, state.inBell)
            Inbox.get(app).mute("suggest.discover", "안 써 본 기능")
            compose.waitForIdle()
            assertEquals(0, state.inBell)
            // An urgent one is never turned off from the 알림함.
            Inbox.get(app).unmute("suggest.discover")
            Inbox.get(app).setSuggestOff(true)
            compose.waitForIdle()
            assertEquals("권유 끄기", 0, state.inBell)
        }
    }
}
