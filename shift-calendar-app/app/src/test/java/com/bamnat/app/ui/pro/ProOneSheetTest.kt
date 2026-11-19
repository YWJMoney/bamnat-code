package com.bamnat.app.ui.pro

import com.bamnat.app.billing.Plan
import com.bamnat.app.billing.ProductIds
import com.bamnat.app.tier.TrialNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 2.0.0 [RVO-05][RVO-06][RVO-07] 프로 화면 한 장·받은 코드 한 칸·결제 고지의 말. */
class ProOneSheetTest {
    private val monthly = Plan(ProductIds.MONTHLY, "4,990원", 4_990_000_000, "KRW", "P1M", trial = false, offerToken = "m")
    private val yearly = Plan(ProductIds.YEARLY, "39,000원", 39_000_000_000, "KRW", "P1Y", trial = true, offerToken = "y")

    @Test fun aCodeIsToldByItsShape() {
        assertEquals(CodeKind.LINK, codeKind("123 456"))
        assertEquals(CodeKind.LINK, codeKind("123456"))
        assertEquals(CodeKind.RECOVERY, codeKind("ABCD-EFGH-2345-6789"))
        assertEquals(CodeKind.RECOVERY, codeKind("abcdefgh23456789"))
        assertEquals(CodeKind.EIGHT, codeKind("abcd-2345"))
        // 0·O·1·I are not in the server's eight letters, so this is no invite or 나눔 code.
        assertEquals(CodeKind.NONE, codeKind("ABC0-1234"))
        assertEquals(CodeKind.NONE, codeKind("12345"))
        assertEquals(CodeKind.NONE, codeKind("1234567"))
    }

    @Test fun anEightLetterCodeSaysWhyItCannotGoIn() {
        assertNull(eightWhy(invite = true, share = false, bought = false, given = false, reported = false))
        assertNull(eightWhy(invite = false, share = true, bought = false, given = false, reported = false))
        assertTrue(eightWhy(false, false, bought = true, given = false, reported = false)!!.contains("직접 산 프로"))
        assertTrue(eightWhy(false, false, bought = false, given = false, reported = true)!!.contains("이미 알렸어요"))
        assertTrue(eightWhy(false, false, bought = false, given = false, reported = false)!!.contains("7일 안에만"))
    }

    @Test fun guessingWaitsAfterFiveWrongCodes() {
        ProCodeTries.reset()
        repeat(ProCodeTries.WRONG_MAX - 1) { ProCodeTries.missed(now = 1_000L) }
        assertTrue(!ProCodeTries.waiting(now = 1_000L))
        ProCodeTries.missed(now = 1_000L)
        assertTrue(ProCodeTries.waiting(now = 1_000L))
        assertTrue(!ProCodeTries.waiting(now = 1_000L + ProCodeTries.WAIT_MS))
        ProCodeTries.reset()
    }

    @Test fun thePaywallAlwaysShowsThePrice() {
        assertEquals("월 4,990원 · 연 39,000원(34% 할인)", priceLine(listOf(monthly, yearly)))
        assertEquals("연 39,000원", priceLine(listOf(yearly)))
        assertNull(priceLine(emptyList()))
        assertTrue(PAYWALL_REST.contains("나머지는 모두 무료"))
    }

    @Test fun theButtonsSayTheRightParticle() {
        // 검토 PRO-13: "월 4,990원로 구독" → "월 4,990원으로 구독".
        assertEquals("으로", com.bamnat.app.ui.Fmt.ro("월 4,990원"))
        assertEquals("으로", com.bamnat.app.ui.Fmt.ro("39,000원"))
        // 통합 4차 PC O31 (에뮬레이터에는 가격이 오지 않아요): the button with the price Play gives.
        assertEquals("월 4,990원으로 구독", subscribeWords("월 4,990원", trial = false))
        assertEquals("연 39,000원으로 구독", subscribeWords("연 39,000원", trial = false))
        assertEquals("월 US\$3.99로 구독", subscribeWords("월 US\$3.99", trial = false))
        assertEquals("1개월 무료로 시작 (연 요금제)", subscribeWords("연 39,000원", trial = true))
    }

    @Test fun theLastTrialNoticeCountsTheDays() {
        val end = LocalDate.of(2027, 4, 10)
        assertEquals("밤낮 프로 무료 체험이 3일 뒤(4월 10일) 끝나요", TrialNotice.finalTitle("밤낮 프로", end, LocalDate.of(2027, 4, 7)))
        assertEquals("밤낮 프로 무료 체험이 내일(4월 10일) 끝나요", TrialNotice.finalTitle("밤낮 프로", end, LocalDate.of(2027, 4, 9)))
        assertEquals("밤낮 프로 무료 체험이 오늘(4월 10일) 끝나요", TrialNotice.finalTitle("밤낮 프로", end, end))
    }

    @Test fun theShareCodeMessageSaysWhereItGoes() {
        assertTrue(shareCodeText("ABCD2345").contains("‘$CODE_INBOX_TITLE’"))
        assertTrue(shareCodeText("ABCD2345").contains("ABCD-2345"))
        assertTrue(com.bamnat.core.tier.InviteRules.shareText("x", "ABCD2345").contains(CODE_INBOX_TITLE))
    }

    /** [RVO-13] HQ-58: the 1년 12개월 line only while the server counts it, from 처리방침 3.13's 30-day date (12-12). */
    /** 2.0.0 [RVO-15] 검토 PRO-06·U11b-06: '구매 복원' says what came back, or that Play did not answer. */
    @Test fun restoreSaysWhatItFound() {
        val now = java.time.Instant.parse("2027-03-10T01:00:00Z")
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val t0 = now.minus(java.time.Duration.ofDays(10))
        val none = com.bamnat.app.tier.ProState()
        assertEquals("Play에 연결하지 못했어요. 인터넷과 Play 스토어 로그인을 확인해 주세요",
            restoreWords(com.bamnat.app.billing.BillingState.UNAVAILABLE, none, now, zone))
        assertEquals("이 Google 계정에서 찾은 구매가 없어요", restoreWords(com.bamnat.app.billing.BillingState.READY, none, now, zone))
        assertEquals("1년 이용권을 다시 찾았어요 · 2월 28일까지 써요",
            restoreWords(com.bamnat.app.billing.BillingState.READY, none.copy(yearPasses = mapOf("GPA.1" to t0)), now, zone))
        val sub = com.bamnat.app.tier.SubRecord(ProductIds.PRO, ProductIds.YEARLY, com.bamnat.core.tier.PlaySub.State.ACTIVE,
            now.plus(java.time.Duration.ofDays(30)), false, true, t0, true, now)
        assertEquals("구독을 다시 찾았어요", restoreWords(com.bamnat.app.billing.BillingState.READY, none.copy(sub = sub), now, zone))
    }

    /** 2.0.0 [RVO-15] 검토 PRO-07: a subscription beside 프로 already open until a day says so first. */
    @Test fun aSubscriptionOverAPaidPeriodSaysItOverlaps() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        fun access(r: com.bamnat.core.tier.ProReason, until: String? = "2027-12-05T03:00:00Z") =
            com.bamnat.core.tier.Access(true, r, until?.let { java.time.Instant.parse(it) })
        assertEquals("12월 5일까지 1년 이용권으로 프로를 써요. 지금 구독하면 그 기간과 겹쳐요.", overlapWords(access(com.bamnat.core.tier.ProReason.PASS_1Y), zone))
        assertEquals("12월 5일까지 감사 기간으로 프로를 써요. 지금 구독하면 그 기간과 겹쳐요.", overlapWords(access(com.bamnat.core.tier.ProReason.THANKS), zone))
        assertEquals("12월 5일까지 나눠 받은 프로를 써요. 지금 구독하면 그 기간과 겹쳐요.", overlapWords(access(com.bamnat.core.tier.ProReason.SHARED), zone))
        assertTrue(overlapWords(access(com.bamnat.core.tier.ProReason.ADMIN, null), zone)!!.startsWith("관리자 휴대폰이라"))
        assertNull("nothing to overlap", overlapWords(com.bamnat.core.tier.Access(false, com.bamnat.core.tier.ProReason.NONE), zone))
        assertNull("today only", overlapWords(access(com.bamnat.core.tier.ProReason.DAY_PASS), zone))
        assertNull("already subscribed: the plans do not show", overlapWords(access(com.bamnat.core.tier.ProReason.SUBSCRIPTION), zone))
    }

    @Test fun thePassesCapLineWaitsForTheServersCap() {
        val d = com.bamnat.core.ops.PolicyConsent.REVISION_EFFECTIVE_30
        val noCap = com.bamnat.core.tier.InviteTerms(need = 5, days = 7, months = 1, capMonths = 0)
        val cap = noCap.copy(capMonths = 12)
        val plain = "운영팀이 확인한 뒤 드려요."
        assertEquals(plain, passesHead(null, d))
        assertEquals("the live server today: capMonths 0", plain, passesHead(noCap, d.plusDays(10)))
        assertEquals("a cap only for 친구 초대 before 12-12 is not one for all rewards", plain, passesHead(noCap.copy(capMonths = 6), d.minusDays(1)))
        assertEquals("운영팀이 확인한 뒤 드려요. 1년에 모두 합쳐 12개월까지예요.", passesHead(cap, d))
    }
}
