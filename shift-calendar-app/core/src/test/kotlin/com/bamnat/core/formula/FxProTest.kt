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

    @Test fun oneOfEachIsATaste() {
        // 사용자 18:38 '하나씩 무료 맛보기': the first 숫자 값 칸 and the first 단추 위젯 (counting it or doing a 1.38 thing) are free.
        val tick = widget.copy(act = LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0))
        val l = Logic(listOf(memo, drink, tick), listOf(night), listOf(water))
        assertTrue(FxPro.uses(l).isEmpty())
        assertEquals(l, FxPro.free(l))
    }

    @Test fun theSecondOfEachIsPro() {
        val juice = LogicValue("주스", ValueReset.DAY)
        val widget2 = widget.copy(label = "배달 +2", givenId = "b9")
        val countJuice = LogicButton("주스", "🧃", ButtonPlace.DAY, LogicAct(LogicVerb.VAL_ADD, "주스", by = 1.0), givenId = "b8")
        val l = Logic(listOf(memo, drink, widget, widget2, countJuice), listOf(night.copy(act = night.act.copy(then = listOf(drink.id)))), listOf(water, juice))
        assertEquals(setOf(FxPro.Part.VALUES, FxPro.Part.VALUE_ACT, FxPro.Part.OUTSIDE, FxPro.Part.NOTIFY_BUTTONS), FxPro.uses(l))
        val free = FxPro.free(l)
        // The old button, the taste and the 알림 stay; the 알림 loses only its buttons; the 값 칸 list is kept so its numbers are not lost.
        assertEquals(listOf(memo, drink, widget), free.buttons)
        assertEquals(listOf(night), free.rules)
        assertEquals(listOf(water, juice), free.values)
        assertEquals(3, FxPro.resting(l))
        // A 시각 값 칸 is never the taste.
        assertEquals(setOf(FxPro.Part.VALUES), FxPro.uses(Logic(values = listOf(water.copy(kind = ValueKind.TIME)))))
    }

    @Test fun againAloneIsFree() {
        val l = Logic(rules = listOf(night.copy(act = night.act.copy(then = listOf("again10")))))
        assertTrue(FxPro.uses(l).isEmpty())
        val both = Logic(listOf(memo), listOf(night.copy(act = night.act.copy(then = listOf("again10", memo.id)))))
        assertEquals(setOf(FxPro.Part.NOTIFY_BUTTONS), FxPro.uses(both))
        assertEquals(listOf("again10"), FxPro.free(both).rules.single().act.then)
    }

    @Test fun bundleTwoDevicesArePro() {
        val chain = night.copy(more = listOf(LogicAct(LogicVerb.MEMO_LINE, "야간")))
        assertEquals(setOf(FxPro.Part.CHAIN), FxPro.uses(Logic(rules = listOf(chain))))
        val onValue = LogicRule("다 마셨어요", RuleTrigger.VALUE, of = "물 잔", goal = true, act = LogicAct(LogicVerb.NOTIFY, "목표!"))
        assertEquals(setOf(FxPro.Part.VALUE_RULE), FxPro.uses(Logic(rules = listOf(onValue), values = listOf(water))))
        val every = LogicRule("스트레칭", RuleTrigger.EVERY, min = 60, inWork = true, act = LogicAct(LogicVerb.NOTIFY, "쉬어요"))
        assertEquals(setOf(FxPro.Part.TIME), FxPro.uses(Logic(rules = listOf(every))))
        val later = night.copy(act = night.act.copy(after = 30))
        assertEquals(setOf(FxPro.Part.TIME), FxPro.uses(Logic(rules = listOf(later))))
        val history = memo.copy(show = "연속(\"물 잔\") >= 3")
        assertEquals(setOf(FxPro.Part.HISTORY), FxPro.uses(Logic(listOf(history))))
        val branch = memo.copy(cond = "근무종류 = \"야간\"", otherwise = listOf(LogicAct(LogicVerb.SCREEN, "memo")))
        assertEquals(setOf(FxPro.Part.CHAIN), FxPro.uses(Logic(listOf(branch))))
        assertTrue(FxPro.free(Logic(listOf(branch), listOf(chain, every))).let { it.buttons.isEmpty() && it.rules.isEmpty() })
    }

    @Test fun bundleTwoRoundTrips() {
        val r = LogicRule("물", RuleTrigger.VALUE, of = "물 잔", goal = true, cond = "주말", act = LogicAct(LogicVerb.NOTIFY, "목표", after = 10),
            more = listOf(LogicAct(LogicVerb.VAL_TEXT, "기분", text = "좋아요")), otherwise = listOf(LogicAct(LogicVerb.VAL_STAMP, "때")))
        val e = LogicRule("쉬기", RuleTrigger.EVERY, min = 90, at = "09:00", until = "18:00", act = LogicAct(LogicVerb.NOTIFY, "쉬어요"))
        val b = memo.copy(givenId = memo.id, cond = "주말", more = listOf(drink.act), otherwise = listOf(LogicAct(LogicVerb.SCREEN, "memo")))
        val l = Logic(listOf(b), listOf(r, e), listOf(water))
        val back = Logic.from(l.toValues())
        assertEquals(l.rules, back.rules)
        assertEquals(l.buttons, back.buttons)
        // '아니면' needs a 조건; 몇 분마다 stays 30분~4시간.
        assertTrue(LogicRule.from(r.copy(cond = "").toMap())!!.otherwise.isEmpty())
        assertEquals(LogicRule.EVERY_MIN, LogicRule.from(e.copy(min = 5).toMap())!!.min)
        assertNull(LogicRule.from(r.copy(of = null).toMap()))
    }

    @Test fun newLooksArePro() {
        assertTrue(FxPro.looks(listOf("calTheme", "shiftLook")).isEmpty())
        assertEquals(setOf(FxPro.Part.LOOK), FxPro.looks("parts", mapOf("parts" to listOf(mapOf("id" to "cal.cell"), mapOf("id" to "night.clock")))))
        assertTrue(FxPro.looks("parts", mapOf("parts" to listOf(mapOf("id" to "cal.cell"), mapOf("id" to "nightly")))).isEmpty())
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

    @Test fun valuesKeepTheirPast() {
        val d = LocalDate.of(2026, 11, 7)
        var k: ValueBook.Kept? = null
        // 11/3 8, 11/4 8, 11/5 3, 11/6 8, 11/7 9 (today)
        listOf(8.0, 8.0, 3.0, 8.0, 9.0).forEachIndexed { i, n -> k = ValueBook.add(k, water, d.minusDays(4L - i), n) }
        val s = ValueSeries(water, k, d, d.atTime(20, 0))
        assertEquals(8.0, s.back(1)!!, 0.0)
        assertEquals(3.0, s.back(2)!!, 0.0)
        assertNull(s.back(9))
        assertEquals(2, s.streak())
        assertEquals(17.0, s.total(2), 0.0)
        assertEquals(36.0, s.total(7), 0.0) // nothing kept counts as 0
        assertEquals(k, ValueBook.decode(ValueBook.encode(mapOf("물 잔" to k!!)))["물 잔"])
        val scope = object : FormulaScope {
            override fun get(name: String): Value? = null
            override fun series(name: String) = if (name == "물 잔") s else null
        }
        assertEquals(8.0, (Formula.eval("지난값(\"물 잔\")", scope) as Value.Num).v, 0.0)
        assertEquals(2.0, (Formula.eval("연속(\"물 잔\")", scope) as Value.Num).v, 0.0)
        assertEquals(17.0, (Formula.eval("값합계(\"물 잔\", 2)", scope) as Value.Num).v, 0.0)
        assertEquals(8.5, (Formula.eval("값평균(\"물 잔\", 2)", scope) as Value.Num).v, 0.0)
        // 시각 값 칸: 지난시간.
        val pill = LogicValue("약", ValueReset.NEVER, kind = ValueKind.TIME)
        val kp = ValueBook.set(pill, d, ValueBook.minutes(d.atTime(8, 0)))
        assertEquals(12.5, ValueSeries(pill, kp, d, d.atTime(20, 30)).hoursSince()!!, 0.0)
        assertEquals("08:00", pill.show(kp.v, d))
    }

    @Test fun aValueRoundTripsInTheLogicPiece() {
        val l = Logic(listOf(drink), values = listOf(water))
        assertEquals(l.values, Logic.from(l.toValues()).values)
        assertFalse(LogicValue.isName(""))
        assertFalse(LogicValue.isName("열세글자가넘는아주긴이름이에요"))
    }
}
