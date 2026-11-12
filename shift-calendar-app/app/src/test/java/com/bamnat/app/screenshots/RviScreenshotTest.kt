package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 I (검토 03 알람·위젯·수면·워치, 07a 메모·할 일): the screens it touches, each in the eight looks the design
 * quality table asks for (밝은·어두운 × 글자 100%·200% × 접은 폭·편 폭). Only taps and seeded data, so the same file
 * shoots the screens before the change too (the PR's 앞·뒤 사진).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RviScreenshotTest : ScreenshotBase() {
    private class Look(val tag: String, val qualifiers: String, val font: Float, val wide: Boolean)

    private val looks = listOf(
        Look("l100f", FOLDED, 1f, false), Look("l200f", FOLDED, 2f, false),
        Look("d100f", FOLDED_NIGHT, 1f, false), Look("d200f", FOLDED_NIGHT, 2f, false),
        Look("l100u", UNFOLDED, 1f, true), Look("l200u", UNFOLDED, 2f, true),
        Look("d100u", UNFOLDED_NIGHT, 1f, true), Look("d200u", UNFOLDED_NIGHT, 2f, true),
    )

    private fun eight(name: String, actions: Screen.(wide: Boolean) -> Unit) {
        for (l in looks) shoot("rvi_${name}_${l.tag}", l.qualifiers, l.font) { actions(l.wide) }
    }

    /** The first of [words] the screen shows, tapped: the same button kept other words before the change. */
    private fun Screen.tapFirst(vararg words: String) {
        val word = words.firstOrNull { shows(it) } ?: words.first()
        tapText(word, prefix = true)
    }

    /**
     * Wide, the card sits under the 새 메모 button at the bottom of the list and a drag can fling past it: it is found
     * as a person would, by the search box, which brings it to the top of the list.
     */
    private fun Screen.openCheckup(wide: Boolean) {
        typeInto("제목, 내용, #태그 검색", "건강검진")
        tapText("건강검진 오전 9시", across = if (wide) 0.3f else 0.5f)
    }

    /**
     * 디자인 품질 1: the writing (the editor's first text box) starts in the top quarter of the screen, chips or not. Unfolded the
     * editor is the right pane, beside the list's search box.
     */
    private fun Screen.writingStartsHigh(wide: Boolean) {
        val width = app.resources.displayMetrics.widthPixels
        val top = nodes().filter { it.config.contains(SemanticsProperties.EditableText) && (!wide || it.boundsInWindow.left > width * 0.4f) }
            .minOf { it.boundsInWindow.top }
        val height = app.resources.displayMetrics.heightPixels
        assertTrue("the writing starts at ${(100 * top / height).toInt()}% of the screen", top < height * 0.25f)
    }

    // [RVI-35] 체크리스트 줄: 끌기 손잡이, 48dp 지우기.
    @Test fun memoChecklist() = eight("memo_checklist") { wide -> tab(2, wide); tapText("준비물") }

    // [RVI-30][RVI-49][RVI-52] 일정 칩 이름, 18dp X 없음.
    @Test fun memoEditor() = eight("memo_editor") { wide -> tab(2, wide); openCheckup(wide); writingStartsHigh(wide) }

    // [RVI-31][RVI-52] 알림 시각 창: 스크롤, '알림 빼기'.
    @Test fun memoScheduleWindow() = eight("memo_schedule") { wide ->
        tab(2, wide); openCheckup(wide); tapFirst("알림 넣기, ", "알람 맞추기", "일정으로 넣기", "알림 넣기")
    }

    // [RVI-48] 카드 ⋮ 48dp, [RVI-49] 다음 차례 줄.
    @Test fun memoCards() = eight("memo_cards") { wide -> tab(2, wide); tapText("일반 메모", prefix = true) }

    // [RVI-38] 설정 첫 화면과 설정 › 알람이 같은 점검.
    @Test fun settingsTop() = eight("settings_top") { wide -> tab(SETTINGS_TAB, wide) }
    @Test fun settingsAlarm() = eight("settings_alarm") { wide -> settingsPage("알람", wide) }

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
