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
        tapText("고급 보기", exact = true)
        tapText("레이어 3", exact = true)
        settle()
        tapText("이번 달도 힘내기", exact = false)
        settle()
        tapText("고치기 ›", exact = true)
        settle()
        assertTrue(appears("숫자로 맞추기", exact = true))
    }
}
