package com.bamnat.core.money

import com.bamnat.core.work.MoneyShareFigures
import com.bamnat.core.work.MonthFigure
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 2.0.0 갈래 K [RVK-60] (검토 I06ab-01, DB 37): 분류를 고정 id로. A line points at its category's fixed id and the name is only what is
 * shown, so a renamed 급여 is still counted as pay once, a renamed 카드값 still fills itself, and a renamed built-in one does not come back
 * beside its new name. Names and amounts are made up.
 */
class CategoryIdsTest {
    private val day = LocalDate.of(2026, 11, 25)
    private val salary = CategoryRole.SALARY.id

    @Test fun idsAreFixedAndSayWhatTheyAre() {
        assertEquals("b.i.급여", salary)
        assertEquals("b.x.식비", CategoryIds.of(MoneyKind.EXPENSE, "식비"))
        assertEquals("같은 이름도 종류마다 다른 id", "b.i.기타", CategoryIds.of(MoneyKind.INCOME, "기타"))
        assertEquals("가게·현장 기본 분류도 기본 id", "b.x.재료비", CategoryIds.of(MoneyKind.EXPENSE, "재료비"))
        assertEquals("n.x.운동", CategoryIds.of(MoneyKind.EXPENSE, "운동"))
        assertEquals("식비", CategoryIds.builtinName("b.x.식비"))
        assertNull(CategoryIds.builtinName("n.x.운동"))
        assertEquals("운동", CategoryIds.nameIn("n.x.운동"))
        assertEquals(MoneyKind.TRANSFER, CategoryIds.kindIn("b.t.카드값"))
        assertEquals(CategoryRole.CARD, CategoryIds.role("b.t.카드값"))
        assertNull(CategoryIds.role("b.x.식비"))
        val own = CategoryIds.own(kotlin.random.Random(7))
        assertTrue(own.matches(Regex("u\\.[a-z0-9]{8}")))
        assertEquals("food", CategoryIds.standard("b.x.배달"))
        assertEquals("work_cost", CategoryIds.standard("b.x.자재비"))
        assertEquals("직접 만든 분류는 묶음 없음", "", CategoryIds.standard("u.abcdefgh"))
    }

    @Test fun aRenamedSalaryIsStillPayOnce() {
        val edits = listOf(CategoryEdit(MoneyKind.INCOME, "월급", 0, id = salary))
        val pay = MoneyEntry(date = day, kind = MoneyKind.INCOME, amount = 2_800_000, category = "월급", categoryId = salary)
        val side = MoneyEntry(date = day, kind = MoneyKind.INCOME, amount = 100_000, category = "부수입", categoryId = "b.i.부수입")
        assertTrue(pay.isRole(CategoryRole.SALARY))
        assertEquals(2_800_000L, pay.shareOf(CategoryRole.SALARY))
        assertEquals(0L, side.shareOf(CategoryRole.SALARY))
        // The month counts the pay as pay (not as other income on top of the estimate).
        val month = MoneyCalculator.month(YearMonth.from(day), listOf(pay, side), emptyList(), emptyList(), payEstimate = 3_000_000)
        assertEquals(2_800_000L, month.payCounted)
        assertEquals(100_000L, month.otherIncome)
        // A split line: its pay part by the part's id.
        val split = MoneyEntry(date = day, kind = MoneyKind.INCOME, amount = 3_000_000, category = "부수입", categoryId = "b.i.부수입",
            parts = listOf(EntryPart("월급", 2_500_000, salary), EntryPart("부수입", 500_000, "b.i.부수입")))
        assertEquals(2_500_000L, split.shareOf(CategoryRole.SALARY))
        assertEquals(listOf(MonthFigure(YearMonth.from(day), 2_500_000, "받은 급여")), MoneyShareFigures.recordedPay(listOf(YearMonth.from(day)), listOf(split)))
        // A line saved before 37 has no id and goes by its name, as it did.
        assertEquals(10L, MoneyEntry(date = day, kind = MoneyKind.INCOME, amount = 10, category = "급여").shareOf(CategoryRole.SALARY))
        // What the user calls it now is found by name; the name 급여 is not offered twice.
        assertEquals(salary, CategoryIds.resolve(MoneyKind.INCOME, "월급", edits))
        assertEquals("월급", CategoryIds.nameOf(MoneyKind.INCOME, salary, edits))
        assertEquals("읽은 '급여'는 사용자의 월급으로", salary, CategoryIds.resolve(MoneyKind.INCOME, "급여", edits))
        val shown = MoneyCategories.of(MoneyKind.INCOME, edits)
        assertEquals("월급", shown.first())
        assertFalse("바꾼 기본 분류의 옛 이름이 다시 생기지 않음", "급여" in shown)
        assertEquals(1, shown.count { it == "월급" })
    }

    @Test fun aRenamedBuiltInDoesNotComeBackBesideIt() {
        // Before 37 the editor saved names only: 카페 renamed 커피 brought 카페 back at the end.
        val edits = listOf(CategoryEdit(MoneyKind.EXPENSE, "식비", 0, id = "b.x.식비"), CategoryEdit(MoneyKind.EXPENSE, "커피", 1, id = "b.x.카페"))
        val list = MoneyCategories.of(MoneyKind.EXPENSE, edits)
        assertEquals(listOf("식비", "커피"), list.take(2))
        assertFalse("카페" in list)
        assertTrue("편의점" in list)
        // Rows written before 37 without ids go by their names, as they did.
        assertEquals(listOf("술", "식비"), MoneyCategories.of(MoneyKind.EXPENSE, listOf(CategoryEdit(MoneyKind.EXPENSE, "술", 0), CategoryEdit(MoneyKind.EXPENSE, "식비", 1))).take(2))
    }

    @Test fun aLineMovedInTheSheetTakesItsNewCategorysId() {
        val edits = listOf(CategoryEdit(MoneyKind.EXPENSE, "외식", 0, id = "b.x.식비"))
        // Same name as its id: kept.
        assertEquals("b.x.식비", CategoryIds.settle(MoneyKind.EXPENSE, "외식", "b.x.식비", edits))
        // Moved to 카페 in the sheet with its old id still on: 카페 wins.
        assertEquals("b.x.카페", CategoryIds.settle(MoneyKind.EXPENSE, "카페", "b.x.식비", edits))
        // A line read as 식비 lands on the user's 외식.
        assertEquals("b.x.식비", CategoryIds.settle(MoneyKind.EXPENSE, "식비", "", edits))
        assertEquals("외식", CategoryIds.nameOf(MoneyKind.EXPENSE, "b.x.식비", edits))
    }

    @Test fun roleNamesStayTheirs() {
        // 부수입 renamed 급여 would be taken for pay: never, while 급여 is another category's.
        assertFalse(MoneyCategories.nameFree(MoneyKind.INCOME, "급여", "b.i.부수입"))
        assertTrue("급여 itself may take its name back", MoneyCategories.nameFree(MoneyKind.INCOME, "급여", salary))
        assertTrue("another kind may", MoneyCategories.nameFree(MoneyKind.EXPENSE, "급여", "n.x.급여"))
        assertTrue(MoneyCategories.nameFree(MoneyKind.EXPENSE, "외식", "b.x.식비"))
    }

    @Test fun cardBillsAndOwnMovesGoByRole() {
        val card = MoneyAccount(id = 3, name = "신한", type = AccountType.CREDIT, payDay = 14)
        assertEquals(CategoryRole.CARD.id, UpcomingPayments.cardCost(card, 300_000).categoryId)
        val renamed = FixedCost(name = "적금 아닌 내 돈", amount = 100_000, day = 10, kind = MoneyKind.TRANSFER, category = "통장끼리",
            categoryId = CategoryRole.OWN.id)
        val book = MoneyBook(fixed = listOf(renamed))
        val (flows, _) = MoneyAhead.flows(book, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), emptyList())
        assertTrue("내 계좌끼리는 빠져나가는 돈이 아님(이름을 바꿔도)", flows.none { it.label == "적금 아닌 내 돈" })
    }

    @Test fun whoseMoneyIsWhose() {
        assertEquals(CategoryScope.WORK, CategoryIds.scopeOf(MoneyKind.EXPENSE, "b.x.자재비", CategoryScope.FOLLOW, null))
        assertEquals(CategoryScope.HOME, CategoryIds.scopeOf(MoneyKind.EXPENSE, "b.x.식비", CategoryScope.FOLLOW, WorkStyle.FIELD))
        assertEquals("가게 달력에서 만든 분류는 가게 돈", CategoryScope.WORK, CategoryIds.scopeOf(MoneyKind.EXPENSE, "u.abcdefgh", CategoryScope.FOLLOW, WorkStyle.BUSINESS))
        assertEquals(CategoryScope.HOME, CategoryIds.scopeOf(MoneyKind.EXPENSE, "u.abcdefgh", CategoryScope.FOLLOW, WorkStyle.SHIFT))
        assertEquals("사용자가 고른 것이 먼저", CategoryScope.HOME, CategoryIds.scopeOf(MoneyKind.EXPENSE, "b.x.자재비", CategoryScope.HOME, null))
    }
}
