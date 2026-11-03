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

    @Test fun minusTakesOffOnlyWhatWasLaidOn() {
        val mine = Logic(listOf(memo), listOf(reset))
        val got = Logic(listOf(memo.copy(label = "남의 단추")), listOf(night))
        assertEquals(mine, mine.plus(got).minus(got))
    }

    @Test fun sameRuleUnderAnotherNameIsSaid() {
        val mine = Logic(rules = listOf(night.copy(name = "B 내 야간 규칙")))
        val theirs = Logic(rules = listOf(night.copy(name = "A 야간 알림"), reset))
        assertEquals(listOf("A 야간 알림" to "B 내 야간 규칙"), mine.same(theirs))
        assertEquals("‘A 야간 알림’은 내 ‘B 내 야간 규칙’과 하는 일이 같아 하나로 뒀어요", mine.mergeSay(theirs))
        assertEquals(null, mine.mergeSay(Logic(rules = listOf(reset))))
        val full = Logic(rules = (0 until Logic.MAX_RULES).map { reset.copy(min = it) })
        assertTrue(full.mergeSay(Logic(rules = listOf(night)))!!.contains("넘는 1개"))
    }

    @Test fun plusKeepsLimits() {
        val many = Logic(rules = (0 until 20).map { reset.copy(min = it) })
        assertEquals(Logic.MAX_RULES, Logic().plus(many).rules.size)
    }

    /** 누락 점검 N8: a button's id survives the recipe check, a bad one is refused; 꾸밈 층 and 길게 누르면 read. */
    @Test fun idDecorAndHoldPassTheRecipeCheck() {
        val body = Logic(listOf(memo.copy(place = ButtonPlace.DECOR, givenId = "deco1")), listOf(reset.copy(on = RuleTrigger.HOLD))).toValues()
        val json = com.bamnat.core.recipe.RecipeCodec.canonical(mapOf("format" to "bamnat-recipe", "v" to 1L, "title" to "꾸밈 단추", "styles" to listOf("SHIFT"),
            "pieces" to listOf(mapOf("kind" to "logic") + body)))
        val r = com.bamnat.core.recipe.RecipeCodec.read(json)
        val back = Logic.from(r.pieces.single().values)
        assertEquals("deco1", back.buttons.single().id)
        assertEquals(ButtonPlace.DECOR, back.buttons.single().place)
        assertEquals(RuleTrigger.HOLD, back.rules.single().on)
        val bad = runCatching { com.bamnat.core.recipe.RecipeCodec.read(json.replace("deco1", "Bad-Id")) }.exceptionOrNull()
        assertEquals("id", (bad as com.bamnat.core.recipe.RecipeException).code)
    }

    @Test fun logicOnlyRecipeIsItsOwnPart() {
        val r = Recipe("규칙", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.LOGIC, Logic(listOf(memo), listOf(reset)).toValues())))
        assertEquals(LookParts.LOGIC, LookParts.partUnit(r))
        assertTrue(LookParts.of(r).none { it.id == LookParts.LOGIC })
        val theme = r.copy(pieces = r.pieces + Piece(PieceKind.CAL_THEME, mapOf("sun" to 0xFF00FF00)))
        assertNull(LookParts.partUnit(theme))
    }
}
