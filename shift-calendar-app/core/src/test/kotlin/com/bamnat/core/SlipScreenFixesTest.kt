package com.bamnat.core

import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [READ-38] 1.38 PC 10차: the user's 계산 방법 photo lost 휴일 8시간 넘는 몫, 기본급I came out 기본급!, missing lines sat at the bottom. */
class SlipScreenFixesTest {
    private val head = "휴일근로수당  해당시간 × 1.5 × 통상임금 / 시수\n"

    private fun premiums(row: String) = RuleText.read(head + row).map { it.change }.filterIsInstance<RuleChange.Premium>()

    @Test fun theHolidayPastEightHoursRowReadsHoweverItIsMisread() {
        for (row in listOf(
            "휴밀연장수당 해당시간 × 2 × 통상임금/시수", "휴일연잠수당 해당시간 × 2 × 통상임금/시수", "휴일언장수당 해당시간 × 2 × 통상임금/시수",
            "휴일연정수당 해당시간 × 2 × 통상임금/시수", "휴일연장수당 해당시간 × z × 통상임금/시수", "휴일연장수당 해당시간x Z x 통상임긍/시수",
        )) assertTrue(row, premiums(row).any { it.target == PremiumTarget.HOLIDAY && it.premium == 1.0 && it.over8h })
        // 휴밀연장 is never read as 연장 100%.
        assertTrue(premiums("휴밀연장수당 해당시간 × 2 × 통상임금/시수").none { it.target == PremiumTarget.OVERTIME })
    }

    @Test fun aRomanNumeralReadAsAMarkIsPutBack() {
        assertEquals("기본급I", SlipLexicon.roman("기본급!"))
        assertEquals("기본급II", SlipLexicon.roman("기본급|l"))
        assertEquals("기본급", SlipLexicon.roman("기본급"))
        assertEquals("상여금1", SlipLexicon.roman("상여금1"))
    }

    @Test fun whatTheLinesFallShortOfIsSaid() {
        val read = PayslipRead(listOf(
            SlipLine("기본급", SlipRole.BASE, 2_000_000), SlipLine("상여", SlipRole.BONUS, 300_000), SlipLine("지급계", SlipRole.GROSS, 2_500_000),
            SlipLine("소득세", SlipRole.DEDUCTION, 50_000), SlipLine("공제계", SlipRole.DEDUCTION_TOTAL, 80_000),
        ))
        assertEquals(200_000L, read.missingEarning)
        assertEquals(30_000L, read.missingDeduction)
        val whole = read.copy(lines = read.lines + SlipLine("수당", SlipRole.ALLOWANCE, 200_000) + SlipLine("주민세", SlipRole.DEDUCTION, 30_000))
        assertNull(whole.missingEarning)
        assertNull(whole.missingDeduction)
    }
}
