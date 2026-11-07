package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 1.39 [FX-01][FX-02][FX-03] 프로 문·하루 써 보기·값 칸: 1.38까지 있던 것은 무료 그대로, 1.39에 새로 연 기능 장치만 프로.
 */
class FxProTest {
    private val water = LogicValue("물 잔", ValueReset.DAY, goal = 8.0, unit = "잔")
    private val drink = LogicButton("물 한 잔", "💧", ButtonPlace.DAY, LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0))
    private val memo = LogicButton("오늘 기록", "📝", ButtonPlace.DAY, LogicAct(LogicVerb.MEMO_TEMPLATE, "하루 기록"))
    private val widget = LogicButton("배달 +1", "🛵", ButtonPlace.WIDGET, LogicAct(LogicVerb.ADD_INCOME, "배달", amount = 3000))
    private val night = LogicRule("야간 알림", RuleTrigger.START, min = -60, act = LogicAct(LogicVerb.NOTIFY, "야간이에요"))

    @Test fun oldThingsStayFree() {
        val old = Logic(listOf(memo), listOf(night))
        assertFalse(FxPro.needs(old))
        assertEquals(old, FxPro.free(old))
        assertEquals(0, FxPro.resting(old))
    }

    @Test fun newDevicesNeedPro() {
        val l = Logic(listOf(memo, drink, widget), listOf(night.copy(act = night.act.copy(then = listOf(drink.id)))), listOf(water))
        assertEquals(setOf(FxPro.Part.VALUES, FxPro.Part.VALUE_ACT, FxPro.Part.OUTSIDE, FxPro.Part.NOTIFY_BUTTONS), FxPro.uses(l))
        val free = FxPro.free(l)
        // The old button and the 알림 stay; the 알림 loses only its buttons; the 값 칸 list is kept so its numbers are not lost.
        assertEquals(listOf(memo), free.buttons)
        assertEquals(listOf(night), free.rules)
        assertEquals(listOf(water), free.values)
        assertEquals(3, FxPro.resting(l))
    }

    @Test fun aFormulaReadingAValueIsPro() {
        assertTrue(FxPro.readsValue("값(\"물 잔\") >= 8"))
        assertFalse(FxPro.readsValue("야간"))
        assertTrue(FxPro.readsValueText("오늘 {값(\"물 잔\")}잔"))
        val l = Logic(listOf(memo.copy(show = "값(\"물 잔\") < 8")))
        assertEquals(setOf(FxPro.Part.VALUE_READ), FxPro.uses(l))
        assertTrue(FxPro.free(l).buttons.isEmpty())
    }

    @Test fun tasteRunsADayOncePerWork() {
        val at = Instant.parse("2026-11-07T09:00:00Z")
        val s0 = FxTaste.State()
        assertEquals(FxTaste.Status.Ready, FxTaste.status(s0, "AB12CD", at))
        val s1 = FxTaste.start(s0, "AB12CD", at)!!
        assertTrue(s1.open(at.plusSeconds(23 * 3600)))
        assertFalse(s1.open(at.plusSeconds(24 * 3600)))
        assertTrue(FxTaste.status(s1, "AB12CD", at.plusSeconds(3600)) is FxTaste.Status.Using)
        assertTrue(FxTaste.status(s1, "AB12CD", at.plusSeconds(25 * 3600)) is FxTaste.Status.Used)
        assertNull("once per work", FxTaste.start(s1, "AB12CD", at.plusSeconds(30 * 3600)))
        assertEquals(s1, FxTaste.decode(FxTaste.encode(s1)))
        assertEquals(FxTaste.State(), FxTaste.decode("not json"))
    }

    @Test fun valuesCountAndStartAgain() {
        val d = LocalDate.of(2026, 11, 7)
        val k1 = ValueBook.add(null, water, d, 1.0)
        val k2 = ValueBook.add(k1, water, d, 1.0)
        assertEquals(2.0, k2.v, 0.0)
        assertEquals("2/8잔", water.show(k2.v))
        // A new day: back to its start.
        assertEquals(0.0, ValueBook.now(k2, water, d.plusDays(1)), 0.0)
        val week = water.copy(reset = ValueReset.WEEK)
        val kw = ValueBook.add(null, week, d, 3.0) // Saturday
        assertEquals(3.0, ValueBook.now(kw, week, d.plusDays(1)), 0.0) // Sunday, same week
        assertEquals(0.0, ValueBook.now(kw, week, d.plusDays(2)), 0.0) // Monday
        assertEquals(ValueBook.LIMIT, ValueBook.add(null, water, d, 1e12).v, 0.0)
        val all = mapOf("물 잔" to k2, "배달" to ValueBook.Kept(12.0, ""))
        assertEquals(all, ValueBook.decode(ValueBook.encode(all)))
    }

    @Test fun aValueRoundTripsInTheLogicPiece() {
        val l = Logic(listOf(drink), values = listOf(water))
        assertEquals(l.values, Logic.from(l.toValues()).values)
        assertFalse(LogicValue.isName(""))
        assertFalse(LogicValue.isName("열세글자가넘는아주긴이름이에요"))
    }
}
