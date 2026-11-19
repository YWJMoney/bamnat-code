package com.bamnat.core

import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.design.DayStoryItems
import com.bamnat.core.memo.MemoBacklog
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.ShiftEvents
import com.bamnat.core.schedule.ShiftProgress
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.sleep.TodayFlow
import com.bamnat.core.work.Commute
import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-21] 결정 19 갈래 I (설계 docs/plans/2.0.0/두-구간-근무-시간-연차-설계.md 4-2절·2-8절): a day of two parts as the alarm,
 * 오늘의 흐름, the lock screen's bar, the widget's countdown, the watch, the day's story, the memo card and 출퇴근 기록 see it. The pass
 * line: a day of one part reads exactly as before (the tests of one-part days are not changed).
 */
class TwoPartDayShowTest {
    private val day = LocalDate.of(2026, 11, 18) // a Wednesday
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private fun type(start: LocalTime, end: LocalTime, gapStart: LocalTime? = null, gap: Int = 0, gapAlarm: Boolean = false) =
        ShiftType(1, "주간", "주", 0xFF1565C0, start, end, gapStart = gapStart, gapMinutes = gap, gapAlarm = gapAlarm,
            alarmEnabled = true, alarms = listOf(AlarmSpec(offsetMinutes = 60)))

    private fun resolver(type: ShiftType) = ScheduleResolver(listOf(type), listOf(WorkPattern.Rotation(day, listOf(1L))), emptyList())
    private fun resolve(type: ShiftType): ResolvedDay = resolver(type).resolve(day)

    /** 4+4: 11:00~15:00 · 17:00~21:00. */
    private val fourFour = type(t(11), t(21), t(15), 120)
    /** 5+3: 10:00~15:00 · 17:00~20:00. */
    private val fiveThree = type(t(10), t(20), t(15), 120)

    // ---- 알람 (4-2절 1번) ----

    @Test fun theSecondPartRingsOnlyWhenTheShiftSaysSo() {
        val quiet = AlarmPlanner.alarmsOn(resolver(fourFour), day)
        assertEquals(listOf(at(10)), quiet.map { it.triggerAt })
        val loud = AlarmPlanner.alarmsOn(resolver(fourFour.copy(gapAlarm = true)), day)
        assertEquals(listOf(at(10), at(16)), loud.map { it.triggerAt }.sorted())
        assertEquals(t(17), loud.maxBy { it.triggerAt }.shiftStart)
        // A day of one part: one alarm, as before, even with the switch on.
        assertEquals(listOf(at(8)), AlarmPlanner.alarmsOn(resolver(type(t(9), t(18), gapAlarm = true)), day).map { it.triggerAt })
    }

    // ---- 오늘의 흐름 (4-2절 2·3번) ----

    private fun flow(type: ShiftType, now: LocalDateTime, talk: TodayFlow.WorkTalk = TodayFlow.WorkTalk.SHIFT) =
        TodayFlow.of(now, resolver(type), alarmsOn = true, talk = talk)

    @Test fun theFirstPartCountsToTheStopNotToLeavingWork() {
        val c = flow(fourFour, at(13), TodayFlow.WorkTalk.SHOP)
        assertEquals("영업 중 · 15:00까지", c.headline)
        assertTrue(c.detail.orEmpty(), c.detail.orEmpty().contains("브레이크 타임 뒤 17:00"))
    }

    @Test fun theStopIsNotWorkAndOffersANapOnly() {
        val shop = flow(fourFour, at(15, 10), TodayFlow.WorkTalk.SHOP)
        assertEquals("브레이크 타임 · 17:00 다시 시작", shop.headline)
        assertEquals(TodayFlow.Action.NAP, shop.action)
        assertEquals(at(16, 30), shop.napUntil)
        // 일근·교대 call it 비는 시간, never 쉬는 시간 (the 휴게 inside a part).
        val office = flow(fourFour, at(15, 10))
        assertEquals("비는 시간 · 17:00 다시 시작", office.headline)
        assertFalse(office.headline.contains("쉬는 시간"))
        // Too near the start again for a 낮잠.
        assertNull(flow(fourFour, at(16, 20)).napUntil)
    }

    @Test fun theSecondPartIsTheDaysEnd() {
        val c = flow(fiveThree, at(18))
        assertEquals("근무 중 · 20:00까지", c.headline)
    }

    @Test fun aPartTimerSaysBreakTime() {
        assertEquals("브레이크 타임", TodayFlow.WorkTalk.of(com.bamnat.core.work.WorkStyle.HOURLY).gap)
        assertEquals("브레이크 타임", TodayFlow.WorkTalk.of(com.bamnat.core.work.WorkStyle.BUSINESS).gap)
        assertEquals("비는 시간", TodayFlow.WorkTalk.of(com.bamnat.core.work.WorkStyle.DAY).gap)
        assertEquals("비는 시간", TodayFlow.WorkTalk.of(com.bamnat.core.work.WorkStyle.SHIFT).gap)
    }

    // ---- 잠금 화면 막대 (ShiftProgress) ----

    @Test fun theLockScreenBarFillsToTheStopThenRests() {
        val work = SleepSchedule.work(resolve(fourFour))
        val first = ShiftProgress.of(work, at(13))!!
        assertEquals(at(15), first.to)
        assertTrue(first.pausesAtEnd)
        assertEquals("비는 시간까지 2시간", ShiftProgress.left(first, "비는 시간까지"))
        assertNull(ShiftProgress.of(work, at(16)))
        assertEquals(at(15)..at(17), ShiftProgress.stopOf(work, at(16)))
        val second = ShiftProgress.of(work, at(18))!!
        assertFalse(second.pausesAtEnd)
        assertEquals(240, second.totalMinutes)
        // Drawn again when the second part starts, not only at the day's first start.
        assertEquals(at(17), ShiftProgress.changes(work, at(16)).first())
        // One part: as before.
        val one = ShiftProgress.of(SleepSchedule.work(resolve(type(t(9), t(18)))), at(12))!!
        assertEquals(at(9), one.from)
        assertEquals(at(18), one.to)
        assertFalse(one.pausesAtEnd)
    }

    // ---- 위젯 줄 (ShiftEvents.countdown) ----

    @Test fun theWidgetCountsToTheStopAndThenToTheStartAgain() {
        val days = listOf(resolve(fourFour))
        assertEquals("주간 중 · 15:00부터 브레이크 타임 (2시간 남음)", ShiftEvents.countdown(days, at(13), "브레이크 타임"))
        assertEquals("브레이크 타임 · 17:00 다시 시작 (1시간 뒤)", ShiftEvents.countdown(days, at(16), "브레이크 타임"))
        assertEquals("주간 중 · 21:00에 끝나요 (3시간 남음)", ShiftEvents.countdown(days, at(18), "브레이크 타임"))
        // Before the day: the first part's start, as before.
        assertEquals("주간까지 2시간 · 11:00", ShiftEvents.countdown(days, at(9)))
    }

    // ---- 워치 (4-2절 5번) ----

    @Test fun theWatchGetsThePartsInANinthField() {
        val d = WatchDays.day(resolve(fourFour))
        assertEquals(listOf(t(11) to t(15), t(17) to t(21)), d.parts)
        assertEquals("11:00–15:00, 17:00–21:00", d.times)
        val p = WatchPayload(listOf(d), at(8), gap = "브레이크 타임")
        val text = WatchDays.encode(p)
        assertEquals("11:00-15:00,17:00-21:00", text.lines()[1].split('\t')[8])
        val back = WatchDays.decode(text)!!
        assertEquals(d.parts, back.days.single().parts)
        assertEquals("브레이크 타임", back.gap)
        // A watch before 2.0.0 reads 8 fields: one stretch 11:00~21:00, never broken.
        val old = text.lines().let { ls -> (listOf(ls[0]) + ls.drop(1).map { it.split('\t').take(8).joinToString("\t") }).joinToString("\n") }
        val oldDay = WatchDays.decode(old)!!.days.single()
        assertEquals(emptyList<Pair<LocalTime, LocalTime>>(), oldDay.parts)
        assertEquals("11:00–21:00", oldDay.times)
        // A day of one part writes an empty 9th field.
        assertEquals("", WatchDays.encode(WatchPayload(listOf(WatchDays.day(resolve(type(t(9), t(18))))), at(8))).lines()[1].split('\t')[8])
    }

    @Test fun theWatchCountsPartByPart() {
        val d = WatchDays.day(resolve(fourFour))
        val list = WatchDays.countdowns(listOf(d), at(9))
        assertEquals(listOf(at(9) to at(11), at(11) to at(15), at(15) to at(17), at(17) to at(21)), list.map { it.from to it.to })
        assertEquals("주간까지 2시간", WatchDays.countdownLine(list[0], at(9)))
        assertEquals("브레이크 타임까지 2시간", WatchDays.countdownLine(list[1], at(13), "브레이크 타임"))
        assertEquals("다시 시작까지 1시간", WatchDays.countdownLine(list[2], at(16)))
        assertEquals("근무 끝까지 3시간", WatchDays.countdownLine(list[3], at(18)))
        // In the stop the first stretch is the stop itself.
        assertTrue(WatchDays.countdowns(listOf(d), at(16)).first().resumes)
        // One part, as before.
        val one = WatchDays.countdowns(listOf(WatchDays.day(resolve(type(t(9), t(18))))), at(8))
        assertEquals(listOf(at(8) to at(9), at(9) to at(18)), one.map { it.from to it.to })
        assertFalse(one.any { it.pausesAtEnd || it.resumes })
    }

    @Test fun aNightOfTwoPartsCountsOverMidnight() {
        // 20:00~05:00, stops 23:00~01:00.
        val night = type(t(20), t(5), t(23), 120)
        val d = WatchDays.day(resolve(night))
        val list = WatchDays.countdowns(listOf(d), at(19))
        assertEquals(listOf(at(19) to at(20), at(20) to at(23), at(23) to day.plusDays(1).atTime(1, 0),
            day.plusDays(1).atTime(1, 0) to day.plusDays(1).atTime(5, 0)), list.map { it.from to it.to })
    }

    // ---- 하루 이야기 ----

    @Test fun theDaysStoryHasTwoLinesOfWork() {
        val items = DayStoryItems.of(day, resolver(fourFour)).filter { it.kind == com.bamnat.core.design.StoryKind.Shift }
        assertEquals(listOf(at(11) to at(15), at(17) to at(21)), items.map { it.start to it.end })
        assertEquals(1, DayStoryItems.of(day, resolver(type(t(9), t(18)))).count { it.kind == com.bamnat.core.design.StoryKind.Shift })
    }

    // ---- 메모 카드 ----

    @Test fun aMemoDueInTheFirstPartComesAtTheStop() {
        val d = resolve(fourFour)
        val parts: (LocalDate) -> List<ClosedRange<LocalDateTime>> = { if (it == day) d.segments else emptyList() }
        assertEquals(at(15), MemoBacklog.partEnd(at(12), parts))
        assertEquals(at(21), MemoBacklog.partEnd(at(18), parts))
        assertNull(MemoBacklog.partEnd(at(16), parts))
    }

    // ---- 출퇴근 기록 (2-8절): 두 구간 날의 출근 1번·퇴근 1번 ----

    @Test fun goingOutForTheStopIsNotLeavingAndComingBackIsNotArriving() {
        val d = resolve(fourFour)
        assertEquals(Commute.Write.None, Commute.arrive(d, at(11), rounding = false))
        assertEquals(Commute.Write.None, Commute.leave(d, at(11), at(15, 2), rounding = false))
        assertEquals(Commute.Write.None, Commute.arrive(d, at(16, 55), rounding = false))
        assertEquals(Commute.Write.None, Commute.arrive(d, at(17, 30), rounding = false))
        assertEquals(Commute.Write.None, Commute.leave(d, at(16, 55), at(21), rounding = false))
        // Leaving well before the stop is still an earlier end, and late after the day still 잔업.
        assertTrue(Commute.leave(d, at(11), at(13), rounding = false) is Commute.Write.EarlyEnd)
        assertTrue(Commute.leave(d, at(17), at(22), rounding = false) is Commute.Write.Overtime)
        // A late first arrival is still late.
        assertTrue(Commute.arrive(d, at(12), rounding = false) is Commute.Write.Late)
    }

    @Test fun aDayOfOnePartCommutesAsBefore() {
        val one = resolve(type(t(9), t(18)))
        assertTrue(Commute.leave(one, at(9), at(15), rounding = false) is Commute.Write.EarlyEnd)
        assertFalse(Commute.inStop(one, at(15)))
        assertFalse(Commute.inSecondHalf(one, at(17)))
        assertNotNull(Commute.arrive(one, at(10), rounding = false) as? Commute.Write.Late)
    }
}
