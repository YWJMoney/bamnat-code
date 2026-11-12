package com.bamnat.app.review

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.guide.OpenAsk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVH-61] 검토 PC-131: 1.39.0에서 쓰던 폰에 2.0.0을 덮어 깔고 처음 열자 달력보다 먼저 Play 별점·리뷰 창이 떴어요(패턴 저장 + 첫 달
 * 마무리 카드를 닫은 폰은 내 달력을 여는 순간 물었음). 판이 바뀐 뒤 첫 방문, 처리방침 카드가 묻는 동안, 여는 창(업데이트 소식)이 뜬
 * 방문에는 묻지 않고, 물을 때는 그대로 남아 다음에 내 달력을 열 때 물어요.
 */
@RunWith(AndroidJUnit4::class)
class InAppReviewHoldTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun prefs() = context.getSharedPreferences("in_app_review", Context.MODE_PRIVATE)

    @Before fun clean() { InAppReview.resetForTest(); prefs().edit().clear().commit() }
    @After fun after() { InAppReview.resetForTest() }

    @Test fun notOnTheFirstVisitOfANewVersion() {
        prefs().edit().putInt("run_code", 53).commit()
        assertTrue("1.39.0(53) → 2.0.0(54) 처음 연 방문", InAppReview.firstRunNow(context, code = 54, visit = 0))
        assertTrue("같은 방문 안에서는 계속", InAppReview.firstRunNow(context, code = 54, visit = 0))
        assertFalse("30분 뒤·다음 날 다시 열면(다음 방문) 물어요", InAppReview.firstRunNow(context, code = 54, visit = 1))
        assertEquals("판은 바로 적어 둠", 54, prefs().getInt("run_code", 0))
        // A new process of the same version is not a first run.
        InAppReview.resetForTest()
        assertFalse(InAppReview.firstRunNow(context, code = 54, visit = 0))
    }

    @Test fun heldWhileTheAppHasSomethingOfItsOwnToSay() {
        assertFalse(InAppReview.held(firstRunOfVersion = false, policyCard = false))
        assertTrue("판 올림 뒤 첫 방문", InAppReview.held(firstRunOfVersion = true, policyCard = false))
        assertTrue("처리방침 카드가 묻는 동안", InAppReview.held(firstRunOfVersion = false, policyCard = true))
        assertTrue("다른 날에 들어갔을 수 있는 근무 카드(PC-72)가 떠 있는 동안", InAppReview.held(firstRunOfVersion = false, policyCard = false, slipCard = true))
        assertTrue(InAppReview.firstRunOfVersion(lastCode = 0, code = 54))
        assertFalse(InAppReview.firstRunOfVersion(lastCode = 54, code = 54))
    }

    @Test fun itWaitsForTheOpeningWindowsAndDoesNotFollowThemInTheSameVisit() {
        val both = listOf(OpenAsk.Want(OpenAsk.WHATS_NEW), OpenAsk.Want(OpenAsk.REVIEW))
        assertEquals("업데이트 소식이 먼저", OpenAsk.WHATS_NEW, OpenAsk.pick(both, null, emptySet(), busy = false))
        assertNull("업데이트 소식이 뜬 방문에는 리뷰 창 없음", OpenAsk.pick(listOf(OpenAsk.Want(OpenAsk.REVIEW)), null, setOf(OpenAsk.WHATS_NEW), busy = false))
        assertNull("시트·적는 중에는 없음", OpenAsk.pick(listOf(OpenAsk.Want(OpenAsk.REVIEW)), null, emptySet(), busy = true))
        assertEquals("다음 방문에", OpenAsk.REVIEW, OpenAsk.pick(listOf(OpenAsk.Want(OpenAsk.REVIEW)), null, emptySet(), busy = false))
    }

    @Test fun aHeldMomentStaysDue() {
        val s = com.bamnat.app.data.AppSettings(reviewDue = true, monthRecapSeen = 1)
        val today = java.time.LocalDate.of(2026, 11, 12)
        // Holding reads nothing it would change: the moment is due before and after, until the ask itself marks it.
        assertTrue(InAppReview.held(InAppReview.firstRunNow(context, code = 54, visit = 0), policyCard = false))
        assertEquals(InAppReview.Moment.FIRST, InAppReview.due(s, today))
    }
}
