package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 5 (검토 HQ-75·HQ-76·UIX-19, 조건 9 '떠 있는 단추가 글·카드를 가리지 않아요'): with big letters the cards fill the width, so
 * even the icon alone sat on the ends of amounts while the page rested at the top ('838,282원 / 1,600,000원'). There the add button is a
 * bar under the page ([com.bamnat.app.ui.components.AddDock]): nothing is under it at rest, and it says its words. 총괄 HQ-111: at 100%
 * it sat on '-4,500원' too, so the bar is the rule wherever the cards fill the width. 가계부 요약·내역과 메모, 접은 폭 밝은·어두운 200%·100%,
 * 411dp 폰 100%, 편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP5ShotsTest : ScreenshotBase() {
    private fun Screen.nothingUnderTheAddButton() {
        LayoutCheck.underFab(nodes(merged = false)).let { check(it.isEmpty()) { "the add button covers $it" } }
    }

    @Test fun moneyDockedLight() = shoot("rvp5_01_money_dock_folded_light_200", FOLDED, 2f) {
        tab(1, wide = false)
        check(appears("돈 적기", exact = true)) { "no 돈 적기 bar" }
        nothingUnderTheAddButton()
    }

    @Test fun moneyDockedDark() = shoot("rvp5_02_money_dock_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f) {
        tab(1, wide = false)
        nothingUnderTheAddButton()
    }

    @Test fun moneyListDocked() = shoot("rvp5_03_money_list_dock_folded_200", FOLDED, 2f) {
        tab(1, wide = false)
        tapText("내역", exact = true, scrolls = 0)
        nothingUnderTheAddButton()
    }

    @Test fun memoDocked() = shoot("rvp5_04_memo_dock_folded_200", FOLDED, 2f) {
        tab(2, wide = false)
        nothingUnderTheAddButton()
    }

    /** 총괄 HQ-111: at 100% too the floating 돈 적기 sat on '-4,500원' (411dp 폰, 접은 폭); now the bar under the page. */
    @Test fun moneyDockedAt100() = shoot("rvp5_05_money_dock_phone411_100", "w411dp-h914dp-port-420dpi") {
        tab(1, wide = false)
        check(appears("돈 적기", exact = true)) { "no 돈 적기" }
        nothingUnderTheAddButton()
    }

    @Test fun moneyDockedFolded100() = shoot("rvp5_06_money_dock_folded_100", FOLDED) {
        tab(1, wide = false)
        nothingUnderTheAddButton()
    }

    /** 편 폭: the bar is the page column's width, in the middle (조건 12). */
    @Test fun moneyDockedOpen() = shoot("rvp5_07_money_dock_open_dark_100", UNFOLDED.replace("-port-", "-port-night-")) {
        tab(1, wide = true)
        nothingUnderTheAddButton()
    }

    @Test fun memoDocked100() = shoot("rvp5_08_memo_dock_folded_100", FOLDED) {
        tab(2, wide = false)
        nothingUnderTheAddButton()
    }

    /**
     * 검토 PC-180 (총괄 11-19): the same 권유 closed with its ✕ a second time is off for good, said inside the 알림함 in the app's one line
     * with 되돌리기 (조건 3·10·16). 접은 폭 밝은·어두운 200%.
     */
    @Test fun aSuggestionClosedTwiceIsOffLight() = shoot("rvp5_11_suggest_off_folded_light_200", FOLDED, 2f) { closeTwice() }

    @Test fun aSuggestionClosedTwiceIsOffDark() = shoot("rvp5_12_suggest_off_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f) {
        closeTwice()
    }

    private fun Screen.closeTwice() {
        val inbox = com.bamnat.app.inbox.Inbox.get(app)
        // Only this 권유 in the 알림함 (one 권유 shows at a time): the calendar's own are off for this shot.
        listOf("calendar.plazaShare", "calendar.shareHint", "suggest.discover", "calendar.bossLedger", "calendar.alarmSuggest",
            "calendar.publicCycle", "calendar.proMoment", "invite.friend").forEach { inbox.mute(it) }
        val row = com.bamnat.app.inbox.InboxRow("suggest.companyRule:1", "suggest.companyRule", com.bamnat.app.inbox.InboxSection.SUGGEST,
            "회사 규칙으로 올리기", "직접 만든 설정을 같은 회사 동료와 나눠 볼까요?")
        // Closed once two weeks ago; it came again today.
        inbox.close(row.key, row.kind, row.title, com.bamnat.core.time.Today.date().minusDays(14))
        check(inbox.post(row)) { "2주 뒤 다시 와요" }
        settle()
        tapText("알림 ", prefix = true, scrolls = 0)
        tapText("닫기: 회사 규칙으로 올리기", exact = true, scrolls = 0)
        check(appears(com.bamnat.app.ui.calendar.SUGGEST_OFF, exact = false)) { "두 번째 ✕의 말이 없어요" }
        check(appears("되돌리기", exact = true)) { "되돌리기가 없어요" }
        check("suggest.companyRule" in inbox.muted.value)
    }
}
