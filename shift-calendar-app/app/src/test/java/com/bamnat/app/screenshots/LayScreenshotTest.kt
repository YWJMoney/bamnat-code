package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.decor.DecorTool
import com.bamnat.app.ui.decor.decor
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [LAY-03][LAY-04][LAY-05][LAY-06] 1.36 꾸밈 층's tool in the studio: the board with a headline and a sticker, the layers, the number boxes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LayScreenshotTest : ScreenshotBase() {
    private val start = Decor(listOf(
        DecorItem(DecorKind.EMOJI, "💰", at = Decor.CELL, on = "d25", x = 780, y = 220, w = 22, h = 22),
        DecorItem(DecorKind.TEXT, "이번 달도 힘내기", at = Decor.HEAD, y = 500, w = 180, h = 30, size = 16.0, weight = 700),
        DecorItem(DecorKind.SHAPE, "tape", at = Decor.HEAD, x = 120, y = 300, w = 60, h = 18, rot = -12, fill = 0xFFF6C1CCL),
    ))

    private fun Screen.tool() = content {
        var d by remember { mutableStateOf(start) }
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                Text("10월", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().height(56.dp).decor(Decor.HEAD, layer = d))
                DecorTool(d, listOf("bg.calendar", Decor.HEAD, Decor.WEEK, Decor.CELL), { _, nd -> d = nd })
            }
        }
    }

    /** [LAY-03] The 💰 pinned to the 25th stands on the 25th's cell only. */
    @Test fun theCoinStandsOnThe25th() = shoot("1483_lay_cells", PHONE) {
        content {
            Surface {
                androidx.compose.foundation.layout.Row(Modifier.padding(12.dp)) {
                    listOf(24, 25, 26).forEach { n ->
                        androidx.compose.foundation.layout.Box(Modifier.padding(2.dp).size(52.dp, 64.dp)
                            .border(1.dp, androidx.compose.ui.graphics.Color.Gray)
                            .decor(Decor.CELL, com.bamnat.core.recipe.DecorDay(10, n, 5, null, false, false), start)) { Text("$n") }
                    }
                }
            }
        }
        settle()
        assertTrue(appears("25", exact = true))
    }

    /** [LAY-03] The real month: a 💰 on today's date and a text on the top. */
    @Test fun theMonthWearsItsDecor() {
        val d = today.dayOfMonth
        app.container.settingsRepository.update { it.copy(decor = Decor(listOf(
            DecorItem(DecorKind.EMOJI, "💰", at = Decor.CELL, on = "d$d", x = 780, y = 220, w = 14, h = 14),
            DecorItem(DecorKind.SHAPE, "star5", at = Decor.CELL, on = "d$d", x = 200, y = 800, w = 12, h = 12, over = true),
            DecorItem(DecorKind.EMOJI, "💰", at = Decor.CELL, on = "d${d + 2}", w = 14, h = 14, over = true),
        )).encode()) }
        shoot("1484_lay_month", PHONE) { settle() }
    }

    @Test fun theBoardShowsTheHeadlineAndItsTools() = shoot("1480_lay_board", PHONE) {
        tool()
        settle()
        assertTrue(appears("🧲 자석", exact = true))
        assertTrue(appears("레이어 3", exact = true))
    }

    @Test fun theLayersListEveryPieceInOrder() = shoot("1481_lay_layers", PHONE) {
        tool()
        settle()
        tapText("레이어 3", exact = true)
        settle()
        assertTrue(appears("묶기", exact = true))
        assertTrue(appears("이번 달도 힘내기", exact = false))
    }

    @Test fun advancedShowsNumberBoxes() = shoot("1482_lay_numbers", PHONE) {
        tool()
        settle()
        // [CNV-13] the row of 자석·돋보기·여러 개·고급 보기 may be folded on a narrow phone: 펼치기 shows it all.
        if (!shows("고급 보기", exact = true)) tapText("보기 도구 모두 펼쳐 보기", exact = true)
        tapText("고급 보기", exact = true)
        tapText("레이어 3", exact = true)
        settle()
        tapText("이번 달도 힘내기", exact = false)
        settle()
        tapText("고치기 ›", exact = true)
        settle()
        assertTrue(appears("숫자로 맞추기", exact = true))
    }

    /**
     * [LAY-10][LAY-11] 누락 점검 N12·N8: a photo the phone does not have yet is a grey box (others' until the operator looked at it), and a
     * decoration picks the 규칙 단추 it presses.
     */
    @Test fun aPhotoAndAButton() = shoot("1485_lay_photo_button", PHONE) {
        content {
            var d by remember { mutableStateOf(Decor(listOf(
                DecorItem(DecorKind.PHOTO, "00000000000000ff", at = Decor.HEAD, x = 820, y = 500, w = 64, h = 40, r = 8),
                DecorItem(DecorKind.EMOJI, "🔔", at = Decor.HEAD, x = 120, y = 500, w = 28, h = 28, tap = "alarm1"),
            ))) }
            Surface {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("10월", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().height(56.dp).decor(Decor.HEAD, layer = d))
                    DecorTool(d, listOf(Decor.HEAD), { _, nd -> d = nd }, buttons = listOf("alarm1" to "⏰ 알람 켜기", "rest" to "🛌 쉬는 날"))
                }
            }
        }
        settle()
        tapText("레이어 2", exact = true)
        settle()
        tapText("사진", exact = true)
        settle()
        tapText("고치기 ›", exact = true)
        settle()
        assertTrue(appears("모서리", exact = true))
        // 누르면: the picked one presses ⏰ 알람 켜기 (scrolled to, then the shot shows the chips).
        tapText("⏰ 알람 켜기", exact = true, scrolls = 20)
        settle()
        assertTrue(appears("그림만", exact = true))
    }

    /** [LAY-12] 누락 점검 N2: the studio's widget example as the 달 위젯, its cells painted by the 위젯 칸 part with a widget sticker on. */
    @Test fun theWidgetExampleHasThreeKinds() = shoot("1486_lay_widget_kinds", PHONE) {
        content {
            Surface {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.bamnat.app.ui.parts.LocalBlueprint provides com.bamnat.core.recipe.Blueprint.decode(
                        """[{"id":"widget.cell","on":"sun","ink":4293284096},{"id":"widget.head","weight":800,"size":16}]"""),
                    com.bamnat.app.ui.decor.LocalDecor provides Decor(listOf(DecorItem(DecorKind.EMOJI, "🌙", at = Decor.WIDGET, x = 920, y = 80, w = 28, h = 28)))) {
                    com.bamnat.app.ui.studio.WidgetSample(com.bamnat.core.recipe.WidgetLook())
                }
            }
        }
        settle()
        assertTrue(appears("오늘 위젯", exact = true))
        tapText("달 위젯", exact = true)
        settle()
        assertTrue(appears("${today.monthValue}월", exact = true))
    }

    /** PC 확인 C4 1.36: a decoration under the calendar screen's words is seen on the real screen (the page above it is clear). */
    @Test fun theCalendarBackgroundShowsItsDecor() {
        app.container.settingsRepository.update { it.copy(decor = Decor(listOf(
            DecorItem(DecorKind.SHAPE, "circle", at = "bg.calendar", x = 820, y = 860, w = 160, h = 160, fill = 0xFFFFB300L),
            DecorItem(DecorKind.PHOTO, "00000000000000ee", at = "bg.calendar", x = 200, y = 880, w = 120, h = 90, r = 12),
        )).encode()) }
        shoot("1487_lay_calendar_bg", PHONE) { settle() }
    }
}

