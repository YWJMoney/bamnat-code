package com.bamnat.core

import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Festival
import com.bamnat.core.pay.NameCheck
import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleSheet
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 갈래 L 6차: a payslip line's name looked at apart from its amount ([RVL-46], 검토 SCAN-29·I07ba-05), the same 상여 read
 * twice ([RVL-47], SCAN-31·PAYC-18), and 설·인상 read by the words around them ([RVL-48], SCAN-32).
 */
class SlipNameRuleTest {
    // ---- [RVL-46] SCAN-29 · I07ba-05 ----

    @Test fun aShortNameAStrokeFromAnotherKindKeepsItsLettersAndAsks() {
        val night = SlipLexicon.of("야근수당")!!
        assertEquals("야근수당", night.name)
        assertEquals(SlipRole.ALLOWANCE, night.role)
        assertEquals("야간수당", night.ask?.name)
        assertEquals(SlipRole.NIGHT, night.ask?.role)
        val work = SlipLexicon.of("작업수당")!!
        assertEquals("작업수당", work.name)
        assertEquals(SlipRole.ALLOWANCE, work.role)
        assertEquals("잔업수당", work.ask?.name)
        assertEquals(SlipRole.EXTRA_WORK, work.ask?.role)
    }

    @Test fun aNameOfTheSameKindIsPutRightAndALongOneStaysThePrintedOne() {
        // 숙비 is a stroke from 식비: put right as before, but the letters as read go with it, to be looked at.
        val lodging = SlipLexicon.of("숙비")!!
        assertEquals("식비", lodging.name)
        assertEquals("숙비", lodging.read)
        assertNull(lodging.ask)
        // A long name a stroke off is the printed one (연장 in 연잔근로수당), as 1.39 read it.
        val over = SlipLexicon.of("연잔근로수당")!!
        assertEquals("연장근로수당", over.name)
        assertEquals(SlipRole.OVERTIME, over.role)
        assertNull(over.ask)
        // Read as printed: nothing to ask.
        assertEquals(0, SlipLexicon.of("야간수당")!!.distance)
        assertNull(SlipLexicon.of("야간수당")!!.ask)
    }

    private fun row(y: Int, vararg cells: Pair<String, Int>) = cells.map { (t, x) -> OcrLine(t, x, y, x + t.length * 28, y + 32) }

    /** 지급 on the left, 공제 on the right, the totals at the foot; every sum meets. */
    private fun payslip(): List<OcrLine> = listOf(
        row(100, "지급항목" to 40, "금액" to 400, "공제항목" to 700, "금액" to 1060),
        row(160, "기본급" to 40, "2,500,000" to 380, "국민연금" to 700, "112,500" to 1040),
        row(220, "야근수당" to 40, "150,000" to 380, "건강보험" to 700, "88,620" to 1040),
        row(280, "숙비" to 40, "200,000" to 380, "소득세" to 700, "58,880" to 1040),
        row(340, "지급합계" to 40, "2,850,000" to 380, "공제합계" to 700, "260,000" to 1040),
        row(400, "실수령액" to 40, "2,590,000" to 380),
    ).flatten()

    @Test fun theSumsVouchForTheAmountsNeverForTheNames() {
        val res = SlipReading.read(listOf(payslip()))
        val lines = res.read.lines
        val night = lines.single { it.amount == 150_000L }
        assertEquals("야근수당", night.name)
        assertEquals(SlipRole.ALLOWANCE, night.role)
        assertEquals(NameCheck("야근수당", "야간수당", SlipRole.NIGHT), night.check)
        val lodging = lines.single { it.amount == 200_000L }
        assertEquals("식비", lodging.name)
        assertEquals("숙비", lodging.check?.printed)
        assertTrue(lodging.check!!.renamed(lodging))
        // The amounts add up to the totals the payslip states: no amount to look at, two names to look at, not sure.
        assertTrue("${res.unsure}", res.unsure.isEmpty())
        assertEquals(setOf(lines.indexOf(night), lines.indexOf(lodging)), res.read.names)
        assertFalse(res.sure)
        // 1.39 took 야근수당 as 야간 pay: the 지급 kinds as read stay plain 수당.
        assertTrue(lines.none { it.role == SlipRole.NIGHT || it.role == SlipRole.EXTRA_WORK })
        // Every name taken as shown ('네, 같아요'): nothing left to look at.
        assertTrue(res.read.namesLooked().names.isEmpty())
    }

    @Test fun aNameAnsweredOnceIsNotAskedNextMonth() {
        val asked = PayslipRead(listOf(
            SlipLine("야근수당", SlipRole.ALLOWANCE, 150_000, check = NameCheck("야근수당", "야간수당", SlipRole.NIGHT)),
            SlipLine("식비", SlipRole.ALLOWANCE, 200_000, check = NameCheck("숙비")),
            SlipLine("볼링회", SlipRole.DEDUCTION, 10_000, check = NameCheck("볼링회")),
        ))
        // '네, 야간이에요', '사진대로 ‘숙비’', '맞아요'.
        val answered = PayslipRead(listOf(
            asked.lines[0].check!!.asMaybe(asked.lines[0]),
            asked.lines[1].check!!.asPrinted(asked.lines[1]),
            asked.lines[2].copy(check = null),
        ))
        assertEquals(SlipLine("야근수당", SlipRole.NIGHT, 150_000), answered.lines[0])
        assertEquals(SlipLine("숙비", SlipRole.ALLOWANCE, 200_000), answered.lines[1])
        val book = SlipNames.learned(asked, answered)
        assertEquals(mapOf("야근수당" to SlipRole.NIGHT, "숙비" to SlipRole.ALLOWANCE, "볼링회" to SlipRole.DEDUCTION), book)
        // Next month the same photo reads the same way: as the user said, nothing asked.
        val next = SlipNames.apply(asked, book)
        assertEquals(answered.lines, next.lines)
        assertTrue(next.names.isEmpty())
    }

    @Test fun aNameLookedAtTravelsWithItsLine() {
        // Through the vote of several readings, the line keeps its question.
        val words = payslip()
        val res = SlipReading.read(listOf(words, words, words))
        assertNotNull(res.read.lines.single { it.amount == 150_000L }.check?.maybe)
        // A line typed by hand has none.
        assertNull(SlipLine("야근수당", SlipRole.ALLOWANCE, 150_000).check)
    }

    // ---- [RVL-47] SCAN-31 · PAYC-18 ----

    private val oct = YearMonth.of(2026, 10)
    private val salary = PayProfile(occupation = Occupation.CUSTOM, wageType = WageType.MONTHLY,
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 3_000_000))))

    private fun bonuses(p: PayProfile) = p.items.filter { it.cadence == Cadence.BONUS }

    @Test fun theSameRulesPutInTwiceAreOneBonus() {
        val s = "상여금 연 400% 짝수달"
        val once = RuleText.apply(salary, RuleText.read(s).map { it.change }, oct)
        val twice = RuleText.apply(once, RuleText.read(s).map { it.change }, oct)
        assertEquals(1, bonuses(once).size)
        assertEquals(bonuses(once), bonuses(twice))
        // The same name read with other numbers changes that one.
        val more = RuleText.apply(twice, RuleText.read("상여금 연 600% 짝수달").map { it.change }, oct)
        assertEquals(1, bonuses(more).size)
        assertEquals(bonuses(once).single().id, bonuses(more).single().id)
        assertEquals(1.0, bonuses(more).single().percentOfBase!!, 1e-9)
        // A 명절 one of the same name, by amount.
        val gift = RuleText.apply(more, RuleText.read("명절상여 50만원 설·추석").map { it.change }, oct)
        val again = RuleText.apply(gift, RuleText.read("명절상여 50만원 설·추석").map { it.change }, oct)
        assertEquals(2, bonuses(again).size)
    }

    @Test fun aBonusLikeOneSetUnderAnotherNameStartsOffAndSaysSo() {
        val set = bonuses(RuleText.apply(salary, RuleText.read("상여금 연 400% 짝수달").map { it.change }, oct))
        val sheet = RuleSheet.read(RuleText.read("정기상여 연 400% 짝수달"), bonuses = set)
        val cell = sheet.cells.single()
        assertEquals("이미 있는 ‘상여금’과 같아 보여요. 다른 상여일 때만 켜 주세요", cell.caution)
        assertFalse(cell.on)
        // Another month or %: another 상여, as read.
        val other = RuleSheet.read(RuleText.read("특별상여 연 100% 12월"), bonuses = set).cells.single()
        assertNull(other.caution)
        // The same name: no question, it changes that one.
        assertNull(RuleSheet.read(RuleText.read("상여금 연 400% 짝수달"), bonuses = set).cells.single().caution)
        // By amount too ("보너스"는 받침이 없어 '와').
        val gift = listOf(PayItem("bonus-1", "보너스", ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 500_000))), Cadence.BONUS,
            festivals = setOf(Festival.SEOLLAL, Festival.CHUSEOK), bonusBasis = BonusBasis.BASE))
        assertEquals("이미 있는 ‘보너스’와 같아 보여요. 다른 상여일 때만 켜 주세요",
            RuleSheet.read(RuleText.read("명절상여 50만원 설·추석"), bonuses = gift).cells.single().caution)
    }

    // ---- [RVL-48] SCAN-32 ----

    private fun bonus(s: String) = RuleText.read(s).map { it.change }.filterIsInstance<RuleChange.Bonus>().single()

    @Test fun seolIsAWordOfItsOwn() {
        // 시설·건설 hold the letter: no 명절, paid in the even months as a 상여 with no month said is.
        assertEquals(emptySet<Festival>(), bonus("시설관리팀 상여금 연 400%").festivals)
        assertEquals(setOf(2, 4, 6, 8, 10, 12), bonus("시설관리팀 상여금 연 400%").months)
        assertEquals(emptySet<Festival>(), bonus("건설 현장 상여금 연 200% 6, 12월").festivals)
        // 설 itself, written as people write it.
        assertEquals(setOf(Festival.SEOLLAL, Festival.CHUSEOK), bonus("명절상여 50만원 설·추석").festivals)
        assertEquals(setOf(Festival.SEOLLAL, Festival.CHUSEOK), bonus("상여 50만원 설과 추석에 지급").festivals)
        assertEquals(setOf(Festival.SEOLLAL), bonus("매년 설날에 상여금 30만원").festivals)
        assertEquals(setOf(Festival.SEOLLAL), bonus("설 상여 30만원").festivals)
        assertEquals(setOf(Festival.SEOLLAL), bonus("구정 보너스 20만원").festivals)
        assertEquals(setOf(Festival.SEOLLAL, Festival.CHUSEOK), bonus("명절 보너스 20만원").festivals)
    }

    @Test fun aRaiseIsYearlyOnlyWhenTheWordsSaySo() {
        fun change(s: String) = RuleText.read(s).single()
        assertEquals(RuleChange.Raise(3, 3.5), change("매년 3월 3.5% 인상").change)
        assertEquals(RuleChange.Raise(1, 4.0), change("해마다 기본급 4% 인상").change)
        assertEquals(RuleChange.Raise(1, 2.0), change("호봉 승급 시 2% 인상").change)
        val once = change("2027년 3월 기본급 3.5% 인상")
        assertEquals(RuleChange.RaiseOnce(3, 3.5), once.change)
        assertTrue("offered, not ticked", once.confidence < 0.6)
        assertEquals("3월 한 번만 기본급 3.5% 인상", once.change.summary)
        // Raised once from March, and never again by itself.
        val p = RuleText.apply(salary, listOf(once.change), YearMonth.of(2026, 11))
        assertEquals(3_000_000L, p.base.on(LocalDate.of(2027, 2, 1)).value)
        assertEquals(3_105_000L, p.base.on(LocalDate.of(2027, 3, 1)).value)
        assertEquals(3_105_000L, p.base.on(LocalDate.of(2028, 3, 1)).value)
        assertNull(p.base.raise)
        // With no month, from the month the rules go in.
        val now = RuleText.apply(salary, listOf(RuleChange.RaiseOnce(null, 3.5)), YearMonth.of(2026, 11))
        assertEquals(3_000_000L, now.base.on(LocalDate.of(2026, 10, 1)).value)
        assertEquals(3_105_000L, now.base.on(LocalDate.of(2026, 11, 1)).value)
    }
}
