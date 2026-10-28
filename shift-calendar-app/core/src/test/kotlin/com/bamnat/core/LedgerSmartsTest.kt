package com.bamnat.core

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.LedgerSmarts
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PaymentParser
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.PayGaps
import com.bamnat.core.pay.PayGap
import com.bamnat.core.pay.Payslip
import com.bamnat.core.pay.SlipAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** E 1.32 가계부 기본기 [LEDX-01]..[LEDX-11] and D 1.32 [PAYA-02][PAYA-04]. Amounts and places are made up. */
class LedgerSmartsTest {
    private val day = LocalDate.of(2026, 10, 20)
    private fun e(kind: MoneyKind, amount: Long, merchant: String = "", time: LocalTime? = LocalTime.of(12, 0), account: Long? = null,
                  id: Long = 0, date: LocalDate = day, category: String = "기타", last4: String = "", note: String = "") =
        MoneyEntry(id = id, date = date, kind = kind, amount = amount, category = category, merchant = merchant, time = time,
            accountId = account, cardLast4 = last4, note = note)

    @Test fun aDepositFromAnotherOwnAccountIsATransferNotIncome() {
        val out = e(MoneyKind.TRANSFER, 1_000_000, "생활비", account = 1, id = 5)
        val into = e(MoneyKind.INCOME, 1_000_000, "홍길동", account = 2, time = LocalTime.of(12, 1))
        val other = LedgerSmarts.ownTransfer(into, listOf(out))
        assertEquals(5L, other?.id)
        val joined = LedgerSmarts.joinTransfer(other!!, into)
        assertEquals(MoneyKind.TRANSFER, joined.kind)
        assertEquals(2L, joined.toAccountId)
        // The same account, or no account known, is not a move between two.
        assertNull(LedgerSmarts.ownTransfer(into.copy(accountId = 1), listOf(out)))
        assertNull(LedgerSmarts.ownTransfer(into.copy(accountId = null), listOf(out)))
        assertNull(LedgerSmarts.ownTransfer(into.copy(date = day.plusDays(3)), listOf(out)))
    }

    @Test fun aCheckCardAndItsBanksWithdrawalAreOnePayment() {
        val card = e(MoneyKind.EXPENSE, 12_000, "김밥천국 역삼점", last4 = "1234", id = 1)
        val bank = e(MoneyKind.TRANSFER, 12_000, "체크카드출금", time = LocalTime.of(12, 3))
        assertEquals(1L, LedgerSmarts.crossDuplicate(bank, listOf(card))?.id)
        assertEquals(card, LedgerSmarts.keepOf(card, bank))
        // Twenty minutes apart they are two.
        assertNull(LedgerSmarts.crossDuplicate(bank.copy(time = LocalTime.of(12, 25)), listOf(card)))
        val pay = e(MoneyKind.EXPENSE, 12_000, "카카오페이", note = "카카오페이", time = LocalTime.of(12, 1))
        assertNotNull(LedgerSmarts.crossDuplicate(pay, listOf(card)))
        assertEquals(card, LedgerSmarts.keepOf(pay, card))
    }

    @Test fun aPartRefundFindsItsPayment() {
        val paid = e(MoneyKind.EXPENSE, 35_000, "올리브영 강남점", date = day.minusDays(5), id = 9)
        val t = LedgerSmarts.refundTarget(12_000, "올리브영 강남점", null, day, listOf(paid))
        assertEquals(9L, t?.id)
        assertTrue(LedgerSmarts.refundNote(12_000, t!!).startsWith("부분취소 12,000원"))
        assertNull(LedgerSmarts.refundTarget(40_000, "올리브영", null, day, listOf(paid)))
        // Abroad a refund may come back a little more than it cost.
        assertNotNull(LedgerSmarts.refundTarget(35_900, "올리브영", null, day, listOf(paid), foreign = true))
    }

    @Test fun installmentsSayTheirTurnAndWhatIsLeft() {
        val tv = e(MoneyKind.EXPENSE, 1_200_000, "전자랜드", date = LocalDate.of(2026, 8, 3)).copy(installments = 6)
        assertEquals(3, LedgerSmarts.installmentOf(tv, YearMonth.of(2026, 10)))
        assertEquals("6개월 할부 · 3/6회 · 남은 3회 600,000원", LedgerSmarts.installmentText(tv, day))
        assertEquals(800_000L, LedgerSmarts.installmentsLeft(listOf(tv), day))
    }

    @Test fun theLearnerPlacesAPlaceByItsLines() {
        val taught = (1..15).map { e(MoneyKind.EXPENSE, 6_000, "동네커피${it % 3}", category = "카페", date = day.minusDays(it.toLong())).copy(reviewed = true) } +
            (1..15).map { e(MoneyKind.EXPENSE, 60_000, "주유소${it % 3}", category = "자동차", date = day.minusDays(it.toLong())).copy(reviewed = true) }
        val l = LedgerSmarts.Learner.of(taught)
        val g = l.guess(e(MoneyKind.EXPENSE, 5_500, "동네커피9"))
        assertEquals("카페", g.first().category)
        assertTrue(g.first().share >= LedgerSmarts.Learner.SURE)
        // Too few lines: nothing said.
        assertTrue(LedgerSmarts.Learner.of(taught.take(5)).guess(e(MoneyKind.EXPENSE, 5_500, "동네커피9")).isEmpty())
    }

    @Test fun aRuleSaysWhichPastLinesItMoves() {
        val lines = listOf(e(MoneyKind.EXPENSE, 3_000, "다이소 역삼점", category = "생활"), e(MoneyKind.EXPENSE, 4_000, "다이소 강남점", category = "장보기·쇼핑"),
            e(MoneyKind.EXPENSE, 4_000, "이마트", category = "장보기·쇼핑"))
        assertEquals(1, LedgerSmarts.ruleWouldChange(lines, PaymentParser.CONTAINS + "다이소", "생활").size)
    }

    @Test fun theMonthsPaceAndTodaysMoney() {
        val lines = (1..20).map { e(MoneyKind.EXPENSE, 10_000, "x", date = LocalDate.of(2026, 10, it)) }
        val p = LedgerSmarts.pace(lines, day, budget = 400_000, fixedToCome = 50_000)
        assertEquals(200_000L, p.spent)
        assertEquals(310_000L + 50_000, p.projected)
        assertEquals(12, p.daysLeft)
        assertEquals((400_000L - 200_000 - 50_000) / 12, p.todayLeft)
        assertEquals(mapOf(day to 10_000L), LedgerSmarts.spentByDay(lines.filter { it.date == day }, YearMonth.of(2026, 10)))
    }

    @Test fun abroadIsWrittenAtARoughRateAndDccIsSaid() {
        val a = LedgerSmarts.abroad("USD", 10.0)!!
        assertEquals(13_900L + 174, a.won)
        assertTrue(a.note.contains("임시"))
        assertNull(LedgerSmarts.abroad("XYZ", 1.0))
        assertTrue(LedgerSmarts.looksDcc("[해외승인] KRW 52,000 AMAZON"))
        assertFalse(LedgerSmarts.looksDcc("[승인] 52,000원 이마트"))
    }

    @Test fun subscriptionsAreFoundWithARaise() {
        val lines = (0..3).map { i -> e(MoneyKind.EXPENSE, if (i == 3) 17_000 else 13_500, "넷플릭스", date = day.minusMonths((3 - i).toLong()), category = "구독") } +
            (0..3).map { i -> e(MoneyKind.EXPENSE, 4_000L + i * 3_000, "동네카페", date = day.minusMonths(i.toLong())) }
        val s = LedgerSmarts.subscriptions(lines, day)
        assertEquals(listOf("넷플릭스"), s.map { it.place })
        assertEquals(3_500L, s.single().raised)
        assertTrue(s.single().stale)
        assertFalse(LedgerSmarts.subscriptions(lines, day, mapOf(PaymentParser.placeKey("넷플릭스") to day)).single().stale)
    }

    @Test fun theCardGaugeSaysWhichCardToUse() {
        val accounts = listOf(MoneyAccount(1, "신용", AccountType.CREDIT), MoneyAccount(2, "체크", AccountType.CHECK))
        val lines = listOf(e(MoneyKind.EXPENSE, 5_000_000, account = 1), e(MoneyKind.EXPENSE, 2_000_000, account = 2))
        val g = LedgerSmarts.cardGauge(lines, accounts, 2026, 40_000_000)!!
        assertEquals(10_000_000L, g.threshold)
        assertEquals(5_000_000L, g.credit)
        assertFalse(g.over)
        assertEquals(3_000_000L, g.toThreshold)
        assertTrue(LedgerSmarts.cardGauge(lines, accounts, 2026, 20_000_000)!!.over)
    }

    @Test fun premiumHoursTellARateGapFromAnHoursGap() {
        val pay = MonthPay(YearMonth.of(2026, 9), listOf(
            PayLine(LineKind.OVERTIME, "연장", 300_000, "", minutes = 20 * 60),
            PayLine(LineKind.NIGHT, "야간", 100_000, "", minutes = 16 * 60),
        ), ordinaryHourly = 10_000, ratesVersion = "t", minutes = emptyList(), warnings = emptyList())
        val slip = Payslip(gross = 3_000_000, overtimeMinutes = 20 * 60, overtimePay = 330_000, nightMinutes = 24 * 60, nightPay = 150_000)
        val rows = SlipAudit.hours(slip, pay)
        assertEquals(SlipAudit.Cause.RATE, rows.first { it.label == "연장" }.cause)
        assertEquals(SlipAudit.Cause.HOURS, rows.first { it.label == "야간" }.cause)
        assertTrue(SlipAudit.hoursVerdict(rows)!!.startsWith("근무 시간부터 달라요"))
        assertTrue(SlipAudit.explain("국민연금", 3_000_000, YearMonth.of(2026, 10)).contains("%"))
    }

    @Test fun underpaidMoneyIsSaidAndWrittenUp() {
        val gap = PayGap(YearMonth.of(2026, 9), expected = 2_500_000, received = 2_400_000, dates = listOf(LocalDate.of(2026, 10, 10)))
        assertEquals(100_000L, PayGaps.short(gap))
        assertNull(PayGaps.short(gap.copy(received = 2_499_500)))
        val doc = PayGaps.doc(gap, 2_900_000, listOf("국민연금" to 137_750L), "4대보험·세금 뺀 참고값")
        assertTrue(doc.title.contains("덜 받은 돈"))
    }
}
