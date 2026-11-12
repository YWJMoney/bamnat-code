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
