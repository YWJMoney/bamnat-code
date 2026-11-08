package com.bamnat.core

import com.bamnat.core.leave.LeaveLeft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 1.38 [P3-X-05] 연차 남은 날: 제60조 from the hire date, less what was taken, oldest lot first. */
class LeaveLeftTest {
    @Test fun firstYearMonthly() {
        val hire = LocalDate.of(2026, 3, 2)
        // 3/2 입사: 4/2, 5/2, … 11/2까지 매달 1일 = 12/1에 8일, 다음은 12/2.
        val l = LeaveLeft.of(hire, mapOf(LocalDate.of(2026, 7, 10) to 1.0, LocalDate.of(2026, 8, 3) to 0.5), LocalDate.of(2026, 12, 1))
        assertEquals(8.0, l.made, 0.0)
        assertEquals(1.5, l.used, 0.0)
        assertEquals(6.5, l.left, 0.0)
        assertEquals(LocalDate.of(2026, 12, 2), l.nextOn)
    }

    @Test fun afterAYearMonthlyLapseAnd15Arrive() {
        val hire = LocalDate.of(2025, 3, 2)
        val l = LeaveLeft.of(hire, mapOf(LocalDate.of(2026, 4, 1) to 2.0), LocalDate.of(2026, 5, 1))
        // 첫해 월 연차는 2026-03-01에 끝나고, 2026-03-02에 15일.
        assertEquals(15.0, l.made, 0.0)
        assertEquals(13.0, l.left, 0.0)
    }

    @Test fun threeYearsGetSixteen() {
        val l = LeaveLeft.of(LocalDate.of(2023, 1, 2), emptyMap(), LocalDate.of(2026, 2, 1))
        assertEquals(16.0, l.made, 0.0)
    }
}
