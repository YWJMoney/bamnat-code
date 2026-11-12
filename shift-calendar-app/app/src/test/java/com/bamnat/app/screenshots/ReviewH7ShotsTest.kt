package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.WebStart
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.setup.WebHandoff
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVH-68] 검토 I50c-01·I50c-03 (RVR-31, SPREAD-30·31): what bamnat.app's Play link carried, on the first setup. The card on the
 * question after the kind of work (교대: the order and today; 알바: the 시급), the times' screen it fills, and 근무 주기's 같은 조 동료에게
 * 보내기. Four looks (접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%); a look a test where the setup's
 * answers change the phone's data.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH7ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    /** A new phone whose install came from the site with [content] (null: from anywhere else). */
    private fun fromSite(content: String?, then: Screen.() -> Unit): Screen.() -> Unit {
        app.container.webStart.putForTest(content?.let { WebHandoff.read(it, "shift_cal") })
        return fresh(null, then)
    }

    private fun one(name: String, look: Int, go: Screen.() -> Unit) {
        val (l, q, scale) = looks[look]
        shoot("rvh7_${name}_$l", q, scale, go)
    }

    /** The 교대 card: "웹에서 보던 주야비휴, 11월 10일부터 채울까요?" with 채우기 and X, over the company question. */
    private fun card(look: Int) = one("43_web_card", look, fromSite("p-DNBO_s-20261110") {
        tapText("교대 근무 · 순서가 돌아가요", exact = true)
        settle()
        assertTrue(appears("웹에서 보던 주야비휴, 11월 10일부터 채울까요?", exact = true))
    })
    @Test fun cardA() = card(0)
    @Test fun cardB() = card(1)
    @Test fun cardC() = card(2)
    @Test fun cardD() = card(3)

    /** 채우기: 2교대 with a 비번, 주야비휴 in, 11월 10일 its first day; the times to check, the rest answered. */
    private fun filled(look: Int) = one("44_web_filled", look, fromSite("p-DNBO_s-20261110") {
        tapText("교대 근무 · 순서가 돌아가요", exact = true)
        settle()
        tapText("채우기", exact = true)
        settle()
        val d = app.container.setupDrafts.draft.value!!
        assertEquals(SetupStep.TIMES, d.step)
        assertEquals(LocalDate.of(2026, 11, 10), d.anchor)
        assertEquals(WebStart.USED, app.container.webStart.answer.value)
    })
    @Test fun filledA() = filled(0)
    @Test fun filledB() = filled(1)
    @Test fun filledC() = filled(2)
    @Test fun filledD() = filled(3)

    /** The calculator's 시급 on an 알바's times: "웹에서 넣은 시급 10,320원으로 시작할까요?". */
    private fun wage(look: Int) = one("45_web_wage", look, fromSite("w-10320") {
        tapText("아르바이트 · 시급", exact = true)
        settle()
        assertTrue(appears("웹에서 넣은 시급 10,320원으로 시작할까요?", exact = true))
    })
    @Test fun wageA() = wage(0)
    @Test fun wageB() = wage(1)
    @Test fun wageC() = wage(2)
    @Test fun wageD() = wage(3)

    /** Before: the same questions from a phone that did not come from the site (what every phone saw before). */
    private fun before(look: Int) {
        one("43_web_card_before", look, fromSite(null) { tapText("교대 근무 · 순서가 돌아가요", exact = true); settle() })
    }
    /** Before 채우기: what the same person tapped instead (회사 건너뛰기, 2교대), reaching the times with 2교대's own order. */
    private fun beforeFilled(look: Int) = one("44_web_filled_before", look, fromSite(null) {
        tapText("교대 근무 · 순서가 돌아가요", exact = true); settle()
        tapText("건너뛰고 직접 정할게요", exact = true); settle()
        tapText("2교대", exact = true); settle()
    })
    @Test fun beforeFilledA() = beforeFilled(0)
    @Test fun beforeFilledB() = beforeFilled(1)
    @Test fun beforeFilledC() = beforeFilled(2)
    @Test fun beforeFilledD() = beforeFilled(3)
    private fun beforeWage(look: Int) = one("45_web_wage_before", look, fromSite(null) { tapText("아르바이트 · 시급", exact = true); settle() })
    @Test fun beforeWageA() = beforeWage(0)
    @Test fun beforeWageB() = beforeWage(1)
    @Test fun beforeWageC() = beforeWage(2)
    @Test fun beforeWageD() = beforeWage(3)

    @Test fun beforeA() = before(0)
    @Test fun beforeB() = before(1)
    @Test fun beforeC() = before(2)
    @Test fun beforeD() = before(3)

    /** 근무 주기 with the saved order: 같은 조 동료에게 보내기 beside 저장 (교대 only). */
    @Test fun patternShare() {
        for ((l, q, scale) in looks) shoot("rvh7_46_pattern_share_$l", q, scale, style(WorkStyle.SHIFT) {
            val p = runBlocking { app.container.scheduleRepository.snapshot.first().patterns.lastOrNull() }
            assertTrue("saved order", p is WorkPattern.Rotation)
            com.bamnat.app.ui.guide.FeatureLinks.open(com.bamnat.core.guide.FeatureRoute(com.bamnat.core.guide.Place.PATTERN))
            settle()
            assertTrue(appears("같은 조 동료에게 보내기", exact = true))
        })
    }
}
