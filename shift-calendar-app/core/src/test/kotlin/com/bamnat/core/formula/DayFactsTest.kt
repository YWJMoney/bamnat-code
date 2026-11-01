package com.bamnat.core.formula

import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [RUL-05] 885 탭 사이 흐름: a day's values from 근무·메모·가계부, and the done case "야간이고 메모에 #인수인계가 있으면". */
class DayFactsTest {
    private val today = LocalDate.of(2026, 11, 2) // 월
    private val night = DayFactsBook.DayShift("야간", "night", "22:00", "07:00", 540)
    private val day = DayFactsBook.DayShift("주간", "day", "07:00", "15:00", 480)
    private val book = DayFactsBook(
        today = today,
        style = "교대",
        shifts = { d -> when (d.dayOfWeek.value) { 1, 2 -> night; 3 -> day; else -> DayFactsBook.DayShift("휴무", "off", off = true) } },
        memos = { d -> if (d == today) listOf(Memo(date = d, title = "인계 #인수인계", isChecklist = true,
            checklist = listOf(ChecklistItem("약품", true), ChecklistItem("기록")))) else emptyList() },
        money = { d -> if (d == today) DayFactsBook.DayCash(expense = 8000, kinds = listOf("식비")) else null },
    )

    @Test fun valuesOfADay() {
        val v = book.values(today)
        assertEquals("야간", v["근무종류"])
        assertEquals(9.0, v["근무시간"])
        assertEquals("월", v["요일"])
        assertEquals(2, v["이번주야간"])
        assertEquals(50, v["체크율"])
        assertEquals(1, v["남은체크"])
        assertEquals(8000L, v["지출"])
        assertEquals(true, v["오늘"])
    }

    @Test fun theDoneCase() {
        val f = "근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\")"
        assertTrue(Formula.eval(f, book.scope(today))!!.truthy)
        assertFalse(Formula.eval(f, book.scope(today.plusDays(1)))!!.truthy) // 야간이지만 메모 없음
        assertFalse(Formula.eval(f, book.scope(today.plusDays(2)))!!.truthy) // 주간
    }

    @Test fun streakStopsAtADayOff() {
        assertEquals(3, book.values(today.plusDays(2))["연속근무"])
        assertEquals(0, book.values(today.plusDays(3))["연속근무"])
    }
}

class LogicSayTest {
    @Test fun ruleReadsAsASentence() {
        assertEquals("근무가 야간 그리고 꼬리표에 인수인계 있으면", ifWord("근무가 야간 그리고 꼬리표에 인수인계 있음"))
        assertEquals("근무종류가 야간이면", ifWord("근무종류가 야간"))
        assertEquals("요일이 토면", ifWord("요일이 토"))
        assertEquals("조건이 맞으면", ifWord("조건이 맞으면"))
    }
}
