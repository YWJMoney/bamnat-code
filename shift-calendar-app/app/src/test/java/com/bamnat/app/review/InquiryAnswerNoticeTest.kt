package com.bamnat.app.review

import android.Manifest
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.company.Inquiry
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.company.InquiryMessage
import com.bamnat.app.company.InquiryReplyJob
import com.bamnat.app.company.InquiryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * [RVF-02] 개발 전 검토 COMP-41: 답을 한 번 읽고 나면 그 뒤에 오는 답(출시 안내 포함)에 알림이 오지 않았어요.
 * 2.0.0 [RVN-22] I10ba-03: 알린 답은 문의마다 따로 세요(하나로 세던 수는 다른 문의의 안 읽은 답에 걸릴 수 있었어요).
 * 알림 작업([InquiryReplyJob.toTell])이 보는 순서 그대로: 답 → 알림 → 읽음 → 답 → 알림.
 * U10b-01·I10bb-04: 알림에는 답의 첫 줄, 고친 판이 들어온 문의면 '해결됐어요·아직이에요'. COMP-55: 바로 알림 폰은 6시간마다.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InquiryAnswerNoticeTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val at = Instant.parse("2026-11-12T00:00:00Z")

    private fun q(answers: Int, id: String = "q1", asked: String = "알람이 안 울려요", fix: Boolean = false) = Inquiry(
        id = id, kind = InquiryKind.BUG, status = "answered", createdAt = at, updatedAt = at,
        messages = listOf(InquiryMessage(false, asked, at)) + List(answers) { InquiryMessage(true, "답 ${it + 1}\n둘째 줄", at) },
        askFixed = fix,
    )

    /** One run of the reply job after the server says [list]: the ids it tells. */
    private fun check(store: InquiryStore, vararg list: Inquiry, saidBefore: Int = 0): List<String> {
        store.update(list.toList())
        store.startTelling(list.toList(), saidBefore)
        return store.untold(list.toList()).also(store::tell).map { it.id }
    }

    @Test fun anAnswerAfterOneWasReadIsToldAgain() {
        val store = InquiryStore(app)
        assertEquals("첫 답은 알려요", listOf("q1"), check(store, q(1)))
        assertEquals("같은 답을 다시 알리지 않아요", emptyList<String>(), check(store, q(1)))
        store.read(q(1), listOf(q(1)))
        assertEquals("읽은 뒤 온 두 번째 답(출시 안내)도 알려요", listOf("q1"), check(store, q(2)))
        store.read(q(2), listOf(q(2)))
        assertEquals("세 번째 답도", listOf("q1"), check(store, q(3)))
    }

    @Test fun unreadAnswersAreToldOnceAndANewOneAgain() {
        val store = InquiryStore(app)
        assertEquals(listOf("q1"), check(store, q(2)))
        assertEquals("안 읽은 두 개는 한 번만", emptyList<String>(), check(store, q(2)))
        assertEquals("하나 더 오면 알려요", listOf("q1"), check(store, q(3)))
    }

    @Test fun eachQuestionKeepsItsOwnCount() {
        val store = InquiryStore(app)
        assertEquals("두 문의에 함께 온 답은 한 알림에 둘 다", listOf("q1", "q2"), check(store, q(1), q(1, "q2")))
        // q2를 열어 읽고, q1은 안 읽은 채로 q2에 답이 하나 더 와요: q2만 알려요(q1의 안 읽은 답에 걸리지 않음).
        store.read(q(1, "q2"), listOf(q(1), q(1, "q2")))
        assertEquals(listOf("q2"), check(store, q(1), q(2, "q2")))
        assertEquals("안 읽은 q1의 같은 답은 다시 알리지 않아요", emptyList<String>(), check(store, q(1), q(2, "q2")))
        store.forget("q2")
        assertEquals("지운 문의는 세지 않아요", emptyList<String>(), check(store, q(1)))
    }

    @Test fun whatTheOneCountSaidBefore200StaysSaid() {
        val store = InquiryStore(app)
        assertEquals("2.0.0 전에 알린 두 답은 다시 알리지 않아요", emptyList<String>(), check(store, q(2), saidBefore = 2))
        assertEquals("그 뒤에 온 답은 알려요", listOf("q1"), check(store, q(3)))
        val fresh = InquiryStore(app.also { it.getSharedPreferences("inquiry", 0).edit().clear().commit() })
        assertEquals("전에 알린 수가 모자라면 지금 안 읽은 답을 알려요", listOf("q1"), check(fresh, q(2), saidBefore = 1))
    }

    @Test fun howOftenALookComes() {
        val day = Duration.ofDays(1)
        assertEquals("보낸 첫날은 15분", TimeUnit.MINUTES.toMillis(15), InquiryReplyJob.every(false, Duration.ofHours(3)))
        assertEquals("하루 지나면 3시간", TimeUnit.HOURS.toMillis(3), InquiryReplyJob.every(false, day.multipliedBy(2)))
        assertEquals("바로 알림이 오는 폰은 첫날도 6시간", TimeUnit.HOURS.toMillis(6), InquiryReplyJob.every(true, Duration.ZERO))
        assertEquals("일주일 넘게 기다리면 모든 폰이 하루 한 번", TimeUnit.DAYS.toMillis(1), InquiryReplyJob.every(false, day.multipliedBy(8)))
        assertEquals(TimeUnit.DAYS.toMillis(1), InquiryReplyJob.every(true, day.multipliedBy(7)))
    }

    private fun posted(vararg told: Inquiry): android.app.Notification {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        InquiryReplyJob.post(app, told.toList())
        val nm = app.getSystemService(NotificationManager::class.java)
        return shadowOf(nm).allNotifications.single()
    }

    @Test fun oneAnswerSaysItsFirstLineAndAFixedOneAsksOnTheNotice() {
        val n = posted(q(1, fix = true))
        assertEquals("문의에 답이 왔어요", n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("답 1", n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals(listOf("해결됐어요", "아직이에요"), n.actions.orEmpty().map { it.title.toString() })
        assertEquals("첫 줄은 60자까지", 60, InquiryReplyJob.firstLine(q(0).copy(messages = listOf(InquiryMessage(true, "가".repeat(80), at)))).length)
    }

    @Test fun anAnswerBeforeTheFixHasNoButtons() {
        val n = posted(q(1))
        assertTrue(n.actions.isNullOrEmpty())
    }

    @Test fun severalQuestionsAreOneNotice() {
        val n = posted(q(1), q(1, "q2", asked = "급여가 이상해요"))
        assertEquals("문의 2개에 답이 왔어요", n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("‘알람이 안 울려요’ 외 1개 · 눌러서 보기", n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
    }
}
