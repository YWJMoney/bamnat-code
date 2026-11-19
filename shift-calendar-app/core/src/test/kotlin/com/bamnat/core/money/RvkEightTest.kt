package com.bamnat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2.0.0 갈래 K 8차 [RVK-43]: 결제 글 읽기의 오인식 길 (검토 MONEY-54), 정규식 한 번만 (MONEY-61), CSV 분류 칸 (U06b-14). Today in
 * these texts is 2026-11-12. Amounts, cards and shops are made up.
 */
class RvkEightTest {
    private val now = LocalDateTime.of(2026, 11, 12, 21, 0)
    private fun parse(s: String, at: LocalDateTime = now) = (PaymentParser.parse(s, at) as PaymentParser.Result.Ok).parsed

    /** MONEY-54: "ABC마트" is a shop, not BC카드; a Latin issuer still counts on its own. */
    @Test fun aLatinIssuerInsideAShopNameIsNotTheCard() {
        val p = parse("[Web발신]\n승인 59,000원 11/10 14:20 ABC마트 강남점")
        assertNull("ABC마트 is not BC카드", p.issuer)
        assertEquals("ABC마트 강남점", p.merchant)
        assertEquals("BC", parse("[Web발신]\nBC카드(1234)승인 12,000원 11/10 12:00 김밥천국").issuer)
    }

    /** MONEY-54: a bank word that is also a plain word is not an issuer in the middle of a shop's name. */
    @Test fun aPlainBankWordInAShopIsNotTheCard() {
        assertNull(parse("[Web발신]\n승인 30,000원 11/10 10:00 씨티 헬스").issuer)
        assertEquals("씨티", parse("씨티카드(5678) 승인 30,000원 11/10 10:00 메가커피").issuer)
    }

    /** MONEY-54: four digits followed by 원 or a comma are the amount, not a card's end. */
    @Test fun anAmountIsNotTheCardsEnd() {
        val p = parse("[Web발신]\n체크 5000원 승인 11/11 08:10 GS25")
        assertNull("5000원 is the amount", p.last4)
        assertEquals(5_000L, p.amount)
        assertEquals("1234", parse("[Web발신]\n신한체크(1234) 승인 5,000원 11/11 08:10 GS25").last4)
    }

    /** MONEY-54: "1.5%" and "3.5만" are not dates. */
    @Test fun aRateIsNotADate() {
        val p = parse("[Web발신]\n신한카드(1234)승인 35,000원 적립 1.5% 11/11 19:00 올리브영")
        assertEquals(LocalDate.of(2026, 11, 11), p.date)
        val q = parse("[Web발신]\n삼성카드 승인 35,000원 2.5배 적립 올리브영")
        assertNull("2.5배 is no date", q.date)
    }

    /** MONEY-54: a day more than two days ahead is last year's (it fell out of this month's sums as if it were gone). */
    @Test fun aDayAheadIsLastYears() {
        assertEquals(LocalDate.of(2025, 11, 20), parse("[Web발신]\n신한카드(1234)승인 9,000원 11/20 12:00 김밥천국").date)
        assertEquals(LocalDate.of(2026, 11, 13), parse("[Web발신]\n신한카드(1234)승인 9,000원 11/13 12:00 김밥천국").date)
        assertEquals(LocalDate.of(2026, 11, 12), parse("[Web발신]\n신한카드(1234)승인 9,000원 11/12 12:00 김밥천국").date)
        // A December text read in early January.
        assertEquals(LocalDate.of(2025, 12, 31),
            parse("[Web발신]\n신한카드(1234)승인 9,000원 12/31 23:00 김밥천국", LocalDateTime.of(2026, 1, 2, 9, 0)).date)
        // 2월 29일 read in a year without one is the leap year's.
        assertEquals(LocalDate.of(2024, 2, 29),
            parse("[Web발신]\n신한카드(1234)승인 9,000원 2/29 12:00 김밥천국", LocalDateTime.of(2025, 3, 1, 9, 0)).date)
    }

    /** MONEY-61: the shown name is kept, and a new names file is not hidden behind it. */
    @Test fun theShownNameFollowsANewNamesFile() {
        assertEquals("배달의민족", MerchantNames.display("우아한형제들"))
        assertEquals("CU 역삼점", MerchantNames.display("비지에프리테일 역삼점"))
        assertEquals("김밥천국", MerchantNames.display("김밥천국"))
        assertEquals("배달의민족", MerchantNames.display("(주)우아한형제들"))
        MerchantNames.use(null)
        assertEquals("배달의민족", MerchantNames.display("우아한형제들"))
    }

    /** U06b-14: a category the user named like a formula is quoted, as the other text columns are. */
    @Test fun aCategoryLikeAFormulaIsQuotedInTheCsv() {
        val e = MoneyEntry(date = LocalDate.of(2026, 11, 10), kind = MoneyKind.EXPENSE, amount = 12_000, category = "=SUM(A1)", merchant = "가게")
        val csv = LedgerCsv.write(listOf(e), emptyList())
        assertTrue(csv, csv.contains(",'=SUM(A1),"))
        assertNotEquals(-1, csv.indexOf("12000"))
    }

    /** U06b-15: a job's balance is '곧 받을 날' from three days before its 입금 약속일, as a written one is. */
    @Test fun aJobsBalanceIsDueSoonThreeDaysBefore() {
        val job = Owed.FromJob(Job(id = 1, date = LocalDate.of(2026, 11, 1), title = "욕실", amount = 900_000, due = LocalDate.of(2026, 11, 15)))
        assertEquals(ReceivableStatus.DUE_SOON, job.status(LocalDate.of(2026, 11, 12)))
        assertEquals(ReceivableStatus.OPEN, job.status(LocalDate.of(2026, 11, 11)))
        assertEquals(ReceivableStatus.OVERDUE, job.status(LocalDate.of(2026, 11, 16)))
    }

    /** T06b-08: a 야간's 02:00 편의점 is that night's, in 야간 도시락 and in 근무별 돈. */
    @Test fun aNightsSmallHoursSpendingIsTheNights() {
        val start = LocalDate.of(2026, 11, 2)
        val nights = setOf(start, start.plusDays(1))
        fun buy(d: LocalDate, h: Int) = MoneyEntry(date = d, kind = MoneyKind.EXPENSE, amount = 4_800, category = "편의점", merchant = "GS25",
            time = java.time.LocalTime.of(h, 30))
        // 11/3 02:30, during the 야간 of 11/2: the night of 11/2 is missed, and 11/3's own night is kept.
        val s = Challenges.status(Challenges.Run(Challenges.Kind.NIGHT_LUNCHBOX, start), listOf(buy(start.plusDays(1), 2)), emptyList(),
            start.plusDays(3)) { it in nights }
        assertEquals(Challenges.Stamp.MISSED, s.stamps[0])
        assertEquals(Challenges.Stamp.KEPT, s.stamps[1])
        // An evening buy stays on its own day.
        assertEquals(start.plusDays(1), NightSpill.dayOf(buy(start.plusDays(1), 20)) { it in nights })
        assertEquals(start.plusDays(1), NightSpill.dayOf(buy(start.plusDays(1), 2).copy(time = null)) { it in nights })
    }

    /** U06a-28: a finished challenge offers two others, the 야간 one only with a roster. */
    @Test fun aFinishedChallengeOffersTwoMore() {
        assertEquals(listOf(Challenges.Kind.LESS_DELIVERY, Challenges.Kind.NIGHT_LUNCHBOX), Challenges.next(Challenges.Kind.NO_SPEND, night = true))
        assertEquals(listOf(Challenges.Kind.LESS_DELIVERY, Challenges.Kind.ENVELOPE_30), Challenges.next(Challenges.Kind.NO_SPEND, night = false))
        assertTrue(Challenges.Kind.ENVELOPE_30 !in Challenges.next(Challenges.Kind.ENVELOPE_30, night = true))
    }
}
