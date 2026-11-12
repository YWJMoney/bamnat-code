package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.PlazaInbox
import com.bamnat.app.inbox.Inbox
import com.bamnat.core.plaza.PlazaNewsItem
import com.bamnat.core.plaza.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-14][RVN-20] 검토 HQ-51, 총괄 11-12 (광장·회사 알림은 알림함으로): the bell's 알림함 with what people did to my plaza things
 * (a row a thing, 답 3 · 나도요 2) and the day's 광장 소식 (채택, 신고 결과), each opening its own place. New in 6차: no 'before'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn6Test : ScreenshotBase() {
    private fun inbox(): Screen.() -> Unit = {
        val c = companyOn()
        Inbox.reset()
        val now = com.bamnat.core.time.Today.millis()
        PlazaInbox.fromDigest(c, listOf(
            Reaction("agree", "pAbCdEfGhIjKlMnO", "교대 광장", 2, now - 3_600_000, room = "style-shift", setup = "room", up = 2),
            Reaction("report_vote", "c1aaaaaaaaaa/s1", "(주)예시정밀", 1, now - 7_200_000, up = 1),
        ), answersCounted = true)
        PlazaInbox.news(c, listOf(
            PlazaNewsItem("adopted", company = "(주)예시정밀", companyId = "c1aaaaaaaaaa", atMillis = now - 600_000),
            PlazaNewsItem("flag_result", result = "kept", post = "pZyXwVuTsRqPoNmL", atMillis = now - 900_000),
        ))
        val state = com.bamnat.app.ui.calendar.NoticeStackState(sheet = true)
        content { com.bamnat.app.ui.calendar.NoticeStack(emptyList(), state = state) }
        settle()
        assertTrue(appears("사람들 반응", exact = true))
        assertTrue(appears("규칙 채택", exact = true))
        assertTrue(appears("보낸 (주)예시정밀 규칙이 채택됐어요.", exact = false))
        assertTrue(appears("교대 광장 · 나도요 2", exact = false))
    }

    @Test fun inboxLight() = shoot("rvn_82_inbox_plaza_folded", FOLDED, inbox())
    @Test fun inboxDarkBig() = shoot("rvn_83_inbox_plaza_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, inbox())
    @Test fun inboxWide() = shoot("rvn_84_inbox_plaza_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, inbox())

    /** U10a-02·I10ab-03 A plaza nobody wrote in yet: 말랑 칸이 and the 운영팀's welcome, the week's topic, three ways to start. */
    @Test fun aQuietPlazaOffersWhatToWriteOneTapEach() = shoot("rvn_t_plaza_starter", FOLDED) {
        val c = companyOn()
        app.container.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.SHIFT) }
        company.talkPosts = emptyList()
        plazaPeopleOn(c)
        val vm = com.bamnat.app.ui.company.PlazaHomeViewModel(c, own = true)
        content { com.bamnat.app.ui.company.PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        reveal("아직 첫 이야기를 기다리고 있어요", exact = true)
        // No topic from the 운영팀 this week: the plaza's own, so the box above is never blank.
        assertTrue(appears("이번 주 주제 · " + com.bamnat.core.plaza.StarterTopics.of(com.bamnat.core.time.Today.date()), exact = true))
        reveal("📊 투표 만들기", exact = true)
        tapText("📊 투표 만들기", exact = true)
        settle()
        assertNotNull(vm.talk.open.value)
        assertEquals("question", vm.talk.kind.value)
    }

    /** The same on a phone before 3.12's 사람 먼저: the plaza's card has the topic and the three ways in place of one line. */
    @Test fun aQuietPlazaBefore312OffersTheSame() = shoot("rvn_t_plaza_starter_before312", FOLDED) {
        val c = companyOn()
        app.container.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.SHIFT) }
        company.talkPosts = emptyList()
        val vm = com.bamnat.app.ui.company.PlazaHomeViewModel(c, own = true)
        content { com.bamnat.app.ui.company.PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        assertTrue(gone("아직 글이 없어요. 처음 이야기를 꺼내 주세요.", exact = true))
        assertTrue(appears("이번 주 주제 · " + com.bamnat.core.plaza.StarterTopics.of(com.bamnat.core.time.Today.date()), exact = true))
        reveal("🙋 궁금한 것 묻기", exact = true)
        tapText("🙋 궁금한 것 묻기", exact = true)
        settle()
        assertNotNull(vm.talk.open.value)
        assertEquals("question", vm.talk.kind.value)
    }

    /** U10a-03 A person on every card: the writer's first letter in a circle (mine is '나'), read out with the name, not twice. */
    @Test fun everyCardHasTheWritersInitial() = shoot("rvn_t_plaza_initial", FOLDED) {
        val c = companyOn()
        app.container.settingsRepository.update { it.copy(workStyle = com.bamnat.core.work.WorkStyle.SHIFT) }
        plazaPeopleOn(c)
        val vm = com.bamnat.app.ui.company.PlazaHomeViewModel(c, own = true)
        content { com.bamnat.app.ui.company.PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        reveal("11월부터 주간이 한 시간 줄었어요", exact = false)
        // The starter is for a quiet plaza only: four posts here.
        assertTrue(gone("이렇게 시작해 보세요", exact = true))
    }

    /** HQ-90 (총괄 사진 검토 11-13): a department's 근무 row is one shift a line, and with big letters the name above it. */
    @Test fun theShiftsOfADepartmentAreOneALine() = shoot("rvn_t_dept_shifts", Rvn5ScreenshotTest.DARK, 2f) {
        val c = companyOn()
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        reveal("야간 20:00~08:00 휴게 60분", exact = true)
        assertTrue(appears("주간 08:00~20:00 휴게 60분", exact = true))
        assertTrue(appears("휴무(쉬는 날)", exact = true))
    }
}
