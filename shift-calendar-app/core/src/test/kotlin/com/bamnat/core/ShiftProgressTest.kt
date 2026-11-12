package com.bamnat.core

import com.bamnat.core.schedule.ShiftProgress
import com.bamnat.core.sleep.SleepSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.0.0 [RVI-97] (검토 I03c-03) 근무가 흐르는 잠금 화면. */
class ShiftProgressTest {
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)
    private val night = SleepSchedule.Work(LocalDate.of(2026, 11, 12), at(12, 19), at(13, 7), "야간", 0xFF5E35B1)

    @Test fun throughTheNightAcrossMidnight() {
        val n = ShiftProgress.of(listOf(night), at(13, 3, 40))!!
        assertEquals(520L, n.elapsedMinutes)
        assertEquals(720L, n.totalMinutes)
        assertEquals("퇴근까지 3시간 20분", ShiftProgress.left(n, "퇴근까지"))
        assertEquals("3:20", ShiftProgress.chip(n))
        assertEquals(at(13, 3, 55), ShiftProgress.nextDraw(n, at(13, 3, 40)))
        // Near the end it draws again at the end.
        val late = ShiftProgress.of(listOf(night), at(13, 6, 50))!!
        assertEquals("마감까지 10분", ShiftProgress.left(late, "마감까지"))
        assertEquals(at(13, 7), ShiftProgress.nextDraw(late, at(13, 6, 50)))
    }

    @Test fun nothingOutsideWork() {
        assertNull(ShiftProgress.of(listOf(night), at(12, 18, 59)))
        assertNull(ShiftProgress.of(listOf(night), at(13, 7)))
        assertEquals("퇴근까지 2시간", ShiftProgress.left(ShiftProgress.of(listOf(night), at(13, 5))!!, "퇴근까지"))
    }
}
