package com.bamnat.core.memo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVI-19]~[RVI-29] (검토 MEMO-30) A repro for each fix of the 메모 일정 review: what the code did before is written in each
 * test's first line.
 */
class MemoReviewFixTest {
    private val day = LocalDate.of(2026, 11, 11) // 수
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m)

    // MEMO-02: an alarm 40분 late (the phone off) told only what was due at that minute; the ones between were lost.
    @Test fun everyScheduleBetweenALateAlarmIsFound() {
        val a = Memo(id = 1, date = day, title = "A", reminder = MemoReminder(at(day, 9), ring = false, notify = true))
        val b = Memo(id = 2, date = day, title = "B", reminder = MemoReminder(at(day, 9, 20), ring = false, notify = true))
        val c = Memo(id = 3, date = day, title = "C", reminder = MemoReminder(at(day, 10), ring = false, notify = true))
        val found = MemoSchedules.between(listOf(a, b, c), at(day, 8, 59), at(day, 9, 40))
        assertEquals(listOf(1L, 2L), found.map { it.second.memo.id })
        assertEquals(listOf(at(day, 9), at(day, 9, 20)), found.map { it.first })
    }

    // MEMO-03: 매일 23:50, put off past midnight, then 끝내기: the next day was the one marked done.
    @Test fun aPutOffPastMidnightIsStillItsOwnDay() {
        val daily = Memo(id = 7, date = day, title = "약", repeat = "D", reminder = MemoReminder(at(day, 23, 50), ring = false, notify = true))
        val later = at(day.plusDays(1), 0, 10)
        val due = MemoSchedules.dueOn(listOf(daily), later, extra = mapOf(7L to later), extraDays = mapOf(7L to day))
        assertEquals(listOf(day), due.map { it.day })
    }

    // MEMO-04: finished in the app after a 미루기: the put-off time still rang.
    @Test fun aPutOffFromAFinishedDayRingsNoMore() {
        val daily = Memo(id = 7, date = day, title = "약", repeat = "D", reminder = MemoReminder(at(day, 9), ring = false, notify = true),
            doneDays = setOf(day))
        val later = at(day, 9, 30)
        assertEquals(emptyList<MemoSchedules.Due>(), MemoSchedules.dueOn(listOf(daily), later, extra = mapOf(7L to later), extraDays = mapOf(7L to day)))
        // Its own time tomorrow still comes.
        assertEquals(at(day.plusDays(1), 9), MemoSchedules.next(listOf(daily), at(day, 9, 1), extra = mapOf(7L to later), extraDays = mapOf(7L to day)))
    }

    // MEMO-10: '야간마다 01:00' rang at 01:00 on the night's own date, 21시간 before the 22:00 start.
    @Test fun aNightsTimeAfterMidnightRingsInThatNight() {
        val night = 3L
        val shifts = object : MemoRepeat.ShiftOf {
            override fun on(date: LocalDate): Long? = if (date == day) night else null
            override fun hours(date: LocalDate) = if (date == day) LocalTime.of(22, 0) to LocalTime.of(7, 0) else null
        }
        val memo = Memo(id = 9, date = day, title = "순찰", repeat = MemoRepeat.shiftCode(night), reminder = MemoReminder(at(day, 1), ring = true, notify = true))
        assertEquals(at(day.plusDays(1), 1), MemoSchedules.nextOf(memo, at(day, 0, 0), shifts))
        // Before the shift starts it is still that night's (23:00 rings the same evening).
        assertEquals(at(day, 23), MemoSchedules.ringsAt(memo, day, LocalTime.of(23, 0), shifts))
        assertEquals(listOf(day), MemoSchedules.dueOn(listOf(memo), at(day.plusDays(1), 1), shiftsOf = { shifts }).map { it.day })
    }

    // MEMO-07: "내일 3시 치과" written on 11/11 was read as 11/13 when opened on 11/12.
    @Test fun relativeWordsCountFromTheDayTheMemoWasWritten() {
        val written = java.time.ZonedDateTime.of(at(day, 20), java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val memo = Memo(id = 1, title = "내일 3시 치과", createdAtMillis = written)
        val found = MemoTime.find(memo, at(day.plusDays(1), 9), java.time.ZoneOffset.UTC)!!
        assertEquals(at(day.plusDays(1), 15), found.at)
        // The answer is kept by the words: asked once, not again the next day.
        assertTrue(found.answeredBy(found.key))
    }

    // MEMO-14: '6시 기상' was 18:00 with no 오전·오후 chip.
    @Test fun aBareEarlyHourIsAMorningGuessToFlip() {
        val l = MemoTime.line("6시 기상", at(day, 22))
        assertEquals(at(day.plusDays(1), 6), l.at)
        assertTrue(l.unmarked)
        assertEquals(at(day.plusDays(1), 18), MemoTime.line("6시 기상", at(day, 22), MemoTime.Half.PM).at)
    }

    // MEMO-15: '평일' begun on a Saturday was also on that Saturday.
    @Test fun aWeekdayRepeatBegunOnAWeekendWaitsForMonday() {
        val sat = LocalDate.of(2026, 11, 14)
        val memo = Memo(id = 2, date = sat, title = "출근 도장", repeat = "B", reminder = MemoReminder(sat.atTime(8, 0), ring = false, notify = true))
        assertFalse(MemoRepeat.occursOn(memo, sat))
        assertFalse(MemoRepeat.occursOn(memo, sat.plusDays(1)))
        assertTrue(MemoRepeat.occursOn(memo, sat.plusDays(2)))
        assertEquals(LocalDate.of(2026, 11, 16), MemoTime.line("평일마다 8시 출근 도장", sat.atTime(13, 0)).date)
    }

    // MEMO-20: '내일배움카드 신청 3시' read 내일 out of 내일배움카드 and broke the title.
    @Test fun aDateWordInsideAWordIsNoDate() {
        val l = MemoTime.line("내일배움카드 신청 3시", at(day, 10))
        assertEquals("내일배움카드 신청", l.title)
        assertEquals(day, l.at!!.toLocalDate())
        assertNull(MemoTime.find("오늘의집 주문", at(day, 10)))
    }

    // MEMO-06: "11. 12. 회의" under a list was renumbered to "2. 12. 회의".
    @Test fun aDateLikeLineIsNotRenumbered() {
        val before = ListContinuation.Edit("1. 장보기\n11. 12. 회의", 0)
        val after = ListContinuation.Edit("1. 장보기 목록\n11. 12. 회의", 8)
        assertEquals(after, ListContinuation.renumber(before, after))
        // A list in order with one line taken out is still put in order.
        val list = ListContinuation.Edit("1. 가\n2. 나\n3. 다", 0)
        val cut = ListContinuation.Edit("1. 가\n3. 다", 5)
        assertEquals("1. 가\n2. 다", ListContinuation.renumber(list, cut).text)
    }
}
