package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.WidgetLook
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVM-45] 검토 RCP-02 (조건 10): 설정형 조각(규칙·단추, 위젯·달력 색 …)은 글 전체가 넣은 그대로일 때만 돌아가서, 내 단추 하나만
 * 더해도 받은 규칙이 남았는데 '되돌렸어요'라고 했어요. 이제 그 작품이 더하고 바꾼 것만 돌아가고, 내가 바꾼 곳은 남기고 그렇다고 말해요.
 */
@RunWith(AndroidJUnit4::class)
class SettingUndoTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val main = PersonalCalendar.MAIN
    private val add = LogicButton("야식 기록", "🍜", ButtonPlace.MONEY, LogicAct(LogicVerb.ADD_MONEY, "야식", 5000))
    private val pay = LogicRule("야간 끝 수당", RuleTrigger.END, act = LogicAct(LogicVerb.ADD_INCOME, "야간 수당", 30000))
    private val mine = LogicButton("물 한 잔", "💧", ButtonPlace.DAY, LogicAct(LogicVerb.ADD_MONEY, "물", 0))

    private fun one(kind: PieceKind, values: Map<String, Any?>, title: String = "작품") =
        Recipe(title, styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(kind, values)))
    private suspend fun putOn(r: Recipe, code: String) =
        c.recipes.apply(r, code, 1, main, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())!!

    @Test fun myOwnButtonDoesNotKeepTheWorksRules() = runBlocking {
        val id = putOn(one(PieceKind.LOGIC, Logic(listOf(add), listOf(pay)).toValues()), "LOGI2345")
        c.settingsRepository.update { it.copy(logic = Logic.encode(Logic.decode(it.logic).plus(Logic(listOf(mine))))) }
        assertTrue(c.recipes.undo(id))
        val now = Logic.decode(c.settingsRepository.current.logic)
        assertFalse("받은 단추는 빠져요", now.buttons.any { it.label == add.label })
        assertTrue("받은 규칙도", now.rules.none { it.key == pay.key })
        assertEquals("내 단추는 남아요", listOf(mine.label), now.buttons.map { it.label })
        assertEquals("되돌렸어요. 그 사이 적은 기록은 그대로예요", c.recipes.undoSay(true))
    }

    @Test fun aLookGoesBackKeyByKeyAndSaysWhatStayed() = runBlocking {
        val id = putOn(one(PieceKind.WIDGET, mapOf("alpha" to 60L, "theme" to "dark")), "WIDG2345")
        assertEquals(WidgetLook(alpha = 60, theme = "dark"), WidgetLook.decode(c.settingsRepository.current.widgetLook))
        c.settingsRepository.update { it.copy(widgetLook = WidgetLook.decode(it.widgetLook).copy(alpha = 80).encode()) }
        assertTrue(c.recipes.undo(id))
        assertEquals("어두운 위젯은 원래대로, 내가 고친 투명도는 그대로", WidgetLook(alpha = 80, theme = "system"),
            WidgetLook.decode(c.settingsRepository.current.widgetLook))
        assertEquals(1, c.recipes.lastUndo.kept)
        assertEquals("되돌렸어요. 넣은 뒤 직접 바꾼 1곳은 그대로 뒀어요", c.recipes.undoSay(true))
    }

    @Test fun aColourIChoseAfterStaysWhenTheWorksGoes() = runBlocking {
        val a = putOn(one(PieceKind.CAL_THEME, mapOf("sat" to 0xFF1565C0L), "파란 토요일"), "AAAA2345")
        c.settingsRepository.update { it.copy(calTheme = CalendarTheme.decode(it.calTheme).copy(sun = 0xFFD32F2FL).encode()) }
        assertTrue(c.recipes.undo(a))
        val t = CalendarTheme.decode(c.settingsRepository.current.calTheme)
        assertNull("작품의 토요일 색은 빠져요", t.sat)
        assertEquals("내가 고른 일요일 색은 남아요", 0xFFD32F2FL, t.sun)
        assertEquals("작품이 바꾸지 않은 곳은 '그대로 둔 곳'이 아니에요", 0, c.recipes.lastUndo.kept)
    }
}
