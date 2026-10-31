package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bamnat.app.billing.ProductIds
import com.bamnat.core.tier.PlaySub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 1.36 841 1년 이용권 on the phone, and 840·841's notice 7 days before a yearly renewal or a pass's end. */
@RunWith(RobolectricTestRunner::class)
class YearPassAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.parse("2027-01-01T00:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")

    @Test fun passesStackAreKeptAndARefundTakesOneAway() {
        context.getSharedPreferences("pro", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ProStore(context)
        store.addYearPass("GPA.1", t0)
        store.addYearPass("GPA.1", t0)
        store.addYearPass("GPA.2", t0.plus(Duration.ofDays(340)))
        assertEquals(t0.plus(Duration.ofDays(730)), ProStore(context).current.yearPassUntil)
        store.removeYearPass("GPA.2")
        assertEquals(t0.plus(Duration.ofDays(365)), ProStore(context).current.yearPassUntil)
        assertEquals(t0.plus(Duration.ofDays(365)), store.current.sources(com.bamnat.app.tier.LaunchConfig(), null, zone, true).yearPassUntil)
    }

    @Test fun theEndNoticeIsForAYearlyRenewalOrAPass() {
        val now = t0.plus(Duration.ofDays(10))
        val yearly = SubRecord(ProductIds.PRO, ProductIds.YEARLY, PlaySub.State.ACTIVE, t0.plus(Duration.ofDays(365)), false, true, t0, true, now)
        assertEquals(t0.plus(Duration.ofDays(365)) to false, EndNotice.end(ProState(sub = yearly), now))
        assertNull("a monthly plan has no yearly notice", EndNotice.end(ProState(sub = yearly.copy(basePlan = ProductIds.MONTHLY)), now))
        assertNull("a trial has its own", EndNotice.end(ProState(sub = yearly.copy(trial = true)), now))
        assertEquals(t0.plus(Duration.ofDays(365)) to true, EndNotice.end(ProState(yearPasses = mapOf("GPA.1" to t0)), now))
        val (title, text) = EndNotice.words(t0.plus(Duration.ofDays(365)), pass = false, found = 68_400, zone = zone)
        assertEquals("프로 연 요금제가 1월 1일에 갱신돼요", title)
        assertTrue(text.startsWith("올해 밤낮이 찾아 준 돈 68,400원 · 광고 0편."))
        assertTrue(EndNotice.words(t0, pass = true, found = 0, zone = zone).second.contains("자동 결제가 없어서"))
    }
}
