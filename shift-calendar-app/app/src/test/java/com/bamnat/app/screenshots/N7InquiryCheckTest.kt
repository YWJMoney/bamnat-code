package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryScreen
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.app.ui.settings.InquiryViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-22] 검토 PC-27: a question's 점검 기록 goes only when the user picks it, as 처리방침 3.6 says ('점검 기록도 보내기'를
 * 고르고 보낼 때만). Choosing 앱이 이상해요 offers it with why it helps and leaves it unticked; a button that names the check
 * (점검 내용과 함께 문의하기, 멈춤 기록과 함께 알려 주기) opens with it ticked.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class N7InquiryCheckTest : ScreenshotBase() {
    /** The 점검 기록도 보내기 row's box, once it was scrolled to. */
    private fun Screen.box(): ToggleableState? {
        check(scrolledTo("점검 기록도 보내기")) { "점검 기록 줄이 없어요" }
        settle()
        return nodes().firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { "점검 기록도 보내기" in it.text } &&
                n.config.getOrNull(SemanticsProperties.ToggleableState) != null
        }?.config?.getOrNull(SemanticsProperties.ToggleableState)
    }

    private fun Screen.open(start: InquiryStart) {
        InquiryLinks.ask(start)
        val vm = InquiryViewModel(app.container)
        content { InquiryScreen(vm, onBack = {}) }
        settle()
    }

    @Test fun aBugReportOffersTheCheckWithoutTickingIt() = shoot("n7_inquiry_bug_check_offered", FOLDED) {
        open(InquiryStart(about = "시험"))
        tapText("앱이 이상해요")
        check(box() == ToggleableState.Off) { "앱이 이상해요를 고르기만 했는데 점검 기록이 골라졌어요" }
        check(shows("오류는 이 기록이 있으면 더 빨리 찾아요")) { "왜 보내면 좋은지 한 줄이 없어요" }
        // Picked by the user, it goes; the line about why is not needed any more.
        tapText("점검 기록도 보내기")
        check(box() == ToggleableState.On)
        check(!shows("오류는 이 기록이 있으면 더 빨리 찾아요"))
    }

    @Test fun aButtonThatNamesTheCheckOpensWithItTicked() = shoot("n7_inquiry_named_check_ticked", FOLDED) {
        open(InquiryStart(about = "알람", kind = InquiryKind.BUG, withCheck = true))
        check(box() == ToggleableState.On) { "점검 내용과 함께 문의하기로 열었는데 점검 기록이 빠졌어요" }
    }

    @Test fun anotherKindNeverTicksIt() = shoot("n7_inquiry_question_check_off", FOLDED) {
        open(InquiryStart(about = "시험"))
        tapText("궁금해요")
        check(box() == ToggleableState.Off)
        check(!shows("오류는 이 기록이 있으면 더 빨리 찾아요"))
    }
}
