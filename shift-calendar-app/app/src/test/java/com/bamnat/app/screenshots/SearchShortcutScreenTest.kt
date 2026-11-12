package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.settingsPage
import com.bamnat.app.ui.settings.titleFor
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.Place
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVO-09] (PC-82, 통합 2차 ac6c21ce 7절 Q3: 설정 찾기에서 '수면 기록'을 치면 기능 줄의 '바로 가기'가 두 번 눌러도 안 열렸어요): 설정 찾기의
 * 기능 줄마다, 사람이 하듯 이름을 치고 그 줄의 '바로 가기'를 눌러 실제 화면이 열리는지 세어요. 여섯 유형 × 그 유형의 기능이 가는 곳(같은 곳은 한 번).
 * 열렸다는 것은 [FeatureWayScreenTest]와 같아요: 탭이면 아래 막대에서 그 탭이 골라지고, 설정이면 그 쪽 제목이, 다른 화면이면 뒤로·닫기가 보이고,
 * 어느 쪽이든 설정 찾기 칸이 더는 보이지 않아요(아무 일도 없으면 찾기 칸과 결과가 그대로 남아요).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchShortcutScreenTest : ScreenshotBase() {
    @Test fun shift() = walk(WorkStyle.SHIFT)
    @Test fun dayWork() = walk(WorkStyle.DAY)
    @Test fun partTime() = walk(WorkStyle.HOURLY)
    @Test fun fieldWork() = walk(WorkStyle.FIELD)
    @Test fun shop() = walk(WorkStyle.BUSINESS)
    @Test fun invest() = walk(WorkStyle.INVEST)

    /**
     * The case the PC found, as the PC had it: the name typed, the keyboard still up. The feature rows are the foot of the results, and
     * the app draws under the keyboard, so 바로 가기 must scroll up above it; a tap under the keyboard goes to the keyboard.
     */
    @Test fun sleepRecordOpensWithKeyboardUp() {
        val wrong = mutableListOf<String>()
        shoot("9321_search_shortcut_sleep_keyboard", PHONE, fresh(WorkStyle.SHIFT) {
            tab(SETTINGS_TAB, wide = false)
            typeInto("설정 찾기", "수면 기록")
            val top = keyboard(KEYBOARD_DP)
            // The results end above the keyboard: their last line can be scrolled up over it, so nothing is left only under it.
            repeat(8) { scrollUp(350, 0.5f) }
            val end = nodes().filter { n -> n.texts().any { it == "찾는 게 아니면 도움말에서 더 찾아봐요" } }.maxOfOrNull { it.boundsInWindow.bottom }
            if (end == null || end > top + 1f) wrong += "설정 찾기 결과의 끝이 키보드 밑에 남아요(끝 $end, 키보드 위 $top)"
            // Back up to the found features, as a person scrolls back to the row they saw.
            repeat(8) { scrollDown(350, 0.5f) }
            val button = shortcutOf("수면 기록", top)
            if (button == null) wrong += "sleep.log: 키보드가 올라온 채로는 바로 가기를 키보드 위로 올릴 수 없어요"
            else {
                tapAt(button)
                if (nodes(merged = false).flatMap { it.texts() }.none { it == "뒤로" }) wrong += "sleep.log: 바로 가기를 눌러도 수면 기록이 안 열려요"
            }
        })
        assertEquals(emptyList<String>(), wrong)
    }

    private fun walk(style: WorkStyle) {
        val wrong = mutableListOf<String>()
        val missed = mutableListOf<String>()
        var pressed = 0
        shoot("9320_search_shortcut_${style.name.lowercase()}", PHONE, fresh(style) {
            // One feature for each way in, tried in the first kind of work that has it: the tap does the same for every feature that shares it.
            val earlier = STYLES.takeWhile { it != style }
            val features = FeatureRegistry.shown.filter { f -> f.fits(style) && f.route.place !in SKIP && earlier.none { sharesRoute(f, it) } }
                .distinctBy { it.route }
            for (f in features) {
                when (val result = check(f, style)) {
                    MISSED -> missed += f.id
                    null -> pressed++
                    else -> { pressed++; wrong += result }
                }
            }
        })
        println("[RVO-09] $style: 바로 가기 $pressed 곳을 눌러 봄, 이름으로 세 줄 안에 안 나온 기능 ${missed.size}개 $missed")
        assertTrue("$style: 바로 가기를 하나도 누르지 못했어요", pressed > 0)
        assertEquals("$style", emptyList<String>(), wrong)
    }

    /** Whether a feature of [other] goes the same way as [f]. */
    private fun sharesRoute(f: com.bamnat.core.guide.AppFeature, other: WorkStyle): Boolean =
        FeatureRegistry.shown.any { it.route == f.route && it.fits(other) && it.route.place !in SKIP }

    /** Types [f]'s name into 설정 찾기, taps 바로 가기 on its row and says what is wrong, or null when its place opened. */
    private fun ScreenshotBase.Screen.check(f: com.bamnat.core.guide.AppFeature, style: WorkStyle): String? {
        toSearch()
        typeInto("설정 찾기", f.name)
        val button = shortcutOf(f.name) ?: return MISSED // not among the three features the search shows for its own name
        tapAt(button)
        val words = nodes(merged = false).flatMap { it.texts() }
        val searchLeft = nodes().any { n -> n.config.contains(SemanticsActions.SetText) && n.texts().any { it == "설정 찾기" } }
        val place = f.route.place
        val home = FeatureRegistry.homeOf(place, style)
        return when {
            place in TAB_PLACES -> {
                shownDialogs().forEach { it.dismiss() }
                settle()
                // A tool of the tab can take the whole screen (화면 꾸미기 hides the bar): it opened when the search is gone.
                barChoice(nodes()).let { if (it != home && !(it == null && !searchLeft)) "${f.id}($place): 아래 막대 '$it' ≠ '$home'" else null }
            }
            searchLeft && shownDialogs().isEmpty() -> "${f.id}($place): 바로 가기를 눌러도 설정 찾기 그대로예요"
            place.isSettings -> place.settingsPage()?.titleFor(style)?.let { t -> if (words.none { it == t }) "${f.id}($place): '$t' 쪽이 안 보여요" else null }
            else -> if (words.none { it == "뒤로" || it == "닫기" }) "${f.id}($place): 열린 화면이 없어요" else null
        }
    }

    /**
     * Back to 설정 찾기 the way a person comes back: a sheet is put away, a screen opened over settings is left by its 뒤로·설정으로,
     * and from a tab 나 › 설정 opens it again.
     */
    private fun ScreenshotBase.Screen.toSearch() {
        repeat(5) {
            shownDialogs().forEach { it.dismiss() }
            settle()
            if (nodes().any { n -> n.config.contains(SemanticsActions.SetText) && n.texts().any { it == "설정 찾기" } }) return
            val words = nodes(merged = false).flatMap { it.texts() }
            if (words.any { it == "뒤로" || it == "닫기" || it == "설정으로" }) back() else tab(SETTINGS_TAB, wide = false)
        }
        error("설정 찾기로 돌아오지 못했어요")
    }

    /**
     * The 바로 가기 of the feature row titled [name] (its title is plain text, a found setting's is a button), scrolled up into view until it is
     * whole above the bottom bar and [keyboardTop]. It is the row's own: no other feature's title stands between the two.
     */
    private fun ScreenshotBase.Screen.shortcutOf(name: String, keyboardTop: Float = Float.MAX_VALUE): SemanticsNode? {
        repeat(8) {
            val all = nodes().filter { it.boundsInWindow.height > 1f }
            val title = all.firstOrNull { n -> n.texts().any { it == name } && !n.config.contains(SemanticsActions.OnClick) }
            if (title != null) {
                val top = title.boundsInWindow.top
                val below = all.filter { n -> n.texts().any { it == "바로 가기" } && n.boundsInWindow.top > top }.minByOrNull { it.boundsInWindow.top }
                val between = below != null && all.any { n -> n !== title && n.boundsInWindow.top > top && n.boundsInWindow.top < below.boundsInWindow.top &&
                    !n.config.contains(SemanticsActions.OnClick) && n.texts().any { it in NAMES } }
                if (below != null && !between && below.boundsInWindow.bottom < minOf(screenBottom(all), keyboardTop)) return below
            }
            scrollUp(250, 0.5f)
        }
        return null
    }

    /** The top of the bottom bar, or the window's foot. */
    private fun screenBottom(all: List<SemanticsNode>): Float =
        all.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }.minOfOrNull { it.boundsInWindow.top } ?: Float.MAX_VALUE

    private fun ScreenshotBase.Screen.tapAt(node: SemanticsNode) {
        val d = app.resources.displayMetrics.density
        val c = node.boundsInWindow.center
        tap(c.x / d, c.y / d)
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
        /** Places opened as a tab of the bottom bar (as in [FeatureWayScreenTest]). */
        val TAB_PLACES = setOf(Place.CALENDAR, Place.TODAY, Place.MONEY, Place.MEMOS, Place.STATS, Place.PLAZA, Place.SHARE, Place.RECEIVE)

        /** 문의하기 opens its sheet over whatever is on screen: not a place of its own. */
        val SKIP = setOf(Place.INQUIRY)

        /** The order the kinds of work are walked in; a way in is tried in the first that has it. */
        val STYLES = listOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY, WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST)

        /** Every feature's name, to tell one row from the next. */
        val NAMES: Set<String> by lazy { FeatureRegistry.all.map { it.name }.toSet() }

        /** A phone keyboard with its suggestion row. */
        const val KEYBOARD_DP = 300

        /** [check]'s answer when the feature's row is not among the results for its own name. */
        const val MISSED = "missed"
    }
}
