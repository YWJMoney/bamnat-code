package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 2.0.0 갈래 K: [RVK-09] 분류 이름 한 번에 바꾸기, [RVK-12] 다른 사람 돈은 합치지 않기. Names and amounts are made up. */
class LedgerK200Test {
    private val day = LocalDate.of(2026, 11, 12)

    private fun applied(start: List<String>, renames: List<Pair<String, String>>): List<String> =
        MoneyCategories.renameSteps(renames).fold(start) { cats, (from, to) -> cats.map { if (it == from) to else it } }

    @Test fun renamesMeantAtOnceNeverLandOnEachOther() {
        // 식비 → 외식 while 장보기 → 식비: the old 식비 lines become 외식, the old 장보기 lines become 식비, none get mixed.
        assertEquals(listOf("외식", "식비", "카페"), applied(listOf("식비", "장보기", "카페"), listOf("식비" to "외식", "장보기" to "식비")))
        // The other way round gives the same.
        assertEquals(listOf("외식", "식비", "카페"), applied(listOf("식비", "장보기", "카페"), listOf("장보기" to "식비", "식비" to "외식")))
        // A swap.
        assertEquals(listOf("카페", "식비"), applied(listOf("식비", "카페"), listOf("식비" to "카페", "카페" to "식비")))
        // No temporary name is left behind.
        assertTrue(applied(listOf("식비", "카페"), listOf("식비" to "카페", "카페" to "식비")).none { it.startsWith("\u0001") })
    }

    @Test fun fixedNamesAreNeverRenamedOrTaken() {
        assertFalse(MoneyCategories.renamable(MoneyCategories.SALARY))
        assertFalse(MoneyCategories.renamable("카드값"))
        assertFalse(MoneyCategories.renamable(MoneyCategories.OTHER))
        assertTrue(MoneyCategories.renamable("식비"))
        assertEquals(emptyList<Pair<String, String>>(), MoneyCategories.renameSteps(listOf("급여" to "월급")))
        assertEquals(emptyList<Pair<String, String>>(), MoneyCategories.renameSteps(listOf("식비" to "기타")))
        // Blank or unchanged names are no step either.
        assertEquals(emptyList<Pair<String, String>>(), MoneyCategories.renameSteps(listOf("식비" to "  ", "카페" to "카페")))
        // The rest still go through.
        assertEquals(listOf("외식", "급여"), applied(listOf("식비", "급여"), listOf("식비" to "외식", "급여" to "월급")))
    }

    private fun line(kind: MoneyKind, amount: Long, name: String, account: Long, id: Long = 0, time: LocalTime = LocalTime.NOON) =
        MoneyEntry(id = id, date = day, kind = kind, amount = amount, category = "기타", merchant = name, time = time,
            source = MoneySource.NOTIFICATION, accountId = account)

    @Test fun moneyOfTwoOtherPeopleStaysTwoLines() {
        val out = line(MoneyKind.TRANSFER, 50_000, "김민수", account = 1, id = 7)
        val into = line(MoneyKind.INCOME, 50_000, "이영희", account = 2, time = LocalTime.of(12, 2))
        // My name known, neither line is me, the two names differ: a payment to 김민수 and 이영희's money, two lines.
        assertNull(LedgerSmarts.ownTransfer(into, listOf(out), myNames = listOf("홍길동")))
        // One of them is me (the bank masks it): my own money moving, one transfer as before.
        assertNotNull(LedgerSmarts.ownTransfer(into.copy(merchant = "홍*동"), listOf(out), myNames = listOf("홍길동")))
        assertNotNull(LedgerSmarts.ownTransfer(into, listOf(out.copy(merchant = "홍길동님")), myNames = listOf("홍길동")))
        // The same name on both: one person's money, joined.
        assertNotNull(LedgerSmarts.ownTransfer(into.copy(merchant = "김민수"), listOf(out), myNames = listOf("홍길동")))
        // My name not set: as before, and every join is kept in 자동으로 정리한 것 to put back.
        assertEquals(7L, LedgerSmarts.ownTransfer(into, listOf(out))?.id)
    }

    @Test fun aBalanceLineBringsTheAccountToWhatIsInIt() {
        val bank = MoneyAccount(id = 3, name = "월급 통장", type = AccountType.BANK, opening = 100_000)
        val spent = MoneyEntry(id = 1, date = day, kind = MoneyKind.EXPENSE, amount = 30_000, category = "식비", accountId = 3)
        val counted = MoneyAccounts.balances(listOf(bank), listOf(spent))[3L]!!
        assertEquals(70_000L, counted)
        // Less in it than counted: a line out of it.
        val out = MoneyAccounts.adjustment(bank, counted, 61_500, day)!!
        assertEquals(MoneyKind.TRANSFER, out.kind)
        assertEquals(8_500L, out.amount)
        assertEquals(61_500L, MoneyAccounts.balances(listOf(bank), listOf(spent, out))[3L])
        // More in it: a line into it.
        val into = MoneyAccounts.adjustment(bank, counted, 72_000, day)!!
        assertEquals(72_000L, MoneyAccounts.balances(listOf(bank), listOf(spent, into))[3L])
        // A transfer, so the month's 수입·지출 stay as they were.
        val month = MoneyCalculator.month(java.time.YearMonth.from(day), listOf(spent, out, into), emptyList(), emptyList(), null, day)
        assertEquals(30_000L, month.spent)
        assertEquals(0L, month.received)
        // Nothing to do when they agree, or for a card.
        assertNull(MoneyAccounts.adjustment(bank, counted, 70_000, day))
        assertNull(MoneyAccounts.adjustment(MoneyAccount(id = 4, name = "카드", type = AccountType.CREDIT), 0, 5_000, day))
    }

    @Test fun myNameReadsMaskedAndSpaced() {
        assertTrue(LedgerChecks.isMine("홍길동", listOf("홍길동")))
        assertTrue(LedgerChecks.isMine("홍*동", listOf("홍길동")))
        assertTrue(LedgerChecks.isMine("(홍길동)", listOf("홍 길동")))
        assertFalse(LedgerChecks.isMine("홍길순", listOf("홍길동")))
        assertFalse(LedgerChecks.isMine("홍길동", listOf("홍")))
    }
}
