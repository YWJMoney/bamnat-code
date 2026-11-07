package com.bamnat.app.pay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.pay.DeductWay
import com.bamnat.app.ui.pay.ItemDraft
import com.bamnat.app.ui.pay.ItemKind
import com.bamnat.app.ui.pay.MadeItem
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.InsuranceLine
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.39 G3: what the 수당 추가 window saves for 이번 달만(소급분)·세후 공제·비과세 rules ([PAYX-56][PAYX-57]), that it all survives the
 * pay file, and A16 (1.37.0 → 1.39 덮어 깔기): a 1.37 pay file with 야간근무마다·고정 연장수당·금액 모름 수당 reads and pays as before.
 */
@RunWith(AndroidJUnit4::class)
class PayAccuracy139AppTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val nov = YearMonth.of(2026, 11)
    private fun draft(kind: ItemKind, name: String, amount: Long) = ItemDraft(kind, "t").apply { this.name = name; this.amount = amount }

    @Test fun oneMonthMakesARuleForThatMonth() {
        val d = draft(ItemKind.MONTHLY, "소급분", 120_000).apply { since = nov; until = nov }
        assertTrue(d.oneMonth)
        val rule = (d.made(start) as MadeItem.Rule).rule
        assertEquals(nov, rule.from); assertEquals(nov, rule.until)
        assertTrue(rule.oneMonth)
        assertTrue("소급분은 세금이 붙어요", rule.taxable)
        // A 식대 put in for one month keeps its 비과세 (it used to be lost as a rule).
        val meal = (draft(ItemKind.MONTHLY, "식대", 200_000).apply { since = nov; until = nov }.made(start) as MadeItem.Rule).rule
        assertFalse(meal.taxable)
        val one = (draft(ItemKind.DEDUCT, "소급분(공제)", 30_000).apply { deductWay = DeductWay.MONTHLY; since = nov; until = nov }.made(start) as MadeItem.Rule).rule
        assertTrue(one.deduct && one.oneMonth)
    }

    @Test fun aDeductionIsGuessedAfterTaxByItsName() {
        fun made(name: String) = (draft(ItemKind.DEDUCT, name, 50_000).apply { deductWay = DeductWay.MONTHLY }.made(start) as MadeItem.Rule).rule
        assertEquals(true, made("사택관리비").afterTax)
        assertTrue(made("사택관리비").paidAfterTax)
        assertEquals(false, made("지각 공제").afterTax)
        // The switch wins.
        val d = draft(ItemKind.DEDUCT, "기숙사비", 50_000).apply { deductWay = DeductWay.MONTHLY; afterTax = false }
        assertFalse((d.made(start) as MadeItem.Rule).rule.paidAfterTax)
        // A day-by-day deduction is never after tax (it is taken off the pay).
        val perDay = (draft(ItemKind.DEDUCT, "식권", 3_000).apply { deductWay = DeductWay.PER_DAY }.made(start) as MadeItem.Rule).rule
        assertNull(perDay.afterTax)
        assertFalse(perDay.paidAfterTax)
    }

    @Test fun aRuleFromBefore139StaysTheOldWayWhenChanged() {
        val old = CustomRule("h", "사택관리비", RuleWhen(), RulePay.Monthly(50_000), deduct = true)
        assertTrue(old.mayBeAfterTax)
        val again = (ItemDraft.of(old)!!.apply { amount = 55_000 }.made(start) as MadeItem.Rule).rule
        assertEquals(false, again.afterTax)
    }

    @Test fun newFieldsSurviveThePayFile() {
        val p = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(start, 3_000_000))),
            customRules = listOf(
                CustomRule("a", "사택관리비", RuleWhen(), RulePay.Monthly(50_000), deduct = true, afterTax = true),
                CustomRule("b", "식대", RuleWhen(), RulePay.Monthly(200_000), from = nov, until = nov, taxable = false),
                CustomRule("c", "기숙사비", RuleWhen(), RulePay.Monthly(40_000), deduct = true),
            ),
            insuranceOff = setOf(InsuranceLine.PENSION))
        val back = PayCodec.decode(PayCodec.encode(p))
        assertEquals(p, back)
        assertNull("1.38 규칙은 afterTax 없이 그대로", back.customRules[2].afterTax)
        // A file without the new keys (1.38) reads as before.
        val json = PayCodec.encode(p.copy(insuranceOff = emptySet()))
        assertFalse(json.has("insuranceOff"))
    }

    /** A16: 1.37.0's pay file (no key added since 1.37) with 야간근무마다·고정 연장수당·금액 모름 수당. */
    @Test fun a137PayFileReadsAndPaysAsBefore() {
        val night = ShiftType(2, "야간", "N", 0xFF3949AB, LocalTime.of(22, 0), LocalTime.of(7, 0), 60, LocalTime.of(3, 0))
        val day = ShiftType(1, "주간", "D", 0xFF1E88E5, LocalTime.of(9, 0), LocalTime.of(18, 0), 60, LocalTime.of(12, 0))
        val p = PayProfile(Occupation.NURSE, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(start, 2_800_000))),
            items = listOf(
                PayItem("n", "나이트 수당", ValueTimeline(listOf(DatedValue(start, 30_000))), Cadence.PER_NIGHT_SHIFT),
                PayItem("f", "고정 연장수당", ValueTimeline(listOf(DatedValue(start, 300_000))), Cadence.MONTHLY, fixedOvertimeMinutes = 20 * 60),
                PayItem("u", "자격수당", ValueTimeline(), Cadence.MONTHLY),
            ),
            estimatedFields = setOf(Unknowns.item("u")))
        val json = PayCodec.encode(p)
        // Keys 1.38·1.39 may add are not in a 1.37 file.
        listOf("insuranceOff").forEach(json::remove)
        val read = PayCodec.decode(json)
        assertEquals(p, read)
        val resolver = ScheduleResolver(listOf(day, night), WorkPattern.Rotation(LocalDate.of(2026, 11, 1), listOf(1L, 2L, 2L)), emptyList())
        val m = PayCalculator().calculate(read, nov, resolver::resolve, checks = false)
        val nights = (1..30).count { resolver.resolve(nov.atDay(it)).shiftType?.id == 2L }
        assertEquals(nights * 30_000L, m.lines.filter { it.label.startsWith("나이트 수당") }.sumOf { it.amount })
        assertTrue("금액 모름 수당은 합계에서 빠지고 확인 필요로", m.missing.any { it.id == "u" })
        assertTrue(m.lines.any { it.label.startsWith("고정 연장수당") })
    }
}
