package com.bamnat.app.recipe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.WidgetLook
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVM-59] 검토 U09b-03 (내 서랍 › 받은 것: 뒤 작품에 밀리면 '바뀜'만 보이고 손쓸 길 없음): each piece of a received work says
 * whether it is in use or which later work covered it, and one piece can be taken back alone while the rest stays on.
 */
@RunWith(AndroidJUnit4::class)
class PieceUndoTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private val main = PersonalCalendar.MAIN
    private val add = LogicButton("야식 기록", "🍜", ButtonPlace.MONEY, LogicAct(LogicVerb.ADD_MONEY, "야식", 5000))
    private val widget = Piece(PieceKind.WIDGET, mapOf("alpha" to 60L, "theme" to "dark"))
    private val logic = Piece(PieceKind.LOGIC, Logic(listOf(add)).toValues())

    private fun work(title: String, vararg p: Piece) = Recipe(title, styles = WorkStyle.entries.toSet(), pieces = p.toList())
    private suspend fun putOn(r: Recipe, code: String) =
        c.recipes.apply(r, code, 1, main, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())!!
    private suspend fun row(id: Long) = c.database.recipeDao().appliedById(id)!!
    private fun buttons() = Logic.decode(c.settingsRepository.current.logic).buttons.map { it.label }

    @Test fun eachPieceSaysWhichLaterWorkCoveredIt() = runBlocking {
        val a = putOn(work("야식 관리판", widget, logic), "PIEC2345")
        putOn(work("어두운 위젯", Piece(PieceKind.WIDGET, mapOf("alpha" to 90L))), "WIDG4567")
        val states = RecipeRepository.pieceStates(row(a))
        assertEquals(listOf(PieceKind.LOGIC to true, PieceKind.WIDGET to false), states.map { it.kind to it.on })
        assertEquals("어두운 위젯", states.single { !it.on }.coveredBy)
        assertNull("조각 하나만 덮였으니 작품 자리는 그대로예요", row(a).replacedAt)
        assertTrue(c.recipes.canUndo(row(a)))
    }

    @Test fun oneWorkCoveringEveryPieceIsNamed() = runBlocking {
        val a = putOn(work("위젯 반투명", widget), "WIDG5678")
        putOn(work("어두운 위젯", Piece(PieceKind.WIDGET, mapOf("alpha" to 90L))), "WIDG6789")
        assertNotNull(row(a).replacedAt)
        assertEquals("어두운 위젯", RecipeRepository.coveredBy(row(a)))
        // Undo of the record it covered is no longer offered; 받은 것 says by what instead.
        assertFalse(c.recipes.canUndo(row(a)))
    }

    @Test fun onePieceGoesBackAndTheRestStaysOn() = runBlocking {
        val look = WidgetLook.decode(c.settingsRepository.current.widgetLook)
        val a = putOn(work("야식 관리판", widget, logic), "PIEC3456")
        assertTrue(c.recipes.undoPieces(a, setOf(PieceKind.WIDGET.key)))
        assertEquals("위젯은 넣기 전으로", look, WidgetLook.decode(c.settingsRepository.current.widgetLook))
        assertEquals("단추는 그대로", listOf(add.label), buttons())
        assertEquals("남은 조각은 그 작품 것으로, 되돌리기도 그대로", PieceKind.LOGIC.key, row(a).pieces)
        assertTrue(c.recipes.canUndo(row(a)))
        assertEquals(1, c.recipes.lastUndo.back)
        // The rest taken back later: the work is 되돌림 once nothing of it is on.
        assertTrue(c.recipes.undo(a))
        assertTrue(buttons().isEmpty())
        assertNotNull(row(a).undoneAt)
    }
}
