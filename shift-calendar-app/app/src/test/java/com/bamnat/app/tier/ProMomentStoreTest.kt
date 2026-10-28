package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bamnat.app.ui.pro.billingWords
import com.bamnat.core.tier.ProMoments
import com.bamnat.core.tier.ProReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** 1.32 [PROX-02][PROX-03]: the money-day offer shows all of its day once, and the 설정 결제 line says what is taken and when. */
@RunWith(RobolectricTestRunner::class)
class ProMomentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun anOfferStaysItsDayThenWaitsItsSeason() {
        context.getSharedPreferences("pro_moments", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ProMomentStore(context)
        val day = LocalDate.of(2026, 9, 10)
        fun due(on: LocalDate) = store.today(store.state.value, on) {
            ProMoments.due(on, null, payday = true, rosterToday = false, offered = store.state.value.offered, lastOffer = store.state.value.lastOffer)
        }
        assertEquals(ProMoments.Moment.PAYDAY, due(day))
        store.shown(ProMoments.Moment.PAYDAY, day)
        // Shown: the same card all day, even though its season is now used.
        assertEquals(ProMoments.Moment.PAYDAY, due(day))
        assertNull(due(day.plusDays(1)))
        store.closed(day)
        assertNull(due(day))
        // A new phone file reads it back.
        val again = ProMomentStore(context)
        assertEquals(day, again.state.value.lastOffer)
        assertTrue(ProMoments.seasonOf(ProMoments.Moment.PAYDAY, day) in again.state.value.offered)
        again.rosterIn(day)
        assertEquals(day, ProMomentStore(context).state.value.rosterOn)
    }

    @Test fun theBillingLineSaysTheNextPaymentAndTheTrialEnd() {
        val d = LocalDate.of(2026, 11, 28)
        assertEquals("프로 구독 중 · 다음 결제 11월 28일 · 월 3,900원", billingWords(ProReason.SUBSCRIPTION, d, true, "월 3,900원"))
        assertEquals("구독을 해지했어요 · 11월 28일까지 써요", billingWords(ProReason.SUBSCRIPTION, d, false, "월 3,900원"))
        assertEquals("무료 체험 11월 28일에 끝나요 · 그 뒤 월 3,900원 결제", billingWords(ProReason.TRIAL, d, true, "월 3,900원"))
        assertTrue(billingWords(ProReason.GRACE, d, true, null)!!.contains("결제 수단"))
        assertNull(billingWords(ProReason.DAY_PASS, d, false, null))
        assertNull(billingWords(ProReason.SUBSCRIPTION, null, true, null))
    }
}
