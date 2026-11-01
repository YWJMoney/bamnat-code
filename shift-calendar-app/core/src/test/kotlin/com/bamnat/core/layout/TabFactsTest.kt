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
}
