package com.bamnat.core

import com.bamnat.core.design.NightLook
import com.bamnat.core.design.NightRule
import com.bamnat.core.sleep.NightLine
import com.bamnat.core.sleep.SleepSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 1.39 [FLOW-14][DSG-07] 밤 색 rules, [FLOW-13] 밤 화면 한 줄. */
class NightLookTest {
    private val d = LocalDate.of(2026, 11, 9)
    private fun at(h: Int, m: Int = 0, day: LocalDate = d) = day.atTime(h, m)

    @Test fun offNeverPhoneFollowsThePhone() {
        assertFalse(NightRule.app(NightLook.OFF, at(3), sleeping = true, planned = emptyList(), systemDark = true))
        assertFalse(NightRule.screen(NightLook.OFF, at(23), sleeping = true, systemDark = true))
        assertTrue(NightRule.app(NightLook.PHONE, at(14), sleeping = false, planned = emptyList(), systemDark = true))
        assertFalse(NightRule.app(NightLook.PHONE, at(3), sleeping = false, planned = emptyList(), systemDark = false))
    }

    @Test fun sleepTimeIsAsleepPlannedOrDawn() {
        val l = NightLook.SLEEP_TIME
        // After a night shift the planned sleep is in the day: 09:00~16:00.
        val plan = listOf(at(9)..at(16))
        assertTrue(NightRule.app(l, at(10), sleeping = false, planned = plan, systemDark = false))
        assertFalse(NightRule.app(l, at(17), sleeping = false, planned = plan, systemDark = false))
        assertTrue(NightRule.app(l, at(17), sleeping = true, planned = plan, systemDark = false))
        // 새벽 근무 중 01~05시.
        assertTrue(NightRule.app(l, at(1), sleeping = false, planned = emptyList(), systemDark = false))
        assertTrue(NightRule.app(l, at(4, 59), sleeping = false, planned = emptyList(), systemDark = false))
        assertFalse(NightRule.app(l, at(5), sleeping = false, planned = emptyList(), systemDark = false))
        assertFalse(NightRule.app(l, at(23), sleeping = false, planned = emptyList(), systemDark = false))
        // The sleep screen goes dark from 22:00 already.
        assertTrue(NightRule.screen(l, at(22), sleeping = false, systemDark = false))
        assertFalse(NightRule.screen(l, at(6), sleeping = false, systemDark = false))
    }

    @Test fun keysRoundTripAndDefault() {
        NightLook.entries.forEach { assertEquals(it, NightLook.of(it.key)) }
        // 1.39 (사용자 11-07): 1.38's own behaviour by default, the whole app only when chosen.
        assertEquals(NightLook.SCREENS, NightLook.of(null))
        assertEquals(NightLook.SCREENS, NightLook.of("??"))
    }

    @Test fun screensOnlyLeavesTheAppItsOwnColours() {
        val l = NightLook.SCREENS
        val plan = listOf(at(9)..at(16))
        assertFalse(NightRule.app(l, at(3), sleeping = true, planned = plan, systemDark = true))
        assertFalse(NightRule.app(l, at(10), sleeping = false, planned = plan, systemDark = false))
        assertTrue(NightRule.screen(l, at(23), sleeping = false, systemDark = false))
        assertTrue(NightRule.screen(l, at(14), sleeping = true, systemDark = false))
        assertFalse(NightRule.screen(l, at(14), sleeping = false, systemDark = true))
    }

    @Test fun nightLineSaysWorkAndAlarm() {
        val now = at(22, 30)
        val work = SleepSchedule.Work(d.plusDays(1), at(7, day = d.plusDays(1)), at(19, day = d.plusDays(1)), "주간")
        assertEquals("내일 07:00 주간 · 알람 06:00 켜짐", NightLine.of(now, work, at(6, day = d.plusDays(1)), alarmsOn = true).text)
        assertEquals("내일 07:00 주간 · 근무 알람 꺼짐", NightLine.of(now, work, null, alarmsOn = false).text)
        assertFalse(NightLine.of(now, work, null, alarmsOn = false).alarmOn)
        assertEquals("이번 주 근무 없음 · 알람 내일 06:30 켜짐", NightLine.of(now, null, at(6, 30, d.plusDays(1)), alarmsOn = true).text)
        assertEquals("내일 07:00 주간 · 울릴 알람 없음", NightLine.of(now, work, null, alarmsOn = true).text)
        // 가게·현장·투자: no 근무표 words.
        assertEquals("알람 꺼짐", NightLine.of(now, null, null, alarmsOn = false, shifts = false).text)
        // One day word for the night screen and the 자는 중 notice: an alarm days away is not "내일".
        assertEquals("오늘", NightLine.day(at(11, 35), at(13)))
        assertEquals("내일", NightLine.day(at(11, 35), at(13, day = d.plusDays(1))))
        assertEquals("모레", NightLine.day(at(11, 35), at(13, day = d.plusDays(2))))
        assertEquals("11/15", NightLine.day(at(11, 35), at(13, day = d.plusDays(6))))
    }
}

/** 1.39 [FLOW-09] 한 숫자 머리. */
class SleepHeadTest {
    @Test fun headSaysAverageAgainstTarget() {
        val h = com.bamnat.core.sleep.SleepHead.of(com.bamnat.core.sleep.SleepDebt(5, 5 * 386L, 5 * 34L), 420)
        assertEquals("6시간 26분", h.value)
        assertEquals("목표보다 34분 부족 · 7일 중 5일 적음", h.caption)
        assertEquals(null, h.tone)
        val short = com.bamnat.core.sleep.SleepHead.of(com.bamnat.core.sleep.SleepDebt(3, 3 * 330L, 3 * 90L), 420)
        assertEquals("caution", short.tone)
        val more = com.bamnat.core.sleep.SleepHead.of(com.bamnat.core.sleep.SleepDebt(2, 2 * 450L, 0), 420)
        assertEquals("목표보다 30분 더 잤어요 · 7일 중 2일 적음", more.caption)
        assertEquals("good", more.tone)
        val none = com.bamnat.core.sleep.SleepHead.of(com.bamnat.core.sleep.SleepDebt(0, 0, 0), 420)
        assertEquals("—", none.value)
    }
}

/** 1.39 G5-N3 폴드 탁상 시계: the clock walks so the screen keeps no shape. */
class NightClockShiftTest {
    @Test fun walksFifteenPlacesWithinFourDp() {
        val places = (0 until 60).map { com.bamnat.core.sleep.NightClockShift.of(it) }
        assertEquals(15, places.toSet().size)
        assertTrue(places.all { (x, y) -> kotlin.math.abs(x) <= 4 && kotlin.math.abs(y) <= 4 })
        // Each minute it moves.
        assertTrue((1 until 60).all { places[it] != places[it - 1] })
    }
}
