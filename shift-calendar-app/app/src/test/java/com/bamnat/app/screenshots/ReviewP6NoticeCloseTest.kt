package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.YearRecap
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 6 검토 HQ-145 (중간, 총괄 11-19: 연말 결산 안내를 알림함 안에서 닫으면 되돌리기 말이 알림함 뒤에 뜨고, '전체 지움' 때 말이 두
 * 번 나와요): a notice that is closed and taken back says so inside the 알림함 it is closed in (조건 10·16), and 전체 지움 says its one
 * line only. HQ-136 (낮음): a 안내 card taller than the 56dp band has its X at the first line, not in the middle of its height.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP6NoticeCloseTest : ScreenshotBase() {
    @Before fun december() {
        Today.fix(java.time.ZonedDateTime.of(2026, 12, 22, 10, 30, 0, 0, SEOUL))
        // A phone already told what is new (as YearRecapShotsTest), so the bell can be reached.
        com.bamnat.app.data.GuideStore.get(app).let { g ->
            g.whatsNewSeen(com.bamnat.app.BuildConfig.VERSION_CODE)
            g.switchedSeen(com.bamnat.core.guide.WhatsNew.switched.map { it.first })
        }
    }
    @After fun clockBack() = Today.fix(SHOT_TIME)

    private val seen get() = app.container.settingsRepository.current.yearRecapSeen

    private fun Screen.closeInBell() {
        tapText("알림 ", prefix = true, scrolls = 0)
        settle()
        reveal(ROW, exact = true)
        val before = seen
        tapText("닫기: $ROW", exact = true, scrolls = 0)
        check(appears(CLOSED, exact = false)) { "닫은 말이 없어요" }
        check(seen == YearRecap.key(Today.date())) { "결산 안내가 닫히지 않았어요" }
        // Its 되돌리기 is the 알림함's own line (the sheet is still open: its title is there).
        check(appears("되돌리기", exact = true)) { "되돌리기가 없어요" }
        check(shows("알림", exact = false))
        tapText("되돌리기", exact = true, scrolls = 0)
        check(seen == before) { "되돌리기가 결산 안내를 돌려놓지 않았어요" }
        check(appears(ROW, exact = true)) { "되돌린 결산 줄이 알림함에 없어요" }
        // Closed again for the picture.
        tapText("닫기: $ROW", exact = true, scrolls = 0)
        check(appears(CLOSED, exact = false))
    }

    @Test fun closedInTheBellLight() = shoot("rvp6_recap_closed_in_bell_folded_light_200", FOLDED, 2f, style(WorkStyle.SHIFT) { closeInBell() })

    @Test fun closedInTheBellDark() = shoot("rvp6_recap_closed_in_bell_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f,
        style(WorkStyle.SHIFT) { closeInBell() })

    /** 전체 지움: its one line, and not the 결산's own after the 알림함 closes (it said both). */
    @Test fun clearAllSaysOnce() = shoot("rvp6_recap_clear_all_folded_light_200", FOLDED, 2f, style(WorkStyle.SHIFT) {
        tapText("알림 ", prefix = true, scrolls = 0)
        settle()
        reveal(ROW, exact = true)
        tapText("전체 지움", exact = true, scrolls = 0)
        check(appears("알림을", exact = false)) { "전체 지움의 말이 없어요" }
        // The 알림함 closed by a tap beside it, as a person does (the back key is the sheet's own window's).
        tap(180f, 40f)
        check(gone("전체 지움", exact = true)) { "알림함이 닫히지 않았어요" }
        check(seen == YearRecap.key(Today.date())) { "전체 지움이 결산 안내를 닫지 않았어요" }
        check(!appears(CLOSED, exact = false, ms = 3_000)) { "전체 지움 뒤에 결산의 말이 또 나왔어요" }
    })

    /** HQ-136: a long 안내 at 200% on the folded width, its X at the first line (light and dark). */
    private fun Screen.tallCard() {
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 24.dp)) {
                com.bamnat.app.ui.components.NudgeCard(LONG, androidx.compose.ui.Modifier.padding(horizontal = 16.dp), detail = DETAIL,
                    action = "결산 보기", onClose = {}, closeLabel = "결산 안내 닫기")
            }
        }
        settle()
        val words = nodes(merged = false).first { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == LONG } == true }
        val x = nodes(merged = true).first { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("결산 안내 닫기") == true }
        val top = words.boundsInWindow.top
        val middle = words.boundsInWindow.center.y
        // The X's middle is nearer the words' first line than the middle of them all.
        check(words.boundsInWindow.height > x.boundsInWindow.height * 1.5f) { "글이 두 줄 넘게 길지 않아요" }
        check(x.boundsInWindow.center.y - top < middle - top - 4f) { "X가 글의 첫 줄이 아니라 가운데에 있어요: X ${x.boundsInWindow}, 글 ${words.boundsInWindow}" }
    }

    @Test fun tallCardLight() = shoot("rvp6_nudge_tall_folded_light_200", FOLDED, 2f) { tallCard() }

    @Test fun tallCardDark() = shoot("rvp6_nudge_tall_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f) { tallCard() }

    private companion object {
        const val ROW = "2026년 내 근무 결산"
        const val CLOSED = "결산 안내를 닫았어요"
        const val LONG = "2026년 내 근무 결산이 나왔어요. 야간 근무와 쉰 날을 한 장으로 봐요"
        const val DETAIL = "야간 82일 · 주간 96일 · 쉰 날 120일"
    }
}
