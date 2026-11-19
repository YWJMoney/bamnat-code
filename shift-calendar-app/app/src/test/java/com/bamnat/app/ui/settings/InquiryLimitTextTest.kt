package com.bamnat.app.ui.settings

import com.bamnat.app.company.CompanyException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVQ-62] PC-217: 하루 여섯 번째 문의는 인터넷 탓이 아니라 한도라고 말하고, 기다려도 같은 답이라 줄 세우지 않아요. */
class InquiryLimitTextTest {
    private val limit = CompanyException("rate_limited", retryAfter = 20 * 3_600, why = "device")

    @Test fun theDaysLimitIsNotQueuedAndDoesNotTalkAboutTheInternet() {
        assertFalse("한도는 보내기 대기에 넣지 않아요", InquiryViewModel.queues(limit))
        val text = InquiryViewModel.limitText(limit)
        assertFalse(text, "인터넷" in text)
        assertTrue(text, "문의 보내기" in text && "다시 해 주세요" in text)
    }

    @Test fun internetAndServerTroubleStillWait() {
        for (code in listOf("offline", "server", "busy", "paused", "http_503")) {
            assertTrue(code, InquiryViewModel.queues(CompanyException(code)))
        }
        assertFalse(InquiryViewModel.queues(CompanyException("email")))
    }

    @Test fun sendNowSaysTheLimitWhenTheLimitStoppedIt() {
        assertFalse(InquiryViewModel.stillWaiting(limit).contains("인터넷"))
        assertTrue(InquiryViewModel.stillWaiting(null).contains("인터넷"))
    }
}
