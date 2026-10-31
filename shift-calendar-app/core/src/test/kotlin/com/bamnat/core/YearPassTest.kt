package com.bamnat.core

import com.bamnat.core.tier.ProAccess
import com.bamnat.core.tier.ProReason
import com.bamnat.core.tier.ProSources
import com.bamnat.core.tier.YearPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 1.36 841 1년 이용권: 365 days each, stacked, no automatic payment, after a subscription and an admin phone, before reward passes. */
class YearPassTest {
    private val t0 = Instant.parse("2027-01-01T00:00:00Z")
    private fun d(n: Long) = Duration.ofDays(n)

    @Test fun eachPassAddsAYearFromItsPurchaseOrTheEndBefore() {
        assertNull(YearPass.until(emptyList()))
        assertEquals(t0.plus(d(365)), YearPass.until(listOf(t0)))
        assertEquals("bought again before the end: it goes on", t0.plus(d(730)), YearPass.until(listOf(t0.plus(d(340)), t0)))
        assertEquals("bought after it ended: a year from then", t0.plus(d(400 + 365)), YearPass.until(listOf(t0, t0.plus(d(400)))))
        assertEquals(t0.plus(d(358)), YearPass.noticeAt(YearPass.until(listOf(t0))))
        assertFalse(YearPass.rebuy(t0.plus(d(365)), t0.plus(d(334))))
        assertTrue(YearPass.rebuy(t0.plus(d(365)), t0.plus(d(335))))
    }

    @Test fun thePassGivesProWithItsEnd() {
        val until = t0.plus(d(365))
        val a = ProAccess.resolve(ProSources(billingLive = true, yearPassUntil = until, passUntil = t0.plus(d(800))), t0.plus(d(10)), ZoneId.of("Asia/Seoul"))
        assertEquals(ProReason.PASS_1Y, a.reason)
        assertEquals(until, a.until)
        assertFalse(ProAccess.resolve(ProSources(billingLive = true, yearPassUntil = until), until, ZoneId.of("Asia/Seoul")).pro)
    }
}
