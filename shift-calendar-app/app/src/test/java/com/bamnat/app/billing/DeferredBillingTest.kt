package com.bamnat.app.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** 1.36 첫 실행 멈춤 (PC 확인 C12): Play's billing is made on the app's thread when first asked, never by a screen reading the plans. */
class DeferredBillingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val plan = Plan("monthly", "₩4,990", 4_990_000_000, "KRW", "P1M", trial = true, offerToken = "t")

    @After fun tearDown() = scope.cancel()

    @Test fun readingThePlansMakesNothing() {
        var made = 0
        val b = DeferredBilling(scope) { made++; NoBilling(listOf(plan)) }
        assertTrue(b.plans.value.isEmpty())
        assertEquals(BillingState.UNKNOWN, b.state.value)
        assertEquals(0, made)
        assertFalse(b.built)
    }

    @Test fun theFirstRefreshMakesItOnceOffTheCallersThread() = runBlocking {
        val on = AtomicReference<Thread>()
        var made = 0
        val b = DeferredBilling(scope) { made++; on.set(Thread.currentThread()); NoBilling(listOf(plan)) }
        withTimeout(5_000) { b.refresh() }
        withTimeout(5_000) { b.refresh() }
        assertEquals(1, made)
        assertNotEquals(Thread.currentThread(), on.get())
        withTimeout(5_000) { while (b.plans.value.isEmpty()) kotlinx.coroutines.delay(10) }
        assertEquals(listOf(plan), b.plans.value)
        assertEquals(BillingState.READY, b.state.value)
    }

    @Test fun noPlayIsUnavailable() = runBlocking {
        val b = DeferredBilling(scope) { error("no Play") }
        withTimeout(5_000) { b.refresh() }
        withTimeout(5_000) { while (b.state.value == BillingState.UNKNOWN) kotlinx.coroutines.delay(10) }
        assertEquals(BillingState.UNAVAILABLE, b.state.value)
    }
}
