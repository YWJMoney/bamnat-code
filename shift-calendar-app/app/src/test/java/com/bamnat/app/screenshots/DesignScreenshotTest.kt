package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.components.EmptyState
import com.bamnat.app.ui.components.HeroNumber
import com.bamnat.app.ui.components.Kani
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.StoryEvent
import com.bamnat.app.ui.components.StoryLine
import com.bamnat.app.ui.components.WeekStrip
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.design.StoryKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [FLOW-26][DSG-01][DSG-02][DSG-03][DSG-05][DSG-06][DSG-09] 갈래0's design parts, numbers 2700~2709: 칸이's seven faces
 * light and dark, the one-number head with small units and meaning colours, the week strip, a day's story with a night
 * shift across midnight, and an empty screen. Each is checked for what a screen reader hears, not only drawn.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DesignScreenshotTest : ScreenshotBase() {
    @Composable private fun Page(dark: Boolean = false, body: @Composable () -> Unit) {
        ShotTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) { body() }
        }
    }

    @Composable private fun AllFaces(dark: Boolean) {
        KaniMood.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { m -> Column { Kani(m, night = dark, size = 72.dp, speak = true); Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface) } }
            }
        }
    }

    @Test fun kaniFaces() = shoot("2700_design_kani_faces", FOLDED) {
        content { Page { AllFaces(false) } }
        settle()
        check(appears("칸이, 해냈어요")) { "칸이 has no spoken name" }
    }

    @Test fun kaniFacesDark() = shoot("2701_design_kani_faces_dark", FOLDED) {
        content { Page(dark = true) { AllFaces(true) } }
        settle()
    }

    @Test fun heroNumbersAndTones() = shoot("2702_design_hero_numbers", FOLDED) {
        content {
            Page {
                HeroNumber("7시간 10분", caption = "지난주보다 40분 더 잤어요", tone = Tone.Good)
                HeroNumber("152,000", unit = "원", caption = "이번 달 예상 수당")
                HeroNumber("52", unit = "시간", caption = "이번 주 근무, 주 52시간에 닿았어요", tone = Tone.Caution)
                HeroNumber("0", unit = "개", caption = "울릴 알람이 없어요", tone = Tone.Danger)
            }
        }
        settle()
        check(appears("7시간 10분, 지난주보다 40분 더 잤어요")) { "the head is not read as one sentence" }
    }

    @Test fun weekStripAndStory() = shoot("2703_design_week_story", FOLDED) {
        val day = LocalDate.of(2026, 10, 14)
        fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)
        content {
            Page {
                WeekStrip((0L..6L).map { day.minusDays(2).plusDays(it) }, selected = day, today = day,
                    marks = mapOf(day.minusDays(1) to Tone.Good, day to Tone.Caution), onPick = {})
                StoryLine(day, listOf(
                    StoryEvent(at(14, 0), at(14, 7), StoryKind.Shift, "야간", "어제 19:00부터 이어진 근무"),
                    StoryEvent(at(14, 8), at(14, 14), StoryKind.Sleep, "잠 6시간", tone = "good"),
                    StoryEvent(at(14, 12, 30), null, StoryKind.Money, "점심 9,000원"),
                    StoryEvent(at(14, 17, 30), null, StoryKind.Alarm, "출근 알람"),
                    StoryEvent(at(14, 19), at(15, 7), StoryKind.Shift, "야간"),
                ).map { if (it.start.dayOfMonth == 14 && it.start.hour == 0) it.copy(start = at(13, 19)) else it },
                    now = at(14, 10, 30))
            }
        }
        settle()
        check(appears("지금")) { "no 지금 mark" }
        check(appears("19:00–내일 07:00")) { "the night shift does not say it runs into tomorrow" }
        check(appears("어제 19:00–07:00")) { "the night shift does not say it began yesterday" }
    }

    @Test fun emptyState() = shoot("2704_design_empty_state", FOLDED) {
        content {
            Page {
                EmptyState("아직 잠 기록이 없어요", "잠들기 전에 ‘잠들었어요’를 누르면 아침에 여기서 잔 시간과 근무 사이 쉼을 보여 줘요",
                    action = "오늘 밤 기록하기" to {})
            }
        }
        settle()
        check(appears("아직 잠 기록이 없어요")) { "the empty screen says nothing" }
        // 2.0.0 [RVP-32] (검토 UIX-55) 칸이 on an empty screen is a picture for the eye: a screen reader does not read it again.
        check(!appears("칸이, 여기예요")) { "칸이 is read on every empty screen" }
    }

    /**
     * 2.0.0 [RVP-59] (검토 14-5) 말랑 칸이 from 24 to 120 on a light page, a dark page and the warm night page, with a shift colour
     * on the headband: the face and the band must still read at 24, and the rings must not sink into the dark.
     */
    @Composable private fun Sizes(night: Boolean) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(24, 40, 56, 72, 96, 120).forEach { Kani(KaniMood.Basic, night = night, size = it.dp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Kani(KaniMood.NightShift, night = night, band = androidx.compose.ui.graphics.Color(0xFF6C5CE7), size = 72.dp, speak = true)
            Kani(KaniMood.Payday, night = night, size = 72.dp)
            Kani(KaniMood.Hello, night = night, size = 72.dp, date = 12, tomorrow = androidx.compose.ui.graphics.Color(0xFF2F80ED))
        }
    }

    @Test fun kaniSizes() = shoot("2705_design_kani_sizes", FOLDED) {
        content {
            Column {
                Page { Sizes(false) }
            }
        }
        settle()
        check(appears("칸이, 야간 근무")) { "the new 야간 근무 face has no spoken name when it is the message" }
    }

    @Test fun kaniSizesDark() = shoot("2706_design_kani_sizes_dark", FOLDED) {
        content { Page(dark = true) { Sizes(true) } }
        settle()
    }

    /**
     * 2.0.0 [RVP-01] (총괄 #445 검토, 조건 12) The nudge card at the folded phone's width with the letters at 200%: a long line, its
     * button and its X. The words are never cut ('…'); with big letters the button goes under them when beside it they would take more than two lines (갈래 P 3/3). Light and dark.
     */
    @Composable private fun Nudges() {
        com.bamnat.app.ui.components.NudgeCard("잠금 화면에 알람 화면이 안 떠요. 알람이 울려도 모를 수 있어요", tone = Tone.Caution,
            action = "켜러 가기", onClose = {})
        com.bamnat.app.ui.components.NudgeCard("이번 달 근무표가 아직 비어 있어요", detail = "근무표 사진을 찍으면 한 번에 채워요",
            action = "사진으로 채우기", onClose = {})
        com.bamnat.app.ui.components.NudgeCard("백업했어요", tone = Tone.Good, onClose = {})
        // 갈래 J가 찾음: 단추가 없는 카드도 큰 글자에서 둘째 줄을 자르지 않아요.
        com.bamnat.app.ui.components.NudgeCard("급여 설정 파일을 읽지 못했어요", detail = "지난번에 저장한 값으로 계산했어요. 설정을 한 번 열어 저장하면 다시 맞춰져요",
            tone = Tone.Caution, onClose = {})
    }

    @Test fun nudgeCardsBigTextFolded() = shoot("2708_design_nudge_200", FOLDED, 2f) {
        content { Page { Nudges() } }
        settle()
        check(appears("켜러 가기", exact = true)) { "the button went" }
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        check(cuts.isEmpty()) { "words cut at 200%: $cuts" }
    }

    /**
     * 검토 HQ-73 (총괄 #492 사진 06, 조건 1): at the folded width and 200% the band '잠금 화면에 알람 화면이 안 떠요 · 허용' was three lines
     * (two of words, '허용' on a third). Now '허용' stands under the X, beside the two lines: the band is two lines high.
     */
    @Test fun bandButtonBesideTheWordsAt200() = shoot("2722_design_band_200", FOLDED, 2f) {
        content { Page { com.bamnat.app.ui.components.NudgeCard("잠금 화면에 알람 화면이 안 떠요", tone = Tone.Caution, action = "허용", onClose = {}) } }
        settle()
        val all = nodes(merged = false)
        fun box(t: String) = all.first { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text == t } == true
        }.boundsInWindow
        val words = box("잠금 화면에 알람 화면이 안 떠요")
        val button = box("허용")
        check(button.top < words.bottom) { "'허용' went under the words (three lines): $button, words $words" }
        check(button.left > words.right - 1f) { "'허용' is beside the words: $button, words $words" }
    }

    @Test fun nudgeCardsBigTextFoldedDark() = shoot("2709_design_nudge_200_dark", FOLDED, 2f) {
        content { Page(dark = true) { Nudges() } }
        settle()
        val m = app.resources.displayMetrics
        check(LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels).isEmpty()) { "words cut at 200% (dark)" }
    }

    @Test fun nudgeCardsFolded() = shoot("2710_design_nudge_100", FOLDED) {
        content { Page { Nudges() } }
        settle()
        val m = app.resources.displayMetrics
        check(LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels).isEmpty()) { "words cut at 100%" }
    }

    @Test fun kaniSizesNight() = shoot("2707_design_kani_sizes_night", FOLDED) {
        content {
            ShotTheme(themeMode = ThemeMode.DARK, night = true) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) { Sizes(true) }
            }
        }
        settle()
    }
}
