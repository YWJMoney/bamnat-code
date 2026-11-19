package com.bamnat.app.ui.pay

import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.WageType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 2.0.0 갈래 J [RVJ-104] 검토 PC-05: 알바 빠른 설정의 세금 방식 첫 값은 그 달력의 주당 시간을 봐요. */
class RvjQuickDefaultsTest {
    @Test fun aPartTimerUnderFifteenHoursStartsOnNotTaken() {
        assertEquals(TaxMode.NONE, quickTaxDefault(WageType.HOURLY, 14 * 60 + 59))
        assertEquals(TaxMode.NONE, quickTaxDefault(WageType.HOURLY, 0))
    }

    @Test fun fifteenHoursAndUpStartsOnTheFourInsurances() {
        assertEquals(TaxMode.INSURANCE_4, quickTaxDefault(WageType.HOURLY, 15 * 60))
        assertEquals("주 18시간이면 떼지 않음으로 시작하지 않아요", TaxMode.INSURANCE_4, quickTaxDefault(WageType.HOURLY, 18 * 60))
    }

    @Test fun otherWagesAreNotAskedByTheHours() {
        assertEquals(TaxMode.INSURANCE_4, quickTaxDefault(WageType.MONTHLY, 0))
        assertEquals(TaxMode.INSURANCE_4, quickTaxDefault(null, 5 * 60))
    }
}
