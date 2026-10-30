package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.MainActivity
import com.bamnat.app.ui.TAB_BACKWARD_ACTION
import com.bamnat.app.ui.TAB_EDIT_ACTION
import com.bamnat.app.ui.TAB_EDIT_HINT_TOP
import com.bamnat.app.ui.TAB_FORWARD_ACTION
import com.bamnat.app.ui.TAB_SAVE_ACTION
import com.bamnat.app.ui.TabWiggle
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.app.ui.settings.TAB_ORDER_ROW
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [NAV-05][NAV-06][NAV-07][GUIDE-09] 묶음 AD's end-to-end checks and screenshots, numbers 1020~1039: a long press on a
 * tab, a drag, a tap elsewhere and the app opening on the tab put first; back ending there; the same from settings
 * and from TalkBack's actions; and the edit drawn on a common phone, a small one, at 200%, dark, without animations,
 * on the unfolded fold's rail and a wide tablet. Each picture is checked for cut text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ADScreenshotTest : ScreenshotBase() {
    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_393_NIGHT = "w393dp-h873dp-port-night-440dpi"
        const val TABLET_LAND = "w1280dp-h800dp-land-240dpi"
        const val MEMO_FIRST = "memos,calendar,money,stats,settings"
    }

    private val c get() = app.container

    /** Tabs gone through in an earlier test of this JVM would be where back goes. */
    @org.junit.Before fun freshHistory() = com.bamnat.app.ui.TabHistory.clear()

    @After fun moving() {
        TabWiggle.frozen = false
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    /** The app, opened as [shoot] opens it, with the activity at hand for checks a picture cannot make. */
    private fun open(qualifiers: String, actions: Screen.(ActivityScenario<MainActivity>) -> Unit) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(1f)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            Screen(scenario).actions(scenario)
        }
    }

    private val barNames = setOf("달력", "가계부", "메모", "근무·급여", "설정")

    /** The app's tabs (not a screen's own tabs), by name. */
    private fun Screen.barTabs() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
        .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.takeIf { it in barNames }?.let { n to it } }

    /** The tab chosen in the bar now, by its name. */
    private fun Screen.tabNow(): String? =
        barTabs().firstOrNull { (n, _) -> n.config.getOrNull(SemanticsProperties.Selected) == true }?.second

    /** The tabs' names in the bar's (or the rail's) order. */
    private fun Screen.barOrder(): List<String> =
        barTabs().sortedBy { (n, _) -> n.boundsInWindow.left * 10_000 + n.boundsInWindow.top }.map { it.second }

    private fun ActivityScenario<MainActivity>.finishing(): Boolean {
        var f = false
        onActivity { f = it.isFinishing }
        return f
    }

    private fun saved(): String = c.settingsRepository.current.tabOrder.joinToString(",")

    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report("ad_$name", cuts)
        assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    /** Runs TalkBack's action [label] on the tab named [tab]. */
    private fun Screen.tabAction(tab: String, label: String) {
        val node = nodes().first { n ->
            n.config.getOrNull(SemanticsProperties.Role) == Role.Tab && n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } == tab
        }
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.firstOrNull { it.label == label } ?: error("‘$tab’ 탭에 ‘$label’ 동작이 없어요: ${actions.map { it.label }}")
        action.action()
        settle()
    }

    private fun memoFirst() = c.settingsRepository.update { it.copy(tabOrder = MEMO_FIRST.split(',')) }

    // ---- 419 [NAV-05] 420 [NAV-06]: 길게 누름 → 끌기 → 밖 누름 → 다시 켜면 그 탭 ----

    @Test fun longPressDragTapOutsideAndTheAppOpensOnMemos() {
        open(PHONE_393) { scenario ->
            assertEquals(listOf("달력", "가계부", "메모", "근무·급여", "설정"), barOrder())
            assertTrue(gone(TAB_EDIT_HINT_TOP))
            // Held still on 메모, then dragged to the front in the same touch.
            dragTab(2, 0, wide = false, hold = 40)
            assertTrue("the edit says what the first tab is", appears(TAB_EDIT_HINT_TOP))
            assertEquals(listOf("메모", "달력", "가계부", "근무·급여", "설정"), barOrder())
            assertEquals("not saved while editing", "", saved())
            // A tab tapped while editing does not open.
            tab(SETTINGS_TAB, wide = false)
            assertEquals("달력", tabNow())
            assertTrue(appears(TAB_EDIT_HINT_TOP))
            // Anywhere else saves and ends the edit, and does nothing else.
            tap(200f, 300f)
            assertTrue(gone(TAB_EDIT_HINT_TOP))
            assertEquals(MEMO_FIRST, saved())
            assertEquals("달력", tabNow())
            // Home moved in this run: back from the calendar goes to 메모, then two backs close the app there.
            back()
            assertEquals("메모", tabNow())
            assertFalse(scenario.finishing())
            back()
            assertFalse("one back on the first tab only warns", scenario.finishing())
            back()
            assertTrue(scenario.finishing())
        }
        // Opened again: on 메모.
        open(PHONE_393) {
            assertEquals("메모", tabNow())
            assertEquals(listOf("메모", "달력", "가계부", "근무·급여", "설정"), barOrder())
        }
    }

    @Test fun backFromTheFirstTabClosesOnTheSecondPressAndOtherTabsGoBackFirst() {
        memoFirst()
        open(PHONE_393) { scenario ->
            assertEquals("메모", tabNow())
            // Bar places now: 메모 달력 돈 근무·급여 설정.
            tab(2, wide = false)
            tab(SETTINGS_TAB, wide = false)
            assertEquals("설정", tabNow())
            back()
            assertEquals("the tab before", "가계부", tabNow())
            back()
            assertEquals("then home, the first tab", "메모", tabNow())
            assertFalse(scenario.finishing())
            back()
            assertFalse(scenario.finishing())
            back()
            assertTrue("two backs on the first tab close the app", scenario.finishing())
        }
    }

    @Test fun backWhileEditingSavesAndStaysInTheApp() = open(PHONE_393) { scenario ->
        dragTab(3, 0, wide = false, hold = 40)
        assertTrue(appears(TAB_EDIT_HINT_TOP))
        back()
        assertTrue(gone(TAB_EDIT_HINT_TOP))
        assertEquals("stats,calendar,money,memos,settings", saved())
        assertFalse(scenario.finishing())
    }

    @Test fun theRailMovesUpAndDownToo() {
        open(UNFOLDED) {
            dragTab(4, 0, wide = true, hold = 40)
            assertTrue(appears(TAB_EDIT_HINT_TOP))
            // The screen beside the rail saves.
            tap(400f, 300f)
            assertEquals("settings,calendar,money,memos,stats", saved())
        }
        open(UNFOLDED) { assertEquals("설정", tabNow()) }
    }

    @Test fun talkBackMovesTheTabsWithActions() {
        open(PHONE_393) {
            tabAction("메모", TAB_FORWARD_ACTION)
            tabAction("메모", TAB_FORWARD_ACTION)
            assertTrue(appears(TAB_EDIT_HINT_TOP))
            assertEquals(listOf("메모", "달력", "가계부", "근무·급여", "설정"), barOrder())
            // What TalkBack reads out after the move, from the hint's live region.
            assertTrue(nodes().any { n -> n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("메모, 1번째") } })
            tabAction("달력", TAB_BACKWARD_ACTION)
            tabAction("가계부", TAB_SAVE_ACTION)
            assertTrue(gone(TAB_EDIT_HINT_TOP))
            assertEquals("memos,money,calendar,stats,settings", saved())
            // Outside the edit a tab offers the long press by name.
            val tabNode = nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
            assertEquals(TAB_EDIT_ACTION, tabNode.config.getOrNull(SemanticsActions.OnLongClick)?.label)
        }
        open(PHONE_393) { assertEquals("메모", tabNow()) }
    }

    // ---- 421 [NAV-07]: 설정 › 화면과 글자 › 첫 화면·메뉴 순서 ----

    @Test fun settingsMovesTheTabsAndPutsThemBack() {
        open(PHONE_393) {
            settingsPage(SettingsPage.CALENDAR.title)
            tapText(TAB_ORDER_ROW, exact = true)
            tapText("가계부 위로", exact = true)
            assertEquals("money,calendar,memos,stats,settings", saved())
            // The bar behind the dialog changed at once, and the screen did not move.
            tapText("완료", exact = true)
            assertEquals(listOf("가계부", "달력", "메모", "근무·급여", "설정"), barOrder())
            assertEquals("설정", tabNow())
        }
        open(PHONE_393) {
            assertEquals("가계부", tabNow())
            // Settings is still the last tab.
            settingsPage(SettingsPage.CALENDAR.title)
            tapText(TAB_ORDER_ROW, exact = true)
            tapText("처음 순서로", exact = true)
            assertEquals("", saved())
            tapText("완료", exact = true)
        }
        open(PHONE_393) { assertEquals("달력", tabNow()) }
    }

    @Test fun searchAndHelpFindIt() = open(PHONE_393) {
        val found = com.bamnat.app.ui.settings.SettingsIndex.search("첫 화면").map { it.title }
        assertTrue(found.toString(), TAB_ORDER_ROW in found)
        for (q in listOf("메뉴 순서", "시작 화면", "탭 순서")) {
            assertTrue(q, TAB_ORDER_ROW in com.bamnat.app.ui.settings.SettingsIndex.search(q).map { it.title })
        }
        val help = com.bamnat.app.ui.settings.HelpFaq.search("달력 말고 메모로 시작하고 싶어요").map { it.id }
        assertEquals(help.toString(), "tabOrder", help.first())
        assertTrue(com.bamnat.app.ui.settings.HelpFaq.about("설정 > 달력 화면").any { it.id == "tabOrder" })
        // The answer's button opens the row.
        val t = com.bamnat.app.ui.settings.HelpFaq.topics().first { it.id == "tabOrder" }
        t.action!!.second.run()
        settle()
        assertTrue(appears(TAB_ORDER_ROW))
    }

    // ---- 422: 사진 1020~1039 ----

    private fun Screen.editing(wide: Boolean = false, from: Int = 2, to: Int = 2, name: String) {
        TabWiggle.frozen = true
        // A long press, again once if the first came before the bar was drawn (a busy machine), then the drag.
        dragTab(from, from, wide = wide, hold = 40)
        if (!shows(TAB_EDIT_HINT_TOP)) dragTab(from, from, wide = wide, hold = 40)
        assertTrue(appears(TAB_EDIT_HINT_TOP))
        if (to != from) dragTab(from, to, wide = wide)
        noCuts(name)
    }

    @Test fun edit393() = shoot("1020_ad_edit_393", PHONE_393) { editing(name = "edit_393") }
    @Test fun editDragged393() = shoot("1021_ad_edit_dragged_393", PHONE_393) { editing(from = 2, to = 0, name = "edit_dragged_393") }
    @Test fun edit320() = shoot("1022_ad_edit_320", SMALL_PHONE) { editing(name = "edit_320") }
    @Test fun edit200() = shoot("1023_ad_edit_200", PHONE_393, 2f) { editing(name = "edit_200") }
    @Test fun editDark() = shoot("1024_ad_edit_dark", PHONE_393_NIGHT) { editing(name = "edit_dark") }
    @Test fun editWithoutAnimations() = shoot("1025_ad_edit_still", PHONE_393) {
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        editing(name = "edit_still")
    }
    @Test fun editRailUnfolded() = shoot("1026_ad_edit_rail_unfolded", UNFOLDED) { editing(wide = true, from = 2, to = 0, name = "edit_rail_unfolded") }
    @Test fun editRailTablet() = shoot("1027_ad_edit_rail_tablet", TABLET_LAND) { editing(wide = true, name = "edit_rail_tablet") }
    @Test fun memoFirstOpen() {
        memoFirst()
        shoot("1028_ad_memo_first", PHONE_393) { assertEquals("메모", tabNow()); noCuts("memo_first") }
    }
    @Test fun settingsRow() = shoot("1029_ad_settings_row", PHONE_393) {
        settingsPage(SettingsPage.CALENDAR.title)
        reveal(TAB_ORDER_ROW, exact = true)
        noCuts("settings_row")
    }
    @Test fun settingsDialog() = shoot("1030_ad_settings_dialog", PHONE_393) {
        memoFirst()
        settingsPage(SettingsPage.CALENDAR.title)
        tapText(TAB_ORDER_ROW, exact = true)
        noCuts("settings_dialog")
    }
    @Test fun settingsDialog200() = shoot("1031_ad_settings_dialog_200", SMALL_PHONE, 2f) {
        settingsPage(SettingsPage.CALENDAR.title)
        tapText(TAB_ORDER_ROW, exact = true)
        noCuts("settings_dialog_200")
    }
    @Test fun settingsDialogDark() = shoot("1032_ad_settings_dialog_dark", PHONE_393_NIGHT) {
        settingsPage(SettingsPage.CALENDAR.title)
        tapText(TAB_ORDER_ROW, exact = true)
    }
    @Test fun helpAnswer() = shoot("1033_ad_help_answer", PHONE_393) {
        com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.INQUIRY)
        settle()
        typeInto("기능과 도움말 찾기", "첫 화면")
        tapText("달력 말고 다른 화면으로 시작하고 싶어요")
        assertTrue(appears("첫 화면·메뉴 순서 열기"))
        noCuts("help_answer")
    }
    @Test fun settingsSearch() = shoot("1034_ad_settings_search", PHONE_393) {
        tab(SETTINGS_TAB, wide = false)
        typeInto("설정 찾기", "첫 화면")
        assertTrue(appears(TAB_ORDER_ROW))
        noCuts("settings_search")
    }
}
