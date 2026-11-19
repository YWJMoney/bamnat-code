package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 10묶음의 앞·뒤 화면 사진: 가게 날짜 창의 근로자 휴식 경고([RVH-72] TYPE-03·T30-11), 가게 날짜 창 › 몸의 잠 계획 말
 * ([RVH-75] TYPE-08·T30-16), 관리 화면 이름([RVH-78] T30-105). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%,
 * 편 폭 어두운 100%).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH10ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun one(name: String, look: Int, style: WorkStyle, go: Screen.() -> Unit) {
        val (l, q, scale) = looks[look]
        shoot("rvh10_${name}_$l", q, scale, style(style) { go() })
    }

    /**
     * TYPE-03: a shop that worked late into an early opening. Before, its day sheet's head had the 근로자 '야간 뒤 휴식 부족' chip and the
     * week in 주의 (a shop owner has no 근로시간 limit to break); now the rest check stays on 교대·일근·알바 only.
     */
    private fun shopRest(): Screen.() -> Unit = {
        nightIntoDay().invoke(this)
        runBlocking {
            val repo = app.container.scheduleRepository
            if (repo.snapshot.first().resolver.resolve(today.minusDays(3)).extras.none { it.covered != null })
                repo.addExtra(today.minusDays(3), cover(repo.shiftTypes.first().first { it.name == "야간" }))
        }
        settle()
        openDay(today.minusDays(3))
        settle()
        // The shop's own day: 매출 적기 is its first chip.
        assertTrue(shownWords().joinToString(" | ").take(1500), appears("매출 적기", exact = true))
        assertFalse("가게에 근로자 휴식 경고", shows("휴식 부족") || shows("연속 근무") || shows("연속 야간"))
    }
    @Test fun shopRestA() = one("50_shop_rest_head", 0, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestB() = one("50_shop_rest_head", 1, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestC() = one("50_shop_rest_head", 2, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestD() = one("50_shop_rest_head", 3, WorkStyle.BUSINESS, shopRest())

    /** TYPE-08: the night a shop stays open late, its 몸 group plans the sleep in 마감 words (was '퇴근 뒤 잠·퇴근길 졸음'). */
    private fun shopSleep(across: Float): Screen.() -> Unit = {
        nightIntoDay().invoke(this)
        openDay(today.minusDays(4))
        settle()
        openGroup("몸", across)
        settle()
        reveal("마감 뒤", across = across)
        assertFalse("가게 잠 계획에 '퇴근'", shows("퇴근"))
    }
    @Test fun shopSleepA() = one("51_shop_sleep", 0, WorkStyle.BUSINESS, shopSleep(FOLDED_ACROSS))
    @Test fun shopSleepB() = one("51_shop_sleep", 1, WorkStyle.BUSINESS, shopSleep(FOLDED_ACROSS))
    @Test fun shopSleepC() = one("51_shop_sleep", 2, WorkStyle.BUSINESS, shopSleep(OPEN_ACROSS))
    @Test fun shopSleepD() = one("51_shop_sleep", 3, WorkStyle.BUSINESS, shopSleep(OPEN_ACROSS))

    /** T30-105: the 관리 화면 of someone who leads a team and runs a 사업장 is '팀·사업장 관리', its ledger card '직원 급여 대장'. */
    private fun manage(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.ManageScreen(PersonalCalendar.MAIN, onBack = {}, showAll = true) }
        settle()
        assertTrue(appears("팀·사업장 관리", exact = true))
        assertFalse(shows("가게"))
    }
    @Test fun manageA() = one("52_manage", 0, WorkStyle.FIELD, manage())
    @Test fun manageB() = one("52_manage", 1, WorkStyle.FIELD, manage())
    @Test fun manageC() = one("52_manage", 2, WorkStyle.FIELD, manage())
    @Test fun manageD() = one("52_manage", 3, WorkStyle.FIELD, manage())

    /**
     * HQ-99 (G가 본 것, 조건 1·9): on a short screen (a split window, a phone on its side) the summary's bottom keeps only '저장하고 달력 보기'; the
     * 사용 통계 card waits in the scroll, under the month, as at a big font ([RVH-47]).
     */
    @Test fun shortSummary() {
        // A new phone on its first screen, as [ReviewH3ShotsTest.setupSummary] starts it.
        app.container.consentStore.forget()
        app.container.consentStore.availableForTest = setOf(com.bamnat.core.consent.ConsentItem.CALENDAR)
        app.container.setupDrafts.clear()
        shoot("rvh10_53_short_summary", SHORT, 1f, fresh(null) {
            setupToToday(); tapText("회사 근무표와 같아요")
            assertTrue(appears("이대로 저장할까요?"))
            val tall = nodes(false).maxOf { it.boundsInWindow.bottom }
            val save = nodes(false).first { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
                ?.any { it.text == "저장하고 달력 보기" } == true }.boundsInWindow
            val card = nodes(false).firstOrNull { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
                ?.any { it.text.startsWith("앱을 고치는 데 도움을") } == true }?.boundsInWindow
            assertFalse("통계 카드가 저장 단추 바로 위 고정 띠에: 카드 $card, 단추 $save", card != null && card.top >= 0f && card.bottom <= save.top && card.top > tall / 3)
            assertTrue("저장 단추가 화면 아래에: $save / $tall", save.top > tall / 2)
        })
    }

    private companion object {
        /** A short window (a phone split in two, a small phone): lower than [com.bamnat.app.ui.setup.SHORT_SCREEN_DP]. */
        const val SHORT = "w369dp-h560dp-port-420dpi"
        const val FOLDED_ACROSS = 0.5f
        const val OPEN_ACROSS = 0.8f
    }
}
