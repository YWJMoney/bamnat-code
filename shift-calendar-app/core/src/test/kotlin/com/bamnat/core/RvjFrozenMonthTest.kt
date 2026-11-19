package com.bamnat.core

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CalcSnapshot
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CloseAsk
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.DaySplit
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayPeriod
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-18] 뿌리 고침 6 '굳은 달': 검토 PC-02 (날짜별 합 ≠ 합계), PAYU-06·I05c-01 (마감한 달이 화면마다 다른 값),
 * U05-01·I05a-02 (급여일이 지나도 마감을 묻지 않음) 재현 시험.
 */
class RvjFrozenMonthTest {
    private fun t(h: Int) = LocalTime.of(h, 0)
    private val day = ShiftType(1, "주간", "주", 0xFF000000, t(9), t(18), 60, t(12))
    private val off = ShiftType(9, "휴무", "휴", 0xFF000000, null, null, isOff = true)
    private val weekdays = ScheduleResolver(listOf(day, off),
        WorkPattern.Rotation(LocalDate.of(2026, 11, 2), List(5) { day.id } + listOf(off.id, off.id)), emptyList())
    private val nov = YearMonth.of(2026, 11)
    private val meal = PayItem("meal", "식대", ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 8_000L))), Cadence.PER_WORK_DAY)
    private val monthly = PayProfile(
        occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 12_000L * 209))),
        items = listOf(meal),
    )
    private val hourly = PayProfile(
        occupation = Occupation.PART_TIME, wageType = WageType.HOURLY, fiveOrMoreEmployees = false,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 10_320L))),
        items = listOf(meal),
    )
    private fun pay(p: PayProfile, month: YearMonth = nov) = PayCalculator().calculate(p, month, weekdays::resolve)

    // ── PC-02: 날짜별 합 + 달로 주는 몫 = 합계 ──
    @Test fun theDaysAndTheMonthsShareAddUpToTheTotal() {
        for (p in listOf(monthly, hourly)) {
            val m = pay(p)
            val split = DaySplit.of(m)
            assertEquals(m.days.sumOf { it.amount }, split.days)
            assertEquals(m.total, split.total)
        }
        // 월급: 기본급과 주휴는 달로 주는 몫이고, 근무마다 주는 식대는 날짜에 있어요.
        val m = pay(monthly)
        val split = DaySplit.of(m)
        assertTrue(split.kinds.toString(), "기본급" in split.kinds)
        assertEquals(m.lines.single { it.label == "식대" }.amount, m.days.sumOf { d -> d.parts.filter { it.label == "식대" }.sumOf { it.amount } })
    }

    @Test fun aShiftWorkersSplitNamesWhatTheMonthPaysAndCallsRoundingRounding() {
        // 3교대 간호사(달로 주는 기본급·교대근무 수당, 날마다 야간·휴일): 날마다 원 단위로 반올림한 몇 원은 '원 단위 끝전'이에요.
        val d = ShiftType(1, "주간", "주", 0xFF000000, t(7), t(15), 60, t(12))
        val e = ShiftType(2, "저녁", "저", 0xFF000000, t(15), t(23), 60, t(19))
        val n = ShiftType(3, "야간", "야", 0xFF000000, t(23), t(7), 60, t(3))
        val preset = com.bamnat.core.pay.PayPresets.of(Occupation.NURSE)
        val p = com.bamnat.core.pay.PayPresets.profile(preset, preset.wageType, preset.baseHint, LocalDate.of(2020, 1, 1),
            preset.items.filter { it.checked }, fiveOrMore = true, holidayMode = com.bamnat.core.pay.HolidayPayMode.FULL, estimated = setOf("base"))
        val r = ScheduleResolver(listOf(d, e, n, off), WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf(1L, 1L, 2L, 2L, 3L, 3L, 9L, 9L)), emptyList())
        val m = PayCalculator().calculate(p, YearMonth.of(2026, 10), r::resolve)
        val split = DaySplit.of(m)
        assertEquals(m.total, split.total)
        assertTrue(split.kinds.toString(), "기본급" in split.kinds && "그 밖의 몫" !in split.kinds)
        assertTrue(split.kinds.toString(), split.kinds.none { it == "야간" || it == "휴일" })
    }

    /**
     * 2.0.0 [RVJ-35] 검토 PC-86: '달로 주는 몫(기본급·원 단위 끝전) 2,799,993원' read as a wrong 기본급. The share comes with its pieces
     * (기본급 2,800,000원, 끝전 −7원), which always add up to it, and its ⓘ says them in words.
     */
    @Test fun theMonthsShareComesWithPiecesThatAddUpToIt() {
        val d = ShiftType(1, "주간", "주", 0xFF000000, t(7), t(15), 60, t(12))
        val e = ShiftType(2, "저녁", "저", 0xFF000000, t(15), t(23), 60, t(19))
        val n = ShiftType(3, "야간", "야", 0xFF000000, t(23), t(7), 60, t(3))
        val preset = com.bamnat.core.pay.PayPresets.of(Occupation.NURSE)
        val nurse = com.bamnat.core.pay.PayPresets.profile(preset, preset.wageType, preset.baseHint, LocalDate.of(2020, 1, 1),
            preset.items.filter { it.checked }, fiveOrMore = true, holidayMode = com.bamnat.core.pay.HolidayPayMode.FULL, estimated = setOf("base"))
        val r = ScheduleResolver(listOf(d, e, n, off), WorkPattern.Rotation(LocalDate.of(2026, 9, 1), listOf(1L, 1L, 2L, 2L, 3L, 3L, 9L, 9L)), emptyList())
        for (m in listOf(pay(monthly), pay(hourly), PayCalculator().calculate(nurse, YearMonth.of(2026, 10), r::resolve))) {
            val split = DaySplit.of(m)
            if (split.month != 0L) {
                assertEquals(split.toString(), split.month, split.pieces.sumOf { it.second })
                assertEquals(split.toString(), split.kinds.toSet(), split.pieces.map { it.first }.toSet())
            }
        }
        val won = { v: Long -> "%,d원".format(v) }
        val baseLessRounding = DaySplit.Split(days = 1_200_000, month = 2_799_993, kinds = listOf("기본급", "원 단위 끝전"),
            pieces = listOf("기본급" to 2_800_000L, "원 단위 끝전" to -7L))
        assertEquals("기본급 2,800,000원 − 원 단위 끝전 7원", DaySplit.piecesText(baseLessRounding, won))
        assertEquals("기본급 2,800,000원 + 주휴·월 수당 300,000원",
            DaySplit.piecesText(DaySplit.Split(0, 3_100_000, listOf("기본급", "주휴·월 수당"), listOf("기본급" to 2_800_000L, "주휴·월 수당" to 300_000L)), won))
        // One piece is the line itself: no ⓘ.
        assertNull(DaySplit.piecesText(DaySplit.Split(0, 2_800_000, listOf("기본급"), listOf("기본급" to 2_800_000L)), won))
    }

    @Test fun anAllowanceWhoseAmountIsUnknownIsInNeitherTheDaysNorTheTotal() {
        // 예전: 금액 모름(짐작 8,000원) 식대가 날짜별 내역에는 더해지고 합계에서는 빠져 두 합이 달랐어요.
        val unknown = monthly.copy(estimatedFields = setOf(Unknowns.item(meal.id)))
        val m = pay(unknown)
        assertTrue(m.lines.none { it.label == "식대" })
        assertTrue(m.missing.any { it.id == meal.id })
        assertTrue(m.days.flatMap { it.parts }.none { it.label == "식대" })
        assertEquals(m.total, DaySplit.of(m).total)
        val h = pay(hourly.copy(estimatedFields = setOf(Unknowns.item(meal.id))))
        assertEquals(h.lines.filter { it.kind == LineKind.BASE }.sumOf { it.amount },
            h.days.flatMap { it.parts }.filter { it.kind == LineKind.BASE }.sumOf { it.amount })
    }

    // ── PAYU-06·I05c-01: 마감한 달은 어디서나 한 값 ──
    @Test fun aClosedMonthKeepsItsLinesDaysAndTakeHomeWhenTheRulesChange() {
        val before = pay(monthly)
        val snap = CalcSnapshot.close(before, 1L, CalcSnapshot.TakeHomeAt(net = 2_200_000, tableNet = 2_210_000, basis = "TABLE"))
        // 마감 뒤 식대가 1만 원으로 올라도 마감한 달은 그대로예요.
        val raised = monthly.copy(items = listOf(meal.copy(amount = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 10_000L))))))
        val fresh = pay(raised)
        assertTrue(fresh.total > before.total)
        val shown = snap.frozen(fresh)
        assertEquals(before.total, shown.total)
        assertEquals(before.days.sumOf { it.amount }, shown.days.sumOf { it.amount })
        assertEquals(before.total, DaySplit.of(shown).total)
        assertEquals(2_200_000L, snap.net)
        assertTrue(snap.hasDays)
        // 다시 계산하면 앞 마감은 이전 기록으로 남아요.
        val again = snap.recalculated(fresh, 2L)
        assertEquals(fresh.total, again.total)
        assertEquals(before.total, again.previous.single().total)
        assertNull(again.net)
    }

    @Test fun aMonthClosedBefore200HasNoDaysAndSaysTheDaysAreRecounted() {
        val before = pay(monthly)
        val old = CalcSnapshot(before.month, before.total, before.lines, before.ratesVersion, 1L)
        assertFalse(old.hasDays)
        val fresh = pay(monthly)
        val shown = old.frozen(fresh)
        assertEquals(before.total, shown.total)
        assertEquals(fresh.days, shown.days)
    }

    // ── U05-01·I05a-02: 급여일 다음 날부터 14일 동안 "이대로 마감할까요?" ──
    @Test fun theCloseQuestionComesTheDayAfterPayDayForTwoWeeks() {
        val payDay = LocalDate.of(2026, 12, 10)
        assertFalse(CloseAsk.due(nov, payDay, payDay, closed = false))
        assertTrue(CloseAsk.due(nov, payDay, payDay.plusDays(1), closed = false))
        assertTrue(CloseAsk.due(nov, payDay, payDay.plusDays(14), closed = false))
        assertFalse(CloseAsk.due(nov, payDay, payDay.plusDays(15), closed = false))
        assertFalse(CloseAsk.due(nov, payDay, payDay.plusDays(1), closed = true))
        // 급여일을 모르면 다음 달 1일을 기준으로.
        assertTrue(CloseAsk.due(nov, null, LocalDate.of(2026, 12, 2), closed = false))
        assertFalse(CloseAsk.due(nov, null, LocalDate.of(2026, 12, 1), closed = false))
        // 이번 달 25일 지급: 25일이 지나야 물어요(마감 전의 달을 굳히라고 하지 않아요).
        val sameMonth = PayPeriod(payDay = 25).payDate(nov)
        assertEquals(LocalDate.of(2026, 11, 25), sameMonth)
        assertFalse(CloseAsk.due(nov, sameMonth, LocalDate.of(2026, 11, 20), closed = false))
        assertTrue(CloseAsk.due(nov, sameMonth, LocalDate.of(2026, 11, 26), closed = false))
        // 월급이 들어온 것을 봤으면(입금 알림·적은 실수령) 급여일을 기다리지 않고 그날부터 물어요.
        assertTrue(CloseAsk.due(nov, payDay, LocalDate.of(2026, 12, 9), closed = false, paidOn = LocalDate.of(2026, 12, 9)))
        assertFalse(CloseAsk.due(nov, payDay, LocalDate.of(2026, 12, 8), closed = false, paidOn = LocalDate.of(2026, 12, 9)))
    }
}
