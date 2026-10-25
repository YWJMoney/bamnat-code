package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleDraft
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PAYS-13] 규칙 조립기의 모든 대상 × 방식: 문장 → [RuleText.read] → [RuleText.apply] → 계산. 사용자 말 "동작은 되지만 적용이 안
 * 되는 것 같다"(2026-10-25)를 전수로 확인해요. 그 대상의 근무가 있는 달에 금액이 바뀌지 않는 조합은 0이어야 해요.
 */
class RuleBuilderRoundTripTest {
    private val oct = YearMonth.of(2026, 10)
    private fun t(h: Int) = LocalTime.of(h, 0)
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private val office = ShiftType(10, "사무", "사", 0xFF1E88E5, t(9), t(18), 60, t(12))
    private val off = ShiftType(99, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val salary = PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_508_000))))

    /**
     * 월~금 사무 from 9/28, 주말 쉼. 10/9(금, 한글날) 사무 근무, 10/10(토) 특근 09~13, 10/11(일) 대근 09~18, 10/13 잔업 18~23
     * (연장 5시간, 22~23 야간).
     */
    private val month = ScheduleResolver(
        listOf(office, off),
        WorkPattern.Rotation(LocalDate.of(2026, 9, 28), List(5) { office.id } + listOf(off.id, off.id)),
        listOf(
            DayEntry(d(10), extras = listOf(ExtraEntry(ExtraKind.SPECIAL_ID, t(9), t(13)))),
            DayEntry(d(11), extras = listOf(ExtraEntry(ExtraKind.COVER_ID, t(9), t(18), breakMinutes = 60))),
            DayEntry(d(13), extras = listOf(ExtraEntry(ExtraKind.OVERTIME_ID, t(18), t(23)))),
        ),
    )

    private fun total(p: PayProfile) = PayCalculator().calculate(p, oct, month::resolve).total

    /** Values that differ from the law, so a rule read right always moves the month. */
    private fun draftOf(target: RuleDraft.Target, way: RuleDraft.Way): RuleDraft {
        val d = RuleDraft().withTarget(target).withWay(way)
        return when (way) {
            RuleDraft.Way.PREMIUM -> d.copy(percent = 100.0)
            RuleDraft.Way.ORDINARY -> d.copy(percent = 250.0)
            else -> d
        }
    }

    @Test
    fun `every target and way is read back and changes the month`() {
        val base = total(salary)
        val failures = mutableListOf<String>()
        var combos = 0
        for (target in RuleDraft.Target.entries) for (way in target.ways) {
            combos++
            val sentence = draftOf(target, way).sentence()
            val found = RuleText.read(sentence, fiveOrMore = true)
            if (found.isEmpty()) { failures += "$sentence: 못 읽음"; continue }
            if (found.any { it.confidence < 0.6 }) failures += "$sentence: 확실도 ${found.map { it.confidence }}"
            val applied = RuleText.apply(salary, found.map { it.change }, oct)
            if (total(applied) == base) failures += "$sentence: 계산에 안 들어감 (${found.map { it.change.summary }})"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // 대근·특근·잔업 4가지, 연장·야간·휴일 1가지, 토·일·빨간날 3가지.
        assertEquals(4 * 3 + 3 + 3 * 3, combos)
    }

    @Test
    fun `빨간날 문장은 빨간날 선택으로 들어감`() {
        fun mode(s: String) = RuleText.apply(salary, RuleText.read(s).map { it.change }, oct).rules.holidayWork
        assertEquals(HolidayWork.AS_EXTRA, mode("공휴일 근무는 특근처럼 계산"))
        assertEquals(HolidayWork.SUBSTITUTE, mode("공휴일은 휴일대체로 다른 날 쉼"))
        assertEquals(HolidayWork.SHIFT_NORMAL, mode("빨간날도 교대제라 평일처럼"))
        assertEquals(HolidayWork.PREMIUM, mode("공휴일 근무는 휴일근로로 쳐줌"))
        assertTrue(RuleText.read("빨간날도 교대제라 평일처럼").single().change is RuleChange.HolidayMode)
        // 특근처럼 judges a night shift by its start.
        assertTrue(RuleText.apply(salary, RuleText.read("공휴일 근무는 특근처럼 계산").map { it.change }, oct).rules.holidayByShiftStart)
    }

    @Test
    fun `a rule put in twice is the same as once`() {
        val s = draftOf(RuleDraft.Target.COVER, RuleDraft.Way.PER_TIME).sentence()
        val once = RuleText.apply(salary, RuleText.read(s).map { it.change }, oct)
        val twice = RuleText.apply(once, RuleText.read(s).map { it.change }, oct)
        assertEquals(total(once), total(twice))
        assertNotEquals(total(salary), total(once))
    }
}
