package com.bamnat.app.ui.leave

import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.work.DayWork
import com.bamnat.core.work.WorkStyle
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [LIFE-02] 연차로 길게 쉬기 reads the ledger: a suggestion that needs more 연차 than is left says so. */
class LeaveBridgesTest {
    private val fri = LocalDate.of(2026, 11, 6)
    private val one = DayWork.Bridge(listOf(fri), fri.minusDays(3), fri.plusDays(2), emptyList())
    private val two = DayWork.Bridge(listOf(fri, fri.plusDays(3)), fri, fri.plusDays(4), emptyList())

    @Test fun whatIsLeftDecidesWhichFit() {
        assertTrue(LeaveBridges.fits(two, 2.0))
        assertTrue(LeaveBridges.fits(one, 1.5))
        assertFalse(LeaveBridges.fits(two, 1.5))
        assertNull(LeaveBridges.shortNote(one, 1.0))
        assertEquals("남은 연차 1.5일로는 모자라요", LeaveBridges.shortNote(two, 1.5))
        // Used more than had arisen: nothing is left, not a negative number.
        assertEquals("남은 연차 0일로는 모자라요", LeaveBridges.shortNote(one, -1.0))
    }

    @Test fun withoutALedgerEverySuggestionStands() {
        assertTrue(LeaveBridges.fits(two, null))
        assertNull(LeaveBridges.shortNote(two, null))
    }

    // ---- 2.0.0 [RVH-85] 검토 PC-12·PC-173 ----
    private val part = ShiftType(1, "알바", "알", 0xFF2F80ED, LocalTime.of(10, 0), LocalTime.of(14, 30))
    private val day = ShiftType(2, "주간", "주", 0xFF2F80ED, LocalTime.of(9, 0), LocalTime.of(18, 0), breakMinutes = 60)
    private val off = ShiftType(3, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val monday = LocalDate.of(2026, 11, 2)
    private fun week(work: Long, days: Set<DayOfWeek>) =
        WorkPattern.Weekly(monday, DayOfWeek.entries.associateWith { if (it in days) work else 3L })
    private val monWedFri = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    /** 월·화, 목·금 8시간 (주 32시간): runs of two working days, so a 연차 or two makes a long rest ([DayWork.restBridges]). */
    private val twoAndTwo = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    /** PC-12: 주 13시간 30분 알바에게 '연차 1일로 6일 쉬어요'가 여덟 줄. 근로기준법 제18조 제3항: 4주 평균 주 15시간 아래는 연차가 없어요. */
    @Test fun underFifteenHoursAWeekHasNoLeaveToBridgeWith() {
        val today = LocalDate.of(2026, 11, 19)
        val short = ScheduleSnapshot(listOf(part, day, off), week(1, monWedFri), emptyList())
        assertTrue(LeaveBridges.underFifteen(short, today))
        assertTrue(LeaveBridges.of(short, WorkStyle.HOURLY, today).isEmpty())
        // 주 32시간 알바는 연차가 있어요: breaks are offered.
        val full = ScheduleSnapshot(listOf(part, day, off), week(2, twoAndTwo), emptyList())
        assertFalse(LeaveBridges.underFifteen(full, today))
        assertTrue(LeaveBridges.of(full, WorkStyle.HOURLY, today).isNotEmpty())
        // The 15-hour line is about 알바 hours; a 교대·일근 calendar is never cut by it.
        assertTrue(LeaveBridges.of(short, WorkStyle.DAY, today).isNotEmpty() || LeaveBridges.of(short, WorkStyle.SHIFT, today).isNotEmpty())
        // Too little of the roster known (under a week) is not read as short hours.
        assertFalse(LeaveBridges.underFifteen(ScheduleSnapshot(listOf(part, day, off), null, emptyList()), today))
    }

    /** PC-173: 12월 표만 받은 직원에게 '12/30~1/30 · 연차 1일로 32일 쉬어요'. A day no roster covers is not a day off. */
    @Test fun aBreakNeverReachesDaysTheRosterDoesNotCover() {
        val dec = LocalDate.of(2026, 12, 1)
        val entries = (0L until 31L).map { dec.plusDays(it) }.map { d ->
            DayEntry(d, ShiftOverride.Shift(if (d.dayOfWeek in twoAndTwo) 2L else 3L))
        }
        val onlyDecember = ScheduleSnapshot(listOf(part, day, off), null, entries)
        val found = LeaveBridges.of(onlyDecember, WorkStyle.SHIFT, dec)
        assertTrue("12월 안의 연휴는 남아요", found.isNotEmpty())
        assertTrue(found.all { !it.from.isBefore(dec) && !it.to.isAfter(LocalDate.of(2026, 12, 31)) })
        // The same December with the pattern going on still offers the 성탄절·신정 breaks.
        val going = ScheduleSnapshot(listOf(part, day, off), week(2, twoAndTwo), emptyList())
        assertTrue(LeaveBridges.of(going, WorkStyle.SHIFT, dec).any { it.to.year == 2027 })
    }
}
