package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.recipe.Blueprint
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PC d0799995: 장터 '표 가계부'의 큰 지출 줄(money.table.row, 종류 = "지출" 그리고 금액 >= 50000)이 표에서 다른 줄과 같았어요.
 * 표 한 줄이 자기 값으로 칠해지는지 보는 사진이에요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FxrRowScreenshotTest : ScreenshotBase() {
    private val parts = Blueprint(Blueprint.clean(listOf(
        mapOf("id" to "money.table.row", "when" to "종류 = \"지출\" 그리고 금액 >= 50000", "fill" to 0xFFFFCDD2L),
        mapOf("id" to "money.table.row", "on" to "in", "fill" to 0xFFFFF8E1L),
    )))
    private val d = LocalDate.of(2026, 11, 2)
    private val rows = listOf(
        MoneyEntry(id = 1, date = d, kind = MoneyKind.EXPENSE, amount = 82_000, category = "식비", note = "큰 지출"),
        MoneyEntry(id = 2, date = d, kind = MoneyKind.EXPENSE, amount = 4_500, category = "카페", note = "커피"),
        MoneyEntry(id = 3, date = d, kind = MoneyKind.INCOME, amount = 1_200_000, category = "급여", note = "월급"),
    )

    @Test fun aBigSpendLineInTheTable() = shoot("1990_fxr_table_row", FOLDED) {
        content {
            androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.parts.LocalBlueprint provides parts) {
                com.bamnat.app.ui.money.LedgerTable(rows, "date" to true, {}, {})
            }
        }
        settle()
    }
}
