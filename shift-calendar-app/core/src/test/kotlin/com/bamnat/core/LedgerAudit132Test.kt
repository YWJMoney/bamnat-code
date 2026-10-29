package com.bamnat.core

import com.bamnat.core.money.AccountType
import com.bamnat.core.money.CardCycle
import com.bamnat.core.money.LedgerChecks
import com.bamnat.core.money.LedgerSmarts
import com.bamnat.core.money.MerchantNames
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.money.PaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 1.32 조사 대조 (계획/1.32-조사-대조표.md): what the first 1.32 pass left out of 가계부 조사 2-4장, now on the phone.
 * [LEDX-12]..[LEDX-19], [LEDX-21], [LEDX-23]. Synthetic texts only.
 */
class LedgerAudit132Test {
    private val now = LocalDateTime.of(2026, 10, 15, 12, 0)
    private fun p(s: String) = (PaymentParser.parse(s, now) as PaymentParser.Result.Ok).parsed
    private fun e(kind: MoneyKind, amount: Long, merchant: String = "", date: LocalDate = LocalDate.of(2026, 10, 5), id: Long = 0, note: String = "", category: String = "기타") =
        MoneyEntry(id = id, date = date, kind = kind, amount = amount, category = category, merchant = merchant, note = note)

    // ---- [LEDX-12] 가맹점 이름 ----

    @Test fun legalNamesShowAsTheShopPeopleKnow() {
        assertEquals("배달의민족", MerchantNames.display("우아한형제들"))
        assertEquals("CU 역삼점", MerchantNames.display("(주)비지에프리테일 역삼점"))
        assertEquals("스타벅스", MerchantNames.display("주식회사 에스씨케이컴퍼니"))
        assertEquals("동네빵집", MerchantNames.display("동네빵집"))
        // The list shows the shop; the line keeps what the text said.
        val line = e(MoneyKind.EXPENSE, 18_000, "우아한형제들")
        assertEquals("배달의민족", line.title)
        assertEquals("우아한형제들", line.merchant)
    }

    @Test fun whatATextAddsToANameIsTakenOff() {
        assertEquals("스타벅스", MerchantNames.clean("스타벅스_A01"))
        assertEquals("쿠팡", MerchantNames.clean("온라인 쿠팡"))
        assertEquals("쿠팡", MerchantNames.clean("쿠팡 (주문 AB12)"))
        assertEquals("올리브영", MerchantNames.clean("올리브영 모바일결제"))
        // Too little left: the name as it was.
        assertEquals("결제", MerchantNames.clean("결제"))
    }

    @Test fun aForeignCardsStarNameReadsAsTheShop() {
        assertEquals("AMAZON", MerchantNames.foreignStar("AMAZON.COM*AB12CD"))
        assertEquals("YouTube", MerchantNames.foreignStar("GOOGLE *YouTube"))
        assertEquals("STEAM", MerchantNames.foreignStar("PAYPAL *STEAM"))
        assertNull(MerchantNames.foreignStar("홍*동"))
        assertNull(MerchantNames.foreignStar("1234*5678"))
    }

    // ---- [LEDX-13] 결제 경로 ----

    @Test fun aPayAppIsTheRouteAndTheShopIsTheShop() {
        assertEquals("네이버페이" to "올리브영", MerchantNames.route("네이버페이 - 올리브영"))
        assertEquals("카카오페이" to "무신사", MerchantNames.route("무신사(카카오페이)"))
        assertEquals(null to "네이버페이 충전", MerchantNames.route("네이버페이 충전"))
        val r = p("[Web발신]\n신한카드(1234)승인 홍*동 12,500원(일시불)10/15 13:20 네이버페이 - 올리브영")
        assertEquals("올리브영", r.merchant)
        assertEquals("네이버페이", r.via)
        val line = PaymentParser.toEntry(r, now.toLocalDate(), MoneySource.NOTIFICATION)!!
        assertTrue(line.note, line.note.contains("네이버페이 결제"))
        assertEquals("네이버페이", LedgerChecks.viaOf(line))
    }

    @Test fun theCardsLineKeepsThePayAppItWentThrough() {
        val card = e(MoneyKind.EXPENSE, 12_500, "올리브영", note = "신한 1234")
        val pay = e(MoneyKind.EXPENSE, 12_500, "올리브영", note = "네이버페이")
        val one = LedgerChecks.withRoute(card, pay)
        assertEquals("신한 1234 · 네이버페이 결제", one.note)
        // Told twice: still once.
        assertEquals(one.note, LedgerChecks.withRoute(one, pay).note)
        // A second card text adds nothing.
        assertEquals(card, LedgerChecks.withRoute(card, card))
    }

    // ---- [LEDX-20] 페이 충전 ----

    @Test fun aTopUpIsAMoveNotSpending() {
        val r = p("[카카오페이] 카카오페이머니 50,000원 충전 완료 10/15 09:00")
        assertTrue(r.charge)
        assertEquals(MoneyKind.TRANSFER, r.kind)
        assertEquals(PaymentParser.CHARGE_CATEGORY, r.category)
        assertEquals("카카오페이", r.merchant)
        val ev = p("신한카드(1234)승인 홍*동 15,000원 10/15 13:20 전기차 급속 충전소")
        assertFalse(ev.charge)
        assertEquals(MoneyKind.EXPENSE, ev.kind)
    }

    // ---- [LEDX-19] 체크 vs 신용 ----

    @Test fun theTextSaysWhichKindOfCard() {
        val r = p("[Web발신]\nKB국민체크(5678)승인 홍*동 8,000원 10/15 12:30 김밥천국")
        assertEquals(AccountType.CHECK, r.cardKind)
        val line = PaymentParser.toEntry(r, now.toLocalDate(), MoneySource.NOTIFICATION)!!
        assertTrue(line.note, line.note.contains("체크카드"))
        assertEquals(AccountType.CHECK, LedgerChecks.cardKind(line, emptyList()))
        // A card the user added says it itself.
        val credit = MoneyAccount(id = 3, name = "국민 카드", type = AccountType.CREDIT, last4 = "5678")
        assertEquals(AccountType.CREDIT, LedgerChecks.cardKind(line, listOf(credit)))
    }

    @Test fun cardsNotAddedYetAreAskedAbout() {
        val lines = (1..3).map { e(MoneyKind.EXPENSE, 8_000, "김밥천국", date = LocalDate.of(2026, 10, it)).copy(cardLast4 = "5678", note = "KB국민 5678 · 체크카드") } +
            e(MoneyKind.EXPENSE, 9_000, "x").copy(cardLast4 = "9999", note = "신한 9999")
        val u = LedgerChecks.unknownCards(lines, emptyList(), LocalDate.of(2026, 9, 1))
        assertEquals(1, u.size)
        assertEquals("5678", u.single().last4)
        assertEquals(AccountType.CHECK, u.single().guess)
        assertEquals("KB국민", u.single().issuer)
        assertEquals(24_000L, u.single().spent)
        assertTrue(LedgerChecks.unknownCards(lines, listOf(MoneyAccount(name = "국민", type = AccountType.CHECK, last4 = "5678")), LocalDate.of(2026, 9, 1)).isEmpty())
    }

    // ---- [LEDX-14] 카드값 대조 ----

    @Test fun aCardsBillIsCheckedAgainstWhatItsLinesSaid() {
        val card = MoneyAccount(id = 7, name = "신한 Deep", type = AccountType.CREDIT, last4 = "1234", payDay = 14)
        val spent = listOf(
            e(MoneyKind.EXPENSE, 100_000, "이마트", date = LocalDate.of(2026, 10, 3)).copy(cardLast4 = "1234"),
            e(MoneyKind.EXPENSE, 30_000, "쿠팡", date = LocalDate.of(2026, 10, 5)).copy(cardLast4 = "1234", installments = 3),
        )
        val paid = e(MoneyKind.TRANSFER, 112_000, "신한카드", date = LocalDate.of(2026, 11, 14), id = 99, category = "카드값")
        val b = LedgerChecks.billCheck(paid, listOf(card), spent + paid)!!
        assertEquals(YearMonth.of(2026, 11), b.payMonth)
        assertEquals(110_000L, b.expected)
        assertEquals(2_000L, b.diff)
        assertEquals("신한 Deep 11월 카드값 · 예상 110,000원 · 실제 112,000원 (2,000원 더)", LedgerChecks.billNote(b))
        // A weekend moves the bill: still found within three days.
        assertEquals(card, LedgerChecks.billCheck(paid.copy(date = LocalDate.of(2026, 11, 16)), listOf(card), spent)!!.card)
        // Far from the 결제일: not a bill.
        assertNull(LedgerChecks.billCheck(paid.copy(date = LocalDate.of(2026, 11, 25)), listOf(card), spent))
        assertNull(LedgerChecks.billCheck(e(MoneyKind.EXPENSE, 112_000, "신한카드", date = LocalDate.of(2026, 11, 14)), listOf(card), spent))
    }

    // ---- [LEDX-15] 내 이름 ----

    @Test fun moneyFromMyselfIsAMoveBetweenMyAccounts() {
        val dep = e(MoneyKind.INCOME, 300_000, "홍*동").copy(accountId = 2)
        assertTrue(LedgerChecks.fromMyself(dep, listOf("홍길동")))
        assertTrue(LedgerChecks.fromMyself(dep.copy(merchant = "홍길동님"), listOf("홍 길동")))
        assertFalse(LedgerChecks.fromMyself(dep.copy(merchant = "김철수"), listOf("홍길동")))
        assertFalse(LedgerChecks.fromMyself(dep.copy(merchant = "홍*"), listOf("홍길동")))
        assertFalse(LedgerChecks.fromMyself(dep, emptyList()))
        val moved = LedgerChecks.asOwnMove(dep)
        assertEquals(MoneyKind.TRANSFER, moved.kind)
        assertEquals(2L, moved.toAccountId)
        assertNull(moved.accountId)
        assertTrue(moved.note.contains("내 계좌끼리"))
    }

    // ---- [LEDX-16] 환불 ----

    @Test fun partRefundsSayWhatIsLeftAndAnUnmatchedOneIsPicked() {
        val buy = e(MoneyKind.EXPENSE, 35_000, "무신사", date = LocalDate.of(2026, 3, 12), id = 1, category = "옷")
        val first = e(MoneyKind.EXPENSE, -12_000, "무신사", date = LocalDate.of(2026, 3, 20), id = 2, note = LedgerChecks.UNMATCHED_REFUND)
        assertTrue(LedgerChecks.isUnmatchedRefund(first))
        assertEquals(buy, LedgerChecks.refundCandidates(first, listOf(buy, e(MoneyKind.EXPENSE, 50_000, "이마트", id = 3).copy(date = LocalDate.of(2026, 3, 18)))).first())
        val linked = LedgerChecks.linkRefund(first, buy, listOf(buy, first))
        assertEquals("옷", linked.category)
        assertEquals("부분취소 12,000원 (원래 35,000원, 3/12) · 남은 23,000원", linked.note)
        assertFalse(LedgerChecks.isUnmatchedRefund(linked))
        val second = e(MoneyKind.EXPENSE, -23_000, "무신사", date = LocalDate.of(2026, 3, 25), id = 4)
        assertEquals(12_000L, LedgerChecks.refundedBefore(buy, listOf(buy, linked, second), except = 4))
        assertTrue(LedgerChecks.linkRefund(second, buy, listOf(buy, linked, second)).note.endsWith("모두 돌려받았어요"))
    }

    // ---- [LEDX-17] 할부 청구 기준·유이자 ----

    @Test fun interestBearingInstallmentsBillTheirFee() {
        val tv = e(MoneyKind.EXPENSE, 300_000, "전자랜드", date = LocalDate.of(2026, 10, 5)).copy(installments = 3, installmentRate = 15.0)
        val first = YearMonth.of(2026, 11)
        assertEquals(3_750L, LedgerChecks.installmentRow(tv, first, first)!!.fee)
        assertEquals(2_500L, LedgerChecks.installmentRow(tv, first, first.plusMonths(1))!!.fee)
        assertEquals(1_250L, LedgerChecks.installmentRow(tv, first, first.plusMonths(2))!!.fee)
        assertNull(LedgerChecks.installmentRow(tv, first, first.plusMonths(3)))
        assertEquals(7_500L, LedgerChecks.installmentFees(tv, first))
        // 무이자: no fee.
        assertEquals(0L, LedgerChecks.installmentFees(tv.copy(installmentRate = 0.0), first))
        // The card's bill carries the fee.
        val card = MoneyAccount(id = 1, name = "현대", type = AccountType.CREDIT, last4 = "1111", payDay = 14)
        val onCard = tv.copy(cardLast4 = "1111")
        assertEquals(100_000L + 3_750, CardCycle.bill(card, first, listOf(onCard), listOf(card)).amount)
    }

    @Test fun theBillViewShowsEachMonthsInstallments() {
        val card = MoneyAccount(id = 1, name = "현대", type = AccountType.CREDIT, last4 = "1111", payDay = 14)
        val a = e(MoneyKind.EXPENSE, 300_000, "전자랜드", date = LocalDate.of(2026, 10, 5)).copy(installments = 3, cardLast4 = "1111")
        val b = e(MoneyKind.EXPENSE, 120_000, "가구", date = LocalDate.of(2026, 11, 2)).copy(installments = 2)
        val plan = LedgerChecks.installmentPlan(listOf(a, b), listOf(card), YearMonth.of(2026, 11), months = 4)
        assertEquals(listOf(100_000L, 160_000L, 160_000L, 0L), plan.map { it.total })
        assertEquals(listOf(1), plan[0].rows.map { it.round })
        assertEquals(listOf(2, 1), plan[1].rows.map { it.round })
        val (owed, fees) = LedgerChecks.owedFrom(listOf(a, b), listOf(card), LocalDate.of(2026, 12, 1))
        assertEquals(320_000L, owed)
        assertEquals(0L, fees)
    }

    // ---- [LEDX-18] 해외: 내 수수료율·내 환율 ----

    @Test fun myOwnRateAndFeeWinOverTheTable() {
        val a = LedgerSmarts.abroad("USD", 10.0, mapOf("USD" to 1_400.0), fee = 0.01)!!
        assertEquals(14_000L + 140, a.won)
        assertTrue(a.note, a.note.contains("내 환율 1,400.00원"))
        assertTrue(a.note.startsWith(LedgerSmarts.ABROAD_MARK))
        assertEquals(1_400.0, LedgerSmarts.learnedRate(10.0, 14_140, 0.01)!!, 0.001)
        assertNull(LedgerSmarts.learnedRate(0.0, 14_140))
        assertEquals("USD" to 10.0, LedgerSmarts.abroadOf(e(MoneyKind.EXPENSE, 14_140, note = a.note)))
        // A slip of 50% is held to the cap.
        assertEquals((13_900 * LedgerSmarts.MAX_ABROAD_FEE).toLong(), LedgerSmarts.abroad("USD", 10.0, fee = 0.5)!!.fee)
    }

    // ---- [LEDX-21] 요일·근무·경로를 배우기 ----

    @Test fun theLearnerKnowsTheDayOnTheCalendar() {
        val nights = setOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3))
        val f = LedgerSmarts.Learner.features(e(MoneyKind.EXPENSE, 4_000, "편의점", date = LocalDate.of(2026, 10, 2), note = "카카오페이 결제")) { if (it in nights) "야간" else null }
        assertTrue(f.contains("#day야간"))
        assertTrue(f.contains("#wd5"))
        assertTrue(f.contains("#via카카오페이"))
        assertTrue(f.any { it.startsWith("3:") })
        // On 야간 days the same shop is 야식; on other days it is 생활.
        val days = (1..28).map { LocalDate.of(2026, 9, 1).plusDays(it.toLong()) }
        val label: (LocalDate) -> String? = { if (it.dayOfMonth % 2 == 0) "야간" else "주간" }
        val taught = days.map { d -> e(MoneyKind.EXPENSE, 4_000, "편의점", date = d, category = if (label(d) == "야간") "야식" else "생활") } +
            (1..4).map { e(MoneyKind.EXPENSE, 60_000, "주유소", category = "자동차") }
        val l = LedgerSmarts.Learner.of(taught, label)
        assertEquals("야식", l.guess(e(MoneyKind.EXPENSE, 4_000, "편의점", date = LocalDate.of(2026, 10, 10))).first().category)
        assertEquals("생활", l.guess(e(MoneyKind.EXPENSE, 4_000, "편의점", date = LocalDate.of(2026, 10, 11))).first().category)
        // Fewer than 30 lines: nothing said.
        assertTrue(LedgerSmarts.Learner.of(taught.take(LedgerSmarts.Learner.MIN_LINES - 1), label).guess(taught.first()).isEmpty())
    }

    // ---- [LEDX-23] 히트맵 ----

    @Test fun theHeatmapShadesDaysAgainstTheMonth() {
        val d = (1..8).associate { LocalDate.of(2026, 10, it) to it * 10_000L } + (LocalDate.of(2026, 10, 9) to 0L)
        val h = LedgerChecks.heat(d)
        assertEquals(8, h.size)
        assertEquals(1, h[LocalDate.of(2026, 10, 1)])
        assertEquals(4, h[LocalDate.of(2026, 10, 8)])
        assertTrue(h.values.toSet().containsAll(listOf(1, 2, 3, 4)))
        assertTrue(LedgerChecks.heat(emptyMap()).isEmpty())
    }
}
