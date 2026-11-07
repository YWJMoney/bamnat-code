package com.bamnat.core

import com.bamnat.core.boss.Durunuri
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.LawChange
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.39 G2-N2 연소근로자, G2-M1 법 바뀜 알림, G2-M2 두루누리. */
class BossNoticesTest {
    private val place = Workplace("p", "카페", fivePlus = false)
    private fun week(days: List<DayOfWeek>, a: Int, b: Int) = days.associateWith { StaffShift(LocalTime.of(a, 0), LocalTime.of(b, 0)) }
    private val five = DayOfWeek.entries.take(5)

    @Test fun under18LongDaysAndNights() {
        val teen = StaffMember("t", "수아", base = 10_320, birth = LocalDate.of(2010, 3, 1), weekly = week(five, 15, 23))
        val keys = PayrollLedger.line(place, teen, YearMonth.of(2026, 11), emptyList()).warnings.map { it.key }
        assertTrue(keys.toString(), "young-hours" in keys)   // 8시간 − 휴게 0.5h... 7시간 넘음, 주 35시간 넘음
        assertTrue(keys.toString(), "young-night" in keys)   // 23시까지
    }

    @Test fun under18WithinTheLimitsAndAdultsAreQuiet() {
        val teen = StaffMember("t", "수아", base = 10_320, birth = LocalDate.of(2010, 3, 1), weekly = week(five.take(3), 16, 21))
        assertTrue(PayrollLedger.line(place, teen, YearMonth.of(2026, 11), emptyList()).warnings.none { it.key.startsWith("young") })
        val adult = teen.copy(birth = LocalDate.of(2000, 1, 1), weekly = week(five, 15, 23))
        assertTrue(PayrollLedger.line(place, adult, YearMonth.of(2026, 11), emptyList()).warnings.none { it.key.startsWith("young") })
        val noBirth = adult.copy(birth = null)
        assertTrue(PayrollLedger.line(place, noBirth, YearMonth.of(2026, 11), emptyList()).warnings.none { it.key.startsWith("young") })
    }

    @Test fun nextMinimumWageIsToldInTheWeeksBefore() {
        val low = StaffMember("a", "민수", base = 10_320, weekly = week(five, 9, 18))
        val ok = StaffMember("b", "지영", base = 11_000, weekly = week(five, 9, 18))
        val n = LawChange.next(place, listOf(low, ok), LocalDate.of(2026, 11, 7))!!
        assertEquals(LocalDate.of(2027, 1, 1), n.from)
        assertTrue(n.changes.toString(), n.changes.first() == "최저임금 시급 10,320원 → 10,700원")
        assertEquals(listOf("a"), n.under.map { it.first.id })
        assertTrue(n.raise > 0)
        assertNull("두 달 반보다 이르면 말하지 않아요", LawChange.next(place, listOf(low), LocalDate.of(2026, 9, 1)))
    }

    @Test fun durunuriOnlyForASmallPlaceWithPensionOrEmployment() {
        val s = StaffMember("a", "민수", base = 10_320, weekly = week(five, 9, 18), insurance = Insurance(pension = true, health = true, employment = true))
        val l = PayrollLedger.month(place, listOf(s), YearMonth.of(2026, 11), emptyList())
        assertTrue(Durunuri.hint(l)!!.contains("270만 원 미만"))
        val none = PayrollLedger.month(place, listOf(s.copy(insurance = Insurance())), YearMonth.of(2026, 11), emptyList())
        assertNull(Durunuri.hint(none))
        val y2027 = PayrollLedger.month(place, listOf(s), YearMonth.of(2027, 1), emptyList())
        assertFalse("확인하지 않은 해는 금액을 말하지 않아요", Durunuri.hint(y2027)!!.contains("만 원"))
    }
}
