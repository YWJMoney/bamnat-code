package com.bamnat.core

import com.bamnat.core.tier.InviteRules
import com.bamnat.core.tier.InviteTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** [INVITE-02][INVITE-03] The invite code, the Play referrer that carries it and the days a code can be typed. */
class InviteRulesTest {
    @Test fun aCodeIsEightPlainLettersHoweverItIsTyped() {
        assertEquals("ABCD2345", InviteRules.code(" abcd-2345 "))
        assertEquals("ABCD2345", InviteRules.code("ABCD 2345"))
        assertNull(InviteRules.code("ABCD234"))
        // 0, O, 1 and I are never in a code.
        assertNull(InviteRules.code("ABCD0O1I"))
        assertNull(InviteRules.code(null))
        assertEquals("ABCD-2345", InviteRules.shown("ABCD2345"))
    }

    @Test fun theReferrerCarriesTheCodeOnlyFromAnInviteLink() {
        // As the company server's storeLink puts it, and as Play gives it back (decoded once).
        assertEquals("ABCD2345", InviteRules.fromReferrer("utm_source=bamnat_invite&utm_content=ABCD2345"))
        assertEquals("ABCD2345", InviteRules.fromReferrer("utm_content=abcd2345&utm_source=bamnat_invite&utm_medium=friend"))
        // A store search or another campaign.
        assertNull(InviteRules.fromReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(InviteRules.fromReferrer("utm_source=other&utm_content=ABCD2345"))
        assertNull(InviteRules.fromReferrer("utm_source=bamnat_invite&utm_content=nope"))
        assertNull(InviteRules.fromReferrer(""))
        assertNull(InviteRules.fromReferrer(null))
    }

    @Test fun aCodeCanBeTypedForSevenDaysAfterTheInstall() {
        val installed = Instant.parse("2026-10-25T00:00:00Z")
        assertTrue(InviteRules.mayType(installed, installed))
        assertTrue(InviteRules.mayType(installed, installed.plus(Duration.ofDays(7))))
        assertFalse(InviteRules.mayType(installed, installed.plus(Duration.ofDays(7)).plusSeconds(1)))
        assertFalse("unknown install time", InviteRules.mayType(null, installed))
        assertTrue(InviteRules.mayReadReferrer(installed, installed.plus(Duration.ofDays(90))))
        assertFalse(InviteRules.mayReadReferrer(installed, installed.plus(Duration.ofDays(91))))
    }

    @Test fun theWordsSayTheLinkTheCodeAndTheCount() {
        val text = InviteRules.shareText("https://company.example/i/ABCD2345", "ABCD2345")
        assertTrue(text.contains("https://company.example/i/ABCD2345"))
        assertTrue(text.contains("ABCD-2345"))
        assertTrue("no store review in it", !text.contains("리뷰") && !text.contains("별점"))
        assertEquals("초대한 친구 2명 · 프로 2개월", InviteRules.summary(2, 2))
        assertEquals("내 초대 코드는 넣을 수 없어요", InviteRules.refused("self"))
    }

    /** [INVITE-08] The server's rules as the inviter reads them, and how far the friends are. */
    @Test fun theRulesAndTheProgressAreSaidPlainly() {
        assertEquals("3/5명 · 2명은 사용 기간 채우는 중", InviteRules.progress(3, 5, 2))
        assertEquals("0/5명", InviteRules.progress(0, 5, 0))
        // AG 1.27 [INVITE-09] 리뷰 2026-10-25: months for each five friends who each used it seven days, not "7일 넘게 쓸 때마다".
        assertEquals("친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또 1개월(상한 없음)", InviteTerms.DEFAULT.words())
        assertEquals("친구 3명이 각자 10일 이상 쓰면 나에게 프로 2개월, 3명 더 채울 때마다 또 2개월(1년에 6개월까지)", InviteTerms(3, 10, 2, 6).words())
        assertTrue(!InviteTerms.DEFAULT.words().contains("넘게 쓸 때마다"))
        assertEquals("친구 5명이 7일 쓰면 프로 1개월", InviteTerms.DEFAULT.short())
        assertEquals("다음 프로까지 2명", InviteRules.toNext(3, 5))
        assertEquals("다음 프로까지 5명", InviteRules.toNext(0, 5))
    }

    /** 2.0.0 [RVO-13] 검토 HQ-48: the one-line promise on top of 프로, its long form behind '자세히', and what came of it in one line. */
    @Test fun thePromiseIsOneLineInTheServersRules() {
        assertEquals("친구 5명이 7일 쓰면 프로 1개월", InviteRules.promise(InviteTerms.DEFAULT))
        assertEquals("친구 5명이 7일 쓰면 프로 1개월 · 1년에 12개월까지", InviteRules.promise(InviteTerms(5, 7, 1, 12)))
        val long = InviteRules.details(InviteTerms(5, 7, 1, 12))
        assertTrue(long, long.contains("운영팀이 확인한 뒤"))
        assertFalse("출시 뒤라 '정식 출시 전' 말은 없어요", long.contains("정식 출시"))
        assertNull(InviteRules.status(0, 0, 0))
        assertEquals("받은 프로 2개월 · 확인 기다리는 1개월 · 기다리는 친구 3명", InviteRules.status(2, 1, 3))
        assertEquals("기다리는 친구 1명", InviteRules.status(0, 0, 1))
    }

    /** 2.0.0 [RVO-13] 통합 2차 PC-114: 1개월 받고 11개월이 확인 전이면 '모두 받았어요'가 아니에요. */
    @Test fun theCapSaysWhatStillWaits() {
        val waiting = InviteRules.capped(12, 11)
        assertFalse(waiting, waiting.contains("모두 받았어요"))
        assertEquals("프로 11개월은 운영팀이 확인한 뒤 들어와요. 확인이 끝나면 올해 받을 수 있는 12개월이 모두 차요. 친구는 계속 셀게요.", waiting)
        assertEquals("올해 받을 수 있는 12개월을 모두 받았어요. 친구는 계속 셀게요.", InviteRules.capped(12, 0))
    }
}
