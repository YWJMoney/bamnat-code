package com.bamnat.app.pay

import com.bamnat.app.ui.pay.ItemDraft
import com.bamnat.app.ui.pay.ItemKind
import com.bamnat.app.ui.pay.MadeItem
import com.bamnat.app.ui.pay.DeductWay
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.pay.RulePay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [PAYX-50] 1.38 수당 창 하나로: what the old '수당 추가' window could do (고정 연장수당, 금액 모름, 비과세 짐작) is in the one
 * builder, and each chip still saves as the app keeps it: 매달·일한 날마다 as a pay item, the rest as the company's own rule.
 */
class OneAddWindowTest {
    private val start = LocalDate.of(2026, 11, 1)

    private fun draft(kind: ItemKind, name: String = "", amount: Long? = null) =
        ItemDraft(kind, "t").apply { this.name = name; this.amount = amount }

    @Test fun aFixedOvertimeAllowanceIsAPayItemNeverOrdinary() {
        val d = draft(ItemKind.MONTHLY, "고정 연장수당", 300_000).apply { ordinary = true; fixedOtHours = 20 }
        val item = (d.made(start) as MadeItem.Item).item
        assertEquals(Cadence.MONTHLY, item.cadence)
        assertEquals(20 * 60, item.fixedOvertimeMinutes)
        assertFalse(item.ordinary)
        assertTrue(item.taxable)
    }

    @Test fun aMealAllowanceIsGuessedTaxFreeAsTheOldWindowDid() {
        val item = (draft(ItemKind.MONTHLY, "식대", 200_000).made(start) as MadeItem.Item).item
        assertFalse(item.taxable)
        assertEquals(0, item.fixedOvertimeMinutes)
    }

    @Test fun anyMonthlyAllowanceCanBeMadeTaxFree() {
        // [PAYX-52] 교통비 is taxed unless the payslip says otherwise; the switch says so.
        assertTrue((draft(ItemKind.MONTHLY, "교통비", 100_000).made(start) as MadeItem.Item).item.taxable)
        val free = draft(ItemKind.MONTHLY, "교통비", 100_000).apply { taxable = false }.made(start) as MadeItem.Item
        assertFalse(free.item.taxable)
        assertFalse((draft(ItemKind.MONTHLY, "연구보조비", 200_000).made(start) as MadeItem.Item).item.taxable)
    }

    @Test fun aChangedMonthlyDeductionKeepsItsMonths() {
        val nov = java.time.YearMonth.of(2026, 11)
        val rule = CustomRule("h", "사택관리비", com.bamnat.core.pay.RuleWhen(), RulePay.Monthly(50_000), deduct = true, byMonth = mapOf(nov to 83_000L))
        val again = ItemDraft.of(rule)!!.apply { amount = 55_000 }.made(start) as MadeItem.Rule
        assertEquals(mapOf(nov to 83_000L), again.rule.byMonth)
        assertEquals(RulePay.Monthly(55_000), again.rule.pay)
    }

    @Test fun anAllowanceWithNoAmountGoesInAsUnknown() {
        val d = draft(ItemKind.MONTHLY, "자격수당")
        assertNull(d.made(start))
        val item = (d.madeOrAsked(start) as MadeItem.Item).item
        assertTrue(item.amount.entries.isEmpty())
        assertEquals(ItemShare.PERSONAL, item.share)
        assertEquals("자격수당", item.name)
    }

    @Test fun aCompanyWideAllowanceNeedsItsAmount() {
        val d = draft(ItemKind.MONTHLY, "교통비").apply { who = ItemShare.FIXED; whoPicked = true }
        assertNull(d.madeOrAsked(start))
    }

    @Test fun theFixedOvertimeHoursOnlyGoOnAMonthlyItem() {
        val d = draft(ItemKind.PER_DAY, "식대", 8_000).apply { fixedOtHours = 20 }
        val item = (d.made(start) as MadeItem.Item).item
        assertEquals(Cadence.PER_WORK_DAY, item.cadence)
        assertEquals(0, item.fixedOvertimeMinutes)
    }

    @Test fun theOtherChipsStillSaveAsRules() {
        assertTrue((draft(ItemKind.PER_SHIFT, "나이트 수당", 20_000).made(start) as MadeItem.Rule).rule.pay is RulePay.PerTime)
        assertTrue((draft(ItemKind.PER_HOUR, "심야 수당", 2_000).made(start) as MadeItem.Rule).rule.pay is RulePay.PerHour)
        val deduct = draft(ItemKind.DEDUCT, "기숙사비", 50_000).apply { deductWay = DeductWay.MONTHLY }.made(start) as MadeItem.Rule
        assertTrue(deduct.rule.deduct)
        // A deduction is never asked as 금액 모름.
        assertNull(draft(ItemKind.DEDUCT, "기숙사비").madeOrAsked(start))
    }

    @Test fun aChangedItemKeepsItsFixedOvertimeAndTaxFree() {
        val old = (draft(ItemKind.MONTHLY, "고정OT", 300_000).apply { fixedOtHours = 30 }.made(start) as MadeItem.Item).item.copy(taxable = false)
        val again = ItemDraft.ofItem(old)!!
        assertEquals(30, again.fixedOtHours)
        val item = (again.made(start) as MadeItem.Item).item
        assertEquals(30 * 60, item.fixedOvertimeMinutes)
        assertFalse(item.taxable)
        assertTrue(item.name.length <= 20 && CustomRule.MAX_NAME > 0)
    }

    /** [PAYX-53] 1.38 PC 12.4.1: an item with no name gets a number of its own, so the list tells them apart. */
    @Test fun anUnnamedItemIsNumbered() {
        assertEquals("매달 수당 1", (draft(ItemKind.MONTHLY, amount = 50_000).made(start) as MadeItem.Item).item.name)
        val second = draft(ItemKind.MONTHLY, amount = 50_000).apply { taken = setOf("매달 수당 1", "식대") }
        assertEquals("매달 수당 2", (second.made(start) as MadeItem.Item).item.name)
        val rule = draft(ItemKind.DEDUCT, amount = 30_000).apply { taken = setOf("차감 1", "차감 2") }
        assertEquals("차감 3", (rule.made(start) as MadeItem.Rule).rule.name)
        assertEquals("기숙사비", (draft(ItemKind.DEDUCT, "기숙사비", 30_000).made(start) as MadeItem.Rule).rule.name)
        assertEquals("매달 수당 1", (draft(ItemKind.MONTHLY).madeOrAsked(start) as MadeItem.Item).item.name)
    }
}
