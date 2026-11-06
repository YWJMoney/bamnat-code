package com.bamnat.core.memo

import com.bamnat.core.guide.RuleCards
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** [EASE-22][EASE-25][EASE-27] 1.38 근무마다 메모, 하루 밀기, 규칙 문장 카드. */
class ShiftMemoRepeatTest {
    private val day = ShiftType(1, "주간", "주", 0xFF2196F3, LocalTime.of(9, 0), LocalTime.of(18, 0), alarmEnabled = true, alarms = listOf(AlarmSpec(60)))
    private val night = ShiftType(2, "야간", "야", 0xFF3F51B5, LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val off = ShiftType(3, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    private val start = LocalDate.of(2026, 11, 2) // 주 야 휴 휴, from Monday
    private val order = WorkPattern.Rotation(start, listOf(1L, 2L, 3L, null))

    private fun resolver(entries: List<DayEntry> = emptyList(), pattern: WorkPattern = order) =
        ScheduleResolver(listOf(day, night, off), listOf(pattern), entries)

    private fun shifts(r: ScheduleResolver) = MemoRepeat.ShiftOf { r.shiftIdOn(it) }

    @Test fun aShiftMemoIsOnEveryDayOfItsShift() {
        val memo = Memo(id = 1, date = start.plusDays(1), title = "도시락", repeat = MemoRepeat.shiftCode(2))
        assertEquals(MemoRepeat.Kind.SHIFT, MemoRepeat.kindOf(memo.repeat))
        assertEquals("야간마다", MemoRepeat.label(memo.repeat) { if (it == 2L) "야간" else null })
        val r = resolver()
        val days = MemoRepeat.datesIn(memo, start, start.plusDays(11), shifts(r))
        assertEquals(listOf(3L, 7L, 11L).map { start.plusDays(it - 2) }, days)
        // Never before its own day.
        assertFalse(MemoRepeat.occursOn(memo, start.minusDays(3), shifts(r)))
    }

    @Test fun itFollowsTheShiftWhenTheDayMovesOrIsChangedByHand() {
        val memo = Memo(id = 1, date = start.plusDays(1), title = "도시락", repeat = MemoRepeat.shiftCode(2))
        // The 야간 of the 6th set by hand to 휴무, and the 7th made a 야간: the memo leaves the 6th and goes to the 7th.
        val r = resolver(listOf(DayEntry(start.plusDays(5), override = ShiftOverride.Shift(3)), DayEntry(start.plusDays(6), override = ShiftOverride.Shift(2))))
        val days = MemoRepeat.datesIn(memo, start.plusDays(4), start.plusDays(7), shifts(r))
        assertEquals(listOf(start.plusDays(6)), days)
        // Pushed a day from the 4th: the 야간 of the 6th is on the 7th, and so is the memo.
        val pushed = PatternTimeline.realign(order, start.plusDays(4), PatternTimeline.pushedStep(order, start.plusDays(4), 1))
        val r2 = ScheduleResolver(listOf(day, night, off), PatternTimeline.changeFrom(listOf(order), pushed, start.plusDays(4)), emptyList())
        assertEquals(listOf(start.plusDays(6)), MemoRepeat.datesIn(memo, start.plusDays(4), start.plusDays(7), shifts(r2)))
        // The days before the push stay: the 야간 of the 3rd keeps its memo.
        assertTrue(MemoRepeat.occursOn(memo, start.plusDays(1), shifts(r2)))
    }

    @Test fun withoutTheScheduleItIsOnItsOwnDateOnly() {
        val memo = Memo(id = 1, date = start, repeat = MemoRepeat.shiftCode(2))
        assertEquals(listOf(start), MemoRepeat.datesIn(memo, start, start.plusDays(10)))
        assertEquals(listOf(memo), MemoRepeat.expand(listOf(memo), start, start.plusDays(10)))
        // With the schedule and no such day in range it is left out of the range.
        assertEquals(emptyList<Memo>(), MemoRepeat.expand(listOf(memo), start, start, { shifts(resolver()) }))
    }

    @Test fun shiftIdOnReadsHandSetDaysFirst() {
        val r = resolver(listOf(DayEntry(start, override = ShiftOverride.Off), DayEntry(start.plusDays(2), override = ShiftOverride.Shift(99))))
        assertNull(r.shiftIdOn(start))
        assertEquals(2L, r.shiftIdOn(start.plusDays(1)))
        assertNull("a deleted kind is nothing", r.shiftIdOn(start.plusDays(2)))
        assertNull(r.shiftIdOn(start.plusDays(3)))
    }

    @Test fun pushingADayRepeatsTheDayBeforeAndMovesTheRestLater() {
        val d = start.plusDays(5) // 야간 (step 1)
        val step = PatternTimeline.pushedStep(order, d, 1)
        assertEquals(0, step)
        assertEquals(1, PatternTimeline.pushOf(order, d, step))
        assertEquals(-1, PatternTimeline.pushOf(order, d, PatternTimeline.pushedStep(order, d, -1)))
        assertEquals(0, PatternTimeline.pushOf(order, d, 1))
        val pushed = PatternTimeline.realign(order, d, step)
        assertEquals(2L, pushed.shiftTypeIdOn(d.plusDays(1)))
        assertEquals(1L, pushed.shiftTypeIdOn(d))
    }

    @Test fun rulesReadAsSentences() {
        val weekly = WorkPattern.Weekly.fromSteps(start, listOf(1L, 1L, 1L, 1L, 1L, null, null), holidaysOff = true)
        val cards = RuleCards.of(listOf(weekly), listOf(day, night, off), start)
        assertTrue(cards.any { it.sentence == "만약 월·화·수·목·금요일이면 → 근무하는 날이에요" })
        assertTrue(cards.any { it.sentence == "만약 공휴일·대체공휴일이면 → 쉬는 날이 되고 알람도 울리지 않아요" && it.on })
        assertTrue(cards.any { it.sentence == "만약 주간 날이면 → 시작 1시간 전에 울려요" })
        assertTrue(cards.any { it.group == RuleCards.Group.ALARM && it.cond == "야간 날이면" && !it.on })
        val rot = RuleCards.of(listOf(order), listOf(day, night, off), start)
        assertEquals("만약 날마다 → 주야휴휴 순서로 4일마다 돌아요", rot.first().sentence)
    }

    /** 1.38 PC 11차 식당 둘러보기: a shop's days read 영업, and a kind put away gets no alarm card. */
    @Test fun aShopReadsItsOwnWordsAndSkipsKindsPutAway() {
        val weekly = WorkPattern.Weekly.fromSteps(start, listOf(1L, 1L, 1L, 1L, 1L, null, null), holidaysOff = true)
        val cards = RuleCards.of(listOf(weekly), listOf(day, night, off), start, hidden = setOf(night.id), workDayLine = "영업하는 날이에요")
        assertTrue(cards.any { it.sentence == "만약 월·화·수·목·금요일이면 → 영업하는 날이에요" })
        assertTrue(cards.none { it.cond == "야간 날이면" })
        assertTrue(cards.any { it.cond == "주간 날이면" })
    }

    @Test fun aDayTakenOffIsNotOneOfItsDays() {
        val weekly = Memo(id = 1, date = start, title = "분리수거", repeat = "W1", skips = setOf(start.plusWeeks(1)))
        assertEquals(listOf(start, start.plusWeeks(2)), MemoRepeat.datesIn(weekly, start, start.plusWeeks(2)))
        // Its own date taken off, with nothing else in range: not shown in that range at all.
        val first = weekly.copy(skips = setOf(start))
        assertEquals(emptyList<Memo>(), MemoRepeat.expand(listOf(first), start, start.plusDays(3)))
        assertFalse(MemoRepeat.occursOn(first, start))
        // A 근무마다 memo leaves that 야간 only.
        val night = Memo(id = 2, date = start.plusDays(1), title = "도시락", repeat = MemoRepeat.shiftCode(2), skips = setOf(start.plusDays(5)))
        assertEquals(listOf(start.plusDays(1), start.plusDays(9)), MemoRepeat.datesIn(night, start, start.plusDays(11), shifts(resolver())))
        // A memo that does not repeat is not taken off its own day.
        assertTrue(MemoRepeat.occursOn(Memo(id = 3, date = start, skips = setOf(start)), start))
        // Stored and read back, in order; junk is left out.
        assertEquals("20760,20764", MemoRepeat.encodeSkips(setOf(LocalDate.ofEpochDay(20764), LocalDate.ofEpochDay(20760))))
        assertEquals(setOf(LocalDate.ofEpochDay(20760)), MemoRepeat.decodeSkips("20760,x,"))
    }

    @Test fun withEveryAlarmOffNoCardSaysItRings() {
        val cards = RuleCards.of(listOf(order), listOf(day, night, off), start, alarmsOn = false)
        val alarms = cards.filter { it.group == RuleCards.Group.ALARM }
        assertTrue(alarms.isNotEmpty())
        assertTrue(alarms.none { it.on || it.result.contains("울려요") })
    }

    @Test fun repeatingMemosAreCards() {
        val memos = listOf(
            Memo(id = 2, date = start, title = "분리수거", repeat = "W1"),
            Memo(id = 1, date = start.plusDays(1), title = "도시락", repeat = MemoRepeat.shiftCode(2)),
            Memo(id = 3, date = start, title = "한 번만"),
            Memo(id = 4, date = start, title = "지운 근무", repeat = MemoRepeat.shiftCode(99)),
        )
        val cards = RuleCards.of(listOf(order), listOf(day, night, off), start, memos = memos).filter { it.group == RuleCards.Group.MEMO }
        assertEquals(listOf("만약 야간 날이면 → 메모 ‘도시락’이 보여요", "만약 매주 월요일이면 → 메모 ‘분리수거’가 보여요"), cards.map { it.sentence })
        assertTrue(cards.all { it.where == RuleCards.Where.MEMOS })
    }
}
