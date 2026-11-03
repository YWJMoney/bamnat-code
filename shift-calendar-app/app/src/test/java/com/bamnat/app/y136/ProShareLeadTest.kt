package com.bamnat.app.y136

import com.bamnat.app.tier.Gate
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pro.shareLead
import com.bamnat.core.tier.Access
import com.bamnat.core.tier.ProReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36 사용자 결정 2026-11-01: a trial shares nothing yet and says it will once paid; a paid one can share. */
class ProShareLeadTest {
    @Test fun aTrialIsToldItSharesOncePaid() {
        val trial = Gate(live = true, tier = Tier.PRO, access = Access(true, ProReason.TRIAL))
        assertFalse(trial.boughtPro)
        assertTrue(shareLead(trial).startsWith("체험이 끝나고 결제되면 1명에게"))
        val free = Gate(live = true, tier = Tier.FREE, access = Access(false, ProReason.NONE))
        assertTrue(shareLead(free).startsWith("프로를 결제하면 1명에게"))
        assertTrue(Gate(live = true, tier = Tier.PRO, access = Access(true, ProReason.SUBSCRIPTION)).boughtPro)
    }
}
