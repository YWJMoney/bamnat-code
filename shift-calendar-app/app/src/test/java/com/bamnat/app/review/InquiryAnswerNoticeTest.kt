package com.bamnat.app.review

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.company.Inquiry
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.company.InquiryMessage
import com.bamnat.app.company.InquiryReplyJob
import com.bamnat.app.company.InquiryStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * [RVF-02] 개발 전 검토 COMP-41: 답을 한 번 읽고 나면 그 뒤에 오는 답(출시 안내 포함)에 알림이 오지 않았어요.
 * 알림 작업([InquiryReplyJob.sayNow])이 보는 순서 그대로: 답 → 알림 → 읽음 → 답 → 알림.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InquiryAnswerNoticeTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val at = Instant.parse("2026-11-12T00:00:00Z")

    private fun q(answers: Int) = Inquiry(
        id = "q1", kind = InquiryKind.BUG, status = "answered", createdAt = at, updatedAt = at,
        messages = listOf(InquiryMessage(false, "알람이 안 울려요", at)) + List(answers) { InquiryMessage(true, "답 ${it + 1}", at) },
    )

    /** One run of the reply job after the server says [answers]. */
    private fun check(store: InquiryStore, answers: Int): Boolean {
        store.update(listOf(q(answers)))
        return InquiryReplyJob.sayNow(app, store.unread.value)
    }

    @Test fun anAnswerAfterOneWasReadIsToldAgain() {
        val store = InquiryStore(app)
        assertTrue("첫 답은 알려요", check(store, 1))
        assertFalse("같은 답을 다시 알리지 않아요", check(store, 1))
        store.read(q(1), listOf(q(1)))
        assertTrue("읽은 뒤 온 두 번째 답(출시 안내)도 알려요", check(store, 2))
        store.read(q(2), listOf(q(2)))
        assertTrue("세 번째 답도", check(store, 3))
    }

    @Test fun unreadAnswersAreToldOnceAndANewOneAgain() {
        val store = InquiryStore(app)
        assertTrue(check(store, 2))
        assertFalse("안 읽은 두 개는 한 번만", check(store, 2))
        assertTrue("하나 더 오면 알려요", check(store, 3))
    }
}
