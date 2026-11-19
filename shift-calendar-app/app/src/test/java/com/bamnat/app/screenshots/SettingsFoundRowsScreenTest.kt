package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.app.ui.guide.settingsPage
import com.bamnat.app.ui.settings.SettingPlace
import com.bamnat.app.ui.settings.SettingsIndex
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVO-10] (검토 SET-03·U11a-04·T11a-01~03: 설정 찾기가 그 유형 쪽에 없는 줄을 찾아 줘요): 여섯 유형마다, 설정 찾기가 찾아 주는
 * 줄(한 쪽의 한 줄)이 그 유형의 그 쪽에 실제로 그려지는지 세어요. 쪽을 열어 끝까지 내리며 보이는 글을 모으고, 거기 없는 줄은 설정 찾기에서
 * 고른 것처럼 그 줄을 가리켜 다시 열어 봐요(접힌 묶음 안의 줄). 그래도 없으면 찾기가 없는 줄을 찾아 준 것이에요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsFoundRowsScreenTest : ScreenshotBase() {
    @Test fun shift() = walk(WorkStyle.SHIFT)
    @Test fun dayWork() = walk(WorkStyle.DAY)
    @Test fun partTime() = walk(WorkStyle.HOURLY)
    @Test fun fieldWork() = walk(WorkStyle.FIELD)
    @Test fun shop() = walk(WorkStyle.BUSINESS)
    @Test fun invest() = walk(WorkStyle.INVEST)

    private fun walk(style: WorkStyle) {
        val missing = mutableListOf<String>()
        var seen = 0
        shoot("9330_settings_found_rows_${style.name.lowercase()}", PHONE, fresh(style) {
            // As the hub builds it for this kind of work (회사 rows only where the work has a company).
            val company = style == WorkStyle.SHIFT || style == WorkStyle.DAY
            val rows = SettingsIndex.entries(board = false, share = true, company = company, style = style, sleepAuto = false, sleepSound = false)
                .mapNotNull { e -> (e.place as? SettingPlace.Row)?.let { Triple(it.page, e.title, it.row) } }
            for ((page, group) in rows.groupBy { it.first }) {
                val place = Place.entries.first { it.settingsPage() == page }
                val drawn = pageWords(place)
                for ((_, title, row) in group) {
                    seen++
                    if (drawnHere(drawn, title, row)) continue
                    // A row inside a folded group: opened the way a search result opens it.
                    FeatureLinks.open(FeatureRoute(place, row))
                    settle()
                    val again = words()
                    if (!drawnHere(again, title, row)) missing += "${page.title} › $title"
                }
            }
        })
        println("[RVO-10] $style: 찾기가 찾는 줄 $seen 개를 그 쪽에서 봄, 없는 줄 ${missing.size}개 $missing")
        assertEquals("$style: 설정 찾기가 찾아 주는데 그 쪽에 없는 줄", emptyList<String>(), missing)
    }

    /** The row shows under its name or the one search gives it; 앱 버전 is the version itself ('밤낮 2.0.0 (54)'). */
    private fun drawnHere(drawn: Set<String>, title: String, row: String): Boolean =
        title in drawn || row in drawn || (row == "버전" && drawn.any { it.startsWith("밤낮 ") && it.any(Char::isDigit) })

    /** Every word the page draws, from its top to its end. */
    private fun ScreenshotBase.Screen.pageWords(place: Place): Set<String> {
        FeatureLinks.open(FeatureRoute(place))
        settle()
        val all = mutableSetOf<String>()
        var still = 0
        repeat(40) {
            val before = all.size
            all += words()
            if (all.size == before) still++ else still = 0
            if (still >= 2) return all
            scrollUp(500, 0.5f)
        }
        return all
    }

    private fun ScreenshotBase.Screen.words(): Set<String> = nodes(merged = false).flatMap { it.texts() }.toSet()

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    @Suppress("unused") private val pages = SettingsPage.entries
}
