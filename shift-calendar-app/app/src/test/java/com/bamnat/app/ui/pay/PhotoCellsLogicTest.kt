package com.bamnat.app.ui.pay

import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RulePay
import com.bamnat.core.pay.RuleWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** [PHOTO-06] 1.29 AQ: a rule read from a photo opens in the same builder as a rule made by hand, and comes back as a change. */
class PhotoCellsLogicTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun cellsOpenInTheBuilderAndComeBack() {
        assertTrue(PhotoCellsLogic.editable(RuleChange.Premium(PremiumTarget.NIGHT, 0.5)))
        val monthly = RuleChange.Allowance("자격수당", 57_500, Cadence.MONTHLY, ordinary = true)
        assertTrue(PhotoCellsLogic.editable(monthly))
        val (rule, item) = PhotoCellsLogic.toEdit(monthly, nov)!!
        assertNull(rule)
        assertNotNull(item)
        assertEquals(57_500L, item!!.amount.on(nov.atDay(1)).value)
        // Back from the builder as the same allowance.
        assertEquals(monthly, PhotoCellsLogic.change(MadeItem.Item(item), emptyMap(), nov))
        val perShift = RuleChange.Custom(CustomRule("r", "교대수당", RuleWhen(), RulePay.PerTime(10_000)))
        assertEquals(perShift.rule, PhotoCellsLogic.toEdit(perShift, nov)!!.first)
        assertEquals(perShift, PhotoCellsLogic.change(MadeItem.Rule(perShift.rule), emptyMap(), nov))
        // Cells the builder does not make stay as read: the user ticks or removes them.
        assertFalse(PhotoCellsLogic.editable(RuleChange.TaxFamily(3, 0)))
        assertFalse(PhotoCellsLogic.editable(RuleChange.OrdinaryHourly(21_618.04, true)))
    }
}
