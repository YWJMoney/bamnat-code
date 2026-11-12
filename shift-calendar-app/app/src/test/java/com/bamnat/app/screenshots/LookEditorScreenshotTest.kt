package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.settings.CellDesigner
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.app.ui.settings.PaySample
import com.bamnat.app.ui.components.scrollEdges
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.CellPattern
import com.bamnat.core.recipe.PayLook
import com.bamnat.core.work.WorkStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVP-61]~[RVP-71] (검토 REST-02~13, U14-01~06) The look editors as a person meets them: 칸 디자인 with two looks on 토·일,
 * the colour code that takes only 0~9·A~F, and the pay sample in an 알바's words. Numbers 2716~2719 (the same scenes before and after
 * the 2/3 PR, for its photos), and 2720 the scroll edges of a dialog.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LookEditorScreenshotTest : ScreenshotBase() {
    private val weekend = CalendarView(
        looks = listOf(CellLook(), CellLook(bg = "FFFDE7E9", corner = 12)),
        pattern = CellPattern(mode = CellPattern.WEEK, week = listOf(0, 0, 0, 0, 0, 1, 1)),
    )

    private fun Screen.designer(dark: Boolean) = content {
        ShotTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            var view by remember { mutableStateOf(weekend) }
            var index by remember { mutableStateOf(1) }
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                CellDesigner(view, index, { index = it }) { view = it }
            }
        }
    }

    @Test fun theCellDesigner() = shoot("2716_look_cell_designer", FOLDED) { designer(dark = false); settle() }

    @Test fun theCellDesignerDarkBig() = shoot("2717_look_cell_designer_dark_200", FOLDED, 2f) { designer(dark = true); settle() }

    @Test fun aColourCodeTakesOnlyItsLetters() = shoot("2718_look_color_code", FOLDED) {
        content {
            ShotTheme(themeMode = ThemeMode.LIGHT) {
                var c by remember { mutableStateOf<Long?>(0xFF2F5BEA) }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    ColorPick("칸 바탕", c) { c = it }
                }
            }
        }
        settle()
        tapText("칸 바탕 직접 고르기", exact = true)
        typeInto("색 코드 (예: 2F5BEA)", "12가G")
        settle()
    }

    @Test fun thePaySampleOfAPartTimer() = shoot("2719_look_pay_hourly", FOLDED) {
        content {
            ShotTheme(themeMode = ThemeMode.LIGHT) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    PaySample(PayLook(), WorkStyle.HOURLY) {}
                }
            }
        }
        settle()
    }

    /** [RVP-83] (조건 11) A dialog whose words scroll: a line under the title and above the buttons while there is more. Number 2720. */
    @Test fun aScrollingDialogShowsItsEdges() = shoot("2720_parts_scroll_edges", FOLDED) {
        content {
            ShotTheme(themeMode = ThemeMode.LIGHT) {
                com.bamnat.app.ui.components.AlertDialog(
                    onDismissRequest = {},
                    title = { androidx.compose.material3.Text("근로계약서") },
                    text = {
                        val s = rememberScrollState(120)
                        Column(Modifier.scrollEdges(s).verticalScroll(s)) {
                            repeat(24) { androidx.compose.material3.Text("제${it + 1}조 근로 시간과 쉬는 시간은 이렇게 정해요.") }
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("닫기") } },
                )
            }
        }
        settle()
    }
}
