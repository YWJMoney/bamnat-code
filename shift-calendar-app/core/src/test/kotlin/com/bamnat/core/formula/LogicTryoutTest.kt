package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 1.39 [MKC-03] 도구 전후 해 보기: a 장터 규칙·단추 tried on the phone's own days, never done. */
class LogicTryoutTest {
    private val mon = LocalDate.of(2026, 11, 2) // 월
    private val night = DayFactsBook.DayShift("야간", "night", "22:00", "07:00", 540)
    private val day = DayFactsBook.DayShift("주간", "day", "07:00", "15:00", 480)
    private val book = DayFactsBook(
        today = mon, style = "교대",
        shifts = { d -> when (d.dayOfWeek.value) { 1, 2 -> night; 3 -> day; else -> DayFactsBook.DayShift("휴무", "off", off = true) } },
        money = { d -> if (d == mon) DayFactsBook.DayCash(expense = 8000, kinds = listOf("식비")) else null },
    )

    @Test fun aNightEndRuleRunsOnNightsOnly() {
        val r = LogicRule("야간 끝", RuleTrigger.END, min = 30, role = "night", act = LogicAct(LogicVerb.ADD_INCOME, "야간 수당", 20_000))
        val n = LogicTryout.rule(r, mon, book)
        assertTrue(n.runs)
        assertEquals(LocalTime.of(7, 30), n.at)
        assertEquals("가계부에 수입 ‘야간 수당 20,000원’ 한 줄이 생겨요", n.result)
        val d = LogicTryout.rule(r, mon.plusDays(2), book)
        assertFalse(d.runs)
        assertTrue(d.why, d.why.contains("주간"))
        val off = LogicTryout.rule(r, mon.plusDays(4), book)
        assertFalse(off.runs)
        assertTrue(off.why.contains("쉬는 날"))
        assertEquals(listOf(mon, mon.plusDays(1), mon.plusDays(7), mon.plusDays(8)), LogicTryout.runsOn(r, (0L..13L).map { mon.plusDays(it) }, book))
    }

    @Test fun aConditionIsReadOnTheDay() {
        val r = LogicRule("지출 알림", RuleTrigger.DAILY, at = "21:00", cond = "지출 > 5000", act = LogicAct(LogicVerb.NOTIFY, "오늘 {지출}원 썼어요"))
        val t = LogicTryout.rule(r, mon, book)
        assertTrue(t.runs)
        assertEquals("21:00에", t.why)
        assertEquals("‘오늘 8000원 썼어요’ 알림이 떠요", t.result)
        assertFalse(LogicTryout.rule(r, mon.plusDays(1), book).runs)
    }

    @Test fun aButtonHidesOnDaysItsShowIsFalse() {
        val b = LogicButton("커피", place = ButtonPlace.MONEY, act = LogicAct(LogicVerb.ADD_MONEY, "커피", 4_500), show = "근무종류 = \"야간\"")
        val on = LogicTryout.press(b, mon, book)
        assertTrue(on.runs)
        assertEquals("가계부에 지출 ‘커피 4,500원’ 한 줄이 생겨요", on.result)
        val hidden = LogicTryout.press(b, mon.plusDays(2), book)
        assertFalse(hidden.runs)
        assertEquals("", hidden.result)
    }

    @Test fun receivedAmountsAreHeld() {
        val got = Logic(buttons = listOf(LogicButton("큰돈", act = LogicAct(LogicVerb.ADD_MONEY, "x", 9_000_000)))).received()
        assertEquals("가계부에 지출 ‘x 1,000,000원’ 한 줄이 생겨요", LogicTryout.press(got.buttons.first(), mon, book).result)
    }
}
