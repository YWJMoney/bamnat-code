package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.recipe.RecipeFonts
import com.bamnat.app.ui.settings.FillEditor
import com.bamnat.app.ui.settings.FontPickerDialog
import com.bamnat.app.ui.settings.MotionEditor
import com.bamnat.app.ui.settings.PhotoAdjustEditor
import com.bamnat.app.ui.settings.PhotoFrameEditor
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.Fill
import com.bamnat.core.recipe.Motion
import com.bamnat.core.recipe.PhotoAdjust
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [MAT-01]~[MAT-07] 1.36 갈래 B 꾸미기 재료 (사용자 2026-11-01 "공개된 글꼴들을 최대한 넣어서 선택지를 애초에 많게"), numbers 1480~1486:
 * the month with cells in a gradient and a pattern, a date font and a background gradient, by day and by night; 채우기 고르기; 글꼴 고르기
 * with samples in each font; 사진 조정·틀; 움직임.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MATScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private companion object {
        const val NIGHT = "w360dp-h780dp-port-night-450dpi"
        val SUNSET = Fill.Linear(135, listOf(Fill.Stop(0xFFFFE0B2), Fill.Stop(0xFFF8BBD0), Fill.Stop(0xFFD1C4E9)))
        val DOTS = Fill.Pattern("dots", 0x33FF7043, 0xFFFFF8E1, 80, 0)
    }

    private fun dressed() {
        val view = CalendarView(looks = listOf(
            CellLook(fill = SUNSET, corner = 10, dateFont = "serif", borderWidth = 15, border = "thin", borderAlpha = 40),
            CellLook(fill = DOTS, corner = 10, dateFont = "serif"),
        ))
        val screens = ScreenLooks(mapOf(ScreenLooks.ALL to ScreenLook(fill = Fill.Radial(50, 0, 120, listOf(Fill.Stop(0xFFFFF3E0), Fill.Stop(0xFFE8EAF6))))))
        c.settingsRepository.update { it.copy(calView = view.encode(), screenLooks = screens.encode(), motion = Motion(today = "glow").encode()) }
    }

    /** [MAT-01][MAT-02][MAT-05] Cells in a gradient and a pattern taking turns, a serif date, the screen in a radial gradient. */
    @Test fun cellsAndScreenInFills() {
        dressed()
        assertEquals(SUNSET, CalendarView.decode(c.settingsRepository.current.calView).looks.first().fill)
        shoot("1480_mat_cells_fills", PHONE)
    }

    @Test fun cellsAndScreenInFillsAtNight() {
        dressed()
        shoot("1481_mat_cells_fills_night", NIGHT)
    }

    /** [MAT-01] 채우기: kinds, ready-made ones and a gradient's handles. */
    @Test fun theFillEditor() = shoot("1482_mat_fill_editor", PHONE) {
        content {
            var f by remember { mutableStateOf<Fill?>(SUNSET) }
            Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { FillEditor("칸 채우기", f) { f = it } } }
        }
        assertTrue(appears("칸 채우기", exact = false))
        assertTrue(appears("그라데이션", exact = false))
    }

    /** [MAT-05][MAT-06] 글꼴 고르기: 내 글꼴 first, then every group, each line written in its own font. */
    @Test fun theFontPicker() = shoot("1483_mat_font_picker", PHONE) {
        content { FontPickerDialog(null, onDismiss = {}, onPick = {}) }
        assertTrue(appears("글꼴 고르기", exact = true))
        assertTrue(appears("+ 내 글꼴 넣기", exact = true))
        assertTrue(appears("3월 14일 야간", exact = false))
    }

    /** [MAT-03][MAT-04] 사진 조정 and 틀. */
    @Test fun photoAdjustAndFrame() = shoot("1484_mat_photo", PHONE) {
        content {
            var a by remember { mutableStateOf(PhotoAdjust(bright = 10, saturate = -20, tint = 0xFF5C6BC0, tintAmount = 30)) }
            var fr by remember { mutableStateOf<String?>("heart") }
            Surface {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    PhotoAdjustEditor(a) { a = it }
                    PhotoFrameEditor("그림 틀", fr) { fr = it }
                }
            }
        }
        assertTrue(appears("그림 틀", exact = false))
    }

    /** [MAT-07] 움직임: 달 넘김, 오늘 강조, 입히기 전환, 빠르기, and 끄기. */
    @Test fun motion() = shoot("1485_mat_motion", PHONE) {
        content {
            var m by remember { mutableStateOf(Motion(turn = "flip", today = "pulse")) }
            Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { MotionEditor(m) { m = it } } }
        }
        assertTrue(appears("오늘", exact = false))
    }

    /** [MAT-06] A recipe names a user font by its id wherever a font goes; ids inside other words are not fonts. */
    @Test fun fontIdsInARecipe() {
        val text = """{"screens":"all=ft:u0123456789abcdef","cells":"ff:u0123456789abcdef/ff:ufedcba9876543210","note":"xu0123456789abcdefx"}"""
        assertEquals(setOf("u0123456789abcdef", "ufedcba9876543210"), RecipeFonts.idsIn(text))
    }
}
