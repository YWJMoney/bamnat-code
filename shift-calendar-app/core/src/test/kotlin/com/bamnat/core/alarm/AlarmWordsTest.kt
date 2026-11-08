package com.bamnat.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.39 [SHOP-13] A shop's 영업 is not called 근무. */
class AlarmWordsTest {
    @Test fun aShopsDayIsNotAShift() {
        assertEquals("주간 근무 알람", AlarmWords.alarm("주간"))
        assertEquals("영업 알람", AlarmWords.alarm("영업"))
        assertEquals("단축영업 알람", AlarmWords.alarm("단축영업"))
        assertEquals("주간근무 알람", AlarmWords.alarm("주간근무"))
        assertEquals("야간 근무", AlarmWords.work("야간"))
    }
}
