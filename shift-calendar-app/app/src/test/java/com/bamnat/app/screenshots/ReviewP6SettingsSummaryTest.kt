package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.settings.SettingsPage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 6 검토 HQ-130 (설정 줄의 '한 줄 요약 + ⓘ'가 글자 200% 접은 폭에서 여덟 글자쯤에서 잘려 '다시 시작 10분 …', '근무 날에 어느 …'만
 * 남았어요, 조건 4·12): every page of 설정, opened a second time (the explanations in their folded form), at 글자 200% on the folded
 * width, light and dark. Each folded summary shows at least [MIN_SEEN] letters (or all of it): with big letters it takes two lines.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP6SettingsSummaryTest : ScreenshotBase() {
    private fun SemanticsNode.underInfo(): Boolean = generateSequence(parent) { it.parent }.any { p ->
        p.config.getOrNull(SemanticsActions.OnClick)?.label == "설명 펼치기"
    }

    /** The folded summaries on screen, with how many of their letters show. */
    private fun Screen.summaries(): List<Pair<String, Int>> = nodes(merged = false).filter { it.underInfo() }.mapNotNull { n ->
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
        val layouts = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.firstOrNull() ?: return@mapNotNull null
        val last = layout.lineCount - 1
        // The letters before the '…' (an ellipsized line ends where the '…' starts).
        val shown = if (layout.isLineEllipsized(last)) layout.getLineEnd(last, visibleEnd = true) else text.length
        text to shown.coerceAtMost(text.length)
    }

    private fun page(p: SettingsPage, q: String, look: String) {
        // The first visit says each explanation in full and counts it seen; the second folds them.
        com.bamnat.app.ui.components.ExplainSeen.reset(app)
        shoot("rvp6_settings_${p.name.lowercase()}_first_$look", q, 2f) { settingsPage(p.title) }
        shoot("rvp6_settings_${p.name.lowercase()}_$look", q, 2f) {
            settingsPage(p.title)
            val found = summaries()
            println("HQ-130 ${p.title} $look: " + found.joinToString(" | ") { (t, n) -> "$n/${t.length} $t" })
            val cut = found.filter { (t, n) -> n < minOf(t.length, MIN_SEEN) }
            check(cut.isEmpty()) { "${p.title}: 요약이 ${MIN_SEEN}글자 전에 잘려요 $cut" }
            // The picture shows the first folded summary of the page (the top of a page is often links).
            found.firstOrNull()?.let { (t, _) -> reveal(t.take(8)) }
        }
        // A page with many: its last one too.
        if (p == SettingsPage.NOTICES) shoot("rvp6_settings_${p.name.lowercase()}_end_$look", q, 2f) {
            settingsPage(p.title)
            summaries().lastOrNull()?.let { (t, _) -> reveal(t.take(8)) }
        }
    }

    @Test fun everyPageLight() = SettingsPage.entries.forEach { page(it, FOLDED, "folded_light_200") }

    @Test fun everyPageDark() = SettingsPage.entries.forEach { page(it, FOLDED.replace("-port-", "-port-night-"), "folded_dark_200") }

    private companion object {
        const val MIN_SEEN = 12
    }
}
