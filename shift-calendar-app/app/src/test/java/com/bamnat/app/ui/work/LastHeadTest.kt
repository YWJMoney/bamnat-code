package com.bamnat.app.ui.work

import com.bamnat.core.boss.Ledger
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 G2 PC 4bd5d745: 민수만 지난달이 있을 때 '+504,062원'이 지영 몫까지 더하던 것. */
class LastHeadTest {
    private val place = Workplace("p", "식당", fivePlus = true)
    private val week = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }
    private val minsu = StaffMember("a", "민수", base = 10_320, hiredOn = LocalDate.of(2025, 11, 3), weekly = week)
    private val jiyoung = StaffMember("b", "지영", base = 10_320, weekly = week)
    private val nov = YearMonth.of(2026, 11)

    @Test fun onlyTheSamePeopleAreSetSideBySide() {
        val now = PayrollLedger.month(place, listOf(minsu, jiyoung), nov, emptyList())
        val last = PayrollLedger.month(place, listOf(minsu), nov.minusMonths(1), emptyList())
        val text = lastHead(now, last)!!
        assertTrue(text, text.startsWith("민수 기준 지난달"))
        val minsuNow = now.lines.first { it.staff.id == "a" }.employerTotal
        assertTrue(text, text.contains(com.bamnat.app.ui.Fmt.won(minsuNow)))
    }

    @Test fun everyoneHasLastMonth() {
        val now = PayrollLedger.month(place, listOf(minsu), nov, emptyList())
        val last = PayrollLedger.month(place, listOf(minsu), nov.minusMonths(1), emptyList())
        assertTrue(lastHead(now, last)!!.startsWith("지난달 "))
    }

    @Test fun nobodyHasLastMonth() {
        val now = PayrollLedger.month(place, listOf(jiyoung), nov, emptyList())
        assertNull(lastHead(now, Ledger(place, nov.minusMonths(1), emptyList())))
    }
}

/** 1.39 G2 PC ef175fbf: the 대장 opens on the month to settle next. */
class OpenMonthTest {
    @Test fun beforeAndAfterLastMonthsPayDay() {
        val place = Workplace("p", "식당", payday = 10)
        org.junit.Assert.assertEquals(YearMonth.of(2026, 10), openMonth(place, LocalDate.of(2026, 11, 7)))
        org.junit.Assert.assertEquals(YearMonth.of(2026, 10), openMonth(place, LocalDate.of(2026, 11, 10)))
        org.junit.Assert.assertEquals(YearMonth.of(2026, 11), openMonth(place, LocalDate.of(2026, 11, 11)))
        org.junit.Assert.assertEquals(YearMonth.of(2026, 10), openMonth(Workplace("p", "식당"), LocalDate.of(2026, 11, 25)))
    }
}
