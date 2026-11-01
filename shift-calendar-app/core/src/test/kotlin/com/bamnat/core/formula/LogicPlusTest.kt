package com.bamnat.core.formula

import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RUL-07] 규칙·단추만 올리고 담아 와서 얹기: a logic-only recipe is its own part, and laying it on adds without doubling. */
class LogicPlusTest {
    private val reset = LogicRule("퇴근 뒤 체크 비우기", RuleTrigger.END, min = 30, act = LogicAct(LogicVerb.CHECK_RESET))
    private val night = LogicRule("야간 알림", RuleTrigger.START, min = -60, act = LogicAct(LogicVerb.NOTIFY, "야간이에요"))
    private val memo = LogicButton("오늘 기록", "📝", ButtonPlace.DAY, LogicAct(LogicVerb.MEMO_TEMPLATE, "하루 기록"))

    @Test fun plusAddsOnlyNew() {
        val mine = Logic(listOf(memo), listOf(reset))
        val got = mine.plus(Logic(listOf(memo), listOf(reset, night)))
        assertEquals(1, got.buttons.size)
        assertEquals(listOf(reset, night), got.rules)
    }

    @Test fun plusKeepsLimits() {
        val many = Logic(rules = (0 until 20).map { reset.copy(min = it) })
        assertEquals(Logic.MAX_RULES, Logic().plus(many).rules.size)
    }

    @Test fun logicOnlyRecipeIsItsOwnPart() {
        val r = Recipe("규칙", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.LOGIC, Logic(listOf(memo), listOf(reset)).toValues())))
        assertEquals(LookParts.LOGIC, LookParts.partUnit(r))
        assertTrue(LookParts.of(r).none { it.id == LookParts.LOGIC })
        val theme = r.copy(pieces = r.pieces + Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFF00FF00)))
        assertNull(LookParts.partUnit(theme))
    }
}
