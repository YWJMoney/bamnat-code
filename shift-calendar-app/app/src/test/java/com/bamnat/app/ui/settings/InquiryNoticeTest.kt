package com.bamnat.app.ui.settings

import com.bamnat.app.company.CompanyText
import com.bamnat.app.company.InquiryClient
import com.bamnat.app.company.InquiryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVO-29] (PC-204): 보내기 위 안내는 고른 종류의 말로 시작하고, 결제·환불 문의는 Play 주문 번호를 받아요. */
class InquiryNoticeTest {
    private fun lead(kind: InquiryKind?, share: Boolean = true) = inquiryNotice(kind, share).lead

    @Test fun eachKindStartsWithItsOwnWords() {
        assertTrue(lead(InquiryKind.BILLING).startsWith("결제·환불 문의는 나만 보고 운영팀이 직접 답해요."))
        assertTrue(lead(InquiryKind.COMPANY).startsWith("회사 방식은 모두의 문의에 올라가지 않고 개발자만 봐요."))
        assertTrue(lead(InquiryKind.STUDIO).startsWith("꾸미기 부탁은 모두의 문의에 올라가지 않고 개발자만 봐요."))
        for (k in listOf(InquiryKind.QUESTION, InquiryKind.BUG, InquiryKind.IDEA, InquiryKind.OTHER, null)) {
            assertTrue("$k", lead(k).startsWith("모두의 문의에 올라가요."))
            assertTrue("$k", lead(k, share = false).startsWith("모두의 문의에 올리지 않고 개발자만 봐요."))
        }
        // 회사 방식 이야기는 회사 방식에만, 결제 문의는 금액 대신 주문 번호를.
        InquiryKind.entries.filter { it != InquiryKind.COMPANY }.forEach { assertFalse("$it", "회사 방식" in lead(it)) }
        assertTrue("Play 주문 번호(GPA.로 시작)" in lead(InquiryKind.BILLING))
        assertFalse("금액은 적지" in lead(InquiryKind.BILLING))
    }

    @Test fun billingKeepsThreeYearsBehindTheInfo() {
        val keep = inquiryNotice(InquiryKind.BILLING, share = false).keep
        assertTrue(keep.startsWith("보낸 문의는 언제든 지울 수 있지만, 결제·환불 문의는 전자상거래법에 따라 마지막 글부터 3년 동안"))
        assertFalse("180일" in keep)
        // 다른 종류의 보관 글은 예전 그대로(180일, 결제 글이 섞이면 3년).
        assertTrue(inquiryNotice(InquiryKind.QUESTION, share = true).keep.contains(INQUIRY_KEEP_WORDS))
    }

    @Test fun aPlayOrderNumberIsNotAnAmount() {
        val vm = InquiryClient
        for (t in listOf("Play 주문 번호는 GPA.3312-4459-2207-48812예요", "gpa.3312-4459-2207-48812..0 환불돼요?")) {
            assertEquals("amount", CompanyText.personal(t))
            assertNull(t, CompanyText.personal(vm.withoutOrder(t)))
        }
        // 금액·다른 번호는 그대로 막혀요(처리방침 1-3항, 서버 inquiryText와 같은 시험 글).
        for (t in listOf("GPA.3312-4459-2207-48812 결제한 13,900원 환불해 주세요", "주문 번호 3312-4459-2207-48812", "GPA.3312-4459-2207-488123")) {
            assertEquals(t, "amount", CompanyText.personal(vm.withoutOrder(t)))
        }
    }
}
