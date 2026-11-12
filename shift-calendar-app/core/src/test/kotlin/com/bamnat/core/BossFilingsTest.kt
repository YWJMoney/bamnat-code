package com.bamnat.core

import com.bamnat.core.boss.BossFilings
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.38 [P3-X-08] 신고·지급 달력: pay day, 10일 원천세, 4대보험 취득·상실 and 퇴사 정산 from the staff rows. */
class BossFilingsTest {
    private val place = Workplace("p", "가나다카페", fivePlus = true, payday = 5)
    private val week = DayOfWeek.entries.take(5).associateWith { StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }
    private val a = StaffMember("a", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 11, 2), weekly = week, insurance = Insurance(true, true, true))
    private val b = StaffMember("b", "최사아", base = 12_000, hiredOn = LocalDate.of(2025, 1, 6), leftOn = LocalDate.of(2026, 10, 31), weekly = week,
        insurance = Insurance(tax = StaffTax.BIZ))
    private val staff = listOf(a, b)
    private fun ledger(m: java.time.YearMonth) = PayrollLedger.month(place, staff, m, emptyList())

    @Test fun theDaysAroundEarlyNovember() {
        val due = BossFilings.upcoming(place, staff, LocalDate.of(2026, 11, 6), ::ledger)
        val byKey = due.associateBy { it.key.substringBefore(':') + ":" + it.date }
        // 10월분 급여 11/5 (어제, 7일 안이라 보여요), 10월 지급분 원천세 11/10.
        assertTrue(byKey.containsKey("pay:2026-11-05"))
        val tax = due.single { it.kind == BossFilings.Kind.WITHHOLD && it.date == LocalDate.of(2026, 11, 10) }
        assertTrue(tax.title.contains("10월 지급분"))
        // 박마바 건강보험 11/16, 나머지 12/15; 최사아 퇴사 정산 11/14, 상실 신고 11/15(건강 없음).
        assertEquals(LocalDate.of(2026, 11, 16), due.single { it.key.startsWith("in-health:a") }.date)
        assertEquals(LocalDate.of(2026, 12, 15), due.single { it.key.startsWith("in-rest:a") }.date)
        assertTrue(due.single { it.key.startsWith("in-rest:a") }.title.startsWith("국민연금·고용보험·산재보험"))
        val settle = due.single { it.kind == BossFilings.Kind.SETTLE }
        assertEquals(LocalDate.of(2026, 11, 14), settle.date)
        assertTrue("1년 넘게 일함", settle.detail.contains("퇴직금"))
        // 2.0.0 [RVJ-14] 검토 BOSS-59: 3.3%(사업소득)로 주는 최사아는 근로자 4대보험·산재 신고가 없어요.
        assertTrue(due.none { it.key.startsWith("out-rest:b") })
        assertTrue(due.none { it.key.startsWith("out-health:b") })
        assertEquals(due.sortedBy { it.date }, due)
    }

    @Test fun bizSplitsThreePointThree() {
        val l = ledger(java.time.YearMonth.of(2026, 10))
        val w = BossFilings.withheld(place, l).single { it.biz }
        assertEquals(w.gross * 3 / 100 / 10 * 10, w.tax)
        assertEquals(w.tax / 10 / 10 * 10, w.local)
        val csv = BossFilings.withheldCsv(place, java.time.YearMonth.of(2026, 10), BossFilings.withheld(place, l))
        assertTrue(csv.contains("최사아,,사업소득,"))
        assertTrue(csv.lines().last { it.isNotBlank() }.contains("합계"))
    }

    // PC 10차(중): someone added today with no 입사일 is not in last month's 원천세. 2.0.0 [RVJ-38] (검토 PC-100): the line stays and says so
    // right by its amount, with what it comes to if they did work that month (the 신고 is not hidden by a missing date).
    @Test fun noHireDateIsLeftOutOfPastMonths() {
        val c = StaffMember("c", "새직원", base = 10_320, weekly = week)
        val all = listOf(c)
        val due = BossFilings.upcoming(place, all, LocalDate.of(2026, 11, 12), ledgerOf = { m -> PayrollLedger.month(place, all, m, emptyList()) })
        val tax = due.single { it.kind == BossFilings.Kind.WITHHOLD && it.key.endsWith(":2026-10") }
        assertEquals("10월 지급분 원천세에 새직원 몫은 없음", 0L, tax.amount)
        assertEquals(listOf("c"), tax.missing!!.staffIds)
        assertTrue(tax.missing!!.line(tax.amount), tax.missing!!.line(tax.amount).startsWith("입사일 없는 새직원님 빼고예요"))
        assertTrue("말에 0명이 없음: ${tax.detail}", !tax.detail.contains("0원 ·") && !tax.detail.contains("0명"))
        assertEquals(listOf("c"), BossFilings.noHireDate(all, LocalDate.of(2026, 11, 12)).map { it.id })
    }

    /** 1.39 G2 PC ef175fbf: '10월분 급여 지급 916,416원 · 1명' read as a wrong sum without saying 민수 was not in it. */
    @Test fun aPartialPayLineSaysWhoIsLeftOut() {
        val c = StaffMember("c", "민수", base = 10_320, weekly = week)
        val d = StaffMember("d", "지영", base = 10_320, hiredOn = LocalDate.of(2025, 11, 3), weekly = week)
        val all = listOf(c, d)
        val due = BossFilings.upcoming(place, all, LocalDate.of(2026, 11, 3), ledgerOf = { m -> PayrollLedger.month(place, all, m, emptyList()) })
        val pay = due.first { it.kind == BossFilings.Kind.PAY && it.key.endsWith(":2026-10") }
        assertTrue(pay.detail, pay.detail.startsWith("1명 · 세전"))
        // [RVJ-38] Said right by the amount, with what it comes to with 민수 in.
        val line = pay.missing!!.line(pay.amount)
        assertTrue(line, line.startsWith("입사일 없는 민수님 빼고예요 · 그 달에 일했으면 입사일을 넣어요(넣으면 약 "))
    }
}
