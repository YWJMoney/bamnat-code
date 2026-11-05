package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.37 [LEDH-28] 한 줄로 적기. Amounts and names are made up. */
class QuickLineTest {
    private val cats = listOf("식비", "교통", "카페")
    private val card = MoneyAccount(id = 7, name = "국민카드", type = AccountType.CREDIT)
    private val check = MoneyAccount(id = 8, name = "토스", type = AccountType.CHECK)

    @Test fun lunchNineThousandByCard() {
        val d = QuickLine.parse("점심 9천 카드", cats, listOf(card, check))!!
        assertEquals(9_000L, d.amount)
        assertEquals(MoneyKind.EXPENSE, d.kind)
        assertEquals("점심", d.place)
        assertEquals(true, d.anyCard)
        val used = listOf(MoneyEntry(date = java.time.LocalDate.of(2026, 11, 1), kind = MoneyKind.EXPENSE, amount = 1, category = "식비", accountId = 8))
        assertEquals(8L, QuickLine.account(d, listOf(card, check), used))
    }

    @Test fun yesterdayTaxiWithCommaAndCategory() {
        val d = QuickLine.parse("어제 교통 택시 12,500", cats)!!
        assertEquals(12_500L, d.amount)
        assertEquals(1, d.daysAgo)
        assertEquals("교통", d.category)
        assertEquals("교통 택시", d.place)
    }

    @Test fun incomeAndManCheon() {
        val d = QuickLine.parse("+ 용돈 3만5천", cats)!!
        assertEquals(35_000L, d.amount)
        assertEquals(MoneyKind.INCOME, d.kind)
        assertEquals(15_000L, QuickLine.parse("1.5만 카페", cats)!!.amount)
    }

    @Test fun namedAccountWithParticle() {
        val d = QuickLine.parse("편의점 4,200 국민카드로", cats, listOf(card))!!
        assertEquals(7L, d.accountId)
        assertEquals("편의점", d.place)
        assertNull(QuickLine.parse("   ", cats))
    }
}
