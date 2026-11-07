package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.ExtraBasis
import com.bamnat.core.pay.ExtraRule
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.schedule.RestCheck
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [PAYX-54] 1.38 PC 12.4.1: 교육 09~13 on a 09~18 일근 is not 4 more hours. It was "이 날 합계 12시간", "+4h" and a red
 * "휴식 11시간 미만" (the two pieces overlapping), and the 4 hours went into pay as 연장.
 */
class SchoolingInShiftTest {
    private val nov = YearMonth.of(2026, 11)
    private val work = ShiftType(1, "일근", "일", 0xFF1E88E5, LocalTime.of(9, 0), LocalTime.of(18, 0), 60, LocalTime.of(12, 0))
    private val off = ShiftType(9, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val school = ExtraKind(50, "교육", "교", 0xFF7048E8, ExtraTiming.AfterShift(240))
    private val date = LocalDate.of(2026, 11, 10)
    private fun resolver(vararg e: ExtraEntry) = ScheduleResolver(listOf(work, off),
        WorkPattern.Rotation(LocalDate.of(2026, 11, 1), listOf(1L)),
        listOf(DayEntry(date, extras = e.toList())), ExtraKinds(ExtraKinds.DEFAULTS + school))
    private fun t(h: Int) = LocalTime.of(h, 0)

    @Test fun schoolingInsideTheShiftAddsNoHours() {
        val d = resolver(ExtraEntry(50, t(9), t(13))).resolve(date)
        assertEquals(0, d.extraMinutes)
        assertEquals(8 * 60, d.totalMinutes)
        assertEquals(4 * 60, d.extras.single().insideShiftMinutes)
        // One stretch of work, so no rest problem between the 교육 and the 일근.
        assertTrue(RestCheck.blocks(d).size == 1)
        assertTrue(RestCheck.check(listOf(d)).none { it.date == date && it.kind == RestCheck.Kind.SHORT_REST })
    }

    @Test fun schoolingPastTheShiftCountsOnlyThePartOutside() {
        val d = resolver(ExtraEntry(50, t(16), t(20))).resolve(date)
        assertEquals(2 * 60, d.extraMinutes)
        assertEquals(2 * 60, d.extras.single().insideShiftMinutes)
        assertEquals(t(18), d.extras.single().from!!.toLocalTime())
        // Apart from the shift: as before.
        assertEquals(4 * 60, resolver(ExtraEntry(50, t(19), t(23))).resolve(date).extraMinutes)
    }

    @Test fun schoolingInsideTheShiftIsNotPaidAsOvertime() {
        val profile = WorkForms.apply(WorkForm.OFFICE, 2_430_000, LocalDate.of(2026, 11, 1))
        val plain = PayCalculator().calculate(profile, nov, resolver()::resolve, checks = false)
        val inside = PayCalculator().calculate(profile, nov, resolver(ExtraEntry(50, t(9), t(13)))::resolve, checks = false)
        assertEquals(plain.total, inside.total)
        // By the 243 시수 with 교육 수당 1회: only the amount each time goes on.
        val ruled = profile.copy(rules = profile.rules.copy(extra = profile.rules.extra +
            (50L to ExtraRule(basis = ExtraBasis.HOURLY_RATE, multiplier = 1.0, monthlyHours = 243, bonus = 10_000))))
        val r = PayCalculator().calculate(ruled, nov, resolver(ExtraEntry(50, t(9), t(13)))::resolve, checks = false)
        assertEquals(plain.total + 10_000, r.total)
    }

    /** [PAYX-55] 1.38 PC 12.5: the month's 교육 line says the numbers it was paid on, not "4시간 × 0%". */
    @Test fun theSchoolingLineSaysItsNumbers() {
        val profile = WorkForms.apply(WorkForm.OFFICE, 2_430_000, LocalDate.of(2026, 11, 1))
        val unit = ExtraRule.lawUnit(0.5, 0.5, 0.5, restAsHoliday = false).copy(monthlyHours = 243)
        val ruled = profile.copy(rules = profile.rules.copy(extra = profile.rules.extra + (50L to unit)))
        val r = PayCalculator().calculate(ruled, nov, resolver(ExtraEntry(50, t(19), t(23)))::resolve, checks = false)
        val line = r.lines.singleOrNull { it.kind == com.bamnat.core.pay.LineKind.EXTRA } ?: error(r.lines.joinToString { it.label + "|" + it.formula })
        assertTrue(line.formula, "4시간 ×" in line.formula && "243" in line.formula && "0%" !in line.formula.replace("50%", ""))
        assertTrue(line.hourly > 0)
    }
}
