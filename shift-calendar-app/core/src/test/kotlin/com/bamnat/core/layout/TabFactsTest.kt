package com.bamnat.core.layout

import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TabFactsTest {
    @Test fun memoFactsCarryItsOwnLinesAndItsDay() {
        val m = Memo(id = 1, title = "마감", body = "매출: 32000\n상태: 끝 #가게", date = LocalDate.of(2026, 11, 7))
        val f = TabFacts.memo(m, day = mapOf("근무" to "야간"))
        assertEquals("마감", f.values["제목"])
        assertEquals(true, f.values["주말"])
        assertEquals("야간", f.values["근무"])
        assertEquals(32000.0, f.inputs["매출"])
        assertEquals("끝 #가게", f.inputs["상태"])
        val list = Memo(id = 2, isChecklist = true, checklist = listOf(ChecklistItem("a", true), ChecklistItem("b")))
        assertEquals(50, TabFacts.memo(list, day = mapOf("근무" to "야간")).values["체크율"])
        assertEquals(null, TabFacts.memo(list, day = mapOf("근무" to "야간")).values["근무"])
    }

    @Test fun moneyFacts() {
        val f = TabFacts.money(MoneyEntry(date = LocalDate.of(2026, 11, 2), kind = MoneyKind.EXPENSE, amount = 12000, category = "식비", merchant = "김밥"))
        assertEquals(12000L, f.values["금액"])
        assertEquals("지출", f.values["종류"])
        assertEquals("김밥", f.values["내용"])
        assertEquals("월", f.values["요일"])
    }

    /** PC 4277abca: '분류 색 가계부' and '고정 메모 금색' read the line's and the card's own values through these facts. */
    @Test fun aLinesOwnValuesDriveItsPart() {
        val bp = com.bamnat.core.recipe.Blueprint(com.bamnat.core.recipe.Blueprint.clean(listOf(
            mapOf("id" to "money.row", "when" to "분류 = \"식비\"", "fill" to 0xFFFFF3E0L),
            mapOf("id" to "money.row", "when" to "분류 = \"교통\"", "fill" to 0xFFE3F2FDL),
            mapOf("id" to "memo.card", "when" to "고정", "line" to "solid", "lineW" to 2.0, "lineC" to 0xFFFFB300L),
        )))
        fun row(cat: String) = TabFacts.money(MoneyEntry(date = LocalDate.of(2026, 11, 2), kind = MoneyKind.EXPENSE, amount = 5000, category = cat))
            .let { bp.style("money.row", emptySet(), com.bamnat.core.formula.FormulaScope.of(it.values, it.inputs)) }
        assertEquals(0xFFFFF3E0L, row("식비").fill)
        assertEquals(0xFFE3F2FDL, row("교통").fill)
        assertEquals(null, row("카페").fill)
        fun card(pinned: Boolean) = TabFacts.memo(Memo(id = 1, title = "a", pinned = pinned))
            .let { bp.style("memo.card", emptySet(), com.bamnat.core.formula.FormulaScope.of(it.values, it.inputs)) }
        assertEquals(0xFFFFB300L, card(true).lineC)
        assertEquals(null, card(false).lineC)
    }
}
