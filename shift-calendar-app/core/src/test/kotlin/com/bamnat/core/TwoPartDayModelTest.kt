package com.bamnat.core

import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.TimeVersion
import com.bamnat.core.model.WorkTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVH-44][RVH-45] 결정 19번 하루 두 구간·시간 단위 연차의 모델(설계 docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 2·3절).
 * The pass line (총괄 검토): a day of one part and a leave of quarters read exactly as before.
 */
class TwoPartDayModelTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun shift(start: LocalTime?, end: LocalTime?, gapStart: LocalTime? = null, gap: Int = 0, rest: Int = 0) =
        ShiftType(1, "주간", "주", 0xFF1565C0, start, end, breakMinutes = rest, gapStart = gapStart, gapMinutes = gap)

    // ---- 2-1절 range rule ----

    @Test fun aGapInsideTheDayWithAPartEachSideIsAGap() {
        // 가게 11:00~22:00, 브레이크 타임 15:00~17:00.
        assertTrue(shift(t(11), t(22), t(15), 120).hasGap)
        // 알바 06:00~09:00 · 19:00~23:00 (10 hours between, not cut to 12 hours or less).
        assertTrue(shift(t(6), t(23), t(9), 600).hasGap)
    }

    @Test fun aGapThatDoesNotFitIsNotUsedNeverCut() {
        assertFalse("no gap", shift(t(9), t(18)).hasGap)
        assertFalse("under 30 minutes", shift(t(9), t(18), t(12), 20).hasGap)
        assertFalse("first part under 30 minutes", shift(t(9), t(18), t(9, 20), 60).hasGap)
        assertFalse("second part under 30 minutes", shift(t(9), t(18), t(16), 100).hasGap)
        assertFalse("runs past the end", shift(t(9), t(18), t(17), 120).hasGap)
        assertFalse("starts before the day", shift(t(9), t(18), t(8), 60).hasGap)
        assertFalse("a day off has none", shift(null, null, t(12), 60).hasGap)
        // Exactly 30 minutes each side fits.
        assertTrue(shift(t(9), t(11), t(9, 30), 60).hasGap)
    }

    @Test fun aGapInADayPastMidnightIsReadAfterTheStart() {
        // 20:00~05:00, stops 01:00~02:00: 5 hours in.
        assertEquals(300, WorkTime.offset(t(20), t(1)))
        assertTrue(shift(t(20), t(5), t(1), 60).hasGap)
        // 2-5절: the gap's clock time on the day's own line is the next day's 01:00.
        val from = LocalDateTime.of(2026, 11, 12, 20, 0)
        assertEquals(LocalDateTime.of(2026, 11, 13, 1, 0), WorkTime.firstAt(from, t(1)))
        assertEquals(from, WorkTime.firstAt(from, t(20)))
    }

    @Test fun aDayOfOnePartReadsAsBefore() {
        val one = shift(t(9), t(18), rest = 60)
        assertFalse(one.hasGap)
        assertEquals(480, one.workMinutes)
        assertNull(one.gapStart)
        assertEquals(0, one.gapMinutes)
    }

    // ---- 시간 기록(WORK-19)에 구간 사이 ----

    @Test fun theTimesHistoryKeepsTheGapAndAnOldLineStillReads() {
        val old = TimeVersion(LocalDate.of(2026, 11, 1), t(9), t(18), 60, t(12))
        val gap = TimeVersion(LocalDate.of(2026, 11, 11), t(11), t(22), 0, null, t(15), 120)
        val raw = TimeVersion.encode(listOf(old, gap))
        // A version without a gap is written exactly as before 2.0.0.
        assertEquals("2026-11-01|540|1080|60|720;2026-11-11|660|1320|0||900|120", raw)
        assertEquals(listOf(old, gap), TimeVersion.decode(raw))
        // What 2.0.0's first build wrote (five parts) still reads.
        assertEquals(listOf(old), TimeVersion.decode("2026-11-01|540|1080|60|720"))
        // A gap of 10 hours is real (not cut to 12 hours of break); one of a day or more, or without its start, is left out.
        assertEquals(600, TimeVersion.decode("2026-11-01|360|1380|0||540|600").single().gapMinutes)
        assertEquals(0, TimeVersion.decode("2026-11-01|360|1380|0||540|1440").single().gapMinutes)
        assertNull(TimeVersion.decode("2026-11-01|360|1380|0|||120").single().gapStart)
    }

    @Test fun aChangeOfTheGapAloneIsAChangeOfTimes() {
        val before = shift(t(11), t(22))
        val after = shift(t(11), t(22), t(15), 120)
        assertFalse(before.sameTimes(after))
        val kept = before.withTimesFrom(after, LocalDate.of(2026, 11, 12))
        assertEquals(t(15), kept.gapStart)
        // The days before keep the day of one part.
        assertFalse(kept.on(LocalDate.of(2026, 11, 11)).hasGap)
        assertTrue(kept.on(LocalDate.of(2026, 11, 12)).hasGap)
    }

    // ---- 3절 시간 단위 연차 ----

    @Test fun anHoursLeaveTakesItsMinutesOverTheDay() {
        val two = Leave.hours(LeaveKind.ANNUAL, 120)
        assertTrue(two.hourly)
        assertFalse(two.isFullDay)
        assertEquals(0.25, two.days, 0.0001)
        assertEquals(0.25, two.days(480), 0.0001)
        // 1일 기준 7시간 30분.
        assertEquals(120 / 450.0, two.days(450), 0.0001)
        assertEquals("연차 2시간", two.label)
        assertEquals("무급 휴가 1시간 30분", Leave.hours(LeaveKind.UNPAID, 90).label)
        assertEquals("연차 30분", Leave.hours(LeaveKind.ANNUAL, 30).label)
        assertEquals("연", two.short)
    }

    @Test fun aLeaveOfQuartersReadsAsBefore() {
        val half = Leave(LeaveKind.ANNUAL, 2)
        assertFalse(half.hourly)
        assertEquals(0.5, half.days, 0.0001)
        assertEquals("반차", half.label)
        assertEquals("반", half.short)
        assertNull(half.linkText(kinds = true))
        assertEquals("연차", Leave().label)
        assertTrue(Leave().isFullDay)
    }

    @Test fun anHoursLeaveIsNeverAWholeDay() {
        assertEquals(1, Leave.legacyQuarters(30))
        assertEquals(1, Leave.legacyQuarters(120))
        assertEquals(2, Leave.legacyQuarters(240))
        assertEquals(3, Leave.legacyQuarters(420))
        // 7시간 50분 of an 8-hour day is still 3 quarters, never 4.
        assertEquals(3, Leave.legacyQuarters(470))
        assertTrue(runCatching { Leave(LeaveKind.ANNUAL, 4, minutes = 60) }.isFailure)
        assertTrue(runCatching { Leave(LeaveKind.ANNUAL, 2, minutes = 24 * 60) }.isFailure)
    }

    @Test fun hoursAreKeptBesideTheCodeAndAWholeDayStaysWhole() {
        val two = Leave.hours(LeaveKind.ANNUAL, 120, fromStart = true)
        val back = Leave.fromCode(two.code)!!.withHours(two.minutes, two.atMinute)
        assertEquals(two, back)
        val middle = Leave.hours(LeaveKind.ANNUAL, 90, at = t(13))
        assertEquals(780, middle.atMinute)
        assertFalse("a middle leave is not off the start", middle.offStart)
        assertEquals(middle, Leave.fromCode(middle.code)!!.withHours(90, 780))
        // A whole day or a value out of range stays as the code says.
        assertEquals(Leave(), Leave().withHours(120, null))
        assertEquals(Leave(LeaveKind.ANNUAL, 2), Leave(LeaveKind.ANNUAL, 2).withHours(0, null))
        assertEquals(Leave(LeaveKind.ANNUAL, 2), Leave(LeaveKind.ANNUAL, 2).withHours(1440, null))
        assertNull(Leave(LeaveKind.ANNUAL, 2).withHours(60, 1440).at)
    }

    // ---- 4-4절 옛 앱이 읽는 근무표 링크 ----

    /** What an app before 2.0.0 does with a link's "leave" (`ReceivedCalendars.ReceivedDay.fullLeave`). */
    private fun oldReadsWholeDay(leave: String?) = leave != null && !leave.contains("반") && !leave.startsWith("0.75")

    /** The share server's `plainLeave` (share-worker index.js) without 휴가 종류 ticked. */
    private fun plain(label: String): String = when {
        label in listOf("연차", "반차", "반반차", "0.75일 연차", "휴가", "0.75일 휴가") -> label
        "반반" in label -> "반반차"
        "반" in label -> "반차"
        label.startsWith("0.75") -> "0.75일 휴가"
        else -> "휴가"
    }

    @Test fun anOldAppNeverReadsAnHoursLeaveAsAWholeDay() {
        for (m in listOf(60, 120, 240, 360, 450)) for (kind in LeaveKind.entries) for (kinds in listOf(true, false)) {
            val text = Leave.hours(kind, m).linkText(kinds)
            assertFalse("$kind ${m}분 → $text", oldReadsWholeDay(text))
            if (text != null) {
                assertTrue("at most 12 letters: $text", text.length <= 12)
                assertFalse("the share server keeps it a part day: $text", oldReadsWholeDay(plain(text)))
            }
        }
        // Under half a day says nothing (the day shows as worked); half a day or more says 반차 with its hours.
        assertNull(Leave.hours(LeaveKind.ANNUAL, 60).linkText(true))
        assertNull(Leave.hours(LeaveKind.ANNUAL, 120).linkText(true))
        assertEquals("반차 4시간", Leave.hours(LeaveKind.ANNUAL, 240).linkText(true))
        assertEquals("무급 반차 6시간", Leave.hours(LeaveKind.UNPAID, 360).linkText(true))
        assertEquals("반차 6시간", Leave.hours(LeaveKind.UNPAID, 360).linkText(false))
        assertEquals("반차 7.5시간", Leave.hours(LeaveKind.ANNUAL, 450).linkText(false))
        // A day of 6 hours: 3 hours is half of it.
        assertEquals("반차 3시간", Leave.hours(LeaveKind.ANNUAL, 180, dayMinutes = 360).linkText(true, 360))
    }
}
