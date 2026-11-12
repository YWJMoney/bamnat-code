package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.app.ui.guide.settingsPage
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 뿌리 고침 13번(갈래 H가 가진 시험, 도약 39번 '기능 찾기가 실제 화면과 다른 곳 0곳'): 여섯 유형마다 그 유형의 기능이 가는 곳을
 * 하나씩 실제로 열어 봐요. 탭이면 아래 막대에서 그 탭(그 유형의 이름)이 골라져 있고, 설정이면 그 쪽 제목이 보이고, 다른 화면이면 뒤로 가는
 * 단추가 있는 화면이 열려야 해요. '어디' 글의 첫 마디가 이곳과 같은지는 core `TypeWordsTest.everyWayStartsWhereItsShortcutLands`가 봐요.
 *
 * 쓰는 법: 새 [Place]를 만들면 AppRoot `openFeature`와 [FeatureRegistry.homeOf]에 더하고, 탭으로 가는 곳이면 [TAB_PLACES]에도 더해요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FeatureWayScreenTest : ScreenshotBase() {
    @Test fun shift() = walk(WorkStyle.SHIFT)
    @Test fun dayWork() = walk(WorkStyle.DAY)
    @Test fun partTime() = walk(WorkStyle.HOURLY)
    @Test fun fieldWork() = walk(WorkStyle.FIELD)
    @Test fun shop() = walk(WorkStyle.BUSINESS)
    @Test fun invest() = walk(WorkStyle.INVEST)

    private fun walk(style: WorkStyle) {
        val wrong = mutableListOf<String>()
        shoot("9310_featureway_${style.name.lowercase()}", PHONE, fresh(style) {
            val places = FeatureRegistry.of(style).map { it.route.place }.distinct() - SKIP
            for (place in places) {
                // From a tab every time (the last place may have pushed a screen or opened a sheet).
                go(Place.MEMOS)
                go(place)
                val home = FeatureRegistry.homeOf(place, style)
                val words = nodes(merged = false).flatMap { it.texts() }
                when {
                    place in TAB_PLACES -> {
                        // A sheet the place opened (오늘, 공유) is its own window over the tab: put away to read the bar.
                        shownDialogs().forEach { it.dismiss() }
                        settle()
                        barChoice(nodes()).let { if (it != home) wrong += "$place: 아래 막대 '$it' ≠ '$home'" }
                    }
                    place.isSettings -> place.settingsPage()?.titleFor(style)?.let { t -> if (words.none { it == t }) wrong += "$place: '$t' 쪽이 안 보여요" }
                    else -> if (words.none { it == "뒤로" || it == "닫기" }) wrong += "$place: 열린 화면이 없어요"
                }
            }
        })
        assertEquals("$style", emptyList<String>(), wrong)
    }

    private fun go(place: Place) {
        FeatureLinks.open(FeatureRoute(place))
        settle()
    }

    /** The name of the chosen tab of the bottom bar: the lowest row of tabs. */
    private fun barChoice(nodes: List<SemanticsNode>): String? {
        val tabs = nodes.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab && it.config.getOrNull(SemanticsProperties.Selected) != null }
        if (tabs.isEmpty()) return null
        val low = tabs.maxOf { it.boundsInWindow.bottom }
        val chosen = tabs.filter { it.boundsInWindow.bottom >= low - 1f }.firstOrNull { it.config.getOrNull(SemanticsProperties.Selected) == true } ?: return null
        return (chosen.texts() + chosen.children.flatMap { it.texts() }).firstOrNull()
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private companion object {
        /** Places opened as a tab of the bottom bar. */
        val TAB_PLACES = setOf(Place.CALENDAR, Place.TODAY, Place.MONEY, Place.MEMOS, Place.STATS, Place.PLAZA, Place.SHARE, Place.RECEIVE)

        /** 문의하기 opens its sheet over whatever is on screen: not a place of its own. 2.0.0 [RVP-75] The ringing alarm's 모두 끄기 has none. */
        val SKIP = setOf(Place.INQUIRY, Place.NOWHERE)
    }
}
