package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.recipe.Blueprint
import androidx.compose.foundation.verticalScroll
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

    /** [BLU-11][BLU-10] 누락 점검 N2·N3: 급여 머리·합계·한 줄, 가계부 날짜 머리·한 줄·금액, 위젯 판·머리·칸이 부품대로. */
    private val more = Blueprint(
        Blueprint.clean(
            listOf(
                mapOf("id" to "pay.head", "font" to "serif", "ink" to 0xFF1E335CL, "deco" to "under"),
                mapOf("id" to "pay.total", "fill" to 0xFF1E335CL, "r" to 4L, "line" to "dash", "lineC" to 0xFFFFC107L, "lineW" to 2L, "tshadow" to 3L),
                mapOf("id" to "pay.row", "on" to "out", "fill" to 0xFFFFEBEEL, "ink" to 0xFFC62828L, "r" to 8L, "pad" to 6L),
                mapOf("id" to "money.day", "fill" to 0xFFE8F5E9L, "r" to 12L),
                mapOf("id" to "money.amount", "on" to "in", "fill" to 0xFFFFF4CCL, "r" to 6L, "pad" to 2L, "italic" to true),
                mapOf("id" to "widget", "fill" to 0xFF102A43L, "r" to 6L),
                mapOf("id" to "widget.head", "ink" to 0xFFFFC107L, "font" to "mono"),
                mapOf("id" to "widget.cell", "on" to "today", "fill" to 0xFF2E7D32L, "r" to 10L, "pad" to 4L),
            ),
        ),
    )

    @Test fun partsOnPayMoneyAndWidget() = shoot("1901_parts_pay_money_widget", FOLDED) {
        content {
            androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.parts.LocalBlueprint provides more) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScrollFixed()) {
                    com.bamnat.app.ui.settings.PaySample(com.bamnat.core.recipe.PayLook(), com.bamnat.core.work.WorkStyle.SHIFT) {}
                    com.bamnat.app.ui.settings.MoneySample(com.bamnat.core.recipe.MoneyLook()) {}
                    com.bamnat.app.ui.studio.WidgetSample(com.bamnat.core.recipe.WidgetLook())
                }
            }
        }
        settle()
    }
}

@androidx.compose.runtime.Composable
private fun androidx.compose.ui.Modifier.verticalScrollFixed() = this.then(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
