package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.39 [FX-18] 일주일 미리 돌려 보기 · [FX-20] 예시로 시작 · 몇 분마다 때 (러너와 같은 계산). */
class LogicWeekTest {
    private val day = LocalDate.of(2026, 11, 9) // 월

    @Test fun everyBetweenAndInWork() {
        val r = LogicRule("쉬기", RuleTrigger.EVERY, min = 120, at = "09:00", until = "13:00", act = LogicAct(LogicVerb.NOTIFY, "쉬어요"))
        assertEquals(listOf(9, 11), LogicWeek.everyTimes(r, day, emptyMap()).map { it.hour })
        val work = r.copy(at = null, until = null, inWork = true)
        val night = mapOf(FormulaName.START.ko to "22:00", FormulaName.END.ko to "06:00")
        val t = LogicWeek.everyTimes(work, day, night)
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), t.first())
        assertEquals(LocalDateTime.of(2026, 11, 10, 6, 0), t.last())
        assertTrue(LogicWeek.everyTimes(work, day, mapOf(FormulaName.OFF.ko to true)).isEmpty())
        val all = r.copy(min = 30, at = null, until = null)
        assertEquals(LogicRule.EVERY_DAY_MAX, LogicWeek.everyTimes(all, day, emptyMap()).size)
    }

    @Test fun examplesGoInOnce() {
        FxExamples.ALL.forEach { e -> assertEquals(e.logic.rules, Logic.from(e.logic.toValues()).rules) }
        val water = FxExamples.ALL.first { it.key == "water" }
        val once = FxExamples.into(Logic(), water)!!
        val twice = FxExamples.into(once, water)!!
        assertEquals(1, twice.values.size)
        assertEquals(2, twice.buttons.map { it.id }.toSet().size)
        // 물 8잔: the first 값 칸 and the first 위젯 are the free taste; only 값이 바뀌면 is 프로.
        assertEquals(setOf(FxPro.Part.VALUE_RULE), FxPro.uses(once))
        var full = Logic()
        repeat(Logic.MAX_RULES) { full = full.copy(rules = full.rules + LogicRule("r$it", RuleTrigger.DAILY, at = "09:00", act = LogicAct(LogicVerb.NOTIFY, "a"))) }
        assertNull(FxExamples.into(full, water))
        assertNotNull(FxExamples.into(Logic(), FxExamples.ALL.first { it.key == "nights" }))
    }

    @Test fun clashesAreSaidBeforePuttingIn() {
        val water = LogicValue("물 잔", ValueReset.DAY, goal = 8.0)
        val pay = LogicRule("야간 수당", RuleTrigger.END, role = "night", act = LogicAct(LogicVerb.ADD_INCOME, "수당", amount = 30000))
        val mine = Logic(rules = listOf(pay), values = listOf(water))
        val got = Logic(
            rules = listOf(pay, pay.copy(name = "야간 적기", act = LogicAct(LogicVerb.ADD_MONEY, "야식", amount = 8000))),
            values = listOf(water.copy(reset = ValueReset.WEEK)),
        )
        val said = LogicClash.check(mine, got)
        assertEquals(2, said.size)
        assertTrue(said[0].contains("매주"))
        assertTrue(said[1].contains("두 번 적힐"))
        // The same rule is kept once, not a clash; a different time is not one either.
        assertTrue(LogicClash.check(mine, Logic(rules = listOf(pay, pay.copy(name = "주간", role = "day", min = 30)))).isEmpty())
    }

    @Test fun aNightsEndIsShownTheNextMorningAndTheMemoSeesTheNewCount() {
        // 4차 PC: 11/7 야간(19:00~07:00)의 끝이 '11/7 07:00'에 보였고, 값이 1이 됐는데 메모는 '0번째', '1 → 2이 돼요'.
        val night = DayFactsBook.DayShift("야간", "night", "19:00", "07:00", 720)
        val facts = DayFactsBook(today = day, style = "교대", shifts = { d -> if (d.dayOfWeek.value <= 2) night else DayFactsBook.DayShift("휴무", "off", off = true) })
        val nights = FxExamples.into(Logic(), FxExamples.ALL.first { it.key == "nights" })!!
        val w = LogicWeek.run(nights, facts, day)
        // 월 night ends 화 07:00, 화 night ends 수 07:00; last 일요일 had no night, so nothing on 월 morning.
        assertEquals(listOf(day.plusDays(1), day.plusDays(2)), w.lines.map { it.date })
        assertTrue(w.lines.all { it.at == java.time.LocalTime.of(7, 0) && it.who == "야간 세기(전날 근무 끝)" })
        assertTrue(w.lines[0].what, w.lines[0].what.contains("0 → 1이 돼요") && w.lines[0].what.contains("이달 야간 1번째"))
        assertTrue(w.lines[1].what, w.lines[1].what.contains("1 → 2가 돼요") && w.lines[1].what.contains("이달 야간 2번째"))
        // A night that began the day before the week still ends inside it.
        val fromTue = LogicWeek.run(nights, facts, day.plusDays(1))
        assertEquals(day.plusDays(1), fromTue.lines.first().date)
        assertEquals("2가", LogicTryout.numIGa("2"))
        assertEquals("10이", LogicTryout.numIGa("10"))
        assertEquals("커피가", LogicTryout.numIGa("커피"))
    }
}
