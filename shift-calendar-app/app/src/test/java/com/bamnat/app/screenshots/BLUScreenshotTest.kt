package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.recipe.Blueprint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [BLU-05] 1.36 부품 꾸미기 (갈래 A 완료 기준): 달 이름·요일·날짜 숫자·근무 표시를 각각 다른 색·글꼴·모서리로, 날짜 칸 안 순서를 바꿔서.
 * PC 1차 확인(328a5123)에서 "달 이름 채우기·모서리가 안 보인다"를 다시 보는 사진이에요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BLUScreenshotTest : ScreenshotBase() {
    private val parts = Blueprint(
        Blueprint.clean(
            listOf(
                mapOf("id" to "cal.head.title", "fill" to 0xFF1E335CL, "r" to 15L, "font" to "serif", "pad" to 4L),
                mapOf("id" to "cal.week.day", "on" to "sun", "ink" to 0xFFE5484DL, "font" to "mono"),
                mapOf("id" to "cal.cell.date", "on" to "today", "fill" to 0xFFFFF4CCL, "r" to 8L, "font" to "hand", "weight" to 700L),
                mapOf("id" to "cal.cell.mark", "on" to "role.night", "line" to "dot"),
                mapOf("id" to "cal.cell", "order" to "mark,date"),
            ),
        ),
    ).encode()

    @Test fun partsOnTheCalendar() = shoot("1900_parts_calendar", FOLDED) {
        app.container.settingsRepository.update { it.copy(parts = this@BLUScreenshotTest.parts) }
        settle()
    }
}
