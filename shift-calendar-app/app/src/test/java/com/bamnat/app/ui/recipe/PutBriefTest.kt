package com.bamnat.app.ui.recipe

import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [PC-33] 넣기 창의 한 줄 요약: a 칸 디자인 that shows shifts by colour alone is said before it goes in, with '글자 남기기'
 * (야간 회복 스테이션: 칸의 주·야·휴 글자가 사라지고 점만 남았어요).
 */
class PutBriefTest {
    private val night = "bg:FF0F1630|ba:80|cr:12|mk:dot|mp:below|pd:2/cr:12|dc:FFB49CFF|mk:dot|mp:below/cr:12|dc:FF4B557A|mk:none|pd:2"
    private val recipe = Recipe(title = "야간 회복 스테이션", styles = setOf(WorkStyle.SHIFT),
        pieces = listOf(Piece(PieceKind.CAL_VIEW, mapOf("looks" to night))))
    private fun state(letters: Boolean = false, on: Boolean = true, lookParts: Set<String>? = null) = RecipeDetailViewModel.State(
        recipe = recipe, pieces = listOf(RecipeDetailViewModel.Piece(PieceKind.CAL_VIEW, on = on, fits = true)), letters = letters, lookParts = lookParts)

    private fun lettersLine(s: RecipeDetailViewModel.State) =
        putBrief(s, fxOn = true).firstOrNull { it.kind == PutLine.Kind.LETTERS || it.kind == PutLine.Kind.LETTERS_KEPT }

    @Test fun colourOnlyCellsAreSaidBeforeTheyGoIn() {
        val line = lettersLine(state())
        assertEquals("칸의 근무 글자 대신 색 점만 보여요", line?.text)
        assertEquals(PutLine.Kind.LETTERS, line?.kind)
        // It comes right after what the look changes.
        val all = putBrief(state(), fxOn = true)
        assertTrue(all.map { it.kind }.toString(), all.indexOfFirst { it.kind == PutLine.Kind.LOOK } < all.indexOf(line))
    }

    @Test fun keepingTheLettersSaysSo() {
        val line = lettersLine(state(letters = true))
        assertEquals("칸에 근무 글자를 남겨요", line?.text)
        assertEquals(PutLine.Kind.LETTERS_KEPT, line?.kind)
    }

    @Test fun notSaidWhenTheCellsDoNotGoIn() {
        assertEquals("달력 모양을 빼고 넣으면", null, lettersLine(state(on = false)))
        assertEquals("칸 디자인을 고르지 않으면", null, lettersLine(state(lookParts = setOf("cal.head"))))
        assertEquals("칸 디자인을 고르면", PutLine.Kind.LETTERS, lettersLine(state(lookParts = setOf("cal.cells")))?.kind)
    }
}
