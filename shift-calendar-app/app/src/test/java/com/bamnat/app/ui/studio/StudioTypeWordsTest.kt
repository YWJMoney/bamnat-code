package com.bamnat.app.ui.studio

import com.bamnat.app.logic.choicesFor
import com.bamnat.core.formula.FormulaName
import com.bamnat.core.recipe.BlueprintParts
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-10] 검토 T09a-06·STU-27: the studio's '어떤 때' and the rule editor's 근무 칩 showed 주간·야간·24시간·당직·연차·대근 to a shop,
 * an investor and a 일근 worker. Each kind now sees only its own kinds of 근무, in its own words, and nothing [TypeWords] forbids it.
 */
class StudioTypeWordsTest {
    @Test fun everyKindReadsItsOwnWordsOnEveryPart() {
        for (style in WorkStyle.entries) for (id in BlueprintParts.IDS) {
            val words = statesFor(id, style).map { stateWord(id, it, style) }
            assertEquals("$style $id: $words", emptyList<String>(), TypeWords.leaks(style, words.joinToString(" ")))
        }
    }

    @Test fun aShopSeesItsDaysAndNotShifts() {
        val shop = statesFor("cal.cell", WorkStyle.BUSINESS)
        assertTrue("role.night" !in shop && "role.long" !in shop && "leave" !in shop && "cover" !in shop)
        assertEquals("영업일", stateWord("cal.cell", "role.day", WorkStyle.BUSINESS))
        assertEquals("휴무", stateWord("cal.cell", "role.off", WorkStyle.BUSINESS))
        // 교대 keeps every one, as before.
        assertTrue(statesFor("cal.cell", WorkStyle.SHIFT).containsAll(listOf("role.eve", "role.night", "role.long", "leave", "extra", "cover")))
        assertEquals("야간 근무", stateWord("cal.cell", "role.night", WorkStyle.SHIFT))
        assertEquals("대타", stateWord("cal.cell", "cover", WorkStyle.HOURLY))
    }

    @Test fun aStateFromAWorkMadeElsewhereStaysToBeTakenOff() {
        assertTrue("role.night" in statesFor("cal.cell", WorkStyle.INVEST, keep = "role.night"))
    }

    @Test fun ruleChipsAreTheWorksOwn() {
        val shop = choicesFor(FormulaName.ROLE.ko, WorkStyle.BUSINESS)!!
        assertEquals(listOf("주간" to "영업일", "휴무" to "휴무"), shop)
        assertEquals(6, choicesFor(FormulaName.ROLE.ko, WorkStyle.SHIFT)!!.size)
        assertTrue("야간" to "야간" in choicesFor(FormulaName.ROLE.ko, WorkStyle.DAY, keep = "야간")!!)
    }
}
