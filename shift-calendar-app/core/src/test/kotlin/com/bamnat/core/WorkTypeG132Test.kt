package com.bamnat.core

import com.bamnat.core.Fixtures.day
import com.bamnat.core.Fixtures.night
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.report.StaffReport
import com.bamnat.core.report.WorkProof
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.ShiftEvents
import com.bamnat.core.tier.AdRules
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.WorkTypeChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** G 1.32 근무 유형 [WTYP-01]..[WTYP-07]. */
class WorkTypeG132Test {
    private val mon = LocalDate.of(2026, 10, 26)

    @Test fun adsWaitAfterLaunchAndWhileTyping() {
        val opened = Instant.parse("2026-10-28T00:00:00Z")
        assertFalse(AdRules.bannerNow(true, opened, opened.plusSeconds(5), typing = false, closed = false))
        assertTrue(AdRules.bannerNow(true, opened, opened.plusSeconds(AdRules.LAUNCH_QUIET_SECONDS + 1), typing = false, closed = false))
        assertFalse(AdRules.bannerNow(true, opened, opened.plusSeconds(60), typing = true, closed = false))
        assertFalse(AdRules.bannerNow(true, opened, opened.plusSeconds(60), typing = false, closed = true))
        assertFalse(AdRules.bannerNow(false, opened, opened.plusSeconds(60), typing = false, closed = false))
        assertTrue(AdRules.MUTED)
    }

    @Test fun platformDaysAreCountedPaidAndMatched() {
        val j = PlatformWork.job(mon, "쿠팡이츠", 18, 92_000, 390, 8_000, withholding = false)
        assertTrue(PlatformWork.isPlatform(j))
        assertEquals(390, PlatformWork.minutes(j))
        assertEquals("운행 6시간 30분", j.note)
        assertEquals(18, PlatformWork.count(j))
        assertFalse(PlatformWork.isPlatform(Job(date = mon, title = "타일", client = "A건설")))

        val m = PlatformWork.month(YearMonth.from(mon), listOf(j),
            listOf(MoneyEntry(date = mon, kind = MoneyKind.EXPENSE, amount = 20_000, category = "자동차")))
        assertEquals(28_000L, m.costs)
        assertEquals((92_000 - 28_000) * 60 / 390L, m.perHour)

        // 쿠팡이츠: 화요일 마감, 금요일 입금. 월요일 일은 다음 날(화) 마감, 그 주 금요일 입금이에요.
        val p = PlatformWork.payouts(listOf(j), FieldRules()).single()
        assertEquals(DayOfWeek.TUESDAY, p.to.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 30), p.payDate)
        val dep = MoneyEntry(date = p.payDate, kind = MoneyKind.INCOME, amount = 90_000, category = "급여", merchant = "쿠팡 정산")
        assertNotNull(PlatformWork.depositFor(p, listOf(dep)))
        assertTrue(PlatformWork.settle(p).all { it.unpaid == 0L })
        // The user's own 정산 days win over the defaults.
        val own = PlatformWork.payouts(listOf(j), FieldRules(platformPay = mapOf("쿠팡이츠" to (DayOfWeek.SUNDAY to DayOfWeek.WEDNESDAY)))).single()
        assertEquals(DayOfWeek.WEDNESDAY, own.payDate.dayOfWeek)
    }

    @Test fun dayRateChangesFromADayAndTakesThreeDecimals() {
        assertEquals(0.125, DayRate.parse("0,125")!!, 0.0)
        assertNull(DayRate.parse("7"))
        assertEquals("1.125", DayRate.trim(1.125))
        val rules = FieldRules(dayRate = 200_000).withRateFrom(mon, 220_000)
        assertEquals(200_000L, rules.rateOn(mon.minusDays(1)))
        assertEquals(220_000L, rules.rateOn(mon.plusDays(3)))
        val before = DayRate.job(mon.minusDays(1), 1.0, 200_000, "A")
        val after = DayRate.job(mon.plusDays(1), 1.0, 200_000, "A").copy(id = 2)
        val changed = DayRate.repriceFrom(mon, 220_000, listOf(before.copy(id = 1), after))
        assertEquals(listOf(2L), changed.map { it.id })
        assertEquals(220_000L, changed.single().amount)
    }

    @Test fun aPausedRotationPicksUpWhereItStopped() {
        val start = LocalDate.of(2026, 9, 1)
        val old = WorkPattern.Rotation(start, listOf(day.id, night.id, null))
        val from = LocalDate.of(2026, 10, 3)
        val before = ScheduleResolver(Fixtures.all, old, emptyList()).resolve(from).shiftType
        val paused = PatternTimeline.pause(listOf(old), from, from.plusDays(4))
        val r = ScheduleResolver(Fixtures.all, paused, emptyList())
        assertNull(r.resolve(from.plusDays(2)).shiftType)
        assertEquals(before, r.resolve(from.plusDays(5)).shiftType)
        assertEquals(ScheduleResolver(Fixtures.all, old, emptyList()).resolve(from.minusDays(1)).shiftType, r.resolve(from.minusDays(1)).shiftType)
    }

    @Test fun workProofCountsWeeksOver52() {
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(mon, listOf(day.id)), emptyList())
        val days = WorkProof.days(mon, mon.plusDays(13), r::resolve)
        assertEquals(14, days.size)
        val weeks = WorkProof.weeks(days)
        assertEquals(2, weeks.size)
        assertTrue(WorkProof.doc("", mon, mon.plusDays(13), days, mon).text().contains("주별 합계"))
    }

    @Test fun threeStaffAreFree() {
        assertTrue(StaffReport.canAdd(StaffRoster(), pro = false))
        assertEquals(3, StaffReport.FREE_STAFF)
    }

    @Test fun nextOffAndTenureAndCover() {
        val r = ScheduleResolver(Fixtures.all, WorkPattern.Rotation(mon, listOf(day.id, day.id, day.id, null)), emptyList())
        val soon = r.resolveRange(mon.minusDays(1), mon.plusDays(7))
        assertEquals("다음 오프까지 3일 · 10/29(목)", ShiftEvents.nextOff(soon, mon))
        assertEquals("내일 쉬어요", ShiftEvents.nextOff(soon, mon.plusDays(2)))
        assertEquals("오늘은 쉬는 날이에요", ShiftEvents.nextOff(soon, mon.plusDays(3)))

        val t = WorkTypeChecks.tenure(LocalDate.of(2025, 11, 1), mon)!!
        assertFalse(t.oneYear)
        assertEquals("11개월", t.text)
        assertTrue(WorkTypeChecks.tenure(LocalDate.of(2024, 3, 1), mon)!!.oneYear)
        assertNull(WorkTypeChecks.tenure(mon.plusDays(1), mon))
        assertNull(WorkTypeChecks.capHours(null, null))
        assertEquals(40, WorkTypeChecks.capHours(40, null))
    }
}
