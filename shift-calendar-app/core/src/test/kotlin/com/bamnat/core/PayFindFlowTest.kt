package com.bamnat.core

import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.plaza.PayFindRows
import com.bamnat.core.plaza.PublicCycles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [PAYF-02][PAYF-06] 1.29 묶음 AP: 급여 방식 찾기가 휴대폰 자료로 바로 보이고 서버 답을 덧붙이기, 기본급은 꼭. */
class PayFindFlowTest {
    private val month = YearMonth.of(2026, 10)

    @Test fun thePhoneListsCompaniesAtOnceOnePerCompany() {
        val rows = PayFindRows.local("현대")
        assertTrue(rows.isNotEmpty())
        assertEquals("one line per company", rows.size, rows.map { PublicCycles.norm(it.name) }.distinct().size)
        assertTrue(rows.all { it.publicId != null && !it.onServer && !it.pay })
        assertTrue(PayFindRows.local("현").isEmpty())
        // The company the user already named comes first, even one not in the public data.
        val known = PayFindRows.local("예시정밀", known = listOf("(주)예시정밀"))
        assertEquals("(주)예시정밀", known.first().name)
        assertTrue(known.first().known)
        // 초성 and the other spelling find the named one too.
        assertTrue(PayFindRows.matches("(주)예시정밀", "ㅇㅅㅈㅁ"))
        assertTrue(PayFindRows.matches("GS칼텍스", "지에스칼텍스"))
        assertFalse(PayFindRows.matches("GS칼텍스", "한화"))
    }

    @Test fun theServerAnswerIsLaidOverWithoutLosingAnyRow() {
        val local = PayFindRows.local("GS칼텍스")
        assertEquals("no answer yet keeps the phone's rows", local, PayFindRows.merge(local, null))
        val server = listOf(
            PayFindRows.Server("c2", "GS칼텍스 여수", users = 2),
            PayFindRows.Server("c1", "GS칼텍스", users = 7, pay = true, ok = 4),
        )
        val merged = PayFindRows.merge(local, server)
        // A pay way first; the public row of the same name joins it rather than standing twice.
        assertEquals("GS칼텍스", merged.first().name)
        assertTrue(merged.first().pay)
        assertEquals(local.first { it.name == "GS칼텍스" }.publicId, merged.first().publicId)
        assertEquals(1, merged.count { PublicCycles.norm(it.name) == PublicCycles.norm("GS칼텍스") })
        assertEquals("급여 방식 있음 · 맞아요 4명", PayFindRows.payLine(merged.first()))
        // An empty answer is "none on the server", the public rows still there.
        assertEquals(local, PayFindRows.merge(local, emptyList()))
        assertEquals("급여 방식은 아직 없어요", PayFindRows.payLine(merged[1]))
        assertTrue(PayFindRows.payLine(local.first()).startsWith("근무주기 공개 자료만 있어요"))
    }

    private fun monthly(base: Long, estimated: Set<String> = emptySet(), items: List<PayItem> = emptyList()) = PayProfile(
        Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2025, 11, 1), base))),
        items = items, estimatedFields = estimated,
    )

    @Test fun theBaseIsAskedUntilItIsTheUsersOwn() {
        assertTrue(BaseAsk.needed(null, month))
        assertTrue(BaseAsk.needed(monthly(0), month))
        assertTrue(BaseAsk.needed(monthly(2_500_000, setOf(Unknowns.BASE)), month))
        assertFalse(BaseAsk.needed(monthly(2_500_000), month))
        assertNull(BaseAsk.known(monthly(0), month))
        assertFalse(BaseAsk.showsEstimate(monthly(0), month))
    }

    @Test fun aMissingBaseIsPutWhereItStoodAndAKnownOneChangesFromNow() {
        // Missing (a company's way came without amounts): the months already worked get it too.
        val filled = BaseAsk.apply(monthly(0), 2_600_000, month)
        assertEquals(2_600_000, filled.base.on(YearMonth.of(2025, 12)).value)
        assertEquals(1, filled.base.entries.size)
        // Guessed: replaced, and the 추정 mark goes.
        val guessed = BaseAsk.apply(monthly(2_000_000, setOf(Unknowns.BASE)), 2_600_000, month)
        assertEquals(2_600_000, guessed.base.on(YearMonth.of(2025, 12)).value)
        assertFalse(Unknowns.BASE in guessed.estimatedFields)
        // Known and different: from this month on, the earlier months as they were.
        val raised = BaseAsk.apply(monthly(2_500_000), 2_600_000, month)
        assertEquals(2_500_000, raised.base.on(YearMonth.of(2026, 9)).value)
        assertEquals(2_600_000, raised.base.on(month).value)
        // Same value: nothing new.
        assertEquals(1, BaseAsk.apply(monthly(2_500_000), 2_500_000, month).base.entries.size)
    }

    @Test fun theHoursComeFromTheOrdinaryHourlyWhenThatIsWhatTheUserKnows() {
        val p = monthly(0, setOf(RuleOnlyNeeds.HOURS_UNSET), items = listOf(
            PayItem("q", "자격수당", ValueTimeline(listOf(DatedValue(LocalDate.of(2025, 11, 1), 90_000))), Cadence.MONTHLY, ordinary = true),
        ))
        // (2,000,000 + 90,000) ÷ 10,000 = 209시간.
        assertEquals(209, BaseAsk.hoursFromHourly(p, month, 2_000_000, 10_000))
        assertNull("a slip of the finger is not a 소정근로시간", BaseAsk.hoursFromHourly(p, month, 2_000_000, 100))
        assertNull(BaseAsk.hoursFromHourly(p.copy(wageType = WageType.HOURLY), month, 2_000_000, 10_000))
        val set = BaseAsk.apply(p, 2_000_000, month, hours = 209)
        assertEquals(209, set.monthlyHours)
        assertFalse(RuleOnlyNeeds.HOURS_UNSET in set.estimatedFields)
        // Hours out of range are left as they were.
        assertEquals(p.monthlyHours, BaseAsk.apply(p, 2_000_000, month, hours = 20).monthlyHours)
    }
}
