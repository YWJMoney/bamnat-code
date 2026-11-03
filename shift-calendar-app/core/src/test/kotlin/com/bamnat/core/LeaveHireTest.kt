package com.bamnat.core

import com.bamnat.core.leave.LeaveSources
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [LEAVE-14] 1.36 S11: a hire date typed on the 연차 screen counts without a pay setup, and a pay setup's own date wins. */
class LeaveHireTest {
    private val hire = LocalDate.of(2024, 3, 2)
    private val pay = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline())

    @Test fun aHireDateAloneCountsTheLawsLeave() {
        val p = LeaveSources.withHire(null, hire)
        assertNull(LeaveSources.noAuto(p))
        assertTrue(LeaveSources.autoLots(p, LocalDate.of(2026, 10, 31)).sumOf { it.days } > 15.0)
    }

    @Test fun thePaySetupsOwnDateWins() {
        val own = LocalDate.of(2020, 1, 1)
        assertEquals(own, LeaveSources.withHire(pay.copy(hireDate = own), hire)!!.hireDate)
        assertEquals(hire, LeaveSources.withHire(pay, hire)!!.hireDate)
        assertNull(LeaveSources.withHire(null, null))
    }
}
