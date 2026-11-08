package com.bamnat.app.screenshots

import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.recipe.LiveTryOn
import com.bamnat.app.ui.recipe.TryOnScreen
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TRY-05] 1.39 실제 화면으로 입어 보기: 입어 보기의 새 단추, and the real app's tabs with a theme tried on (and 지금 모습) — the 가계부,
 * 근무·급여 and 달력 with the sample records, folded and open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LiveTryOnScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val neon: Recipe get() = BuiltInThemes.of(WorkStyle.SHIFT).first { it.title.contains("네온") }

    @After fun letGo() = runBlocking { LiveTryOn.settle(c) }

    /** [TRY-06] A work of 기능: a 가계부 button and a 메모 틀. */
    private val night = Recipe("야식 관리판", styles = WorkStyle.entries.toSet(), pieces = listOf(
        com.bamnat.core.recipe.Piece(com.bamnat.core.recipe.PieceKind.LOGIC, com.bamnat.core.formula.Logic(listOf(
            com.bamnat.core.formula.LogicButton("야식 5천", "🍜", com.bamnat.core.formula.ButtonPlace.MONEY,
                com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.ADD_MONEY, "식비", 5000)))).toValues()),
        com.bamnat.core.recipe.Piece(com.bamnat.core.recipe.PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "야식 메모", "body" to "무엇을 먹었나")))),
    ))

    private fun Screen.tryOn(tab: String) {
        runBlocking { LiveTryOn.start(c, neon, own = false, LookParts.of(neon).map { it.id }.toSet(), tab, LiveTryOn.From.Detail("ABCD2345")) }
        settle()
        settle()
    }

    @Test fun button() = shoot("1420_try_on_live_button_folded", FOLDED) {
        content { TryOnScreen(neon, WorkStyle.SHIFT, onDismiss = {}, onKeep = { true }, live = { _, _ -> true }) { Text("이 테마 입기") } }
    }
    @Test fun moneyMine() = shoot("1421_try_on_live_money_before_folded", FOLDED) { tab(1, wide = false) }
    @Test fun moneyTried() = shoot("1422_try_on_live_money_folded", FOLDED) { tryOn("money") }
    @Test fun moneyNow() = shoot("1423_try_on_live_money_now_folded", FOLDED) { tryOn("money"); LiveTryOn.show(c, false); settle() }
    @Test fun statsTried() = shoot("1424_try_on_live_stats_folded", FOLDED) { tryOn("stats") }
    @Test fun calendarTriedOpen() = shoot("1425_try_on_live_calendar_unfolded", UNFOLDED) { tryOn("calendar") }
    @Test fun memosTried() = shoot("1426_try_on_live_memos_folded", FOLDED) { tryOn("memos") }
    /** [TRY-06] 기능까지 써 보기: the 가계부 with the work's button, the band saying it really runs. */
    @Test fun functionsTried() = shoot("1427_try_functions_money_folded", FOLDED) {
        runBlocking {
            LiveTryOn.start(c, night, own = false, emptySet(), "money", LiveTryOn.From.Detail("FN234567"),
                functions = mapOf(com.bamnat.core.recipe.PieceKind.LOGIC to com.bamnat.app.recipe.RecipePieces.Mode.REPLACE,
                    com.bamnat.core.recipe.PieceKind.MEMO_TEMPLATES to com.bamnat.app.recipe.RecipePieces.Mode.BOTH))
        }
        settle()
        settle()
    }
    @Test fun functionsButton() = shoot("1428_try_functions_button_folded", FOLDED) {
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.bamnat.app.ui.recipe.TryFunctionsButton(listOf(com.bamnat.core.recipe.PieceKind.LOGIC, com.bamnat.core.recipe.PieceKind.MEMO_TEMPLATES), enabled = true) { true }
                com.bamnat.app.ui.recipe.TriedCard("‘야식 관리판’ 써 보기를 끝냈어요. 단추·규칙이 2번 일했고, 그 기록은 모두 지웠어요") {}
            }
        }
    }
    /** [TRY-07] '바뀐 곳 ▶' walked once: the next tab the look changes, the band two rows on the folded phone. */
    @Test fun tourNext() = shoot("1429_try_on_live_tour_next_folded", FOLDED) { tryOn("money"); LiveTryOn.next(); settle(); settle() }
    /**
     * PC 4차 c0ca3d9f (장터 3, 상): a part tried on the real app from its page in 마을, then 끝내기: back from the page is 마을, and 마을 on
     * the bar is 마을 (it opened 근무·급여 with 근무·급여 lit, and back went to the part's page). [tour]: '바뀐 곳' walked to another tab first.
     */
    private fun Screen.endedThenVillage(tour: Boolean) {
        tab(4, wide = false)
        assertTrue(appears("장터", exact = true))
        com.bamnat.app.recipe.RecipeLinks.open("ABCD2345")
        settle(); settle()
        tryOn("stats")
        if (tour) { LiveTryOn.next(); settle(); settle() }
        LiveTryOn.end(c)
        settle(); settle()
        assertTrue("the page again", appears("레시피", exact = true))
        back()
        assertTrue("back is 마을", appears("장터", exact = true))
        tab(1, wide = false)
        tab(4, wide = false)
        assertTrue("마을 is 마을", appears("장터", exact = true))
        assertFalse(shows("10월 총 근무"))
    }
    @Test fun endedThenVillageIsTheVillage() = shoot("1430_try_on_live_end_village_folded", FOLDED) { endedThenVillage(tour = false) }
    @Test fun touredEndedThenVillageIsTheVillage() = shoot("1431_try_on_live_tour_end_village_folded", FOLDED) { endedThenVillage(tour = true) }
}
