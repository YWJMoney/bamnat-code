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

    /**
     * 2.0.0 [RVM-23] U09b-01 (넣기 창 맨 위에 '조심 줄'을 따로, RCP-05·17·STU-23): the alarm made weaker, what writes 가계부·메모 (a later
     * step too) and a 꾸밈 that presses a 단추 come first, each marked; a work with none of them has none.
     */
    @Test fun whatOneWouldNotSeeComingIsSaidFirst() {
        val coffee = com.bamnat.core.formula.LogicButton("커피", act = com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.VAL_ADD, "커피", 1),
            more = listOf(com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.ADD_MONEY, "카페", 4_500)), givenId = "b0001")
        val night = com.bamnat.core.formula.LogicRule("야간 끝 회복", com.bamnat.core.formula.RuleTrigger.DAILY, at = "09:00",
            act = com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.NOTIFY, "수고했어요"),
            more = listOf(com.bamnat.core.formula.LogicAct(com.bamnat.core.formula.LogicVerb.MEMO_LINE, "야간 끝")))
        val logic = com.bamnat.core.formula.Logic(buttons = listOf(coffee), rules = listOf(night))
        val decor = com.bamnat.core.recipe.Decor(listOf(com.bamnat.core.recipe.DecorItem(com.bamnat.core.recipe.DecorKind.EMOJI, "☕",
            com.bamnat.core.recipe.Decor.CELL, w = 20, h = 20, tap = "b0001")))
        val r = Recipe(title = "커피 데스크", styles = setOf(WorkStyle.DAY), pieces = listOf(
            Piece(PieceKind.LOGIC, logic.toValues()), Piece(PieceKind.DECOR, decor.values()), Piece(PieceKind.ALARMS, mapOf("ring" to 5L))))
        fun state(weaker: Boolean = false) = RecipeDetailViewModel.State(recipe = r, pieces = listOf(
            RecipeDetailViewModel.Piece(PieceKind.LOGIC, on = true, fits = true), RecipeDetailViewModel.Piece(PieceKind.DECOR, on = true, fits = true),
            RecipeDetailViewModel.Piece(PieceKind.ALARMS, on = true, fits = true, lines = listOf(com.bamnat.app.recipe.RecipePieces.Change("울림 길이 10분 → 5분", weak = true)))),
            alarmWeak = listOf("울림 10분 → 5분"), alarmWeaker = weaker)
        val lines = putBrief(state(), fxOn = true)
        assertEquals(lines.joinToString("\n") { it.text }, listOf(PutLine.Kind.ALARM_WEAK, PutLine.Kind.WRITES, PutLine.Kind.TAPS), lines.take(3).map { it.kind })
        assertTrue(lines.take(3).all { it.caution } && lines.drop(3).none { it.caution })
        assertEquals("누르면 가계부에 적는 단추 1개 · 메모에 적는 규칙 1개는 꺼진 채로 들어와요", lines[1].text)
        assertEquals("달력 칸의 꾸밈을 누르면 단추가 눌려요 · 1개", lines[2].text)
        assertEquals(PutLine.Kind.ALARM_WEAK_ON, putBrief(state(weaker = true), fxOn = true).first().kind)
        // Without its 단추 the 꾸밈 presses nothing, so it is not said; a look alone has no 조심 줄.
        val noLogic = state().copy(pieces = state().pieces.map { if (it.kind == PieceKind.LOGIC) it.copy(on = false) else it })
        assertTrue(putBrief(noLogic, fxOn = true).none { it.kind == PutLine.Kind.TAPS || it.kind == PutLine.Kind.WRITES })
        assertTrue(putBrief(this.state(), fxOn = true).none { it.caution })
    }
}
