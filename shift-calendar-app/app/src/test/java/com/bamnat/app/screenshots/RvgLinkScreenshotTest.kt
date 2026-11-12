package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.SharedDay
import com.bamnat.app.share.SharedSchedule
import com.bamnat.app.share.SharedType
import com.bamnat.app.ui.onboarding.FirstStyleScreen
import com.bamnat.app.ui.setup.LinkStart
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * [RVG-15][RVG-16][RVG-18] 받은 근무표 링크로 새로 깐 사람의 첫 화면과 요약 (2.0.0 디자인 품질 조건): 밝은·어두운 × 글자 100%·200% × 폴드
 * 접은 폭·편 폭. 채운 단추는 하나, 단추 글이 잘리지 않고, 다음 달 근무표는 그 달로 열려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvgLinkScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun Screen.first(title: String, lean: LinkStart.Owner) = content {
        FirstStyleScreen(onPick = {}, onSkip = {}, onLater = {}, textStep = c.settingsRepository.current.textStep,
            linkWaiting = true, linkTitle = title, linkLean = lean, onLinkStart = {}, onLinkTheirs = {}, onLinkClose = {})
    }

    private fun family(): Screen.() -> Unit = {
        first("민지", LinkStart.Owner.THEIRS)
        settle()
        assertTrue(appears("가족·동료 것이에요"))
        assertTrue(appears("내 근무표예요"))
    }

    @Test fun familyFoldLight() = shoot("rvg15_first_family_fold_light_100", FOLDED, family())
    @Test fun familyFoldDark() = shoot("rvg15_first_family_fold_dark_100", FOLDED_NIGHT, family())
    @Test fun familyFoldLightHuge() = shoot("rvg15_first_family_fold_light_200", FOLDED, fontScale = 2f, family())
    @Test fun familyFoldDarkHuge() = shoot("rvg15_first_family_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, family())
    @Test fun familyOpenLight() = shoot("rvg15_first_family_open_light_100", UNFOLDED, family())
    @Test fun familyOpenDarkHuge() = shoot("rvg15_first_family_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, family())

    @Test fun teamFoldLight() = shoot("rvg15_first_team_fold_light_100", FOLDED) {
        first("3병동 김간호", LinkStart.Owner.MINE)
        settle()
    }

    private fun kept(): Screen.() -> Unit = {
        content { FirstStyleScreen(onPick = {}, onSkip = {}, onLater = {}, textStep = c.settingsRepository.current.textStep, theirsKept = "민지") }
        settle()
    }
    @Test fun keptFoldLight() = shoot("rvg15_first_kept_fold_light_100", FOLDED, kept())
    @Test fun keptFoldDarkHuge() = shoot("rvg15_first_kept_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, kept())

    /** A 리더's member link of next month (도약 14): the summary opens on that month, with '어떻게 일하세요?' under it. */
    private fun summary(): Screen.() -> Unit = {
        val next = YearMonth.from(today).plusMonths(1)
        val types = listOf(
            SharedType(1, "데이", "D", 0xFF2196F3, "07:00-15:00", false, null),
            SharedType(2, "이브닝", "E", 0xFFFF9800, "15:00-23:00", false, null),
            SharedType(3, "나이트", "N", 0xFF673AB7, "23:00-07:00", false, null),
            SharedType(4, "오프", "O", 0xFF9E9E9E, null, true, null),
        )
        val order = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L, 4L)
        val days = (1..next.lengthOfMonth()).associate { d -> next.atDay(d) to SharedDay(next.atDay(d), order[(d - 1) % order.size], null, null, null, emptyList(), null) }
        c.setupDrafts.save(LinkStart.draft(SharedSchedule("3병동 김간호", types, days, emptyList(), emptySet()), today)!!)
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c)
        content { com.bamnat.app.ui.setup.SetupHost(vm) }
        settle()
    }
    @Test fun summaryFoldLight() = shoot("rvg16_summary_fold_light_100", FOLDED, summary())
    @Test fun summaryFoldDarkHuge() = shoot("rvg16_summary_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, summary())
    @Test fun summaryOpenLight() = shoot("rvg16_summary_open_light_100", UNFOLDED, summary())
    @Test fun summaryOpenDarkHuge() = shoot("rvg16_summary_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, summary())
    @Test fun summaryFoldLightScrolled() = shoot("rvg16_summary_fold_light_100_below", FOLDED) {
        summary().invoke(this)
        reveal("어떻게 일하세요?")
    }

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
