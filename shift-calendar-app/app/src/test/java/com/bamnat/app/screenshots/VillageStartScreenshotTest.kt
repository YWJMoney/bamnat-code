package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.hub.HubSection
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [RVP-45] 검토 HQ-67 (디자인 품질 조건 1 '핵심이 먼저 보여요'): 마을의 네 탭(광장·장터·테마·공방)에서 첫 줄이 화면 위 25% 안에서
 * 시작해요. 글자 200%에서 마을 머리(이름 줄 + 탭)가 124dp라 장터의 첫 카드가 31%에서 시작했어요. 탭 줄 아래 첫 글(또는 읽어 주는 이름)의
 * 위쪽이 화면 높이의 몇 %인지 재고, [GOAL]을 넘으면 실패해요. 숫자는 build/reports/design-rules.txt에도 남아요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VillageStartScreenshotTest : ScreenshotBase() {

    /** Where the first line under 마을's tabs starts, as a share of the screen's height. */
    private fun Screen.firstLine(): Float {
        val m = app.resources.displayMetrics
        val labels = HubSection.entries.map { it.label }.toSet()
        fun inside(b: androidx.compose.ui.geometry.Rect) = b.height > 1f && b.left >= 0f && b.right <= m.widthPixels + 1f && b.top >= 0f && b.bottom <= m.heightPixels + 1f
        val all = nodes(merged = false)
        fun words(n: androidx.compose.ui.semantics.SemanticsNode) =
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val tabs = all.filter { inside(it.boundsInWindow) && words(it).any { w -> w in labels } }
        val headBottom = tabs.maxOfOrNull { it.boundsInWindow.bottom } ?: return 1f
        // The bar at the bottom of the screen is not the page's first line.
        val bar = m.heightPixels * 0.85f
        val first = all.filter { n -> val b = n.boundsInWindow
            inside(b) && b.top >= headBottom - 1f && b.top < bar && words(n).any { it.isNotBlank() } }
            .minOfOrNull { it.boundsInWindow.top } ?: return 1f
        return first / m.heightPixels
    }

    private fun measure(shot: String, qualifiers: String, scale: Float) {
        val at = mutableMapOf<HubSection, Int>()
        shoot(shot, qualifiers, scale, style(WorkStyle.SHIFT) {
            tapText("마을", exact = true, scrolls = 0)
            settle()
            for (s in HubSection.entries) {
                tapText(s.label, exact = true, scrolls = 0)
                settle()
                at[s] = Math.round(firstLine() * 100)
            }
            tapText(HubSection.SHARE.label, exact = true, scrolls = 0)
            settle()
        })
        record(shot, at.values.max())
        for ((s, percent) in at) assertTrue("마을 › ${s.label}: 첫 줄이 화면 위 $percent%에서 시작해요(목표 $GOAL%, $shot)", percent <= GOAL)
    }

    private fun record(shot: String, percent: Int) {
        val f = File("build/reports/design-rules.txt").apply { parentFile?.mkdirs() }
        val what = "마을 첫 줄 높이 $shot"
        val old = if (f.exists()) f.readLines().filterNot { it.startsWith("$what:") } else emptyList()
        f.writeText((old + "$what: 지금 $percent% · 목표 $GOAL%").sorted().joinToString("\n") + "\n")
    }

    @Test fun folded200() = measure("rvp_village_start_folded_200", FOLDED, 2f)
    @Test fun phone200() = measure("rvp_village_start_phone_200", PHONE, 2f)
    @Test fun folded100() = measure("rvp_village_start_folded_100", FOLDED, 1f)
    @Test fun open200() = measure("rvp_village_start_open_200", UNFOLDED, 2f)

    companion object {
        const val GOAL = 25
    }
}
