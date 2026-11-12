package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.consent.ConsentItem
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicValue
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.ValueReset
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 3묶음의 앞·뒤 화면 사진(디자인 품질 조건 확인): 작품의 바로 단추가 달력 머리 줄의 단추로([RVH-42] 검토 PC-29·30).
 * 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%)으로 그려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH3ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh_${name}_$look", q, scale, style(style) { go() })
    }

    /** A new phone on its first screen (no kind of work yet), as [SScreenshotTest] starts it. */
    private fun allNew(name: String, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) {
            app.container.consentStore.forget()
            app.container.consentStore.availableForTest = setOf(ConsentItem.CALENDAR)
            // The setup in progress lives past one shot (the draft store); each look starts on the first screen.
            app.container.setupDrafts.clear()
            shoot("rvh_${name}_$look", q, scale, fresh(null) { go() })
        }
    }

    /** '야간 회복 스테이션'의 머리 칩 '물 +1잔'(작품 그대로: 오른쪽 아래, '도구'·'나' 자리)과 그 단추. */
    private fun Screen.wearWater() {
        app.container.settingsRepository.update {
            it.copy(
                decor = Decor(listOf(DecorItem(DecorKind.TEXT, "물 +1잔", at = Decor.HEAD, x = 850, y = 690, w = 104, h = 26, fill = 0xFF7CF0FF,
                    ink = 0xFF04121A, r = 13, size = 11.0, weight = 800, over = true, tap = "water"))).encode(),
                logic = Logic.encode(Logic(
                    buttons = listOf(LogicButton("물 한 잔", "💧", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0), givenId = "water")),
                    values = listOf(LogicValue("물 잔", ValueReset.DAY, goal = 6.0, unit = "잔")),
                )),
            )
        }
        settle()
    }

    @Test fun headQuick() = all("14_head_quick", WorkStyle.SHIFT) { wearWater() }

    /** PC-29: 도구 opens the tools, not the work's button; PC-30: the button is read by its words and its name. */
    @Test fun theToolsAreTheToolsAndTheButtonIsRead() = shoot("rvh_14_head_quick_tools", FOLDED) {
        wearWater()
        assertTrue("바로 단추를 읽어 줘요", appears("물 +1잔, 물 한 잔", exact = true))
        tapText("도구", exact = true)
        settle()
        assertTrue("도구를 누르면 도구가 열려요", appears("달력만 보기", exact = true) || appears("카드도 함께 보기", exact = true))
        assertFalse("물 단추는 도구 안에 따로 없어요(머리에 있어요)", appears("💧 물 +1잔", exact = true))
    }

    /** [RVH-43] PC-56: the first screen starts with the question; text size is the '가' button, 칸이 says hello in one line. */
    @Test fun firstScreen() = allNew("15_first_screen") {
        assertTrue(appears("어떤 일을 하고 계신가요?"))
    }

    /** [RVH-43] PC-67: 평일 근무 is not offered the 일용직 일당 template. */
    @Test fun dayCompany() = allNew("16_day_company") {
        tapText("평일 근무", exact = true)
        assertTrue(appears("어느 회사에서 일하세요?"))
        assertFalse("일근에게 일당을 권하지 않아요", appears("일용직 일당"))
    }

    /** [RVH-43] PC-31: the month on '이대로 저장할까요?' is the month the save makes, from the 1st. */
    @Test fun setupSummary() = allNew("17_setup_summary") {
        setupToToday(); tapText("회사 근무표와 같아요")
        assertTrue(appears("이대로 저장할까요?"))
    }
}
