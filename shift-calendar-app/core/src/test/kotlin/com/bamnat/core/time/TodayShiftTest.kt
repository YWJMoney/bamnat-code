package com.bamnat.core.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [I60b-01] (검토 2026-11-12) 시간 여행 시험: `-Pbamnat.todayShift=P30D`로 돌면 Today의 시스템 시계가 30일 앞이에요.
 * 날마다 main에서 core·앱 시험을 30·365일 뒤로 돌리는 일(.github/workflows/date-watch.yml)이 정말 시계를 옮기는지도 봐요.
 */
class TodayShiftTest {
    @After fun back() = Today.reset()

    @Test fun readsAnIsoPeriodAndIgnoresTheRest() {
        assertEquals(Duration.ofDays(30), Today.shiftOf("P30D"))
        assertEquals(Duration.ofDays(365), Today.shiftOf(" P365D "))
        assertEquals(Duration.ofHours(-6), Today.shiftOf("PT-6H"))
        assertNull(Today.shiftOf(null))
        assertNull(Today.shiftOf(""))
        assertNull(Today.shiftOf("30"))
    }

    @Test fun theSystemClockIsMovedOnlyInATimeTravelRun() {
        val asked = Today.shiftOf(System.getProperty("bamnat.todayShift"))
        assertEquals(asked, Today.travelled)
        val gap = Duration.between(java.time.Clock.systemUTC().instant(), Today.instant()) // real clock: the gap to the moved clock
        val want = asked ?: Duration.ZERO
        assertTrue("시스템 시계와 Today의 차이 $gap, 바란 것 $want", gap.minus(want).abs() < Duration.ofMinutes(1))
    }

    @Test fun aFixedClockIsNotMoved() {
        val at = ZonedDateTime.of(2026, 12, 31, 23, 59, 0, 0, ZoneId.of("Asia/Seoul"))
        Today.fix(at)
        assertEquals(at.toInstant(), Today.instant())
    }
}
