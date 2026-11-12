package com.bamnat.app.company

import com.bamnat.app.ui.company.WayPickWords
import com.bamnat.app.ui.company.wayPickFits
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PAYP-18] 명세서로 맞추기 shows only where a payslip's 기본급 means the monthly base of a company's shared way. */
class WayPickFitsTest {
    @Test fun onlyAMonthlyOrFirstPayOverASharedWayWithTheServer() {
        assertTrue(wayPickFits(shared = true, wage = null, server = true))
        assertTrue(wayPickFits(shared = true, wage = WageType.MONTHLY, server = true))
        // 알바·일당: the payslip's 기본급 is not the 시급 or 일당 the pay keeps.
        assertFalse(wayPickFits(shared = true, wage = WageType.HOURLY, server = true))
        assertFalse(wayPickFits(shared = true, wage = WageType.DAILY, server = true))
        // A way made by hand (not shared), or a build with no server, has no versions to pick from.
        assertFalse(wayPickFits(shared = false, wage = WageType.MONTHLY, server = true))
        assertFalse(wayPickFits(shared = true, wage = WageType.MONTHLY, server = false))
    }

    @Test fun theWordsSayTheWayStaysHiddenAndTheFiguresStayOnThePhone() {
        assertTrue(WayPickWords.PRIVATE.contains("계산 방식은 보이지 않고"))
        assertTrue(WayPickWords.PRIVATE.contains("휴대폰 밖으로 나가지 않아요"))
    }
}
