package com.bamnat.core

import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.plaza.ShareAsk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** PC 12차 유형별 빈틈 3·5 (알바): 가게 말로 묻고, 쉬는 시간 0분은 '없음'. */
class AlbaWords12Test {
    @Test fun anAlbaIsAskedAboutItsShopNotTheSquare() {
        val pay = ShareAsk.Moment.PAY.titleFor(shop = true)
        assertEquals("직접 맞춘 급여 방식, 같은 가게 분들께도 알려 줄까요?", pay)
        assertFalse("광장" in pay)
        ShareAsk.Moment.entries.forEach { assertFalse(it.name, "회사" in it.titleFor(shop = true)) }
        assertEquals(ShareAsk.Moment.PAY.title, ShareAsk.Moment.PAY.titleFor(shop = false))
    }

    @Test fun noBreakReadsNone() {
        assertEquals("없음", HoursFormat.breakText(0))
        assertEquals("30분", HoursFormat.breakText(30))
        assertEquals("1시간", HoursFormat.breakText(60))
    }
}
