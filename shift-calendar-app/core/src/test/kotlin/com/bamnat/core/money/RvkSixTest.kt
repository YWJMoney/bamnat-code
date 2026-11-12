package com.bamnat.core.money

import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.pay.PayDayOff
import com.bamnat.core.pay.PayPeriod
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 [RVK-37~41] 갈래 K 6차: the 가계부 로직 rows of the review (검토 06b MONEY-42·44~53·56·58·59·79). Each test is the bug as the
 * review found it, written before the fix. Amounts and names are made up.
 */
class RvkSixTest {
    private val bank = MoneyAccount(id = 1, name = "월급통장", type = AccountType.BANK, opening = 3_000_000)
    private val card = MoneyAccount(id = 2, name = "신용", type = AccountType.CREDIT, payDay = 14, payFromId = 1)
    private val accounts = listOf(bank, card)
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun spend(date: LocalDate, amount: Long, account: Long? = 2, category: String = "식비", installments: Int = 1, id: Long = 0) =
        MoneyEntry(id = id, date = date, kind = MoneyKind.EXPENSE, amount = amount, category = category, accountId = account, installments = installments)
    private fun payCard(date: LocalDate, amount: Long) =
        MoneyEntry(date = date, kind = MoneyKind.TRANSFER, amount = amount, category = "카드값", accountId = 1, toAccountId = 2)

    // ---- MONEY-44 카드값 일부만 갚으면 그 달 청구가 다 갚은 것으로 숨던 것 ----

    @Test fun aPartPaidEarlyLeavesTheRestOfTheBillDue() {
        val lines = listOf(spend(d(10, 10), 1_000_000), payCard(d(11, 1), 300_000))
        val nov = YearMonth.of(2026, 11)
        // Old: one transfer of any amount within two weeks of 11/14 made the whole bill paid and it left 다가오는 돈.
        assertFalse(CardCycle.paid(card, nov, lines, accounts))
        val bill = UpcomingPayments.within(emptyList(), lines, d(11, 10), 7, accounts).single()
        assertEquals(d(11, 14), bill.date)
        assertEquals(700_000L, bill.cost.amount)
        // The rest paid on the day: now it is paid and gone from the list.
        val all = lines + payCard(d(11, 14), 700_000)
        assertTrue(CardCycle.paid(card, nov, all, accounts))
        assertTrue(UpcomingPayments.within(emptyList(), all, d(11, 10), 7, accounts).isEmpty())
    }

    // ---- MONEY-45 결제일의 실제 청구액(연회비·안 적은 줄)과 앱 셈이 다를 때 차액이 다음 달로 넘어가던 것 ----

    @Test fun thePaymentOnTheBillsDayIsThatBillAndNothingCarries() {
        val lines = listOf(spend(d(10, 10), 1_000_000), spend(d(11, 3), 500_000))
        val months = listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12))
        // 1,020,000 with a 연회비 the app did not know: November is paid and December stays whole (old: 480,000).
        val more = CardCycle.left(card, months, lines + payCard(d(11, 14), 1_020_000), accounts, d(11, 20))
        assertEquals(0L, more.getValue(months[0]))
        assertEquals(500_000L, more.getValue(months[1]))
        // 980,000 because one line was never written: still the bill, paid.
        val less = CardCycle.left(card, months, lines + payCard(d(11, 13), 980_000), accounts, d(11, 20))
        assertEquals(0L, less.getValue(months[0]))
        // Less than half on the day is a part payment.
        val part = CardCycle.left(card, months, lines + payCard(d(11, 14), 400_000), accounts, d(11, 20))
        assertEquals(600_000L, part.getValue(months[0]))
    }

    // ---- MONEY-46 카드값을 안 적는 사람의 '갚을 돈'이 달마다 쌓이던 것 ----

    @Test fun billsGonePastTheirDayAreNotOwedForever() {
        val lines = listOf(spend(d(8, 10), 500_000), spend(d(9, 10), 500_000), spend(d(10, 10), 500_000), spend(d(11, 5), 200_000))
        val now = NetWorth.on(accounts, lines, d(11, 20))
        // Old: 1,700,000 owed and the bank untouched. The bills of 9/14, 10/14 and 11/14 left the bank on their days.
        assertEquals(200_000L, now.owe)
        assertEquals(1_500_000L, now.have)
        assertEquals(3, now.unrecorded)
        assertEquals(1_300_000L, now.net)
        // One 카드값 written (9/14): the other two are still counted as paid on their day.
        val one = NetWorth.on(accounts, lines + payCard(d(9, 14), 500_000), d(11, 20))
        assertEquals(200_000L, one.owe)
        assertEquals(2, one.unrecorded)
        assertEquals(1_500_000L, one.have)
        // After a 잔액 맞춤 on the bank (10/20) the bills before it are already in the balance.
        val fixed = MoneyEntry(date = d(10, 20), kind = MoneyKind.EXPENSE, amount = 1_000_000, category = "기타", merchant = MoneyAccounts.ADJUST_NAME, accountId = 1)
        assertEquals(1_500_000L, NetWorth.on(accounts, lines + fixed, d(11, 20)).have)
        // A card without a 결제일 keeps the old sum.
        val noDay = listOf(bank, card.copy(payDay = null))
        assertEquals(1_700_000L, NetWorth.on(noDay, lines, d(11, 20)).owe)
    }

    // ---- MONEY-47 한 번 산 큰 것·할부가 '하루 생활비'를 석 달 동안 부풀리던 것 ----

    @Test fun oneBigLineAndAnInstallmentAreNotEverydaySpending() {
        val days = (0 until 30).map { spend(d(10, 13).plusDays(it.toLong()), 10_000, account = null) }
        val lines = days + spend(d(10, 20), 3_000_000, account = null, category = "가전") + spend(d(10, 25), 600_000, account = null, installments = 6)
        val (perDay, _) = MoneyAhead.everydayPerDay(lines, d(11, 12))
        assertEquals(10_000L, perDay)
    }

    // ---- MONEY-79 월급날이 주말·공휴일이면 ----

    @Test fun payOnAWeekendOrHolidayMovesAsTheUserSays() {
        assertEquals(d(10, 23), PayDayOff.BEFORE.move(d(10, 25)))
        assertEquals(d(10, 26), PayDayOff.AFTER.move(d(10, 25)))
        assertEquals(d(10, 25), PayDayOff.SAME.move(d(10, 25)))
        assertTrue(KoreanHolidays.isHoliday(d(12, 25)))
        assertEquals(d(12, 24), PayDayOff.BEFORE.move(d(12, 25)))
        assertEquals(d(12, 28), PayDayOff.AFTER.move(d(12, 25)))
        val oct = YearMonth.of(2026, 10)
        assertEquals(d(10, 25), PayPeriod(payDay = 25).payDate(oct))
        assertEquals(d(10, 23), PayPeriod(payDay = 25).payDateWorkday(oct))
        assertEquals(d(10, 26), PayPeriod(payDay = 25, payDayOff = PayDayOff.AFTER).payDateWorkday(oct))
        assertEquals(d(11, 25), PayPeriod(payDay = 25).payDateWorkday(YearMonth.of(2026, 11)))
    }

    @Test fun payThatCameEarlyIsNotCountedAgainOnItsDay() {
        val came = MoneyEntry(date = d(10, 23), kind = MoneyKind.INCOME, amount = 2_500_000, category = MoneyCategories.SALARY)
        val book = MoneyBook(entries = listOf(came))
        val (flows, _) = MoneyAhead.flows(book, d(10, 24), d(11, 20), listOf(PayDay(d(10, 25), 2_500_000, "월급")))
        assertTrue(flows.none { it.kind == FlowKind.PAY })
        // Last week's 주급 is not this week's.
        val weekly = MoneyBook(entries = listOf(came.copy(date = d(10, 17), amount = 500_000)))
        val (next, _) = MoneyAhead.flows(weekly, d(10, 23), d(11, 20), listOf(PayDay(d(10, 24), 500_000, "주급")))
        assertEquals(1, next.count { it.kind == FlowKind.PAY })
    }

    // ---- MONEY-49 배달·대리 순수익에 3.3%를 빼지 않던 것, 손으로 적은 유류비가 경비에 안 들던 것 ----

    @Test fun platformNetIsWhatCameInHand() {
        val job = Job(id = 1, date = d(11, 10), title = "쿠팡이츠", client = "쿠팡이츠", amount = 1_000_000, withholding = true)
        val fuel = spend(d(11, 11), 50_000, account = null, category = "유류비")
        val m = PlatformWork.month(YearMonth.of(2026, 11), listOf(job), listOf(fuel))
        assertEquals(33_000L, m.withheld)
        assertEquals(50_000L, m.costs)
        assertEquals(m.netBeforeTax - 33_000, m.net)
        assertEquals(1_000_000L - 50_000 - m.fees - 33_000, m.net)
    }

    // ---- MONEY-52 배달앱이 한 주문을 여러 번 알려 매출이 여러 번 적히던 것 ----

    @Test fun oneDeliveryOrderIsCountedOnce() {
        val sale = ShopCapture.Sale(ShopCapture.Bucket.DELIVERY, 25_000, "배달의민족")
        val t0 = 1_800_000_000_000L
        val min = 60_000L
        val first = ShopCapture.counted("[배달의민족] 주문번호 B1A2C3 새 주문이 들어왔어요 25,000원", sale, t0)
        assertEquals("B1A2C3", first.no)
        assertTrue(ShopCapture.isRepeat("주문번호 B1A2C3 배달 완료 25,000원", sale, t0 + 30 * min, listOf(first)))
        // Another order of the same price with its own number counts.
        assertFalse(ShopCapture.isRepeat("주문번호 Z9Y8X7 새 주문 25,000원", sale, t0 + 3 * min, listOf(first)))
        // Its cancellation counts as one (minus).
        val cancel = sale.copy(amount = -25_000)
        assertFalse(ShopCapture.isRepeat("주문번호 B1A2C3 주문 취소 25,000원", cancel, t0 + 40 * min, listOf(first)))
        // Without a number: a later step of the same amount soon after is the same order, a new order is not.
        val plain = ShopCapture.Counted(null, 25_000, t0)
        assertTrue(ShopCapture.isRepeat("조리 완료 25,000원", sale, t0 + 20 * min, listOf(plain)))
        assertFalse(ShopCapture.isRepeat("새 주문 25,000원", sale, t0 + 5 * min, listOf(plain)))
        assertFalse(ShopCapture.isRepeat("조리 완료 25,000원", sale, t0 + 120 * min, listOf(plain)))
        // A card terminal tells each payment once.
        assertFalse(ShopCapture.isRepeat("승인 25,000원", sale.copy(bucket = ShopCapture.Bucket.CARD), t0 + min, listOf(plain)))
        assertEquals(first, ShopCapture.Counted.decode(first.encode()))
        assertEquals(plain, ShopCapture.Counted.decode(plain.encode()))
        assertNull(ShopCapture.Counted.decode("깨진 줄"))
        assertEquals(ShopCapture.Counted.MAX, (1..400).fold(emptyList<ShopCapture.Counted>()) { l, i -> ShopCapture.remember(l, plain.copy(at = i.toLong())) }.size)
    }

    // ---- MONEY-51 가계약 일감에 '받을 돈이 남았어요' 알림이 가던 것 ----

    @Test fun aTentativeJobIsNotMoneyOwed() {
        val job = Job(id = 7, date = d(11, 1), title = "촬영", client = "OO스튜디오", amount = 300_000, due = d(11, 5), status = JobStatus.TENTATIVE)
        assertTrue(MoneyAlerts.all(listOf(job), emptyList(), emptyList(), d(11, 3)).none { it.key.startsWith("unpaid-") })
        val sure = job.copy(status = JobStatus.CONFIRMED)
        assertEquals(2, MoneyAlerts.all(listOf(sure), emptyList(), emptyList(), d(11, 3)).count { it.key.startsWith("unpaid-") })
    }

    // ---- MONEY-50 부가세 일정이 일반·간이과세를 가리지 않던 것 ----

    @Test fun vatDeadlinesFollowGeneralOrSimple() {
        val from = LocalDate.of(2027, 1, 1)
        val to = LocalDate.of(2027, 12, 31)
        val shop = setOf(WorkStyle.BUSINESS)
        val general = TaxDeadlines.deadlines(from, to, shop, setOf(TaxDeadlines.Vat.GENERAL))
        assertEquals(listOf("부가세 신고", "부가세 예정고지 납부", "종합소득세 신고", "부가세 신고", "부가세 예정고지 납부"), general.map { it.name })
        assertEquals(general, TaxDeadlines.deadlines(from, to, shop))
        val simple = TaxDeadlines.deadlines(from, to, shop, setOf(TaxDeadlines.Vat.SIMPLE))
        assertEquals(listOf("부가세 신고", "종합소득세 신고", "부가세 예정부과 납부"), simple.map { it.name })
        assertTrue(simple.first().text.contains("한 해에 한 번"))
        assertTrue(simple.last().text.contains("신고하지 않아요"))
        // 현장 has only the 종합소득세.
        assertEquals(listOf("종합소득세 신고"), TaxDeadlines.deadlines(from, to, setOf(WorkStyle.FIELD), setOf(TaxDeadlines.Vat.SIMPLE)).map { it.name })
        // The alerts go the same way.
        val alerts = MoneyAlerts.all(emptyList(), emptyList(), emptyList(), LocalDate.of(2027, 4, 1), styles = shop, vat = setOf(TaxDeadlines.Vat.SIMPLE))
        assertTrue(alerts.none { it.title.contains("예정고지") })
    }

    // ---- MONEY-59 자동 기록이 안 됐는데 '가계부에 적었어요'라고 알리던 것, 시계를 앞으로 돌렸다 오면 영영 안 적히던 것 ----

    @Test fun anAutoLineIsWrittenAndToldTruthfully() {
        val cost = FixedCost(id = 1, name = "통신비", amount = 55_000, day = 12, category = "통신", auto = true)
        val today = d(11, 12)
        // The mark went to 12/20 while the clock was ahead: today's line is still written.
        assertEquals(listOf(today), Recurring.due(listOf(cost), emptyList(), today, mapOf(1L to d(12, 20))).map { it.date })
        assertTrue(Recurring.due(listOf(cost), emptyList(), today, mapOf(1L to today)).isEmpty())
        val unwritten = MoneyAlerts.all(emptyList(), listOf(cost), emptyList(), today).single { it.key == "bill-1-$today-0" }
        assertEquals("가계부에 아직 안 적혔어요", unwritten.title)
        val written = MoneyAlerts.all(emptyList(), listOf(cost), listOf(UpcomingPayments.entry(cost, today)), today).single { it.key == "bill-1-$today-0" }
        assertEquals("가계부에 적었어요", written.title)
    }

    // ---- MONEY-48 할부 회차를 결제한 달로 세어 청구 기준 보기와 어긋나던 것 ----

    @Test fun installmentsCountByTheBill() {
        val closes15 = MoneyAccount(id = 3, name = "카드", type = AccountType.CREDIT, payDay = 25, closeDay = 15)
        val tv = spend(d(10, 10), 1_000_000, account = 3, category = "가전", installments = 3)
        val withCard = listOf(closes15)
        // Bought 10/10, the card closes on the 15th and bills on the 25th: the first 회차 is October's bill.
        assertEquals(YearMonth.of(2026, 10), LedgerSmarts.installmentFirst(tv, withCard))
        assertEquals("3개월 할부 · 1/3회 · 남은 2회 666,666원", LedgerSmarts.installmentText(tv, d(10, 20), withCard))
        assertEquals("3개월 할부 · 3/3회 · 다 갚았어요", LedgerSmarts.installmentText(tv, d(12, 26), withCard))
        // Without the card known it is the month after, and before it nothing is said to be paid yet.
        assertEquals("3개월 할부 · 11월부터 청구 · 1,000,000원", LedgerSmarts.installmentText(tv, d(10, 20)))
        assertEquals(1_000_000L, LedgerSmarts.installmentsLeft(listOf(tv), d(10, 20)))
        assertEquals(1_000_000L, LedgerSmarts.installmentsLeft(listOf(tv), d(10, 20), withCard))
        assertEquals(666_666L, LedgerSmarts.installmentsLeft(listOf(tv), d(11, 20), withCard))
        assertEquals(1, LedgerSmarts.installmentOf(tv, YearMonth.of(2026, 10), withCard))
        assertNull(LedgerSmarts.installmentOf(tv, YearMonth.of(2026, 10)))
    }

    // ---- MONEY-53 급여일 주기로 보는 예산의 '넘었어요'가 1일~말일로 셈하던 것 ----

    @Test fun budgetCrossingFollowsThePayCycle() {
        val budgets = listOf(MoneyBudget("식비", 300_000))
        val before = listOf(spend(d(10, 26), 200_000, account = null, id = 1))
        val new = spend(d(11, 3), 150_000, account = null)
        val cycle = PayCycle.Range(d(10, 25), d(11, 24))
        assertTrue(Budgets.crossedBy(new, budgets, before, d(11, 3), cycle).any { it.category == "식비" })
        // By 1일~말일 November has only 150,000.
        assertTrue(Budgets.crossedBy(new, budgets, before, d(11, 3)).none { it.category == "식비" })
    }

    // ---- MONEY-56 '돈 안 쓴 날'이 앱을 쓰기 전 날까지 세던 것 ----

    @Test fun noSpendDaysStartAtTheFirstLine() {
        val lines = listOf(spend(d(11, 10), 10_000, account = null))
        assertEquals("처음 적은 11월 10일부터 3일 중 2일은 적은 지출이 없어요.", LedgerAsk.answer(LedgerAsk.Question.NO_SPEND, lines, d(11, 12)))
        assertEquals("아직 적은 기록이 없어요.", LedgerAsk.answer(LedgerAsk.Question.NO_SPEND, emptyList(), d(11, 12)))
        val older = lines + spend(d(10, 3), 5_000, account = null)
        assertEquals("이번 달 12일 중 11일은 적은 지출이 없어요.", LedgerAsk.answer(LedgerAsk.Question.NO_SPEND, older, d(11, 12)))
    }

    // ---- MONEY-58 일용 세금 끝자리 (갈래 J [RVJ-37]이 지급 한 번 단위로 먼저 고침, 여기는 검토 줄의 예만) ----

    @Test fun dailyWorkerTaxDropsBelowTenWon() {
        assertEquals(DailyWorkerTax.Tax(1_080, 100), DailyWorkerTax.paidDaily(listOf(190_000)))
        assertEquals(DailyWorkerTax.Tax(2_160, 210), DailyWorkerTax.paidTogether(listOf(190_000, 190_000)))
    }

    // ---- MONEY-42 계좌에서 나간 통신비·월세가 '내 계좌로 옮김'으로만 적혀 지출에 안 들던 것 ----

    private val now = LocalDateTime.of(2026, 10, 15, 12, 0)
    private fun bankText(to: String, won: String = "55,000") = "[Web발신]\n신한 10/15 12:03\n110-***-123456\n출금 $won\n잔액 1,000,000\n$to"
    private fun parse(s: String, rules: Map<String, String> = emptyMap()) = (PaymentParser.parse(s, now, rules) as PaymentParser.Result.Ok).parsed

    @Test fun aBankWithdrawalToAKnownKindOfSpendingIsSpending() {
        val phone = parse(bankText("LG유플러스"))
        assertEquals(MoneyKind.EXPENSE, phone.kind)
        assertEquals("통신", phone.category)
        assertTrue(PaymentParser.toEntry(phone, d(10, 15), MoneySource.NOTIFICATION)!!.note.contains(PaymentParser.WITHDRAW_NOTE))
        // A person's name may be the user's own money moving: as before.
        val person = parse(bankText("김철수"))
        assertEquals(MoneyKind.TRANSFER, person.kind)
        assertEquals("내 계좌", person.category)
        // Once the user filed that receiver as 주거 (월세), it is spending; filed as 저축 it stays a move.
        assertEquals(MoneyKind.EXPENSE, parse(bankText("김철수"), mapOf("김철수" to "주거")).kind)
        assertEquals("주거", parse(bankText("김철수"), mapOf("김철수" to "주거")).category)
        assertEquals(MoneyKind.TRANSFER, parse(bankText("김철수"), mapOf("김철수" to "저축")).kind)
        // A card bill is still a move.
        assertEquals("카드값", parse("[Web발신]\n우리 10/25 출금 1,234,560 신한카드대금 잔액 500,000").category)
    }

    @Test fun aCheckCardAndTheBanksSpendingLineAreOnePayment() {
        val bankLine = PaymentParser.toEntry(parse(bankText("GS25역삼", "12,000")), d(10, 15), MoneySource.NOTIFICATION)!!.copy(id = 2)
        assertEquals(MoneyKind.EXPENSE, bankLine.kind)
        val cardLine = MoneyEntry(id = 1, date = d(10, 15), kind = MoneyKind.EXPENSE, amount = 12_000, category = "편의점", merchant = "GS25역삼",
            time = LocalTime.of(12, 0), cardLast4 = "1234", source = MoneySource.NOTIFICATION)
        assertNotNull(LedgerSmarts.crossDuplicate(bankLine, listOf(cardLine)))
        assertEquals(cardLine, LedgerSmarts.keepOf(bankLine, cardLine))
        assertEquals(cardLine, LedgerSmarts.keepOf(cardLine, bankLine))
        // Two card payments of the same amount at the same shop are not one.
        assertNull(LedgerSmarts.crossDuplicate(cardLine.copy(id = 3, time = LocalTime.of(12, 4)), listOf(cardLine)))
    }

    @Test fun aWithdrawalToARepeatingSpendIsThatSpending() {
        // The user's 반복 지출 '에듀윌' (교육): a 출금 to it is that spending, though the built-in words do not know the name.
        assertEquals(MoneyKind.TRANSFER, parse(bankText("에듀윌")).kind)
        val p = (PaymentParser.parse(bankText("에듀윌"), now, emptyMap(), mapOf("에듀윌" to "교육")) as PaymentParser.Result.Ok).parsed
        assertEquals(MoneyKind.EXPENSE, p.kind)
        assertEquals("교육", p.category)
        // A rule learned from a 지출 기타 line keeps it spending.
        assertEquals(MoneyKind.EXPENSE, parse(bankText("김철수"), mapOf("김철수" to MoneyCategories.OTHER)).kind)
    }

    @Test fun aWithdrawalToSomeoneElseIsAskedOnce() {
        val line = PaymentParser.toEntry(parse(bankText("김철수")), d(10, 15), MoneySource.NOTIFICATION)!!.copy(id = 5)
        val me = listOf("홍길동")
        assertTrue(LedgerSmarts.spentOrMoved(line, me, emptyMap()))
        // Not for the user's own name, a receiver with a rule, a card's 출금, or what is already spending.
        assertFalse(LedgerSmarts.spentOrMoved(line.copy(merchant = "홍*동"), me, emptyMap()))
        assertFalse(LedgerSmarts.spentOrMoved(line, me, mapOf("김철수" to LedgerSmarts.MOVE_CATEGORY)))
        assertFalse(LedgerSmarts.spentOrMoved(line.copy(merchant = "체크카드출금"), me, emptyMap()))
        val spent = LedgerSmarts.asSpending(line, "주거")
        assertFalse(LedgerSmarts.spentOrMoved(spent, me, emptyMap()))
        assertEquals(MoneyKind.EXPENSE, spent.kind)
        assertEquals("주거", spent.category)
        assertTrue(LedgerSmarts.fromWithdraw(spent))
        assertEquals(1, LedgerSmarts.asSpending(spent, "주거").note.split(" · ").count { it == PaymentParser.WITHDRAW_NOTE })
    }
}
