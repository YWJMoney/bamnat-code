package com.bamnat.app.screenshots

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.Fmt
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 6 검토 PC-199 (통합 5차 PC a17-86: 탁상 자세(가로로 반쯤 접음) 달력에서 위쪽에 3주만 보이고 오늘이 든 주가 접힌 선에 반쯤
 * 걸렸어요): half open across, the week of today is whole above the hinge, the month moved up by whole weeks when it has to. A late day of
 * the month (10월 29일, the fifth week), on a Fold on its side and on a Flip, light and dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP6TabletopTest : ScreenshotBase() {
    private val late = java.time.ZonedDateTime.of(2026, 10, 29, 10, 30, 0, 0, SEOUL)

    @Before fun lateInTheMonth() = Today.fix(late)
    @After fun clockBack() = Today.fix(SHOT_TIME)

    private fun Screen.todayAboveTheHinge() {
        halfOpen()
        settle()
        val title = Fmt.dayTitle(late.toLocalDate())
        val all = nodes(merged = true)
        val cell = all.firstOrNull { n ->
            (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
                .any { it.startsWith(title) }
        } ?: error("오늘 칸이 없어요: $title")
        // The hinge across the middle of the window (the test fold's), and the cell where it is laid out (not clipped).
        val hinge = all.maxOf { it.boundsInWindow.bottom } / 2f
        val top = cell.layoutInfo.coordinates.positionInWindow().y
        val bottom = top + cell.layoutInfo.coordinates.size.height
        check(top >= 0f && bottom <= hinge + 2f) { "오늘이 든 주가 접힌 선 위에 다 보이지 않아요: 칸 $top~$bottom, 접힌 선 $hinge" }
    }

    @Test fun unfoldedLight() = shoot("rvp6_tabletop_unfolded_light", "w823dp-h707dp-land-420dpi") { todayAboveTheHinge() }

    @Test fun unfoldedDark() = shoot("rvp6_tabletop_unfolded_dark", "w823dp-h707dp-land-night-420dpi") { todayAboveTheHinge() }

    @Test fun flipLight() = shoot("rvp6_tabletop_flip_light", "w373dp-h839dp-port-420dpi") { todayAboveTheHinge() }
}
